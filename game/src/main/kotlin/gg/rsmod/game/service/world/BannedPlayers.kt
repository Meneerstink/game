package gg.rsmod.game.service.world

import gg.rsmod.game.model.priv.Punishments
import java.io.File

/**
 * Session-persistent username ban list, checked by [SimpleWorldVerificationService] at login
 * (`ACCOUNT_BANNED` - the real client login-block message, not a fake/local-only block). Plain
 * newline-delimited text file, same convention as [gg.rsmod.game.model.npc.NpcCensus]'s
 * `./npc_inventory.csv` (relative to the server process working directory) - no database needed
 * for an admin-tool ban list this size.
 *
 * Owner 2026-10-10 (staff tools, Void `Ban`: "banned_until" in epoch seconds): a line is `name` (permanent, the old format) or
 * `name|until|reason`; an expired ban no longer blocks the login and is dropped on the next write.
 */
object BannedPlayers {
    class Ban(val until: Int, val reason: String)

    // ROOT_CAUSES #109: a test server bans in its own scratch copy, never in the live server's list.
    private val file: File = gg.rsmod.util.io.SharedDataFiles.of("banned_players.txt")
    private val banned: MutableMap<String, Ban> = load()

    private fun key(username: String) = username.trim().lowercase()

    private fun load(): MutableMap<String, Ban> {
        val out = linkedMapOf<String, Ban>()
        if (!file.exists()) return out
        file.readLines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val parts = line.split('|', limit = 3)
            val until = parts.getOrNull(1)?.toIntOrNull() ?: Punishments.PERMANENT
            out[key(parts[0])] = Ban(until, parts.getOrNull(2) ?: "")
        }
        return out
    }

    @Synchronized
    private fun persist() {
        val now = Punishments.epochSeconds()
        banned.entries.removeIf { it.value.until <= now }
        val lines =
            banned.toSortedMap().map { (name, ban) ->
                if (ban.until == Punishments.PERMANENT && ban.reason.isEmpty()) name else "$name|${ban.until}|${ban.reason.replace('\n', ' ')}"
            }
        gg.rsmod.util.io.AtomicFiles.writeText(file, lines.joinToString("\n"))
    }

    fun isBanned(username: String): Boolean = (banned[key(username)]?.until ?: 0) > Punishments.epochSeconds()

    fun banOf(username: String): Ban? = banned[key(username)]?.takeIf { it.until > Punishments.epochSeconds() }

    /** @return true if this ban is new (false if the player was already banned). Permanent, as before. */
    fun ban(username: String): Boolean = ban(username, Punishments.PERMANENT, "")

    /** Bans [username] until [until] (epoch seconds, [Punishments.PERMANENT] = forever); replaces an earlier ban. */
    @Synchronized
    fun ban(
        username: String,
        until: Int,
        reason: String,
    ): Boolean {
        val added = !isBanned(username)
        banned[key(username)] = Ban(until, reason.trim())
        persist()
        return added
    }

    /** @return true if the player was banned and is now unbanned. */
    @Synchronized
    fun unban(username: String): Boolean {
        val removed = banned.remove(key(username)) != null
        if (removed) persist()
        return removed
    }
}
