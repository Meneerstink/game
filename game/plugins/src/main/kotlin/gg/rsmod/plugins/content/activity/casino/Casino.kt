package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.model.entity.Player

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
