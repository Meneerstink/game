package gg.rsmod.plugins.content.activity.casino

import java.math.BigInteger

/**
 * The payout mathematics of the two house games that have a published return-to-player figure.
 *
 * SOURCE: the Roat Pkz wiki pages "Dice" ("a random number between 0 and 100", target 1-100, the roll must be
 * "higher" or "equal" to the target, "99% RTP") and "Mines" ("a 5x5 grid containing 25 tiles", 1-24 mines, cash out
 * at any time, "a 98% RTP"), together with the exact outcome counts of the MIT RNG in [ProvablyFairDice] and
 * [ProvablyFairMines].
 *
 * ADAPTED: the MIT repository publishes the RNG only - it contains no multiplier table - so the multipliers here are
 * derived from the sourced RTP rather than copied. The derivation is the standard one and reproduces the stated RTP
 * exactly: `multiplier = RTP / winChance`, where `winChance` is computed from the real number of winning outcomes of
 * the published algorithm. Every multiplier is therefore a consequence of two sourced numbers, not a hand-tuned
 * table, and the house edge is exactly `1 - RTP` at every target and every mine count.
 *
 * All payouts are computed in exact integer arithmetic and floored. Flooring is what gives the house its edge on the
 * final coin and guarantees a payout can never exceed the mathematically correct amount through rounding.
 */
object CasinoOdds {
    /** Roat Pkz wiki "Dice": "99% RTP". */
    const val DICE_RTP_PERCENT = 99

    /** Roat Pkz wiki "Mines": "a 98% RTP". */
    const val MINES_RTP_PERCENT = 98

    const val MIN_DICE_TARGET = 1
    const val MAX_DICE_TARGET = 100

    /**
     * Number of winning draws for [target]: the roll is the scaled `0 .. 10000` draw of [ProvablyFairDice] and the
     * player wins when it is at least `target * 100` (the wiki's "higher or equal to").
     */
    fun diceWinningOutcomes(target: Int): Int {
        require(target in MIN_DICE_TARGET..MAX_DICE_TARGET) { "target must be $MIN_DICE_TARGET-$MAX_DICE_TARGET" }
        return ProvablyFairDice.OUTCOMES - target * 100
    }

    /** Win chance for [target] as a fraction of 1. */
    fun diceWinChance(target: Int): Double = diceWinningOutcomes(target).toDouble() / ProvablyFairDice.OUTCOMES

    /** `RTP / winChance`, i.e. what one staked coin returns on a win. */
    fun diceMultiplier(target: Int): Double =
        (DICE_RTP_PERCENT.toDouble() / 100.0) * ProvablyFairDice.OUTCOMES / diceWinningOutcomes(target)

    /** Won iff the scaled draw is at least `target * 100`. */
    fun diceWins(
        rollScaled: Int,
        target: Int,
    ): Boolean {
        require(target in MIN_DICE_TARGET..MAX_DICE_TARGET) { "target must be $MIN_DICE_TARGET-$MAX_DICE_TARGET" }
        return rollScaled >= target * 100
    }

    /**
     * Total returned to the player on a winning dice roll, stake included, floored:
     * `stake * RTP% * 10001 / (100 * winningOutcomes)`.
     */
    fun dicePayout(
        stake: Long,
        target: Int,
    ): Long {
        require(stake >= 0) { "stake must not be negative" }
        return BigInteger
            .valueOf(stake)
            .multiply(BigInteger.valueOf(DICE_RTP_PERCENT.toLong()))
            .multiply(BigInteger.valueOf(ProvablyFairDice.OUTCOMES.toLong()))
            .divide(BigInteger.valueOf(100L).multiply(BigInteger.valueOf(diceWinningOutcomes(target).toLong())))
            .toLong()
    }

    /**
     * Mines multiplier after [gems] safe cells have been revealed on a board with [mines] mines.
     *
     * The chance of surviving [gems] picks is `C(25-mines, gems) / C(25, gems)`, so the fair multiplier is its
     * reciprocal scaled by the RTP.
     */
    fun minesMultiplier(
        mines: Int,
        gems: Int,
    ): Double {
        val (numerator, denominator) = minesFraction(mines, gems)
        return numerator.toDouble() / denominator.toDouble()
    }

    /** Total returned when cashing out after [gems] safe reveals, stake included, floored. */
    fun minesPayout(
        stake: Long,
        mines: Int,
        gems: Int,
    ): Long {
        require(stake >= 0) { "stake must not be negative" }
        val (numerator, denominator) = minesFraction(mines, gems)
        return BigInteger.valueOf(stake).multiply(numerator).divide(denominator).toLong()
    }

    /** The exact multiplier as a fraction, so payouts never drift through floating point. */
    private fun minesFraction(
        mines: Int,
        gems: Int,
    ): Pair<BigInteger, BigInteger> {
        require(mines in ProvablyFairMines.MIN_MINES..ProvablyFairMines.MAX_MINES) {
            "mines must be ${ProvablyFairMines.MIN_MINES}-${ProvablyFairMines.MAX_MINES}"
        }
        val safeCells = ProvablyFairMines.SLOTS - mines
        require(gems in 0..safeCells) { "gems must be 0-$safeCells" }
        // RTP * C(25, gems) / C(25 - mines, gems)
        val numerator =
            BigInteger
                .valueOf(MINES_RTP_PERCENT.toLong())
                .multiply(binomial(ProvablyFairMines.SLOTS, gems))
        val denominator = BigInteger.valueOf(100L).multiply(binomial(safeCells, gems))
        return numerator to denominator
    }

    private fun binomial(
        n: Int,
        k: Int,
    ): BigInteger {
        if (k < 0 || k > n) {
            return BigInteger.ZERO
        }
        var result = BigInteger.ONE
        for (i in 0 until k) {
            result = result.multiply(BigInteger.valueOf((n - i).toLong())).divide(BigInteger.valueOf((i + 1).toLong()))
        }
        return result
    }
}
