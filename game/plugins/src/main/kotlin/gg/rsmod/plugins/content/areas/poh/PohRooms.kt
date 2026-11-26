package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.StaticObject
import gg.rsmod.plugins.api.Skills

/**
 * CONSTRUCTION-99 (owner 2026-11-26): rooms a player builds himself, next to the fixed house of 2026-09/10 (garden, PvP/PvM room,
 * portal chamber, chapel, dining room - those stay exactly as the owner set them up).
 *
 * A built room is a room template of the player's house style (2009scape `RoomProperties`: the chunk of the style's map square;
 * Darkan `HouseRooms` has the same chunks, absolute) copied into a free cell of the house grid with a rotation. Its build hotspots
 * are its own map objects; what was built on them is saved per hotspot (cell + the hotspot's loc id + its tile in the TEMPLATE,
 * so a rotation keeps the furniture).
 *
 * OSRS Wiki "Player-owned house" (Restrictions / Maximum number of rooms and area): 24 rooms (gardens included) from level 1,
 * +1 at 26 and every 6 levels to 92, +1 at 96 and 99 (38); the house fits in 3x3 rooms at level 1, 4x4 at 15, 5x5 at 30, 6x6 at 45,
 * 7x7 at 60. Room levels and prices: OSRS Wiki / 2009scape `RoomProperties` (the same numbers).
 */
object PohRooms {
    enum class Kind { CHAMBER, LAND, DUNGEON }

    /**
     * Where a room may be built (Darkan `House.createRoom` / OSRS Wiki): [ANY] on the ground floor or upstairs, [GROUND] only on
     * the ground floor (gardens, the throne room), [UPSTAIRS] only upstairs (the top of a hall's staircase), [DUNGEON] only
     * underground, [OUBLIETTE] only underground below a throne room's trapdoor.
     */
    enum class Where { ANY, GROUND, UPSTAIRS, DUNGEON, OUBLIETTE }

    /** Levels of a built room ([Built.plane]): the ground floor, upstairs and the dungeon. */
    const val GROUND_FLOOR = 0
    const val UPSTAIRS = 1
    const val DUNGEON = -1

    /**
     * The dungeon is its own 8x8 grid in the same house map, [DUNGEON_OFFSET] chunks east of the house (out of sight: further
     * than the client's 52-tile view from any house cell), on height 0; the ground floor is height 0 and upstairs height 1 of
     * the house's own cells.
     */
    const val DUNGEON_OFFSET = 16

    /** The instance chunk (x, z, height) of house cell [x],[z] on [plane]. */
    fun instanceCell(
        x: Int,
        z: Int,
        plane: Int,
    ): Triple<Int, Int, Int> = if (plane == DUNGEON) Triple(x + DUNGEON_OFFSET, z, 0) else Triple(x, z, plane)

    /** The house cell (x, z, plane) [tile] of a house map with south-west corner [baseX],[baseZ] is in; null outside every grid. */
    fun cellOf(
        baseX: Int,
        baseZ: Int,
        tile: Tile,
    ): Triple<Int, Int, Int>? {
        val cx = (tile.x - baseX) shr 3
        val cz = (tile.z - baseZ) shr 3
        if (cz !in 0 until PlayerHouse.HOUSE_CHUNKS) return null
        return when {
            cx in 0 until PlayerHouse.HOUSE_CHUNKS && tile.height in 0..1 -> Triple(cx, cz, tile.height)
            cx - DUNGEON_OFFSET in 0 until PlayerHouse.HOUSE_CHUNKS && tile.height == 0 -> Triple(cx - DUNGEON_OFFSET, cz, DUNGEON)
            else -> null
        }
    }

    /** The house cell [tile] is in, inside a live house. */
    fun cellOf(tile: Tile): Triple<Int, Int, Int>? {
        val area = PlayerHouse.houseArea(tile) ?: return null
        return cellOf(area.bottomLeftX, area.bottomLeftZ, tile)
    }

    /** The south-west tile of house cell [x],[z] on [plane] of the house map starting at [baseX],[baseZ]. */
    fun cornerOf(
        baseX: Int,
        baseZ: Int,
        x: Int,
        z: Int,
        plane: Int,
    ): Tile {
        val (cx, cz, h) = instanceCell(x, z, plane)
        return Tile(baseX + cx * 8, baseZ + cz * 8, h)
    }

    /** The same spot one level up or down ([to]): the dungeon lies [DUNGEON_OFFSET] chunks east, upstairs one height up. */
    fun sameSpotOn(
        tile: Tile,
        from: Int,
        to: Int,
    ): Tile {
        val dx = (if (to == DUNGEON) DUNGEON_OFFSET * 8 else 0) - (if (from == DUNGEON) DUNGEON_OFFSET * 8 else 0)
        val h = if (to == DUNGEON) 0 else to
        return Tile(tile.x + dx, tile.z, h)
    }

    enum class RoomType(
        val displayName: String,
        val level: Int,
        val cost: Int,
        val chunkX: Int,
        val chunkZ: Int,
        val kind: Kind,
        val where: Where = Where.ANY,
    ) {
        PARLOUR("Parlour", 1, 1_000, 0, 7, Kind.CHAMBER),
        GARDEN("Garden", 1, 1_000, 0, 1, Kind.LAND, Where.GROUND),
        KITCHEN("Kitchen", 5, 5_000, 2, 7, Kind.CHAMBER),
        DINING_ROOM("Dining room", 10, 5_000, 4, 7, Kind.CHAMBER),
        WORKSHOP("Workshop", 15, 10_000, 0, 5, Kind.CHAMBER),
        BEDROOM("Bedroom", 20, 10_000, 6, 7, Kind.CHAMBER),
        SKILL_HALL("Skill hall", 25, 15_000, 1, 6, Kind.CHAMBER, Where.GROUND),
        GAMES_ROOM("Games room", 30, 25_000, 5, 4, Kind.CHAMBER),
        COMBAT_ROOM("Combat room", 32, 25_000, 3, 4, Kind.CHAMBER),
        QUEST_HALL("Quest hall", 35, 25_000, 5, 6, Kind.CHAMBER, Where.GROUND),
        STUDY("Study", 40, 50_000, 4, 5, Kind.CHAMBER),
        COSTUME_ROOM("Costume room", 42, 50_000, 6, 1, Kind.CHAMBER),
        CHAPEL("Chapel", 45, 50_000, 2, 5, Kind.CHAMBER),
        PORTAL_CHAMBER("Portal chamber", 50, 100_000, 1, 4, Kind.CHAMBER),
        FORMAL_GARDEN("Formal garden", 55, 75_000, 2, 1, Kind.LAND, Where.GROUND),
        THRONE_ROOM("Throne room", 60, 150_000, 6, 5, Kind.CHAMBER, Where.GROUND),

        // Darkan HouseRooms (chunks from 232,632): HALL_SKILL_DOWN 235,638 / HALL_QUEST_DOWN 239,638 are the halls at the top of a
        // staircase (their stairs lead down), the dungeon rooms OUBLIETTE 238,635, DUNGEON_CORRIDOR 236,635, DUNGEON_JUNCTION 232,635,
        // DUNGEON_STAIRS 234,635, DUNGEON_PIT 237,634, TREASURE_ROOM 239,636. Levels and prices: OSRS Wiki (the same numbers).
        SKILL_HALL_UP("Skill hall", 25, 15_000, 3, 6, Kind.CHAMBER, Where.UPSTAIRS),
        QUEST_HALL_UP("Quest hall", 35, 25_000, 7, 6, Kind.CHAMBER, Where.UPSTAIRS),
        OUBLIETTE("Oubliette", 65, 150_000, 6, 3, Kind.DUNGEON, Where.OUBLIETTE),
        DUNGEON_CORRIDOR("Dungeon corridor", 70, 7_500, 4, 3, Kind.DUNGEON, Where.DUNGEON),
        DUNGEON_JUNCTION("Dungeon junction", 70, 7_500, 0, 3, Kind.DUNGEON, Where.DUNGEON),
        DUNGEON_STAIRS("Dungeon stairs", 70, 7_500, 2, 3, Kind.DUNGEON, Where.DUNGEON),
        DUNGEON_PIT("Dungeon pit", 70, 10_000, 5, 2, Kind.DUNGEON, Where.DUNGEON),
        TREASURE_ROOM("Treasure room", 75, 250_000, 7, 4, Kind.DUNGEON, Where.DUNGEON),
        ;

        /** True when this room may be built on [plane] from a doorway. */
        fun buildableOn(plane: Int): Boolean =
            when (plane) {
                DUNGEON -> where == Where.DUNGEON
                UPSTAIRS -> where == Where.ANY || where == Where.UPSTAIRS
                else -> where == Where.ANY || where == Where.GROUND
            }

        /** The template's south-west tile for [style]. */
        fun template(style: PlayerHouse.Style): Tile = style.chunk(chunkX, chunkZ)
    }

    /** One built room: grid cell (instance chunk), plane, rotation (0-3 clockwise). */
    data class Built(val type: RoomType, val x: Int, val z: Int, val plane: Int, val rot: Int) {
        fun encode() = "${type.name},$x,$z,$plane,$rot"

        companion object {
            fun decode(s: String): Built? {
                val p = s.split(',')
                val type = RoomType.values().firstOrNull { it.name == p.getOrNull(0) } ?: return null
                return Built(type, p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt() and 3)
            }
        }
    }

    /** Saved rooms ("TYPE,x,z,plane,rot"). */
    val ROOMS_ATTR = AttributeKey<MutableList<String>>(persistenceKey = "poh_rooms")

    /** Saved furniture ("x,z,plane:hotspotLoc@templateX,templateZ=DECO_KEY"). */
    val FURNITURE_ATTR = AttributeKey<MutableList<String>>(persistenceKey = "poh_furniture")

    fun rooms(player: Player): List<Built> = (player.attr[ROOMS_ATTR] ?: emptyList<String>()).mapNotNull { Built.decode(it) }

    fun roomAt(
        player: Player,
        x: Int,
        z: Int,
        plane: Int = 0,
    ): Built? = rooms(player).firstOrNull { it.x == x && it.z == z && it.plane == plane }

    /** The fixed house's cells (garden at 1,1; see [PlayerHouse.Room]). */
    val FIXED_CELLS: Set<Pair<Int, Int>> = PlayerHouse.Room.values().map { (it.dx + 1) to (it.dz + 1) }.toSet()

    /** OSRS Wiki maximum rooms (gardens included); the fixed house's five are free on top, see [roomCount]. */
    fun maxRooms(level: Int): Int {
        var max = 24
        if (level >= 26) max += minOf(12, (level - 26) / 6 + 1)
        if (level >= 96) max++
        if (level >= 99) max++
        return max
    }

    /** OSRS Wiki maximum house dimension (rooms per side). */
    fun maxDimension(level: Int): Int =
        when {
            level >= 60 -> 7
            level >= 45 -> 6
            level >= 30 -> 5
            level >= 15 -> 4
            else -> 3
        }

    /** "a parlour" / "an oubliette" / "a dungeon stairs room". */
    fun aRoom(type: RoomType): String {
        val name = type.displayName.lowercase().let { if (it.endsWith("stairs")) "$it room" else it }
        return (if (name.first() in "aeiou") "an " else "a ") + name
    }

    /** A room upstairs at [x],[z] needs a room below it (a built room or the fixed house's, never a garden). */
    fun supportsAbove(
        player: Player,
        x: Int,
        z: Int,
    ): Boolean {
        val below = roomAt(player, x, z, GROUND_FLOOR)
        if (below != null) return below.type.kind != Kind.LAND
        val fixed = PlayerHouse.Room.values().firstOrNull { (it.dx + 1) to (it.dz + 1) == x to z } ?: return false
        return fixed != PlayerHouse.Room.GARDEN
    }

    /** True when [room] is a ground-floor room with a room upstairs on it. */
    fun holdsUp(
        player: Player,
        room: Built,
    ): Boolean = room.plane == GROUND_FLOOR && roomAt(player, room.x, room.z, UPSTAIRS) != null

    /** Darkan `House.createRoom`: one games room (and one menagerie) per house. */
    fun onlyOne(
        player: Player,
        type: RoomType,
    ): Boolean = type != RoomType.GAMES_ROOM || rooms(player).none { it.type == RoomType.GAMES_ROOM }

    /**
     * Rooms that count towards [maxRooms]. CON99-VASTHUIS (owner 2026-11-26): the free fixed house (garden, PvP/PvM room, portal
     * chamber, chapel, dining room) does NOT count, so a player gets the full OSRS room limit on top of it.
     */
    fun roomCount(player: Player): Int = rooms(player).size

    /** Whether a room at [x],[z] keeps the house inside its maximum dimension. */
    fun fitsDimension(
        player: Player,
        x: Int,
        z: Int,
    ): Boolean {
        val cells = FIXED_CELLS + rooms(player).map { it.x to it.z } + (x to z)
        val dim = maxDimension(player.skills.getMaxLevel(Skills.CONSTRUCTION))
        return cells.maxOf { it.first } - cells.minOf { it.first } < dim && cells.maxOf { it.second } - cells.minOf { it.second } < dim
    }

    /** A saved furniture value is "DECO_KEY" or "DECO_KEY#variant" (a directed portal: its destination). */
    fun decoKey(value: String): String = value.substringBefore('#')

    fun variantOf(value: String): Int? = value.substringAfter('#', "").toIntOrNull()

    /** The loc a saved furniture value places in [style]. */
    fun placedObj(
        value: String,
        style: PlayerHouse.Style,
    ): Int? {
        val deco = PohFurniture.DECOS[decoKey(value)] ?: return null
        val obj = deco.obj(style)
        val variant = variantOf(value) ?: return obj
        return PohFurnitureUse.PORTAL_FRAMES[obj]?.plus(variant) ?: obj
    }

    fun furniture(player: Player): Map<String, String> =
        (player.attr[FURNITURE_ATTR] ?: emptyList<String>()).associate { it.substringBefore('=') to it.substringAfter('=') }

    fun setFurniture(
        player: Player,
        key: String,
        deco: String?,
    ) {
        val list = player.attr[FURNITURE_ATTR] ?: mutableListOf<String>().also { player.attr[FURNITURE_ATTR] = it }
        list.removeIf { it.substringBefore('=') == key }
        if (deco != null) list.add("$key=$deco")
    }

    fun addRoom(
        player: Player,
        room: Built,
    ) {
        val list = player.attr[ROOMS_ATTR] ?: mutableListOf<String>().also { player.attr[ROOMS_ATTR] = it }
        list.removeIf { s -> Built.decode(s)?.let { it.x == room.x && it.z == room.z && it.plane == room.plane } == true }
        list.add(room.encode())
    }

    /** Removes the room and everything built in it (OSRS: removing a room destroys its furniture). */
    fun removeRoom(
        player: Player,
        room: Built,
    ) {
        player.attr[ROOMS_ATTR]?.removeIf { s -> Built.decode(s)?.let { it.x == room.x && it.z == room.z && it.plane == room.plane } == true }
        val prefix = "${room.x},${room.z},${room.plane}:"
        player.attr[FURNITURE_ATTR]?.removeIf { it.startsWith(prefix) }
    }

    /**
     * One hotspot of a built room: the template object ([loc], [type], [rot] and its tile [templateX],[templateZ] in the template)
     * and where the allocator put its copy in the house ([tile]). A piece built on it stands where the template would have it,
     * turned with the room: [pieceTile] uses the piece's own size, as the allocator turns any map object.
     */
    class Spot(
        val hotspot: PohFurniture.Hotspot,
        val loc: Int,
        val type: Int,
        val rot: Int,
        val templateX: Int,
        val templateZ: Int,
        val tile: Tile,
        private val frame: PlayerHouse.RoomFrame,
    ) {
        fun key(room: Built) = "${room.x},${room.z},${room.plane}:$loc@$templateX,$templateZ"

        /** The rotation a loc placed on this spot has in the house. */
        val houseRot: Int get() = (rot + frame.rotation) and 3

        fun pieceTile(
            world: World,
            id: Int,
        ): Tile {
            val def = world.definitions.get(ObjectDef::class.java, id)
            val w = if (rot and 1 == 1) def.length else def.width
            val l = if (rot and 1 == 1) def.width else def.length
            return frame.tile(templateX, templateZ, w, l)
        }
    }

    /**
     * Every buildable hotspot of [room] in this house: read from the room template of [style] and turned the way the allocator turns
     * the room's map objects ([PlayerHouse.RoomFrame]: tile + the object's rotated size), at the room's corner [corner].
     */
    fun spots(
        world: World,
        style: PlayerHouse.Style,
        room: Built,
        corner: Tile,
    ): List<Spot> {
        val template = room.type.template(style)
        val frame = PlayerHouse.RoomFrame(world, corner, room.rot)
        return world.chunks.get(template.chunkCoords, createIfNeeded = true)!!
            .getEntities<StaticObject>(EntityType.STATIC_OBJECT)
            .filter { it.tile.height == template.height }
            .mapNotNull { obj ->
                val h = PohFurniture.hotspotOf(obj.id) ?: return@mapNotNull null
                val lx = obj.tile.x - template.x
                val lz = obj.tile.z - template.z
                val spot = Spot(h, obj.id, obj.type, obj.rot, lx, lz, Tile(0, 0), frame)
                Spot(h, obj.id, obj.type, obj.rot, lx, lz, spot.pieceTile(world, obj.id), frame)
            }
    }

    /** The objects (map and spawned) on [tile]. */
    fun objectsAt(
        world: World,
        tile: Tile,
    ): List<GameObject> =
        world.chunks.get(tile, createIfNeeded = true)!!.getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)

    /** The style's plain window (2009scape `HousingStyle` window; the Wilderness theme keeps its own stained glass). */
    fun windowOf(style: PlayerHouse.Style): Int =
        when (style) {
            PlayerHouse.Style.BASIC_WOOD -> 13099
            PlayerHouse.Style.BASIC_STONE -> 13091
            PlayerHouse.Style.WHITEWASHED_STONE -> 13005
            PlayerHouse.Style.FREMENNIK_WOOD -> 13112
            PlayerHouse.Style.TROPICAL_WOOD -> 10816
            PlayerHouse.Style.FANCY_STONE -> 13117
            PlayerHouse.Style.WILDERNESS -> style.stainedGlass
        }

    const val WINDOW_SPACE = 13830

    /**
     * Furnishes a built room after the allocator copied it: every buildable hotspot becomes the piece built there, or stays
     * (building mode) or goes (otherwise); window spaces become the style's window; every other space (stairs, crests - not
     * buildable here yet) goes outside building mode. Called from [PlayerHouse.build]; doorways are joined there.
     */
    fun furnish(
        world: World,
        player: Player,
        style: PlayerHouse.Style,
        room: Built,
        corner: Tile,
        buildMode: Boolean,
    ) {
        val built = furniture(player)
        val copies =
            world.chunks.get(corner.chunkCoords, createIfNeeded = true)!!
                .getEntities<StaticObject>(EntityType.STATIC_OBJECT)
                .filter { it.tile.height == corner.height }
        val handled = HashSet<StaticObject>()
        spots(world, style, room, corner).forEach { spot ->
            val copy = copies.firstOrNull { it.id == spot.loc && it.tile == spot.tile }
            copy?.let { handled.add(it) }
            val id = built[spot.key(room)]?.let { placedObj(it, style) }
            if (id != null) {
                copy?.let { world.remove(it) }
                if (id > 0) world.spawn(DynamicObject(id, spot.type, spot.houseRot, spot.pieceTile(world, id)))
            } else if (!buildMode) {
                copy?.let { world.remove(it) }
            }
        }
        copies.filter { it !in handled }.forEach { obj ->
            val name = world.definitions.get(ObjectDef::class.java, obj.id).name.lowercase()
            when {
                obj.id == WINDOW_SPACE -> {
                    world.remove(obj)
                    world.spawn(DynamicObject(windowOf(style), obj.type, obj.rot, obj.tile))
                }
                name == "door hotspot" -> Unit
                name.endsWith(" space") && !buildMode -> world.remove(obj)
            }
        }
    }

    /** The built room the house tile [tile] is in (instance chunk from the house corner). */
    fun builtAt(
        player: Player,
        tile: Tile,
    ): Built? {
        val (x, z, plane) = cellOf(tile) ?: return null
        return roomAt(player, x, z, plane)
    }

    /** The spot of a built room at [obj] (a hotspot or a piece standing on one). */
    fun spotAt(
        player: Player,
        obj: GameObject,
        matchLoc: Boolean,
    ): Pair<Built, Spot>? {
        val room = builtAt(player, obj.tile) ?: return null
        val corner = PlayerHouse.cellCorner(obj.tile) ?: return null
        val world = player.world
        val style = PlayerHouse.styleOf(player)
        val all = spots(world, style, room, corner)
        // A piece: the spot that holds exactly this piece (two hotspots can share a template tile - the skill hall's rug and stairs).
        val built = furniture(player)
        val spot =
            if (matchLoc) {
                all.firstOrNull { it.loc == obj.id && it.tile == obj.tile }
            } else {
                all.firstOrNull { s -> built[s.key(room)]?.let { placedObj(it, style) } == obj.id && s.pieceTile(world, obj.id) == obj.tile }
                    ?: all.firstOrNull { it.pieceTile(world, obj.id) == obj.tile }
            } ?: return null
        return room to spot
    }
}
