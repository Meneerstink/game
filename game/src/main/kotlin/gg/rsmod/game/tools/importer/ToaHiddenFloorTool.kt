package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * N601: OSRS overlay41 is a transparent mask, not an absent overlay. Dropping it makes the 667 terrain builder
 * turn shape0 into underlay-only shape12, exposing grey ground around the Wardens floor. Native overlay41 has
 * the same RGB ff00ff, no texture/secondary colour and no occlusion. Restore the source mask and its shape;
 * retain every height, underlay, flag and environment byte. No definition or client change.
 * Sources: cache2686 overlay41 and ToA terrain; 667 FloorOverlayType.colour, Class306.loadBlended/loadUnblended.
 * Usage: plan|apply
 */
object ToaHiddenFloorTool {
    const val SOURCE_OVERLAY = 41
    const val LOCAL_OVERLAY = 41
    val TARGETS = ToaUpperTileFlagTool.targets("raid") + ToaUpperTileFlagTool.targets("lobby")

    /** Only the uncoloured source mask: other hidden overlays have separate minimap colours, left for their own repair. */
    fun preserveMask(tileValue: Int, source: ModernOverlayDef?, squares: List<Int>): Boolean =
        tileValue == SOURCE_OVERLAY + 1 && source != null && source.rgb == 0xFF00FF && source.texture == -1 &&
            !source.hideUnderlay && source.secondaryRgb == -1 && squares.isNotEmpty() && squares.all { it in TARGETS }

    fun validateNative(def: Rev667OverlayDef) {
        check(def.rgb == 0xFF00FF && def.texture == -1 && !def.occludes && def.blendRgb == -1) {
            "Native overlay41 is no longer the transparent, uncoloured source mask"
        }
    }

    internal fun restore(bytes: ByteArray, source: Array<Array<Array<ModernTile>>>): Pair<ByteArray, IntArray> {
        val map = Rev667TileCodec.decode(bytes)
        check(Rev667TileCodec.encode(map).contentEquals(bytes)) { "Tile roundtrip failed" }
        val counts = IntArray(4)
        for (p in 0..3) for (x in 0..63) for (z in 0..63) {
            val src = source[p][x][z]
            if (src.overlayId != SOURCE_OVERLAY + 1) continue
            val tile = map.tiles[p][x][z]
            check(tile.height == src.height) { "Source height differs at $p/$x/$z" }
            check((tile.overlayId and 255) in setOf(0, LOCAL_OVERLAY + 1)) { "Unexpected overlay at $p/$x/$z" }
            if ((tile.overlayId and 255) == LOCAL_OVERLAY + 1 && tile.overlayShape == src.overlayPath &&
                tile.overlayRotation == src.overlayRotation) continue
            tile.overlayId = LOCAL_OVERLAY + 1
            tile.overlayShape = src.overlayPath
            tile.overlayRotation = src.overlayRotation
            counts[p]++
        }
        return Rev667TileCodec.encode(map) to counts
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply" }
        val paths = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        val mutations = mutableListOf<CacheMutation>()
        ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache"))).use { reader ->
            val mask = ModernFloorDefs.decodeOverlay(requireNotNull(reader.file(2, 4, SOURCE_OVERLAY)))
            check(preserveMask(SOURCE_OVERLAY + 1, mask, TARGETS.keys.toList())) { "Source overlay41 changed" }
            val game = CacheLibrary(paths[0])
            val files = CacheLibrary(paths[1])
            try {
                for (cache in listOf(game, files))
                    validateNative(Rev667FloorCodec.decodeOverlay(requireNotNull(cache.data(2, 4, LOCAL_OVERLAY))))
                for ((square, name) in TARGETS) {
                    val source = ModernRegion.decodeTiles(requireNotNull(reader.file(5, square, 0)))
                    val archive = requireNotNull(game.index(5).archive(name)) { "Missing $name" }
                    val other = requireNotNull(files.index(5).archive(name)) { "Fileserver missing $name" }
                    val before = requireNotNull(archive.file(0)?.data)
                    check(archive.id == other.id && before.contentEquals(other.file(0)?.data)) { "$name cache copies differ" }
                    val (after, counts) = restore(before, source)
                    println("HIDDEN_FLOOR source=$square target=$name restored=${counts.toList()}")
                    if (!before.contentEquals(after)) mutations += CacheMutation(5, archive.id, 0, after,
                        "ToA $name: restore source overlay41 transparent masks", CacheItemProbeTool.sha1(before))
                }
            } finally { game.close(); files.close() }
        }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(paths, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (args[0] == "plan") { println("DRY_RUN ${tx.id} maps=${mutations.size}"); return }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "Cache verification failed: ${tx.id}" }
        println("HIDDEN_FLOOR transaction=${tx.id} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
