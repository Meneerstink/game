package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.tools.importer.CasinoInterfaceImportTool as Layout

/*
 * Automated player-versus-player Flower Poker (owner 2026-09-20).
 *
 * Roat Pkz wiki: "a fully automated, player-versus-player (PvP) game - no hosts are required", "no house
 * advantage or interference". There is no dealer to trust and no host to run off with the pot: the server
 * escrows both stakes, plants the flowers from a commitment both players saw beforehand, and pays the winner.
 *
 * A challenge is issued with `fp <name>` rather than a right-click option, because the player menu slots in this
 * revision are fixed client-side (`OpPlayerExtraMessage`: OPPLAYER1 and 5-10) and "Challenge" already belongs to
 * the Duel Arena. Binding a second handler to it would be a duplicate bind and would refuse to boot.
 */

/** How long the challenge invite stays open, in ticks (about 30 seconds). */
val FP_INVITE_TICKS = 50

/** Ticks between each flower appearing, so a match reads as a plant rather than a flash. */
val FP_PLANT_DELAY = 2

/** Ticks a decided round stays on screen before a replant. */
val FP_ROUND_PAUSE = 4

val FP_PENDING = AttributeKey<String>()

fun fpBusy(player: Player): Boolean = player.getFlowerMatch() != null || Casino.hasLiveRound(player)

fun fpOpen(match: FlowerPokerMatch) {
    CasinoScreens.Flower.open(match.challenger)
    CasinoScreens.Flower.open(match.opponent)
}

fun fpRefresh(
    match: FlowerPokerMatch,
    shown: Int = FlowerPoker.HAND_SIZE,
    status: String? = null,
) {
    CasinoScreens.Flower.refresh(match.challenger, shown, status)
    CasinoScreens.Flower.refresh(match.opponent, shown, status)
}

fun fpMessage(
    match: FlowerPokerMatch,
    line: String,
) {
    match.challenger.message(line)
    match.opponent.message(line)
}

/**
 * Plants the match out over several ticks and pays the winner.
 *
 * The whole match is already decided before the first flower appears - [FlowerPokerMatch.start] resolved it from
 * the three seeds - so this loop is presentation only. That matters: nothing a player does during the animation,
 * including logging out, can change the result or the payout.
 */
fun fpPlay(match: FlowerPokerMatch) {
    val world = match.challenger.world
    world.queue {
        for (round in match.rounds) {
            for (shown in 1..FlowerPoker.HAND_SIZE) {
                fpRefresh(match, shown, status = "Planting flower $shown of ${FlowerPoker.HAND_SIZE}...")
                wait(FP_PLANT_DELAY)
            }
            fpRefresh(match, FlowerPoker.HAND_SIZE, status = round.reason)
            fpMessage(match, round.reason)
            if (round.replant) {
                wait(FP_ROUND_PAUSE)
            }
        }
        val settlement = match.finish()
        if (settlement == null) {
            // Should not happen: play() always ends on a decided round. Refund rather than strand the pot.
            fpMessage(match, "The match could not be decided. Your stakes have been returned.")
            CasinoWallet.refund(match.challenger, match.stake)
            CasinoWallet.refund(match.opponent, match.stake)
            CasinoHistory.logAdjustment(match.challenger, CasinoGame.FLOWER_POKER, "undecided_refund", match.stake)
            CasinoHistory.logAdjustment(match.opponent, CasinoGame.FLOWER_POKER, "undecided_refund", match.stake)
            match.clear()
            return@queue
        }
        val winner = settlement.winner
        fpRefresh(match, FlowerPoker.HAND_SIZE, status = "${winner.username} wins ${CasinoWallet.format(settlement.pot)} coins!")
        fpMessage(match, "${winner.username} wins the pot of ${CasinoWallet.format(settlement.pot)} coins.")
        fpMessage(match, "Server seed revealed: ${settlement.revealedServerSeed}")
        winner.playSound(Sfx.COINS_JINGLE_1)
        CasinoScreens.announce(winner, settlement.credited)
        match.clear()
    }
}

/** Both sides have accepted: escrow the stakes and start planting. */
fun fpTryStart(match: FlowerPokerMatch) {
    if (!match.bothAccepted()) {
        return
    }
    when (val result = match.start()) {
        FlowerPokerMatch.StartResult.OK -> {
            fpMessage(match, "Both players accepted. Commitment: ${ProvablyFair.shortHash(match.serverSeedHash)}")
            fpPlay(match)
        }
        FlowerPokerMatch.StartResult.CHALLENGER_SHORT ->
            fpMessage(match, String.format(result.message, match.challenger.username))
        FlowerPokerMatch.StartResult.OPPONENT_SHORT ->
            fpMessage(match, String.format(result.message, match.opponent.username))
        else -> fpMessage(match, result.message)
    }
    fpRefresh(match)
}

on_command("fp") {
    val args = player.getCommandArgs()
    if (args.isEmpty()) {
        player.message("Use: fp &lt;player name&gt; - challenge someone to Flower Poker.")
        return@on_command
    }
    val name = args.joinToString(" ")
    val target = world.getPlayerForName(name)
    if (target == null || target == player) {
        player.message("Could not find '$name'.")
        return@on_command
    }
    if (fpBusy(player)) {
        player.message("Finish your current game first.")
        return@on_command
    }
    if (fpBusy(target)) {
        player.message("${target.username} is busy.")
        return@on_command
    }
    target.attr[FP_PENDING] = player.username
    player.message("You challenge ${target.username} to Flower Poker.")
    target.message("<col=ffff00>${player.username} challenges you to Flower Poker. Type 'fpaccept' to play.</col>")
    player.queue {
        wait(FP_INVITE_TICKS)
        if (target.attr[FP_PENDING] == player.username) {
            target.attr.remove(FP_PENDING)
            player.message("${target.username} did not answer your Flower Poker challenge.")
        }
    }
}

on_command("fpaccept") {
    val challengerName = player.attr[FP_PENDING]
    if (challengerName == null) {
        player.message("Nobody has challenged you to Flower Poker.")
        return@on_command
    }
    player.attr.remove(FP_PENDING)
    val challenger = world.getPlayerForName(challengerName)
    if (challenger == null) {
        player.message("$challengerName is no longer available.")
        return@on_command
    }
    if (fpBusy(player) || fpBusy(challenger)) {
        player.message("One of you is already in a game.")
        return@on_command
    }
    val match = FlowerPokerMatch(challenger, player)
    challenger.attr[FLOWER_MATCH_ATTR] = match
    player.attr[FLOWER_MATCH_ATTR] = match
    fpOpen(match)
    fpMessage(match, "Flower Poker: agree a stake, then both accept.")
}

on_button(interfaceId = Layout.FLOWER_ID, component = Layout.CLOSE) {
    val match = player.getFlowerMatch()
    if (match != null && match.stage == FlowerPokerMatch.Stage.CONFIGURING) {
        fpMessage(match, "${player.username} closed the Flower Poker screen.")
        match.cancel()
    }
    player.closeInterface(Layout.FLOWER_ID)
}

on_button(interfaceId = Layout.FLOWER_ID, component = Layout.Flower.STAKE_BUTTON) {
    val match = player.getFlowerMatch() ?: return@on_button
    if (match.stage != FlowerPokerMatch.Stage.CONFIGURING) return@on_button
    player.queue {
        val amount = inputInt("Stake each:")
        if (amount <= 0) return@queue
        match.setStake(amount.toLong())
        fpMessage(match, "${player.username} set the stake to ${CasinoWallet.format(match.stake)} coins each.")
        fpRefresh(match)
    }
}

on_button(interfaceId = Layout.FLOWER_ID, component = Layout.Flower.ACCEPT_BUTTON) {
    val match = player.getFlowerMatch() ?: return@on_button
    if (match.stage != FlowerPokerMatch.Stage.CONFIGURING) return@on_button
    if (match.stake < CasinoWallet.MIN_WAGER) {
        player.message(FlowerPokerMatch.StartResult.BAD_STAKE.message)
        return@on_button
    }
    match.setAccepted(player, true)
    fpRefresh(match)
    fpTryStart(match)
}

on_button(interfaceId = Layout.FLOWER_ID, component = Layout.Flower.DECLINE_BUTTON) {
    val match = player.getFlowerMatch() ?: return@on_button
    if (match.stage == FlowerPokerMatch.Stage.CONFIGURING) {
        fpMessage(match, "${player.username} declined.")
        match.cancel()
        match.challenger.closeInterface(Layout.FLOWER_ID)
        match.opponent.closeInterface(Layout.FLOWER_ID)
    }
}

on_button(interfaceId = Layout.FLOWER_ID, component = Layout.Flower.SET_SEED_BUTTON) {
    player.queue { CasinoDialogs.askClientSeed(this, player); player.getFlowerMatch()?.let { fpRefresh(it) } }
}

on_button(interfaceId = Layout.FLOWER_ID, component = Layout.Flower.VERIFY_BUTTON) {
    player.queue { CasinoDialogs.showFairness(this, player); player.getFlowerMatch()?.let { fpRefresh(it) } }
}

/*
 * A player who logs out while the stake screen is open simply cancels it - nothing is escrowed yet. A player who
 * logs out mid-plant does not stop the match: the result is already fixed, the pot is already escrowed, and
 * fpPlay pays the winner whether or not both clients are watching.
 */
on_logout {
    val match = player.getFlowerMatch()
    if (match != null && match.stage == FlowerPokerMatch.Stage.CONFIGURING) {
        match.other(player).message("${player.username} left. The Flower Poker match is off.")
        match.cancel()
    }
}
