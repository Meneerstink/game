package gg.rsmod.game.service.vote

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import mu.KLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Owner 2026-10-10 voting system: every vote a toplist reports ([VoteService]) for an existing account is worth one reward, at most
 * once per site per [cooldownMillis] (owner: 12 hours) per account; the game hands the reward out as soon as the player is online
 * ([takePending]). The ledger is written to [file] after every change, so a vote cast while the server restarts or the player is
 * offline is never lost. Thread-safe: the web threads record, the game thread takes.
 */
class VoteLedger(
    private val file: Path?,
    val cooldownMillis: Long = 12 * 60 * 60 * 1000L,
) {
    enum class Result { ACCEPTED, COOLDOWN, BAD_NAME }

    private class State(
        /** "site|account" -> when that account's last rewarded vote on that site came in. */
        val lastVote: MutableMap<String, Long> = HashMap(),
        /** account -> rewards not handed out yet. */
        val pending: MutableMap<String, Int> = HashMap(),
    )

    private val state: State = load()

    /** The account key: lower case, '_' and ' ' the same (as the save files and the login treat them). */
    fun key(username: String): String = username.trim().lowercase().replace('_', ' ')

    @Synchronized
    fun record(
        site: String,
        username: String,
        now: Long = System.currentTimeMillis(),
    ): Result {
        if (!username.trim().matches(NAME)) return Result.BAD_NAME
        val account = key(username)
        val slot = "$site|$account"
        val last = state.lastVote[slot]
        if (last != null && now - last < cooldownMillis) return Result.COOLDOWN
        state.lastVote[slot] = now
        state.pending[account] = (state.pending[account] ?: 0) + 1
        save()
        return Result.ACCEPTED
    }

    @Synchronized
    fun pending(username: String): Int = state.pending[key(username)] ?: 0

    /** Removes and returns [username]'s waiting rewards. */
    @Synchronized
    fun takePending(username: String): Int {
        val n = state.pending.remove(key(username)) ?: return 0
        save()
        return n
    }

    /** Puts [count] rewards back (the bank and inventory were full). */
    @Synchronized
    fun giveBack(
        username: String,
        count: Int,
    ) {
        if (count <= 0) return
        val account = key(username)
        state.pending[account] = (state.pending[account] ?: 0) + count
        save()
    }

    /** Milliseconds until [username] may be rewarded again on [site]; 0 = now. */
    @Synchronized
    fun waitMillis(
        site: String,
        username: String,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val last = state.lastVote["$site|${key(username)}"] ?: return 0
        return (last + cooldownMillis - now).coerceAtLeast(0)
    }

    private fun load(): State {
        val file = file ?: return State()
        if (!Files.exists(file)) return State()
        return runCatching {
            val type = object : TypeToken<Map<String, Map<String, Number>>>() {}.type
            val raw: Map<String, Map<String, Number>> = GSON.fromJson(Files.readString(file), type)
            State(
                raw["lastVote"].orEmpty().mapValuesTo(HashMap()) { it.value.toLong() },
                raw["pending"].orEmpty().mapValuesTo(HashMap()) { it.value.toInt() },
            )
        }.getOrElse { e ->
            logger.error(e) { "Vote ledger $file unreadable - starting empty (the file is kept as .broken)." }
            runCatching { Files.copy(file, file.resolveSibling("${file.fileName}.broken"), StandardCopyOption.REPLACE_EXISTING) }
            State()
        }
    }

    private fun save() {
        val file = file ?: return
        runCatching {
            Files.createDirectories(file.toAbsolutePath().parent)
            val tmp = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(tmp, GSON.toJson(mapOf("lastVote" to state.lastVote, "pending" to state.pending)))
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { e -> logger.error(e) { "Vote ledger $file could not be written." } }
    }

    companion object : KLogging() {
        private val GSON = GsonBuilder().setPrettyPrinting().create()

        /** Account names as the login allows them. */
        val NAME = Regex("[A-Za-z0-9_ -]{1,12}")

        /** Set by [VoteService] at boot; null on a server without voting. */
        @Volatile var instance: VoteLedger? = null

        /** Called on the game thread for every accepted vote with the account name (the plugin hands the reward out). */
        @Volatile var onVote: ((String) -> Unit)? = null
    }
}
