package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Takes the flat grey "dark stone" apron (overlay 217) that GeHomeArrivalMapTool laid outside the Grand Exchange south
 * gate back to the original ground (owner 2026-09-23: "the gray ground needs to be removed"). Every tile in the
 * rectangle that still carries overlay 217 gets its overlay, shape, rotation and underlay back from the map as it was
 * before the first arrival transaction (journal tx-20260922-184728). Nothing else in the map changes.
 *
 * Usage: `./gradlew :game:runGeHomeFloorRestoreTool --args="plan|apply"`.
 */
object GeHomeFloorRestoreTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val ORIGINAL = "C:/RSPS/import-journal/tx-20260922-184728/C_RSPS_game_game_data_cache__idx5_grp266_file0.orig"
    private const val REGION_ID = 12598
    private const val DARK_STONE = 217
    private val X = 3150..3180
    private val Z = 3456..3470

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val rx = REGION_ID shr 8
        val rz = REGION_ID and 0xFF
        val original = Rev667TileCodec.decode(File(ORIGINAL).readBytes())

        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        try {
            val archive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive("m${rx}_$rz") ?: error("map missing")
            val bytes = archive.file(0)?.data ?: error("map data missing")
            val map = Rev667TileCodec.decode(bytes)
            check(Rev667TileCodec.encode(map).contentEquals(bytes)) { "map round-trip failed" }
            var restored = 0
            for (x in X) for (z in Z) {
                val tile = map.tiles[0][x - rx * 64][z - rz * 64]
                if (tile.overlayId and 0xFF != DARK_STONE) continue
                val before = original.tiles[0][x - rx * 64][z - rz * 64]
                tile.overlayId = before.overlayId
                tile.overlayShape = before.overlayShape
                tile.overlayRotation = before.overlayRotation
                tile.underlayId = before.underlayId
                restored++
            }
            val updated = Rev667TileCodec.encode(map)
            if (!updated.contentEquals(bytes)) {
                mutations += CacheMutation(Rev667RegionProbeTool.MAP_INDEX, archive.id, 0, updated, "GE home: $restored grey apron tiles back to original ground", CacheItemProbeTool.sha1(bytes))
            }
            println("GE_FLOOR_RESTORE restored=$restored")
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) return println("NOTHING_TO_DO")
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") return println("PLAN_ONLY transaction=${tx.id} (nothing written)")
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
