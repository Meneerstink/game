package gg.rsmod.game.service.world

import java.io.File

/**
 * Session-persistent username ban list, checked by [SimpleWorldVerificationService] at login
 * (`ACCOUNT_BANNED` - the real client login-block message, not a fake/local-only block). Plain
 * newline-delimited text file, same convention as [gg.rsmod.game.model.npc.NpcCensus]'s
 * `./npc_inventory.csv` (relative to the server process working directory) - no database needed
 * for an admin-tool ban list this size.
 */
object BannedPlayers {
    private val file = File("banned_players.txt")
    private val banned: MutableSet<String> = load()

    private fun load(): MutableSet<String> =
        if (file.exists()) {
            file.readLines().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toMutableSet()
        } else {
            mutableSetOf()
        }

    private fun persist() {
        gg.rsmod.util.io.AtomicFiles.writeText(file, banned.sorted().joinToString("\n"))
    }

    fun isBanned(username: String): Boolean = banned.contains(username.trim().lowercase())

    /** @return true if this ban is new (false if the player was already banned). */
    fun ban(username: String): Boolean {
        val added = banned.add(username.trim().lowercase())
        if (added) persist()
        return added
    }

    /** @return true if the player was banned and is now unbanned. */
    fun unban(username: String): Boolean {
        val removed = banned.remove(username.trim().lowercase())
        if (removed) persist()
        return removed
    }
}
