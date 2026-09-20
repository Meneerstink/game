package gg.rsmod.plugins.content.activity.casino

/**
 * Card arithmetic for the 8-deck blackjack shoe.
 *
 * SOURCE: Roat Pkz wiki "Blackjack" - "8 standard decks shuffled every game". A card is an index `0..415` into the
 * shuffled shoe; its identity is `index % 52`, from which rank is `% 13` (0 = Ace, 10 = Jack, 11 = Queen, 12 =
 * King) and suit is `/ 13`. This is the same 0-53 card numbering the MIT verifier's card images use, so a shoe we
 * publish lines up with `html/blackjack.html`.
 */
object BlackjackCards {
    const val DECKS = 8
    const val CARDS_PER_DECK = 52
    const val SHOE_SIZE = DECKS * CARDS_PER_DECK

    val RANK_NAMES = arrayOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
    val SUIT_NAMES = arrayOf("Hearts", "Spades", "Diamonds", "Clubs")

    /** Builds the shoe in canonical order and shuffles it with the published algorithm. */
    fun shoe(
        clientSeed: String,
        serverSeed: String,
        nonce: Long,
    ): List<Int> {
        val cards = MutableList(SHOE_SIZE) { it % CARDS_PER_DECK }
        ProvablyFairBlackjack.shuffle(cards, clientSeed, serverSeed, nonce)
        return cards
    }

    fun rank(card: Int): Int = (card % CARDS_PER_DECK) % 13

    fun suit(card: Int): Int = (card % CARDS_PER_DECK) / 13

    fun isAce(card: Int): Boolean = rank(card) == 0

    /** Face cards and tens are all worth ten; an ace counts as one here and is promoted in [value]. */
    fun baseValue(card: Int): Int {
        val rank = rank(card)
        return when {
            rank == 0 -> 1
            rank >= 9 -> 10
            else -> rank + 1
        }
    }

    fun name(card: Int): String = "${RANK_NAMES[rank(card)]} of ${SUIT_NAMES[suit(card)]}"

    fun shortName(card: Int): String = RANK_NAMES[rank(card)]

    /** The best total for a hand, and whether an ace is still counted as eleven ("soft"). */
    fun value(cards: List<Int>): Hand {
        var total = 0
        var aces = 0
        for (card in cards) {
            total += baseValue(card)
            if (isAce(card)) {
                aces++
            }
        }
        val soft = aces > 0 && total + 10 <= 21
        if (soft) {
            total += 10
        }
        return Hand(total, soft)
    }

    data class Hand(
        val total: Int,
        val soft: Boolean,
    ) {
        val bust: Boolean
            get() = total > 21
    }

    /**
     * A natural: exactly two cards totalling 21.
     *
     * Only ever asked of a hand that was not split - under standard rules (and Roat's, which allows only one card
     * on split aces) 21 reached on a split hand pays as an ordinary win, not 3:2.
     */
    fun isNatural(cards: List<Int>): Boolean = cards.size == 2 && value(cards).total == 21

    /**
     * Roat Pkz wiki: "The game permits splitting identical-value hands", so a ten and a king split just as a pair
     * of eights does.
     */
    fun canSplit(cards: List<Int>): Boolean = cards.size == 2 && baseValue(cards[0]) == baseValue(cards[1])
}
