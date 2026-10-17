package gg.rsmod.game.tools.importer

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * N10: what the rev-667 client does with a map-square loc file `l<x>_<z>` (index 5) and the XTEA key the server sends in
 * the rebuild packet. Mirrors the client step by step:
 *
 *  - `js5.unpackFile` (2011scape-client runescape/.../js5/js5.java:417-425): an all-zero key means "not encrypted", any
 *    other key decrypts with `Packet.tinydec(key, length)` (Packet.java:605-624: from offset 5, `(length - 5) / 8` blocks).
 *    The length is the container without the 2-byte version trailer the cache stores behind every group.
 *  - `js5.decodeContainer` (js5.java:134-163): ctype + clen are plain; ctype 0 copies clen bytes; otherwise ulen is read
 *    from the (decrypted) bytes 5..8, a negative ulen returns `new byte[1]` (an empty loc file), anything else allocates
 *    `new byte[ulen]` (line 151 - the N10 OutOfMemoryError) and inflates into it (a failure is the client's "T3" error).
 *
 * The server sends 0,0,0,0 for every square without a key in data/xteas/xteas.json (XteaKeyService.get), so a loc file
 * that is still encrypted is read as ciphertext: a garbage ulen crashes the client on every map that contains the square.
 */
object ClientMapLocDecode {
    enum class Outcome(val crashesClient: Boolean) {
        /** ctype 0, read as stored. */
        PLAIN(false),

        /** Compressed, inflates to exactly its ulen. */
        DECODES(false),

        /** Garbage negative ulen: the client returns byte[1] and draws no locs there, but does not crash. */
        EMPTY_NEGATIVE_SIZE(false),

        /** Garbage ulen this large cannot be allocated with the client's -Xmx1024m: OutOfMemoryError (N10). */
        OUT_OF_MEMORY(true),

        /** clen invalid, or the inflate fails / gives another size than ulen: the client's "T3" JagException. */
        T3(true),

        /** A compression type this check does not emulate (no rev-667 loc file uses bzip2 today); counted as a failure. */
        UNSUPPORTED(true),
    }

    /** Client heap is -Xmx1024m (client/build.gradle.kts, Launcher78); a single array of 256 MB or more is treated as fatal. */
    const val OUT_OF_MEMORY_SIZE = 256 shl 20

    private const val GOLDEN_RATIO = -0x61c88647

    /**
     * @param stored the group exactly as the cache stores it (container + 2-byte version trailer), e.g. displee
     *   `index(5).readArchiveSector(group).data`.
     * @param key the key the server sends for the square (all zero = none).
     */
    fun classify(
        stored: ByteArray,
        key: IntArray,
    ): Outcome {
        val length = stored.size - 2
        if (length < 5) return Outcome.T3
        val data = stored.copyOf(length)
        if (key.any { it != 0 }) tinydec(data, key, length)
        val ctype = data[0].toInt() and 0xFF
        val clen = int(data, 1)
        if (clen < 0) return Outcome.T3
        if (ctype == 0) return if (clen <= length - 5) Outcome.PLAIN else Outcome.T3
        if (length < 9) return Outcome.T3
        val ulen = int(data, 5)
        if (ulen < 0) return Outcome.EMPTY_NEGATIVE_SIZE
        if (ulen >= OUT_OF_MEMORY_SIZE) return Outcome.OUT_OF_MEMORY
        if (ctype != 2) return Outcome.UNSUPPORTED
        if (clen > length - 9) return Outcome.T3
        return try {
            val inflated = GZIPInputStream(ByteArrayInputStream(data, 9, clen)).use { it.readBytes() }
            if (inflated.size == ulen) Outcome.DECODES else Outcome.T3
        } catch (e: Exception) {
            Outcome.T3
        }
    }

    /** Packet.tinydec(key, end) of the rev-667 client: XTEA decipher from offset 5, whole 8-byte blocks only. */
    fun tinydec(
        data: ByteArray,
        key: IntArray,
        end: Int,
    ) {
        var pos = 5
        repeat((end - 5) / 8) {
            var v0 = int(data, pos)
            var v1 = int(data, pos + 4)
            var sum = GOLDEN_RATIO * 32
            repeat(32) {
                v1 -= ((v0 shl 4) xor (v0 ushr 5)) + v0 xor (key[(sum ushr 11) and 3] + sum)
                sum -= GOLDEN_RATIO
                v0 -= ((v1 shl 4) xor (v1 ushr 5)) + v1 xor (sum + key[sum and 3])
            }
            put(data, pos, v0)
            put(data, pos + 4, v1)
            pos += 8
        }
    }

    private fun int(
        b: ByteArray,
        p: Int,
    ): Int = ((b[p].toInt() and 0xFF) shl 24) or ((b[p + 1].toInt() and 0xFF) shl 16) or ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)

    private fun put(
        b: ByteArray,
        p: Int,
        v: Int,
    ) {
        b[p] = (v ushr 24).toByte()
        b[p + 1] = (v ushr 16).toByte()
        b[p + 2] = (v ushr 8).toByte()
        b[p + 3] = v.toByte()
    }
}
