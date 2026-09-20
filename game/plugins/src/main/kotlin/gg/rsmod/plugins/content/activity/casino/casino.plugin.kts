package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.tools.importer.CasinoInterfaceImportTool as Layout

/*
 * The three house games: Dice, Mines and Blackjack (owner 2026-09-20).
 *
 * Every button here does one thing: read the click, ask the game object to do something, redraw from whatever the
 * game object now says. No game rule and no payout lives in this file, so a button cannot become a second, weaker
 * copy of the rules - and a click the server does not consider legal simply redraws the unchanged screen.
 *
 * Buttons are the cap buttons [Layout.Screen.button] builds, whose op lives on the button's *layer*, so the
 * component a handler binds is the layer id the layout names - never an offset off it.
 *
 * Flower Poker is player-versus-player and lives in flower_poker.plugin.kts.
 */

// ============================================================ shared

/**
 * The provably-fair overlay, bound once for all four screens (owner 2026-09-20: "why do i see seed").
 *
 * The seeds no longer sit on any screen; this panel is where they live, and it behaves the same everywhere, so
 * binding it per screen would be four copies of one behaviour.
 */
val CASINO_SCREENS =
    listOf(
        Layout.DICE_ID to Layout.Dice.FAIRNESS,
        Layout.MINES_ID to Layout.Mines.FAIRNESS,
        Layout.BLACKJACK_ID to Layout.Blackjack.FAIRNESS,
        Layout.FLOWER_ID to Layout.Flower.FAIRNESS,
    )

for ((interfaceId, fairnessBase) in CASINO_SCREENS) {
    on_button(interfaceId = interfaceId, component = fairnessBase + Layout.Fair.CLOSE) {
        CasinoScreens.closeFairness(player, interfaceId)
    }

    on_button(interfaceId = interfaceId, component = fairnessBase + Layout.Fair.SET_SEED) {
        player.queue {
            CasinoDialogs.askClientSeed(this, player)
            CasinoScreens.refreshFairness(player, interfaceId)
        }
    }

    on_button(interfaceId = interfaceId, component = fairnessBase + Layout.Fair.NEW_SEED) {
        val revealed = Casino.rotateSeed(player)
        if (revealed == null) {
            player.message(Casino.SeedChange.ROUND_LIVE.message)
            return@on_button
        }
        player.message("Retired server seed: ${revealed.serverSeed}")
        player.message("It hashes to ${revealed.hash} - the commitment you were shown.")
        CasinoScreens.refreshFairness(player, interfaceId)
    }
}

// ============================================================ Dice

on_button(interfaceId = Layout.DICE_ID, component = Layout.CLOSE) { player.closeInterface(Layout.DICE_ID) }

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.FAIR_BUTTON) {
    CasinoScreens.openFairness(player, Layout.DICE_ID)
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.BET_HALVE) {
    CasinoScreens.setBet(player, CasinoScreens.bet(player) / 2)
    CasinoScreens.Dice.refresh(player)
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.BET_DOUBLE) {
    CasinoScreens.setBet(player, CasinoScreens.bet(player) * 2)
    CasinoScreens.Dice.refresh(player)
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.BET_MAX) {
    CasinoScreens.setBet(player, CasinoScreens.maxBet(player))
    CasinoScreens.Dice.refresh(player)
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.BET_CUSTOM) {
    player.queue {
        CasinoDialogs.askBet(this, player)
        CasinoScreens.Dice.refresh(player)
    }
}

for (chip in 0 until Layout.QUICK_BETS) {
    on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.quickBet(chip)) {
        CasinoScreens.setBet(player, Layout.QUICK_BET_AMOUNTS[chip])
        CasinoScreens.Dice.refresh(player)
    }
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.MODE_OVER) {
    CasinoScreens.setUnder(player, false)
    CasinoScreens.Dice.refresh(player)
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.MODE_UNDER) {
    CasinoScreens.setUnder(player, true)
    CasinoScreens.Dice.refresh(player)
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.TARGET_MINUS) {
    CasinoScreens.setTarget(player, CasinoScreens.target(player) - 1)
    CasinoScreens.Dice.refresh(player)
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.TARGET_PLUS) {
    CasinoScreens.setTarget(player, CasinoScreens.target(player) + 1)
    CasinoScreens.Dice.refresh(player)
}

for ((button, preset) in listOf(Layout.Dice.TARGET_P25 to 25, Layout.Dice.TARGET_P50 to 50, Layout.Dice.TARGET_P75 to 75)) {
    on_button(interfaceId = Layout.DICE_ID, component = button) {
        CasinoScreens.setTarget(player, preset)
        CasinoScreens.Dice.refresh(player)
    }
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.TARGET_CUSTOM) {
    player.queue {
        val value = inputInt("Target (1-100):")
        CasinoScreens.setTarget(player, value)
        CasinoScreens.Dice.refresh(player)
    }
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.ROLL_BUTTON) {
    val stake = CasinoScreens.bet(player)
    val target = CasinoScreens.target(player)
    val under = CasinoScreens.under(player)
    val refusal = DiceGame.validate(player, stake, target, under)
    if (refusal != null) {
        player.message(refusal.message)
        return@on_button
    }
    val result = DiceGame.roll(player, stake, target, under)
    if (result == null) {
        player.message("That bet could not be placed.")
        return@on_button
    }
    CasinoScreens.rememberStake(player, stake)
    player.playSound(if (result.won) Sfx.COINS_JINGLE_1 else Sfx.DESTROY_OBJECT)
    CasinoScreens.announce(player, result.credited)
    CasinoScreens.Dice.showResult(player, result)
}

// ============================================================ Mines

on_button(interfaceId = Layout.MINES_ID, component = Layout.CLOSE) { player.closeInterface(Layout.MINES_ID) }

on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.FAIR_BUTTON) {
    CasinoScreens.openFairness(player, Layout.MINES_ID)
}

/** The bet may only move while no board is live: the stake of a running board is already committed. */
fun minesSetBet(
    player: Player,
    amount: Long,
) {
    if (MinesGame.isPlaying(player)) {
        return
    }
    CasinoScreens.setBet(player, amount)
    CasinoScreens.Mines.refresh(player)
}

on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.BET_HALVE) {
    minesSetBet(player, CasinoScreens.bet(player) / 2)
}

on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.BET_DOUBLE) {
    minesSetBet(player, CasinoScreens.bet(player) * 2)
}

on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.BET_MAX) {
    minesSetBet(player, CasinoScreens.maxBet(player))
}

on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.BET_CUSTOM) {
    if (!MinesGame.isPlaying(player)) {
        player.queue {
            CasinoDialogs.askBet(this, player)
            CasinoScreens.Mines.refresh(player)
        }
    }
}

for (chip in 0 until Layout.QUICK_BETS) {
    on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.quickBet(chip)) {
        minesSetBet(player, Layout.QUICK_BET_AMOUNTS[chip])
    }
}

on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.MINES_MINUS) {
    if (!MinesGame.isPlaying(player)) {
        CasinoScreens.setMineCount(player, CasinoScreens.mineCount(player) - 1)
        CasinoScreens.Mines.refresh(player)
    }
}

on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.MINES_PLUS) {
    if (!MinesGame.isPlaying(player)) {
        CasinoScreens.setMineCount(player, CasinoScreens.mineCount(player) + 1)
        CasinoScreens.Mines.refresh(player)
    }
}

for (index in Layout.Mines.PRESETS.indices) {
    on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.preset(index)) {
        if (!MinesGame.isPlaying(player)) {
            CasinoScreens.setMineCount(player, Layout.Mines.PRESETS[index])
            CasinoScreens.Mines.refresh(player)
        }
    }
}

on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.START_BUTTON) {
    val stake = CasinoScreens.bet(player)
    val mines = CasinoScreens.mineCount(player)
    val refusal = MinesGame.validate(player, stake, mines)
    if (refusal != null) {
        player.message(refusal.message)
        return@on_button
    }
    if (MinesGame.start(player, stake, mines) == null) {
        player.message("That board could not be started.")
        return@on_button
    }
    CasinoScreens.rememberStake(player, stake)
    CasinoScreens.Mines.refresh(player, status = "Board laid. Reveal a tile.")
}

/** Redraws mines after a reveal, whichever way it went. Shared by the tile clicks and "Pick random tile". */
fun showMinesReveal(
    player: Player,
    reveal: MinesGame.Reveal,
) {
    when (reveal) {
        is MinesGame.Reveal.Ignored -> {}
        is MinesGame.Reveal.Gem -> {
            player.playSound(Sfx.COINS_JINGLE_1)
            CasinoScreens.Mines.refresh(player)
        }
        is MinesGame.Reveal.Boom -> {
            player.playSound(Sfx.DESTROY_OBJECT)
            CasinoScreens.Mines.refresh(
                player,
                revealedMines = reveal.mines,
                status = "You hit a mine and lost ${CasinoWallet.format(reveal.stake)} coins.",
            )
        }
        is MinesGame.Reveal.Cleared -> {
            player.playSound(Sfx.COINS_JINGLE_1)
            CasinoScreens.announce(player, reveal.credited)
            CasinoScreens.Mines.refresh(
                player,
                revealedMines = reveal.mines,
                status = "Board cleared! You won ${CasinoWallet.format(reveal.payout)} coins.",
            )
        }
    }
}

for (cell in 0 until Layout.Mines.CELLS) {
    on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.cover(cell)) {
        showMinesReveal(player, MinesGame.reveal(player, cell))
    }
}

/**
 * "Pick random tile" (owner 2026-09-20: "give it some more options").
 *
 * The choice is made here, from the cells the board itself still reports as unrevealed - the server never asks the
 * client which tile is free, so a tampered client cannot steer the pick towards a safe cell.
 */
on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.RANDOM_BUTTON) {
    val board = MinesGame.active(player)
    if (board == null) {
        player.message("Start a board first.")
        return@on_button
    }
    val choices = (0 until Layout.Mines.CELLS).filter { it !in board.revealed }
    if (choices.isEmpty()) {
        return@on_button
    }
    showMinesReveal(player, MinesGame.reveal(player, choices.random()))
}

on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.CASHOUT_BUTTON) {
    val cashout = MinesGame.cashout(player)
    if (cashout == null) {
        player.message("There is nothing to cash out yet.")
        return@on_button
    }
    player.playSound(Sfx.COINS_JINGLE_1)
    CasinoScreens.announce(player, cashout.credited)
    CasinoScreens.Mines.refresh(
        player,
        revealedMines = cashout.mines,
        status = "Cashed out ${CasinoWallet.format(cashout.payout)} coins on ${cashout.gems} gems.",
    )
}

// ============================================================ Blackjack

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.CLOSE) { player.closeInterface(Layout.BLACKJACK_ID) }

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.FAIR_BUTTON) {
    CasinoScreens.openFairness(player, Layout.BLACKJACK_ID)
}

/** The bet may only move between hands; a dealt table's stake is already committed. */
fun blackjackSetBet(
    player: Player,
    amount: Long,
) {
    if (BlackjackGame.isPlaying(player)) {
        return
    }
    CasinoScreens.setBet(player, amount)
    CasinoScreens.Blackjack.refresh(player)
}

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.BET_HALVE) {
    blackjackSetBet(player, CasinoScreens.bet(player) / 2)
}

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.BET_DOUBLE) {
    blackjackSetBet(player, CasinoScreens.bet(player) * 2)
}

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.BET_MAX) {
    blackjackSetBet(player, CasinoScreens.maxBet(player))
}

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.BET_CUSTOM) {
    if (!BlackjackGame.isPlaying(player)) {
        player.queue {
            CasinoDialogs.askBet(this, player)
            CasinoScreens.Blackjack.refresh(player)
        }
    }
}

for (chip in 0 until Layout.QUICK_BETS) {
    on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.quickBet(chip)) {
        blackjackSetBet(player, Layout.QUICK_BET_AMOUNTS[chip])
    }
}

/** Deals a hand at the composed stake. Shared by Deal and Rebet, so both go through the same validation. */
fun blackjackDeal(player: Player) {
    BlackjackGame.finish(player) // clear a settled table before dealing the next one
    val stake = CasinoScreens.bet(player)
    val refusal = BlackjackGame.validate(player, stake)
    if (refusal != null) {
        player.message(refusal.message)
        return
    }
    val table = BlackjackGame.deal(player, stake)
    if (table == null) {
        player.message("That hand could not be dealt.")
        return
    }
    CasinoScreens.rememberStake(player, stake)
    showBlackjack(player, table)
}

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.DEAL_BUTTON) { blackjackDeal(player) }

/**
 * Rebet: put the stake of the last committed wager back and deal it again.
 *
 * It is not the same as Deal, which uses whatever is currently composed - after a run of quick-bet chips the two
 * differ, and repeating the last real bet in one click is the thing a player actually wants.
 */
on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.REBET_BUTTON) {
    if (BlackjackGame.isPlaying(player)) {
        return@on_button
    }
    CasinoScreens.setBet(player, CasinoScreens.lastStake(player))
    blackjackDeal(player)
}

/** Redraws blackjack and reports a settlement, if this action produced one. */
fun showBlackjack(
    player: Player,
    table: BlackjackGame.Table,
) {
    val settlement = table.settlement
    if (settlement != null) {
        if (settlement.payout > 0) {
            player.playSound(Sfx.COINS_JINGLE_1)
            CasinoScreens.announce(player, settlement.credited)
        }
        settlement.lines.forEach { player.message(it) }
        CasinoScreens.Blackjack.refresh(player, status = settlement.lines.joinToString(" "))
    } else {
        CasinoScreens.Blackjack.refresh(player)
    }
}

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.HIT_BUTTON) {
    BlackjackGame.hit(player)?.let { showBlackjack(player, it) }
}

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.STAND_BUTTON) {
    BlackjackGame.stand(player)?.let { showBlackjack(player, it) }
}

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.DOUBLE_BUTTON) {
    val table = BlackjackGame.double(player)
    if (table == null) {
        player.message("You cannot double that hand.")
        return@on_button
    }
    showBlackjack(player, table)
}

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.SPLIT_BUTTON) {
    val table = BlackjackGame.split(player)
    if (table == null) {
        player.message("You cannot split that hand.")
        return@on_button
    }
    showBlackjack(player, table)
}

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.INSURE_BUTTON) {
    val table = BlackjackGame.insure(player)
    if (table == null) {
        player.message("You cannot take insurance right now.")
        return@on_button
    }
    showBlackjack(player, table)
}

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.DECLINE_BUTTON) {
    BlackjackGame.declineInsurance(player)?.let { showBlackjack(player, it) }
}

// ============================================================ commands

on_command("dice") { CasinoScreens.open(player, CasinoGame.DICE) }
on_command("mines") { CasinoScreens.open(player, CasinoGame.MINES) }
on_command("blackjack") { CasinoScreens.open(player, CasinoGame.BLACKJACK) }

on_command("fair") { player.queue { CasinoDialogs.showFairness(this, player) } }

on_command("casinohistory") {
    val rows = CasinoHistory.recent(player)
    if (rows.isEmpty()) {
        player.message("You have not played any casino games yet.")
        return@on_command
    }
    player.message("<col=ffff00>Your last ${rows.size} rounds</col>")
    rows.forEach { row ->
        val outcome =
            if (row.profit >= 0) {
                "<col=00ff00>+${CasinoWallet.format(row.profit)}</col>"
            } else {
                "<col=ff0000>${CasinoWallet.format(row.profit)}</col>"
            }
        player.message("${row.game.displayName}: $outcome (nonce ${row.nonce}) ${row.detail}")
    }
}
