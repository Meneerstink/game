package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.CasinoInterfaceImportTool as Layout
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import gg.rsmod.plugins.api.ext.setComponentSprite
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
 * The per-screen bet, target, mine count and dice direction the player is composing are the one piece of
 * interface-local state, and they live on the player as plain attributes rather than in the client, for the same
 * reason.
 *
 * Owner 2026-09-20 ("why do i see seed"): the seeds are no longer part of any screen. They live in a hidden
 * overlay ([openFairness]) that the "Provably fair" button reveals, so the protocol is unchanged and unaffected
 * while nobody has to look at a hex digest to place a bet.
 */
object CasinoScreens {
    /** op1 only - every casino control is a single-option button. */
    private const val EVENTS_OP1 = 0x2

    val BET = AttributeKey<String>(persistenceKey = "casino_ui_bet")
    val TARGET = AttributeKey<String>(persistenceKey = "casino_ui_target")
    val MINE_COUNT = AttributeKey<String>(persistenceKey = "casino_ui_mines")

    /** "1" while the dice screen is betting on a roll *under* the target. Persisted so the choice survives a relog. */
    val DICE_UNDER = AttributeKey<String>(persistenceKey = "casino_ui_dice_under")

    /**
     * The boundary and direction currently drawn on this player's dice bar, as `target * 2 + under`. Deliberately
     * NOT persisted: it describes what the client is showing, not anything about the player, and a stale value
     * read back at login would suppress the first redraw of a bar the client has never drawn.
     */
    private val BAR_STATE = AttributeKey<Int>()

    /** Items used to draw a mines board; both are ordinary cache items, so the screen needs no new sprites. */
    const val GEM_ITEM = Items.UNCUT_EMERALD
    const val MINE_ITEM = Items.CANNONBALL

    // ---------------------------------------------------------------- composing state

    fun bet(player: Player): Long =
        player.attr[BET]?.toLongOrNull()?.coerceIn(CasinoWallet.MIN_WAGER, CasinoWallet.MAX_WAGER)
            ?: CasinoWallet.MIN_WAGER

    fun setBet(
        player: Player,
        amount: Long,
    ) {
        player.attr[BET] = amount.coerceIn(CasinoWallet.MIN_WAGER, CasinoWallet.MAX_WAGER).toString()
    }

    /** The most this player could stake right now: what they hold, capped by the house limit. */
    fun maxBet(player: Player): Long =
        CasinoWallet
            .balance(player)
            .coerceAtMost(CasinoWallet.MAX_WAGER)
            .coerceAtLeast(CasinoWallet.MIN_WAGER)

    fun target(player: Player): Int =
        player.attr[TARGET]?.toIntOrNull()?.coerceIn(CasinoOdds.MIN_DICE_TARGET, CasinoOdds.MAX_DICE_TARGET) ?: 50

    fun setTarget(
        player: Player,
        value: Int,
    ) {
        player.attr[TARGET] = value.coerceIn(CasinoOdds.MIN_DICE_TARGET, CasinoOdds.MAX_DICE_TARGET).toString()
    }

    fun under(player: Player): Boolean = player.attr[DICE_UNDER] == "1"

    fun setUnder(
        player: Player,
        value: Boolean,
    ) {
        player.attr[DICE_UNDER] = if (value) "1" else "0"
    }

    /**
     * The stake of the last wager this player committed, so "Rebet" can put it back after they have been clicking
     * the quick-bet chips. Persisted, because the first thing a player does after a relog is repeat their bet.
     */
    val LAST_STAKE = AttributeKey<String>(persistenceKey = "casino_ui_last_stake")

    fun rememberStake(
        player: Player,
        amount: Long,
    ) {
        player.attr[LAST_STAKE] = amount.toString()
    }

    fun lastStake(player: Player): Long = player.attr[LAST_STAKE]?.toLongOrNull() ?: bet(player)

    fun mineCount(player: Player): Int =
        player.attr[MINE_COUNT]?.toIntOrNull()?.coerceIn(ProvablyFairMines.MIN_MINES, ProvablyFairMines.MAX_MINES) ?: 3

    fun setMineCount(
        player: Player,
        value: Int,
    ) {
        player.attr[MINE_COUNT] = value.coerceIn(ProvablyFairMines.MIN_MINES, ProvablyFairMines.MAX_MINES).toString()
    }

    // ---------------------------------------------------------------- shared widgets

    /*
     * The three button helpers take the player as a plain parameter rather than being member extensions on
     * [Player]. A nested object such as [Dice] would have to resolve the extension through the enclosing object as
     * its dispatch receiver, which is exactly the kind of thing that compiles or does not depending on the
     * Kotlin version; a plain function is unambiguous and reads the same at the call site.
     */

    /** Arms the op on a cap button. The op lives on the button's layer, which is the id the screens name. */
    private fun armButton(
        player: Player,
        interfaceId: Int,
        button: Int,
    ) = player.setInterfaceEvents(interfaceId, button + Layout.BUTTON_LAYER, -1..-1, EVENTS_OP1)

    /**
     * Shows or hides a whole cap button with one packet.
     *
     * Hiding the layer is enough: this client skips a hidden component's entire subtree when it draws, so the
     * three caps and the caption go with it. The first draft had to hide a sprite and its caption separately and
     * got that wrong wherever a third component crept in.
     */
    private fun showButton(
        player: Player,
        interfaceId: Int,
        button: Int,
        visible: Boolean,
    ) = player.setComponentHidden(interfaceId, button + Layout.BUTTON_LAYER, !visible)

    /** Turns a cap button red (active) or grey (idle) by swapping its three slices. */
    private fun setButtonActive(
        player: Player,
        interfaceId: Int,
        button: Int,
        active: Boolean,
    ) {
        val left = if (active) Layout.RED_LEFT else Layout.GREY_LEFT
        val middle = if (active) Layout.RED_MIDDLE else Layout.GREY_MIDDLE
        val right = if (active) Layout.RED_RIGHT else Layout.GREY_RIGHT
        player.setComponentSprite(interfaceId, button + Layout.BUTTON_LEFT, Layout.sprite(left))
        player.setComponentSprite(interfaceId, button + Layout.BUTTON_MIDDLE, Layout.sprite(middle))
        player.setComponentSprite(interfaceId, button + Layout.BUTTON_RIGHT, Layout.sprite(right))
    }

    /** The header every screen shares: the player's coins, live. */
    private fun refreshHeader(
        player: Player,
        interfaceId: Int,
    ) = player.setComponentText(interfaceId, Layout.COINS_TEXT, CasinoWallet.format(CasinoWallet.balance(player)))

    /** Arms the bet row and the five quick-bet chips of a house game. */
    private fun armBetControls(
        player: Player,
        interfaceId: Int,
        halve: Int,
        double: Int,
        max: Int,
        custom: Int,
        quickFirst: Int,
    ) {
        listOf(halve, double, max, custom).forEach { armButton(player, interfaceId, it) }
        for (i in 0 until Layout.QUICK_BETS) {
            armButton(player, interfaceId, quickFirst + i * Layout.BUTTON_STRIDE)
        }
    }

    // ---------------------------------------------------------------- provably fair overlay

    /**
     * The fairness base of a screen, so one pair of handlers serves all four.
     *
     * Returns null for an interface that is not a casino screen, which is what a stray button click from a
     * tampered client looks like.
     */
    fun fairnessBase(interfaceId: Int): Int? =
        when (interfaceId) {
            Layout.DICE_ID -> Layout.Dice.FAIRNESS
            Layout.MINES_ID -> Layout.Mines.FAIRNESS
            Layout.BLACKJACK_ID -> Layout.Blackjack.FAIRNESS
            Layout.FLOWER_ID -> Layout.Flower.FAIRNESS
            else -> null
        }

    private fun armFairness(
        player: Player,
        interfaceId: Int,
        button: Int,
        base: Int,
    ) {
        player.setInterfaceEvents(interfaceId, Layout.CLOSE, -1..-1, EVENTS_OP1)
        armButton(player, interfaceId, button)
        listOf(Layout.Fair.SET_SEED, Layout.Fair.NEW_SEED, Layout.Fair.CLOSE).forEach {
            armButton(player, interfaceId, base + it)
        }
    }

    /** Fills the overlay from the live seed state. The full 64-character digest never fits, so it is shortened. */
    fun refreshFairness(
        player: Player,
        interfaceId: Int,
    ) {
        val base = fairnessBase(interfaceId) ?: return
        val view = Casino.fairness(player)
        player.setComponentText(interfaceId, base + Layout.Fair.HASH_TEXT, view.shortHash)
        player.setComponentText(interfaceId, base + Layout.Fair.CLIENT_TEXT, view.clientSeed)
        player.setComponentText(interfaceId, base + Layout.Fair.NONCE_TEXT, view.nextNonce.toString())
        player.setComponentText(
            interfaceId,
            base + Layout.Fair.PREVIOUS,
            view.previous?.let { "Retired seed: ${it.serverSeed} (${it.rounds} rounds)" } ?: "",
        )
    }

    fun openFairness(
        player: Player,
        interfaceId: Int,
    ) {
        val base = fairnessBase(interfaceId) ?: return
        refreshFairness(player, interfaceId)
        player.setComponentHidden(interfaceId, base + Layout.Fair.LAYER, false)
    }

    fun closeFairness(
        player: Player,
        interfaceId: Int,
    ) {
        val base = fairnessBase(interfaceId) ?: return
        player.setComponentHidden(interfaceId, base + Layout.Fair.LAYER, true)
    }

    // ---------------------------------------------------------------- dice

    object Dice {
        val ID = Layout.DICE_ID

        fun open(player: Player) {
            player.openInterface(ID, InterfaceDestination.MAIN_SCREEN)
            armBetControls(
                player, ID,
                Layout.Dice.BET_HALVE, Layout.Dice.BET_DOUBLE, Layout.Dice.BET_MAX, Layout.Dice.BET_CUSTOM,
                Layout.Dice.QUICK_FIRST,
            )
            listOf(
                Layout.Dice.MODE_OVER,
                Layout.Dice.MODE_UNDER,
                Layout.Dice.TARGET_MINUS,
                Layout.Dice.TARGET_PLUS,
                Layout.Dice.TARGET_P25,
                Layout.Dice.TARGET_P50,
                Layout.Dice.TARGET_P75,
                Layout.Dice.TARGET_CUSTOM,
                Layout.Dice.ROLL_BUTTON,
            ).forEach { armButton(player, ID, it) }
            armFairness(player, ID, Layout.Dice.FAIR_BUTTON, Layout.Dice.FAIRNESS)
            closeFairness(player, ID)
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
         * wide, so the segment the boundary falls in is part win and part loss; painting it as a win is the honest
         * choice, because a roll there can win.
         *
         * In roll-over mode the representative draw is the top of the segment and in roll-under mode it is the
         * bottom, which is the same "a win is possible here" rule read from the winning end in each direction.
         */
        fun segmentWins(
            index: Int,
            target: Int,
            under: Boolean = false,
        ): Boolean {
            if (under) {
                return CasinoOdds.diceWins(index * DRAWS_PER_SEGMENT, target, under = true)
            }
            val last = index == Layout.Dice.SEGMENTS - 1
            val top = if (last) ProvablyFairDice.OUTCOMES - 1 else index * DRAWS_PER_SEGMENT + DRAWS_PER_SEGMENT - 1
            return CasinoOdds.diceWins(top, target)
        }

        /**
         * Slides the colour boundary of the win/lose bar to the current target and direction.
         *
         * Redrawing the bar is a hundred hide packets, and only the target and the direction can move the
         * boundary, so the last state drawn is remembered and an unchanged one is skipped: clicking the bet's
         * "x2" costs a handful of packets rather than a hundred and six. [force] is for the one case the memory
         * cannot see - reopening the interface, where the client has reset every component to the state the cache
         * defines.
         */
        fun refreshBar(
            player: Player,
            force: Boolean = false,
        ) {
            val target = target(player)
            val under = under(player)
            val state = target * 2 + if (under) 1 else 0
            if (!force && player.attr[BAR_STATE] == state) {
                return
            }
            player.attr[BAR_STATE] = state
            for (i in 0 until Layout.Dice.SEGMENTS) {
                val winning = segmentWins(i, target, under)
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
            val under = under(player)
            val payout = CasinoOdds.dicePayout(stake, target, under)

            player.setComponentText(ID, Layout.Dice.BET_TEXT, CasinoWallet.format(stake))
            player.setComponentText(ID, Layout.Dice.TARGET_LABEL, if (under) "Roll under" else "Roll over")
            player.setComponentText(ID, Layout.Dice.TARGET_TEXT, target.toString())
            player.setComponentText(
                ID,
                Layout.Dice.CHANCE_TEXT,
                CasinoWallet.percent(CasinoOdds.diceWinChance(target, under) * 100.0),
            )
            player.setComponentText(
                ID,
                Layout.Dice.MULTIPLIER_TEXT,
                CasinoWallet.multiplier(CasinoOdds.diceMultiplier(target, under)),
            )
            player.setComponentText(ID, Layout.Dice.PAYOUT_TEXT, CasinoWallet.format(payout))
            player.setComponentText(ID, Layout.Dice.PROFIT_TEXT, CasinoWallet.format(payout - stake))

            setButtonActive(player, ID, Layout.Dice.MODE_OVER, !under)
            setButtonActive(player, ID, Layout.Dice.MODE_UNDER, under)

            refreshHeader(player, ID)
            refreshBar(player)
            refreshHistory(player)
        }

        /**
         * The strip of recent rolls along the bottom.
         *
         * It is read back out of [CasinoHistory] rather than kept in a second list on the side, so what the strip
         * shows is exactly what the audited history holds - including after a relog.
         */
        fun refreshHistory(player: Player) {
            val rows = CasinoHistory.recent(player).filter { it.game == CasinoGame.DICE }
            for (i in 0 until Layout.Dice.HISTORY_SLOTS) {
                val row = rows.getOrNull(i)
                val roll = row?.detail?.substringAfter("roll=", "")?.takeIf { it.isNotBlank() }
                val won = row != null && row.profit >= 0
                val shown = if (won) Layout.Dice.historyWin(i) else Layout.Dice.historyLose(i)
                val hidden = if (won) Layout.Dice.historyLose(i) else Layout.Dice.historyWin(i)
                player.setComponentText(ID, shown, roll ?: "")
                player.setComponentHidden(ID, shown, roll == null)
                player.setComponentHidden(ID, hidden, true)
            }
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
            val direction = if (result.under) "under" else "over"
            val line =
                if (result.won) {
                    "You rolled $direction ${result.target} and won ${CasinoWallet.format(result.payout - result.stake)} coins."
                } else {
                    "You needed to roll $direction ${result.target}. You lost ${CasinoWallet.format(result.stake)} coins."
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
            armBetControls(
                player, ID,
                Layout.Mines.BET_HALVE, Layout.Mines.BET_DOUBLE, Layout.Mines.BET_MAX, Layout.Mines.BET_CUSTOM,
                Layout.Mines.QUICK_FIRST,
            )
            listOf(
                Layout.Mines.MINES_MINUS,
                Layout.Mines.MINES_PLUS,
                Layout.Mines.START_BUTTON,
                Layout.Mines.CASHOUT_BUTTON,
                Layout.Mines.RANDOM_BUTTON,
            ).forEach { armButton(player, ID, it) }
            for (i in Layout.Mines.PRESETS.indices) {
                armButton(player, ID, Layout.Mines.preset(i))
            }
            for (cell in 0 until Layout.Mines.CELLS) {
                player.setInterfaceEvents(ID, Layout.Mines.cover(cell), -1..-1, EVENTS_OP1)
            }
            armFairness(player, ID, Layout.Mines.FAIR_BUTTON, Layout.Mines.FAIRNESS)
            closeFairness(player, ID)
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

            showButton(player, ID, Layout.Mines.START_BUTTON, !playing)
            // Cash out only becomes real once a gem is showing; before that there is nothing to take.
            showButton(player, ID, Layout.Mines.CASHOUT_BUTTON, playing && board!!.gems > 0)
            showButton(player, ID, Layout.Mines.RANDOM_BUTTON, playing)

            player.setComponentText(ID, Layout.Mines.MINES_TEXT, (board?.mineCount ?: mineCount(player)).toString())
            player.setComponentText(ID, Layout.Mines.BET_TEXT, CasinoWallet.format(board?.stake ?: bet(player)))
            player.setComponentText(ID, Layout.Mines.GEMS_TEXT, (board?.gems ?: 0).toString())

            // The mine-count presets light up the one that is selected, so the current board is readable at a glance.
            val mines = board?.mineCount ?: mineCount(player)
            Layout.Mines.PRESETS.forEachIndexed { index, value ->
                setButtonActive(player, ID, Layout.Mines.preset(index), value == mines)
            }

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
            player.setComponentText(ID, Layout.Mines.STATUS_TEXT, line)
            refreshHeader(player, ID)
        }
    }

    // ---------------------------------------------------------------- blackjack

    object Blackjack {
        val ID = Layout.BLACKJACK_ID

        /** Hearts and diamonds are drawn red, spades and clubs black, as at a real table. */
        private fun isRed(card: Int): Boolean = BlackjackCards.suit(card) == 0 || BlackjackCards.suit(card) == 2

        /**
         * The suit as a single letter.
         *
         * The cache's fonts have no suit glyphs and this project has no sprite encoder, so a letter under the rank
         * is the readable choice. The first draft used `v ^ + *`, which told a player nothing.
         */
        private fun suitLetter(card: Int): String =
            when (BlackjackCards.suit(card)) {
                0 -> "H"
                1 -> "S"
                2 -> "D"
                else -> "C"
            }

        /**
         * Draws one card slot. A null [card] hides the whole card with a single packet; a face-down card shows the
         * plate with a "?" in black, whatever it really is, so the hole card leaks nothing through its colour.
         */
        private fun drawCard(
            player: Player,
            base: Int,
            card: Int?,
            facedown: Boolean = false,
        ) {
            if (card == null) {
                player.setComponentHidden(ID, base + Layout.CARD_LAYER, true)
                return
            }
            player.setComponentHidden(ID, base + Layout.CARD_LAYER, false)
            val red = !facedown && isRed(card)
            val rankShown = base + if (red) Layout.CARD_RANK_RED else Layout.CARD_RANK_BLACK
            val rankHidden = base + if (red) Layout.CARD_RANK_BLACK else Layout.CARD_RANK_RED
            val suitShown = base + if (red) Layout.CARD_SUIT_RED else Layout.CARD_SUIT_BLACK
            val suitHidden = base + if (red) Layout.CARD_SUIT_BLACK else Layout.CARD_SUIT_RED

            player.setComponentText(ID, rankShown, if (facedown) "?" else BlackjackCards.shortName(card))
            player.setComponentText(ID, suitShown, if (facedown) "" else suitLetter(card))
            player.setComponentHidden(ID, rankShown, false)
            player.setComponentHidden(ID, suitShown, false)
            player.setComponentHidden(ID, rankHidden, true)
            player.setComponentHidden(ID, suitHidden, true)
        }

        fun open(player: Player) {
            player.openInterface(ID, InterfaceDestination.MAIN_SCREEN)
            armBetControls(
                player, ID,
                Layout.Blackjack.BET_HALVE, Layout.Blackjack.BET_DOUBLE, Layout.Blackjack.BET_MAX,
                Layout.Blackjack.BET_CUSTOM, Layout.Blackjack.QUICK_FIRST,
            )
            listOf(
                Layout.Blackjack.REBET_BUTTON,
                Layout.Blackjack.DEAL_BUTTON,
                Layout.Blackjack.HIT_BUTTON,
                Layout.Blackjack.STAND_BUTTON,
                Layout.Blackjack.DOUBLE_BUTTON,
                Layout.Blackjack.SPLIT_BUTTON,
                Layout.Blackjack.INSURE_BUTTON,
                Layout.Blackjack.DECLINE_BUTTON,
            ).forEach { armButton(player, ID, it) }
            armFairness(player, ID, Layout.Blackjack.FAIR_BUTTON, Layout.Blackjack.FAIRNESS)
            closeFairness(player, ID)
            refresh(player)
        }

        fun refresh(
            player: Player,
            status: String? = null,
        ) {
            val table = BlackjackGame.active(player)
            val live = table != null && table.phase == BlackjackGame.Phase.PLAYER

            player.setComponentText(ID, Layout.Blackjack.BET_TEXT, CasinoWallet.format(table?.baseStake ?: bet(player)))
            refreshHeader(player, ID)

            // Dealer row. While the player still has decisions, the hole card stays face down.
            for (i in 0 until Layout.Blackjack.DEALER_CARDS) {
                drawCard(player, Layout.Blackjack.dealerCard(i), table?.dealer?.getOrNull(i), facedown = live && i >= 1)
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
                    val marker = if (live && table.active == row) "<" else ""
                    val hand = seat.hand()
                    // The column beside the cards is 42px wide, so it carries the total, an `s` for a soft hand and
                    // the arrow for the hand being played, and nothing else. The stake is on the Bet line above.
                    val soft = if (hand.soft && !hand.bust) "s" else ""
                    player.setComponentText(ID, Layout.Blackjack.rowTotal(row), "${hand.total}$soft$marker")
                }
                for (i in 0 until Layout.Blackjack.PLAYER_CARDS) {
                    drawCard(player, Layout.Blackjack.playerCard(row, i), seat?.cards?.getOrNull(i))
                }
            }

            val seat = if (live) table!!.current() else null
            val insuring = table?.insuranceOffered() == true
            val settled = table == null || table.phase == BlackjackGame.Phase.SETTLED
            showButton(player, ID, Layout.Blackjack.DEAL_BUTTON, settled)
            showButton(player, ID, Layout.Blackjack.REBET_BUTTON, settled)
            showButton(player, ID, Layout.Blackjack.HIT_BUTTON, live && !insuring && seat != null && !seat.splitAce)
            showButton(player, ID, Layout.Blackjack.STAND_BUTTON, live && !insuring && seat != null)
            showButton(
                player,
                ID,
                Layout.Blackjack.DOUBLE_BUTTON,
                live && !insuring && seat != null && seat.cards.size == 2 && !seat.doubled && !seat.splitAce,
            )
            showButton(
                player,
                ID,
                Layout.Blackjack.SPLIT_BUTTON,
                live && !insuring && seat != null && BlackjackCards.canSplit(seat.cards) &&
                    table!!.hands.size < BlackjackGame.MAX_HANDS,
            )
            showButton(player, ID, Layout.Blackjack.INSURE_BUTTON, insuring)
            showButton(player, ID, Layout.Blackjack.DECLINE_BUTTON, insuring)

            val line =
                status ?: when {
                    insuring -> "The dealer shows an Ace. Insurance?"
                    table == null -> "Place your bet and deal."
                    table.phase == BlackjackGame.Phase.SETTLED -> "Deal again when you are ready."
                    else -> "Your move."
                }
            player.setComponentText(ID, Layout.Blackjack.STATUS_TEXT, line)
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
            ).forEach { armButton(player, ID, it) }
            armFairness(player, ID, Layout.Flower.FAIR_BUTTON, Layout.Flower.FAIRNESS)
            closeFairness(player, ID)
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
            player.setComponentText(ID, Layout.Flower.CHALLENGER_TICK, if (match.accepted(mine)) "Accepted" else "")
            player.setComponentText(ID, Layout.Flower.OPPONENT_TICK, if (match.accepted(theirs)) "Accepted" else "")

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
            player.setComponentText(ID, Layout.Flower.ROUND_TEXT, if (round == null) "-" else "${round.index + 1}")

            val staking = match.stage == FlowerPokerMatch.Stage.CONFIGURING
            showButton(player, ID, Layout.Flower.STAKE_BUTTON, staking)
            showButton(player, ID, Layout.Flower.ACCEPT_BUTTON, staking)

            player.setComponentText(
                ID,
                Layout.Flower.STATUS_TEXT,
                status ?: when (match.stage) {
                    FlowerPokerMatch.Stage.CONFIGURING -> "Agree a stake and both accept."
                    FlowerPokerMatch.Stage.PLANTING -> "Planting..."
                    FlowerPokerMatch.Stage.FINISHED -> "Match over."
                },
            )
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
