package gg.rsmod.game.service.recovery

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import de.mkammerer.argon2.Argon2Factory
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import mu.KLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * OSRS-style account recovery (owner 2026-09-19: "forgot password option should work", "yes osrs style"): an account links a
 * verified e-mail address in game (`setemail`, `confirmemail`); a lost password is reset on the recovery web page with a
 * one-time code sent to that address. Codes are random, stored only as Argon2 hashes, expire after [CODE_LIFETIME_MS], allow
 * [MAX_ATTEMPTS] tries and are rate limited per account. Passwords and codes are never logged.
 */
class AccountRecovery(
    private val mailer: ((to: String, subject: String, body: String) -> Unit)?,
    private val savesPath: Path,
    val publicUrl: String,
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

    private fun rateLimited(key: String): Boolean {
        val now = System.currentTimeMillis()
        val list = sentAt.getOrPut(key) { mutableListOf() }
        synchronized(list) {
            list.removeIf { now - it > 3_600_000L }
            if (list.size >= MAX_CODES_PER_HOUR) return true
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
        if (rateLimited("link:${player.username.lowercase()}")) return "Too many codes requested - try again in an hour."
        val code = newCode()
        emailCodes[player.username.lowercase()] = PendingCode(hash(code), System.currentTimeMillis() + CODE_LIFETIME_MS, email = address)
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
        val key = player.username.lowercase()
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

    /** Web page step 1. Always answers the same way, so the page cannot be used to find out which accounts exist. */
    fun requestReset(username: String): String {
        val generic = "If that account has a confirmed recovery e-mail, a reset code has been sent to it."
        val mail = mailer ?: return NOT_CONFIGURED
        val file = runCatching { saveFile(username) }.getOrNull() ?: return generic
        val email = runCatching { emailOf(readSave(file)) }.getOrNull() ?: return generic
        if (rateLimited("reset:${username.lowercase()}")) return generic
        val code = newCode()
        resetCodes[username.trim().lowercase()] = PendingCode(hash(code), System.currentTimeMillis() + CODE_LIFETIME_MS)
        runCatching { mail(email, "78 - password reset code", "Your 78 password reset code is: $code\n\nEnter it on $publicUrl within 15 minutes.\nIf you did not ask for this, ignore this e-mail.") }
            .onFailure { logger.warn { "Reset mail for $username failed: ${it.javaClass.simpleName}" } }
        return generic
    }

    /** Web page step 2: [code] + new password. Refused while the account is logged in. */
    fun resetPassword(
        isOnline: (String) -> Boolean,
        username: String,
        code: String,
        newPassword: String,
    ): String {
        val key = username.trim().lowercase()
        val pending = resetCodes[key] ?: return "Request a reset code first."
        if (System.currentTimeMillis() > pending.expires || pending.attempts >= MAX_ATTEMPTS) {
            resetCodes.remove(key)
            return "That code has expired. Request a new one."
        }
        pending.attempts++
        if (!argon2.verify(pending.hash, code.trim().uppercase().toCharArray())) return "That code is not correct."
        if (newPassword.length !in 5..20 || !newPassword.all { it.isLetterOrDigit() }) return "Passwords are 5 to 20 letters and numbers."
        if (newPassword.equals(username.trim(), ignoreCase = true)) return "Your password can't be your username."
        if (isOnline(username.trim())) return "Log out of the game first, then reset your password."
        val file = saveFile(username) ?: return "That code is not correct."
        val save = readSave(file)
        save.addProperty("passwordHash", Argon2Factory.create().hash(2, 65536, 1, newPassword.toCharArray()))
        val temp = file.resolveSibling(file.fileName.toString() + ".reset.tmp")
        Files.writeString(temp, GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(save))
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        resetCodes.remove(key)
        logger.info { "Password reset through the recovery page for account ${username.trim()}." }
        return "Your password has been changed. You can log in now."
    }

    companion object : KLogging() {
        const val RECOVERY_EMAIL_KEY = "recovery_email"
        val RECOVERY_EMAIL = AttributeKey<String>(persistenceKey = RECOVERY_EMAIL_KEY)
        const val CODE_LIFETIME_MS = 15 * 60_000L
        const val MAX_ATTEMPTS = 5
        const val MAX_CODES_PER_HOUR = 3
        const val NOT_CONFIGURED = "Password recovery by e-mail is not switched on on this server yet."
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val EMAIL = Regex("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,24}$")

        fun mask(email: String): String {
            val at = email.indexOf('@')
            return if (at <= 1) email else email.first() + "***" + email.substring(at)
        }

        /** Set by [AccountRecoveryService] at boot; null until the service runs. */
        @Volatile
        var instance: AccountRecovery? = null
    }
}
