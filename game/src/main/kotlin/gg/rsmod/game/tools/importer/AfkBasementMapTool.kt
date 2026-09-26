package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Builds the Skilling Basement "Guild Cellar" under the Grand Exchange hall (owner 2026-09-26, design
 * `C:/RSPS/foto/Skilling Basement - Guild Cellar@1x.png`). The square [MAP_NAME] / [LOC_NAME] (46,95) is our own - its
 * eight neighbours are empty, which the plan checks - so nothing else is drawn around it.
 *
 * The room is 24 x 18 tiles ([ROOM_X] x [ROOM_Z]), walled like the Lumbridge castle cellar (3208-3219, 9615-9624: wall
 * 32902 on the tile outside the room, its diagonal corner, the rock backing 32904 / 32906 behind it) and floored with
 * real map floors: the cellar's dark stone, a red carpet runner, garden grass with a pond (river water), the Varrock
 * houses' wooden planks and the Varrock palace's stone slabs. Zones (south = the stairs):
 *  - centre: the carpet from the stairs to the north strip, stone pillars either side, two chandeliers;
 *  - west "Gathering" (garden): tree, pond with a fishing spot npc ([FISHING_SPOT_NPC], placed by the server), rocks,
 *    herb patch, bushes with a box trap;
 *  - east "Artisan" (planks): range, fire pit, furnace + anvil on slabs, spinning and potter's wheels, fletching
 *    workbench, herblore table, construction workbench, the air altar in the south-east corner;
 *  - north "Support": a balance beam on a mat, three market stalls, two 78 house banners on the north wall.
 * Every station is a copy of a cache loc (same models and size) under its own id ([FIRST_STATION_LOC]..) with only its
 * skill's option ([Station.option]); nothing else in the world uses these ids, so the world's own trees, rocks and
 * stalls keep their normal handlers. The server's AfkArea holds the same ids and tiles; a plugin test pins them here.
 *
 * The way down is placed in the hall's own square (l49_54, encrypted): [ENTRANCE_LOC], a copy of the dungeon stairs 55
 * with the dungeon mapelement ([DUNGEON_MAP_ELEMENT]) so the minimap shows the dungeon icon on exactly this one loc,
 * and a standing torch either side.
 *
 * Usage: `java -cp <game classes + lib> gg.rsmod.game.tools.importer.AfkBasementMapTool probe <locId...>|elements|plan|apply`
 */
object AfkBasementMapTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val XTEAS_FILE = "C:/RSPS/game/game/data/xteas/xteas.json"

    const val SQUARE_X = 46
    const val SQUARE_Z = 95
    const val REGION_ID = (SQUARE_X shl 8) or SQUARE_Z
    const val MAP_NAME = "m${SQUARE_X}_$SQUARE_Z"
    const val LOC_NAME = "l${SQUARE_X}_$SQUARE_Z"
    const val BASE_X = SQUARE_X * 64
    const val BASE_Z = SQUARE_Z * 64

    /** Room floor, local tiles inclusive: x 20..43, z 22..39 (world 2964..2987, 6102..6119). */
    val ROOM_X = 20..43
    val ROOM_Z = 22..39

    // Floors as the map file stores them (definition id + 1), each copied from a real tile (Rev667RegionProbeTool tile).
    /** Lumbridge castle cellar 3212,9620: overlay 75 = FloType 74 (dark grey stone, texture 600). */
    const val FLOOR_STONE = 75

    /** Overlay 13 = FloType 12 (red 993333 on the carpet texture 301). */
    const val FLOOR_CARPET = 13

    /** Varrock house 3222,3395: overlay 98 = FloType 97 (wooden planks, texture 492). */
    const val FLOOR_WOOD = 98

    /** Varrock palace 3212,3473: overlay 103 = FloType 102 (stone slabs, texture 505). */
    const val FLOOR_SLAB = 103

    /** Lumbridge river 3240,3232: overlay 112 = FloType 111 (water, texture 669), blocked like the river. */
    const val FLOOR_WATER = 112

    /** Overlay 71 = FloType 70 (brown a06947 on the carpet texture 301): the agility mat. */
    const val FLOOR_MAT = 71

    /** Lumbridge lawn 3232,3224: underlay 161 = FloType 160 (grass, texture 917), no overlay. */
    const val UNDERLAY_GRASS = 161

    const val WALL = 32902
    const val ROCK = 32904
    const val ROCK_CORNER = 32906
    private const val BLOCKED = 1

    val CARPET_X = 30..33
    val GARDEN_X = 20..27
    val WORKSHOP_X = 36..43
    val SOUTH_Z = 22..34
    val NORTH_Z = 35..39
    val POND_X = 21..23
    val POND_Z = 31..33
    val MAT_X = 22..29
    val MAT_Z = 36..38
    val SLAB_X = 41..43
    val SLAB_Z = 31..34

    /** The basement's way up (a copy of the dungeon stairs 54, 2 x 3, "Climb-up") and its south-west tile. */
    const val STAIRS_UP_SOURCE = 54
    const val STAIRS_LX = 31
    const val STAIRS_LZ = 22

    /** The fishing spot npc copy the server spawns in the middle of the pond (NpcCopy of 327, Net / Bait). */
    const val FISHING_SPOT_NPC = 14483
    const val FISHING_SPOT_SOURCE = 327
    const val FISHING_LX = 22
    const val FISHING_LZ = 32

    /** The Survival Expert's post, east of the stairs. */
    const val EXPERT_LX = 35
    const val EXPERT_LZ = 23

    /**
     * A skill station: a copy of cache loc [source] at local ([lx], [lz]) with [rotation], its first option [option] and
     * the others removed. A station may be placed on more tiles ([more]); every tile trains the same skill.
     */
    class Station(val skill: String, val source: Int, val lx: Int, val lz: Int, val rotation: Int, val option: String, val more: List<Pair<Int, Int>> = emptyList())

    /** First loc id the station copies take ([STATIONS] order); the plan checks they are free or already these copies. */
    const val FIRST_STATION_LOC = 62870

    val STATIONS =
        listOf(
            // west: Gathering
            Station("Woodcutting", 1276, 25, 33, 0, "Chop down"),
            Station("Mining", 11960, 21, 27, 0, "Mine"),
            Station("Mining", 11957, 23, 26, 0, "Mine"),
            Station("Farming", 18819, 25, 26, 0, "Rake"),
            Station("Hunter", 19187, 24, 23, 0, "Check"),
            // east: Artisan
            Station("Cooking", 114, 36, 33, 0, "Cook"),
            Station("Firemaking", 38827, 38, 33, 0, "Tend"),
            Station("Smithing", 11666, 42, 32, 0, "Smelt"),
            Station("Smithing", 2783, 41, 32, 0, "Smith"),
            Station("Crafting", 2644, 36, 29, 0, "Spin"),
            Station("Crafting", 2642, 38, 29, 0, "Form"),
            Station("Fletching", 540, 40, 29, 0, "Fletch"),
            Station("Herblore", 12280, 42, 29, 0, "Mix"),
            Station("Construction", 13704, 36, 25, 0, "Build"),
            Station("Runecrafting", 2478, 41, 23, 0, "Craft-rune"),
            // north: Support
            Station("Agility", 43527, 23, 37, 0, "Balance", more = (24..28).map { it to 37 }),
            Station("Thieving", 34384, 35, 36, 0, "Steal-from"),
            Station("Thieving", 34383, 38, 36, 0, "Steal-from"),
            Station("Thieving", 34385, 41, 36, 0, "Steal-from"),
        )

    const val CHANDELIER_LOC = FIRST_STATION_LOC + 19
    const val SIGN_GATHERING = FIRST_STATION_LOC + 20
    const val SIGN_ARTISAN = FIRST_STATION_LOC + 21
    const val SIGN_SUPPORT = FIRST_STATION_LOC + 22
    const val STAIRS_UP_LOC = FIRST_STATION_LOC + 23
    const val ENTRANCE_LOC = FIRST_STATION_LOC + 24

    /** Varrock chandelier (plane 1 over the bank); the copy hangs a plane higher and does not block. */
    const val CHANDELIER_SOURCE = 23933
    const val SIGN_SOURCE = 14869
    const val ENTRANCE_SOURCE = 55

    /** MapElementType 607 (sprite: the skull), the dungeon icon: its marker loc 5118 marks the Stronghold of Security, Edgeville dungeon and more (`elements`, sprite sheet). */
    const val DUNGEON_MAP_ELEMENT = 607

    /** The hall (square 49,54) tile of the way down, where the marble spiral staircase stood (AfkArea.HALL_STAIRS). */
    const val HALL_SQUARE_X = 49
    const val HALL_SQUARE_Z = 54
    const val ENTRANCE_X = 3160
    const val ENTRANCE_Z = 3487
    const val ENTRANCE_ROTATION = 0
    val ENTRANCE_TORCHES = listOf(3159 to 3488, 3162 to 3488)

    const val STANDING_TORCH = 724
    const val WALL_TORCH = 31145
    const val PILLAR = 32903
    const val BANNER = 62749
    const val BARREL = 15674
    const val SHELVES = 32284
    const val BOOKCASE = 380
    const val BUSH = 36782

    /** Decoration: id, local x, local z, type, rotation. */
    class Place(val id: Int, val lx: Int, val lz: Int, val type: Int = 10, val rotation: Int = 0)

    val DECOR: List<Place> =
        buildList {
            // pillars either side of the carpet
            for (lz in listOf(24, 27, 30, 33)) {
                add(Place(PILLAR, 29, lz))
                add(Place(PILLAR, 34, lz))
            }
            add(Place(CHANDELIER_LOC, 31, 32))
            add(Place(CHANDELIER_LOC, 31, 27))
            // torches: either side of the stairs, then every 3-4 tiles on the walls (wall decoration on the wall's edge)
            add(Place(STANDING_TORCH, 30, 22))
            add(Place(STANDING_TORCH, 33, 22))
            listOf(24, 28, 32, 36).forEach { add(Place(WALL_TORCH, 20, it, 4, 0)) }
            listOf(26, 30, 37).forEach { add(Place(WALL_TORCH, 43, it, 4, 2)) }
            listOf(22, 26, 37).forEach { add(Place(WALL_TORCH, it, 22, 4, 3)) }
            listOf(22, 26, 36, 40).forEach { add(Place(WALL_TORCH, it, 39, 4, 1)) }
            // 78 house banners on the north wall, at the carpet's head (62749 rot 0: back north)
            add(Place(BANNER, 30, 39, 4, 0))
            add(Place(BANNER, 33, 39, 4, 0))
            // zone signs
            add(Place(SIGN_GATHERING, 27, 34))
            add(Place(SIGN_ARTISAN, 37, 34))
            add(Place(SIGN_SUPPORT, 32, 36))
            // garden: bushes round the box trap, a barrel in the corner
            add(Place(BUSH, 21, 24))
            add(Place(BUSH, 22, 22))
            add(Place(BUSH, 26, 24))
            add(Place(BUSH, 27, 22))
            add(Place(BARREL, 20, 34))
            // workshop: barrels, shelves, a bookcase
            add(Place(BARREL, 38, 22))
            add(Place(BARREL, 39, 22))
            add(Place(SHELVES, 38, 25))
            add(Place(SHELVES, 39, 25))
            add(Place(BOOKCASE, 43, 26, 10, 2))
            // north strip corners
            add(Place(BARREL, 20, 38))
            add(Place(BARREL, 20, 39))
            add(Place(BARREL, 43, 39))
            add(Place(SHELVES, 43, 38))
        }

    @JvmStatic
    fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            "probe" -> probe(args.drop(1).map { it.toInt() })
            "elements" -> elements()
            "plan" -> run(apply = false)
            "apply" -> run(apply = true)
            else -> error("Usage: probe <locId...> | elements | plan | apply")
        }
    }

    private fun locBytes(library: CacheLibrary, id: Int): ByteArray? = library.data(Rev667RegionProbeTool.LOC_INDEX, id ushr 8, id and 0xFF)

    private fun locType(library: CacheLibrary, id: Int): Rev667LocType? = locBytes(library, id)?.let { Rev667LocType.decode(id, it) }

    fun probe(ids: List<Int>) {
        val library = CacheLibrary(GAME_CACHE)
        try {
            ids.forEach { id ->
                val def = locType(library, id)
                if (def == null) println("$id ABSENT") else println("$id '${def.name}' size=${def.sizeX}x${def.sizeZ} shapes=${def.modelsByShape.keys} options=${def.options.toList()} mapelement=${def.mapElement}")
            }
        } finally {
            library.close()
        }
    }

    /** Every mapelement a loc carries, with how many locs carry it and their names: finds the dungeon icon. */
    fun elements() {
        val library = CacheLibrary(GAME_CACHE)
        try {
            val index = library.index(Rev667RegionProbeTool.LOC_INDEX)
            val byElement = sortedMapOf<Int, MutableList<String>>()
            index.archiveIds().forEach { group ->
                val archive = library.index(Rev667RegionProbeTool.LOC_INDEX).archive(group) ?: return@forEach
                archive.fileIds().forEach { file ->
                    val id = (group shl 8) or file
                    val def = runCatching { locType(library, id) }.getOrNull() ?: return@forEach
                    if (def.mapElement >= 0) byElement.getOrPut(def.mapElement) { mutableListOf() }.add("$id:${def.name}")
                }
            }
            val types = library.index(Rev667RegionProbeTool.CONFIG_INDEX).archive(36)!!
            types.fileIds().forEach { file ->
                val data = library.data(Rev667RegionProbeTool.CONFIG_INDEX, 36, file) ?: return@forEach
                val text = String(data, Charsets.ISO_8859_1).split(Regex("[^\\x20-\\x7E]+")).filter { it.length >= 4 }
                val sprite = runCatching { FeroxMapSpriteProbeTool.mapElementSprite(data) }.getOrDefault(-1)
                println("ELEMENT $file sprite=$sprite text=$text locs=${byElement[file]?.take(4)}")
            }
        } finally {
            library.close()
        }
    }

    private fun run(apply: Boolean) {
        val mutations = plan()
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        println(preflight.joinToString("\n"))
        val errors = tx.blockingErrors(preflight)
        check(errors.isEmpty()) { errors.joinToString("\n") }
        if (!apply) {
            println("PLAN_ONLY transaction=${tx.id} mutations=${mutations.size}")
            return
        }
        val result = tx.apply(preflight)
        val problems = tx.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("AFK_BASEMENT transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
        zeroXteaKey()
    }

    /** The floor overlay of a room tile, or 0 for the garden's grass (underlay only). */
    fun floor(lx: Int, lz: Int): Int =
        when {
            lx in POND_X && lz in POND_Z -> FLOOR_WATER
            lx in CARPET_X && lz <= SOUTH_Z.last -> FLOOR_CARPET
            lz in NORTH_Z -> if (lx in MAT_X && lz in MAT_Z) FLOOR_MAT else FLOOR_STONE
            lx in GARDEN_X -> 0
            lx in SLAB_X && lz in SLAB_Z -> FLOOR_SLAB
            lx in WORKSHOP_X -> FLOOR_WOOD
            else -> FLOOR_STONE
        }

    fun plan(): List<CacheMutation> {
        val library = CacheLibrary(GAME_CACHE)
        try {
            val maps = library.index(Rev667RegionProbeTool.MAP_INDEX)
            for (dx in -1..1) for (dz in -1..1) {
                val x = SQUARE_X + dx
                val z = SQUARE_Z + dz
                if (dx == 0 && dz == 0) continue
                check(maps.archive("m${x}_$z") == null && maps.archive("l${x}_$z") == null) { "neighbour square $x,$z is not empty" }
            }
            val existingMap = maps.archive(MAP_NAME)
            val existingLocs = maps.archive(LOC_NAME)
            val mapGroup = existingMap?.id ?: ((maps.archiveIds().maxOrNull() ?: -1) + 1)
            val locGroup = existingLocs?.id ?: (mapGroup + 1)

            val tiles = Rev667TileMap()
            val ringX = ROOM_X.first - 2..ROOM_X.last + 2
            val ringZ = ROOM_Z.first - 2..ROOM_Z.last + 2
            for (lx in 0 until 64) for (lz in 0 until 64) {
                val t = tiles.tiles[0][lx][lz]
                t.height = 0
                if (lx in ROOM_X && lz in ROOM_Z) {
                    val overlay = floor(lx, lz)
                    t.overlayId = overlay
                    t.overlayShape = 0
                    t.overlayRotation = 0
                    t.underlayId = if (overlay == 0) UNDERLAY_GRASS else 0
                    if (overlay == FLOOR_WATER) t.flags = BLOCKED
                } else if (lx in ringX && lz in ringZ) {
                    // under the walls and the rock backing: the same stone, never walkable
                    t.overlayId = FLOOR_STONE
                    t.flags = BLOCKED
                } else {
                    t.flags = BLOCKED
                }
            }

            val locs = mutableListOf<Rev667Loc>()
            // Walls on the tiles just outside the room (as in the Lumbridge cellar), diagonal corner pieces, rock backing.
            for (lx in ROOM_X) {
                locs += Rev667Loc(WALL, lx, ROOM_Z.first - 1, 0, 0, 1)
                locs += Rev667Loc(WALL, lx, ROOM_Z.last + 1, 0, 0, 3)
            }
            for (lz in ROOM_Z) {
                locs += Rev667Loc(WALL, ROOM_X.first - 1, lz, 0, 0, 2)
                locs += Rev667Loc(WALL, ROOM_X.last + 1, lz, 0, 0, 0)
            }
            locs += Rev667Loc(WALL, ROOM_X.first - 1, ROOM_Z.first - 1, 0, 1, 1)
            locs += Rev667Loc(WALL, ROOM_X.last + 1, ROOM_Z.first - 1, 0, 1, 0)
            locs += Rev667Loc(WALL, ROOM_X.last + 1, ROOM_Z.last + 1, 0, 1, 3)
            locs += Rev667Loc(WALL, ROOM_X.first - 1, ROOM_Z.last + 1, 0, 1, 2)
            for (lx in ROOM_X.first - 1..ROOM_X.last + 1) {
                locs += Rev667Loc(ROCK, lx, ROOM_Z.first - 2, 0, 10, 1)
                locs += Rev667Loc(ROCK, lx, ROOM_Z.last + 2, 0, 10, 3)
            }
            for (lz in ROOM_Z.first - 1..ROOM_Z.last + 1) {
                locs += Rev667Loc(ROCK, ROOM_X.first - 2, lz, 0, 10, 2)
                locs += Rev667Loc(ROCK, ROOM_X.last + 2, lz, 0, 10, 0)
            }
            locs += Rev667Loc(ROCK_CORNER, ROOM_X.first - 2, ROOM_Z.first - 2, 0, 10, 1)
            locs += Rev667Loc(ROCK_CORNER, ROOM_X.last + 2, ROOM_Z.first - 2, 0, 10, 0)
            locs += Rev667Loc(ROCK_CORNER, ROOM_X.last + 2, ROOM_Z.last + 2, 0, 10, 3)
            locs += Rev667Loc(ROCK_CORNER, ROOM_X.first - 2, ROOM_Z.last + 2, 0, 10, 2)

            val occupied = mutableSetOf<Pair<Int, Int>>()
            POND_X.forEach { x -> POND_Z.forEach { z -> occupied += x to z } }
            occupied += (EXPERT_LX to EXPERT_LZ)
            fun claim(id: Int, lx: Int, lz: Int, rotation: Int, type: Int, sizeFrom: Int) {
                if (type != 10) {
                    check(lx in ROOM_X && lz in ROOM_Z) { "loc $id at $lx,$lz leaves the room" }
                    locs += Rev667Loc(id, lx, lz, 0, type, rotation)
                    return
                }
                val def = requireNotNull(locType(library, sizeFrom)) { "loc $sizeFrom missing" }
                val w = if (rotation and 1 == 1) def.sizeZ else def.sizeX
                val d = if (rotation and 1 == 1) def.sizeX else def.sizeZ
                for (x in lx until lx + w) for (z in lz until lz + d) {
                    check(x in ROOM_X && z in ROOM_Z) { "loc $id at $lx,$lz leaves the room" }
                    check(occupied.add(x to z)) { "loc $id at $lx,$lz overlaps $x,$z" }
                }
                locs += Rev667Loc(id, lx, lz, 0, type, rotation)
            }

            val copies = mutableListOf<CacheMutation>()
            fun copy(copyId: Int, source: Int, label: String, patch: (ByteArrayOutputStream) -> Unit) {
                val base = requireNotNull(locBytes(library, source)) { "loc $source missing" }
                check(base.last().toInt() == 0) { "loc $source does not end with opcode 0" }
                val out = ByteArrayOutputStream()
                out.write(base, 0, base.size - 1)
                patch(out)
                out.write(0)
                val bytes = out.toByteArray()
                val current = locBytes(library, copyId)
                check(current == null || current.contentEquals(bytes) || copyId in REBUILT) { "loc id $copyId is taken by something else" }
                if (current == null || !current.contentEquals(bytes)) {
                    copies += CacheMutation(Rev667RegionProbeTool.LOC_INDEX, copyId ushr 8, copyId and 0xFF, bytes, "Skilling basement: $label (copy of loc $source -> $copyId)", current?.let { CacheItemProbeTool.sha1(it) })
                }
            }

            claim(STAIRS_UP_LOC, STAIRS_LX, STAIRS_LZ, 0, 10, STAIRS_UP_SOURCE)
            copy(STAIRS_UP_LOC, STAIRS_UP_SOURCE, "stairs up") { }
            STATIONS.forEachIndexed { index, station ->
                val copyId = FIRST_STATION_LOC + index
                copy(copyId, station.source, "${station.skill} station '${station.option}'") { stationOptions(it, station.option) }
                claim(copyId, station.lx, station.lz, station.rotation, 10, station.source)
                station.more.forEach { (x, z) -> claim(copyId, x, z, station.rotation, 10, station.source) }
            }
            copy(CHANDELIER_LOC, CHANDELIER_SOURCE, "chandelier, a plane up, walk-through") { out ->
                out.write(17) // not solid
                out.write(71) // y offset, signed short << 2: -60 << 2 = -240 = one plane up
                out.write((-60 shr 8) and 0xFF)
                out.write(-60 and 0xFF)
            }
            listOf(SIGN_GATHERING to "Gathering", SIGN_ARTISAN to "Artisan", SIGN_SUPPORT to "Support").forEach { (id, text) ->
                copy(id, SIGN_SOURCE, "sign '$text'") { out ->
                    out.write(2)
                    out.write(text.toByteArray(Charsets.ISO_8859_1))
                    out.write(0)
                }
            }
            DECOR.forEach { place ->
                val sizeFrom =
                    when (place.id) {
                        CHANDELIER_LOC -> CHANDELIER_SOURCE
                        SIGN_GATHERING, SIGN_ARTISAN, SIGN_SUPPORT -> SIGN_SOURCE
                        else -> place.id
                    }
                if (place.id == CHANDELIER_LOC) {
                    check(place.lx in ROOM_X && place.lz in ROOM_Z)
                    locs += Rev667Loc(place.id, place.lx, place.lz, 0, 10, place.rotation)
                } else {
                    claim(place.id, place.lx, place.lz, place.rotation, place.type, sizeFrom)
                }
            }

            // The way down in the hall's square: the entrance copy with the dungeon icon and a torch either side.
            check(DUNGEON_MAP_ELEMENT >= 0) { "DUNGEON_MAP_ELEMENT not chosen yet (run 'elements')" }
            copy(ENTRANCE_LOC, ENTRANCE_SOURCE, "hall entrance with the dungeon mapelement") { out ->
                out.write(107)
                out.write(DUNGEON_MAP_ELEMENT ushr 8)
                out.write(DUNGEON_MAP_ELEMENT and 0xFF)
            }
            val hallMutation = hallEntrance(library)

            // The pond's fishing spot: a byte copy of npc 327 (Fishing spot, Net / Bait) under its own id.
            val npcSource = requireNotNull(library.data(OsrsNpcImportTool.INDEX_NPC, FISHING_SPOT_SOURCE ushr 7, FISHING_SPOT_SOURCE and 0x7F)) { "npc $FISHING_SPOT_SOURCE missing" }
            val npcCurrent = library.data(OsrsNpcImportTool.INDEX_NPC, FISHING_SPOT_NPC ushr 7, FISHING_SPOT_NPC and 0x7F)
            check(npcCurrent == null || npcCurrent.contentEquals(npcSource)) { "npc id $FISHING_SPOT_NPC is taken by something else" }
            if (npcCurrent == null) {
                copies += CacheMutation(OsrsNpcImportTool.INDEX_NPC, FISHING_SPOT_NPC ushr 7, FISHING_SPOT_NPC and 0x7F, npcSource, "Skilling basement fishing spot (copy of npc $FISHING_SPOT_SOURCE -> $FISHING_SPOT_NPC)")
            }

            val mapBytes = Rev667TileCodec.encode(tiles)
            val sorted = locs.sortedWith(compareBy({ it.id }, { it.packedPosition }, { it.type }))
            val locBytes = Rev667LocCodec.encode(sorted)
            check(Rev667LocCodec.decode(locBytes).size == sorted.size) { "loc round trip" }
            println("PLAN $MAP_NAME=$mapGroup $LOC_NAME=$locGroup locs=${sorted.size} copies=${copies.size}")
            return copies + listOfNotNull(hallMutation) + listOf(
                CacheMutation(Rev667RegionProbeTool.MAP_INDEX, mapGroup, 0, mapBytes, "Skilling basement tiles $MAP_NAME", expectedCurrentSha1 = existingMap?.let { library.data(Rev667RegionProbeTool.MAP_INDEX, mapGroup, 0) }?.let { CacheItemProbeTool.sha1(it) }, groupName = MAP_NAME),
                CacheMutation(Rev667RegionProbeTool.MAP_INDEX, locGroup, 0, locBytes, "Skilling basement locs $LOC_NAME (unencrypted)", expectedCurrentSha1 = existingLocs?.let { library.data(Rev667RegionProbeTool.MAP_INDEX, locGroup, 0) }?.let { CacheItemProbeTool.sha1(it) }, groupName = LOC_NAME),
            )
        } finally {
            library.close()
        }
    }

    /** Ids this tool may overwrite when their bytes change between runs (its own copies). */
    private val REBUILT = (FIRST_STATION_LOC..ENTRANCE_LOC).toSet()

    /** Adds the entrance and its torches to the hall square's loc file; null when they are already there. */
    private fun hallEntrance(library: CacheLibrary): CacheMutation? {
        val regionId = (HALL_SQUARE_X shl 8) or HALL_SQUARE_Z
        val key = Rev667RegionProbeTool.loadKeys(File(XTEAS_FILE))[regionId]
        val name = "l${HALL_SQUARE_X}_$HALL_SQUARE_Z"
        val archive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive(name, key) ?: error("$name missing")
        val bytes = archive.file(0)!!.data!!
        val locs = Rev667LocCodec.decode(bytes)
        check(Rev667LocCodec.encode(locs).contentEquals(bytes)) { "$name round-trip failed" }
        val bx = HALL_SQUARE_X * 64
        val bz = HALL_SQUARE_Z * 64
        val wanted =
            listOf(Rev667Loc(ENTRANCE_LOC, ENTRANCE_X - bx, ENTRANCE_Z - bz, 0, 10, ENTRANCE_ROTATION)) +
                ENTRANCE_TORCHES.map { (x, z) -> Rev667Loc(STANDING_TORCH, x - bx, z - bz, 0, 10, 0) }
        fun same(a: Rev667Loc, b: Rev667Loc) = a.id == b.id && a.localX == b.localX && a.localZ == b.localZ && a.plane == b.plane && a.type == b.type && a.rotation == b.rotation
        // Earlier runs of this tool (other rotation) are replaced; nothing else in the square is touched.
        val kept = locs.filterNot { l -> l.id == ENTRANCE_LOC || (l.id == STANDING_TORCH && ENTRANCE_TORCHES.any { (x, z) -> l.localX == x - bx && l.localZ == z - bz && l.plane == 0 }) }
        if (wanted.all { w -> locs.any { same(it, w) } } && kept.size + wanted.size == locs.size) return null
        val entranceTiles = (0..1).flatMap { dx -> (0..1).map { dz -> (ENTRANCE_X - bx + dx) to (ENTRANCE_Z - bz + dz) } }
        val clash = kept.filter { it.plane == 0 && it.type in 9..11 && (it.localX to it.localZ) in entranceTiles }
        check(clash.isEmpty()) { "hall entrance tiles hold other locs: ${clash.map { it.id }}" }
        val merged = (kept + wanted).sortedWith(compareBy({ it.id }, { it.packedPosition }, { it.type }))
        val newBytes = Rev667LocCodec.encode(merged)
        check(Rev667LocCodec.decode(newBytes).size == merged.size)
        println("HALL $name locs ${locs.size} -> ${merged.size}")
        return CacheMutation(Rev667RegionProbeTool.MAP_INDEX, archive.id, 0, newBytes, "Skilling basement entrance in $name", CacheItemProbeTool.sha1(bytes), xtea = key)
    }

    /** Option 1 [text], options 2-5 "Hidden" (the client drops "Hidden" options), so only the station's action shows. */
    private fun stationOptions(out: ByteArrayOutputStream, text: String) {
        fun option(op: Int, value: String) {
            out.write(op)
            out.write(value.toByteArray(Charsets.ISO_8859_1))
            out.write(0)
        }
        option(30, text)
        (31..34).forEach { option(it, "Hidden") }
    }

    private fun zeroXteaKey() {
        val file = File(XTEAS_FILE)
        val root = JsonParser().parse(file.readText()).asJsonArray
        if (root.none { it.asJsonObject.get("mapsquare").asInt == REGION_ID }) {
            root.add(JsonObject().apply {
                addProperty("mapsquare", REGION_ID)
                add("key", JsonArray().apply { repeat(4) { add(0) } })
            })
            file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(root))
            println("XTEAS zero key added for $REGION_ID")
        }
    }
}
