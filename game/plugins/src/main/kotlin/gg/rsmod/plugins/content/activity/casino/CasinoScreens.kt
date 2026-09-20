package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.CasinoInterfaceImportTool as Layout
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents

/**
 * Draws the four casino screens that [gg.rsmod.game.tools.importer.CasinoInterfaceImportTool] builds into the
 * cache, and keeps them in step with the server-side state.
 *
 * The screens hold no game state of their own. Every refresh reads the authoritative state back out of
 * [MinesGame], [BlackjackGame], [FlowerPokerMatch] and the player's coins and re-sends it, so a desynced or
 * hostile client can never be a source of truth - the worst a tampered client achieves is showing its owner a
 * wrong picture of a game the server is still scoring correctly.
 *
 * The per-screen "bet amount" and "target" the player is composing are the one piece of interface-local state,
 * and they live on the player as plain attributes rather than in the client, for the same reason.
 */
object CasinoScreens {
    /** op1 only - every casino control is a single-option button. */
    private const val EVENTS_OP1 = 0x2

    val BET = gg.rsmod.game.model.attr.AttributeKey<String>(persistenceKey = "casino_ui_bet")
    val TARGET = gg.rsmod.game.model.attr.AttributeKey<String>(persistenceKey = "casino_ui_target")
    val MINE_COUNT = gg.rsmod.game.model.attr.AttributeKey<String>(persistenceKey = "casino_ui_mines")

    /**
     * The dice bar boundary currently drawn on this player's client. Deliberately NOT persisted: it describes what
     * the client is showing, not anything about the player, and a stale value read back at login would suppress the
     * first redraw of a bar the client has never drawn.
     */
    private val BAR_TARGET = gg.rsmod.game.model.attr.AttributeKey<Int>()

    /** Items used to draw a mines board; both are ordinary cache items, so the screen needs no new sprites. */
    const val GEM_ITEM = Items.UNCUT_EMERALD
    const val MINE_ITEM = Items.CANNONBALL

    fun bet(player: Player): Long = player.attr[BET]?.toLongOrNull()?.coerceIn(CasinoWallet.MIN_WAGER, CasinoWallet.MAX_WAGER) ?: CasinoWallet.MIN_WAGER

    fun setBet(
        player: Player,
        amount: Long,
    ) {
        player.attr[BET] = amount.coerceIn(CasinoWallet.MIN_WAGER, CasinoWallet.MAX_WAGER).toString()
    }

    fun target(player: Player): Int =
        player.attr[TARGET]?.toIntOrNull()?.coerceIn(CasinoOdds.MIN_DICE_TARGET, CasinoOdds.MAX_DICE_TARGET) ?: 50

    fun setTarget(
        player: Player,
        value: Int,
    ) {
        player.attr[TARGET] = value.coerceIn(CasinoOdds.MIN_DICE_TARGET, CasinoOdds.MAX_DICE_TARGET).toString()
    }

    fun mineCount(player: Player): Int =
        player.attr[MINE_COUNT]?.toIntOrNull()?.coerceIn(ProvablyFairMines.MIN_MINES, ProvablyFairMines.MAX_MINES) ?: 3

    fun setMineCount(
        player: Player,
        value: Int,
    ) {
        player.attr[MINE_COUNT] = value.coerceIn(ProvablyFairMines.MIN_MINES, ProvablyFairMines.MAX_MINES).toString()
    }

    // ---------------------------------------------------------------- shared

    /** The fairness strip at the foot of every screen. */
    private fun refreshFairness(
        player: Player,
        interfaceId: Int,
        hashText: Int,
        clientText: Int,
        nonceText: Int,
    ) {
        val view = Casino.fairness(player)
        player.setComponentText(interfaceId, hashText, view.shortHash)
        player.setComponentText(interfaceId, clientText, view.clientSeed)
        player.setComponentText(interfaceId, nonceText, view.nextNonce.toString())
    }

    private fun armFairness(
        player: Player,
        interfaceId: Int,
        setSeed: Int,
        verify: Int,
    ) {
        player.setInterfaceEvents(interfaceId, Layout.CLOSE, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(interfaceId, setSeed, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(interfaceId, verify, -1..-1, EVENTS_OP1)
    }

    // ---------------------------------------------------------------- dice

    object Dice {
        val ID = Layout.DICE_ID

        fun open(player: Player) {
            player.openInterface(ID, InterfaceDestination.MAIN_SCREEN)
            listOf(
                Layout.Dice.BET_MINUS,
                Layout.Dice.BET_PLUS,
                Layout.Dice.BET_CUSTOM,
                Layout.Dice.TARGET_MINUS,
                Layout.Dice.TARGET_PLUS,
                Layout.Dice.TARGET_CUSTOM,
                Layout.Dice.ROLL_BUTTON,
            ).forEach { player.setInterfaceEvents(ID, it, -1..-1, EVENTS_OP1) }
            armFairness(player, ID, Layout.Dice.SET_SEED_BUTTON, Layout.Dice.VERIFY_BUTTON)
            clearResult(player)
            refreshBar(player, force = true)
            refresh(player)
        }

        /**
         * Draws per bar segment. The range is 10,001 discrete draws over 50 segments, which is 200 draws each with
         * one left over - the 10,000th - and [segmentWins] hands that odd draw to the last segment rather than
         * letting it fall off the end of the bar.
         */
        const val DRAWS_PER_SEGMENT = (ProvablyFairDice.OUTCOMES - 1) / Layout.Dice.SEGMENTS

        /**
         * Is any roll that lands in segment [index] a win at [target]?
         *
         * The bar is coloured by this and nothing else, so the boundary a player sees is scored by
         * [CasinoOdds.diceWins] itself rather than by a second, drifting copy of the rule. A segment is two points
         * wide, so the segment the boundary falls in is part win and part loss; painting it green is the honest
         * choice, because a roll there can win.
         */
        fun segmentWins(
            index: Int,
            target: Int,
        ): Boolean {
            val last = index == Layout.Dice.SEGMENTS - 1
            val top = if (last) ProvablyFairDice.OUTCOMES - 1 else index * DRAWS_PER_SEGMENT + DRAWS_PER_SEGMENT - 1
            return CasinoOdds.diceWins(top, target)
        }

        /**
         * Slides the colour boundary of the win/lose bar to the current target.
         *
         * Redrawing the bar is a hundred hide packets, and only the target can move the boundary, so the last
         * boundary drawn is remembered and an unchanged one is skipped: clicking the bet's "+" costs six packets
         * rather than a hundred and six. [force] is for the one case the memory cannot see - reopening the
         * interface, where the client has reset every component to the state the cache defines.
         */
        fun refreshBar(
            player: Player,
            force: Boolean = false,
        ) {
            val target = target(player)
            if (!force && player.attr[BAR_TARGET] == target) {
                return
            }
            player.attr[BAR_TARGET] = target
            for (i in 0 until Layout.Dice.SEGMENTS) {
                val winning = segmentWins(i, target)
                player.setComponentHidden(ID, Layout.Dice.loseSegment(i), winning)
                player.setComponentHidden(ID, Layout.Dice.winSegment(i), !winning)
            }
        }

        /** Puts the readout and the outcome line back to their neutral colour and hides every roll marker. */
        fun clearResult(player: Player) {
            player.setComponentHidden(ID, Layout.Dice.ROLL_IDLE, false)
            player.setComponentHidden(ID, Layout.Dice.ROLL_WIN, true)
            player.setComponentHidden(ID, Layout.Dice.ROLL_LOSE, true)
            player.setComponentHidden(ID, Layout.Dice.OUTCOME_IDLE, false)
            player.setComponentHidden(ID, Layout.Dice.OUTCOME_WIN, true)
            player.setComponentHidden(ID, Layout.Dice.OUTCOME_LOSE, true)
            for (i in 0 until Layout.Dice.SEGMENTS) {
                player.setComponentHidden(ID, Layout.Dice.marker(i), true)
            }
        }

        fun refresh(player: Player) {
            val stake = bet(player)
            val target = target(player)
            player.setComponentText(ID, Layout.Dice.BET_TEXT, CasinoWallet.format(stake))
            player.setComponentText(ID, Layout.Dice.TARGET_TEXT, target.toString())
            player.setComponentText(ID, Layout.Dice.CHANCE_TEXT, CasinoWallet.percent(CasinoOdds.diceWinChance(target) * 100.0))
            player.setComponentText(ID, Layout.Dice.MULTIPLIER_TEXT, CasinoWallet.multiplier(CasinoOdds.diceMultiplier(target)))
            player.setComponentText(ID, Layout.Dice.PAYOUT_TEXT, CasinoWallet.format(CasinoOdds.dicePayout(stake, target)))
            player.setComponentText(ID, Layout.Dice.COINS_TEXT, CasinoWallet.format(CasinoWallet.balance(player)))
            refreshBar(player)
            refreshFairness(player, ID, Layout.Dice.SEED_HASH_TEXT, Layout.Dice.CLIENT_SEED_TEXT, Layout.Dice.NONCE_TEXT)
        }

        fun showResult(
            player: Player,
            result: DiceGame.Result,
        ) {
            clearResult(player)

            val readout = if (result.won) Layout.Dice.ROLL_WIN else Layout.Dice.ROLL_LOSE
            player.setComponentText(ID, readout, DiceGame.format(result.rollPercentage))
            player.setComponentHidden(ID, Layout.Dice.ROLL_IDLE, true)
            player.setComponentHidden(ID, readout, false)

            // An exact 100.00 is the one draw that does not fit 50 segments of 200, so it is clamped onto the
            // last segment - the same segment [segmentWins] gives it - rather than addressing a component that
            // does not exist.
            val segment = (result.rollScaled / DRAWS_PER_SEGMENT).coerceIn(0, Layout.Dice.SEGMENTS - 1)
            player.setComponentHidden(ID, Layout.Dice.marker(segment), false)

            val outcome = if (result.won) Layout.Dice.OUTCOME_WIN else Layout.Dice.OUTCOME_LOSE
            val line =
                if (result.won) {
                    "You rolled over ${result.target} and won ${CasinoWallet.format(result.payout - result.stake)} coins."
                } else {
                    "You needed ${result.target}.00 or higher. You lost ${CasinoWallet.format(result.stake)} coins."
                }
            player.setComponentText(ID, outcome, line)
            player.setComponentHidden(ID, Layout.Dice.OUTCOME_IDLE, true)
            player.setComponentHidden(ID, outcome, false)

            refresh(player)
        }
    }

    // ---------------------------------------------------------------- mines

    object Mines {
        val ID = Layout.MINES_ID

        fun open(player: Player) {
            player.openInterface(ID, InterfaceDestination.MAIN_SCREEN)
            listOf(
                Layout.Mines.MINES_MINUS,
                Layout.Mines.MINES_PLUS,
                Layout.Mines.BET_CUSTOM,
                Layout.Mines.START_BUTTON,
                Layout.Mines.CASHOUT_BUTTON,
            ).forEach { player.setInterfaceEvents(ID, it, -1..-1, EVENTS_OP1) }
            for (cell in 0 until Layout.Mines.CELLS) {
                player.setInterfaceEvents(ID, Layout.Mines.cover(cell), -1..-1, EVENTS_OP1)
            }
            armFairness(player, ID, Layout.Mines.SET_SEED_BUTTON, Layout.Mines.VERIFY_BUTTON)
            refresh(player)
        }

        /** Redraws the whole board from the authoritative state. */
        fun refresh(
            player: Player,
            revealedMines: Set<Int> = emptySet(),
            status: String? = null,
        ) {
            val board = MinesGame.active(player)
            val playing = board != null

            player.setComponentHidden(ID, Layout.Mines.START_BUTTON, playing)
            player.setComponentHidden(ID, Layout.Mines.START_BUTTON + 1, playing)
            // Cash out only becomes real once a gem is showing; before that there is nothing to take.
            val canCash = playing && board!!.gems > 0
            player.setComponentHidden(ID, Layout.Mines.CASHOUT_BUTTON, !canCash)
            player.setComponentHidden(ID, Layout.Mines.CASHOUT_BUTTON + 1, !canCash)

            player.setComponentText(ID, Layout.Mines.MINES_TEXT, (board?.mineCount ?: mineCount(player)).toString())
            player.setComponentText(ID, Layout.Mines.BET_TEXT, CasinoWallet.format(board?.stake ?: bet(player)))

            if (board != null) {
                player.setComponentText(ID, Layout.Mines.MULTIPLIER_TEXT, CasinoWallet.multiplier(board.multiplier()))
                val next = board.nextMultiplier()
                player.setComponentText(ID, Layout.Mines.NEXT_TEXT, if (next == null) "-" else CasinoWallet.multiplier(next))
                player.setComponentText(ID, Layout.Mines.VALUE_TEXT, CasinoWallet.format(board.cashoutValue()))
            } else {
                player.setComponentText(ID, Layout.Mines.MULTIPLIER_TEXT, "-")
                player.setComponentText(ID, Layout.Mines.NEXT_TEXT, "-")
                player.setComponentText(ID, Layout.Mines.VALUE_TEXT, "-")
            }

            for (cell in 0 until Layout.Mines.CELLS) {
                val isGem = board?.revealed?.contains(cell) == true
                val isMine = cell in revealedMines
                when {
                    isGem -> {
                        player.setComponentItem(ID, Layout.Mines.slot(cell), GEM_ITEM, 1)
                        player.setComponentHidden(ID, Layout.Mines.cover(cell), true)
                    }
                    isMine -> {
                        player.setComponentItem(ID, Layout.Mines.slot(cell), MINE_ITEM, 1)
                        player.setComponentHidden(ID, Layout.Mines.cover(cell), true)
                    }
                    else -> {
                        player.setComponentItem(ID, Layout.Mines.slot(cell), -1, 0)
                        player.setComponentHidden(ID, Layout.Mines.cover(cell), false)
                    }
                }
            }

            val line =
                status ?: when {
                    board == null -> "Pick your mines and start."
                    board.gems == 0 -> "Reveal a tile."
                    else -> "${board.gems} gem${if (board.gems == 1) "" else "s"} - cash out or keep going."
                }
            player.setComponentText(ID, Layout.Mines.COINS_TEXT, CasinoWallet.format(CasinoWallet.balance(player)))
            player.setComponentText(ID, Layout.Mines.STATUS_TEXT, line)
            refreshFairness(player, ID, Layout.Mines.SEED_HASH_TEXT, Layout.Mines.CLIENT_SEED_TEXT, Layout.Mines.NONCE_TEXT)
        }
    }

    // ---------------------------------------------------------------- blackjack

    object Blackjack {
        val ID = Layout.BLACKJACK_ID

        /**
         * Hearts and diamonds are drawn red, spades and clubs black, as at a real table.
         *
         * The client cannot recolour a text component, so every card carries a black rank and a red rank stacked on
         * the same plate and [drawCard] shows the one that matches. The earlier single black rank made this
         * function dead code and the suits indistinguishable.
         */
        private fun isRed(card: Int): Boolean = BlackjackCards.suit(card) == 0 || BlackjackCards.suit(card) == 2

        /**
         * Draws one card slot. A null [card] hides the whole slot; a face-down card shows the plate with a "?" in
         * black, whatever it really is, so the hole card leaks nothing through its colour.
         */
        private fun drawCard(
            player: Player,
            plate: Int,
            blackText: Int,
            redText: Int,
            card: Int?,
            facedown: Boolean = false,
        ) {
            if (card == null) {
                player.setComponentHidden(ID, plate, true)
                player.setComponentHidden(ID, blackText, true)
                player.setComponentHidden(ID, redText, true)
                return
            }
            player.setComponentHidden(ID, plate, false)
            val red = !facedown && isRed(card)
            val shown = if (red) redText else blackText
            player.setComponentText(ID, shown, if (facedown) "?" else face(card))
            player.setComponentHidden(ID, shown, false)
            player.setComponentHidden(ID, if (red) blackText else redText, true)
        }

        private fun face(card: Int): String {
            val suit =
                when (BlackjackCards.suit(card)) {
                    0 -> "v" // hearts
                    1 -> "^" // spades
                    2 -> "+" // diamonds
                    else -> "*" // clubs
                }
            return "${BlackjackCards.shortName(card)}$suit"
        }

        fun open(player: Player) {
            player.openInterface(ID, InterfaceDestination.MAIN_SCREEN)
            listOf(
                Layout.Blackjack.DEAL_BUTTON,
                Layout.Blackjack.HIT_BUTTON,
                Layout.Blackjack.STAND_BUTTON,
                Layout.Blackjack.DOUBLE_BUTTON,
                Layout.Blackjack.SPLIT_BUTTON,
                Layout.Blackjack.INSURE_BUTTON,
                Layout.Blackjack.DECLINE_BUTTON,
                Layout.Blackjack.BET_CUSTOM,
            ).forEach { player.setInterfaceEvents(ID, it, -1..-1, EVENTS_OP1) }
            armFairness(player, ID, Layout.Blackjack.SET_SEED_BUTTON, Layout.Blackjack.VERIFY_BUTTON)
            refresh(player)
        }

        fun refresh(
            player: Player,
            status: String? = null,
        ) {
            val table = BlackjackGame.active(player)
            val live = table != null && table.phase == BlackjackGame.Phase.PLAYER

            player.setComponentText(ID, Layout.Blackjack.BET_TEXT, CasinoWallet.format(table?.baseStake ?: bet(player)))
            player.setComponentText(ID, Layout.Blackjack.COINS_TEXT, CasinoWallet.format(CasinoWallet.balance(player)))

            // Dealer row. While the player still has decisions, the hole card stays face down.
            val hideHole = live
            for (i in 0 until Layout.Blackjack.DEALER_CARDS) {
                drawCard(
                    player,
                    Layout.Blackjack.dealerPlate(i),
                    Layout.Blackjack.dealerText(i),
                    Layout.Blackjack.dealerRedText(i),
                    table?.dealer?.getOrNull(i),
                    facedown = hideHole && i >= 1,
                )
            }
            val dealerTotal =
                when {
                    table == null -> ""
                    live -> BlackjackCards.value(table.dealer.take(1)).total.toString() + " + ?"
                    else -> BlackjackCards.value(table.dealer).total.toString()
                }
            player.setComponentText(ID, Layout.Blackjack.DEALER_TOTAL, dealerTotal)

            for (row in 0 until Layout.Blackjack.PLAYER_ROWS) {
                val seat = table?.hands?.getOrNull(row)
                player.setComponentHidden(ID, Layout.Blackjack.rowTotal(row), seat == null)
                if (seat != null) {
                    val marker = if (live && table!!.active == row) "<" else ""
                    val hand = seat.hand()
                    // The column beside the cards is 42px wide, so it carries the total, an `s` for a soft hand and
                    // the arrow for the hand being played, and nothing else. The stake is on the Bet line above.
                    val soft = if (hand.soft && !hand.bust) "s" else ""
                    player.setComponentText(ID, Layout.Blackjack.rowTotal(row), "${hand.total}$soft$marker")
                }
                for (i in 0 until Layout.Blackjack.PLAYER_CARDS) {
                    drawCard(
                        player,
                        Layout.Blackjack.playerPlate(row, i),
                        Layout.Blackjack.playerText(row, i),
                        Layout.Blackjack.playerRedText(row, i),
                        seat?.cards?.getOrNull(i),
                    )
                }
            }

            val seat = if (live) table!!.current() else null
            val insuring = table?.insuranceOffered() == true
            fun show(
                button: Int,
                visible: Boolean,
            ) {
                player.setComponentHidden(ID, button, !visible)
                player.setComponentHidden(ID, button + 1, !visible)
            }
            show(Layout.Blackjack.DEAL_BUTTON, table == null || table.phase == BlackjackGame.Phase.SETTLED)
            show(Layout.Blackjack.HIT_BUTTON, live && !insuring && seat != null && !seat.splitAce)
            show(Layout.Blackjack.STAND_BUTTON, live && !insuring && seat != null)
            show(Layout.Blackjack.DOUBLE_BUTTON, live && !insuring && seat != null && seat.cards.size == 2 && !seat.doubled && !seat.splitAce)
            show(
                Layout.Blackjack.SPLIT_BUTTON,
                live && !insuring && seat != null && BlackjackCards.canSplit(seat.cards) && table!!.hands.size < BlackjackGame.MAX_HANDS,
            )
            show(Layout.Blackjack.INSURE_BUTTON, insuring)
            show(Layout.Blackjack.DECLINE_BUTTON, insuring)

            val line =
                status ?: when {
                    insuring -> "The dealer shows an Ace. Insurance?"
                    table == null -> "Place your bet and deal."
                    table.phase == BlackjackGame.Phase.SETTLED -> "Deal again when you are ready."
                    else -> "Your move."
                }
            player.setComponentText(ID, Layout.Blackjack.STATUS_TEXT, line)
            refreshFairness(player, ID, Layout.Blackjack.SEED_HASH_TEXT, Layout.Blackjack.CLIENT_SEED_TEXT, Layout.Blackjack.NONCE_TEXT)
        }
    }

    // ---------------------------------------------------------------- flower poker

    object Flower {
        val ID = Layout.FLOWER_ID

        fun open(player: Player) {
            player.openInterface(ID, InterfaceDestination.MAIN_SCREEN)
            listOf(
                Layout.Flower.STAKE_BUTTON,
                Layout.Flower.ACCEPT_BUTTON,
                Layout.Flower.DECLINE_BUTTON,
            ).forEach { player.setInterfaceEvents(ID, it, -1..-1, EVENTS_OP1) }
            armFairness(player, ID, Layout.Flower.SET_SEED_BUTTON, Layout.Flower.VERIFY_BUTTON)
            refresh(player)
        }

        /**
         * Redraws the match from the shared [FlowerPokerMatch]. [shown] limits how many flowers of the current
         * round are visible, so the plugin can plant them one tick at a time instead of all at once.
         */
        fun refresh(
            player: Player,
            shown: Int = FlowerPoker.HAND_SIZE,
            status: String? = null,
        ) {
            val match = player.getFlowerMatch()
            if (match == null) {
                player.setComponentText(ID, Layout.Flower.STATUS_TEXT, "No match.")
                return
            }
            // The viewing player is always drawn on the top row, whichever side of the match they are.
            val mine = player
            val theirs = match.other(player)
            val viewerIsChallenger = player == match.challenger

            player.setComponentText(ID, Layout.Flower.CHALLENGER_NAME, mine.username)
            player.setComponentText(ID, Layout.Flower.OPPONENT_NAME, theirs.username)
            player.setComponentText(ID, Layout.Flower.POT_TEXT, CasinoWallet.format(match.pot))
            player.setComponentText(ID, Layout.Flower.STAKE_TEXT, CasinoWallet.format(match.stake))
            player.setComponentText(ID, Layout.Flower.CHALLENGER_TICK, if (match.accepted(mine)) "You accepted" else "")
            player.setComponentText(ID, Layout.Flower.OPPONENT_TICK, if (match.accepted(theirs)) "${theirs.username} accepted" else "")

            val round = match.rounds.lastOrNull()
            val mineFlowers = if (round == null) emptyList() else if (viewerIsChallenger) round.first else round.second
            val theirFlowers = if (round == null) emptyList() else if (viewerIsChallenger) round.second else round.first

            for (i in 0 until FlowerPoker.HAND_SIZE) {
                val a = mineFlowers.getOrNull(i)?.takeIf { i < shown }
                val b = theirFlowers.getOrNull(i)?.takeIf { i < shown }
                player.setComponentItem(ID, Layout.Flower.challengerFlower(i), a?.itemId ?: -1, if (a == null) 0 else 1)
                player.setComponentItem(ID, Layout.Flower.opponentFlower(i), b?.itemId ?: -1, if (b == null) 0 else 1)
            }

            val complete = round != null && shown >= FlowerPoker.HAND_SIZE
            player.setComponentText(
                ID,
                Layout.Flower.CHALLENGER_HAND,
                if (complete) FlowerPoker.evaluate(mineFlowers).displayName else "",
            )
            player.setComponentText(
                ID,
                Layout.Flower.OPPONENT_HAND,
                if (complete) FlowerPoker.evaluate(theirFlowers).displayName else "",
            )
            player.setComponentText(ID, Layout.Flower.ROUND_TEXT, if (round == null) "" else "Plant ${round.index + 1}")

            val staking = match.stage == FlowerPokerMatch.Stage.CONFIGURING
            listOf(Layout.Flower.STAKE_BUTTON, Layout.Flower.ACCEPT_BUTTON).forEach {
                player.setComponentHidden(ID, it, !staking)
                player.setComponentHidden(ID, it + 1, !staking)
            }

            player.setComponentText(
                ID,
                Layout.Flower.STATUS_TEXT,
                status ?: when (match.stage) {
                    FlowerPokerMatch.Stage.CONFIGURING -> "Agree a stake and both accept."
                    FlowerPokerMatch.Stage.PLANTING -> "Planting..."
                    FlowerPokerMatch.Stage.FINISHED -> "Match over."
                },
            )
            refreshFairness(player, ID, Layout.Flower.SEED_HASH_TEXT, Layout.Flower.CLIENT_SEED_TEXT, Layout.Flower.NONCE_TEXT)
        }
    }

    /** Opens the screen for [game]. Shared by the croupiers and the commands, so both routes behave alike. */
    fun open(
        player: Player,
        game: CasinoGame,
    ) {
        when (game) {
            CasinoGame.DICE -> Dice.open(player)
            CasinoGame.MINES -> Mines.open(player)
            CasinoGame.BLACKJACK -> Blackjack.open(player)
            // Flower poker needs a second player, so there is nothing to open on its own.
            CasinoGame.FLOWER_POKER -> player.message("Challenge another player with: fp <name>")
        }
    }

    /** Tells the player where a payout landed when it could not all go to the inventory. */
    fun announce(
        player: Player,
        payout: CasinoWallet.Payout,
    ) {
        if (payout.bank > 0) {
            player.message("${CasinoWallet.format(payout.bank)} coins would not fit and were sent to your bank.")
        }
        if (payout.ground > 0) {
            player.message("${CasinoWallet.format(payout.ground)} coins were dropped at your feet.")
        }
    }
}
