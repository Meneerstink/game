package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.mechanics.pvp.RemoteAccess

/**
 * The one entry point the interfaces and plugin scripts use.
 *
 * Its job is the rules that span games rather than any single game: a player may hold only one live round at a
 * time, and the seed pair may not move while a round is live.
 *
 * That second rule is load-bearing. A live Mines board and a live Blackjack table both store the plaintext server
 * seed they were dealt from, and both derive their hidden state (where the mines are, what the next card is) from
 * it. Rotating the seed publishes that plaintext - so rotating mid-round would hand the player the answer to a
 * board they are still playing. [rotateSeed] therefore refuses while anything is live, and [setClientSeed] refuses
 * alongside it so the two controls behave the same way.
 */
object Casino {
    /**
     * Bug search 2026-10-02: `dice`, `mines` and `blackjack` opened from anywhere, so coins could be put into a round in
     * the middle of a fight. Opening a game and every action that puts coins into one follows the shared remote-service
     * rule (safe zone, out of combat, not dying or locked - [RemoteAccess]). True when refused (the player is told why).
     */
    fun refusePlay(player: Player): Boolean = RemoteAccess.refuse(player, "play casino games")

    /** [refusePlay] without telling [player] - for checking the other side of a two-player game. */
    fun refusalOf(player: Player): String? = RemoteAccess.refusal(player, "play casino games")

    /**
     * Settles every round [player] still has coins in, at the value the round has right now, and returns those coins
     * instead of crediting them: called by the death resolution, which hands them to the killer, the gravestone or (on a
     * safe death) back to the player like any other carried coins. Mines cashes out, Blackjack stands every open hand, and
     * a Flower Poker match being planted pays its fixed result - the dying player's share is returned, an opponent's pot
     * is paid to them normally. Without this a live stake was outside the inventory at death and came back afterwards.
     */
    fun settleForDeath(player: Player): Long {
        var owed = 0L
        var settled = MinesGame.isPlaying(player) || BlackjackGame.isPlaying(player)
        owed += MinesGame.settleForDeath(player)
        owed += BlackjackGame.settleForDeath(player)
        val match = player.getFlowerMatch()
        if (match != null && match.stage == FlowerPokerMatch.Stage.PLANTING) {
            settled = true
            match.finish { winner, pot ->
                if (winner == player) {
                    owed += pot
                    CasinoWallet.Payout(0, 0, 0)
                } else {
                    CasinoWallet.deposit(winner, pot)
                }
            }
        }
        if (settled) {
            player.message("Your unfinished casino round was settled when you died: ${CasinoWallet.format(owed)} coins.")
            gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.ITEM) {
                "casino round of ${player.username} settled at death: $owed coins handed to the death resolution at ${player.tile}"
            }
        }
        return owed
    }

    /**
     * What [settleForDeath] would hand to a killer right now, for the risk read-outs ([gg.rsmod.plugins.content.mechanics.
     * death.DeathRules.pvpLoss]) - read only. Mines: the current cash-out value. Blackjack: the coins committed to the
     * table (the stand result is only known once the dealer plays). Flower Poker: the pot if this player won the match.
     */
    fun riskCoins(player: Player): Long {
        var coins = MinesGame.active(player)?.cashoutValue() ?: 0L
        BlackjackGame.active(player)?.takeIf { it.phase == BlackjackGame.Phase.PLAYER }?.let { coins += it.committed() }
        val match = player.getFlowerMatch()
        if (match != null && match.stage == FlowerPokerMatch.Stage.PLANTING) {
            val decisive = match.decisive()
            val winner = if (decisive == null) null else if (decisive.outcome > 0) match.challenger else match.opponent
            if (winner == player) coins += match.pot
        }
        return coins
    }

    /** True while the player is in any round that is still deciding coins. */
    fun hasLiveRound(player: Player): Boolean =
        MinesGame.isPlaying(player) ||
            BlackjackGame.isPlaying(player) ||
            player.getFlowerMatch()?.stage == FlowerPokerMatch.Stage.PLANTING

    /** Names the live round, for the "you can't do that right now" message. */
    fun liveRoundName(player: Player): String? =
        when {
            MinesGame.isPlaying(player) -> CasinoGame.MINES.displayName
            BlackjackGame.isPlaying(player) -> CasinoGame.BLACKJACK.displayName
            player.getFlowerMatch()?.stage == FlowerPokerMatch.Stage.PLANTING -> CasinoGame.FLOWER_POKER.displayName
            else -> null
        }

    enum class SeedChange(
        val message: String,
    ) {
        OK("Done."),
        ROUND_LIVE("You cannot change your seeds while a game is running."),
        INVALID_SEED(
            "A seed may only use letters, numbers and hyphens, up to ${ProvablyFair.MAX_CLIENT_SEED_LENGTH} characters.",
        ),
    }

    fun setClientSeed(
        player: Player,
        seed: String,
    ): SeedChange {
        if (hasLiveRound(player)) {
            return SeedChange.ROUND_LIVE
        }
        return if (CasinoSeeds.setClientSeed(player, seed)) SeedChange.OK else SeedChange.INVALID_SEED
    }

    /** Retires the live server seed, publishes it and mints a new one. */
    fun rotateSeed(player: Player): CasinoSeeds.Revealed? {
        if (hasLiveRound(player)) {
            return null
        }
        val revealed = CasinoSeeds.rotate(player)
        CasinoHistory.logReveal(player, revealed)
        // Audit E-07: the old seed is public now. Save the new one at once: after a crash before the autosave the
        // revealed seed would be live again and every result it decides would be known in advance.
        player.persistNow()
        return revealed
    }

    /** What the "provably fair" panel shows. */
    data class FairnessView(
        val clientSeed: String,
        val serverSeedHash: String,
        val shortHash: String,
        val nextNonce: Int,
        val previous: CasinoSeeds.Revealed?,
    )

    fun fairness(player: Player): FairnessView {
        val hash = CasinoSeeds.serverSeedHash(player)
        return FairnessView(
            clientSeed = CasinoSeeds.clientSeed(player),
            serverSeedHash = hash,
            shortHash = ProvablyFair.shortHash(hash),
            nextNonce = CasinoSeeds.peekNonce(player),
            previous = CasinoSeeds.lastRevealed(player),
        )
    }
}
