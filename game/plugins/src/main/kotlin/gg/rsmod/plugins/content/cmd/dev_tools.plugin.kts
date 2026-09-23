package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.timer.TimerKey
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/*
 * Owner 2026-09-23 developer mode for world editing with the agent:
 *  - `devmode` toggles the RSPS client's id display on right-click (locs show "(id @ x,z,level)", npcs and ground items
 *    their id) and its "Tag" / "Tag tile" entries (client marker "__RSPS_DEV__:on|off").
 *  - While on, the player's tile is written to C:/RSPS/screens/position.txt so the agent always knows where he stands.
 *  - `devtag x z level locId` (sent by the client's Tag entries) appends the tile, loc and an optional note to
 *    C:/RSPS/screens/tags.txt, e.g. the exact spots for 78 banners.
 */

val DEV_POSITION_TIMER = TimerKey()
val DEV_DIR = File("C:/RSPS/screens")
/** Saved with the player: developer mode stays on across logins until switched off. */
val DEV_ON = gg.rsmod.game.model.attr.AttributeKey<Boolean>("dev_mode_on")
val DEV_LAST_TILE = gg.rsmod.game.model.attr.AttributeKey<Tile>()

fun writePosition(player: Player) {
    val tile = player.tile
    if (player.attr[DEV_LAST_TILE] == tile) return
    player.attr[DEV_LAST_TILE] = tile
    runCatching {
        DEV_DIR.mkdirs()
        File(DEV_DIR, "position.txt").writeText("${player.username} ${tile.x},${tile.z},${tile.height}\n")
    }
}

on_command("devmode", Privilege.ADMIN_POWER) {
    val on = player.attr[DEV_ON] != true
    player.attr[DEV_ON] = on
    player.message("__RSPS_DEV__:${if (on) "on" else "off"}", type = ChatMessageType.CONSOLE)
    if (on) {
        writePosition(player)
        player.timers[DEV_POSITION_TIMER] = 2
    } else {
        player.timers.remove(DEV_POSITION_TIMER)
    }
    player.message("Developer mode ${if (on) "on: right-click shows ids and 'Tag'." else "off."}")
}

on_login {
    if (player.attr[DEV_ON] != true || !player.privilege.powers.contains(Privilege.ADMIN_POWER)) return@on_login
    player.message("__RSPS_DEV__:on", type = ChatMessageType.CONSOLE)
    player.timers[DEV_POSITION_TIMER] = 2
}

on_timer(DEV_POSITION_TIMER) {
    if (player.attr[DEV_ON] != true) return@on_timer
    writePosition(player)
    player.timers[DEV_POSITION_TIMER] = 2
}

on_command("devtag", Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    val x = args.getOrNull(0)?.toIntOrNull() ?: return@on_command
    val z = args.getOrNull(1)?.toIntOrNull() ?: return@on_command
    val level = args.getOrNull(2)?.toIntOrNull() ?: 0
    val locId = args.getOrNull(3)?.toIntOrNull() ?: -1
    val note = args.drop(4).joinToString(" ")
    val name = if (locId >= 0) runCatching { world.definitions.get(ObjectDef::class.java, locId).name }.getOrNull() ?: "?" else "tile"
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    val line = "$stamp\t$x,$z,$level\t${if (locId >= 0) "loc $locId '$name'" else "tile"}${if (note.isNotEmpty()) "\t$note" else ""}"
    runCatching {
        DEV_DIR.mkdirs()
        File(DEV_DIR, "tags.txt").appendText(line + "\n")
    }
    player.message("Tagged $x,$z,$level ${if (locId >= 0) "($name $locId)" else ""}")
}
