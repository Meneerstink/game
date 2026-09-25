package gg.rsmod.game

import java.security.MessageDigest

/**
 * Audit S-09: authentication for the local command port (`commandServer.tcpPort`, 50017).
 *
 * Any process on the host could send `shutdown`, `kick` or `teleport` without credentials. Every
 * line must now start with the shared secret from game.yml:
 *
 * ```
 * commandServer:
 *     tcpPort: 50017
 *     token: "<long random string>"
 * ```
 *
 * and looks like `<token> <command> [args]`, ending with a newline. With no token configured every
 * command is refused.
 */
object CommandServerAuth {
    /**
     * The command part of [line] when it starts with [token], otherwise null. The token is compared
     * in constant time; a blank [token] never authorizes anything.
     */
    fun authorize(
        line: String,
        token: String,
    ): String? {
        if (token.isBlank()) {
            return null
        }
        val trimmed = line.trim()
        val space = trimmed.indexOf(' ')
        val given = if (space < 0) trimmed else trimmed.substring(0, space)
        val command = if (space < 0) "" else trimmed.substring(space + 1).trim()
        return if (constantTimeEquals(given, token)) command else null
    }

    /** Compares SHA-256 digests with [MessageDigest.isEqual], so neither the content nor the length of [expected] leaks through timing. */
    fun constantTimeEquals(
        given: String,
        expected: String,
    ): Boolean = MessageDigest.isEqual(sha256(given), sha256(expected))

    private fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
}
