package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Removes the plane-0 ground decorations (loc type 22) inside one rectangle of the Grand Exchange home map square from
 * both production caches. The 667 client bakes static ground decorations into its scene, so a server-side `del` in
 * `home_decor.txt` cannot take away the white leaf litter and worn dirt-path decals around the south gate; only the map
 * itself can. Bounded, transactional (preflight, journal, verify) and idempotent.
 *
 * The same holds for any loc whose origin tile carries the bridge flag (the Grand Exchange's raised paving rings): the
 * client keeps it on the tile underneath the bridge, where a server-side removal (sent on the bridge level) cannot reach
 * it. The Royal Hall (owner 2026-09-25) replaces the north-east bank booth 47173, which stands on such a tile, so the
 * optional last argument names the loc types to remove (default `22`).
 *
 * Usage: `./gradlew :game:runGeHomeGroundDecorTool --args="plan|apply <minX> <minZ> <maxX> <maxZ> [locId,locId,...|*] [type,type,...]"`;
 * without ids (or with `*`) every loc of the named types in the rectangle goes.
 */
object GeHomeGroundDecorTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val XTEAS_FILE = "C:/RSPS/game/game/data/xteas/xteas.json"
    private const val REGION_ID = 12598

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require((mode == "plan" || mode == "apply") && args.size in 5..7) {
            "Usage: plan|apply <minX> <minZ> <maxX> <maxZ> [locId,locId,...|*] [type,type,...]"
        }
        val (minX, minZ, maxX, maxZ) = args.slice(1..4).map { it.toInt() }
        val ids = args.getOrNull(5)?.takeIf { it != "*" }?.split(',')?.map { it.trim().toInt() }?.toSet()
        val types = args.getOrNull(6)?.split(',')?.map { it.trim().toInt() }?.toSet() ?: setOf(22)
        val rx = REGION_ID shr 8
        val rz = REGION_ID and 0xFF
        require(minX >= rx * 64 && maxX < rx * 64 + 64 && minZ >= rz * 64 && maxZ < rz * 64 + 64) { "Rectangle leaves map square $REGION_ID" }

        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        try {
            val key = Rev667RegionProbeTool.loadKeys(File(XTEAS_FILE))[REGION_ID] ?: error("No XTEA for region $REGION_ID")
            val locName = "l${rx}_$rz"
            val locArchive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive(locName, key) ?: error("$locName missing")
            val locBytes = locArchive.file(0)?.data ?: error("$locName file 0 missing")
            val locs = Rev667LocCodec.decode(locBytes)
            check(Rev667LocCodec.encode(locs).contentEquals(locBytes)) { "$locName loc round-trip failed" }
            val removed =
                locs.filter {
                    it.plane == 0 && it.type in types && (ids == null || it.id in ids) && rx * 64 + it.localX in minX..maxX && rz * 64 + it.localZ in minZ..maxZ
                }
            removed.groupingBy { it.id }.eachCount().forEach { (id, count) -> println("REMOVE loc $id x$count") }
            val updatedLocs = Rev667LocCodec.encode(locs - removed.toSet())
            if (!updatedLocs.contentEquals(locBytes)) {
                mutations +=
                    CacheMutation(
                        Rev667RegionProbeTool.MAP_INDEX,
                        locArchive.id,
                        0,
                        updatedLocs,
                        "GE home: remove ${removed.size} locs of types $types in $minX,$minZ..$maxX,$maxZ",
                        CacheItemProbeTool.sha1(locBytes),
                        xtea = key,
                    )
            }
            println("GE_HOME_GROUND_DECOR removed=${removed.size}")
        } finally {
            library.close()
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
