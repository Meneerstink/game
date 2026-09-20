package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.model.entity.Player

/**
 * Dice against the house.
 *
 * SOURCE: Roat Pkz wiki "Dice" - pick a target between 1 and 100, the server rolls 0.00-100.00, and the player wins
 * when the roll is at least the target. Lower targets win often for little, high targets rarely for a lot. 99% RTP,
 * so the multiplier is [CasinoOdds.diceMultiplier].
 *
 * A round is a single indivisible step: the stake is debited, the nonce is consumed, the roll is derived and the
 * payout is credited before [roll] returns. There is no interim state a disconnect or a replayed packet could sit
 * in - which is what makes dice the one game here that needs no persistence at all.
 */
object DiceGame {
    /** Everything the interface and the audit trail need about one settled roll. */
    data class Result(
        val target: Int,
        /** True when the bet was "roll under the target" rather than the sourced "roll over". */
        val under: Boolean,
        val rollScaled: Int,
        val won: Boolean,
        val stake: Long,
        val payout: Long,
        val multiplier: Double,
        val clientSeed: String,
        val serverSeedHash: String,
        val nonce: Int,
        val credited: CasinoWallet.Payout,
    ) {
        val rollPercentage: Double
            get() = rollScaled / 100.0
    }

    /** Why a bet was not accepted. The player is told; nothing is debited. */
    enum class Rejection(
        val message: String,
    ) {
        BAD_TARGET("Pick a number between ${CasinoOdds.MIN_DICE_TARGET} and ${CasinoOdds.MAX_DICE_TARGET}."),
        STAKE_TOO_SMALL("The minimum bet is ${CasinoWallet.MIN_WAGER} coins."),
        STAKE_TOO_LARGE("The maximum bet is ${CasinoWallet.MAX_WAGER} coins."),
        PAYOUT_TOO_LARGE("That bet could win more coins than you can hold. Lower your bet or your target."),
        NOT_ENOUGH_COINS("You do not have enough coins for that bet."),
    }

    /** A validated bet that has not been charged yet. */
    fun validate(
        player: Player,
        stake: Long,
        target: Int,
        under: Boolean = false,
    ): Rejection? {
        if (target !in CasinoOdds.MIN_DICE_TARGET..CasinoOdds.MAX_DICE_TARGET) {
            return Rejection.BAD_TARGET
        }
        if (stake < CasinoWallet.MIN_WAGER) {
            return Rejection.STAKE_TOO_SMALL
        }
        if (stake > CasinoWallet.MAX_WAGER) {
            return Rejection.STAKE_TOO_LARGE
        }
        // A win must be payable as coins. Checked before the bet, never discovered after the roll.
        if (!CasinoWallet.fitsPayout(CasinoOdds.dicePayout(stake, target, under))) {
            return Rejection.PAYOUT_TOO_LARGE
        }
        if (CasinoWallet.balance(player) < stake) {
            return Rejection.NOT_ENOUGH_COINS
        }
        return null
    }

    /**
     * Plays one round. Returns null when the bet was rejected or the stake could not be taken, in which case
     * nothing has changed.
     */
    fun roll(
        player: Player,
        stake: Long,
        target: Int,
        under: Boolean = false,
    ): Result? {
        if (validate(player, stake, target, under) != null) {
            return null
        }
        // Debit first: from here on the player is in a round they have paid for, and a duplicate packet that
        // reaches us can only ever start a second paid round, never a free one.
        if (!CasinoWallet.withdraw(player, stake)) {
            return null
        }

        val clientSeed = CasinoSeeds.clientSeed(player)
        val serverSeed = CasinoSeeds.serverSeed(player)
        val serverSeedHash = CasinoSeeds.serverSeedHash(player)
        val nonce = CasinoSeeds.takeNonce(player)

        val rollScaled = ProvablyFairDice.rollScaled(clientSeed, serverSeed, nonce)
        val won = CasinoOdds.diceWins(rollScaled, target, under)
        val payout = if (won) CasinoOdds.dicePayout(stake, target, under) else 0L
        val credited = if (payout > 0) CasinoWallet.deposit(player, payout) else CasinoWallet.Payout(0, 0, 0)

        val result =
            Result(
                target = target,
                under = under,
                rollScaled = rollScaled,
                won = won,
                stake = stake,
                payout = payout,
                multiplier = CasinoOdds.diceMultiplier(target, under),
                clientSeed = clientSeed,
                serverSeedHash = serverSeedHash,
                nonce = nonce,
                credited = credited,
            )

        CasinoHistory.record(
            player,
            CasinoRound(
                game = CasinoGame.DICE,
                stake = stake,
                payout = payout,
                clientSeed = clientSeed,
                serverSeedHash = serverSeedHash,
                nonce = nonce,
                detail = "${if (under) "under" else "over"}=$target roll=${format(result.rollPercentage)}",
            ),
        )
        return result
    }

    fun format(percentage: Double): String = String.format(java.util.Locale.ENGLISH, "%.2f", percentage)
}
