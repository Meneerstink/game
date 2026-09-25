package gg.rsmod.plugins.content.cmd

import de.mkammerer.argon2.Argon2Factory
import gg.rsmod.game.service.login.PasswordPolicy
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Audit S-04: `::changepass <old> <new>`. */
class PasswordChangeTests {
    private val oldHash = Argon2Factory.create().hash(2, 65536, 1, "oldpass1".toCharArray())

    @Test
    fun `a wrong old password leaves the hash unchanged`() {
        assertNull(PasswordChange.rehash(oldHash, "notmine1", "newpass1"))
    }

    @Test
    fun `the right old password yields a hash of the new one`() {
        val newHash = assertNotNull(PasswordChange.rehash(oldHash, "oldpass1", "newpass1"))
        assertTrue(Argon2Factory.create().verify(newHash, "newpass1".toCharArray()))
        assertFalse(Argon2Factory.create().verify(newHash, "oldpass1".toCharArray()))
    }

    @Test
    fun `a second attempt within the cooldown is refused`() {
        val now = 10_000_000L
        assertNull(PasswordChange.precheck("anudd", "oldpass1", "newpass1", lastAttemptMs = null, nowMs = now))
        assertEquals(
            PasswordChange.COOLDOWN_MESSAGE,
            PasswordChange.precheck("anudd", "oldpass1", "newpass1", lastAttemptMs = now, nowMs = now + PasswordChange.COOLDOWN_MS - 1),
        )
        assertNull(PasswordChange.precheck("anudd", "oldpass1", "newpass1", lastAttemptMs = now, nowMs = now + PasswordChange.COOLDOWN_MS))
    }

    @Test
    fun `the new password follows the recovery rule`() {
        assertEquals(PasswordPolicy.RULE_MESSAGE, PasswordChange.precheck("anudd", "oldpass1", "abc", null, 0L))
        assertEquals(PasswordPolicy.SAME_AS_NAME_MESSAGE, PasswordChange.precheck("anudd", "oldpass1", "anudd", null, 0L))
        assertEquals(PasswordChange.SAME_PASSWORD_MESSAGE, PasswordChange.precheck("anudd", "oldpass1", "oldpass1", null, 0L))
    }

    @Test
    fun `the command never echoes a password`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/cmd/commands.plugin.kts").readText()
        val command = source.substringAfter("on_command(\"changepass\")").substringBefore("on_command(")
        assertFalse("args[0]}" in command || "args[1]}" in command || "\$password" in command)
        assertTrue("PasswordChange.request(" in command)
    }
}
