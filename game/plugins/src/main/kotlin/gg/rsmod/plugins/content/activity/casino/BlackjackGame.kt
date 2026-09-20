package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player

/**
 * Blackjack against the house.
 *
 * SOURCE: Roat Pkz wiki "Blackjack" - eight decks shuffled every game, the dealer "draw[s] on 16 or less and
 * stand[s] on any 17" (so it stands on soft 17 too), blackjack pays 3:2, insurance pays 2:1, hit / stand / double
 * on any first two cards, splitting identical-value hands, doubling after a split, and "hitting split Aces is
 * limited to one hit". No surrender.
 *
 * ADAPTED: the wiki does not state a re-split limit, so the standard four-hand cap is used ([MAX_HANDS]).
 *
 * Like Mines, the whole shoe is fixed by `(clientSeed, serverSeed, nonce)` before the first card is dealt, so the
 * cards a player will receive cannot be changed by disconnecting, re-sending a packet or playing a different
 * action - only *which* of those predetermined cards they reach. The table persists across a relog for the same
 * reason: force-resolving it would be either a free escape from a bad hand or a stolen stake.
 */
object BlackjackGame {
    /** Standard re-split cap (ADAPTED - the wiki is silent). */
    const val MAX_HANDS = 4

    val STAKE = AttributeKey<String>(persistenceKey = "casino_bj_stake")
    val NONCE = AttributeKey<String>(persistenceKey = "casino_bj_nonce")
    val CLIENT_SEED = AttributeKey<String>(persistenceKey = "casino_bj_client_seed")
    val SERVER_SEED = AttributeKey<String>(persistenceKey = "casino_bj_server_seed")
    val CURSOR = AttributeKey<String>(persistenceKey = "casino_bj_cursor")
    val DEALER = AttributeKey<String>(persistenceKey = "casino_bj_dealer")
    val HANDS = AttributeKey<String>(persistenceKey = "casino_bj_hands")
    val ACTIVE = AttributeKey<String>(persistenceKey = "casino_bj_active")
    val INSURANCE = AttributeKey<String>(persistenceKey = "casino_bj_insurance")
    val PHASE = AttributeKey<String>(persistenceKey = "casino_bj_phase")

    enum class Phase { PLAYER, SETTLED }

    enum class Rejection(
        val message: String,
    ) {
        ALREADY_PLAYING("Finish your current blackjack hand first."),
        STAKE_TOO_SMALL("The minimum bet is ${CasinoWallet.MIN_WAGER} coins."),
        STAKE_TOO_LARGE("The maximum bet is ${CasinoWallet.MAX_WAGER} coins."),
        PAYOUT_TOO_LARGE("That bet could win more coins than you can hold. Lower your bet."),
        NOT_ENOUGH_COINS("You do not have enough coins for that bet."),
    }

    /** One player hand at the table. A split turns one into two. */
    class Seat(
        val cards: MutableList<Int>,
        var stake: Long,
        var done: Boolean = false,
        var doubled: Boolean = false,
        var splitAce: Boolean = false,
        var fromSplit: Boolean = false,
    ) {
        fun hand(): BlackjackCards.Hand = BlackjackCards.value(cards)

        /** A natural only counts on an unsplit two-card hand. */
        fun natural(): Boolean = !fromSplit && BlackjackCards.isNatural(cards)
    }

    class Table(
        val baseStake: Long,
        val nonce: Int,
        val clientSeed: String,
        val serverSeed: String,
        var cursor: Int,
        val dealer: MutableList<Int>,
        val hands: MutableList<Seat>,
        var active: Int,
        var insurance: Long,
        var phase: Phase,
    ) {
        /**
         * Set by the call that settled this table, so the caller that triggered it gets the payout and the
         * per-hand result lines. Transient: a [Table] is rebuilt from the saved attributes on every call, so this
         * is only ever populated for the action that caused the settlement.
         */
        var settlement: Settlement? = null

        val shoe: List<Int> by lazy { BlackjackCards.shoe(clientSeed, serverSeed, nonce.toLong()) }

        val serverSeedHash: String by lazy { ProvablyFair.sha256Hex(serverSeed) }

        fun draw(): Int = shoe[cursor++]

        fun current(): Seat? = hands.getOrNull(active)

        /** Total coins currently committed: every hand's stake plus any insurance. */
        fun committed(): Long = hands.sumOf { it.stake } + insurance

        fun dealerUpcard(): Int = dealer.first()

        fun insuranceOffered(): Boolean =
            phase == Phase.PLAYER &&
                insurance == 0L &&
                dealer.size == 2 &&
                BlackjackCards.isAce(dealerUpcard()) &&
                hands.size == 1 &&
                hands[0].cards.size == 2 &&
                !hands[0].doubled
    }

    // ------------------------------------------------------------------ state

    fun active(player: Player): Table? {
        val stake = player.attr[STAKE]?.toLongOrNull() ?: return null
        val nonce = player.attr[NONCE]?.toIntOrNull() ?: return null
        val clientSeed = player.attr[CLIENT_SEED] ?: return null
        val serverSeed = player.attr[SERVER_SEED] ?: return null
        val cursor = player.attr[CURSOR]?.toIntOrNull() ?: return null
        val dealer = decodeCards(player.attr[DEALER].orEmpty())
        val hands = decodeHands(player.attr[HANDS].orEmpty())
        if (hands.isEmpty()) {
            return null
        }
        val phase = if (player.attr[PHASE] == Phase.SETTLED.name) Phase.SETTLED else Phase.PLAYER
        return Table(
            baseStake = stake,
            nonce = nonce,
            clientSeed = clientSeed,
            serverSeed = serverSeed,
            cursor = cursor,
            dealer = dealer.toMutableList(),
            hands = hands.toMutableList(),
            active = player.attr[ACTIVE]?.toIntOrNull() ?: 0,
            insurance = player.attr[INSURANCE]?.toLongOrNull() ?: 0L,
            phase = phase,
        )
    }

    fun isPlaying(player: Player): Boolean = active(player)?.phase == Phase.PLAYER

    private fun save(
        player: Player,
        table: Table,
    ) {
        player.attr[STAKE] = table.baseStake.toString()
        player.attr[NONCE] = table.nonce.toString()
        player.attr[CLIENT_SEED] = table.clientSeed
        player.attr[SERVER_SEED] = table.serverSeed
        player.attr[CURSOR] = table.cursor.toString()
        player.attr[DEALER] = table.dealer.joinToString(",")
        player.attr[HANDS] = encodeHands(table.hands)
        player.attr[ACTIVE] = table.active.toString()
        player.attr[INSURANCE] = table.insurance.toString()
        player.attr[PHASE] = table.phase.name
    }

    private fun clear(player: Player) {
        listOf(STAKE, NONCE, CLIENT_SEED, SERVER_SEED, CURSOR, DEALER, HANDS, ACTIVE, INSURANCE, PHASE)
            .forEach { player.attr.remove(it) }
    }

    // ------------------------------------------------------------------ play

    fun validate(
        player: Player,
        stake: Long,
    ): Rejection? {
        if (active(player) != null) {
            return Rejection.ALREADY_PLAYING
        }
        if (stake < CasinoWallet.MIN_WAGER) {
            return Rejection.STAKE_TOO_SMALL
        }
        if (stake > CasinoWallet.MAX_WAGER) {
            return Rejection.STAKE_TOO_LARGE
        }
        // Worst case for the house: four split hands, each doubled, all won. Checked up front so a win can always
        // be paid in coins.
        if (!CasinoWallet.fitsPayout(stake * MAX_HANDS * 2 * 2)) {
            return Rejection.PAYOUT_TOO_LARGE
        }
        if (CasinoWallet.balance(player) < stake) {
            return Rejection.NOT_ENOUGH_COINS
        }
        return null
    }

    /** Debits the stake and deals. Returns null when the bet was refused; nothing changes then. */
    fun deal(
        player: Player,
        stake: Long,
    ): Table? {
        if (validate(player, stake) != null) {
            return null
        }
        if (!CasinoWallet.withdraw(player, stake)) {
            return null
        }
        val clientSeed = CasinoSeeds.clientSeed(player)
        val serverSeed = CasinoSeeds.serverSeed(player)
        val nonce = CasinoSeeds.takeNonce(player)

        val table =
            Table(
                baseStake = stake,
                nonce = nonce,
                clientSeed = clientSeed,
                serverSeed = serverSeed,
                cursor = 0,
                dealer = mutableListOf(),
                hands = mutableListOf(Seat(mutableListOf(), stake)),
                active = 0,
                insurance = 0,
                phase = Phase.PLAYER,
            )
        // Player, dealer, player, dealer - the usual order, and the order the shoe is verified in.
        table.hands[0].cards += table.draw()
        table.dealer += table.draw()
        table.hands[0].cards += table.draw()
        table.dealer += table.draw()

        // A natural on either side ends the hand immediately, except that an ace up still offers insurance first.
        if (table.hands[0].natural() || (BlackjackCards.isNatural(table.dealer) && !table.insuranceOffered())) {
            table.hands[0].done = true
            save(player, table)
            return settle(player, table)
        }
        save(player, table)
        return table
    }

    /** Buys insurance for half the current stake. Only legal when the dealer shows an ace. */
    fun insure(player: Player): Table? {
        val table = active(player) ?: return null
        if (!table.insuranceOffered()) {
            return null
        }
        val cost = table.hands[0].stake / 2
        if (cost <= 0 || !CasinoWallet.withdraw(player, cost)) {
            return null
        }
        table.insurance = cost
        // With insurance settled, a dealer natural resolves the hand right away.
        if (BlackjackCards.isNatural(table.dealer)) {
            table.hands[0].done = true
            save(player, table)
            return settle(player, table)
        }
        save(player, table)
        return table
    }

    /** Declines insurance, which lets a dealer natural resolve. */
    fun declineInsurance(player: Player): Table? {
        val table = active(player) ?: return null
        if (!table.insuranceOffered()) {
            return null
        }
        table.insurance = -1 // sentinel: offered and refused, so it is not offered again
        if (BlackjackCards.isNatural(table.dealer)) {
            table.hands[0].done = true
            save(player, table)
            return settle(player, table)
        }
        save(player, table)
        return table
    }

    fun hit(player: Player): Table? {
        val table = active(player) ?: return null
        if (table.phase != Phase.PLAYER) {
            return null
        }
        val seat = table.current() ?: return null
        if (seat.done || seat.splitAce) {
            return null
        }
        seat.cards += table.draw()
        if (seat.hand().bust || seat.hand().total == 21) {
            seat.done = true
        }
        return advance(player, table)
    }

    fun stand(player: Player): Table? {
        val table = active(player) ?: return null
        if (table.phase != Phase.PLAYER) {
            return null
        }
        val seat = table.current() ?: return null
        seat.done = true
        return advance(player, table)
    }

    /** Doubles this hand's stake for exactly one more card. Legal on any two-card hand, including after a split. */
    fun double(player: Player): Table? {
        val table = active(player) ?: return null
        if (table.phase != Phase.PLAYER) {
            return null
        }
        val seat = table.current() ?: return null
        if (seat.done || seat.doubled || seat.splitAce || seat.cards.size != 2) {
            return null
        }
        if (!CasinoWallet.withdraw(player, seat.stake)) {
            return null
        }
        seat.stake *= 2
        seat.doubled = true
        seat.cards += table.draw()
        seat.done = true
        return advance(player, table)
    }

    /** Splits a two-card hand of equal-value cards into two hands, each with its own stake. */
    fun split(player: Player): Table? {
        val table = active(player) ?: return null
        if (table.phase != Phase.PLAYER) {
            return null
        }
        val seat = table.current() ?: return null
        if (seat.done || seat.doubled || table.hands.size >= MAX_HANDS || !BlackjackCards.canSplit(seat.cards)) {
            return null
        }
        if (!CasinoWallet.withdraw(player, seat.stake)) {
            return null
        }
        val moved = seat.cards.removeAt(1)
        val splittingAces = BlackjackCards.isAce(moved)

        val second = Seat(mutableListOf(moved), seat.stake, fromSplit = true)
        seat.fromSplit = true
        table.hands.add(table.active + 1, second)

        // Each half draws one card immediately, exactly as at a real table.
        seat.cards += table.draw()
        second.cards += table.draw()

        if (splittingAces) {
            // "Hitting split Aces is limited to one hit": both halves are finished after that single card.
            seat.splitAce = true
            second.splitAce = true
            seat.done = true
            second.done = true
        } else {
            if (seat.hand().total == 21) seat.done = true
            if (second.hand().total == 21) second.done = true
        }
        return advance(player, table)
    }

    /** Moves to the next unfinished hand, or runs the dealer and settles when there are none. */
    private fun advance(
        player: Player,
        table: Table,
    ): Table {
        while (table.active < table.hands.size && table.hands[table.active].done) {
            table.active++
        }
        if (table.active < table.hands.size) {
            save(player, table)
            return table
        }
        save(player, table)
        return settle(player, table)
    }

    // ------------------------------------------------------------------ settlement

    data class Settlement(
        val payout: Long,
        val credited: CasinoWallet.Payout,
        val lines: List<String>,
    )

    /**
     * Plays the dealer out and pays every hand.
     *
     * The dealer only draws when at least one hand can still be beaten - with every hand bust the dealer's cards
     * are irrelevant and drawing them would burn shoe positions the verifier expects to stay unused.
     */
    private fun settle(
        player: Player,
        table: Table,
    ): Table {
        val anyLive = table.hands.any { !it.hand().bust }
        val dealerNatural = BlackjackCards.isNatural(table.dealer)
        if (anyLive && !dealerNatural) {
            // "Draw on 16 or less and stand on any 17" - soft 17 included.
            while (BlackjackCards.value(table.dealer).total < 17) {
                table.dealer += table.draw()
            }
        }

        val dealerHand = BlackjackCards.value(table.dealer)
        var payout = 0L
        val lines = mutableListOf<String>()

        // Insurance is settled against the dealer's natural, independently of the hands.
        if (table.insurance > 0) {
            if (dealerNatural) {
                // 2:1 on the insurance bet, plus the insurance stake back.
                payout += table.insurance * 3
                lines += "Insurance paid ${CasinoWallet.format(table.insurance * 2)}."
            } else {
                lines += "Insurance lost."
            }
        }

        table.hands.forEachIndexed { index, seat ->
            val hand = seat.hand()
            val label = if (table.hands.size == 1) "Hand" else "Hand ${index + 1}"
            when {
                hand.bust -> lines += "$label bust (${hand.total})."
                seat.natural() && !dealerNatural -> {
                    // 3:2, stake included: stake + stake * 3 / 2.
                    val win = seat.stake + seat.stake * 3 / 2
                    payout += win
                    lines += "$label blackjack! Paid ${CasinoWallet.format(win)}."
                }
                seat.natural() && dealerNatural -> {
                    payout += seat.stake
                    lines += "$label pushes against the dealer's blackjack."
                }
                dealerNatural -> lines += "$label loses to the dealer's blackjack."
                dealerHand.bust -> {
                    payout += seat.stake * 2
                    lines += "$label wins, dealer bust (${dealerHand.total})."
                }
                hand.total > dealerHand.total -> {
                    payout += seat.stake * 2
                    lines += "$label wins ${hand.total} to ${dealerHand.total}."
                }
                hand.total == dealerHand.total -> {
                    payout += seat.stake
                    lines += "$label pushes on ${hand.total}."
                }
                else -> lines += "$label loses ${hand.total} to ${dealerHand.total}."
            }
        }

        val staked = table.hands.sumOf { it.stake } + maxOf(table.insurance, 0)
        table.phase = Phase.SETTLED
        save(player, table)

        val credited = if (payout > 0) CasinoWallet.deposit(player, payout) else CasinoWallet.Payout(0, 0, 0)
        CasinoHistory.record(
            player,
            CasinoRound(
                game = CasinoGame.BLACKJACK,
                stake = staked,
                payout = payout,
                clientSeed = table.clientSeed,
                serverSeedHash = table.serverSeedHash,
                nonce = table.nonce,
                detail =
                    "dealer=${dealerHand.total} hands=${table.hands.joinToString("/") { it.hand().total.toString() }}" +
                        if (table.insurance > 0) " insurance=${table.insurance}" else "",
            ),
        )
        table.settlement = Settlement(payout, credited, lines)
        return table
    }

    /** Clears a settled table so the player can bet again. */
    fun finish(player: Player) {
        if (active(player)?.phase == Phase.SETTLED) {
            clear(player)
        }
    }

    // ------------------------------------------------------------------ encoding

    private fun decodeCards(raw: String): List<Int> =
        raw.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0 until BlackjackCards.CARDS_PER_DECK }

    /** `cards:stake:done:doubled:splitAce:fromSplit` per hand, hands separated by `;`. */
    private fun encodeHands(hands: List<Seat>): String =
        hands.joinToString(";") { seat ->
            listOf(
                seat.cards.joinToString(","),
                seat.stake.toString(),
                if (seat.done) "1" else "0",
                if (seat.doubled) "1" else "0",
                if (seat.splitAce) "1" else "0",
                if (seat.fromSplit) "1" else "0",
            ).joinToString(":")
        }

    private fun decodeHands(raw: String): List<Seat> {
        if (raw.isBlank()) {
            return emptyList()
        }
        return raw.split(';').mapNotNull { chunk ->
            val parts = chunk.split(':')
            if (parts.size < 6) {
                return@mapNotNull null
            }
            Seat(
                cards = decodeCards(parts[0]).toMutableList(),
                stake = parts[1].toLongOrNull() ?: return@mapNotNull null,
                done = parts[2] == "1",
                doubled = parts[3] == "1",
                splitAce = parts[4] == "1",
                fromSplit = parts[5] == "1",
            )
        }
    }
}
