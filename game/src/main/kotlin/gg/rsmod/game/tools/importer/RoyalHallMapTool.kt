package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Map-square work under the Grand Exchange's Royal Hall (owner 2026-09-25: "i want the building to move in the middel of
 * grand exchange", then "restore the ge clercks in north east").
 *
 * The hall first stood in the exchange's north-east corner; this tool now rebuilds region 12598's map and locs from the
 * pristine files journaled before any Royal Hall work ([PRISTINE_MAP], [PRISTINE_LOCS]), so the north-east booth, its
 * map markers, the ring section, trees and spirit tree come back exactly as they were, and prepares the centre instead.
 *
 * The hall (floor x [FLOOR_MIN_X]-[FLOOR_MAX_X], z [FLOOR_MIN_Z]-[FLOOR_MAX_Z], walls on the ring of tiles around it; see
 * `ge_home_hall.plugin.kts`) is centred on the exchange's fountain. The centre is flat grass terrain (height 40) under an
 * invisible raised walk surface (plane 1, bridge flag, overlay 124 = magenta, not drawn); everything players see there is
 * the four 10 x 10 paving models 47606/47607/47909/47911. They are replaced by RoyalHallLocTool's copies without the
 * hall's floor ([RoyalHallLocTool.CUTS]), so the paving runs on under the walls and all round the hall unchanged.
 * For every tile of [MIN_X]..[MAX_X] x [MIN_Z]..[MAX_Z] this tool:
 *  - removes the plane-1 surface (bridge flag, overlay and underlay), so the hall has one floor level;
 *  - lays white marble ([MARBLE_OVERLAY]) on the floor; the wall-ring tiles keep their ground (under the paving);
 *  - sets the heights of levels 1-3 for the second storey and the slate roof ([STOREY_STEPS]) and marks the floor and
 *    doorway tiles "remove roof" (tile flag 4), so with the client's selective roof removal the roof shows from outside
 *    and disappears when a player walks in.
 * It removes the centre's four planters (47119) with their plane-1 fences (84), the canopy ring on pillars (47174,
 * 47175, 47244, 47246), the map copy of the fountain (47150 on the raised paving; the hall places it on the floor) and the
 * paving decals (type 22) under the hall. Byte-exact codec round-trips, one [CacheTransaction] (preflight, journal,
 * verify) over both production caches; idempotent.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.RoyalHallMapTool plan|apply`
 */
object RoyalHallMapTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val XTEAS_FILE = "C:/RSPS/game/game/data/xteas/xteas.json"
    private const val REGION_ID = 12598

    /** Region 12598's map and locs as journaled by the first Royal Hall transactions, before any hall change. */
    private const val PRISTINE_MAP = "C:/RSPS/import-journal/tx-20260925-185824/C_RSPS_game_game_data_cache__idx5_grp266_file0.orig"
    private const val PRISTINE_MAP_SHA1 = "bae2a674ac89"
    private const val PRISTINE_LOCS = "C:/RSPS/import-journal/tx-20260925-184002/C_RSPS_game_game_data_cache__idx5_grp267_file0.orig"
    private const val PRISTINE_LOCS_SHA1 = "1ce8d4f83f33"

    const val FLOOR_MIN_X = 3158
    const val FLOOR_MAX_X = 3171
    const val FLOOR_MIN_Z = 3486
    const val FLOOR_MAX_Z = 3497
    private const val MIN_X = FLOOR_MIN_X - 1
    private const val MAX_X = FLOOR_MAX_X + 1
    private const val MIN_Z = FLOOR_MIN_Z - 1
    private const val MAX_Z = FLOOR_MAX_Z + 1
    private val SIDE_DOORS = 3490..3493
    private val SOUTH_DOOR = 3163..3166

    /** Overlay definition 243 (texture 1112, white marble with an inlaid pattern), written +1 as the map stores overlays. */
    private const val MARBLE_OVERLAY = 244
    private const val BLOCKED = 1
    private const val BRIDGE = 2
    private const val REMOVE_ROOF = 4

    /**
     * Height steps (x8 client units) of levels 1..3 above the one below: 30 = 240, the wall height, for the second storey;
     * 31 = 248 puts the roof's eaves (level 2) on top of the second storey's walls; 9 = 72 is the rise of one ring of the
     * roof set 41409, so the level-3 ring continues the level-2 ring.
     */
    private val STOREY_STEPS = intArrayOf(30, 31, 9)

    /** The centre's planters, their fences, the canopy ring on pillars and the map fountain. */
    private val REMOVED_IDS = setOf(47119, 84, 47174, 47175, 47244, 47246, 47150)

    /** The ground tiles of the four paving blocks round the hall (RoyalHallLocTool.CUTS), laid as a white marble plaza. */
    private val PLAZA_X = 3155..3174
    private val PLAZA_Z = 3482..3501

    /** Inside the Grand Exchange's walls (the gate paths beyond them keep their earth). */
    private val EXCHANGE_X = 3143..3190
    private val EXCHANGE_Z = 3470..3515
    private const val DIRT_OVERLAY = 188

    /** A balcony: level-1 marble floor on these tiles, outside a doorway of the upper floor. */
    class Balcony(val xs: IntRange, val zs: IntRange)

    val BALCONIES =
        listOf(
            // Reached through the upper doorways in the wall ring (floored by the hall loop).
            Balcony(SOUTH_DOOR.first - 1..SOUTH_DOOR.last + 1, MIN_Z - 2..MIN_Z - 1),
            Balcony(SOUTH_DOOR.first - 1..SOUTH_DOOR.last + 1, MAX_Z + 1..MAX_Z + 2),
            Balcony(MIN_X - 2..MIN_X - 1, SIDE_DOORS.first - 1..SIDE_DOORS.last + 1),
            Balcony(MAX_X + 1..MAX_X + 2, SIDE_DOORS.first - 1..SIDE_DOORS.last + 1),
        )
    /** Invisible marker on the fountain tile (model 1105), kept: it is the fountain's ambient sound (loc opcode 78). */
    private const val KEPT_MARKER = 29419

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val rx = REGION_ID shr 8
        val rz = REGION_ID and 0xFF

        val pristineMap = File(PRISTINE_MAP).readBytes()
        check(CacheItemProbeTool.sha1(pristineMap).startsWith(PRISTINE_MAP_SHA1)) { "pristine map journal changed" }
        val pristineLocs = File(PRISTINE_LOCS).readBytes()
        check(CacheItemProbeTool.sha1(pristineLocs).startsWith(PRISTINE_LOCS_SHA1)) { "pristine locs journal changed" }

        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        try {
            val mapArchive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive("m${rx}_$rz") ?: error("map missing")
            val mapBytes = mapArchive.file(0)?.data ?: error("map file 0 missing")
            val tiles = Rev667TileCodec.decode(pristineMap)
            check(Rev667TileCodec.encode(tiles).contentEquals(pristineMap)) { "map tile round-trip failed" }

            var paved = 0
            var unbridged = 0
            var upstairs = 0
            var roofed = 0
            var plaza = 0
            fun tile(level: Int, x: Int, z: Int) = tiles.tiles[level][x - rx * 64][z - rz * 64]

            // Remove the raised walk surface (bridge flag, overlay, underlay) of level 1, so the tile's own level 1 is free.
            val leftBridge = HashSet<Pair<Int, Int>>()
            fun unbridge(x: Int, z: Int) {
                val upper = tile(1, x, z)
                if (upper.flags and BRIDGE != 0) {
                    unbridged++
                    leftBridge += x to z
                }
                upper.flags = 0
                upper.overlayId = 0
                upper.overlayShape = 0
                upper.overlayRotation = 0
                upper.underlayId = 0
            }

            // The white marble plaza: every ground tile of the four paving blocks round the hall is laid in marble, so
            // wherever the exchange's paving models leave the ground visible (the ring of bare earth that ran round the old
            // centre) it is marble; under the paving models it stays hidden.
            for (x in PLAZA_X) for (z in PLAZA_Z) {
                if (x in MIN_X..MAX_X && z in MIN_Z..MAX_Z) continue
                val ground = tile(0, x, z)
                ground.overlayId = MARBLE_OVERLAY
                ground.overlayShape = 0
                ground.overlayRotation = 0
                plaza++
            }

            // No bare earth inside the exchange's walls: every dirt-path tile (overlay 187, stored 188) becomes the white
            // marble, keeping its shape and rotation so the curved edges stay (owner 2026-09-26: "i can still see some dirt
            // ground outside the building").
            var dirt = 0
            for (x in EXCHANGE_X) for (z in EXCHANGE_Z) {
                val ground = tile(0, x, z)
                if (ground.overlayId and 0xFF == DIRT_OVERLAY) {
                    ground.overlayId = MARBLE_OVERLAY
                    dirt++
                }
            }
            plaza += dirt

            // Storey heights. A tile's height is its south-west corner and a loc stands on the average of its footprint's
            // corners, so the heights run one tile past the east and north wall rings (else the upper walls, parapet and
            // turrets there sank), and under the three balconies with their far corners.
            val storeyTiles = HashSet<Pair<Int, Int>>()
            for (x in MIN_X..MAX_X + 1) for (z in MIN_Z..MAX_Z + 1) storeyTiles += x to z
            // Balconies with a border for the railings that stand on the neighbouring tiles at their corners.
            BALCONIES.forEach { b -> for (x in b.xs.first - 1..b.xs.last + 2) for (z in b.zs.first - 1..b.zs.last + 2) storeyTiles += x to z }
            for ((x, z) in storeyTiles) {
                // A bridge tile walks on its level-1 heights, so every tile touching a raised corner (the storey tile and
                // its west, south and south-west neighbours) leaves the bridge; its paving model does not change.
                for ((nx, nz) in listOf(x to z, x - 1 to z, x to z - 1, x - 1 to z - 1)) {
                    if (nx !in MIN_X..MAX_X || nz !in MIN_Z..MAX_Z) unbridge(nx, nz)
                }
                for (level in 1..3) tile(level, x, z).height = STOREY_STEPS[level - 1]
                // Level 2 and 3 of the old canopy carried flag 8 ("never removed with the roof"): cleared, so the roof and
                // parapet hide like the rest of the building.
                for (level in 2..3) tile(level, x, z).flags = 0
            }

            for (x in MIN_X..MAX_X) for (z in MIN_Z..MAX_Z) {
                val ground = tile(0, x, z)
                val upper = tile(1, x, z)
                val floor = x in FLOOR_MIN_X..FLOOR_MAX_X && z in FLOOR_MIN_Z..FLOOR_MAX_Z
                val doorway = (x == MIN_X || x == MAX_X) && z in SIDE_DOORS || (z == MIN_Z || z == MAX_Z) && x in SOUTH_DOOR
                if (floor) {
                    ground.overlayId = MARBLE_OVERLAY
                    ground.overlayShape = 0
                    ground.overlayRotation = 0
                    paved++
                }
                ground.flags = ground.flags and BLOCKED.inv()
                unbridge(x, z)
                // The upper floor: one full marble floor over the whole hall, and through the upper doorways (above the
                // ground floor's) out onto the balconies. Roof removed when standing on it, so the roof hides upstairs too.
                if (floor || doorway) {
                    upper.overlayId = MARBLE_OVERLAY
                    upstairs++
                }
                // The wall ring on both levels carries walls: part of the building for roof removal. Upstairs only the
                // doorways lead out (onto the balconies); the rest of the ring is blocked so nobody steps off the building.
                upper.flags = if (floor || doorway) REMOVE_ROOF else REMOVE_ROOF or BLOCKED
                // Selective roof removal (client Static409 flood fill over flag-4 tiles): the whole footprint, wall ring
                // included, is the building. The hall's walls stand on the ring outside the floor (the reverse of the
                // game's own buildings), so an unflagged ring kept its upper walls and parapet on screen from inside.
                ground.flags = ground.flags or REMOVE_ROOF
                roofed++
            }

            // Under the balconies (and their corner railings) the ground counts as building for roof removal: the
            // porticoes are roofed by the balcony, and from inside the hall the balconies hide with the upper storey
            // (owner 2026-09-26 foto bh: "when u are in the building u can see the balcony").
            BALCONIES.forEach { b ->
                for (x in b.xs.first - 1..b.xs.last + 1) for (z in b.zs.first - 1..b.zs.last + 1) {
                    if (x in MIN_X..MAX_X && z in MIN_Z..MAX_Z) continue
                    tile(0, x, z).flags = tile(0, x, z).flags or REMOVE_ROOF
                    tile(1, x, z).flags = tile(1, x, z).flags or REMOVE_ROOF
                }
            }
            // The balconies: marble floors on level 1 outside the three doorways, carried by the porticoes' pillars.
            BALCONIES.forEach { b ->
                for (x in b.xs) for (z in b.zs) {
                    val upper = tile(1, x, z)
                    upper.overlayId = MARBLE_OVERLAY
                    upper.overlayShape = 0
                    upper.overlayRotation = 0
                    upper.flags = REMOVE_ROOF
                    upstairs++
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
                        "Royal Hall (GE centre, north-east restored): pave $paved, plaza $plaza, upper floor $upstairs, unbridge $unbridged, roof flags $roofed",
                        CacheItemProbeTool.sha1(mapBytes),
                    )
            }

            val key = Rev667RegionProbeTool.loadKeys(File(XTEAS_FILE))[REGION_ID] ?: error("No XTEA for region $REGION_ID")
            val locArchive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive("l${rx}_$rz", key) ?: error("locs missing")
            val locBytes = locArchive.file(0)?.data ?: error("locs file 0 missing")
            val locs = Rev667LocCodec.decode(pristineLocs)
            check(Rev667LocCodec.encode(locs).contentEquals(pristineLocs)) { "loc round-trip failed" }
            val inHall = { l: Rev667Loc -> rx * 64 + l.localX in MIN_X..MAX_X && rz * 64 + l.localZ in MIN_Z..MAX_Z }
            val removed = locs.filter { inHall(it) && (it.id in REMOVED_IDS || it.type == 22 && it.id != KEPT_MARKER) }
            removed.groupBy { it.id }.forEach { (id, list) -> println("REMOVE $id x${list.size}") }
            val cuts = RoyalHallLocTool.CUTS.associateBy { Triple(it.source, it.x, it.z) }
            var swapped = 0
            var lowered = 0
            val swappedLocs =
                (locs - removed.toSet()).map { loc ->
                    val cut = cuts[Triple(loc.id, rx * 64 + loc.localX, rz * 64 + loc.localZ)]
                    when {
                        cut != null && loc.plane == 0 -> Rev667Loc(cut.id, loc.localX, loc.localZ, loc.plane, loc.type, loc.rotation).also { swapped++ }
                        // A level-1 loc on a tile that left the bridge stood on the bridge surface; it now stands on the ground.
                        loc.plane == 1 && (rx * 64 + loc.localX to rz * 64 + loc.localZ) in leftBridge ->
                            Rev667Loc(loc.id, loc.localX, loc.localZ, 0, loc.type, loc.rotation).also { lowered++ }
                        else -> loc
                    }
                }
            check(swapped == cuts.size) { "expected ${cuts.size} paving models, swapped $swapped" }
            // The Royal Hall's map icon is one world-map static element (RoyalHallLocTool), which the minimap draws too; a
            // marker loc as well showed it twice (owner 2026-09-26).
            val kept = swappedLocs
            kept.filter(inHall).forEach { println("REMAINS ${it.id} type=${it.type} at ${rx * 64 + it.localX},${rz * 64 + it.localZ},${it.plane}") }
            val updatedLocs = Rev667LocCodec.encode(kept)
            if (!updatedLocs.contentEquals(locBytes)) {
                mutations +=
                    CacheMutation(
                        Rev667RegionProbeTool.MAP_INDEX,
                        locArchive.id,
                        0,
                        updatedLocs,
                        "Royal Hall (GE centre, north-east restored): remove ${removed.size} planters, fences, canopy, fountain and decals, $swapped paving models without the floor",
                        CacheItemProbeTool.sha1(locBytes),
                        xtea = key,
                    )
            }
            println("ROYAL_HALL_MAP lowered=$lowered plaza=$plaza upstairs=$upstairs swapped=$swapped paved=$paved unbridged=$unbridged roofed=$roofed locsRemoved=${removed.size}")
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
