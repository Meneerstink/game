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
            PohRooms.addRoom(player, PohRooms.Built(type, args[2].toInt(), args[3].toInt(), args.getOrNull(5)?.toInt() ?: 0, args.getOrNull(4)?.toInt() ?: 0))
            PlayerHouse.rebuild(player)
        }
        "tmpl" -> {
            // condev tmpl <STYLE> <chunkX> <chunkZ>: the objects of a style's template chunk (ids + names), for the sweep's findings.
            val style = PlayerHouse.Style.valueOf(args[1].uppercase())
            val t = style.chunk(args[2].toInt(), args[3].toInt())
            val objs = world.chunks.get(t.chunkCoords, createIfNeeded = true)!!.getEntities<gg.rsmod.game.model.entity.StaticObject>(gg.rsmod.game.model.EntityType.STATIC_OBJECT)
                .filter { it.tile.height == t.height }
            val names = objs.groupBy { it.id }.map { (id, l) -> "$id:${world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, id).name}x${l.size}" }
            names.chunked(12).forEach { player.message("[tmpl] ${it.joinToString(" ")}", type = ChatMessageType.CONSOLE) }
            if (args.getOrNull(4) == "pos") {
                objs.filter { world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, it.id).name.endsWith("space", true) }.sortedBy { it.id }
                    .chunked(8).forEach { l -> player.message("[tmplpos] " + l.joinToString(" ") { "${it.id}@${it.tile.x - t.x},${it.tile.z - t.z}r${it.rot}" }, type = ChatMessageType.CONSOLE) }
            }
        }
        "fill" -> player.message("[con] filled " + PohSweep.fill(player, args[1].toInt(), args[2].toInt(), args.getOrElse(3) { "0" }.toInt(), args.getOrElse(4) { "99" }.toInt()), type = ChatMessageType.CONSOLE)
        "sweep" -> {
            val arg = args.getOrNull(1)?.uppercase()
            val styles = if (arg == "ALL") PlayerHouse.Style.values().toList() else listOf(arg?.let { PlayerHouse.Style.valueOf(it) } ?: PlayerHouse.styleOf(player))
            PohSweep.run(player, styles).take(60).forEach { player.message(it, type = ChatMessageType.CONSOLE) }
        }
        "furn" -> PohRooms.furniture(player).forEach { (k, v) -> player.message("[con] $k = $v", type = ChatMessageType.CONSOLE) }
    }
    val lvl = player.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.CONSTRUCTION)
    player.message(
        "[con] rooms=${PohRooms.rooms(player).joinToString(" ") { it.encode() }} count=${PohRooms.roomCount(player)}/${PohRooms.maxRooms(lvl)} " +
            "dim=${PohRooms.maxDimension(lvl)} cell=${PohRooms.cellOf(player.tile)} " +
            "decos=${PohFurniture.DECOS.size} hotspots=${PohFurniture.BUILDABLE.size}",
        type = ChatMessageType.CONSOLE,
    )
}

// Bones on a built altar (the lit gilded altar 13199 keeps its own offering in prayer_altar.plugin.kts).
gg.rsmod.plugins.content.skills.prayer.burying.BoneData.values.forEach { data ->
    PohFurnitureUse.ALTARS.filter { it != PlayerHouse.ALTAR }.forEach { altar ->
        on_item_on_obj(obj = altar, item = data.bone) {
            val obj = player.getInteractingGameObj()
            if (PlayerHouse.houseArea(obj.tile) != null) PohFurnitureUse.offerBones(player, obj, data.bone)
        }
    }
}
