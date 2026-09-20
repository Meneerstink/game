package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.inputInt
import gg.rsmod.plugins.api.ext.inputString
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options

/**
 * The prompts shared by every casino screen.
 *
 * These live in a compiled Kotlin file rather than in a `.plugin.kts` because a script's top-level functions are
 * local to that script: the flower-poker script cannot call a helper declared in the dice/mines/blackjack one.
 */
object CasinoDialogs {
    /** Asks for an amount and stores it as the working bet. */
    suspend fun askBet(
        task: QueueTask,
        player: Player,
    ) {
        val amount = task.inputInt("Enter your bet:")
        if (amount <= 0) {
            return
        }
        CasinoScreens.setBet(player, amount.toLong())
    }

    /** Lets the player choose their own client seed, which is what stops the server picking all the material. */
    suspend fun askClientSeed(
        task: QueueTask,
        player: Player,
    ) {
        val seed = task.inputString("Enter your client seed:")
        when (Casino.setClientSeed(player, seed)) {
            Casino.SeedChange.OK -> player.message("Your client seed is now: ${CasinoSeeds.clientSeed(player)}")
            Casino.SeedChange.ROUND_LIVE -> player.message(Casino.SeedChange.ROUND_LIVE.message)
            Casino.SeedChange.INVALID_SEED -> player.message(Casino.SeedChange.INVALID_SEED.message)
        }
    }

    /**
     * Prints everything needed to audit past rounds and offers to rotate the server seed.
     *
     * Rotating is what turns a commitment into a proof: the retired plaintext seed is printed, and anyone can
     * hash it themselves and check it against the commitment they were shown before they bet.
     */
    suspend fun showFairness(
        task: QueueTask,
        player: Player,
    ) {
        val view = Casino.fairness(player)
        player.message("<col=ffff00>Provably fair</col>")
        player.message("Server seed hash: ${view.serverSeedHash}")
        player.message("Client seed: ${view.clientSeed}")
        player.message("Rounds played on this seed: ${view.nextNonce}")
        view.previous?.let {
            player.message("Previous server seed: ${it.serverSeed} (${it.rounds} rounds)")
        }
        if (Casino.hasLiveRound(player)) {
            player.message("Finish your ${Casino.liveRoundName(player)} game before changing seeds.")
            return
        }
        val choice = task.options("Rotate my server seed (reveals the old one).", "Close.")
        if (choice != 1) {
            return
        }
        val revealed = Casino.rotateSeed(player)
        if (revealed == null) {
            player.message(Casino.SeedChange.ROUND_LIVE.message)
            return
        }
        player.message("Revealed server seed: ${revealed.serverSeed}")
        player.message("It hashes to ${revealed.hash} - the commitment you were shown.")
        player.message("New commitment: ${CasinoSeeds.serverSeedHash(player)}")
    }
}
