package gg.rsmod.plugins.content.activity.cox

import kotlin.random.Random

/**
 * Chambers of Xeric layouts (owner 2026-11-15 14:1x besluit 9a: random rooms from the OSRS layouts; dossier team\dossiers\COX.txt).
 *
 * Sources (two independent, they agree 1-on-1 - COX.txt 'BRONTABEL KAMERS'):
 *  - RuneLite runelite-api InstanceTemplates.java + raids plugin LayoutSolver (the 26 known layout codes, room types, 4x2 grid per floor);
 *  - RSPSApp/tsps Layout.ChambersOfXeric.js (code help only; its rooms/rotations were checked against rsprox OSRS captures + the Wiki).
 * A room is a 4x4-chunk (32x32 tile) block of the cache's raid map. The cache holds three versions of each room side by side at chunk
 * x 408 / 412 / 416: the path turning WEST (left) / going straight / turning EAST (right). The room is copied turned so that it faces the
 * way the path enters it (rotation = index of the entry direction in [N, E, S, W]).
 */
enum class CoxDirection(val dx: Int, val dz: Int) {
    NORTH(0, 1), EAST(1, 0), SOUTH(0, -1), WEST(-1, 0);

    val left: CoxDirection get() = values()[(ordinal + 3) % 4]

    companion object {
        fun of(char: Char): CoxDirection? = when (char) {
            'N' -> NORTH
            'E' -> EAST
            'S' -> SOUTH
            'W' -> WEST
            else -> null
        }
    }
}

enum class CoxRoomKind { START, END, SCAVENGERS, COMBAT, FARMING, PUZZLE }

/** Every room: the cache chunk row (z) it sits on, the plane it is copied from, its kind and the RuneLite raids-plugin name. */
enum class CoxRoom(val chunkZ: Int, val plane: Int, val kind: CoxRoomKind, val displayName: String) {
    FLOOR_END_DOWNSTAIRS(644, 0, CoxRoomKind.END, "End"),
    FLOOR_START_UPSTAIRS(712, 0, CoxRoomKind.START, "Start"),
    RAID_START(648, 0, CoxRoomKind.START, "Start"),
    SMALL_SCAVENGER_RUNT(652, 0, CoxRoomKind.SCAVENGERS, "Scavengers"),
    LIZARDMEN_SHAMAN(656, 0, CoxRoomKind.COMBAT, "Shamans"),
    VASA_NISTIRIO(660, 0, CoxRoomKind.COMBAT, "Vasa"),
    VANGUARD(664, 0, CoxRoomKind.COMBAT, "Vanguards"),
    ICE_DEMON(668, 0, CoxRoomKind.PUZZLE, "Ice Demon"),
    CREATURE_KEEPER(672, 0, CoxRoomKind.PUZZLE, "Thieving"),
    RESOURCES_A(680, 0, CoxRoomKind.FARMING, "Farming"),
    LARGE_SCAVENGER_BEAST(652, 1, CoxRoomKind.SCAVENGERS, "Scavengers"),
    DARK_ALTAR_ROOM(656, 1, CoxRoomKind.COMBAT, "Mystics"),
    TEKTON(660, 1, CoxRoomKind.COMBAT, "Tekton"),
    MUTTADILES(664, 1, CoxRoomKind.COMBAT, "Muttadiles"),
    DEATHLY_ROOM(668, 1, CoxRoomKind.PUZZLE, "Tightrope"),
    RESOURCES_B(680, 1, CoxRoomKind.FARMING, "Farming"),
    GUARDIANS(656, 2, CoxRoomKind.COMBAT, "Guardians"),
    VESPULA(660, 2, CoxRoomKind.COMBAT, "Vespula"),
    CRAB_PUZZLE(668, 2, CoxRoomKind.PUZZLE, "Crabs"),
}

/** One room placed on a floor's grid. [variant] 0/1/2 = west / straight / east version; [sourceChunkZ] differs only for the CM middle end. */
data class CoxLaidRoom(
    val room: CoxRoom,
    val gridX: Int,
    val gridZ: Int,
    val entry: CoxDirection,
    val variant: Int,
    val sourceChunkZ: Int = room.chunkZ,
) {
    val rotation: Int get() = entry.ordinal
    val sourceChunkX: Int get() = CoxLayouts.VARIANT_CHUNK_X[variant]
}

data class CoxLayout(val pattern: String?, val challenge: Boolean, val floors: List<List<CoxLaidRoom>>) {
    /** Size in chunks (rooms are 4x4 chunks); every OSRS layout fits 16x8 so the Olm's chamber fits above it in one 16x16 instance. */
    val widthChunks: Int get() = floors.flatten().maxOf { it.gridX + 1 } * CoxLayouts.ROOM_CHUNKS
    val heightChunks: Int get() = floors.flatten().maxOf { it.gridZ + 1 } * CoxLayouts.ROOM_CHUNKS
}

object CoxLayouts {
    const val ROOM_CHUNKS = 4
    val VARIANT_CHUNK_X = intArrayOf(408, 412, 416)
    /** tsps (rsprox capture 3389): the CM middle floor's end room is the copy at chunk z 640 (hole 32542). */
    const val MIDDLE_END_CHUNK_Z = 640

    val SCAVENGER_ROOMS = listOf(CoxRoom.SMALL_SCAVENGER_RUNT, CoxRoom.LARGE_SCAVENGER_BEAST)
    val FARMING_ROOMS = listOf(CoxRoom.RESOURCES_A, CoxRoom.RESOURCES_B)
    val PUZZLE_ROOMS = listOf(CoxRoom.ICE_DEMON, CoxRoom.CREATURE_KEEPER, CoxRoom.DEATHLY_ROOM, CoxRoom.CRAB_PUZZLE)

    /** OSRS Wiki: the two orders combat rooms come in; a raid takes a run of them from a random start, forward or backward. */
    val BOSS_ROTATIONS = listOf(
        listOf(CoxRoom.TEKTON, CoxRoom.VASA_NISTIRIO, CoxRoom.GUARDIANS, CoxRoom.DARK_ALTAR_ROOM, CoxRoom.LIZARDMEN_SHAMAN,
            CoxRoom.MUTTADILES, CoxRoom.VANGUARD, CoxRoom.VESPULA),
        listOf(CoxRoom.TEKTON, CoxRoom.MUTTADILES, CoxRoom.GUARDIANS, CoxRoom.VESPULA, CoxRoom.LIZARDMEN_SHAMAN,
            CoxRoom.VASA_NISTIRIO, CoxRoom.VANGUARD, CoxRoom.DARK_ALTAR_ROOM),
    )

    /**
     * RuneLite raids plugin LayoutSolver codes: room kinds (# start, * end, S scavengers, C combat, F farming, P puzzle) of floor 1 then
     * floor 2, ' - ', then per room the direction the path moves to enter it. The last two (8-room floors, 'Large') are from rsprox
     * captures 843/3282/3285 (Wiki: five combat rooms and three puzzles).
     */
    val LAYOUT_CODES = listOf(
        "#FSCCP*#PCSCF* - #WNWSWN#ESEENW", "#FSCCS*#PCPSF* - #WSEEEN#WSWNWS", "#FSCPC*#CSCPF* - #WNWWSE#EENWWW",
        "#SCCFC*#PSCSF* - #EEENWW#WSEEEN", "#SCCFP*#CCSPF* - #NESEEN#WSWNWS", "#SCFCP*#CCSPF* - #ESEENW#ESWWNW",
        "#SCFCP*#CSCFS* - #ENEESW#ENWWSW", "#SCFPC*#CSPCF* - #WSWWNE#WSEENE", "#SCFPC*#PCCSF* - #WSEENE#WWWSEE",
        "#SCFPC*#SCPCF* - #NESENE#WSWWNE", "#SCPFC*#CCPSF* - #NWWWSE#WNEESE", "#SCPFC*#CSPCF* - #NEEESW#WWNEEE",
        "#SCPFC*#CSPSF* - #WWSEEE#NWSWWN", "#SCSPF*#CCSPF* - #ESWWNW#ESENES", "#SFCCP*#CSCPF* - #WNEESE#NWSWWN",
        "#SFCCS*#PCPSF* - #ENWWSW#ENESEN", "#SPCFC*#CSPCF* - #WWNEEE#WSWNWS", "#SPCFC*#SCCPF* - #ESENES#WWWNEE",
        "#SPSFP*#CCCSF* - #NWSWWN#ESEENW", "#SCFCP*#CSCPF* - #ENESEN#WWWSEE", "#SCPFC*#PCSCF* - #WNEEES#NWSWNW",
        "#FSPCC*#PSCCF* - #WWWSEE#ENWWSW", "#FSCCP*#PCSCF* - #ENWWWS#NEESEN", "#SCPFC*#CCSSF* - #NEESEN#WSWWNE",
        "#SFCCPC*#PCSCPF* - #WSEENES#WWWNEEE",
        "#SCFCPC*#CPPCSF* - #ESWWNWS#NESENES", "#SCCFPC*#CPCSPF* - #NESEENW#ESWWNWS",
    )

    data class PatternRoom(val kind: CoxRoomKind, val direction: CoxDirection?)
    data class Pattern(val code: String, val rooms: List<PatternRoom>) {
        /** Wiki: a large raid's floors have eight rooms each. */
        val large: Boolean get() = rooms.size == 16
    }

    fun parse(code: String): Pattern {
        val (kinds, directions) = code.split(" - ")
        return Pattern(code, kinds.mapIndexed { i, c ->
            val kind = when (c) {
                '#' -> CoxRoomKind.START
                '*' -> CoxRoomKind.END
                'S' -> CoxRoomKind.SCAVENGERS
                'C' -> CoxRoomKind.COMBAT
                'F' -> CoxRoomKind.FARMING
                else -> CoxRoomKind.PUZZLE
            }
            PatternRoom(kind, directions.getOrNull(i)?.let(CoxDirection::of))
        })
    }

    val PATTERNS: List<Pattern> = LAYOUT_CODES.map(::parse)
    val SMALL_PATTERNS: List<Pattern> = PATTERNS.filter { !it.large }
    val LARGE_PATTERNS: List<Pattern> = PATTERNS.filter { it.large }

    /** Wiki: Challenge Mode's fixed floors - each room and the way the path LEAVES it (the 'Full' map layout uses them too). */
    val CHALLENGE_FLOORS: List<List<Pair<CoxRoom, CoxDirection>>> = listOf(
        listOf(CoxRoom.RAID_START to CoxDirection.WEST, CoxRoom.TEKTON to CoxDirection.WEST, CoxRoom.CRAB_PUZZLE to CoxDirection.NORTH,
            CoxRoom.SMALL_SCAVENGER_RUNT to CoxDirection.EAST, CoxRoom.ICE_DEMON to CoxDirection.EAST, CoxRoom.LIZARDMEN_SHAMAN to CoxDirection.EAST,
            CoxRoom.RESOURCES_B to CoxDirection.SOUTH, CoxRoom.FLOOR_END_DOWNSTAIRS to CoxDirection.SOUTH),
        listOf(CoxRoom.FLOOR_START_UPSTAIRS to CoxDirection.WEST, CoxRoom.VANGUARD to CoxDirection.WEST, CoxRoom.CREATURE_KEEPER to CoxDirection.WEST,
            CoxRoom.SMALL_SCAVENGER_RUNT to CoxDirection.NORTH, CoxRoom.VESPULA to CoxDirection.EAST, CoxRoom.RESOURCES_A to CoxDirection.EAST,
            CoxRoom.DEATHLY_ROOM to CoxDirection.EAST, CoxRoom.FLOOR_END_DOWNSTAIRS to CoxDirection.EAST),
        listOf(CoxRoom.FLOOR_START_UPSTAIRS to CoxDirection.WEST, CoxRoom.GUARDIANS to CoxDirection.WEST, CoxRoom.VASA_NISTIRIO to CoxDirection.WEST,
            CoxRoom.SMALL_SCAVENGER_RUNT to CoxDirection.SOUTH, CoxRoom.DARK_ALTAR_ROOM to CoxDirection.EAST, CoxRoom.MUTTADILES to CoxDirection.EAST,
            CoxRoom.RESOURCES_B to CoxDirection.EAST, CoxRoom.FLOOR_END_DOWNSTAIRS to CoxDirection.EAST),
    )

    /** Floors go on instance planes 3, 2 (and 1 in Challenge Mode); the Olm's chamber is on plane 0. */
    val FLOOR_PLANES = intArrayOf(3, 2, 1)

    data class RoomPick(val room: CoxRoom, val direction: CoxDirection?)

    /** The rooms of a normal raid split into its two floors (tsps generateRooms; rsprox: rotations run forward AND backward). */
    fun generateRooms(random: Random, patterns: List<Pattern> = SMALL_PATTERNS): Pair<String, List<List<RoomPick>>> {
        val pattern = patterns[random.nextInt(patterns.size)]
        val forward = BOSS_ROTATIONS[random.nextInt(BOSS_ROTATIONS.size)]
        val rotation = if (random.nextDouble() < 0.5) forward else forward.reversed()
        val combatCount = pattern.rooms.count { it.kind == CoxRoomKind.COMBAT }
        val start = random.nextInt(rotation.size)
        val combat = ArrayDeque((0 until combatCount).map { rotation[(start + it) % rotation.size] })
        val pools = mutableMapOf(
            CoxRoomKind.SCAVENGERS to SCAVENGER_ROOMS.toMutableList(),
            CoxRoomKind.FARMING to FARMING_ROOMS.toMutableList(),
            CoxRoomKind.PUZZLE to PUZZLE_ROOMS.toMutableList(),
        )
        // Wiki: two of the four puzzles are left out of each raid.
        repeat(2) { pools.getValue(CoxRoomKind.PUZZLE).let { it.removeAt(random.nextInt(it.size)) } }
        val all = mapOf(CoxRoomKind.SCAVENGERS to SCAVENGER_ROOMS, CoxRoomKind.FARMING to FARMING_ROOMS, CoxRoomKind.PUZZLE to PUZZLE_ROOMS)
        val rooms = pattern.rooms.mapIndexed { index, p ->
            val room = when (p.kind) {
                CoxRoomKind.START -> if (index == 0) CoxRoom.RAID_START else CoxRoom.FLOOR_START_UPSTAIRS
                CoxRoomKind.END -> CoxRoom.FLOOR_END_DOWNSTAIRS
                CoxRoomKind.COMBAT -> combat.removeFirst()
                else -> {
                    val pool = pools.getValue(p.kind)
                    if (pool.isNotEmpty()) pool.removeAt(random.nextInt(pool.size)) else all.getValue(p.kind).let { it[random.nextInt(it.size)] }
                }
            }
            RoomPick(room, p.direction)
        }
        val half = rooms.size / 2
        return pattern.code to listOf(rooms.subList(0, half), rooms.subList(half, rooms.size))
    }

    /** Lays rooms out on a grid as a path (tsps Palette): grid tile, entry direction (= rotation) and version per room, then cropped. */
    private class Palette(firstDirection: CoxDirection) {
        var x = 7
        var z = 7
        var last = firstDirection
        val rooms = mutableListOf<CoxLaidRoom>()

        fun add(room: CoxRoom, leave: CoxDirection, lastFloor: Boolean) {
            var entry = last
            val variant = when (room) {
                CoxRoom.RAID_START -> { entry = CoxDirection.NORTH; if (leave == CoxDirection.WEST) 0 else if (leave == CoxDirection.NORTH) 1 else 2 }
                // rsprox captures 820/843/3282/3285/3389: the last floor's hole is 29735 (second version), floors above 29734 (first).
                CoxRoom.FLOOR_END_DOWNSTAIRS -> if (lastFloor) 1 else 0
                else -> if (last == leave) 1 else if (leave == last.left) 0 else 2
            }
            rooms += CoxLaidRoom(room, x, z, entry, variant)
            x += leave.dx
            z += leave.dz
            last = leave
        }

        fun crop(): List<CoxLaidRoom> {
            val minX = rooms.minOf { it.gridX }
            val minZ = rooms.minOf { it.gridZ }
            return rooms.map { it.copy(gridX = it.gridX - minX, gridZ = it.gridZ - minZ) }
        }
    }

    private fun layFloor(rooms: List<RoomPick>, first: CoxDirection, lastFloor: Boolean): List<CoxLaidRoom> {
        val palette = Palette(first)
        rooms.forEachIndexed { index, pick -> palette.add(pick.room, rooms.getOrNull(index + 1)?.direction ?: CoxDirection.SOUTH, lastFloor) }
        return palette.crop()
    }

    /** A normal raid (besluit 2a: only Normal for now): a random small pattern, its two floors laid out. */
    fun normal(random: Random, patterns: List<Pattern> = SMALL_PATTERNS): CoxLayout {
        val (code, floors) = generateRooms(random, patterns)
        val laid = floors.mapIndexed { floor, rooms ->
            val second = rooms.getOrNull(1)?.direction
            val first = if (second == CoxDirection.WEST) CoxDirection.WEST else if (second == CoxDirection.NORTH) CoxDirection.NORTH else CoxDirection.EAST
            layFloor(rooms, first, floor == 1)
        }
        return CoxLayout(code, false, laid)
    }

    /** Challenge Mode / 'Full' layout: always the same three floors (Wiki). Not used until Challenge Mode is built (besluit 2a). */
    fun full(challenge: Boolean): CoxLayout {
        val floors = CHALLENGE_FLOORS.mapIndexed { floor, rooms ->
            val palette = Palette(CoxDirection.NORTH)
            rooms.forEach { (room, leave) -> palette.add(room, leave, floor == CHALLENGE_FLOORS.lastIndex) }
            val laid = palette.crop()
            if (floor == 1) laid.map { if (it.room == CoxRoom.FLOOR_END_DOWNSTAIRS) it.copy(sourceChunkZ = MIDDLE_END_CHUNK_Z) else it } else laid
        }
        return CoxLayout(null, challenge, floors)
    }
}
