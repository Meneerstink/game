package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Map-square work under the Grand Exchange's Royal Hall (owner 2026-09-25, after the first in-game look: "the gold carpet
 * does not cover the whole building, I still see green under it", "worldmap still shows grand exchange icon and minimap
 * also on the spot of building", "fix everything").
 *
 * The hall (floor x 3176-3188, z 3503-3514, walls one tile outside it; see `ge_home_hall.plugin.kts`) stands where the
 * ring's raised paving (plane 1, bridge flag), a grass lawn and the spirit tree's mound (heights up to 51) met, so the
 * carpet, a flat ground decoration, was crossed by grass and slopes. For every tile of [MIN_X]..[MAX_X] x [MIN_Z]..[MAX_Z]
 * (the floor and its wall ring) this tool:
 *  - levels plane 0 to height [FLOOR_HEIGHT] and lays the Grand Exchange paving overlay (188, whole tile), clearing the
 *    walk-block bit so the floor is one flat, walkable surface;
 *  - removes the plane-1 bridge surface (bridge flag, overlay and underlay), so the hall has one floor level instead of a
 *    raised half;
 *  - sets the heights of levels 1-3 for the second storey and the slate roof ([STOREY_STEPS]) and marks the floor and
 *    doorway tiles "remove roof" (tile flag 4), so with the client's selective roof removal the roof shows from outside
 *    and disappears when a player walks in.
 *
 * It also removes the plane-1 content that belonged to the north-east corner: the booth's Grand Exchange (637) and bank
 * (560) map-marker locs, which put their icons on the minimap and world map, the booth's four corner pieces, and the
 * plane-1 trees whose crowns stood in the hall or on the spirit tree's new spot. Byte-exact codec round-trips, one
 * [CacheTransaction] (preflight, journal, verify) over both production caches; idempotent.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.RoyalHallMapTool plan|apply`
 */
object RoyalHallMapTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val XTEAS_FILE = "C:/RSPS/game/game/data/xteas/xteas.json"
    private const val REGION_ID = 12598

    private const val MIN_X = 3175
    private const val MAX_X = 3189
    private const val MIN_Z = 3502
    private const val MAX_Z = 3515
    private const val FLOOR_HEIGHT = 40
    private const val GE_PAVING_OVERLAY = 188
    private const val BLOCKED = 1
    private const val REMOVE_ROOF = 4

    private const val FLOOR_MIN_X = 3176
    private const val FLOOR_MAX_X = 3188
    private const val FLOOR_MIN_Z = 3503
    private const val FLOOR_MAX_Z = 3514

    /**
     * Height steps (x8 client units) of levels 1..3 above the one below: 30 = 240, the Legends' Guild wall height, for the
     * second storey; 31 = 248 puts the roof's eaves (level 2) on top of the second storey's walls; 9 = 72 is the rise of
     * one ring of the roof set 41409, so the level-3 ring continues the level-2 ring. (Owner 2026-09-25: "geef t gebouw
     * een dak"; the colonnade's cut beams, about 600 up, now end at the hall's roof line.)
     */
    private val STOREY_STEPS = intArrayOf(30, 31, 9)

    class Removal(val id: Int, val x: Int, val z: Int, val plane: Int, val type: Int, val label: String)

    val REMOVALS =
        listOf(
            Removal(27990, 3179, 3503, 1, 22, "Grand Exchange map marker 637 (north-east booth)"),
            Removal(2738, 3182, 3506, 1, 22, "bank map marker 560 (north-east booth)"),
            Removal(28320, 3186, 3504, 1, 22, "invisible marker (model 1105) beside the booth"),
            Removal(46444, 3179, 3503, 1, 9, "north-east booth corner piece"),
            Removal(46444, 3179, 3506, 1, 9, "north-east booth corner piece"),
            Removal(46444, 3182, 3503, 1, 9, "north-east booth corner piece"),
            Removal(46444, 3182, 3506, 1, 9, "north-east booth corner piece"),
            Removal(40297, 3180, 3511, 1, 10, "tree (3x3) standing in the hall"),
            Removal(38795, 3175, 3512, 1, 10, "tree (3x3) on the hall's north-west corner"),
            Removal(40295, 3189, 3501, 1, 10, "tree (3x3) on the spirit tree's new spot"),
            Removal(40313, 3193, 3505, 1, 10, "tree (3x3) on the spirit tree's new spot"),
            Removal(46444, 3179, 3504, 1, 0, "north-east booth fence"),
            Removal(46444, 3179, 3505, 1, 0, "north-east booth fence"),
            Removal(46444, 3180, 3503, 1, 0, "north-east booth fence"),
            Removal(46444, 3180, 3506, 1, 0, "north-east booth fence"),
            Removal(46444, 3181, 3503, 1, 0, "north-east booth fence"),
            Removal(46444, 3181, 3506, 1, 0, "north-east booth fence"),
            Removal(46444, 3182, 3504, 1, 0, "north-east booth fence"),
            Removal(46444, 3182, 3505, 1, 0, "north-east booth fence"),
            Removal(60279, 3183, 3506, 0, 4, "Grand Exchange wall banner of the booth"),
            // Also hidden live by data/cfg/home_decor.txt `del` lines; removed from the map so it matches.
            Removal(47451, 3175, 3502, 0, 10, "ring section (paving, colonnade roof, pillars) under the hall"),
            Removal(38785, 3176, 3513, 0, 10, "tree in the hall"),
            Removal(38785, 3181, 3512, 0, 10, "tree in the hall"),
            Removal(1317, 3186, 3509, 0, 10, "spirit tree (stands east of the hall, home_decor.txt)"),
        )

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val rx = REGION_ID shr 8
        val rz = REGION_ID and 0xFF

        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        try {
            val mapArchive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive("m${rx}_$rz") ?: error("map missing")
            val mapBytes = mapArchive.file(0)?.data ?: error("map file 0 missing")
            val tiles = Rev667TileCodec.decode(mapBytes)
            check(Rev667TileCodec.encode(tiles).contentEquals(mapBytes)) { "map tile round-trip failed" }

            var levelled = 0
            var paved = 0
            var unbridged = 0
            var storeys = 0
            var roofed = 0
            for (x in MIN_X..MAX_X) for (z in MIN_Z..MAX_Z) {
                val ground = tiles.tiles[0][x - rx * 64][z - rz * 64]
                if (ground.height != FLOOR_HEIGHT) {
                    ground.height = FLOOR_HEIGHT
                    levelled++
                }
                if (ground.overlayId and 0xFF != GE_PAVING_OVERLAY || ground.overlayShape != 0 || ground.overlayRotation != 0) {
                    ground.overlayId = GE_PAVING_OVERLAY
                    ground.overlayShape = 0
                    ground.overlayRotation = 0
                    paved++
                }
                ground.flags = ground.flags and BLOCKED.inv()
                val upper = tiles.tiles[1][x - rx * 64][z - rz * 64]
                if (upper.flags != 0 || upper.overlayId != 0 || upper.underlayId != 0) {
                    upper.flags = 0
                    upper.overlayId = 0
                    upper.overlayShape = 0
                    upper.overlayRotation = 0
                    upper.underlayId = 0
                    unbridged++
                }
                // Storey heights for the second storey and the roof (client: level height = level below - value * 8).
                for (level in 1..3) {
                    val tile = tiles.tiles[level][x - rx * 64][z - rz * 64]
                    if (tile.height != STOREY_STEPS[level - 1]) {
                        tile.height = STOREY_STEPS[level - 1]
                        storeys++
                    }
                }
                // Selective roof removal: standing on the hall's floor or in a doorway hides the upper storey and roof.
                val inside = x in FLOOR_MIN_X..FLOOR_MAX_X && z in FLOOR_MIN_Z..FLOOR_MAX_Z
                val doorway = (x == MIN_X || x == MAX_X) && z in 3508..3510 || z == MIN_Z && x in 3181..3183
                val wanted = if (inside || doorway) ground.flags or REMOVE_ROOF else ground.flags and REMOVE_ROOF.inv()
                if (ground.flags != wanted) {
                    ground.flags = wanted
                    roofed++
                }
            }
            val updatedMap = Rev667TileCodec.encode(tiles)
            if (!updatedMap.contentEquals(mapBytes)) {
                mutations +=
                    CacheMutation(
                        Rev667RegionProbeTool.MAP_INDEX,
                        mapArchive.id,
                        0,
                        updatedMap,
                        "Royal Hall: level $levelled, pave $paved, unbridge $unbridged, storey heights $storeys, roof flags $roofed tiles in $MIN_X,$MIN_Z..$MAX_X,$MAX_Z",
                        CacheItemProbeTool.sha1(mapBytes),
                    )
            }

            val key = Rev667RegionProbeTool.loadKeys(File(XTEAS_FILE))[REGION_ID] ?: error("No XTEA for region $REGION_ID")
            val locArchive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive("l${rx}_$rz", key) ?: error("locs missing")
            val locBytes = locArchive.file(0)?.data ?: error("locs file 0 missing")
            val locs = Rev667LocCodec.decode(locBytes)
            check(Rev667LocCodec.encode(locs).contentEquals(locBytes)) { "loc round-trip failed" }
            val removed = ArrayList<Rev667Loc>()
            REMOVALS.forEach { r ->
                val match =
                    locs.filter {
                        it.id == r.id && it.plane == r.plane && it.type == r.type && rx * 64 + it.localX == r.x && rz * 64 + it.localZ == r.z
                    }
                println(if (match.isEmpty()) "ALREADY_GONE ${r.id} at ${r.x},${r.z},${r.plane} (${r.label})" else "REMOVE ${r.id} at ${r.x},${r.z},${r.plane} (${r.label})")
                removed += match
            }
            val kept = locs - removed.toSet()
            kept.filter { rx * 64 + it.localX in MIN_X..MAX_X && rz * 64 + it.localZ in MIN_Z..MAX_Z }.forEach {
                println("REMAINS ${it.id} type=${it.type} at ${rx * 64 + it.localX},${rz * 64 + it.localZ},${it.plane}")
            }
            val updatedLocs = Rev667LocCodec.encode(kept)
            if (!updatedLocs.contentEquals(locBytes)) {
                mutations +=
                    CacheMutation(
                        Rev667RegionProbeTool.MAP_INDEX,
                        locArchive.id,
                        0,
                        updatedLocs,
                        "Royal Hall: remove ${removed.size} plane-1 markers, booth pieces and trees",
                        CacheItemProbeTool.sha1(locBytes),
                        xtea = key,
                    )
            }
            println("ROYAL_HALL_MAP levelled=$levelled paved=$paved unbridged=$unbridged storeys=$storeys roofed=$roofed locsRemoved=${removed.size}")
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
