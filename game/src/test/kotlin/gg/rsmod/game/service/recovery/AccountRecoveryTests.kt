package gg.rsmod.game.service.recovery

import de.mkammerer.argon2.Argon2Factory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountRecoveryTests {
    private val world: (String) -> Boolean = { false }

    private fun setup(email: String?): Triple<AccountRecovery, java.nio.file.Path, MutableList<Pair<String, String>>> {
        val dir = Files.createTempDirectory("saves")
        val attrs = if (email != null) """{"recovery_email":"$email"}""" else "{}"
        Files.writeString(dir.resolve("anudd"), """{"username":"anudd","passwordHash":"old","attributes":$attrs}""")
        val sent = mutableListOf<Pair<String, String>>()
        return Triple(AccountRecovery({ to, _, body -> sent += to to body }, dir, "http://x/recover"), dir, sent)
    }

    private fun code(body: String) = Regex("code is: ([A-Z0-9]{8})").find(body)!!.groupValues[1]

    @Test
    fun `a reset code mailed to the confirmed address changes the password hash, and only once`() {
        val (recovery, dir, sent) = setup("owner@example.com")
        recovery.requestReset("Anudd")
        assertEquals("owner@example.com", sent.single().first)
        val code = code(sent.single().second)
        assertEquals("That code is not correct.", recovery.resetPassword(world, "anudd", "WRONGCOD", "newpass1"))
        assertEquals("Your password has been changed. You can log in now.", recovery.resetPassword(world, "anudd", code, "newpass1"))
        @Suppress("DEPRECATION") val hash = com.google.gson.JsonParser().parse(Files.readString(dir.resolve("anudd"))).asJsonObject["passwordHash"].asString
        assertTrue(Argon2Factory.create().verify(hash, "newpass1".toCharArray()))
        assertEquals("Request a reset code first.", recovery.resetPassword(world, "anudd", code, "another1"))
    }

    @Test
    fun `unknown accounts and accounts without an e-mail get the same answer and no mail`() {
        val (recovery, _, sent) = setup(null)
        val a = recovery.requestReset("anudd")
        val b = recovery.requestReset("nobody")
        assertEquals(a, b)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `without a mail service everything says recovery is switched off`() {
        val recovery = AccountRecovery(null, Files.createTempDirectory("saves"), "http://x/recover")
        assertEquals(AccountRecovery.NOT_CONFIGURED, recovery.requestReset("anudd"))
    }

    @Test
    fun `weak passwords are refused`() {
        val (recovery, _, sent) = setup("owner@example.com")
        recovery.requestReset("anudd")
        assertEquals("Passwords are 5 to 20 letters and numbers.", recovery.resetPassword(world, "anudd", code(sent.single().second), "abc"))
    }

    /** Audit S-08: spacing and case variations are one account, and an open code is never replaced. */
    @Test
    fun `ten requests with name variations send one mail and keep the first code valid`() {
        val (recovery, _, sent) = setup("owner@example.com")
        listOf("anudd", "Anudd", "anudd ", " anudd", "ANUDD", "anudd  ", "AnUdD", "anudd\t", " Anudd ", "anudd").forEach {
            recovery.requestReset(it)
        }
        assertTrue(sent.size <= AccountRecovery.MAX_CODES_PER_HOUR)
        assertEquals(1, sent.size)
        assertEquals("Your password has been changed. You can log in now.", recovery.resetPassword(world, " ANUDD ", code(sent.single().second), "newpass1"))
    }

    @Test
    fun `requests are limited per address`() {
        val (recovery, _, sent) = setup("owner@example.com")
        repeat(AccountRecovery.MAX_REQUESTS_PER_IP_PER_HOUR) { recovery.requestReset("nobody$it", ip = "1.2.3.4") }
        // The per-IP budget is used up, so even the real account gets no mail from that address.
        recovery.requestReset("anudd", ip = "1.2.3.4")
        assertTrue(sent.isEmpty())
        recovery.requestReset("anudd", ip = "5.6.7.8")
        assertEquals(1, sent.size)
    }

    @Test
    fun `the save is not rewritten while the account is logged in or logging in`() {
        val (recovery, dir, sent) = setup("owner@example.com")
        recovery.requestReset("anudd")
        val code = code(sent.single().second)
        assertEquals(
            "Log out of the game first, then reset your password.",
            recovery.resetPassword(world, "anudd", code, "newpass1", lockAccount = { null }),
        )
        assertTrue(Files.readString(dir.resolve("anudd")).contains("\"old\""), "hash unchanged")
        val released = mutableListOf<String>()
        assertEquals(
            "Your password has been changed. You can log in now.",
            recovery.resetPassword(world, "anudd", code, "newpass1", lockAccount = { name -> AutoCloseable { released += name } }),
        )
        assertEquals(listOf("anudd"), released, "the login slot is given back after the write")
    }
}
