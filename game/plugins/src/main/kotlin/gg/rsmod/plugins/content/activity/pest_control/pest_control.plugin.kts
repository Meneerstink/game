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

// N205 (owner 2026-10-22: "summoning familiars moeten kunnen meehelpen", every combat familiar attacks pests and portals): no
// summoning ban in a game any more (Darkan PestControlGameController refused it). A familiar fights through the player's own
// canAttack, so a shielded portal stays out of reach for it too. Owner 2026-10-10: teleporting out of a minigame such as Pest
// Control is allowed and instant (Darkan refused it), see DeadmanTimerGate.inMinigame; the game's tick drops whoever left.

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

/*
 * Elite void (owner 2026-10-04: Pest Control rewards = OSRS - the elite top and robe cost 200 points each and replace the normal piece).
 * This cache's 2009 reward interface 267 has no elite slot, so the upgrade uses Darkan's route (CommendationExchange.kt: a Void Knight
 * top or robe used on Commodore Tyr) at the OSRS price instead of Darkan's 100. Darkan's 11681 is the quest multinpc (invisible here, no
 * Void Stares Back); the outpost spawns his visible form 11682 (owner 2026-10-04 "make Tyr visible").
 */
val COMMODORE_TYR = Npcs.COMMODORE_TYR_11682

mapOf(
    Items.VOID_KNIGHT_TOP to Items.ELITE_VOID_KNIGHT_TOP,
    Items.VOID_KNIGHT_TOP_10611 to Items.ELITE_VOID_KNIGHT_TOP,
    Items.VOID_KNIGHT_ROBE to Items.ELITE_VOID_KNIGHT_ROBE,
).forEach { (piece, elite) ->
    on_item_on_npc(item = piece, npc = COMMODORE_TYR) {
        // Owner 2026-10-10: no requirement besides the points (no Hard Western Provinces Diary, no won-games count).
        if ((player.attr[PEST_POINTS] ?: 0) < ELITE_VOID_UPGRADE_POINTS) {
            player.message("You need $ELITE_VOID_UPGRADE_POINTS Void Knight Commendation Points to upgrade void knight armour.")
            return@on_item_on_npc
        }
        player.queue {
            val choice = options("Yes, upgrade my armour.", "Nevermind.", title = "Upgrade this piece to elite for $ELITE_VOID_UPGRADE_POINTS commendations?")
            val points = player.attr[PEST_POINTS] ?: 0
            if (choice != 1 || points < ELITE_VOID_UPGRADE_POINTS || !player.inventory.remove(piece).hasSucceeded()) return@queue
            player.inventory.add(elite)
            gg.rsmod.game.model.attr.PointBalance.take(player.attr, PEST_POINTS, ELITE_VOID_UPGRADE_POINTS) // N106: never below 0
            player.message("<col=571D07>Remaining Void Knight Commendation Points: ${player.attr[PEST_POINTS]}")
        }
    }
}
