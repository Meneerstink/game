package gg.rsmod.plugins.content.areas.poh

/**
 * CONSTRUCTION-99: building rooms and furniture in the player's own house ([PohConstruct], [PohRooms], [PohFurniture]).
 */
on_world_init {
    world.plugins.bindObjectOverride { p, obj, opt -> PohConstruct.override(p, obj, opt) }
}

/**
 * Test command (admin): `condev rooms | clear | add <TYPE> <x> <z> [rot] | furn | level <n>` - reports and sets up the built rooms;
 * every real route is the in-game one (doorway / hotspot Build).
 */
on_command("condev", gg.rsmod.game.model.priv.Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    when (args.getOrNull(0)) {
        "clear" -> {
            player.attr.remove(PohRooms.ROOMS_ATTR)
            player.attr.remove(PohRooms.FURNITURE_ATTR)
            PlayerHouse.rebuild(player)
        }
        "add" -> {
            val type = PohRooms.RoomType.valueOf(args[1].uppercase())
            PohRooms.addRoom(player, PohRooms.Built(type, args[2].toInt(), args[3].toInt(), 0, args.getOrNull(4)?.toInt() ?: 0))
            PlayerHouse.rebuild(player)
        }
        "furn" -> PohRooms.furniture(player).forEach { (k, v) -> player.message("[con] $k = $v", type = ChatMessageType.CONSOLE) }
    }
    val lvl = player.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.CONSTRUCTION)
    player.message(
        "[con] rooms=${PohRooms.rooms(player).joinToString(" ") { it.encode() }} count=${PohRooms.roomCount(player)}/${PohRooms.maxRooms(lvl)} " +
            "dim=${PohRooms.maxDimension(lvl)} cell=${PlayerHouse.offsetInHouse(player.tile)?.let { (it.first shr 3) to (it.second shr 3) }} " +
            "decos=${PohFurniture.DECOS.size} hotspots=${PohFurniture.BUILDABLE.size}",
        type = ChatMessageType.CONSOLE,
    )
}
