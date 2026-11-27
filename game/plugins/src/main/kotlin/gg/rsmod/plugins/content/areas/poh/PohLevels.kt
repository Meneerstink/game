package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards

/**
 * CONSTRUCTION-99 part 3: the house's other levels - upstairs and the dungeon - and the pieces that join them.
 *
 * After Darkan `House.climbStaircase` / `climbLadder` (rev 727): a skill or quest hall's staircase leads to the room at the same
 * cell one level up or down; when nothing is there yet the owner, in building mode, is offered the room at the other end (a hall
 * upstairs or downstairs, the dungeon stairs room below the ground floor) with the same staircase already standing in it, at the
 * room's own price and Construction level (OSRS Wiki: the stairs come with the room at the other end). A throne room's trapdoor
 * leads down to an oubliette (built the same way, with its ladder); the ladder climbs back up. "Remove-room" on a staircase,
 * trapdoor or ladder removes the room at its other end. Dungeon doors open for the owner (there are never guests in a house
 * here, so Pick-lock and Force simply open the door too).
 */
object PohLevels {
    private val STAIRS_UP = setOf(13497, 13499, 13501)
    private val STAIRS_DOWN = setOf(13498, 13500, 13502)
    private val SPIRALS = setOf(13503, 13505)
    private val TRAPDOORS = setOf(13675, 13676, 13677)
    private val LADDERS = setOf(13328, 13329, 13330)
    private val DOORS = (13344..13349).toSet() + setOf(13314, 13317, 13320, 13323, 13326)

    /** A garden's dungeon entrance (centrepiece): down to the dungeon room below it. */
    private const val DUNGEON_ENTRANCE = 13409

    private const val CLIMB_UP_ANIM = 828
    private const val CLIMB_DOWN_ANIM = 827

    fun handles(id: Int): Boolean =
        id in STAIRS_UP || id in STAIRS_DOWN || id in SPIRALS || id in TRAPDOORS || id in LADDERS || id in DOORS || id == DUNGEON_ENTRANCE

    fun handle(
        player: Player,
        obj: GameObject,
        option: String,
    ): Boolean {
        val id = obj.id
        when {
            option == "remove-room" -> player.queue { removeOtherEnd(this, obj) }
            id in DOORS -> passDoor(player, obj)
            option == "climb-up" -> player.queue {
                // A ground-floor hall's staircase also leads down to a dungeon stairs room (OSRS: Climb up / Climb down).
                val cell = PohRooms.cellOf(obj.tile)
                val below = cell != null && cell.third == PohRooms.GROUND_FLOOR && id in STAIRS_UP &&
                    (PohRooms.roomAt(player, cell.first, cell.second, PohRooms.DUNGEON) != null || PlayerHouse.isBuildMode(player))
                if (!below) {
                    climb(this, obj, up = true)
                } else {
                    when (options("Climb up.", "Climb down.", title = "Climb up or down the stairs?")) {
                        1 -> climb(this, obj, up = true)
                        2 -> climb(this, obj, up = false)
                    }
                }
            }
            option == "climb-down" -> player.queue { climb(this, obj, up = false) }
            option == "climb" && id in LADDERS -> player.queue { climb(this, obj, up = true) }
            option == "climb" -> player.queue {
                when (options("Climb up.", "Climb down.", title = "Climb up or down the stairs?")) {
                    1 -> climb(this, obj, up = true)
                    2 -> climb(this, obj, up = false)
                }
            }
            option == "open" && id in TRAPDOORS -> player.queue { climb(this, obj, up = false) }
            option == "enter" && id == DUNGEON_ENTRANCE -> player.queue { climb(this, obj, up = false) }
            else -> return false
        }
        return true
    }

    /** The level [up]/down from [plane], or null at the top (upstairs) or the bottom (the dungeon). */
    private fun levelFrom(
        plane: Int,
        up: Boolean,
    ): Int? =
        when (plane) {
            PohRooms.UPSTAIRS -> if (up) null else PohRooms.GROUND_FLOOR
            PohRooms.GROUND_FLOOR -> if (up) PohRooms.UPSTAIRS else PohRooms.DUNGEON
            else -> if (up) PohRooms.GROUND_FLOOR else null
        }

    /** The rooms that may stand at the other end of [piece] (Darkan `climbStaircase` / `climbLadder`). */
    private fun roomsAtOtherEnd(
        piece: Int,
        to: Int,
    ): List<PohRooms.RoomType> =
        when {
            piece in TRAPDOORS -> listOf(PohRooms.RoomType.OUBLIETTE)
            piece == DUNGEON_ENTRANCE -> PohRooms.RoomType.values().filter { it.buildableOn(PohRooms.DUNGEON) }
            piece in LADDERS -> emptyList()
            to == PohRooms.UPSTAIRS -> listOf(PohRooms.RoomType.SKILL_HALL_UP, PohRooms.RoomType.QUEST_HALL_UP)
            to == PohRooms.DUNGEON -> listOf(PohRooms.RoomType.DUNGEON_STAIRS)
            else -> listOf(PohRooms.RoomType.SKILL_HALL, PohRooms.RoomType.QUEST_HALL)
        }

    /** The hotspots that join two levels: staircases, a throne room's trapdoor and an oubliette's ladder. */
    private fun isJoin(spot: PohRooms.Spot): Boolean = spot.hotspot.type == "STAIRCASE" || spot.hotspot.key == "TRAPDOOR" || spot.hotspot.key == "LADDER"

    /** The joining spot of [room] (cell corner [corner]) that has a piece built on it. */
    private fun builtJoin(
        player: Player,
        room: PohRooms.Built,
        corner: Tile,
    ): PohRooms.Spot? {
        val built = PohRooms.furniture(player)
        return PohRooms.spots(player.world, PlayerHouse.styleOf(player), room, corner).firstOrNull { isJoin(it) && built[it.key(room)] != null }
    }

    private suspend fun climb(
        task: QueueTask,
        obj: GameObject,
        up: Boolean,
    ) {
        val player = task.player
        val (x, z, plane) = PohRooms.cellOf(obj.tile) ?: return
        val room = PohRooms.roomAt(player, x, z, plane) ?: return
        val to = levelFrom(plane, up)
        if (to == null) {
            player.message("You are on the ${if (up) "highest" else "lowest"} possible level so you cannot go any ${if (up) "higher" else "lower"}.")
            return
        }
        val noun =
            when (obj.id) {
                in TRAPDOORS -> "trapdoor"
                in LADDERS -> "ladder"
                DUNGEON_ENTRANCE -> "entrance"
                else -> "stairs"
            }
        val target = PohRooms.roomAt(player, x, z, to)
        if (target == null) {
            val types = roomsAtOtherEnd(obj.id, to)
            if (!PlayerHouse.isBuildMode(player) || types.isEmpty()) {
                player.message("${if (noun == "stairs") "These stairs do" else "This $noun does"} not lead anywhere.")
                return
            }
            if (!buildOtherEnd(task, room, obj, x, z, to, types)) return
        } else {
            val area = PlayerHouse.houseArea(obj.tile) ?: return
            val corner = PohRooms.cornerOf(area.bottomLeftX, area.bottomLeftZ, x, z, to)
            if (obj.id != DUNGEON_ENTRANCE && builtJoin(player, target, corner) == null) {
                player.message("${if (noun == "stairs") "These stairs do" else "This $noun does"} not lead anywhere.")
                return
            }
        }
        // The house may have been rebuilt (a new room) somewhere else: the same spot of the cell, from the live house's corner.
        val lx = player.tile.x and 7
        val lz = player.tile.z and 7
        val area = PlayerHouse.houseAreaOf(player) ?: return
        val dest = PohRooms.cornerOf(area.bottomLeftX, area.bottomLeftZ, x, z, to).transform(lx, lz)
        if (noun != "stairs") {
            player.animate(if (up) CLIMB_UP_ANIM else CLIMB_DOWN_ANIM)
            task.wait(2)
        }
        val landing = CityGuards.nearestWalkable(player.world, dest, radius = 4) ?: dest
        player.moveTo(landing)
        if (noun == "trapdoor") player.message("You climb down into the oubliette.")
    }

    /**
     * The room at the other end of a staircase / trapdoor ([obj], in [from]) does not exist yet: the owner, in building mode, may
     * build it - with the matching piece already standing on its joining spot (the same tier: an oak staircase leads to oak stairs
     * down). True when the room was built and the house rebuilt.
     */
    private suspend fun buildOtherEnd(
        task: QueueTask,
        from: PohRooms.Built,
        obj: GameObject,
        x: Int,
        z: Int,
        to: Int,
        types: List<PohRooms.RoomType>,
    ): Boolean {
        val player = task.player
        val where = if (to == PohRooms.UPSTAIRS) "top" else "bottom"
        val what =
            when (obj.id) {
                in TRAPDOORS -> "This trapdoor does"
                DUNGEON_ENTRANCE -> "This entrance does"
                else -> "These stairs do"
            }
        if (task.options("Yes.", "No.", title = "$what not lead anywhere. Build a room at the $where?") != 1) return false
        if (to == PohRooms.GROUND_FLOOR && PohRooms.FIXED_CELLS.contains(x to z)) {
            task.messageBox("There is already a room of your house below.")
            return false
        }
        if (to == PohRooms.UPSTAIRS && !PohRooms.supportsAbove(player, x, z)) return false
        val type =
            if (types.size == 1) {
                types[0]
            } else {
                val labels = types.map { "${it.displayName}: ${"%,d".format(it.cost)} coins (level ${it.level})" }
                types.getOrNull(task.options(*labels.toTypedArray(), title = "Select a room") - 1) ?: return false
            }
        val level = player.skills.getCurrentLevel(Skills.CONSTRUCTION)
        when {
            level < type.level -> {
                task.messageBox("You need to have a Construction level of ${type.level} to build that room.")
                return false
            }
            PohRooms.roomCount(player) >= PohRooms.maxRooms(level) -> {
                task.messageBox("You can't build any more rooms: your Construction level allows ${PohRooms.maxRooms(level)}.")
                return false
            }
            !PohRooms.fitsDimension(player, x, z) -> {
                task.messageBox("Your house can't be any bigger: with your Construction level it may only be ${PohRooms.maxDimension(level)} rooms across.")
                return false
            }
            player.inventory.getItemCount(Items.COINS_995) < type.cost -> {
                task.messageBox("You need ${"%,d".format(type.cost)} coins to build ${PohRooms.aRoom(type)}.")
                return false
            }
        }
        if (task.options("Build it (${"%,d".format(type.cost)} coins)", "Cancel", title = "Build ${PohRooms.aRoom(type)} at the $where?") != 1) return false
        if (PohRooms.roomAt(player, x, z, to) != null || player.inventory.getItemCount(Items.COINS_995) < type.cost) return false
        // The piece the player climbs now, and its tier in its hotspot's list.
        val (_, spot) = PohRooms.spotAt(player, obj, matchLoc = false) ?: return false
        val key = PohRooms.furniture(player)[spot.key(from)]?.let { PohRooms.decoKey(it) } ?: return false
        val tier = spot.hotspot.decos.indexOfFirst { it?.key == key }
        player.inventory.remove(Items.COINS_995, type.cost)
        val room = PohRooms.Built(type, x, z, to, from.rot)
        PohRooms.addRoom(player, room)
        // Its joining spot: the one on the same template tile if the room has one there, else its first.
        // The new room's corner in this house map (its spots' keys do not depend on where the map lies).
        val area = PlayerHouse.houseArea(obj.tile) ?: return false
        val corner = PohRooms.cornerOf(area.bottomLeftX, area.bottomLeftZ, x, z, to)
        val joins = PohRooms.spots(player.world, PlayerHouse.styleOf(player), room, corner).filter { isJoin(it) }
        val join = joins.firstOrNull { it.templateX == spot.templateX && it.templateZ == spot.templateZ } ?: joins.firstOrNull()
        val deco = join?.hotspot?.decos?.getOrNull(tier)
        if (join != null && deco != null && obj.id != DUNGEON_ENTRANCE) PohRooms.setFurniture(player, join.key(room), deco.key)
        player.playSound(Sfx.POH_BUILD_WOOD)
        return PlayerHouse.rebuild(player)
    }

    /** "Remove-room" on a staircase, trapdoor or ladder: the room at its other end goes, with everything in it. */
    private suspend fun removeOtherEnd(
        task: QueueTask,
        obj: GameObject,
    ) {
        val player = task.player
        if (!PlayerHouse.ownsHouseAt(player) || !PohBuild.requireBuildMode(player)) return
        val (x, z, plane) = PohRooms.cellOf(obj.tile) ?: return
        val up = obj.id in STAIRS_UP || obj.id in LADDERS || (obj.id in SPIRALS && plane != PohRooms.UPSTAIRS)
        val candidates =
            listOfNotNull(levelFrom(plane, up)?.let { PohRooms.roomAt(player, x, z, it) }) +
                if (obj.id in SPIRALS) listOfNotNull(levelFrom(plane, !up)?.let { PohRooms.roomAt(player, x, z, it) }) else emptyList()
        val other = candidates.firstOrNull()
        if (other == null) {
            task.messageBox("There is no room at the other end.")
            return
        }
        if (PohRooms.holdsUp(player, other)) {
            task.messageBox("You can't remove the ${other.type.displayName.lowercase()}: it holds up the room above it.")
            return
        }
        val name = other.type.displayName.lowercase()
        if (task.options("Yes", "No", title = "Really remove the $name ${if (other.plane > plane) "above" else "below"} and everything in it?") != 1) return
        PohRooms.removeRoom(player, other)
        PlayerHouse.rebuild(player)
    }

    /** A dungeon door or an oubliette cage door: the owner walks through it. */
    private fun passDoor(
        player: Player,
        door: GameObject,
    ) {
        val across =
            when (door.rot and 3) {
                0 -> door.tile.transform(-1, 0)
                1 -> door.tile.transform(0, 1)
                2 -> door.tile.transform(1, 0)
                else -> door.tile.transform(0, -1)
            }
        player.playSound(Sfx.DOOR_OPEN)
        player.moveTo(if (player.tile == door.tile) across else door.tile)
    }
}
