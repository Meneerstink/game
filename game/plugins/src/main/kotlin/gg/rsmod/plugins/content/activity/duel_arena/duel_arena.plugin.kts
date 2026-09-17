package gg.rsmod.plugins.content.activity.duel_arena

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.KILLER_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.mechanics.death.SafeDeath
import gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions
import gg.rsmod.plugins.content.mechanics.trading.getTradeSession

/**
 * Q-055 Duel Arena. Real rule/equipment-lock data and real static Al Kharid arena/lobby tiles - see [DuelArenaData].
 *
 * RCV-010 C2-b: every rule is now enforced through the shared gates (see [DuelArenaRules]): food, drinks, prayer and
 * curses, special attacks, teleports and summoning via [ActivityRestrictions]; attack style, target and fun weapons
 * via `can_attack`; equipment locks via `can_equip_any_item`; No Movement via the engine movement restriction; the
 * Novite 3-2-1 countdown, rule exclusivity, accept validation, logout handling and payout overflow.
 *
 * RCV-010 C2-a: the native 667 flow (lobby "Challenge" option, 640 challenge type, 631/637 rules, 628 stake inventory,
 * 626/639 confirmation, 634 spoils) lives in [DuelArenaInterfaces] and the bindings at the bottom of this file. The
 * older commands stay as a fallback: `duel <name>`, `duelrule <name>`, `duellock <slot>`, `dueloffer <slot> <amount>`,
 * `duelaccept`, `dueldecline`, `forfeit`.
 */
val CHALLENGE_ATTR = AttributeKey<Player>()

fun Player.busyWithSomethingElse(): Boolean = getDuelMatch() != null || getTradeSession() != null || isLocked()

on_command("duel") {
    val name = player.getCommandArgs().getOrNull(0) ?: run { player.message("Usage: duel <name>"); return@on_command }
    val target = player.world.players.firstOrNull { it.username.equals(name, ignoreCase = true) }
    if (target == null || target == player) {
        player.message("That player isn't online.")
        return@on_command
    }
    if (player.busyWithSomethingElse() || target.busyWithSomethingElse()) {
        player.message("One of you is already busy.")
        return@on_command
    }
    target.attr[CHALLENGE_ATTR] = player
    player.message("Sending ${target.username} a request...")
    target.message("${player.username} wishes to duel with you(stake). Use duelaccept to begin configuring rules.")
}

on_command("duelaccept") {
    val existingMatch = player.getDuelMatch()
    if (existingMatch != null) {
        if (existingMatch.stage != DuelStage.CONFIGURING) return@on_command
        DuelArenaRules.acceptRefusal(existingMatch, player)?.let {
            player.message(it)
            return@on_command
        }
        existingMatch.setAccepted(player, true)
        player.message("Waiting for other player...")
        existingMatch.other(player).message("Other player has accepted.")
        if (existingMatch.bothAccepted()) startDuel(existingMatch)
        return@on_command
    }
    val challenger = player.attr[CHALLENGE_ATTR]
    if (challenger == null) {
        player.message("You have no pending duel challenge.")
        return@on_command
    }
    player.attr.remove(CHALLENGE_ATTR)
    if (challenger.busyWithSomethingElse() || player.busyWithSomethingElse()) {
        player.message("Your challenger is no longer available.")
        return@on_command
    }
    val newMatch = DuelArenaMatch(challenger, player)
    challenger.attr[DUEL_MATCH_ATTR] = newMatch
    player.attr[DUEL_MATCH_ATTR] = newMatch
    val rules = DuelRule.values().joinToString(", ") { it.label }
    val locks = DuelEquipLock.values().joinToString(", ") { it.slot.name }
    challenger.message("${player.username} accepted your challenge. Configure with duelrule <name>, duellock <slot>, dueloffer <slot> <amount>, then duelaccept.")
    player.message("Configuring a duel with ${challenger.username}. Rules: $rules. Locks: $locks.")
}

on_command("duelrule") {
    val match = player.getDuelMatch()?.takeIf { it.stage == DuelStage.CONFIGURING } ?: run { player.message("You're not configuring a duel."); return@on_command }
    val name = player.getCommandArgs().getOrNull(0)?.uppercase() ?: run { player.message("Usage: duelrule <name>"); return@on_command }
    val rule = DuelRule.values().firstOrNull { it.name == name || it.name.replace("_", "") == name.replace("_", "") }
    if (rule == null) {
        player.message("Unknown rule. Options: ${DuelRule.values().joinToString(", ") { it.name }}")
        return@on_command
    }
    DuelArenaRules.toggleRule(match, rule).forEach { player.message(it) }
    val state = if (rule in match.rules) "ON" else "OFF"
    challengerAndOpponentMessage(match, "${rule.label} is now $state. Both players must duelaccept again.")
}

on_command("duellock") {
    val match = player.getDuelMatch()?.takeIf { it.stage == DuelStage.CONFIGURING } ?: run { player.message("You're not configuring a duel."); return@on_command }
    val name = player.getCommandArgs().getOrNull(0)?.uppercase() ?: run { player.message("Usage: duellock <slot>"); return@on_command }
    val lock = DuelEquipLock.values().firstOrNull { it.name == name || it.slot.name == name }
    if (lock == null) {
        player.message("Unknown slot. Options: ${DuelEquipLock.values().joinToString(", ") { it.slot.name }}")
        return@on_command
    }
    if (lock in match.lockedSlots) match.lockedSlots.remove(lock) else match.lockedSlots.add(lock)
    match.resetAccepted()
    val state = if (lock in match.lockedSlots) "locked" else "unlocked"
    challengerAndOpponentMessage(match, "${lock.slot.name} is now $state. Both players must duelaccept again.")
}

on_command("dueloffer") {
    // Anti-dupe: stakes can only change while configuring, never once the fight has started.
    val match = player.getDuelMatch()?.takeIf { it.stage == DuelStage.CONFIGURING } ?: run { player.message("You're not configuring a duel."); return@on_command }
    val cmdArgs = player.getCommandArgs()
    val slot = cmdArgs.getOrNull(0)?.toIntOrNull()
    val amount = cmdArgs.getOrNull(1)?.toIntOrNull() ?: 1
    if (slot == null || slot !in 0 until player.inventory.capacity) {
        player.message("Usage: dueloffer <inventory slot> <amount>")
        return@on_command
    }
    val item = player.inventory[slot] ?: run { player.message("No item in that slot."); return@on_command }
    val count = minOf(amount, player.inventory.getItemCount(item.id))
    if (count <= 0) return@on_command
    val transaction = player.inventory.remove(item.id, count, assureFullRemoval = true, beginSlot = slot)
    if (transaction.hasSucceeded()) {
        match.stakeOf(player).add(item.id, count)
        match.resetAccepted()
        player.message("You stake ${count}x ${item.id}. Both players must duelaccept again.")
        match.other(player).message("${player.username} changed their stake. Both players must duelaccept again.")
    }
}

on_command("dueldecline") {
    val match = player.getDuelMatch() ?: run { player.message("You're not in a duel."); return@on_command }
    if (match.stage == DuelStage.FIGHTING) {
        player.message("You can't decline once the duel has started - use forfeit.")
        return@on_command
    }
    returnStakes(match)
    match.other(player).message("<col=ff0000>Other player declined the duel!")
    match.clear()
}

on_command("forfeit") {
    val match = player.getDuelMatch() ?: run { player.message("You're not in a duel."); return@on_command }
    DuelArenaRules.forfeitRefusal(match)?.let {
        player.message(it)
        return@on_command
    }
    endDuel(match, winner = match.other(player), loser = player)
}

fun challengerAndOpponentMessage(match: DuelArenaMatch, message: String) {
    match.challenger.message(message)
    match.opponent.message(message)
}

fun returnStakes(match: DuelArenaMatch) {
    match.challengerStake.rawItems.filterNotNull().forEach { DuelArenaRules.giveOrDrop(match.challenger, it) }
    match.opponentStake.rawItems.filterNotNull().forEach { DuelArenaRules.giveOrDrop(match.opponent, it) }
    match.challengerStake.removeAll()
    match.opponentStake.removeAll()
}

fun startDuel(match: DuelArenaMatch) {
    match.stage = DuelStage.FIGHTING
    val centre = DuelArenaLocations.pick(match.rules)
    match.arenaTile = centre
    val (first, second) = DuelArenaRules.battleTiles(match, centre)
    for (participant in listOf(match.challenger, match.opponent)) {
        for (lock in match.lockedSlots) {
            val existing = participant.equipment[lock.slot.id]
            if (existing != null) {
                participant.equipment[lock.slot.id] = null
                participant.inventory.add(existing.id, existing.amount)
            }
        }
    }
    match.challenger.moveTo(first)
    match.opponent.moveTo(second)
    DuelArenaRules.onFightStart(match)
    // Novite beginBattle: "3", "2", "1" then "FIGHT!", two ticks apart; attacks are refused until then.
    world.queue {
        for (count in 3 downTo 1) {
            if (match.stage != DuelStage.FIGHTING) return@queue
            listOf(match.challenger, match.opponent).forEach {
                if (it.isOnline) it.forceChat("$count")
            }
            wait(2)
        }
        if (match.stage == DuelStage.FIGHTING) {
            listOf(match.challenger, match.opponent).forEach {
                if (it.isOnline) {
                    it.attr[DuelArenaRules.CAN_FIGHT_ATTR] = true
                    it.forceChat("FIGHT!")
                }
            }
        }
    }
}

fun endDuel(match: DuelArenaMatch, winner: Player, loser: Player) {
    if (match.stage != DuelStage.FIGHTING) return
    match.stage = DuelStage.CONFIGURING
    if (!match.friendly) {
        val spoils = gg.rsmod.game.model.container.ItemContainer(match.stakeOf(loser))
        DuelArenaInterfaces.showSpoils(winner, loser, spoils)
    }
    DuelArenaRules.payout(match, winner)
    winner.message(DuelArenaRules.wonMessage(loser))
    loser.message(DuelArenaRules.lostMessage(winner))
    listOf(winner, loser).forEach { DuelArenaRules.onFightEnd(it) }
    winner.moveTo(DuelArenaLocations.LOBBY.random())
    loser.moveTo(DuelArenaLocations.LOBBY.random())
    match.clear()
}

on_world_init {
    SafeDeath.register { player ->
        val match = player.getDuelMatch()
        match != null && match.stage == DuelStage.FIGHTING && DuelArenaLocations.inArena(player.tile)
    }
    ActivityRestrictions.register(DuelArenaRules::activityRefusal)
}

can_attack { attacker, target ->
    if (attacker !is Player) return@can_attack true
    val weapon = attacker.equipment[gg.rsmod.plugins.api.EquipmentType.WEAPON.id]?.id ?: -1
    val refusal = DuelArenaRules.attackRefusal(attacker, target, CombatConfigs.getCombatClass(attacker), weapon) ?: return@can_attack true
    if (world.plugins.notifyAttackRefusal) attacker.message(refusal)
    false
}

can_equip_any_item { player, item ->
    val refusal = DuelArenaRules.equipRefusal(player, item) ?: return@can_equip_any_item true
    player.message(refusal)
    false
}

/*
 * Dying while a duel is still being configured (the lobby is not a safe zone under Deadman rules)
 * used to leave the match open with both stakes in escrow. Treat it like a decline, before the
 * loot is resolved, so the stake is back with its owner - the same rule the trade session applies.
 */
on_player_pre_death {
    val match = player.getDuelMatch() ?: return@on_player_pre_death
    if (match.stage != DuelStage.CONFIGURING) return@on_player_pre_death
    returnStakes(match)
    match.other(player).message("<col=ff0000>Other player declined the duel!")
    match.clear()
}

on_player_death {
    val match = player.getDuelMatch() ?: return@on_player_death
    if (match.stage != DuelStage.FIGHTING) return@on_player_death
    val killer = player.attr[KILLER_ATTR]?.get() as? Player
    val winner = if (killer != null && killer == match.other(player)) killer else match.other(player)
    endDuel(match, winner = winner, loser = player)
}

/* ------------------------- RCV-010 C2-a: native 667 screens (Novite DuelArena/DuelControler) ------------------------- */

val DUEL_LOBBY_TIMER = TimerKey()

on_login {
    player.timers[DUEL_LOBBY_TIMER] = 1
}

on_timer(DUEL_LOBBY_TIMER) {
    DuelArenaInterfaces.refreshLobby(player)
    player.timers[DUEL_LOBBY_TIMER] = 2
}

on_player_option(option = "Challenge") {
    val target = player.getInteractingPlayer()
    if (!DuelArenaInterfaces.inLobby(player.tile)) return@on_player_option
    if (player.busyWithSomethingElse() || target.busyWithSomethingElse()) {
        player.message("The other player is busy.")
        return@on_player_option
    }
    val incoming = player.attr[DuelArenaInterfaces.CHALLENGED_BY_ATTR]?.get()
    if (incoming == target) {
        // Accepting a challenge: both players configure the same match.
        player.attr.remove(DuelArenaInterfaces.CHALLENGED_BY_ATTR)
        val friendly = player.attr[DuelArenaInterfaces.CHALLENGED_FRIENDLY_ATTR] ?: false
        player.attr.remove(DuelArenaInterfaces.CHALLENGED_FRIENDLY_ATTR)
        val match = DuelArenaMatch(target, player)
        match.friendly = friendly
        target.attr[DUEL_MATCH_ATTR] = match
        player.attr[DUEL_MATCH_ATTR] = match
        DuelArenaInterfaces.openRules(match, target)
        DuelArenaInterfaces.openRules(match, player)
        return@on_player_option
    }
    DuelArenaInterfaces.openChallengeScreen(player, target)
}

DuelArenaInterfaces.CHALLENGE_FRIENDLY_BUTTONS.forEach { component ->
    on_button(DuelArenaInterfaces.CHALLENGE_SCREEN, component) { DuelArenaInterfaces.chooseChallengeType(player, friendly = true) }
}
DuelArenaInterfaces.CHALLENGE_STAKE_BUTTONS.forEach { component ->
    on_button(DuelArenaInterfaces.CHALLENGE_SCREEN, component) { DuelArenaInterfaces.chooseChallengeType(player, friendly = false) }
}
on_button(DuelArenaInterfaces.CHALLENGE_SCREEN, DuelArenaInterfaces.CHALLENGE_SEND) { DuelArenaInterfaces.sendChallenge(player) }

fun configuring(player: Player): DuelArenaMatch? =
    player.getDuelMatch()?.takeIf { it.stage == DuelStage.CONFIGURING }

listOf(DuelArenaInterfaces.STAKE_RULES, DuelArenaInterfaces.FRIENDLY_RULES).forEach { rulesInterface ->
    DuelRule.values().forEach { rule ->
        on_button(rulesInterface, if (rulesInterface == DuelArenaInterfaces.STAKE_RULES) rule.id631 else rule.id637) {
            val match = configuring(player)?.takeIf { !it.confirming } ?: return@on_button
            DuelArenaRules.toggleRule(match, rule).forEach { player.message(it) }
            DuelArenaInterfaces.refreshStakes(match)
            listOf(match.challenger, match.opponent).forEach { DuelArenaInterfaces.refreshStatus(match, it) }
        }
    }
    DuelEquipLock.values().forEach { lock ->
        on_button(rulesInterface, if (rulesInterface == DuelArenaInterfaces.STAKE_RULES) lock.id631 else lock.id637) {
            val match = configuring(player)?.takeIf { !it.confirming } ?: return@on_button
            if (lock in match.lockedSlots) match.lockedSlots.remove(lock) else match.lockedSlots.add(lock)
            match.resetAccepted()
            DuelArenaInterfaces.refreshStakes(match)
            listOf(match.challenger, match.opponent).forEach { DuelArenaInterfaces.refreshStatus(match, it) }
        }
    }
}

fun acceptRules(player: Player) {
    val match = configuring(player)?.takeIf { !it.confirming } ?: return
    DuelArenaRules.acceptRefusal(match, player)?.let {
        player.message(it)
        return
    }
    match.setAccepted(player, true)
    if (match.bothAccepted()) {
        DuelArenaInterfaces.openConfirmation(match)
    } else {
        listOf(match.challenger, match.opponent).forEach { DuelArenaInterfaces.refreshStatus(match, it) }
    }
}

fun acceptConfirmation(player: Player) {
    val match = configuring(player)?.takeIf { it.confirming } ?: return
    match.setAccepted(player, true)
    if (match.bothAccepted()) {
        listOf(match.challenger, match.opponent).forEach { it.closeInterface(DuelArenaInterfaces.confirmInterface(match)) }
        startDuel(match)
    } else {
        listOf(match.challenger, match.opponent).forEach { DuelArenaInterfaces.refreshStatus(match, it) }
    }
}

fun declineNative(player: Player) {
    val match = configuring(player) ?: return
    returnStakes(match)
    val other = match.other(player)
    match.clear()
    listOf(player, other).forEach {
        it.closeInterface(dest = InterfaceDestination.MAIN_SCREEN)
        it.closeInterface(dest = InterfaceDestination.TAB_AREA)
    }
    other.message("<col=ff0000>Other player declined the duel!")
}

on_button(DuelArenaInterfaces.STAKE_RULES, DuelArenaInterfaces.STAKE_ACCEPT) { acceptRules(player) }
on_button(DuelArenaInterfaces.FRIENDLY_RULES, DuelArenaInterfaces.FRIENDLY_ACCEPT) { acceptRules(player) }
on_button(DuelArenaInterfaces.STAKE_CONFIRM, DuelArenaInterfaces.STAKE_CONFIRM_ACCEPT) { acceptConfirmation(player) }
on_button(DuelArenaInterfaces.FRIENDLY_CONFIRM, DuelArenaInterfaces.FRIENDLY_CONFIRM_ACCEPT) { acceptConfirmation(player) }
on_button(DuelArenaInterfaces.STAKE_RULES, DuelArenaInterfaces.STAKE_DECLINE) { declineNative(player) }
on_button(DuelArenaInterfaces.FRIENDLY_RULES, DuelArenaInterfaces.FRIENDLY_DECLINE) { declineNative(player) }
on_button(DuelArenaInterfaces.STAKE_CONFIRM, DuelArenaInterfaces.STAKE_CONFIRM_DECLINE) { declineNative(player) }
on_button(DuelArenaInterfaces.FRIENDLY_CONFIRM, DuelArenaInterfaces.FRIENDLY_CONFIRM_DECLINE) { declineNative(player) }
on_button(DuelArenaInterfaces.SPOILS, DuelArenaInterfaces.SPOILS_CLAIM) { player.closeInterface(DuelArenaInterfaces.SPOILS) }

listOf(DuelArenaInterfaces.STAKE_RULES, DuelArenaInterfaces.FRIENDLY_RULES, DuelArenaInterfaces.STAKE_CONFIRM, DuelArenaInterfaces.FRIENDLY_CONFIRM).forEach { id ->
    on_interface_close(id) {
        val match = configuring(player) ?: return@on_interface_close
        // Moving from the rules screen to the confirmation screen closes the first one on purpose.
        if (match.confirming && (id == DuelArenaInterfaces.STAKE_RULES || id == DuelArenaInterfaces.FRIENDLY_RULES)) return@on_interface_close
        declineNative(player)
    }
}

fun stakeAmount(opcode: Int): Int? =
    when (opcode) {
        61 -> 1
        64 -> 5
        4 -> 10
        else -> null
    }

on_button(DuelArenaInterfaces.STAKE_INVENTORY, 0) {
    val match = configuring(player) ?: return@on_button
    val slot = player.getInteractingSlot()
    val opcode = player.getInteractingOpcode()
    val item = player.inventory[slot] ?: return@on_button
    player.queue(TaskPriority.WEAK) {
        val amount = stakeAmount(opcode) ?: when (opcode) {
            52 -> player.inventory.getItemCount(item.id)
            81 -> inputInt("Enter Amount:")
            else -> return@queue
        }
        if (DuelArenaInterfaces.addStake(match, player, slot, amount)) {
            DuelArenaInterfaces.refreshStakes(match)
            listOf(match.challenger, match.opponent).forEach { DuelArenaInterfaces.refreshStatus(match, it) }
        }
    }
}

on_button(DuelArenaInterfaces.STAKE_RULES, DuelArenaInterfaces.STAKE_REMOVE_COMPONENT) {
    val match = configuring(player) ?: return@on_button
    val slot = player.getInteractingSlot()
    val opcode = player.getInteractingOpcode()
    val item = match.stakeOf(player)[slot] ?: return@on_button
    player.queue(TaskPriority.WEAK) {
        val amount = stakeAmount(opcode) ?: when (opcode) {
            52 -> match.stakeOf(player).getItemCount(item.id)
            81 -> inputInt("Enter Amount:")
            else -> return@queue
        }
        if (DuelArenaInterfaces.removeStake(match, player, slot, amount)) {
            DuelArenaInterfaces.refreshStakes(match)
            listOf(match.challenger, match.opponent).forEach { DuelArenaInterfaces.refreshStatus(match, it) }
        }
    }
}

// Novite `logout()`: leaving mid-fight loses the duel; leaving while configuring returns both stakes.
on_logout {
    val match = player.getDuelMatch() ?: return@on_logout
    if (match.stage == DuelStage.FIGHTING) {
        endDuel(match, winner = match.other(player), loser = player)
    } else {
        returnStakes(match)
        match.other(player).message("<col=ff0000>Other player declined the duel!")
        match.clear()
    }
}
