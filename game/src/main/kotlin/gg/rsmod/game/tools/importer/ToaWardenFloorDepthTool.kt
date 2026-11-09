package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File
import kotlin.math.ceil

/**
 * N601: OSRS paints these floor meshes over terrain at the same height. In 667 OpenGL the textured terrain can win
 * that depth test, showing two grey patches through the P3 floor. Keep the original mesh and its raised grid together,
 * one existing [OsrsDecalLiftTool.CLEARANCE] above the terrain. This is renderer clearance, not a change to raid geometry.
 * Source: cache2686 models45432/45469/45471 and map15696 p0/p1; Kaoz DQ6FzzXdA4E at2140/2350s.
 * Usage: java -cp <game lib> gg.rsmod.game.tools.importer.ToaWardenFloorDepthTool plan|apply
 */
object ToaWardenFloorDepthTool {
    val SOURCE_CACHE = RspsPaths.root("import-source/openrs2-2686/cache")
    val MODELS = mapOf(45432 to 16567, 45469 to 16980, 45471 to 17016)

    class Patch(val original: ByteArray, val gridOnly: ByteArray, val output: ByteArray) {
        fun accepts(current: ByteArray): Boolean =
            current.contentEquals(original) || current.contentEquals(gridOnly) || current.contentEquals(output)
    }

    /** Always derive the result from the pinned source: a repeated apply never raises the floor again. */
    fun patch(sourceId: Int, bytes: ByteArray): Patch {
        require(sourceId in MODELS) { "Not a Wardens floor model: $sourceId" }
        val source = ModernModelDecoder.decode(bytes)
        require(source.version == 12 && source.texSpaceCount == 0 && source.vertexCount > 0)
        require(source.vertexX.all { it in -64..64 } && source.vertexZ.all { it in -64..64 })
        require(source.vertexY.all { it in -1..0 } && source.vertexY.any { it == 0 }) { "Unexpected floor height" }
        // The plain tile has base/grid priorities 2/3; both broken tiles also have rubble at 3 and the grid at 4.
        val expectedLayers = if (sourceId == 45432) mapOf(2.toByte() to 2, 3.toByte() to 32)
            else mapOf(2.toByte() to 2, 3.toByte() to 2, 4.toByte() to 32)
        require(source.facePriority?.toList()?.groupingBy { it }?.eachCount() == expectedLayers) { "Unexpected floor layers" }
        val original = Rev667ModelEncoder.encode(source)
        val grid = OsrsDecalLiftTool.liftAll(source)?.first ?: source
        val gridBytes = Rev667ModelEncoder.encode(grid)
        val raised = Rev667ModelDecoder.decode(gridBytes)
        val clearance = ceil(OsrsDecalLiftTool.CLEARANCE).toInt()
        for (v in raised.vertexY.indices) raised.vertexY[v] -= clearance
        val output = Rev667ModelEncoder.encode(raised)
        check(ModelConvertTool.compare(raised, Rev667ModelDecoder.decode(output)).isEmpty()) { "Floor roundtrip failed" }
        return Patch(original, gridBytes, output)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply" }
        val targets = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        val patches = ModernCacheReader(File(SOURCE_CACHE)).use { source ->
            MODELS.mapValues { (osrs, _) -> patch(osrs, requireNotNull(source.file(7, osrs, 0))) }
        }
        val mutations = mutableListOf<CacheMutation>()
        val cache = CacheLibrary(targets.first())
        try {
            for ((osrs, local) in MODELS) {
                val current = requireNotNull(ModelArchive.read(cache, local)) { "Missing model $local" }
                val patch = patches.getValue(osrs)
                check(patch.accepts(current)) { "Model $local differs from the pinned source and supported floor repairs" }
                mutations += CacheMutation(7, local, 0, patch.output, "ToA floor $osrs->$local: terrain depth clearance",
                    expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
                println("WARDEN_FLOOR source=$osrs local=$local changed=${!current.contentEquals(patch.output)}")
            }
        } finally { cache.close() }
        val tx = CacheTransaction(targets, mutations)
        val preflight = tx.preflight()
        check(tx.blockingErrors(preflight).isEmpty()) { tx.blockingErrors(preflight).joinToString("\n") }
        if (args[0] == "plan") { println("DRY_RUN ${tx.id}"); return }
        val result = tx.apply(preflight)
        val errors = tx.verify()
        check(errors.isEmpty()) { errors.joinToString("\n") }
        println("WARDEN_FLOOR transaction=${tx.id} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
