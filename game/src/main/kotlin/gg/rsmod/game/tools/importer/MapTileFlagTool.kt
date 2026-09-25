package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Clears the terrain "blocked" bit (map tile flags bit 0, the client's and the server's walk block) on named tiles in both
 * production caches. Owner 2026-09-24: the two "Border guard" locs (45861) at 3137,3467 and 3139,3467 were removed with
 * developer mode, but the road stayed blocked. The loc itself never blocked (LocType blockwalk 0); the map data marks the
 * booth bases 3137,3467 and 3140,3467 as blocked terrain, so the booths' footprint stayed a wall after the booths were gone.
 *
 * Only bit 0 of the named tiles changes; the region map is round-tripped byte-exact before the edit, written through
 * [CacheTransaction] (preflight, journal, verify) to both caches.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.MapTileFlagTool plan|apply x,z,plane [x,z,plane ...]`
 */
object MapTileFlagTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val BLOCKED = 1

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply x,z,plane [x,z,plane ...]" }
        val tiles =
            args.drop(1).map { arg ->
                val (x, z, plane) = arg.split(',').map { it.trim().toInt() }
                Triple(x, z, plane)
            }
        require(tiles.isNotEmpty()) { "Name at least one x,z,plane tile." }

        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        try {
            for ((region, regionTiles) in tiles.groupBy { ((it.first shr 6) shl 8) or (it.second shr 6) }) {
                val rx = region shr 8
                val rz = region and 0xFF
                val mapName = "m${rx}_$rz"
                val archive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive(mapName) ?: error("$mapName missing")
                val bytes = archive.file(0)?.data ?: error("$mapName file 0 missing")
                val map = Rev667TileCodec.decode(bytes)
                check(Rev667TileCodec.encode(map).contentEquals(bytes)) { "$mapName tile round-trip failed" }
                val changed = ArrayList<String>()
                for ((x, z, plane) in regionTiles) {
                    val tile = map.tiles[plane][x and 63][z and 63]
                    println("TILE $x,$z,$plane flags=${tile.flags}")
                    if (tile.flags and BLOCKED != 0) {
                        tile.flags = tile.flags and BLOCKED.inv()
                        changed += "$x,$z,$plane"
                    }
                }
                if (changed.isEmpty()) continue
                mutations +=
                    CacheMutation(
                        Rev667RegionProbeTool.MAP_INDEX,
                        archive.id,
                        0,
                        Rev667TileCodec.encode(map),
                        "terrain walk block cleared on ${changed.joinToString(" ")}",
                        CacheItemProbeTool.sha1(bytes),
                    )
            }
        } finally {
            library.close()
        }

        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO every named tile is already walkable terrain")
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
