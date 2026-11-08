package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * N462 07-11 round 8 (testclient: Kephri antechamber + arena black, player invisible): the ToA raid import cleared OSRS tile flag 0x8 on
 * EVERY plane (N522 lesson: 0x8 on plane-0 cave floors). On planes 1-3, 0x8 means "render this tile on level 0" in OSRS and in the
 * rev-667 client alike (Static705.method9198; Static409 keeps such tiles out of the roof flood). Without it the ToA ceilings
 * (cache2686 14164 planes 1-2 are 0x8 on every tile over Kephri's rooms) are drawn as real upper levels right above the player.
 *
 * This tool sets 0x8 back on planes 1-3 of the moved raid squares (+9,+24), lobby and Necropolis in-place, only where the OSRS tile has it. Nothing else changes; each
 * tile file is round-tripped byte-exact first and written through [CacheTransaction] (preflight, journal, verify) to both caches.
 *
 * Usage: `plan|apply [raid|lobby|necropolis|all]` (default raid preserves the original scope).
 */
object ToaUpperTileFlagTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    private const val RENDER_LOW = 0x8
    private val SQUARES = listOf(14160, 14162, 14164, 14672, 14674, 14676, 15184, 15186, 15188, 15696, 15698, 15700)
    private val NECROPOLIS = listOf(13097, 13098, 13353, 13354, 13355, 13609, 13610, 13611)

    internal fun targets(scope: String): Map<Int, String> {
        val raid = SQUARES.associateWith { "m${(it shr 8) + 9}_${(it and 255) + 24}" }
        val lobby = mapOf(13454 to "m52_142")
        val exterior = NECROPOLIS.associateWith { "m${it shr 8}_${it and 255}" }
        return when (scope) {
            "raid" -> raid
            "lobby" -> lobby
            "necropolis" -> exterior
            "all" -> raid + lobby + exterior
            else -> error("Unknown scope $scope; expected raid|lobby|necropolis|all")
        }
    }

    /** Restore only missing source bit8, retaining all other fields, trailing environment and plane0 byte-for-byte. */
    internal fun restore(bytes: ByteArray, settings: (Int, Int, Int) -> Int): Pair<ByteArray, IntArray> {
        val map = Rev667TileCodec.decode(bytes)
        check(Rev667TileCodec.encode(map).contentEquals(bytes)) { "tile round-trip failed; refusing noncanonical data" }
        val perPlane = IntArray(4)
        for (plane in 1 until 4) for (x in 0 until 64) for (z in 0 until 64) {
            val tile = map.tiles[plane][x][z]
            if (settings(plane, x, z) and RENDER_LOW != 0 && tile.flags and RENDER_LOW == 0) {
                tile.flags = tile.flags or RENDER_LOW
                perPlane[plane]++
            }
        }
        return Rev667TileCodec.encode(map) to perPlane
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(args.size <= 2 && mode in setOf("plan", "apply")) { "Usage: plan|apply [raid|lobby|necropolis|all]" }
        val selected = targets(args.getOrNull(1) ?: "raid")
        val keys = ModernRegionProbeTool.loadKeys(File(FeroxImportTool.MODERN_KEYS))
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        val files = CacheLibrary(FILE_SERVER_CACHE)
        try {
            ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { reader ->
                for ((square, name) in selected) {
                    val osrs = ModernRegion.load(reader, square, keys[square])
                    val archive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive(name) ?: error("$name missing")
                    val bytes = archive.file(0)?.data ?: error("$name file 0 missing")
                    val other = files.index(Rev667RegionProbeTool.MAP_INDEX).archive(name) ?: error("fileserver $name missing")
                    check(archive.id == other.id && bytes.contentEquals(other.file(0)?.data)) { "$name differs between target caches" }
                    val (patched, perPlane) = restore(bytes) { p, x, z -> osrs.tiles[p][x][z].settings }
                    println("SQUARE $square -> $name set0x8 plane1=${perPlane[1]} plane2=${perPlane[2]} plane3=${perPlane[3]}")
                    if (perPlane.sum() == 0) continue
                    mutations += CacheMutation(Rev667RegionProbeTool.MAP_INDEX, archive.id, 0, patched,
                        "ToA $name: OSRS 0x8 back on planes 1-3", CacheItemProbeTool.sha1(bytes))
                }
            }
        } finally {
            library.close()
            files.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} mutations=${mutations.size} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
