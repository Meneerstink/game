package gg.rsmod.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Audit S-09: the command port only runs lines that start with the configured token. */
class CommandServerAuthTests {
    private val token = "s3cret-token"

    @Test
    fun `a command without the token is refused`() {
        assertNull(CommandServerAuth.authorize("shutdown", token))
        assertNull(CommandServerAuth.authorize("teleport anudd 3200 3900 0", token))
        assertNull(CommandServerAuth.authorize("wrong-token shutdown", token))
        assertNull(CommandServerAuth.authorize("s3cret-tokenX shutdown", token))
    }

    @Test
    fun `with no token configured everything is refused`() {
        assertNull(CommandServerAuth.authorize("shutdown", ""))
        assertNull(CommandServerAuth.authorize(" shutdown", "  "))
    }

    @Test
    fun `the token is stripped from an accepted command`() {
        assertEquals("shutdown", CommandServerAuth.authorize("s3cret-token shutdown", token))
        assertEquals("teleport anudd 3200 3900 0", CommandServerAuth.authorize("  s3cret-token   teleport anudd 3200 3900 0 ", token))
        assertEquals("", CommandServerAuth.authorize("s3cret-token", token))
    }

    @Test
    fun `constant-time comparison`() {
        assertTrue(CommandServerAuth.constantTimeEquals("abc", "abc"))
        assertFalse(CommandServerAuth.constantTimeEquals("abc", "abd"))
        assertFalse(CommandServerAuth.constantTimeEquals("abc", "abcd"))
    }
}
