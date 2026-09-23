package gg.rsmod.plugins.content.mechanics.worldedit

import gg.rsmod.game.model.priv.Privilege

/*
 * Live world edits ([WorldEdits], data/world_edits.json). Applied at boot; every command below saves the file and
 * re-applies it immediately, so nobody has to log out or restart. The dev* commands are sent by the RSPS client's
 * developer-mode right-click entries (see `devmode`).
 */

/** 78 house banner locs (HouseBannerTool ids): the raised wall banner and the free-standing standard. */
val BANNER_78 = 62751
val STANDARD_78 = 62750

/** The small 78 banner that fits bank walls (HouseBannerTool smallLoc). */
val SMALL_78 = 62753

fun intArgs(player: Player): List<Int> = player.getCommandArgs().mapNotNull { it.toIntOrNull() }

/** The 78 banner hangs on a wall: wall decoration (type 4), so it never takes the wall's own client layer slot. */
fun defaultType(id: Int): Int = if (id == BANNER_78 || id == SMALL_78) 4 else 10

fun locName(player: Player, id: Int): String = runCatching { player.world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, id).name }.getOrNull() ?: "$id"

/**
 * One loc per client layer per tile: refuse a placement that would silently replace [what] holds that layer, and
 * tell the owner what is in the way (it can be removed or picked first).
 */
fun blocked(player: Player, tile: Tile, type: Int, moving: gg.rsmod.game.model.entity.GameObject? = null): Boolean {
    val occupant = WorldEdits.occupant(player.world, tile, type) ?: return false
    if (occupant === moving) return false
    player.message("That spot already holds ${locName(player, occupant.id)} (${occupant.id}) in the same layer - remove or move it first.")
    return true
}

on_world_init {
    // A couple of ticks after boot, so every npc spawn plugin (Skully, bankers ...) has run and npc edits find them.
    world.queue {
        wait(2)
        val applied = WorldEdits.reload(world)
        println("WorldEdits: applied $applied edits from ${WorldEdits.FILE}")
    }
}

// devmoveloc / devcopyloc  fromX fromZ level id toX toZ toLevel
on_command("devmoveloc", Privilege.ADMIN_POWER) {
    val a = intArgs(player)
    if (a.size < 7) return@on_command
    val obj = WorldEdits.objectsAt(world, Tile(a[0], a[1], a[2])).firstOrNull { it.id == a[3] }
    if (obj != null && blocked(player, Tile(a[4], a[5], a[6]), obj.type, moving = obj)) return@on_command
    val ok = WorldEdits.moveLoc(world, Tile(a[0], a[1], a[2]), a[3], Tile(a[4], a[5], a[6]), copy = false)
    player.message(if (ok) "Moved ${a[3]} to ${a[4]},${a[5]},${a[6]}." else "That object is no longer there.")
}

on_command("devcopyloc", Privilege.ADMIN_POWER) {
    val a = intArgs(player)
    if (a.size < 7) return@on_command
    val obj = WorldEdits.objectsAt(world, Tile(a[0], a[1], a[2])).firstOrNull { it.id == a[3] }
    if (obj != null && blocked(player, Tile(a[4], a[5], a[6]), obj.type)) return@on_command
    val ok = WorldEdits.moveLoc(world, Tile(a[0], a[1], a[2]), a[3], Tile(a[4], a[5], a[6]), copy = true)
    player.message(if (ok) "Copied ${a[3]} to ${a[4]},${a[5]},${a[6]}." else "That object is no longer there.")
}

// devnpc remove|rotate x z level id   /   devnpc move|copy x z level id toX toZ toLevel
on_command("devnpc", Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    val op = args.getOrNull(0) ?: return@on_command
    val a = args.drop(1).mapNotNull { it.toIntOrNull() }
    if (a.size < 4) return@on_command
    val to = if (a.size >= 7) Tile(a[4], a[5], a[6]) else null
    val ok = WorldEdits.editNpc(world, op, Tile(a[0], a[1], a[2]), a[3], to)
    player.message(if (ok) "Npc ${a[3]}: $op done." else "Npc ${a[3]} not found there.")
}

on_command("reloadedits", Privilege.ADMIN_POWER) {
    player.message("World edits reloaded: ${WorldEdits.reload(world)} applied.")
}

// undoedit [count]: takes back the last click(s) - a whole click at a time, however many edits it made.
on_command("undoedit", Privilege.ADMIN_POWER) {
    val count = player.getCommandArgs().getOrNull(0)?.toIntOrNull()?.coerceIn(1, 1000) ?: 1
    val dropped = WorldEdits.undo(world, count)
    player.message(if (dropped == 0) "No world edits to undo." else "Undone $count click(s) ($dropped edits).")
}

// undoall: takes back every world edit in the file.
on_command("undoall", Privilege.ADMIN_POWER) {
    val dropped = WorldEdits.undo(world, Int.MAX_VALUE / 2)
    player.message("All world edits undone ($dropped edits).")
}

// devinfo x z level id: full details of a loc (type, rotation, whether an edit placed it).
on_command("devinfo", Privilege.ADMIN_POWER) {
    val a = intArgs(player)
    if (a.size < 4) return@on_command
    val tile = Tile(a[0], a[1], a[2])
    WorldEdits.objectsAt(world, tile).filter { it.id == a[3] }.forEach {
        val def = world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, it.id)
        player.message("${def.name} ${it.id} at ${tile.x},${tile.z},${tile.height} type=${it.type} rot=${it.rot} size=${def.width}x${def.length} ${it.entityType}")
    }
}

// devnpcinfo x z level id
on_command("devnpcinfo", Privilege.ADMIN_POWER) {
    val a = intArgs(player)
    if (a.size < 4) return@on_command
    val npc = WorldEdits.npcAt(world, Tile(a[0], a[1], a[2]), a[3])
    if (npc == null) {
        player.message("Npc ${a[3]} not found there.")
        return@on_command
    }
    player.message("${npc.name} ${npc.id} index=${npc.index} at ${npc.tile.x},${npc.tile.z},${npc.tile.height} post=${npc.spawnTile.x},${npc.spawnTile.z} walkRadius=${npc.walkRadius}")
}


// devremove x z level id
on_command("devremove", Privilege.ADMIN_POWER) {
    val a = intArgs(player)
    if (a.size < 4) return@on_command
    WorldEdits.removeLoc(world, Tile(a[0], a[1], a[2]), a[3])
    player.message("Removed ${a[3]} at ${a[0]},${a[1]},${a[2]} (undoedit to take it back).")
}

// devrotate x z level id
on_command("devrotate", Privilege.ADMIN_POWER) {
    val a = intArgs(player)
    if (a.size < 4) return@on_command
    val ok = WorldEdits.rotateLoc(world, Tile(a[0], a[1], a[2]), a[3])
    player.message(if (ok) "Rotated ${a[3]} at ${a[0]},${a[1]},${a[2]}." else "Nothing with id ${a[3]} on that tile.")
}

// devplace x z level id [rot] [type]  - also typed by hand: place id [rot] [type] puts it on your own tile.
on_command("devplace", Privilege.ADMIN_POWER) {
    val a = intArgs(player)
    if (a.size < 4) return@on_command
    val type = a.getOrNull(5) ?: defaultType(a[3])
    if (blocked(player, Tile(a[0], a[1], a[2]), type)) return@on_command
    WorldEdits.add(world, WorldEdits.Edit("spawn", a[3], a[0], a[1], a[2], type = type, rot = a.getOrNull(4) ?: 0))
    player.message("Placed ${locName(player, a[3])} at ${a[0]},${a[1]},${a[2]} - right-click it to Rotate.")
}

on_command("place", Privilege.ADMIN_POWER) {
    val a = intArgs(player)
    if (a.isEmpty()) {
        player.message("Usage: place id [rot] [type]")
        return@on_command
    }
    val t = player.tile
    WorldEdits.add(world, WorldEdits.Edit("spawn", a[0], t.x, t.z, t.height, type = a.getOrNull(2) ?: 10, rot = a.getOrNull(1) ?: 0))
    player.message("Placed ${a[0]} at ${t.x},${t.z},${t.height}.")
}
