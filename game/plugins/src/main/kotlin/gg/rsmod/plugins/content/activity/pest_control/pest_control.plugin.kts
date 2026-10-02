package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.priv.Privilege

/**
 * Hook wiring for Pest Control. See PestControlHandler.kt for the sources and the flow.
 */

// ---- landers ------------------------------------------------------------------------------------------------------------

for (tier in PestControlTier.values()) {
    on_obj_option(obj = tier.gangplankId, option = "cross") {
        PestControlHandler.board(player, tier)
    }
    on_obj_option(obj = tier.exitLadderId, option = "climb") {
        if (PestControlHandler.lobbyOf(player) == null) {
            player.moveTo(tier.leaveTile)
        } else {
            PestControlHandler.leaveLander(player, move = true)
        }
    }
}

// Bug search 2026-10-02 (BS3): the commands stay a remote route (PestControlHandler.boardRemote).
on_command("pestcontrolnovice") { PestControlHandler.boardRemote(player, PestControlTier.NOVICE) }
on_command("pestcontrolintermediate") { PestControlHandler.boardRemote(player, PestControlTier.INTERMEDIATE) }
on_command("pestcontrolveteran") { PestControlHandler.boardRemote(player, PestControlTier.VETERAN) }

// Admin: depart now whatever the team size (pcstart novice|intermediate|veteran) - testing on a quiet server.
on_command("pcstart", Privilege.ADMIN_POWER) {
    val name = player.getCommandArgs().firstOrNull()?.uppercase()
    val tier = PestControlTier.values().firstOrNull { it.name == name } ?: PestControlHandler.lobbyOf(player)
    if (tier == null) {
        player.message("Usage: pcstart novice|intermediate|veteran (or board a lander first).")
        return@on_command
    }
    if (!PestControlHandler.forceStart(world, tier)) player.message("Nobody is waiting on the ${tier.displayName.lowercase()} lander.")
}

// ---- the game ---------------------------------------------------------------------------------------------------------

world.timers[PC_MASTER_TICK] = 1
on_timer(PC_MASTER_TICK) {
    PestControlHandler.tick(world)
    world.timers[PC_MASTER_TICK] = 1
}

on_npc_option(npc = PC_LEAVE_SQUIRE, option = "leave") {
    if (PestControlHandler.gameOf(player) == null) return@on_npc_option
    PestControlHandler.leaveGame(player, move = true)
}

on_npc_option(npc = PC_LEAVE_SQUIRE, option = "talk-to") {
    if (PestControlHandler.gameOf(player) == null) return@on_npc_option
    player.queue { chatNpc("Be quick, we're under attack!") }
}

on_npc_death(Npcs.PORTAL, Npcs.PORTAL_6143, Npcs.PORTAL_6144, Npcs.PORTAL_6145, Npcs.PORTAL_6150, Npcs.PORTAL_6151, Npcs.PORTAL_6152, Npcs.PORTAL_6153) {
    PestControlHandler.gameOf(npc)?.onPortalDeath(npc)
}

on_npc_death(*PC_KNIGHT_IDS) {
    PestControlHandler.gameOf(npc)?.let { if (it.knight === npc) it.end(success = false) }
}

on_npc_death(*(3727..3731).toList().toIntArray()) {
    PestControlHandler.gameOf(npc)?.let { PestControlMonsters.splatterExplode(it, npc, dying = true) }
}

on_npc_death(*(3747..3751).toList().toIntArray()) {
    PestControlHandler.gameOf(npc)?.spinnerPortal?.remove(npc)
}

// Dying in the game is safe (items kept) and puts the player back at the landing.
gg.rsmod.plugins.content.mechanics.death.SafeDeath.register { PestControlHandler.respawnTile(it) != null }

on_player_death {
    val tile = PestControlHandler.respawnTile(player) ?: return@on_player_death
    player.moveTo(tile)
}

on_logout {
    if (PestControlHandler.lobbyOf(player) != null) PestControlHandler.leaveLander(player, move = true)
    if (player.attr.has(PestControlHandler.GAME_ATTR)) PestControlHandler.leaveGame(player, move = true)
}

// ---- barricades, gates, towers ----------------------------------------------------------------------------------------

for (id in PC_REPAIRABLE_BARRICADE_IDS) {
    on_obj_option(obj = id, option = "repair") { PestControlBarricades.repair(player, player.getInteractingGameObj()) }
}
for (id in 14237..14248) {
    on_obj_option(obj = id, option = "repair") { PestControlBarricades.repair(player, player.getInteractingGameObj()) }
}
for (id in 14233..14248) {
    on_obj_option(obj = id, option = if (id % 2 != 0) "open" else "close") { PestControlBarricades.toggleGate(player, player.getInteractingGameObj()) }
}
on_obj_option(obj = PC_TOWER_LADDER, option = "climb") { PestControlBarricades.climbTower(player, player.getInteractingGameObj()) }

// ---- rewards ----------------------------------------------------------------------------------------------------------

for (knight in intArrayOf(Npcs.VOID_KNIGHT_3786, Npcs.VOID_KNIGHT_3788, Npcs.VOID_KNIGHT_3789)) {
    on_npc_option(npc = knight, option = "exchange") { PestControlRewards.open(player) }
}

on_button(PC_REWARD_INTERFACE, PestControlRewards.BUTTONS.toTypedArray()) {
    PestControlRewards.select(player, player.attr[gg.rsmod.game.model.attr.INTERACTING_BUTTON_ID] ?: -1)
}

on_button(PC_REWARD_INTERFACE, PestControlRewards.CONFIRM) {
    PestControlRewards.confirm(player)
}

on_button(PC_REWARD_INTERFACE, 94) {
    player.closeInterface(PC_REWARD_INTERFACE)
}
