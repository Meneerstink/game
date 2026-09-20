package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player

val FLOWER_MATCH_ATTR = AttributeKey<FlowerPokerMatch>()

fun Player.getFlowerMatch(): FlowerPokerMatch? = this.attr[FLOWER_MATCH_ATTR]

/**
 * An automated player-versus-player flower-poker match.
 *
 * SOURCE: Roat Pkz wiki "Flower Poker" - "a fully automated, player-versus-player (PvP) game - no hosts are
 * required", with "no house advantage or interference". The winner takes the whole pot; the house takes nothing,
 * so [pot] is exactly both stakes.
 *
 * One object is shared by reference between both players (the pattern `DuelArenaMatch` already uses on this
 * server), so a stake or accept change made by either side is immediately visible to both and there is no
 * two-object mirroring to drift.
 *
 * Fairness across two untrusting players is what the seed layout is for: the material is
 * `p1Seed : p2Seed : serverSeed`, so **neither player nor the server alone controls the deal**. The server seed is
 * minted per match and its hash is shown to both before anything is staked; it is revealed when the match ends,
 * at which point either player can replay every planted round. Seeds are captured at accept time, so a player
 * cannot change their client seed once they have seen the commitment and the pot.
 */
class FlowerPokerMatch(
    val challenger: Player,
    val opponent: Player,
) {
    enum class Stage { CONFIGURING, PLANTING, FINISHED }

    /** The per-match server seed. Secret until [finish]; only its hash is shown while the match runs. */
    private val serverSeed: String = ProvablyFair.randomSeed()

    val serverSeedHash: String = ProvablyFair.sha256Hex(serverSeed)

    var stake: Long = 0
        private set

    var challengerAccepted = false
        private set

    var opponentAccepted = false
        private set

    var stage = Stage.CONFIGURING
        private set

    /** Captured when the pot is escrowed, so a later client-seed change cannot alter a running match. */
    var challengerSeed: String = ""
        private set

    var opponentSeed: String = ""
        private set

    /** Rounds planted so far; the last one is decisive unless it was a replant. */
    val rounds = mutableListOf<FlowerPoker.Round>()

    var winner: Player? = null
        private set

    val pot: Long
        get() = stake * 2

    fun other(player: Player): Player = if (player == challenger) opponent else challenger

    fun accepted(player: Player): Boolean = if (player == challenger) challengerAccepted else opponentAccepted

    fun bothAccepted(): Boolean = challengerAccepted && opponentAccepted

    /** Any change to the pot drops both acceptances, so nobody can be dragged into a stake they did not see. */
    fun setStake(amount: Long) {
        if (stage != Stage.CONFIGURING) {
            return
        }
        stake = amount.coerceAtLeast(0)
        challengerAccepted = false
        opponentAccepted = false
    }

    fun setAccepted(
        player: Player,
        value: Boolean,
    ) {
        if (stage != Stage.CONFIGURING) {
            return
        }
        if (player == challenger) {
            challengerAccepted = value
        } else {
            opponentAccepted = value
        }
    }

    enum class StartResult(
        val message: String,
    ) {
        OK(""),
        NOT_READY("Both players must accept first."),
        BAD_STAKE("The stake must be at least ${CasinoWallet.MIN_WAGER} coins."),
        STAKE_TOO_LARGE("The stake is too large."),
        CHALLENGER_SHORT("%s does not have enough coins."),
        OPPONENT_SHORT("%s does not have enough coins."),
    }

    /**
     * Escrows both stakes and plants the match out.
     *
     * The two debits are all-or-nothing together: if the second one fails the first is refunded, so the match can
     * never take one player's coins without taking the other's. Nothing is credited until a winner exists.
     */
    fun start(): StartResult {
        if (stage != Stage.CONFIGURING) {
            return StartResult.NOT_READY
        }
        if (!bothAccepted()) {
            return StartResult.NOT_READY
        }
        if (stake < CasinoWallet.MIN_WAGER) {
            return StartResult.BAD_STAKE
        }
        if (stake > CasinoWallet.MAX_WAGER || !CasinoWallet.fitsPayout(pot)) {
            return StartResult.STAKE_TOO_LARGE
        }
        if (CasinoWallet.balance(challenger) < stake) {
            return StartResult.CHALLENGER_SHORT
        }
        if (CasinoWallet.balance(opponent) < stake) {
            return StartResult.OPPONENT_SHORT
        }

        if (!CasinoWallet.withdraw(challenger, stake)) {
            return StartResult.CHALLENGER_SHORT
        }
        if (!CasinoWallet.withdraw(opponent, stake)) {
            // Put the first stake straight back; the match never happened.
            CasinoWallet.refund(challenger, stake)
            CasinoHistory.logAdjustment(challenger, CasinoGame.FLOWER_POKER, "opponent_stake_failed_refund", stake)
            return StartResult.OPPONENT_SHORT
        }

        challengerSeed = CasinoSeeds.clientSeed(challenger)
        opponentSeed = CasinoSeeds.clientSeed(opponent)
        stage = Stage.PLANTING
        rounds.clear()
        rounds += FlowerPoker.play(challengerSeed, opponentSeed, serverSeed)
        return StartResult.OK
    }

    /** The decisive round, once the match has been planted. */
    fun decisive(): FlowerPoker.Round? = rounds.lastOrNull()?.takeIf { !it.replant }

    /**
     * Pays the pot to the winner and closes the match. Safe to call once; later calls do nothing.
     *
     * The server seed is revealed here and only here - every round it produced is already settled, so publishing
     * it costs nothing and is what lets either player audit the match afterwards.
     */
    fun finish(): Settlement? {
        if (stage != Stage.PLANTING) {
            return null
        }
        val decisive = decisive() ?: return null
        val won = if (decisive.outcome > 0) challenger else opponent
        winner = won
        stage = Stage.FINISHED

        val credited = CasinoWallet.deposit(won, pot)
        val detail =
            "rounds=${rounds.size} ${challenger.username}=${decisive.firstHand.displayName} " +
                "${opponent.username}=${decisive.secondHand.displayName} winner=${won.username}"

        // Both sides get a history row: the winner's payout is the pot, the loser's is nothing.
        CasinoHistory.record(
            challenger,
            CasinoRound(
                game = CasinoGame.FLOWER_POKER,
                stake = stake,
                payout = if (won == challenger) pot else 0L,
                clientSeed = challengerSeed,
                serverSeedHash = serverSeedHash,
                nonce = rounds.size,
                detail = detail,
            ),
        )
        CasinoHistory.record(
            opponent,
            CasinoRound(
                game = CasinoGame.FLOWER_POKER,
                stake = stake,
                payout = if (won == opponent) pot else 0L,
                clientSeed = opponentSeed,
                serverSeedHash = serverSeedHash,
                nonce = rounds.size,
                detail = detail,
            ),
        )
        CasinoHistory.logReveal(won, CasinoSeeds.Revealed(serverSeed, rounds.size))
        return Settlement(won, pot, credited, decisive, serverSeed)
    }

    /**
     * Cancels a match that never reached [Stage.PLANTING]. Nothing has been escrowed at that point, so there is
     * nothing to refund - this only tears the shared object down.
     */
    fun cancel() {
        if (stage == Stage.PLANTING) {
            return
        }
        stage = Stage.FINISHED
        clear()
    }

    fun clear() {
        challenger.attr.remove(FLOWER_MATCH_ATTR)
        opponent.attr.remove(FLOWER_MATCH_ATTR)
    }

    data class Settlement(
        val winner: Player,
        val pot: Long,
        val credited: CasinoWallet.Payout,
        val decisive: FlowerPoker.Round,
        val revealedServerSeed: String,
    )
}
