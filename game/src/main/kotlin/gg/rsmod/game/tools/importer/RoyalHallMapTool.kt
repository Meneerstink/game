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

    /** Corners raised for the throne dais (height units x8), see the floor loop. */
    private val DAIS_X = FLOOR_MIN_X + 4..FLOOR_MIN_X + 10
    private val DAIS_Z = FLOOR_MIN_Z + 10..FLOOR_MIN_Z + 11
    private const val DAIS_RISE = 3

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

    /** Tile of the hall's map icon marker: on the fountain, beside the fountain's own sound marker [KEPT_MARKER]. */
    private const val ICON_X = 3164
    private const val ICON_Z = 3491

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
            var gallery = 0
            var roofed = 0
            for (x in MIN_X..MAX_X) for (z in MIN_Z..MAX_Z) {
                val ground = tiles.tiles[0][x - rx * 64][z - rz * 64]
                val upper = tiles.tiles[1][x - rx * 64][z - rz * 64]
                val floor = x in FLOOR_MIN_X..FLOOR_MAX_X && z in FLOOR_MIN_Z..FLOOR_MAX_Z
                val doorway = (x == MIN_X || x == MAX_X) && z in SIDE_DOORS || z == MIN_Z && x in SOUTH_DOOR
                // The throne dais: a tile's height is its south-west corner, so these corners lift the red platform
                // (floor dx 4-9, dz 10) and ramp gently to the north wall and the rug.
                if (x in DAIS_X && z in DAIS_Z) ground.height += DAIS_RISE
                if (floor) {
                    ground.overlayId = MARBLE_OVERLAY
                    ground.overlayShape = 0
                    ground.overlayRotation = 0
                    paved++
                }
                ground.flags = ground.flags and BLOCKED.inv()
                if (upper.flags and BRIDGE != 0) unbridged++
                upper.flags = 0
                upper.overlayId = 0
                upper.overlayShape = 0
                upper.overlayRotation = 0
                upper.underlayId = 0
                // The gallery lounge: a two-tile marble ring along the walls on level 1, roof removed when standing on it
                // (so the roof hides upstairs too); the rest of level 1 over the floor is open to the hall below and
                // blocked, so nobody walks off the gallery.
                if (floor) {
                    val dx = x - FLOOR_MIN_X
                    val dz = z - FLOOR_MIN_Z
                    if (dx <= 1 || dx >= FLOOR_MAX_X - FLOOR_MIN_X - 1 || dz <= 1 || dz >= FLOOR_MAX_Z - FLOOR_MIN_Z - 1) {
                        upper.overlayId = MARBLE_OVERLAY
                        upper.flags = REMOVE_ROOF
                        gallery++
                    } else {
                        upper.flags = BLOCKED
                    }
                }
                // Storey heights for the second storey and the roof (client: level height = level below - value * 8).
                for (level in 1..3) tiles.tiles[level][x - rx * 64][z - rz * 64].height = STOREY_STEPS[level - 1]
                // Selective roof removal: standing on the hall's floor or in a doorway hides the upper storey and roof.
                if (floor || doorway) {
                    ground.flags = ground.flags or REMOVE_ROOF
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
                        "Royal Hall (GE centre, north-east restored): pave $paved, unbridge $unbridged, roof flags $roofed tiles in $MIN_X,$MIN_Z..$MAX_X,$MAX_Z",
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
            val swappedLocs =
                (locs - removed.toSet()).map { loc ->
                    val cut = cuts[Triple(loc.id, rx * 64 + loc.localX, rz * 64 + loc.localZ)]
                    if (cut == null || loc.plane != 0) loc else Rev667Loc(cut.id, loc.localX, loc.localZ, loc.plane, loc.type, loc.rotation).also { swapped++ }
                }
            check(swapped == cuts.size) { "expected ${cuts.size} paving models, swapped $swapped" }
            // The Royal Hall's minimap / world-map icon (RoyalHallLocTool.ICON_LOC), on the fountain beside its sound marker.
            val kept = swappedLocs + Rev667Loc(RoyalHallLocTool.ICON_LOC, ICON_X - rx * 64, ICON_Z - rz * 64, 0, 22, 0)
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
            println("ROYAL_HALL_MAP gallery=$gallery swapped=$swapped paved=$paved unbridged=$unbridged roofed=$roofed locsRemoved=${removed.size}")
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
