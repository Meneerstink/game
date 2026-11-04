package gg.rsmod.plugins.content.mechanics.moderation

import gg.rsmod.game.message.impl.LogoutFullMessage
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.priv.Punishments
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.game.service.world.BannedPlayers
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.getCommandArgs
import gg.rsmod.plugins.api.ext.getInteractingOpcode
import gg.rsmod.plugins.api.ext.inputString
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.game.tools.importer.StaffCommandsInterfaceImportTool as Ui

/*
 * Owner 2026-10-10 staff tools: moderators may kick, mute, ban and jail (plus ::teleto / ::teletome and the command list);
 * admins everything; the owner keeps his own commands. Rules: Moderation.kt (Void Mute/Ban, Novite jail). Player names with a
 * space are typed with an underscore, as in every other command.
 */

fun Plugin.targetName(index: Int = 0): String? = player.getCommandArgs().getOrNull(index)?.replace("_", " ")

/** The online player named by the first argument, or null (the staff member is told). */
fun Plugin.onlineTarget(usage: String): Player? {
    val name = targetName() ?: run {
        player.message("Usage: $usage")
        return null
    }
    val target = world.getPlayerForName(name)
    if (target == null) player.message("$name is not online.")
    return target
}

fun Plugin.mayPunish(target: Player): Boolean {
    if (Moderation.mayPunish(player, target)) return true
    player.message("You can't punish ${target.username}.")
    return false
}

fun kickNow(target: Player) {
    target.requestLogout()
    target.write(LogoutFullMessage())
    target.channelClose()
}

// ---- the command list -------------------------------------------------------------------------------------------------

// Owner 2026-10-10: "::mod" opens it ("maak er mod van"); ::commands / ::cmds stay as aliases.
listOf("mod", "commands", "cmds").forEach { name ->
    on_command(name, Privilege.MOD_POWER) {
        // A fresh ::mod starts on Players (A17); "::mod tele" searches every command at once.
        val search = player.getCommandArgs().joinToString(" ")
        StaffCommands.open(player, search, 0)
    }
}

on_button(interfaceId = StaffCommands.INTERFACE_ID, component = Ui.SEARCH) {
    player.queue {
        val text = inputString("Search the commands (name or what it does):")
        StaffCommands.open(player, text)
    }
}

on_button(interfaceId = StaffCommands.INTERFACE_ID, component = Ui.CLEAR) {
    StaffCommands.open(player, "", 0)
}

Ui.TABS.forEachIndexed { index, id ->
    on_button(interfaceId = StaffCommands.INTERFACE_ID, component = id) {
        // A17: a tab shows its own category again, so a click also ends a search.
        StaffCommands.open(player, "", index)
    }
}

on_button(interfaceId = StaffCommands.INTERFACE_ID, component = gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.CLOSE) {
    player.closeInterface(StaffCommands.INTERFACE_ID)
}

(0 until Ui.ROWS).forEach { r ->
    on_button(interfaceId = StaffCommands.INTERFACE_ID, component = Ui.row(r)) {
        when (player.getInteractingOpcode()) {
            StaffCommands.OPCODE_INFO -> StaffCommands.show(player, r)
            else -> StaffCommands.run(player, r)
        }
    }
}

// Owner 2026-10-10: the owner's gear presets (GearPresets), one command each; in the list through Search (A17, VRAGEN 17 = A).
GearPresets.ALL.forEach { preset ->
    on_command(preset.command, Privilege.OWNER_POWER) {
        GearPresets.apply(player, preset)
    }
}

// Owner 2026-11-04 ('je bent dood want je bot prayt niet'): boss tests on the team test accounts died between two restores.
// ::god toggles "take no damage" - only for the team test accounts (TeamAccountPresets), never for a real account.
on_command("god", Privilege.OWNER_POWER) {
    if (TeamAccountPresets.forName(player.username) == null) {
        player.message("::god is only for the team test accounts.")
        return@on_command
    }
    if (player.hitModifier == null) {
        player.hitModifier = { hit -> hit.hitmarks.forEach { it.damage = 0 } }
        player.message("God mode on: you take no damage (test account).")
    } else {
        player.hitModifier = null
        player.message("God mode off.")
    }
}

// ---- mute -------------------------------------------------------------------------------------------------------------

on_command("mute", Privilege.MOD_POWER) {
    val target = onlineTarget("::mute <player> <minutes> (0 = permanent)") ?: return@on_command
    if (!mayPunish(target)) return@on_command
    val minutes = player.getCommandArgs().getOrNull(1)?.toIntOrNull() ?: 60
    val until = Punishments.untilMinutes(minutes)
    target.attr[Punishments.MUTED_UNTIL] = until
    target.message("You have been muted ${Punishments.remaining(until)} for breaking a rule.")
    player.message("${target.username} is muted ${Punishments.remaining(until)}.")
    Moderation.log(player, "mute", target.username, if (minutes <= 0) "permanent" else "$minutes min")
}

on_command("unmute", Privilege.MOD_POWER) {
    val target = onlineTarget("::unmute <player>") ?: return@on_command
    target.attr.remove(Punishments.MUTED_UNTIL)
    target.message("You have been unmuted.")
    player.message("${target.username} is unmuted.")
    Moderation.log(player, "unmute", target.username)
}

// ---- ban (online or offline, by name) ----------------------------------------------------------------------------------

on_command("ban", Privilege.MOD_POWER) {
    val args = player.getCommandArgs()
    val name = targetName() ?: run {
        player.message("Usage: ::ban <player> <hours> <reason> (0 hours = permanent)")
        return@on_command
    }
    val hours = args.getOrNull(1)?.toIntOrNull() ?: 48
    val reason = args.drop(2).joinToString(" ").ifBlank { "No reason given" }
    val online = world.getPlayerForName(name)
    if (online != null && !mayPunish(online)) return@on_command
    if (online == null) {
        // Review 2026-10-10: an offline account is checked too - no ban for a typo, none for an equal or higher rank.
        val savedRank = Moderation.offlinePrivilegeId(name)
        if (savedRank == null) {
            player.message("There is no account named $name.")
            return@on_command
        }
        if (!Moderation.mayPunishOffline(player, savedRank)) {
            player.message("You can't punish $name.")
            return@on_command
        }
    }
    val until = Punishments.untilHours(hours)
    BannedPlayers.ban(name, until, "${player.username}: $reason")
    online?.let(::kickNow)
    player.message("$name is banned ${Punishments.remaining(until)}${if (online == null) " (offline)" else ""}. Reason: $reason")
    Moderation.log(player, "ban", name, "${if (hours <= 0) "permanent" else "$hours h"} - $reason")
}

on_command("unban", Privilege.MOD_POWER) {
    val name = targetName() ?: run {
        player.message("Usage: ::unban <player>")
        return@on_command
    }
    if (BannedPlayers.unban(name)) {
        player.message("$name is unbanned.")
        Moderation.log(player, "unban", name)
    } else {
        player.message("$name is not banned.")
    }
}

// ---- jail (Novite JailCommand / JailControler) ------------------------------------------------------------------------

on_command("jail", Privilege.MOD_POWER) {
    val target = onlineTarget("::jail <player> <hours> (0 = until ::unjail)") ?: return@on_command
    if (!mayPunish(target)) return@on_command
    val hours = player.getCommandArgs().getOrNull(1)?.toIntOrNull() ?: 1
    val until = Punishments.untilHours(hours)
    Moderation.jail(target, until)
    target.message("You have been jailed ${Punishments.remaining(until)}!")
    player.message("You have jailed ${target.username} ${Punishments.remaining(until)}.")
    Moderation.log(player, "jail", target.username, if (hours <= 0) "until unjail" else "$hours h")
}

on_command("unjail", Privilege.MOD_POWER) {
    val target = onlineTarget("::unjail <player>") ?: return@on_command
    if (!Punishments.isJailed(target)) {
        player.message("${target.username} is not jailed.")
        return@on_command
    }
    Moderation.release(target)
    player.message("${target.username} is released from jail.")
    Moderation.log(player, "unjail", target.username)
}

on_timer(Moderation.JAIL_TIMER) { Moderation.check(player) }

on_login {
    if (player.attr.has(Punishments.JAILED_UNTIL)) Moderation.check(player)
}

// Novite JailControler.sendDeath: a jailed player who dies is put back in a cell.
on_player_death {
    if (Punishments.isJailed(player)) player.moveTo(Moderation.jailTile(player))
}

// Novite JailControler.processMagicTeleport / processItemTeleport: no teleport of any kind.
gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.register { p, action ->
    if (action == gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.TELEPORT && Punishments.isJailed(p)) Moderation.JAILED_MESSAGE else null
}

on_world_init {
    // Novite JailControler.processObjectClick1: no object (doors included) while jailed.
    world.plugins.bindObjectOverride { p, _, _ ->
        if (!Punishments.isJailed(p) || p.privilege.powers.contains(Privilege.MOD_POWER)) return@bindObjectOverride false
        p.message("You cannot do any activities while being jailed.")
        true
    }
}
