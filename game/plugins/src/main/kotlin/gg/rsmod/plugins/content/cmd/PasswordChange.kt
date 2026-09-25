package gg.rsmod.plugins.content.cmd

import de.mkammerer.argon2.Argon2Factory
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.service.GameService
import gg.rsmod.game.service.login.PasswordPolicy
import gg.rsmod.game.service.serializer.PlayerSerializerService
import mu.KLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Audit S-04: `::changepass <old password> <new password>`.
 *
 * The command used to hash (Argon2, 64 MiB) and save on the game thread up to 30 times per tick,
 * needed no old password, had no password rule and printed the new password back. Now:
 * - the old password must be given and is verified;
 * - the new one follows [PasswordPolicy] (the recovery page's rule);
 * - one attempt per account per [COOLDOWN_MS], right or wrong;
 * - verify + hash run on one background thread; only the resulting hash is applied and saved on
 *   the game thread (the save snapshots the whole player, which only the game thread may read);
 * - neither password is ever echoed or logged (ClientCheatHandler redacts the arguments).
 */
object PasswordChange : KLogging() {
    const val COOLDOWN_MS = 60_000L

    const val USAGE = "Usage: ::changepass <old password> <new password>"
    const val COOLDOWN_MESSAGE = "You can only try to change your password once a minute."
    const val SAME_PASSWORD_MESSAGE = "Your new password must be different from your old one."
    const val WRONG_PASSWORD_MESSAGE = "That is not your current password. Your password was not changed."
    const val CHANGED_MESSAGE = "Your password has been changed."
    const val FAILED_MESSAGE = "Your password could not be changed right now. Please try again later."

    /** Last attempt per account (login name), so a relog does not reset the wait. */
    private val lastAttempt = ConcurrentHashMap<String, Long>()

    /** One thread: at most one 64 MiB Argon2 job at a time, however many players ask. */
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "password-change").apply { isDaemon = true } }

    /** Why the request is refused before any hashing, or null when it may go ahead. Pure; no side effects. */
    fun precheck(
        username: String,
        oldPassword: String,
        newPassword: String,
        lastAttemptMs: Long?,
        nowMs: Long,
    ): String? {
        if (lastAttemptMs != null) {
            val elapsed = nowMs - lastAttemptMs
            if (elapsed >= 0 && elapsed < COOLDOWN_MS) {
                return COOLDOWN_MESSAGE
            }
        }
        PasswordPolicy.problem(username, newPassword)?.let { return it }
        if (oldPassword == newPassword) {
            return SAME_PASSWORD_MESSAGE
        }
        return null
    }

    /**
     * The new password's hash, or null when [oldPassword] does not match [currentHash]. Slow (two
     * Argon2 operations): never call this on the game thread.
     */
    fun rehash(
        currentHash: String,
        oldPassword: String,
        newPassword: String,
    ): String? {
        val argon2 = Argon2Factory.create()
        if (!argon2.verify(currentHash, oldPassword.toCharArray())) {
            return null
        }
        return argon2.hash(2, 65536, 1, newPassword.toCharArray())
    }

    /**
     * Starts a password change for [client]. [reply] is always called on the game thread.
     */
    fun request(
        client: Client,
        oldPassword: String,
        newPassword: String,
        reply: (String) -> Unit,
    ) {
        val account = client.loginUsername.lowercase()
        val now = System.currentTimeMillis()
        precheck(account, oldPassword, newPassword, lastAttempt[account], now)?.let {
            reply(it)
            return
        }
        lastAttempt[account] = now

        val world = client.world
        val gameService = world.getService(GameService::class.java)
        if (gameService == null) {
            reply(FAILED_MESSAGE)
            return
        }
        val hashAtRequest = client.passwordHash
        executor.execute {
            val newHash = runCatching { rehash(hashAtRequest, oldPassword, newPassword) }
            gameService.submitGameThreadJob {
                // The player may have logged out (their save is already written) or changed the
                // password another way meanwhile: never apply a hash to a stale session.
                val stillOnline = client.isOnline && world.players[client.index] === client
                if (!stillOnline || client.passwordHash != hashAtRequest) {
                    return@submitGameThreadJob
                }
                val hash = newHash.getOrElse { e ->
                    logger.error(e) { "Password change failed for ${client.loginUsername}." }
                    reply(FAILED_MESSAGE)
                    return@submitGameThreadJob
                }
                if (hash == null) {
                    reply(WRONG_PASSWORD_MESSAGE)
                    return@submitGameThreadJob
                }
                client.passwordHash = hash
                world.getService(PlayerSerializerService::class.java, searchSubclasses = true)?.saveClientData(client)
                logger.info { "Password changed in game for account ${client.loginUsername}." }
                reply(CHANGED_MESSAGE)
            }
        }
    }
}
