package gg.rsmod.game.service.login

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Audit S-03: an account is either free or held by exactly one session, including during logout. */
class AccountSessionRegistryTests {
    @Test
    fun `a claimed account cannot be claimed again until it is released`() {
        val sessions = AccountSessionRegistry()
        assertTrue(sessions.tryClaim("Anudd"))
        assertFalse(sessions.tryClaim("anudd"), "same account, other case")
        assertFalse(sessions.tryClaim(" anudd "), "same account, padded")
        sessions.release("ANUDD")
        assertTrue(sessions.tryClaim("anudd"))
    }

    @Test
    fun `spaces and underscores name the same account`() {
        val sessions = AccountSessionRegistry()
        assertTrue(sessions.tryClaim("big bob"))
        assertFalse(sessions.tryClaim("Big_Bob"))
        assertTrue(sessions.isActive("BIG BOB"))
    }

    @Test
    fun `a login during the logout save is refused, and allowed once the save is written`() {
        val sessions = AccountSessionRegistry()
        val saves = mutableListOf<String>()
        // First session is online.
        assertTrue(sessions.tryClaim("anudd"))
        // Logout starts: the save is being written, the claim is still held.
        saves += "logout save"
        assertFalse(sessions.tryClaim("anudd"), "the second client must not read the save before the logout save is on disk")
        // Client.handleLogout releases after the save.
        sessions.release("anudd")
        assertTrue(sessions.tryClaim("anudd"))
        assertEquals(listOf("logout save"), saves)
    }

    @Test
    fun `exactly one of many simultaneous logins wins`() {
        val sessions = AccountSessionRegistry()
        val threads = 16
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val winners = AtomicInteger()
        repeat(threads) {
            pool.execute {
                start.await()
                if (sessions.tryClaim("anudd")) {
                    winners.incrementAndGet()
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        assertEquals(1, winners.get())
        assertEquals(1, sessions.size())
    }
}
