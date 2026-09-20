package gg.rsmod.plugins.content.activity.casino

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

import kotlin.test.assertTrue

/**
 * The rules of blackjack and flower poker, tested as pure functions - no world, no player, no cache.
 *
 * Everything asserted here is a sourced rule from the Roat Pkz wiki pages, so these tests are the record of what
 * "correct" means for the two games whose rules the MIT repository does not ship.
 */
class CasinoRulesTests {
    // ---------------------------------------------------------------- blackjack cards

    /** Builds a card of the given rank (0 = Ace, 9 = Ten, 12 = King) in the first suit. */
    private fun card(rank: Int): Int = rank

    private val ace = card(0)
    private val two = card(1)
    private val five = card(4)
    private val six = card(5)
    private val seven = card(6)
    private val nine = card(8)
    private val ten = card(9)
    private val jack = card(10)
    private val queen = card(11)
    private val king = card(12)

    @Test
    fun `card values follow blackjack scoring`() {
        assertEquals(1, BlackjackCards.baseValue(ace))
        assertEquals(2, BlackjackCards.baseValue(two))
        assertEquals(10, BlackjackCards.baseValue(ten))
        assertEquals(10, BlackjackCards.baseValue(jack))
        assertEquals(10, BlackjackCards.baseValue(queen))
        assertEquals(10, BlackjackCards.baseValue(king))
    }

    @Test
    fun `an ace counts as eleven only while it fits`() {
        assertEquals(BlackjackCards.Hand(21, soft = true), BlackjackCards.value(listOf(ace, king)))
        assertEquals(BlackjackCards.Hand(17, soft = true), BlackjackCards.value(listOf(ace, six)))
        // Adding a ten to soft 17 drops the ace back to one: 1 + 6 + 10 = 17, now hard.
        assertEquals(BlackjackCards.Hand(17, soft = false), BlackjackCards.value(listOf(ace, six, ten)))
        // Two aces cannot both be eleven.
        assertEquals(BlackjackCards.Hand(12, soft = true), BlackjackCards.value(listOf(ace, ace)))
        assertEquals(BlackjackCards.Hand(13, soft = true), BlackjackCards.value(listOf(ace, ace, ace)))
    }

    @Test
    fun `bust is over twenty one`() {
        assertTrue(BlackjackCards.value(listOf(king, queen, two)).bust)
        assertFalse(BlackjackCards.value(listOf(king, queen)).bust)
        assertFalse(BlackjackCards.value(listOf(ace, king)).bust)
    }

    @Test
    fun `a natural is twenty one on exactly two cards`() {
        assertTrue(BlackjackCards.isNatural(listOf(ace, king)))
        assertFalse(BlackjackCards.isNatural(listOf(seven, seven, seven)))
        assertFalse(BlackjackCards.isNatural(listOf(king, nine)))
    }

    @Test
    fun `splitting is allowed on equal card values, not just equal ranks`() {
        // Roat Pkz wiki: "splitting identical-value hands".
        assertTrue(BlackjackCards.canSplit(listOf(ten, king)))
        assertTrue(BlackjackCards.canSplit(listOf(jack, queen)))
        assertTrue(BlackjackCards.canSplit(listOf(five, five)))
        assertFalse(BlackjackCards.canSplit(listOf(nine, ten)))
        assertFalse(BlackjackCards.canSplit(listOf(ace, king)))
        assertFalse(BlackjackCards.canSplit(listOf(five, five, five)))
    }

    @Test
    fun `the shoe holds eight of every card`() {
        val shoe = BlackjackCards.shoe("c", "s", 0)
        assertEquals(416, shoe.size)
        val counts = shoe.groupingBy { it }.eachCount()
        assertEquals(52, counts.size)
        assertTrue(counts.values.all { it == 8 }, "every card must appear exactly 8 times: $counts")
    }

    // ---------------------------------------------------------------- flower poker

    private fun hand(vararg flowers: CasinoFlower) = flowers.toList()

    @Test
    fun `every flower poker hand is ranked as the wiki lists it`() {
        val r = CasinoFlower.RED
        val b = CasinoFlower.BLUE
        val y = CasinoFlower.YELLOW
        val p = CasinoFlower.PURPLE
        val o = CasinoFlower.ORANGE

        assertEquals(FlowerPokerHand.BUST, FlowerPoker.evaluate(hand(r, b, y, p, o)))
        assertEquals(FlowerPokerHand.ONE_PAIR, FlowerPoker.evaluate(hand(r, r, y, p, o)))
        assertEquals(FlowerPokerHand.TWO_PAIR, FlowerPoker.evaluate(hand(r, r, y, y, o)))
        assertEquals(FlowerPokerHand.THREE_OAK, FlowerPoker.evaluate(hand(r, r, r, p, o)))
        assertEquals(FlowerPokerHand.FULL_HOUSE, FlowerPoker.evaluate(hand(r, r, r, y, y)))
        assertEquals(FlowerPokerHand.FOUR_OAK, FlowerPoker.evaluate(hand(r, r, r, r, o)))
        assertEquals(FlowerPokerHand.FIVE_OAK, FlowerPoker.evaluate(hand(r, r, r, r, r)))
    }

    @Test
    fun `hand ranks are ordered lowest to highest`() {
        val ordered =
            listOf(
                FlowerPokerHand.BUST,
                FlowerPokerHand.ONE_PAIR,
                FlowerPokerHand.TWO_PAIR,
                FlowerPokerHand.THREE_OAK,
                FlowerPokerHand.FULL_HOUSE,
                FlowerPokerHand.FOUR_OAK,
                FlowerPokerHand.FIVE_OAK,
            )
        assertEquals(ordered, ordered.sortedBy { it.rank })
    }

    @Test
    fun `a rare flower forces a replant whatever the hands are`() {
        val winning = hand(CasinoFlower.RED, CasinoFlower.RED, CasinoFlower.RED, CasinoFlower.RED, CasinoFlower.RED)
        val losing = hand(CasinoFlower.BLUE, CasinoFlower.YELLOW, CasinoFlower.PURPLE, CasinoFlower.ORANGE, CasinoFlower.BLACK)
        val round = FlowerPoker.Round(0, winning, losing)
        assertTrue(round.rareDrawn)
        assertTrue(round.replant, "a black flower must replant even against five of a kind")
        assertEquals(0, round.outcome)
    }

    @Test
    fun `equal hands replant because colour carries no rank`() {
        val redPair = hand(CasinoFlower.RED, CasinoFlower.RED, CasinoFlower.YELLOW, CasinoFlower.PURPLE, CasinoFlower.ORANGE)
        val bluePair = hand(CasinoFlower.BLUE, CasinoFlower.BLUE, CasinoFlower.YELLOW, CasinoFlower.PURPLE, CasinoFlower.ORANGE)
        val round = FlowerPoker.Round(0, redPair, bluePair)
        assertEquals(FlowerPokerHand.ONE_PAIR, round.firstHand)
        assertEquals(FlowerPokerHand.ONE_PAIR, round.secondHand)
        assertTrue(round.replant)
    }

    @Test
    fun `the higher hand wins`() {
        val trips = hand(CasinoFlower.RED, CasinoFlower.RED, CasinoFlower.RED, CasinoFlower.PURPLE, CasinoFlower.ORANGE)
        val pair = hand(CasinoFlower.BLUE, CasinoFlower.BLUE, CasinoFlower.YELLOW, CasinoFlower.PURPLE, CasinoFlower.ORANGE)
        assertEquals(1, FlowerPoker.Round(0, trips, pair).outcome)
        assertEquals(-1, FlowerPoker.Round(0, pair, trips).outcome)
    }

    @Test
    fun `a match plays until someone wins and consumes the stream in order`() {
        val rounds = FlowerPoker.play("alice", "bob", "srv")
        assertTrue(rounds.isNotEmpty())
        // Only the last round may be decisive; every earlier one must have been a replant.
        rounds.dropLast(1).forEach { assertTrue(it.replant, "round ${it.index} should have been a replant") }
        assertFalse(rounds.last().replant, "the match never resolved")
        // Round indices are consecutive from zero, so no draws are skipped or reused.
        assertEquals(rounds.indices.toList(), rounds.map { it.index })
    }

    @Test
    fun `a replant deals new flowers rather than re-rolling the same ones`() {
        val first = FlowerPoker.round("a", "b", "s", 0)
        val second = FlowerPoker.round("a", "b", "s", 1)
        assertTrue(first.first != second.first || first.second != second.second)
    }

    @Test
    fun `rounds are reproducible from the three seeds`() {
        assertEquals(FlowerPoker.round("a", "b", "s", 3), FlowerPoker.round("a", "b", "s", 3))
        assertTrue(FlowerPoker.round("a", "b", "s", 3) != FlowerPoker.round("a", "b", "other", 3))
    }

    @Test
    fun `the decisive round is the one that ends the match`() {
        val rounds = FlowerPoker.play("alice", "bob", "srv")
        val decisive = rounds.last()
        assertNotNull(decisive)
        assertFalse(decisive.replant)
        assertTrue(decisive.outcome != 0)
    }

    @Test
    fun `evaluate refuses a hand that is not five flowers`() {
        val failed =
            try {
                FlowerPoker.evaluate(listOf(CasinoFlower.RED))
                false
            } catch (expected: IllegalArgumentException) {
                true
            }
        assertTrue(failed)
    }

    @Test
    fun `flower colours map to the cache ids the match plants`() {
        // Item and object ids are the classic flower set; a wrong id here would plant the wrong flower in-game.
        assertEquals(2462, CasinoFlower.RED.itemId)
        assertEquals(2464, CasinoFlower.BLUE.itemId)
        assertEquals(2466, CasinoFlower.YELLOW.itemId)
        assertEquals(2468, CasinoFlower.PURPLE.itemId)
        assertEquals(2470, CasinoFlower.ORANGE.itemId)
        assertEquals(2472, CasinoFlower.MIXED.itemId)
        assertEquals(2460, CasinoFlower.ASSORTED.itemId)
        assertEquals(2476, CasinoFlower.BLACK.itemId)
        assertEquals(2474, CasinoFlower.WHITE.itemId)
        assertEquals(2981, CasinoFlower.RED.objectId)
        assertEquals(2988, CasinoFlower.BLACK.objectId)
        assertEquals(2987, CasinoFlower.WHITE.objectId)
        assertTrue(CasinoFlower.BLACK.rare && CasinoFlower.WHITE.rare)
        assertTrue(CasinoFlower.values.none { it.rare && it != CasinoFlower.BLACK && it != CasinoFlower.WHITE })
    }
}
