package gg.rsmod.plugins.content.activity.casino

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the payout mathematics to the two sourced return-to-player figures (Roat Pkz wiki: Dice 99%, Mines 98%).
 *
 * The multipliers are derived, not copied, so the tests that matter most are the RTP identities: for every target
 * and every mine count, `winChance * payout` must come out at the stated RTP of the stake, off by less than a
 * single coin of flooring. That is a much stronger guarantee than spot-checking a table, and it is what makes the
 * house edge provably exactly 1% and 2% rather than approximately so.
 */
class CasinoOddsTests {
    // ---------------------------------------------------------------- dice

    @Test
    fun `dice winning outcome counts follow the published roll range`() {
        // 10,001 discrete draws; the player wins when the draw is at least target * 100.
        assertEquals(9_901, CasinoOdds.diceWinningOutcomes(1))
        assertEquals(5_001, CasinoOdds.diceWinningOutcomes(50))
        assertEquals(1_001, CasinoOdds.diceWinningOutcomes(90))
        assertEquals(1, CasinoOdds.diceWinningOutcomes(100))
    }

    @Test
    fun `dice win boundary is inclusive`() {
        assertTrue(CasinoOdds.diceWins(rollScaled = 5_000, target = 50))
        assertTrue(CasinoOdds.diceWins(rollScaled = 10_000, target = 50))
        assertFalse(CasinoOdds.diceWins(rollScaled = 4_999, target = 50))
        assertTrue(CasinoOdds.diceWins(rollScaled = 10_000, target = 100))
        assertFalse(CasinoOdds.diceWins(rollScaled = 9_999, target = 100))
    }

    @Test
    fun `dice payout matches the hand-computed value`() {
        // floor(1,000,000 * 99 * 10001 / (100 * 5001)) = floor(990,099,000,000 / 500,100) = 1,979,802
        assertEquals(1_979_802L, CasinoOdds.dicePayout(1_000_000, 50))
    }

    @Test
    fun `dice returns exactly 99 percent of the stake at every target`() {
        val stake = 1_000_000_000L
        for (target in CasinoOdds.MIN_DICE_TARGET..CasinoOdds.MAX_DICE_TARGET) {
            val winners = CasinoOdds.diceWinningOutcomes(target).toLong()
            val payout = CasinoOdds.dicePayout(stake, target)
            // Expected return = P(win) * payout, computed without floating point.
            val expectedNumerator = winners * payout
            val ideal = stake * CasinoOdds.DICE_RTP_PERCENT / 100 * ProvablyFairDice.OUTCOMES
            // Flooring the payout can only ever cost the player, and at most one coin per winning outcome.
            assertTrue(
                expectedNumerator <= ideal,
                "target $target returns more than 99%: $expectedNumerator > $ideal",
            )
            assertTrue(
                ideal - expectedNumerator < ProvablyFairDice.OUTCOMES.toLong() * 2,
                "target $target loses too much to rounding: ${ideal - expectedNumerator}",
            )
        }
    }

    @Test
    fun `dice multiplier grows as the target gets harder`() {
        var previous = 0.0
        for (target in CasinoOdds.MIN_DICE_TARGET..CasinoOdds.MAX_DICE_TARGET) {
            val multiplier = CasinoOdds.diceMultiplier(target)
            assertTrue(multiplier > previous, "multiplier did not grow at target $target")
            previous = multiplier
        }
        assertEquals(0.99, CasinoOdds.diceMultiplier(1) * CasinoOdds.diceWinChance(1), 1e-9)
    }

    // ---------------------------------------------------------------- mines

    @Test
    fun `mines pays the stake back before anything is revealed`() {
        for (mines in ProvablyFairMines.MIN_MINES..ProvablyFairMines.MAX_MINES) {
            assertEquals(0.98, CasinoOdds.minesMultiplier(mines, 0), 1e-12)
        }
    }

    @Test
    fun `mines payout matches the hand-computed value`() {
        // 24 mines, one gem: 98 * C(25,1) / (100 * C(1,1)) = 24.5x
        assertEquals(24_500L, CasinoOdds.minesPayout(1_000, mines = 24, gems = 1))
    }

    @Test
    fun `mines returns exactly 98 percent of the stake at every cash-out point`() {
        val stake = 1_000_000_000L
        for (mines in ProvablyFairMines.MIN_MINES..ProvablyFairMines.MAX_MINES) {
            val safe = ProvablyFairMines.SLOTS - mines
            for (gems in 0..safe) {
                val payout = CasinoOdds.minesPayout(stake, mines, gems)
                // P(surviving `gems` picks) = C(safe, gems) / C(25, gems); multiply back out in exact integers.
                val survive = binomial(safe, gems)
                val total = binomial(ProvablyFairMines.SLOTS, gems)
                val expected = java.math.BigInteger.valueOf(payout).multiply(survive)
                val ideal =
                    java.math.BigInteger
                        .valueOf(stake)
                        .multiply(java.math.BigInteger.valueOf(CasinoOdds.MINES_RTP_PERCENT.toLong()))
                        .divide(java.math.BigInteger.valueOf(100))
                        .multiply(total)
                assertTrue(
                    expected <= ideal,
                    "mines=$mines gems=$gems returns more than 98%",
                )
                assertTrue(
                    ideal.subtract(expected) <= total,
                    "mines=$mines gems=$gems loses too much to rounding",
                )
            }
        }
    }

    @Test
    fun `mines multiplier grows with every safe reveal`() {
        for (mines in ProvablyFairMines.MIN_MINES..ProvablyFairMines.MAX_MINES) {
            val safe = ProvablyFairMines.SLOTS - mines
            var previous = 0.0
            for (gems in 0..safe) {
                val multiplier = CasinoOdds.minesMultiplier(mines, gems)
                assertTrue(multiplier > previous, "mines=$mines gems=$gems did not grow")
                previous = multiplier
            }
        }
    }

    @Test
    fun `mines rejects boards and cash-outs that cannot happen`() {
        assertFails { CasinoOdds.minesPayout(100, mines = 0, gems = 0) }
        assertFails { CasinoOdds.minesPayout(100, mines = 25, gems = 0) }
        // Only 24 safe cells exist on a one-mine board.
        assertFails { CasinoOdds.minesPayout(100, mines = 1, gems = 25) }
    }

    private fun assertFails(block: () -> Unit) {
        val failed =
            try {
                block()
                false
            } catch (expected: IllegalArgumentException) {
                true
            }
        assertTrue(failed, "expected an IllegalArgumentException")
    }

    private fun binomial(
        n: Int,
        k: Int,
    ): java.math.BigInteger {
        if (k < 0 || k > n) {
            return java.math.BigInteger.ZERO
        }
        var result = java.math.BigInteger.ONE
        for (i in 0 until k) {
            result =
                result
                    .multiply(java.math.BigInteger.valueOf((n - i).toLong()))
                    .divide(java.math.BigInteger.valueOf((i + 1).toLong()))
        }
        return result
    }
}
