package gg.rsmod.game.service.recovery

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import de.mkammerer.argon2.Argon2Factory
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.service.login.AccountSessionRegistry
import gg.rsmod.game.service.login.PasswordPolicy
import mu.KLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * OSRS-style account recovery (owner 2026-09-19: "forgot password option should work", "yes osrs style"): an account links a
 * verified e-mail address in game (`setemail`, `confirmemail`); a lost password is reset on the recovery web page with a
 * one-time code sent to that address. Codes are random, stored only as Argon2 hashes, expire after [CODE_LIFETIME_MS], allow
 * [MAX_ATTEMPTS] tries and are rate limited per account. Passwords and codes are never logged.
 *
 * Audit S-08: account names are normalised ([normalize]) for every key, so "name", "Name " and "name_" share one rate limit;
 * requests and reset attempts are also limited per IP; a code that is still valid is never replaced by a new request (a
 * stranger could otherwise keep invalidating the owner's code); reset mails go out on [mailExecutor] instead of the HTTP
 * thread; and the save is rewritten only while the account's login slot is held ([resetPassword]'s `lockAccount`).
 */
class AccountRecovery(
    private val mailer: ((to: String, subject: String, body: String) -> Unit)?,
    private val savesPath: Path,
    val publicUrl: String,
    /** Where reset mails are sent from. The default runs them on the calling thread (tests); the service passes a background thread. */
    private val mailExecutor: Executor = Executor { it.run() },
) {
    private data class PendingCode(val hash: String, val expires: Long, var attempts: Int = 0, val email: String? = null)

    private val resetCodes = ConcurrentHashMap<String, PendingCode>()
    private val emailCodes = ConcurrentHashMap<String, PendingCode>()
    private val sentAt = ConcurrentHashMap<String, MutableList<Long>>()
    private val random = SecureRandom()
    private val argon2 = Argon2Factory.create()

    val enabled: Boolean get() = mailer != null

    private fun newCode(): String = (0 until 8).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    private fun hash(code: String): String = argon2.hash(2, 16384, 1, code.toCharArray())

    private fun rateLimited(
        key: String,
        maxPerHour: Int = MAX_CODES_PER_HOUR,
    ): Boolean {
        val now = System.currentTimeMillis()
        val list = sentAt.computeIfAbsent(key) { mutableListOf() }
        synchronized(list) {
            list.removeIf { now - it > 3_600_000L }
            if (list.size >= maxPerHour) return true
            list += now
        }
        return false
    }

    /** In game: starts linking [email] to [player]; returns the message to show. */
    fun requestEmailLink(
        player: Player,
        email: String,
    ): String {
        val mail = mailer ?: return NOT_CONFIGURED
        val address = email.trim()
        if (!EMAIL.matches(address)) return "That doesn't look like a valid e-mail address."
        if (rateLimited("link:${normalize(player.username)}")) return "Too many codes requested - try again in an hour."
        val code = newCode()
        emailCodes[normalize(player.username)] = PendingCode(hash(code), System.currentTimeMillis() + CODE_LIFETIME_MS, email = address)
        return try {
            mail(address, "78 - confirm your recovery e-mail", "Your 78 confirmation code is: $code\n\nType in game: confirmemail $code\nThe code expires in 15 minutes.")
            "A confirmation code was sent to $address. Type: confirmemail <code>"
        } catch (e: Exception) {
            logger.warn { "Recovery mail to ${player.username} failed: ${e.javaClass.simpleName}" }
            "The e-mail could not be sent right now. Please try again later."
        }
    }

    /** In game: confirms the pending address with [code]; on success the address is stored on the account. */
    fun confirmEmailLink(
        player: Player,
        code: String,
    ): String {
        val key = normalize(player.username)
        val pending = emailCodes[key] ?: return "You have no e-mail confirmation in progress. Type: setemail <address>"
        if (System.currentTimeMillis() > pending.expires || pending.attempts >= MAX_ATTEMPTS) {
            emailCodes.remove(key)
            return "That code has expired. Type setemail <address> again."
        }
        pending.attempts++
        if (!argon2.verify(pending.hash, code.trim().uppercase().toCharArray())) return "That code is not correct."
        emailCodes.remove(key)
        player.attr[RECOVERY_EMAIL] = pending.email!!
        return "Your recovery e-mail is now ${mask(pending.email)}. A lost password can be reset at $publicUrl"
    }

    private fun saveFile(username: String): Path? {
        val name = username.trim()
        if (name.isEmpty() || !name.matches(Regex("[A-Za-z0-9_ -]{1,12}"))) return null
        val forms = setOf(name.lowercase(), name.lowercase().replace(' ', '_'), name.lowercase().replace('_', ' '))
        return Files.list(savesPath).use { files -> files.filter { it.fileName.toString().lowercase() in forms }.findFirst().orElse(null) }
    }

    private fun readSave(file: Path): JsonObject = @Suppress("DEPRECATION") JsonParser().parse(Files.readString(file)).asJsonObject

    private fun emailOf(save: JsonObject): String? = save.getAsJsonObject("attributes")?.get(RECOVERY_EMAIL_KEY)?.asString

    /**
     * Web page step 1. Always answers the same way, so the page cannot be used to find out which accounts exist.
     *
     * @param ip the requester's address, for the per-IP limit (null: not limited per IP).
     */
    fun requestReset(
        username: String,
        ip: String? = null,
    ): String {
        val generic = "If that account has a confirmed recovery e-mail, a reset code has been sent to it."
        val mail = mailer ?: return NOT_CONFIGURED
        if (ip != null && rateLimited("request-ip:$ip", MAX_REQUESTS_PER_IP_PER_HOUR)) return generic
        val key = normalize(username)
        val open = resetCodes[key]
        if (open != null && System.currentTimeMillis() <= open.expires && open.attempts < MAX_ATTEMPTS) {
            // A code that can still be used stays valid: a new request must not silently invalidate it.
            return generic
        }
        val file = runCatching { saveFile(username) }.getOrNull() ?: return generic
        val email = runCatching { emailOf(readSave(file)) }.getOrNull() ?: return generic
        if (rateLimited("reset:$key")) return generic
        val code = newCode()
        resetCodes[key] = PendingCode(hash(code), System.currentTimeMillis() + CODE_LIFETIME_MS)
        val account = username.trim()
        mailExecutor.execute {
            runCatching { mail(email, "78 - password reset code", "Your 78 password reset code is: $code\n\nEnter it on $publicUrl within 15 minutes.\nIf you did not ask for this, ignore this e-mail.") }
                .onFailure { logger.warn { "Reset mail for $account failed: ${it.javaClass.simpleName}" } }
        }
        return generic
    }

    /**
     * Web page step 2: [code] + new password. Refused while the account is logged in.
     *
     * @param ip the requester's address, for the per-IP limit on attempts (null: not limited per IP).
     * @param lockAccount Audit S-08: claims the account's login slot for the duration of the rewrite and returns the handle
     * that releases it, or null when the account is online or logging in/out. Without it a login could read the old save
     * between the online check and the write, and its later logout save would put the old password back.
     */
    fun resetPassword(
        isOnline: (String) -> Boolean,
        username: String,
        code: String,
        newPassword: String,
        ip: String? = null,
        lockAccount: (String) -> AutoCloseable? = { AutoCloseable {} },
    ): String {
        if (ip != null && rateLimited("reset-ip:$ip", MAX_RESET_ATTEMPTS_PER_IP_PER_HOUR)) return "Too many attempts - try again later."
        val key = normalize(username)
        val pending = resetCodes[key] ?: return "Request a reset code first."
        synchronized(pending) {
            if (System.currentTimeMillis() > pending.expires || pending.attempts >= MAX_ATTEMPTS) {
                resetCodes.remove(key, pending)
                return "That code has expired. Request a new one."
            }
            pending.attempts++
        }
        if (!argon2.verify(pending.hash, code.trim().uppercase().toCharArray())) return "That code is not correct."
        PasswordPolicy.problem(username, newPassword)?.let { return it }
        val lock = lockAccount(key) ?: return "Log out of the game first, then reset your password."
        lock.use {
            if (isOnline(username.trim())) return "Log out of the game first, then reset your password."
            val file = saveFile(username) ?: return "That code is not correct."
            // The code is spent before the write, so two simultaneous correct submissions cannot both write.
            if (!resetCodes.remove(key, pending)) return "Request a reset code first."
            val save = readSave(file)
            save.addProperty("passwordHash", Argon2Factory.create().hash(2, 65536, 1, newPassword.toCharArray()))
            val temp = Files.createTempFile(file.toAbsolutePath().parent, file.fileName.toString() + ".", ".reset.tmp")
            try {
                Files.writeString(temp, GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(save))
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                Files.deleteIfExists(temp)
            }
        }
        logger.info { "Password reset through the recovery page for account ${username.trim()}." }
        return "Your password has been changed. You can log in now."
    }

    companion object : KLogging() {
        const val RECOVERY_EMAIL_KEY = "recovery_email"
        val RECOVERY_EMAIL = AttributeKey<String>(persistenceKey = RECOVERY_EMAIL_KEY)
        const val CODE_LIFETIME_MS = 15 * 60_000L
        const val MAX_ATTEMPTS = 5
        const val MAX_CODES_PER_HOUR = 3
        const val MAX_REQUESTS_PER_IP_PER_HOUR = 10
        const val MAX_RESET_ATTEMPTS_PER_IP_PER_HOUR = 20
        const val NOT_CONFIGURED = "Password recovery by e-mail is not switched on on this server yet."
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val EMAIL = Regex("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,24}$")

        /** Audit S-08: the one key form of an account name (trimmed, lower case, underscores as spaces). */
        fun normalize(username: String): String = AccountSessionRegistry.normalize(username)

        fun mask(email: String): String {
            val at = email.indexOf('@')
            return if (at <= 1) email else email.first() + "***" + email.substring(at)
        }

        /** Set by [AccountRecoveryService] at boot; null until the service runs. */
        @Volatile
        var instance: AccountRecovery? = null
    }
}
