package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.tools.importer.CasinoInterfaceImportTool as Layout

/*
 * The three house games: Dice, Mines and Blackjack (owner 2026-09-20).
 *
 * Every button here does one thing: read the click, ask the game object to do something, redraw from whatever the
 * game object now says. No game rule and no payout lives in this file, so a button cannot become a second, weaker
 * copy of the rules - and a click the server does not consider legal simply redraws the unchanged screen.
 *
 * Flower Poker is player-versus-player and lives in flower_poker.plugin.kts.
 */

// ============================================================ Dice

on_button(interfaceId = Layout.DICE_ID, component = Layout.CLOSE) { player.closeInterface(Layout.DICE_ID) }

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.BET_MINUS) {
    CasinoScreens.setBet(player, CasinoScreens.bet(player) / 2)
    CasinoScreens.Dice.refresh(player)
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.BET_PLUS) {
    CasinoScreens.setBet(player, CasinoScreens.bet(player) * 2)
    CasinoScreens.Dice.refresh(player)
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.BET_CUSTOM) {
    player.queue {
        CasinoDialogs.askBet(this, player)
        CasinoScreens.Dice.refresh(player)
    }
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.TARGET_MINUS) {
    CasinoScreens.setTarget(player, CasinoScreens.target(player) - 1)
    CasinoScreens.Dice.refresh(player)
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.TARGET_PLUS) {
    CasinoScreens.setTarget(player, CasinoScreens.target(player) + 1)
    CasinoScreens.Dice.refresh(player)
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.TARGET_CUSTOM) {
    player.queue {
        val value = inputInt("Roll over (1-100):")
        CasinoScreens.setTarget(player, value)
        CasinoScreens.Dice.refresh(player)
    }
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.ROLL_BUTTON) {
    val stake = CasinoScreens.bet(player)
    val target = CasinoScreens.target(player)
    val refusal = DiceGame.validate(player, stake, target)
    if (refusal != null) {
        player.message(refusal.message)
        return@on_button
    }
    val result = DiceGame.roll(player, stake, target)
    if (result == null) {
        player.message("That bet could not be placed.")
        return@on_button
    }
    player.playSound(if (result.won) Sfx.COINS_JINGLE_1 else Sfx.DESTROY_OBJECT)
    CasinoScreens.announce(player, result.credited)
    CasinoScreens.Dice.showResult(player, result)
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.SET_SEED_BUTTON) {
    player.queue { CasinoDialogs.askClientSeed(this, player); CasinoScreens.Dice.refresh(player) }
}

on_button(interfaceId = Layout.DICE_ID, component = Layout.Dice.VERIFY_BUTTON) {
    player.queue { CasinoDialogs.showFairness(this, player); CasinoScreens.Dice.refresh(player) }
}

// ============================================================ Mines

on_button(interfaceId = Layout.MINES_ID, component = Layout.CLOSE) { player.closeInterface(Layout.MINES_ID) }

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

on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.BET_CUSTOM) {
    if (!MinesGame.isPlaying(player)) {
        player.queue {
            CasinoDialogs.askBet(this, player)
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
    CasinoScreens.Mines.refresh(player, status = "Board laid. Reveal a tile.")
}

for (cell in 0 until Layout.Mines.CELLS) {
    on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.cover(cell)) {
        when (val reveal = MinesGame.reveal(player, cell)) {
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

on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.SET_SEED_BUTTON) {
    player.queue { CasinoDialogs.askClientSeed(this, player); CasinoScreens.Mines.refresh(player) }
}

on_button(interfaceId = Layout.MINES_ID, component = Layout.Mines.VERIFY_BUTTON) {
    player.queue { CasinoDialogs.showFairness(this, player); CasinoScreens.Mines.refresh(player) }
}

// ============================================================ Blackjack

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.CLOSE) { player.closeInterface(Layout.BLACKJACK_ID) }

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.BET_CUSTOM) {
    if (BlackjackGame.active(player) == null) {
        player.queue {
            CasinoDialogs.askBet(this, player)
            CasinoScreens.Blackjack.refresh(player)
        }
    }
}

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.DEAL_BUTTON) {
    BlackjackGame.finish(player) // clear a settled table before dealing the next one
    val stake = CasinoScreens.bet(player)
    val refusal = BlackjackGame.validate(player, stake)
    if (refusal != null) {
        player.message(refusal.message)
        return@on_button
    }
    val table = BlackjackGame.deal(player, stake)
    if (table == null) {
        player.message("That hand could not be dealt.")
        return@on_button
    }
    showBlackjack(player, table)
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

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.SET_SEED_BUTTON) {
    player.queue { CasinoDialogs.askClientSeed(this, player); CasinoScreens.Blackjack.refresh(player) }
}

on_button(interfaceId = Layout.BLACKJACK_ID, component = Layout.Blackjack.VERIFY_BUTTON) {
    player.queue { CasinoDialogs.showFairness(this, player); CasinoScreens.Blackjack.refresh(player) }
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
        val outcome = if (row.profit >= 0) "<col=00ff00>+${CasinoWallet.format(row.profit)}</col>" else "<col=ff0000>${CasinoWallet.format(row.profit)}</col>"
        player.message("${row.game.displayName}: $outcome (nonce ${row.nonce}) ${row.detail}")
    }
}
