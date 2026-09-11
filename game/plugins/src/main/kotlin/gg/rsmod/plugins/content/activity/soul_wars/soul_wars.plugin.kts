package gg.rsmod.plugins.content.activity.soul_wars

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Objs
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.mechanics.death.SafeDeath

/**
 * Soul Wars (2010). Real ids/tiles adapted from a same-cache-family GitHub Kotlin source
 * (kennethyork/single-rs-2012, `darkan-world-server`, `content/minigames/soulwars/SoulWars.kt`),
 * fetched read-only per this project's GitHub-fallback rule after confirming neither donor has
 * real Soul Wars game logic (Void has only supporting data tomls, Novite has nothing at all).
 * Every id was independently re-verified against this project's own 667 cache before use:
 * `Npcs.AVATAR_OF_CREATION`/`AVATAR_OF_DESTRUCTION`/`NOMAD`, `Items.BONES_14638`/
 * `SOUL_FRAGMENT_14646`/`RED_CAPE`/`BLUE_CAPE`, and every `Objs.*_420xx` id below matched the
 * donor's declared constants exactly by both number AND target's own auto-generated name -
 * strong evidence this GitHub source targets the same cache revision as this project. Interface
 * 836/837's real component/varc/varp wiring (`runInterfaceHookProbeTool`) was also spot-checked
 * and found byte-identical to the donor's declared varc scheme. Objects 42029/42031's real
 * placements (`runObjectPlacementProbeTool`) matched the donor's tile logic exactly.
 *
 * Known, disclosed gaps (real working vertical slice, not full parity):
 * - The soul avatars are spawned as plain npcs with their own cache-native combat stats; the
 *   donor's custom AI (attack only players inside specific "danger" proximity chunks, and reduce
 *   incoming damage to 0 from any attacker whose Slayer level is below the avatar's tracked
 *   level) is NOT wired - this engine has no per-instance Npc subclassing (`class Npc` has a
 *   `private constructor`, behavior is attached via global hooks, not overridden methods), and a
 *   safe equivalent (a verified pre-hit-damage-filter hook) wasn't found within this batch's time
 *   budget. The avatar's tracked "level" (raised by burying bones, lowered by the enemy team's
 *   soul fragments) still drives the real health-percentage overlay and the win condition, so the
 *   core objective loop is meaningful even without this specific damage-reduction rule.
 * - Zone control uses real tile bounding boxes instead of the donor's literal chunk-id integers
 *   (those are specific to the donor's own different chunk-hashing scheme and don't transfer
 *   between engines) - same real tile coordinates, different (but equivalent) presence check.
 * - A real donor bug was found and NOT replicated: the source's own zeal-award message text says
 *   a draw grants "2 Zeal" but its code only ever produces 1 or 3 (no draw branch exists) -
 *   implemented against the donor's evident intent (win=3, draw=2, loss=1) instead of copying the
 *   inconsistency forward.
 * - No points shop / cosmetic rewards for spent Zeal are wired this batch (Zeal is tracked per
 *   player via `SW_ZEAL` but has no spend path yet).
 */

val SW_TICK_TIMER = TimerKey()

on_world_init {
    world.timers[SW_TICK_TIMER] = 1
    SafeDeath.register { player -> player.attr[SW_TEAM] != null }
}

on_timer(SW_TICK_TIMER) {
    SoulWarsHandler.tick(world)
    if (SoulWarsMatch.lobbyPlayers.isNotEmpty()) {
        SoulWarsMatch.lobbyTicks++
        if (SoulWarsMatch.lobbyTicks % SoulWarsData.TICKS_BETWEEN_GAME_ATTEMPTS == 0) {
            SoulWarsHandler.attemptStartGame(world)
        }
    }
    for (p in SoulWarsMatch.lobbyPlayers) SoulWarsHandler.updateLobbyVars(p)
    for (p in SoulWarsMatch.blueTeam) SoulWarsHandler.updateIngameVars(p)
    for (p in SoulWarsMatch.redTeam) SoulWarsHandler.updateIngameVars(p)
    world.timers[SW_TICK_TIMER] = 1
}

// Real, independently-confirmed lobby entry points (both lead into the same auto-balanced
// lobby - Soul Wars has no "choose your team" step, per the donor's SoulWars.init logic).
on_obj_option(Objs.BLUE_BARRIER_42029, "Pass") {
    SoulWarsHandler.joinLobby(player)
}
on_obj_option(Objs.RED_BARRIER_42030, "Pass") {
    SoulWarsHandler.joinLobby(player)
}
on_obj_option(Objs.BALANCE_PORTAL, "Join-team") {
    SoulWarsHandler.joinLobby(player)
}
on_obj_option(Objs.SOUL_WARS_PORTAL, "Enter") {
    player.moveTo(SoulWarsData.LOBBY_ENTRY_TILE)
}

on_command("soulwarsleave") {
    if (SoulWarsMatch.lobbyPlayers.contains(player)) {
        SoulWarsHandler.leaveLobby(player)
        player.message("You leave the Soul Wars lobby.")
    }
}

// Soul Wars-specific "Bones" (item 14638) is a distinct cache item from the real Prayer-training
// Bones (item 526) - verified via target's own generated Items.kt naming, no conflict with the
// existing Prayer bone-burying binding.
on_item_option(Items.BONES_14638, "Bury") {
    val team = player.attr[SW_TEAM] ?: return@on_item_option
    if (SoulWarsHandler.bury(player, team)) {
        player.inventory.remove(Items.BONES_14638, 1)
    }
}

on_item_on_obj(obj = Objs.SOUL_OBELISK, item = Items.SOUL_FRAGMENT_14646) {
    handleSoulFragment(player)
}
on_item_on_obj(obj = Objs.SOUL_OBELISK_42011, item = Items.SOUL_FRAGMENT_14646) {
    handleSoulFragment(player)
}
on_item_on_obj(obj = Objs.SOUL_OBELISK_42012, item = Items.SOUL_FRAGMENT_14646) {
    handleSoulFragment(player)
}

fun handleSoulFragment(player: Player) {
    val team = player.attr[SW_TEAM] ?: return
    val held = player.inventory.getItemCount(Items.SOUL_FRAGMENT_14646)
    if (held <= 0) return
    val used = SoulWarsHandler.useSoulFragmentOnObelisk(player, team, held)
    if (used > 0) {
        player.inventory.remove(Items.SOUL_FRAGMENT_14646, used)
    }
}

on_npc_death(Npcs.AVATAR_OF_CREATION) {
    SoulWarsHandler.onAvatarDeath(SoulWarsTeam.BLUE, world)
}
on_npc_death(Npcs.AVATAR_OF_DESTRUCTION) {
    SoulWarsHandler.onAvatarDeath(SoulWarsTeam.RED, world)
}

// Teammates can't attack each other; avatars are only attackable by the opposing team.
can_attack { attacker, target ->
    val avatarTeam =
        when (target) {
            SoulWarsMatch.blueAvatar -> SoulWarsTeam.BLUE
            SoulWarsMatch.redAvatar -> SoulWarsTeam.RED
            else -> null
        }
    if (avatarTeam != null) {
        val attackerTeam = (attacker as? Player)?.attr?.get(SW_TEAM)
        return@can_attack attackerTeam != null && attackerTeam != avatarTeam
    }
    val attackerTeam = (attacker as? Player)?.attr?.get(SW_TEAM)
    val targetTeam = (target as? Player)?.attr?.get(SW_TEAM)
    if (attackerTeam != null && targetTeam != null) {
        return@can_attack attackerTeam != targetTeam
    }
    true
}

on_player_death {
    val team = player.attr[SW_TEAM] ?: return@on_player_death
    val respawn = if (team == SoulWarsTeam.BLUE) SoulWarsData.BLUE_RESPAWN_AREA.first else SoulWarsData.RED_RESPAWN_AREA.first
    player.moveTo(respawn)
}
