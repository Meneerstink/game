package gg.rsmod.plugins.content.activity.casino

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Provably-fair primitives shared by every casino game (owner 2026-09-20: "Provably-fair server-side RNG with server
 * seed, client seed, nonce and verification").
 *
 * SOURCE: Roat Pkz `rsps-provably-fair` (MIT, https://github.com/roatpkz/rsps-provably-fair), classes
 * `ProvablyFair`, `ProvablyFairDice`, `ProvablyFairMines`, `ProvablyFairBlackjack`, `ProvablyFairFlowerPoker` and
 * the `seeds` package, read from the repository on 2026-09-20. The commit-lock-reveal-replay protocol and the hash material
 * layouts are reproduced **byte for byte** rather than reimplemented, so a player can paste our published seeds into
 * Roat's own verifier pages (`html/dice.html`, `html/mines.html`, `html/blackjack.html`, `html/flowerpoker.html`) and
 * get our exact results. Changing the separator, the byte count or the counter order here silently breaks that
 * property - do not "tidy" the material strings.
 *
 * The protocol:
 *  1. COMMIT - the server generates a secret server seed and shows only `SHA-256(serverSeed)`.
 *  2. LOCK   - the player may set their own client seed at any time while the server seed is unrevealed, which stops
 *              the server from choosing all of the input material.
 *  3. REVEAL - rotating the server seed publishes the old plaintext seed.
 *  4. REPLAY - anyone can re-hash the material and reproduce every result the seed pair produced.
 */
object ProvablyFair {
    /** Thread-safe CSPRNG, used only to mint seeds - never to decide a game outcome. */
    private val RNG = SecureRandom()

    /** The MIT reference alphabet: 62 alphanumerics plus the hyphen. */
    private const val ALPHANUM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-"

    /** The MIT reference seed length. */
    const val SEED_LENGTH = 16

    /** A client seed the player types must stay short enough to render in the interface. */
    const val MAX_CLIENT_SEED_LENGTH = 32

    private val ALLOWED = BooleanArray(128).also { table -> ALPHANUM.forEach { table[it.code] = true } }

    /** `SHA-256(s)` as lowercase hex - the published commitment of a server seed. */
    fun sha256Hex(s: String): String {
        val bytes = digest(s)
        val sb = StringBuilder(64)
        for (b in bytes) {
            sb.append(HEX[(b.toInt() shr 4) and 0xF])
            sb.append(HEX[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()

    /** Raw `SHA-256` of the UTF-8 bytes of [material]. */
    fun digest(material: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(StandardCharsets.UTF_8))

    /** A fresh 16-character seed from the CSPRNG. */
    fun randomSeed(): String {
        val buf = CharArray(SEED_LENGTH)
        for (i in 0 until SEED_LENGTH) {
            buf[i] = ALPHANUM[RNG.nextInt(ALPHANUM.length)]
        }
        return String(buf)
    }

    /**
     * Accepts only the MIT reference alphabet. A player-supplied client seed reaches the hash material verbatim, so
     * anything outside `[a-zA-Z0-9-]` is rejected rather than escaped: it would break the verifier round-trip and is
     * an obvious injection surface for the strings we log and render.
     */
    fun isSeedValid(seed: String?): Boolean {
        if (seed.isNullOrEmpty() || seed.length > MAX_CLIENT_SEED_LENGTH) {
            return false
        }
        for (c in seed) {
            if (c.code >= ALLOWED.size || !ALLOWED[c.code]) {
                return false
            }
        }
        return true
    }

    /** First 4 bytes of [hash] as an unsigned 32-bit value (MIT `ProvablyFairDice.computeScaled`). */
    fun unsigned32(hash: ByteArray): Long =
        ((hash[0].toLong() and 0xFF) shl 24) or
            ((hash[1].toLong() and 0xFF) shl 16) or
            ((hash[2].toLong() and 0xFF) shl 8) or
            (hash[3].toLong() and 0xFF)

    /** First 8 bytes of [hash] as a raw 64-bit value, to be consumed with [Long.remainderUnsigned]. */
    fun unsigned64(hash: ByteArray): Long {
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (hash[i].toLong() and 0xFF)
        }
        return v
    }

    /** Unsigned `value % bound`, the MIT reference's `Long.remainderUnsigned`. */
    fun boundedUnsigned(
        value: Long,
        bound: Int,
    ): Int = java.lang.Long.remainderUnsigned(value, bound.toLong()).toInt()

    /** Shortened commitment for the interface, e.g. `3f0a1...9c4de` (MIT `ServerSeed.getHashedSeedVisual`). */
    fun shortHash(hash: String?): String {
        if (hash == null || hash.length <= 8) {
            return hash ?: ""
        }
        return hash.substring(0, 5) + "..." + hash.substring(hash.length - 5)
    }
}
