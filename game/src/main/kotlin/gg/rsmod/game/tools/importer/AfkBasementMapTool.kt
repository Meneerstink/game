package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Builds the AFK skill basement under the Grand Exchange hall (owner 2026-09-26: "BOUW in de mansion een trap naar een
 * kelder met de AFK skill area"). Like [DeathsOfficeMapImportTool] it writes a map square that did not exist - here
 * [MAP_NAME] / [LOC_NAME] (square 46,95; it and its eight neighbours are empty, which the plan checks) - so nothing else is
 * drawn around it and it lies outside the instance allocator (x >= 6400).
 *
 * The square: every tile blocked and without floor (black void), except the [ROOM] floor - the Royal Hall's own white
 * marble (overlay/underlay copied from the hall's floor tile in m49_54) - walled with the hall's wall loc 33868 and lit by
 * nothing but the client's default dungeon light. In it: the hall's white marble spiral staircase bottom (62779, "climb
 * up") and one station per non-combat skill ([STATIONS]; cache-native locs, their normal handlers are overridden by the
 * AFK plugin on these tiles only). The server's afk_area plugin (AfkArea) holds the same coordinates; a plugin test pins
 * them to this tool.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.AfkBasementMapTool probe <locId...>|plan|apply`
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

    /** Room floor, local tiles inclusive: x 16..47, z 20..43 (world 2960..2991, 6100..6123). */
    val ROOM_X = 16..47
    val ROOM_Z = 20..43

    /** The Royal Hall floor tile the marble is copied from (RoyalHallMapTool floor, region 12598 = m49_54). */
    private const val HALL_MAP_NAME = "m49_54"
    private const val HALL_TILE_LX = 3164 - 49 * 64
    private const val HALL_TILE_LZ = 3488 - 54 * 64

    const val WALL = 33868
    const val STAIRS_UP = 62779
    private const val BLOCKED = 1

    /** The spiral staircase's south-west tile (2 x 2), local. */
    const val STAIRS_LX = 31
    const val STAIRS_LZ = 21

    /**
     * A skill station: a copy of cache loc [loc] (same models, name and size; option 1 "Train", every other option
     * hidden, so no normal skill handler can react) at local ([lx], [lz]) with [rotation]. [type] is the placement shape
     * the loc has a model for (10 scenery, 4 wall decoration on the west wall, 22 floor decoration).
     */
    class Station(val skill: String, val loc: Int, val lx: Int, val lz: Int, val rotation: Int, val type: Int = 10)

    /** First loc id the station copies take ([STATIONS] order); the plan checks they are free or already these copies. */
    const val FIRST_STATION_LOC = 62851

    /** Along the west, north and east walls, facing into the room (rotation 1 west wall, 2 north wall, 3 east wall). */
    val STATIONS =
        listOf(
            Station("Cooking", 2728, 18, 24, 1),
            Station("Firemaking", 2732, 18, 29, 1),
            Station("Fishing", 969, 16, 34, 0, type = 4),
            Station("Herblore", 692, 18, 39, 1),
            Station("Woodcutting", 1309, 20, 40, 0),
            Station("Fletching", 540, 25, 41, 0),
            Station("Crafting", 2644, 29, 41, 0),
            Station("Smithing", 2783, 33, 41, 0),
            Station("Mining", 2091, 37, 41, 0),
            Station("Construction", 3231, 41, 41, 0, type = 22),
            Station("Runecrafting", 2478, 43, 38, 3),
            Station("Thieving", 635, 44, 33, 3),
            Station("Agility", 1762, 44, 28, 3),
            Station("Farming", 3972, 43, 23, 3),
            Station("Hunter", 19438, 22, 22, 1),
        )

    @JvmStatic
    fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            "probe" -> probe(args.drop(1).map { it.toInt() })
            "plan" -> run(apply = false)
            "apply" -> run(apply = true)
            else -> error("Usage: probe <locId...> | plan | apply")
        }
    }

    private fun locType(library: CacheLibrary, id: Int): Rev667LocType? =
        library.data(Rev667RegionProbeTool.LOC_INDEX, id ushr 8, id and 0xFF)?.let { Rev667LocType.decode(id, it) }

    fun probe(ids: List<Int>) {
        val library = CacheLibrary(GAME_CACHE)
        try {
            ids.forEach { id ->
                val def = locType(library, id)
                if (def == null) println("$id ABSENT") else println("$id '${def.name}' size=${def.sizeX}x${def.sizeZ} shapes=${def.modelsByShape.keys} options=${def.options.toList()}")
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
        if (!apply) return
        val result = tx.apply(preflight)
        val problems = tx.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("AFK_BASEMENT transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
        zeroXteaKey()
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

            val hall = Rev667TileCodec.decode(requireNotNull(library.data(Rev667RegionProbeTool.MAP_INDEX, requireNotNull(maps.archive(HALL_MAP_NAME)).id, 0)))
            val marble = hall.tiles[0][HALL_TILE_LX][HALL_TILE_LZ]
            check(marble.overlayId != 0) { "hall floor tile has no overlay" }

            val tiles = Rev667TileMap()
            for (lx in 0 until 64) for (lz in 0 until 64) {
                val t = tiles.tiles[0][lx][lz]
                t.height = 0
                if (lx in ROOM_X && lz in ROOM_Z) {
                    t.overlayId = marble.overlayId
                    t.overlayShape = 0
                    t.overlayRotation = 0
                    t.underlayId = marble.underlayId
                } else {
                    t.flags = BLOCKED
                }
            }

            val wall = requireNotNull(locType(library, WALL)) { "wall loc $WALL missing" }
            val cornerShape = 2 in wall.modelsByShape
            val locs = mutableListOf<Rev667Loc>()
            for (lx in ROOM_X) {
                locs += Rev667Loc(WALL, lx, ROOM_Z.first, 0, 0, 3)
                locs += Rev667Loc(WALL, lx, ROOM_Z.last, 0, 0, 1)
            }
            for (lz in ROOM_Z) {
                if (lz == ROOM_Z.first || lz == ROOM_Z.last) continue
                locs += Rev667Loc(WALL, ROOM_X.first, lz, 0, 0, 0)
                locs += Rev667Loc(WALL, ROOM_X.last, lz, 0, 0, 2)
            }
            if (cornerShape) {
                // One loc per tile edge slot: the four corner tiles get the L-shaped corner instead of a straight wall.
                locs.removeAll { (it.localX == ROOM_X.first || it.localX == ROOM_X.last) && (it.localZ == ROOM_Z.first || it.localZ == ROOM_Z.last) }
                locs += Rev667Loc(WALL, ROOM_X.first, ROOM_Z.last, 0, 2, 0)
                locs += Rev667Loc(WALL, ROOM_X.last, ROOM_Z.last, 0, 2, 1)
                locs += Rev667Loc(WALL, ROOM_X.last, ROOM_Z.first, 0, 2, 2)
                locs += Rev667Loc(WALL, ROOM_X.first, ROOM_Z.first, 0, 2, 3)
            }

            val occupied = mutableSetOf<Pair<Int, Int>>()
            fun claim(id: Int, lx: Int, lz: Int, rotation: Int, type: Int, sizeFrom: Int = id) {
                val def = requireNotNull(locType(library, sizeFrom)) { "loc $sizeFrom missing" }
                val w = if (rotation and 1 == 1) def.sizeZ else def.sizeX
                val d = if (rotation and 1 == 1) def.sizeX else def.sizeZ
                for (x in lx until lx + w) for (z in lz until lz + d) {
                    check(x in ROOM_X && z in ROOM_Z) { "loc $id at $lx,$lz leaves the room" }
                    check(occupied.add(x to z)) { "loc $id at $lx,$lz overlaps another station" }
                }
                locs += Rev667Loc(id, lx, lz, 0, type, rotation)
            }
            claim(STAIRS_UP, STAIRS_LX, STAIRS_LZ, 0, 10)
            val copies = mutableListOf<CacheMutation>()
            STATIONS.forEachIndexed { index, station ->
                val copyId = FIRST_STATION_LOC + index
                val base = requireNotNull(library.data(Rev667RegionProbeTool.LOC_INDEX, station.loc ushr 8, station.loc and 0xFF)) { "loc ${station.loc} missing" }
                val copy = trainCopy(base)
                val current = library.data(Rev667RegionProbeTool.LOC_INDEX, copyId ushr 8, copyId and 0xFF)
                check(current == null || current.contentEquals(copy)) { "loc id $copyId is taken by something else" }
                if (current == null) copies += CacheMutation(Rev667RegionProbeTool.LOC_INDEX, copyId ushr 8, copyId and 0xFF, copy, "AFK station ${station.skill}: copy of loc ${station.loc} -> $copyId (Train)")
                if (station.type == 10) claim(copyId, station.lx, station.lz, station.rotation, 10, sizeFrom = station.loc) else locs += Rev667Loc(copyId, station.lx, station.lz, 0, station.type, station.rotation)
            }

            val mapBytes = Rev667TileCodec.encode(tiles)
            val sorted = locs.sortedWith(compareBy({ it.id }, { it.packedPosition }))
            val locBytes = Rev667LocCodec.encode(sorted)
            check(Rev667LocCodec.decode(locBytes).size == sorted.size) { "loc round trip" }
            println("PLAN $MAP_NAME=$mapGroup $LOC_NAME=$locGroup locs=${sorted.size} cornerShape=$cornerShape marble overlay=${marble.overlayId} underlay=${marble.underlayId}")
            return copies + listOf(
                CacheMutation(Rev667RegionProbeTool.MAP_INDEX, mapGroup, 0, mapBytes, "AFK basement tiles $MAP_NAME", expectedCurrentSha1 = existingMap?.let { library.data(Rev667RegionProbeTool.MAP_INDEX, mapGroup, 0) }?.let { CacheItemProbeTool.sha1(it) }, groupName = MAP_NAME),
                CacheMutation(Rev667RegionProbeTool.MAP_INDEX, locGroup, 0, locBytes, "AFK basement locs $LOC_NAME (unencrypted)", expectedCurrentSha1 = existingLocs?.let { library.data(Rev667RegionProbeTool.MAP_INDEX, locGroup, 0) }?.let { CacheItemProbeTool.sha1(it) }, groupName = LOC_NAME),
            )
        } finally {
            library.close()
        }
    }

    /** [base] with option 1 "Train" and options 2-5 "Hidden" (the client drops "Hidden" options), appended before the end. */
    fun trainCopy(base: ByteArray): ByteArray {
        check(base.last().toInt() == 0) { "loc definition does not end with opcode 0" }
        val out = java.io.ByteArrayOutputStream()
        out.write(base, 0, base.size - 1)
        fun option(op: Int, text: String) {
            out.write(op)
            out.write(text.toByteArray(Charsets.ISO_8859_1))
            out.write(0)
        }
        option(30, "Train")
        (31..34).forEach { option(it, "Hidden") }
        out.write(0)
        return out.toByteArray()
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
