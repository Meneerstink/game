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
 *  - lays white marble ([MARBLE_OVERLAY]) on the floor and on the wall ring under the walls and towers;
 *  - sets the heights of levels 1-3 for the second storey and the slate roof ([STOREY_STEPS]) and marks only the floor
 *    tiles inside the walls "remove roof" (tile flag 4), so with the client's selective roof removal the roof shows from
 *    outside - thresholds, porticoes and balconies included - and disappears when a player walks in.
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

    /** Client TileFlag.ZERO_LEVEL: the tile is drawn as part of level 0, so roof removal never hides it. */
    private const val ZERO_LEVEL = 8

    /**
     * RSPS tile flag 0x20 on a level-1 tile ("part of the building below", client Static409 / RspsGraphicsProfile): the
     * roof flood of a player INSIDE the ground floor also runs over it and hides what stands on it, like the roof, while
     * a player standing on or under it is outside. Map flags only encode 1..32, so such a tile carries no other flag.
     */
    const val BELOW_BUILDING = 0x20

    /**
     * Height steps (x8 client units) of levels 1..3 above the one below: 30 = 240, the wall height (walls 33868/33877 are
     * 240 high), for the second storey and again for the roof's eaves and parapet (level 2) exactly on the second storey's
     * wall tops - 31 (248) left an 8-unit slit all round the building between wall and parapet that the sky showed
     * through (owner 2026-09-26: "some walls can be seen through"); 9 = 72 is the rise of one ring of the roof set 41409,
     * so the level-3 ring continues the level-2 ring.
     */
    private val STOREY_STEPS = intArrayOf(30, 30, 9)

    /** The centre's planters, their fences, the canopy ring on pillars and the map fountain. */
    private val REMOVED_IDS = setOf(47119, 84, 47174, 47175, 47244, 47246, 47150)

    /** A balcony: level-1 marble floor on these tiles, outside a doorway of the upper floor. */
    class Balcony(val xs: IntRange, val zs: IntRange)

    /**
     * Reached through the upper doorways. Each includes its row of the wall ring, so the balcony floor runs up to the
     * building's face: without it the ring tiles either side of a doorway had no level-1 floor and the balcony stood a
     * tile off the wall (owner 2026-09-26: "the balconys are not connecting visible to the building").
     */
    val BALCONIES =
        listOf(
            Balcony(SOUTH_DOOR.first - 1..SOUTH_DOOR.last + 1, MIN_Z - 2..MIN_Z),
            Balcony(SOUTH_DOOR.first - 1..SOUTH_DOOR.last + 1, MAX_Z..MAX_Z + 2),
            Balcony(MIN_X - 2..MIN_X, SIDE_DOORS.first - 1..SIDE_DOORS.last + 1),
            Balcony(MAX_X..MAX_X + 2, SIDE_DOORS.first - 1..SIDE_DOORS.last + 1),
        )
    /**
     * Death's Domain (loc 62430, 2 x 3) in the hall's south-east corner (owner 2026-09-26: "verplaats de death van grand
     * exchange naar 3169, 3487, 0 ... precies t zelfde te benaderen als de death entrance in ferox enclave"): one tile
     * south of the owner's tile so its back stands on the south wall as the Ferox one stands on its wall.
     */
    const val DEATHS_DOMAIN_LOC = 62430
    const val DEATHS_DOMAIN_X = 3169
    const val DEATHS_DOMAIN_Z = 3486
    const val DEATHS_DOMAIN_ROT = 2

    /** Ferox's underlay under the tomb (stored id), shown where the front row's shaped overlay leaves the tile open. */
    const val FEROX_UNDERLAY = 50

    /** Ferox's floor under and beside the tomb (x 3124-3127, z 3630-3632), turned 180 degrees: dx, dz, shape, rotation, overlay. */
    val DEATHS_DOMAIN_FLOOR =
        listOf(
            listOf(0, 0, 0, 0, 61), listOf(1, 0, 0, 0, 61),
            listOf(0, 1, 0, 0, 61), listOf(1, 1, 0, 0, 61),
            listOf(0, 2, 6, 3, 61), listOf(1, 2, 6, 3, 61),
            // The two side tiles beside the front row are full marble here: Ferox cuts them (shape 9) over its underlay 50,
            // which showed as grass inside the mansion at 3168,3488 and 3171,3488 (owner 2026-09-27).
            listOf(-1, 2, 0, 0, MARBLE_OVERLAY), listOf(2, 2, 0, 0, MARBLE_OVERLAY),
        )

    /** Dark granite: overlay 236 = FloType 235 (35302d, texture 928), Lumbridge castle courtyard 3222,3218. */
    const val GRANITE_OVERLAY = 236

    /** The brown ring road / path overlay (stored id 188 = FloType 187, 4e4329, texture 441). */
    const val DIRT_ROAD_OVERLAY = 188

    /** Falador square cobbles: overlay 137 = FloType 136 (746949, texture 608), Falador 2965,3380. */
    const val BRICK_OVERLAY = 137

    /** The path between the grey blocks south of the south entrance (owner's tags, foto d.png). */
    val BRICK_PATH: Set<Pair<Int, Int>> = (3164..3165).flatMap { x -> (3478..3482).map { z -> x to z } }.toSet()

    /** The exchange centre inside the brown ring road; the grass band fill must stay inside it. */
    val BAND_BOX_X = 3143..3186
    val BAND_BOX_Z = 3472..3512

    /** South-west tiles of the four corner turrets 62766 (2 x 2), as home_decor.txt places them. */
    val TURRETS = listOf(3157 to 3485, 3171 to 3485, 3157 to 3497, 3171 to 3497)

    /** Invisible marker on the fountain tile (model 1105), kept: it is the fountain's ambient sound (loc opcode 78). */
    private const val KEPT_MARKER = 29419

    /**
     * The floor a tomb loc (Death's Domain 62430 and the Skilling Basement's way down, both 2 x 3 at rotation 2) stands
     * on: [DEATHS_DOMAIN_FLOOR] from its south-west tile ([x], [z]) - the hole its dark pit and sunken steps show through.
     */
    fun layTombFloor(tiles: Rev667TileMap, rx: Int, rz: Int, x: Int, z: Int) {
        DEATHS_DOMAIN_FLOOR.forEach { (dx, dz, shape, rot, overlay) ->
            val ground = tiles.tiles[0][x + dx - rx * 64][z + dz - rz * 64]
            ground.overlayId = overlay
            ground.overlayShape = shape
            ground.overlayRotation = rot
            ground.underlayId = FEROX_UNDERLAY
        }
    }

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

            // Owner 2026-09-26 (foto wit): "het wit moet weg uit de cirkel alleen wit op terrein van mansion zelf". The
            // white marble plaza round the hall and the marble on every dirt path of the exchange are gone again: outside
            // the hall's footprint (floor and wall ring, below) the exchange keeps its own pristine ground.

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
                // The floor and the wall ring under the walls and towers: the ring kept its grass and earth, which showed as
                // a green and brown strip along the facades and beside every entrance (owner 2026-09-26: "the grass and dirt
                // is still visible outside the west entrance ... on all sides entrances").
                ground.overlayId = MARBLE_OVERLAY
                ground.overlayShape = 0
                ground.overlayRotation = 0
                paved++
                ground.flags = ground.flags and BLOCKED.inv()
                unbridge(x, z)
                // The upper floor: one full marble floor over the whole hall, and through the upper doorways (above the
                // ground floor's) out onto the balconies. Roof removed when standing on it, so the roof hides upstairs too.
                if (floor || doorway) {
                    upper.overlayId = MARBLE_OVERLAY
                    upstairs++
                }
                // Selective roof removal (client Static276/Static409: the roof hides only while the player's own tile carries
                // flag 4, then flood-fills over the connected flag-4 tiles). "Inside" is exactly the floor: the hall's walls
                // stand on the inner edge of the ring tiles (home_decor.txt: west rot 2, east rot 0, south rot 1, north
                // rot 3), so a ring tile - threshold included - lies outside the wall, and a flagged ring or portico made a
                // player outside hide the whole roof and upper storey (owner 2026-09-27, fotos mansion_bug1/3/6/7). The
                // flood still reaches the ring as its edge; the ring's upper levels carry ZERO_LEVEL (below) and stay drawn.
                // Upstairs only the doorways lead out (onto the balconies); the rest of the ring is blocked.
                upper.flags = if (floor) REMOVE_ROOF else if (doorway) 0 else BLOCKED
                if (floor) {
                    ground.flags = ground.flags or REMOVE_ROOF
                    roofed++
                } else {
                    ground.flags = ground.flags and REMOVE_ROOF.inv()
                }
            }

            // The porticoes under the balconies are outside: no roof flag there (it used to run three tiles out from every
            // door, so a player in front of the mansion hid its roof, fotos mansion_bug6 vs mansion_bug7). The upper
            // storey's walls stay drawn from inside (ZERO_LEVEL), so the balconies sit behind them as real outside space.
            // The balconies: marble floors on level 1 outside the three doorways, carried by the porticoes' pillars.
            BALCONIES.forEach { b ->
                for (x in b.xs) for (z in b.zs) {
                    val upper = tile(1, x, z)
                    upper.overlayId = MARBLE_OVERLAY
                    upper.overlayShape = 0
                    upper.overlayRotation = 0
                    // A wall-ring tile beside the doorway keeps its storey wall, so it stays closed to walking.
                    // A balcony is outside: standing on or under it shows the roof. From inside the ground floor it hides
                    // like the roof, upper doorway included (owner 2026-09-27: "elke balkon kan ik vanaf de begane grond
                    // doorheen kijken" - a floating balcony floor seen through the upper doorway) - BELOW_BUILDING.
                    val ringWall = (x in MIN_X..MAX_X && z in MIN_Z..MAX_Z) && (upper.flags and BLOCKED != 0)
                    upper.flags = if (ringWall) BLOCKED else BELOW_BUILDING
                    upstairs++
                }
            }
            // The Death's Domain entrance, laid exactly like its Ferox Enclave original (3125,3630 rotation 0, OSRS 39637):
            // the tomb's back against a wall and its sunken steps shown through overlay 61 (stored; def 60 = magenta, not
            // drawn: a hole in the floor), with the front row shaped (6) and its two neighbours cut (9) like at Ferox.
            // Here it is turned 180 degrees - back on the hall's south wall, door onto the gold rug.
            layTombFloor(tiles, rx, rz, DEATHS_DOMAIN_X, DEATHS_DOMAIN_Z)

            // The grass strip round the mansion (owner 2026-09-27, foto jj.png: "the mansion outside still has the low quality
            // grass ... put something else", chosen: dark granite with a gold trim). It is the exchange's unpaved ground
            // (underlay only) between the wall ring and the brown ring road (overlay 188): flood-filled from the ring over
            // tiles without an overlay, inside a box that the fill must never reach. The row touching the wall ring is gold.
            val band = HashSet<Pair<Int, Int>>()
            val queue = ArrayDeque<Pair<Int, Int>>()
            fun footprint(x: Int, z: Int) = x in MIN_X..MAX_X && z in MIN_Z..MAX_Z
            for (x in MIN_X - 1..MAX_X + 1) for (z in MIN_Z - 1..MAX_Z + 1) if (!footprint(x, z)) queue += x to z
            while (queue.isNotEmpty()) {
                val (x, z) = queue.removeFirst()
                if (footprint(x, z) || (x to z) in band || tile(0, x, z).overlayId != 0) continue
                check(x in BAND_BOX_X && z in BAND_BOX_Z) { "grass band leaks out of the exchange centre at $x,$z" }
                band += x to z
                listOf(x - 1 to z, x + 1 to z, x to z - 1, x to z + 1).forEach { queue += it }
            }
            // One granite band, no trim row: the "gold trim" overlay 157 (FloType 156) carries texture 61, which renders as
            // orange lava-like strips along the walls (owner 2026-09-27, fotos mansion_bug1 / mansion_bug3).
            band.forEach { (x, z) ->
                val ground = tile(0, x, z)
                ground.overlayId = GRANITE_OVERLAY
                ground.overlayShape = 0
                ground.overlayRotation = 0
            }
            check(band.size in 100..1200) { "grass band has ${band.size} tiles" }

            // The dirt path south of the south entrance, between the grey paving blocks (owner 2026-09-27, foto d.png and
            // his tags 3164-3165, 3478-3482): Falador square cobbles instead of the brown ring road overlay.
            BRICK_PATH.forEach { (x, z) ->
                val ground = tile(0, x, z)
                check((ground.overlayId and 0xFF) == DIRT_ROAD_OVERLAY) { "brick path tile $x,$z is not the dirt road (overlay ${ground.overlayId})" }
                ground.overlayId = BRICK_OVERLAY
                ground.overlayShape = 0
                ground.overlayRotation = 0
            }
            val repaved = band + BRICK_PATH

            // See-through walls (owner 2026-09-27, fotos pp.png / wr.png: "I can see through the walls everywhere, first and
            // second floor"). The client's selective roof removal (Static409 flood fill over flag-4 tiles) hides every level
            // above the player's on the whole footprint - wall ring included - unless that tile carries ZERO_LEVEL (8).
            // Inside on the ground floor the upper-storey walls vanished (one-storey walls to look over, beside full-height
            // turrets); on the upper floor the parapet above vanished and the walls looked cut open. The wall ring keeps
            // its upper walls and parapet always drawn, as the Grand Exchange's own canopy did with the same flag; the
            // floor inside still clears above the player. The upper doorways are the exception (below).
            for (x in MIN_X..MAX_X) for (z in MIN_Z..MAX_Z) {
                val ring = x == MIN_X || x == MAX_X || z == MIN_Z || z == MAX_Z
                if (!ring) continue
                for (level in 1..3) {
                    // The upper doorways hide with their balcony instead (BELOW_BUILDING).
                    if (level == 1 && tile(1, x, z).flags == BELOW_BUILDING) continue
                    tile(level, x, z).flags = tile(level, x, z).flags or ZERO_LEVEL
                }
            }

            // The corner turrets (62766, home_decor.txt) are the 2 x 2 Falador turret drawn 1.375x wide, so the model hangs
            // about 0.4 tile over each side of its 2 x 2 collision, and players stood inside the tower with their weapon
            // through the stone (owner 2026-09-27, foto dde.png). The tiles along each side of a turret are blocked on
            // both storeys; the diagonal ones are clear of the round tower.
            TURRETS.forEach { (tx, tz) ->
                val sides = (tz..tz + 1).flatMap { z -> listOf(tx - 1 to z, tx + 2 to z) } + (tx..tx + 1).flatMap { x -> listOf(x to tz - 1, x to tz + 2) }
                sides.forEach { (x, z) -> for (level in 0..1) tile(level, x, z).flags = tile(level, x, z).flags or BLOCKED }
            }

            // The Skilling Basement's way down needs its dark opening in this floor (after the paving above).
            AfkBasementMapTool.cutEntrance(tiles, rx, rz)
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
            // Floor decorations (dirt decals 46976, grass tufts) on the repaved granite band and brick path go too.
            val onRepaved = { l: Rev667Loc -> l.plane == 0 && l.type == 22 && (rx * 64 + l.localX to rz * 64 + l.localZ) in repaved }
            val removed = locs.filter { inHall(it) && (it.id in REMOVED_IDS || it.type == 22 && it.id != KEPT_MARKER) || onRepaved(it) }
            removed.groupBy { it.id }.forEach { (id, list) -> println("REMOVE $id x${list.size}") }
            removed.filter { !inHall(it) }.forEach { println("REMOVE_REPAVED ${it.id} at ${rx * 64 + it.localX},${rz * 64 + it.localZ}") }
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
            // In the map, like at Ferox, so the minimap draws its Death's Office icon (map element 1108 on loc 62430); a
            // server-spawned copy never reaches the minimap.
            // The Skilling Basement's way down and its two torches (AfkBasementMapTool) belong to this loc file too, so a run
            // of this tool never drops them.
            val kept =
                swappedLocs.filterNot { it.id == DEATHS_DOMAIN_LOC || it.id == AfkBasementMapTool.ENTRANCE_LOC } +
                    Rev667Loc(DEATHS_DOMAIN_LOC, DEATHS_DOMAIN_X - rx * 64, DEATHS_DOMAIN_Z - rz * 64, 0, 10, DEATHS_DOMAIN_ROT) +
                    Rev667Loc(AfkBasementMapTool.ENTRANCE_LOC, AfkBasementMapTool.ENTRANCE_X - rx * 64, AfkBasementMapTool.ENTRANCE_Z - rz * 64, 0, 10, AfkBasementMapTool.ENTRANCE_ROTATION) +
                    AfkBasementMapTool.ENTRANCE_TORCHES.map { (x, z) -> Rev667Loc(AfkBasementMapTool.STANDING_TORCH, x - rx * 64, z - rz * 64, 0, 10, 0) } +
                    // The casino's one minimap star, on the upper floor (a map-file loc: server-spawned ones never reach the minimap).
                    Rev667Loc(RoyalHallLocTool.CASINO_MARKER, RoyalHallLocTool.CASINO_MARKER_X - rx * 64, RoyalHallLocTool.CASINO_MARKER_Z - rz * 64, 1, 22, 0)
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
