package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.prepareForTeleport
import gg.rsmod.plugins.content.magic.teleport

/** Building mode, the dining table, the garden theme, room rotation and the demon butler - see [PohBuild] and [PohServant]. */

on_obj_option(obj = PlayerHouse.TABLE_SPACE, option = "build") {
    val space = player.getInteractingGameObj()
    player.queue { PohBuild.buildTable(this, space) }
}

PohBuild.DINING_TABLES.forEach { table ->
    on_obj_option(obj = table.obj, option = "remove") {
        val obj = player.getInteractingGameObj()
        player.queue { PohBuild.removeTable(this, obj) }
    }
}

on_obj_option(obj = PlayerHouse.THEME_SPACE, option = "build") {
    player.queue { PohBuild.buildTheme(this) }
}

on_obj_option(obj = PlayerHouse.OTHERWORLDLY_THEME, option = "remove") {
    player.queue { PohBuild.removeTheme(this) }
}

// Every style's door hotspots (667 locs named "Door hotspot"): in building mode a doorway's Build rotates the room behind it.
(15300..15320).filter { world.definitions.get(ObjectDef::class.java, it).name == "Door hotspot" }.forEach { hotspot ->
    on_obj_option(obj = hotspot, option = "build") {
        val obj = player.getInteractingGameObj()
        player.queue { PohBuild.rotateRoom(this, obj) }
    }
}

// The dining room's rope bell-pull calls the servant.
on_obj_option(obj = PlayerHouse.BELL_PULL, option = "ring") {
    PohServant.call(player)
}

on_obj_option(obj = PlayerHouse.BELL_PULL, option = "remove") {
    player.message("The bell-pull is part of the house; it can't be removed.")
}

on_npc_option(npc = PohServant.NPC, option = "talk-to") {
    val servant = player.getInteractingNpc()
    player.queue { PohServant.talk(this, servant) }
}

on_npc_option(npc = PohServant.NPC, option = "fetch-from-bank") {
    val servant = player.getInteractingNpc()
    player.queue { PohServant.fetchMenu(this, servant) }
}

on_any_item_on_npc(PohServant.NPC) {
    val servant = player.getInteractingNpc()
    val item = player.getInteractingItem()
    player.queue { PohServant.itemOn(this, servant, item) }
}

/*
 * Construct. cape (owner 2026-10-10 night run: perks like OSRS; menus by ConstructionCapeMenuTool). OSRS Wiki "Construct. cape":
 * unlimited teleports to the house ("Tele to POH") and to the house-portal towns ("Teleport"), from the inventory or worn; the
 * teleports are the jewellery kind every cape teleport here uses and pass the same Deadman gates (canTeleport). Yanille is left
 * out like everywhere else in the house code (OSRS Deadman disables its portal).
 */
val CONSTRUCTION_CAPES = intArrayOf(Items.CONSTRUCT_CAPE, Items.CONSTRUCT_CAPE_T)

fun capeToHouse(player: Player) {
    player.canTeleport(TeleportType.JEWELRY) {
        val type = TeleportType.JEWELRY
        player.lock = LockState.FULL_WITH_DAMAGE_IMMUNITY
        player.queue(TaskPriority.STRONG) {
            player.prepareForTeleport()
            type.startSound?.let { player.playSound(it) }
            player.animate(type.animation)
            type.graphic?.let { player.graphic(it) }
            wait(type.teleportDelay)
            player.animate(Anims.RESET)
            player.unlock()
            PlayerHouse.enter(player)
        }
    }
}

fun capeToPortal(player: Player) {
    player.queue {
        val towns = PlayerHouse.Location.values().filter { it.offered }
        val pick = options(*towns.map { it.displayName }.toTypedArray(), title = "Teleport to which house portal?")
        val town = towns.getOrNull(pick - 1) ?: return@queue
        player.canTeleport(TeleportType.JEWELRY) {
            val spot = gg.rsmod.plugins.content.mechanics.pvp.CityGuards.nearestWalkable(world, town.exit, radius = 3) ?: town.exit
            player.teleport(spot, TeleportType.JEWELRY)
        }
    }
}

CONSTRUCTION_CAPES.forEach { cape ->
    on_item_option(item = cape, option = "Tele to POH") { capeToHouse(player) }
    on_item_option(item = cape, option = "Teleport") { capeToPortal(player) }
    on_equipment_option(item = cape, option = "Tele to POH") { capeToHouse(player) }
    on_equipment_option(item = cape, option = "Teleport") { capeToPortal(player) }
}

/**
 * Test command for the house (admin only; the night-run client checks): `pohdev info | enter | build on|off | rot <room> <0-3> |
 * reset | location <n>`. Every real route stays the in-game one; this only sets up and reports the state.
 */
on_command("pohdev", gg.rsmod.game.model.priv.Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    fun info() {
        val room = PlayerHouse.roomAt(player.tile)
        val rotations = PlayerHouse.Room.values().joinToString(" ") { "${it.name}=${PlayerHouse.roomRotation(player, it)}" }
        player.message(
            "[poh] tile=${player.tile} room=$room offset=${PlayerHouse.offsetInHouse(player.tile)} build=${PlayerHouse.isBuildMode(player)} " +
                "table=${PlayerHouse.diningTableOf(player)} theme=${PlayerHouse.hasGardenTheme(player)} loc=${PlayerHouse.locationOf(player)} " +
                "style=${PlayerHouse.styleOf(player)} rot: $rotations",
            type = ChatMessageType.CONSOLE,
        )
    }
    when (args.getOrNull(0)) {
        "enter" -> PlayerHouse.enter(player)
        "build" -> PohBuild.setBuildMode(player, args.getOrNull(1) == "on")
        "rot" -> {
            val room = PlayerHouse.Room.valueOf(args[1].uppercase())
            PlayerHouse.setRoomRotation(player, room, args[2].toInt())
            PlayerHouse.rebuild(player)
        }
        "reset" -> {
            player.attr.remove(PlayerHouse.DINING_TABLE_ATTR)
            player.attr.remove(PlayerHouse.GARDEN_THEME_ATTR)
            player.attr.remove(PlayerHouse.ROOM_ROTATIONS_ATTR)
            PohBuild.setBuildMode(player, false)
        }
        "location" -> player.attr[PlayerHouse.LOCATION_ATTR] = args[1].toInt()
        "style" -> PlayerHouse.setStyle(player, PlayerHouse.Style.values()[args[1].toInt()])
        "rebuild" -> PlayerHouse.rebuild(player)
        // Pest Control state for the elite void check at Commodore Tyr: pohdev pest <wins> <points>.
        "unskull" -> { player.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER); gg.rsmod.plugins.content.mechanics.pvp.RiskSkull.refresh(player) }
        "pest" -> {
            player.attr[gg.rsmod.plugins.content.activity.pest_control.PEST_WINS] = args[1].toInt()
            gg.rsmod.game.model.attr.PointBalance.set(player.attr, gg.rsmod.plugins.content.activity.pest_control.PEST_POINTS, args[2].toLong())
        }
        // The rotations the door-hotspot dialogue offers per room, and the door hotspots of the house the player stands in.
        "allowed" -> {
            PlayerHouse.Room.values().forEach { room ->
                player.message("[poh] allowed ${room.name}=${PlayerHouse.allowedRotations(world, PlayerHouse.styleOf(player), room)}", type = ChatMessageType.CONSOLE)
            }
            val map = world.instanceAllocator.getMap(player.tile)
            if (map != null) {
                val doors = mutableListOf<String>()
                for (x in map.area.bottomLeftX until map.area.topRightX) for (z in map.area.bottomLeftZ until map.area.topRightZ) {
                    val t = gg.rsmod.game.model.Tile(x, z, 0)
                    world.chunks.get(t, createIfNeeded = false)?.getEntities<gg.rsmod.game.model.entity.GameObject>(t, gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)
                        ?.filter { world.definitions.get(ObjectDef::class.java, it.id).name == "Door hotspot" }
                        ?.forEach { doors += "${it.id}@${x - map.area.bottomLeftX},${z - map.area.bottomLeftZ}r${it.rot}" }
                }
                player.message("[poh] door hotspots: ${doors.joinToString(" ")}", type = ChatMessageType.CONSOLE)
            }
        }
        // An item stored in the removed costume room (as an old save holds it): the next login must move it to the bank.
        "store" -> {
            val family = PohStorage.Family.values().first { it.ids.contains(args[1].toInt()) }
            PohStorage.put(player, family, args[1].toInt())
        }
        // Every nexus row and jewellery box destination: its landing tile must exist and be walkable.
        "leaves" -> {
            val boxes = gg.rsmod.game.tools.importer.PohTeleportInterfaceImportTool.BOXES
            val box =
                boxes.flatMapIndexed { i, b ->
                    b.destinations.indices.mapNotNull { d -> PohTeleportUi.jewelleryLeaf(gg.rsmod.game.tools.importer.PohTeleportInterfaceImportTool.Box.button(i, d)) }
                }
            val all = PohTeleportUi.NEXUS_ROWS.map { "nexus" to it } + box.map { "box" to it }
            var bad = 0
            all.forEach { (where, leaf) ->
                val tile = leaf.tile(player)
                val ok = tile != null && gg.rsmod.plugins.content.mechanics.pvp.CityGuards.nearestWalkable(world, tile, radius = 0) == tile
                if (!ok) {
                    bad++
                    player.message("[poh] $where '${leaf.name}' -> $tile NOT WALKABLE", type = ChatMessageType.CONSOLE)
                }
            }
            player.message("[poh] leaves checked=${all.size} bad=$bad", type = ChatMessageType.CONSOLE)
        }
        // The nexus row / jewellery destination by name, through the same route a click on the row takes.
        "go" -> {
            val name = args.drop(1).joinToString(" ")
            val boxes = gg.rsmod.game.tools.importer.PohTeleportInterfaceImportTool.BOXES
            val box =
                boxes.flatMapIndexed { i, b ->
                    b.destinations.indices.mapNotNull { d -> PohTeleportUi.jewelleryLeaf(gg.rsmod.game.tools.importer.PohTeleportInterfaceImportTool.Box.button(i, d)) }
                }
            val leaf = (PohTeleportUi.NEXUS_ROWS + box).firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (leaf == null) player.message("[poh] no leaf '$name'", type = ChatMessageType.CONSOLE) else PohTeleportUi.go(player, PohTeleportUi.NEXUS, leaf)
        }
    }
    info()
}
