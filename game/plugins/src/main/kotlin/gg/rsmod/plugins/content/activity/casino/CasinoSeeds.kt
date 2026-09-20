package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player

/**
 * The per-player seed pair behind every casino game, and the commit/reveal bookkeeping around it.
 *
 * SOURCE: MIT `seeds/GamblingSeed`, `seeds/ServerSeed`, `seeds/ClientSeed` (`rsps-provably-fair`) and the Roat Pkz
 * "Provably Fair" page ("Commit. Lock. Reveal. Replay."): the server publishes `SHA-256(serverSeed)` up front, the
 * player may set their own client seed while the server seed is still secret, and rotating the seed reveals the old
 * plaintext so every round it produced can be replayed offline.
 *
 * Two rules make the commitment worth anything, and both are enforced here rather than by the callers:
 *
 *  - A server seed is revealed **only** when it is being retired ([rotate]). While it is live, nothing may hand the
 *    plaintext to the player - that would let them compute the next result before betting.
 *  - A `(clientSeed, serverSeed, nonce)` triple is used for exactly one round. [takeNonce] hands out each value
 *    once and never rewinds, so two rounds can never share an outcome, and changing the client seed does not reset
 *    it. The nonce is allocated when the wager is committed, not when the result is shown.
 *
 * Counters persist as strings on purpose: the JSON player serializer reads plain numbers back as doubles, which
 * silently turns a large nonce into a rounded one (the same reason `LootKeys` stores its counters as strings).
 */
object CasinoSeeds {
    val SERVER_SEED = AttributeKey<String>(persistenceKey = "casino_server_seed")
    val CLIENT_SEED = AttributeKey<String>(persistenceKey = "casino_client_seed")
    val NONCE = AttributeKey<String>(persistenceKey = "casino_nonce")

    /** The last retired server seed and how many rounds it covered, kept so old rounds stay verifiable. */
    val REVEALED_SEED = AttributeKey<String>(persistenceKey = "casino_revealed_server_seed")
    val REVEALED_NONCE = AttributeKey<String>(persistenceKey = "casino_revealed_nonce")

    /** The live server seed, minted on first use. Never shown to the player while it is live. */
    fun serverSeed(player: Player): String {
        val existing = player.attr[SERVER_SEED]
        if (existing != null && ProvablyFair.isSeedValid(existing)) {
            return existing
        }
        val fresh = ProvablyFair.randomSeed()
        player.attr[SERVER_SEED] = fresh
        player.attr[NONCE] = "0"
        return fresh
    }

    /** The public commitment: `SHA-256` of the live server seed. */
    fun serverSeedHash(player: Player): String = ProvablyFair.sha256Hex(serverSeed(player))

    /** The player's client seed, defaulted to a random one so a player who never sets it is still protected. */
    fun clientSeed(player: Player): String {
        val existing = player.attr[CLIENT_SEED]
        if (existing != null && ProvablyFair.isSeedValid(existing)) {
            return existing
        }
        val fresh = ProvablyFair.randomSeed()
        player.attr[CLIENT_SEED] = fresh
        return fresh
    }

    /**
     * Sets a player-chosen client seed. Rejects anything outside the MIT alphabet: the seed is concatenated straight
     * into the hash material and rendered in the interface, so it is validated, never escaped.
     *
     * The nonce deliberately survives a client-seed change - resetting it would re-use triples.
     */
    fun setClientSeed(
        player: Player,
        seed: String,
    ): Boolean {
        val trimmed = seed.trim()
        if (!ProvablyFair.isSeedValid(trimmed)) {
            return false
        }
        player.attr[CLIENT_SEED] = trimmed
        return true
    }

    /** The nonce the next round will use, without consuming it (for display only). */
    fun peekNonce(player: Player): Int = player.attr[NONCE]?.toIntOrNull() ?: 0

    /**
     * Consumes and returns the next nonce. Call this exactly once per committed wager; it is the single point that
     * guarantees no two rounds share a `(clientSeed, serverSeed, nonce)` triple.
     */
    fun takeNonce(player: Player): Int {
        serverSeed(player) // make sure a seed (and therefore a nonce baseline) exists
        val current = peekNonce(player)
        player.attr[NONCE] = (current + 1).toString()
        return current
    }

    /**
     * Retires the live server seed and mints a new one.
     *
     * This is the only path that reveals a plaintext server seed, and it does so *after* the seed can no longer
     * decide anything. Returns the revealed seed and the number of rounds it covered.
     */
    fun rotate(player: Player): Revealed {
        val retired = serverSeed(player)
        val rounds = peekNonce(player)
        player.attr[REVEALED_SEED] = retired
        player.attr[REVEALED_NONCE] = rounds.toString()
        player.attr[SERVER_SEED] = ProvablyFair.randomSeed()
        player.attr[NONCE] = "0"
        return Revealed(retired, rounds)
    }

    /** The previously retired seed, if the player has ever rotated. */
    fun lastRevealed(player: Player): Revealed? {
        val seed = player.attr[REVEALED_SEED] ?: return null
        return Revealed(seed, player.attr[REVEALED_NONCE]?.toIntOrNull() ?: 0)
    }

    data class Revealed(
        val serverSeed: String,
        val rounds: Int,
    ) {
        val hash: String
            get() = ProvablyFair.sha256Hex(serverSeed)
    }
}
