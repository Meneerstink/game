package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*

/**
 * CONSTRUCTION-99 (owner 2026-11-26): building rooms and furniture in the player's own house, as OSRS - in building mode a doorway
 * onto an empty cell builds a new room there (coins, Construction level, the OSRS room limit and house size), a doorway of a built
 * room rotates or removes it, every furniture hotspot of a built room builds its pieces (OSRS Wiki level, experience and materials,
 * [PohFurnitureData]) and Remove takes a piece down again.
 *
 * Mechanism after 2009scape `BuildRoomDialogue` / `ConstructionInterface` / `BuildingUtils` and Darkan `House` (rev 727): rooms
 * cost coins only and give no experience; a piece takes its materials, plays the hotspot's build animation and gives its
 * experience once (a rug or a combat ring fills every spot of its group at once). The fixed house (garden, PvP/PvM room, portal
 * chamber, chapel, dining room) stays as the owner made it: its rooms rotate as before and cannot be removed.
 *
 * Every click comes through [override] (an object override): only a hotspot or piece inside a room the player built himself is
 * taken, so the fixed house's own bindings keep working.
 */
object PohConstruct {
    private const val BUILD = "build"
    private const val REMOVE = "remove"

    /** Wall rotation (0 west, 1 north, 2 east, 3 south) -> cell step. */
    private fun step(side: Int): Pair<Int, Int> =
        when (side and 3) {
            0 -> -1 to 0
            1 -> 0 to 1
            2 -> 1 to 0
            else -> 0 to -1
        }

    private fun optionOf(
        player: Player,
        obj: GameObject,
        opt: Int,
    ): String? = player.world.definitions.get(ObjectDef::class.java, obj.id).options.getOrNull(opt - 1)?.lowercase()

    private fun isDoorHotspot(
        player: Player,
        id: Int,
    ): Boolean = player.world.definitions.get(ObjectDef::class.java, id).name.equals("door hotspot", true)

    /** The object override: true when the click was a Construction action of a built room (or a new room) and is handled. */
    fun override(
        player: Player,
        obj: GameObject,
        opt: Int,
    ): Boolean {
        if (PlayerHouse.houseArea(obj.tile) == null) return false
        val option = optionOf(player, obj, opt) ?: return false
        if (option != BUILD && option != REMOVE) {
            if (PohLevels.handles(obj.id)) return PohLevels.handle(player, obj, option)
            if (PohMenagerie.handles(obj.id)) return PohMenagerie.handle(player, obj, option)
            if (PohCostume.handles(obj.id)) return PohCostume.handle(player, obj, option)
            if (PohSuperiorGarden.handles(obj.id)) return PohSuperiorGarden.handle(player, obj, option)
            if (PohGallery.handles(obj.id)) return PohGallery.handle(player, obj, option)
            if (PohWorkshop.handles(obj.id) && (option == "upgrade" || !PohFurnitureUse.handles(obj.id))) return PohWorkshop.handle(player, obj, option)
            return PohFurnitureUse.handles(obj.id) && PohFurnitureUse.handle(player, obj, option)
        }
        if (!PlayerHouse.ownsHouseAt(player)) return false
        if (option == BUILD && isDoorHotspot(player, obj.id)) {
            val target = doorTarget(player, obj) ?: return false
            val (cell, side) = target
            val (cellX, cellZ, plane) = cell
            val fixed = plane == PohRooms.GROUND_FLOOR && PohRooms.FIXED_CELLS.contains(cellX to cellZ)
            if (fixed) return false
            player.queue { doorBuild(this, cellX, cellZ, plane, side) }
            return true
        }
        if (option == BUILD && PohFurniture.hotspotOf(obj.id) != null) {
            val found = PohRooms.spotAt(player, obj, matchLoc = true) ?: return false
            player.queue { buildFurniture(this, found.first, found.second) }
            return true
        }
        if (option == REMOVE && (PohFurniture.PIECE_IDS.containsKey(obj.id) || obj.id in PohFurnitureUse.PORTALS)) {
            val found = PohRooms.spotAt(player, obj, matchLoc = false) ?: return false
            if (PohRooms.furniture(player)[found.second.key(found.first)] == null) return false
            player.queue { removeFurniture(this, found.first, found.second) }
            return true
        }
        return false
    }

    /**
     * The cell a doorway leads to from where the player stands, with the side of that cell the doorway is on (the side a room
     * there must have a door on). Null when the doorway leads outside the house.
     */
    private fun doorTarget(
        player: Player,
        hotspot: GameObject,
    ): Pair<Triple<Int, Int, Int>, Int>? {
        val area = PlayerHouse.houseArea(hotspot.tile) ?: return null
        val (sx, sz) = step(hotspot.rot)
        val across = hotspot.tile.transform(sx, sz)
        fun cell(t: Tile) = PohRooms.cellOf(area.bottomLeftX, area.bottomLeftZ, t)
        val own = cell(hotspot.tile) ?: return null
        val other = cell(across)
        val mine = cell(player.tile)
        val target = (if (mine == other) own else other) ?: return null
        val from = (if (target == own) other else own) ?: return null
        if (target.third != from.third) return null
        val dx = from.first - target.first
        val dz = from.second - target.second
        val side =
            when {
                dx < 0 -> 0
                dz > 0 -> 1
                dx > 0 -> 2
                else -> 3
            }
        return target to side
    }

    /** The sides of [type]'s template that have a doorway, for rotation [rot]. */
    private fun doorSides(
        player: Player,
        type: PohRooms.RoomType,
        rot: Int,
    ): Set<Int> {
        val world = player.world
        val template = type.template(PlayerHouse.styleOf(player))
        return world.chunks.get(template.chunkCoords, createIfNeeded = true)!!
            .getEntities<gg.rsmod.game.model.entity.StaticObject>(gg.rsmod.game.model.EntityType.STATIC_OBJECT)
            .filter { it.tile.height == template.height && isDoorHotspot(player, it.id) }
            .map { (it.rot + rot) and 3 }
            .toSet()
    }

    /** The rotations of [type] that keep a doorway on [side] (the room it is entered from). */
    fun rotationsFacing(
        player: Player,
        type: PohRooms.RoomType,
        side: Int,
    ): List<Int> = (0..3).filter { side in doorSides(player, type, it) }

    /** Build on a doorway: a new room in an empty cell, or the built room behind it rotated / removed. */
    private suspend fun doorBuild(
        task: QueueTask,
        cellX: Int,
        cellZ: Int,
        plane: Int,
        side: Int,
    ) {
        val player = task.player
        if (!PohBuild.requireBuildMode(player)) return
        val existing = PohRooms.roomAt(player, cellX, cellZ, plane)
        if (existing != null) {
            manageRoom(task, existing, side)
            return
        }
        val level = player.skills.getCurrentLevel(Skills.CONSTRUCTION)
        if (PohRooms.roomCount(player) >= PohRooms.maxRooms(level)) {
            task.messageBox("You can't build any more rooms: your Construction level allows ${PohRooms.maxRooms(level)}.")
            return
        }
        if (!PohRooms.fitsDimension(player, cellX, cellZ)) {
            val dim = PohRooms.maxDimension(level)
            task.messageBox("Your house can't be any bigger: with your Construction level it may only be $dim rooms across.")
            return
        }
        if (plane == PohRooms.UPSTAIRS && !PohRooms.supportsAbove(player, cellX, cellZ)) {
            task.messageBox("There is no room below to hold up a room here.")
            return
        }
        val rooms = PohRooms.RoomType.values().filter { it.buildableOn(plane) }
        val pieces = rooms.mapIndexed { i, it -> PohBuild.Piece(i, "${it.displayName}: ${"%,d".format(it.cost)} coins", it.level, 0.0, emptyList()) }
        val picked = PohBuild.choose(task, pieces, "Build which room?") ?: return
        val type = rooms[picked.obj]
        if (!PohRooms.onlyOne(player, type)) {
            task.messageBox("You can only build one ${type.displayName.lowercase()}.")
            return
        }
        if (level < type.level) {
            task.messageBox("You need to have a Construction level of ${type.level} to build that room.")
            return
        }
        if (player.inventory.getItemCount(Items.COINS_995) < type.cost) {
            task.messageBox("You need ${"%,d".format(type.cost)} coins to build ${PohRooms.aRoom(type)}.")
            return
        }
        val rot = rotationsFacing(player, type, side).firstOrNull()
        if (rot == null) {
            task.messageBox("That room has no doorway that could face this way.")
            return
        }
        if (task.options("Build it (${"%,d".format(type.cost)} coins)", "Cancel", title = "Build ${PohRooms.aRoom(type)} here?") != 1) return
        // Re-check after the dialogue (the coins may have gone, the cell may be taken).
        if (PohRooms.roomAt(player, cellX, cellZ, plane) != null || player.inventory.getItemCount(Items.COINS_995) < type.cost) return
        player.inventory.remove(Items.COINS_995, type.cost)
        PohRooms.addRoom(player, PohRooms.Built(type, cellX, cellZ, plane, rot))
        player.playSound(Sfx.POH_BUILD_WOOD)
        PlayerHouse.rebuild(player)
    }

    /** A built room's doorway: rotate it (keeping a doorway onto the room the player stands in) or remove it. */
    private suspend fun manageRoom(
        task: QueueTask,
        room: PohRooms.Built,
        side: Int,
    ) {
        val player = task.player
        var current = room
        while (true) {
            val name = current.type.displayName.lowercase()
            val stuck = stuckPieces(player, current)
            val labels = listOf("Rotate clockwise", "Rotate anticlockwise", "Remove room") + (if (stuck.isEmpty()) emptyList() else listOf("Remove furniture")) + "Done"
            val choice =
                when (labels.getOrNull(task.options(*labels.toTypedArray(), title = "The $name") - 1)) {
                    "Rotate clockwise" -> 1
                    "Rotate anticlockwise" -> 2
                    "Remove room" -> 3
                    "Remove furniture" -> 4
                    else -> 5
                }
            when (choice) {
                4 -> {
                    val pieces = stuck.mapIndexed { i, (_, deco) -> PohBuild.Piece(i, deco.name, deco.level, 0.0, emptyList()) }
                    val picked = PohBuild.choose(task, pieces, "Remove which piece?") { it.name } ?: return
                    removeFurniture(task, current, stuck[picked.obj].first)
                    return
                }
                1, 2 -> {
                    val clockwise = choice == 1
                    val allowed = rotationsFacing(player, current.type, side)
                    if (allowed.size < 2) {
                        task.messageBox("The $name can only face this way: it must keep a doorway onto this room.")
                        return
                    }
                    val idx = allowed.indexOf(current.rot).coerceAtLeast(0)
                    val next = allowed[(idx + if (clockwise) 1 else allowed.size - 1) % allowed.size]
                    current = current.copy(rot = next)
                    PohRooms.addRoom(player, current)
                    player.playSound(Sfx.POH_BUILD_WOOD)
                    if (!PlayerHouse.rebuild(player)) return
                }
                3 -> {
                    if (PohRooms.holdsUp(player, current)) {
                        task.messageBox("You can't remove the $name: it holds up the room above it.")
                        return
                    }
                    if (current.type == PohRooms.RoomType.COSTUME_ROOM && PohCostume.anyStored(player)) {
                        task.messageBox("You'll have to empty the furniture before you can remove the $name.")
                        return
                    }
                    if (current.type == PohRooms.RoomType.MENAGERIE && PohMenagerie.stored(player) > 0) {
                        task.messageBox("You'll have to take your pets out of the pet house before you can remove the $name.")
                        return
                    }
                    if (task.options("Yes", "No", title = "Really remove the $name and everything in it?") != 1) return
                    PohRooms.removeRoom(player, current)
                    PlayerHouse.rebuild(player)
                    return
                }
                else -> return
            }
        }
    }

    /**
     * CON99-REMOVE: the pieces built in [room] whose loc has no "Remove" in the cache - PohObjectOptionTool took it off ~36 top pieces
     * (opulent rug, mahogany bookcase, telescope, magic tree ...) for the fixed house, which uses the same locs. Without this they
     * could never be taken down again; the room's doorway (Build in building mode) offers them instead. A linked group or a
     * recursive hotspot (a rug's 16 spots) is one entry, as one Remove takes all of it.
     */
    private fun stuckPieces(
        player: Player,
        room: PohRooms.Built,
    ): List<Pair<PohRooms.Spot, PohFurniture.Deco>> {
        val world = player.world
        val area = PlayerHouse.houseAreaOf(player) ?: return emptyList()
        val style = PlayerHouse.styleOf(player)
        val built = PohRooms.furniture(player)
        val corner = PohRooms.cornerOf(area.bottomLeftX, area.bottomLeftZ, room.x, room.z, room.plane)
        return PohRooms.spots(world, style, room, corner)
            .mapNotNull { s ->
                val value = built[s.key(room)] ?: return@mapNotNull null
                val deco = PohFurniture.DECOS[PohRooms.decoKey(value)] ?: return@mapNotNull null
                val id = PohRooms.placedObj(value, style, PohHeraldry.crestIndex(player)) ?: return@mapNotNull null
                val options = world.definitions.get(ObjectDef::class.java, id).options
                if (options.any { it.equals(REMOVE, true) }) null else s to deco
            }.distinctBy { (s, deco) -> if (s.hotspot.type == "INDIVIDUAL") s.key(room) else deco.key }
    }

    private fun pieceOf(deco: PohFurniture.Deco): PohBuild.Piece =
        PohBuild.Piece(deco.objs.first(), deco.name, deco.level, deco.xp, deco.materials, plant = deco.watering && 'H' !in deco.tools, needsTools = deco.tools.isNotEmpty())

    /** The spots one build on [spot] fills: its linked group's spots, every spot of a recursive hotspot, or itself. */
    private fun targets(
        player: Player,
        room: PohRooms.Built,
        spot: PohRooms.Spot,
        decoIndex: Int,
    ): List<Pair<PohRooms.Spot, PohFurniture.Deco>> {
        val h = spot.hotspot
        val corner = PlayerHouse.cellCorner(spot.tile) ?: return emptyList()
        val all = PohRooms.spots(player.world, PlayerHouse.styleOf(player), room, corner)
        return when (h.type) {
            "INDIVIDUAL" -> listOfNotNull(h.decos.getOrNull(decoIndex)?.let { spot to it })
            "RECURSIVE", "HABITAT" -> all.filter { it.hotspot === h }.mapNotNull { s -> h.decos.getOrNull(decoIndex)?.let { s to it } }
            else ->
                PohFurniture.group(h).flatMap { g ->
                    val deco = g.decos.getOrNull(decoIndex) ?: return@flatMap emptyList()
                    all.filter { it.hotspot === g }.map { it to deco }
                }
        }
    }

    private suspend fun buildFurniture(
        task: QueueTask,
        room: PohRooms.Built,
        spot: PohRooms.Spot,
    ) {
        val player = task.player
        if (!PohBuild.requireBuildMode(player)) return
        val world = player.world
        val decos = spot.hotspot.decos.filterNotNull()
        if (decos.isEmpty()) return
        val title = world.definitions.get(ObjectDef::class.java, spot.loc).name
        val pieces = decos.map { pieceOf(it) }
        val picked = PohBuild.choose(task, pieces, title) ?: return
        placePiece(task, room, spot, decos[pieces.indexOf(picked)])
    }

    /**
     * Builds [deco] on [spot] (and the rest of its group): from its flatpack when the player carries one (OSRS: a hammer and the
     * level, no materials and no experience - the workbench gave the experience), else from its materials.
     */
    internal suspend fun placePiece(
        task: QueueTask,
        room: PohRooms.Built,
        spot: PohRooms.Spot,
        deco: PohFurniture.Deco,
        cost: PohBuild.Piece? = null,
    ) {
        val player = task.player
        val world = player.world
        // [cost]: an upgrade with its own OSRS row (a pet house: the "(Upgrade)" materials and experience, [PohMenagerie.UPGRADES]).
        val picked = cost ?: pieceOf(deco)
        val flatpack = cost == null && deco.flatpack > 0 && player.inventory.contains(deco.flatpack)
        if (flatpack) {
            if (player.skills.getCurrentLevel(Skills.CONSTRUCTION) < deco.level) {
                task.messageBox("You need to have a Construction level of ${deco.level} to build that.")
                return
            }
            if (!player.inventory.contains(Items.HAMMER)) {
                task.messageBox("You need a hammer to assemble the flatpack.")
                return
            }
        } else if ('C' in deco.tools && !player.inventory.contains(Items.CHISEL)) {
            task.messageBox("You need a chisel to build that.")
            return
        }
        if (!flatpack && 'W' in deco.tools && 'H' in deco.tools && PohBuild.WATERING_CANS.none { player.inventory.contains(it) }) {
            task.messageBox("You need a watering can with some water in it to plant this.")
            return
        }
        // Deel 7: a crest piece shows the owner's heraldic crest (OSRS Wiki "Oak wall decoration": "requires ... a heraldic crest").
        if (deco.crestPiece && PohHeraldry.crest(player) == null) {
            task.messageBox("You need a family crest for that. Sir Renitee in Falador keeps the records of every family.")
            return
        }
        if (!flatpack && !PohSuperiorGarden.canPlant(task, deco)) return
        if (!flatpack && !PohBuild.canBuild(task, picked)) return
        val targets = targets(player, room, spot, spot.hotspot.decos.indexOf(deco))
        if (targets.isEmpty()) return
        val style = PlayerHouse.styleOf(player)
        if (flatpack) player.inventory.remove(deco.flatpack, 1) else PohBuild.takeMaterials(player, picked)
        player.lock = LockState.FULL
        player.animate(spot.hotspot.anim)
        player.playSound(if (picked.plant) Sfx.FARMING_WATERING else Sfx.POH_BUILD_WOOD)
        task.wait(2)
        // Bigger pieces last: overlapping spots of one group share a tile slot (the oubliette's rocnar over its floor, turned).
        targets.sortedBy { (_, d) -> world.definitions.get(ObjectDef::class.java, d.objs.first()).let { it.width * it.length } }.forEach { (s, d) ->
            PohRooms.objectsAt(world, s.tile).filter { it.id == s.loc }.forEach { world.remove(it) }
            val habitat = s.hotspot.type == "HABITAT"
            val id = if (habitat) PohFurniture.habitatObj(s.hotspot, d, s.loc) else d.obj(style, PohHeraldry.crestIndex(player))
            if (id > 0) world.spawn(DynamicObject(id, s.type, s.houseRot, s.pieceTile(world, id)))
            PohRooms.setFurniture(player, s.key(room), if (habitat) "${d.key}#${s.hotspot.ids.indexOf(s.loc)}" else d.key)
        }
        if (!flatpack) player.addXp(Skills.CONSTRUCTION, picked.xp, checkBrawlingGloves = true)
        if (!flatpack) PohSuperiorGarden.plantXp(player, deco)
        PohMenagerie.refreshPets(player)
        player.unlock()
    }

    /** "Upgrade" on a workbench or clockmaker's bench: the next piece of its hotspot built in its place (its full materials). */
    internal suspend fun upgradePiece(
        task: QueueTask,
        obj: GameObject,
    ) {
        val player = task.player
        if (!PlayerHouse.ownsHouseAt(player) || !PohBuild.requireBuildMode(player)) return
        val (room, spot) = PohRooms.spotAt(player, obj, matchLoc = false) ?: return
        upgradeAt(task, room, spot)
    }

    /** Builds the next piece of [spot]'s hotspot in place of the built one (the OSRS upgrade row when there is one). */
    internal suspend fun upgradeAt(
        task: QueueTask,
        room: PohRooms.Built,
        spot: PohRooms.Spot,
        target: PohFurniture.Deco? = null,
    ) {
        val player = task.player
        if (!PlayerHouse.ownsHouseAt(player) || !PohBuild.requireBuildMode(player)) return
        val key = PohRooms.furniture(player)[spot.key(room)]?.let { PohRooms.decoKey(it) } ?: return
        val index = spot.hotspot.decos.indexOfFirst { it?.key == key }
        // [target]: an upgrade that is not simply the next piece (an Ancient or Lunar altar to the occult altar, PohGallery).
        val next = target?.takeIf { it.key != key } ?: spot.hotspot.decos.getOrNull(index + 1)
        if (index < 0 || next == null) {
            task.messageBox("This can't be upgraded any further.")
            return
        }
        // The old piece goes first, as a Remove would; the hotspot is then built with the next one.
        val world = player.world
        val style = PlayerHouse.styleOf(player)
        val old = PohRooms.placedObj(PohRooms.furniture(player).getValue(spot.key(room)), style, PohHeraldry.crestIndex(player))
        val cost = PohMenagerie.UPGRADES[next.key] ?: PohSuperiorGarden.UPGRADES[next.key] ?: PohGallery.upgradeCost(spot.hotspot.decos[index]!!, next) ?: spot.hotspot.decos[index]?.let { PohCostume.upgradeCost(it, next) }
        val needed = cost ?: pieceOf(next)
        val ok = player.skills.getCurrentLevel(Skills.CONSTRUCTION) >= needed.level && needed.materials.all { (item, qty) -> PohBuild.countOf(player, item) >= qty }
        if (!ok) {
            PohBuild.canBuild(task, needed)
            return
        }
        if (old != null) PohRooms.objectsAt(world, spot.pieceTile(world, old)).filter { it.id == old }.forEach { world.remove(it) }
        placePiece(task, room, spot, next, cost)
    }

    private suspend fun removeFurniture(
        task: QueueTask,
        room: PohRooms.Built,
        spot: PohRooms.Spot,
    ) {
        val player = task.player
        if (!PohBuild.requireBuildMode(player)) return
        val world = player.world
        if (PohRooms.furniture(player)[spot.key(room)]?.let { PohRooms.decoKey(it) } in PohMenagerie.PET_HOUSES && PohMenagerie.stored(player) > 0) {
            task.messageBox("You'll have to take your pets out of the pet house before you can remove it.")
            return
        }
        val costumeKey = PohRooms.furniture(player)[spot.key(room)]?.let { PohRooms.decoKey(it) }
        if (costumeKey != null && PohCostume.holdsItems(player, costumeKey)) {
            // OSRS: a piece that stores items can't be removed while it holds them; it can be upgraded, the contents stay.
            val i = spot.hotspot.decos.indexOfFirst { it?.key == costumeKey }
            val next = spot.hotspot.decos.getOrNull(i + 1)
            if (next == null) {
                task.messageBox("You'll need to empty it before you can remove it.")
                return
            }
            if (PohCostume.upgradeCost(spot.hotspot.decos[i]!!, next) == null) return
            if (task.options("Upgrade to ${next.name.lowercase()} (contents stay)", "Cancel", title = "It isn't empty - you can't remove it.") != 1) return
            upgradeAt(task, room, spot)
            return
        }
        if (!PohBuild.confirmRemove(task)) return
        // Read again after the dialogue: the piece may have changed meanwhile.
        val built = PohRooms.furniture(player)
        val deco = built[spot.key(room)]?.let { PohFurniture.DECOS[PohRooms.decoKey(it)] } ?: return
        val style = PlayerHouse.styleOf(player)
        // A clipped topiary is still the topiary hotspot's piece (PohSuperiorGarden.baseDeco).
        val targets = targets(player, room, spot, spot.hotspot.decos.indexOf(PohGallery.baseDeco(PohSuperiorGarden.baseDeco(deco)))).filter { (s, _) -> built[s.key(room)] != null }
        player.lock = LockState.FULL
        player.animate(PohBuild.REMOVE_ANIM)
        task.wait(2)
        targets.forEach { (s, _) ->
            val id = built[s.key(room)]?.let { PohRooms.placedObj(it, style, PohHeraldry.crestIndex(player)) }
            if (id != null) {
                PohRooms.objectsAt(world, s.pieceTile(world, id)).filter { it.id == id }.forEach { world.remove(it) }
            }
            world.spawn(DynamicObject(s.loc, s.type, s.houseRot, s.tile))
            PohRooms.setFurniture(player, s.key(room), null)
        }
        player.unlock()
        PohMenagerie.refreshPets(player)
    }
}
