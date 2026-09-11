package gg.rsmod.util

/**
 * The 37-symbol name encoding RuneScape uses whenever a display name has to travel as a number.
 *
 * The alphabet is `_abcdefghijklmnopqrstuvwxyz0123456789`: index 0 is the space/underscore symbol,
 * 1-26 are the letters and 27-36 the digits. A name is the base-37 value of its symbols, most
 * significant symbol first, with trailing spaces normalised away, so `"Zezima"` and `"Zezima "`
 * encode identically.
 *
 * Needed because the friends-chat protocol identifies a channel by its 8-byte encoded name rather
 * than by a string: `UPDATE_FRIENDCHAT_CHANNEL_FULL` sends the channel as a `g8()` and the client
 * turns it back into text with its own `Base37`.
 *
 * Mirrors `com.jagex.core.stringtools.general.Base37` in the client, including its split between
 * [decode] - the raw symbols - and [decodeName] - the same name as the client renders it for
 * display. Keeping both apart matters: the raw form is what round-trips through [encode], and the
 * display form is lossy (`"mod_ash"` and `"Mod Ash"` share one encoding).
 */
object Base37 {
    private const val MAX_LENGTH = 12

    /**
     * The largest value [decode] will accept, `37^12`. The client uses the same literal bound and
     * rejects anything at or above it rather than letting a hostile value overrun its buffer.
     */
    private const val MAX_ENCODED = 6582952005840035281L

    private val SYMBOLS = "_abcdefghijklmnopqrstuvwxyz0123456789".toCharArray()

    /**
     * Encodes [name] into its base-37 value, or `0` when the name carries no symbols.
     *
     * Characters outside the alphabet, including spaces, encode as symbol 0. A leading run of them
     * therefore cannot affect the result at all - `0 * 37 + 0` is still `0` - and a trailing run is
     * divided back out, which is what makes the encoding stable for names padded to a fixed width.
     */
    fun encode(name: String): Long {
        var encoded = 0L
        for (i in 0 until minOf(name.length, MAX_LENGTH)) {
            val char = name[i]
            encoded *= 37L
            when (char) {
                in 'A'..'Z' -> encoded += (char - 'A' + 1).toLong()
                in 'a'..'z' -> encoded += (char - 'a' + 1).toLong()
                in '0'..'9' -> encoded += (char - '0' + 27).toLong()
            }
        }
        while (encoded % 37L == 0L && encoded != 0L) {
            encoded /= 37L
        }
        return encoded
    }

    /**
     * Decodes [value] back into the raw symbols it was built from, lowercase and with the space
     * symbol written as `_`.
     *
     * Values the encoding can never produce - zero, negatives, anything at or above [MAX_ENCODED],
     * and multiples of 37, which would have to end in the space symbol [encode] always strips -
     * decode to an empty name rather than throwing. This arrives over the network, so a malformed
     * value has to be inert.
     */
    fun decode(value: Long): String {
        if (value <= 0L || value >= MAX_ENCODED || value % 37L == 0L) {
            return ""
        }
        var remaining = value
        val chars = CharArray(MAX_LENGTH)
        var index = MAX_LENGTH
        while (remaining != 0L) {
            val symbol = (remaining % 37L).toInt()
            remaining /= 37L
            chars[--index] = SYMBOLS[symbol]
        }
        return String(chars, index, MAX_LENGTH - index)
    }

    /**
     * Decodes [value] into a display name: spaces instead of underscores, and a capital at the
     * start of every word, exactly as the client's `decodeName` renders it.
     */
    fun decodeName(value: Long): String {
        val decoded = decode(value)
        if (decoded.isEmpty()) {
            return ""
        }
        val chars = decoded.toCharArray()
        var startOfWord = true
        for (i in chars.indices) {
            if (chars[i] == '_') {
                chars[i] = ' '
                startOfWord = true
            } else if (startOfWord) {
                chars[i] = chars[i].uppercaseChar()
                startOfWord = false
            }
        }
        return String(chars)
    }
}
