package gg.rsmod.game.service.login

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Audit S-06: login attempts per IP, account lock after wrong passwords, new accounts per IP. */
class LoginThrottleTests {
    private var now = 1_000_000L

    private fun throttle() = LoginThrottle(clock = { now })

    @Test
    fun `an address gets ten attempts a minute`() {
        val throttle = throttle()
        repeat(10) { assertTrue(throttle.allowAttempt("1.2.3.4")) }
        assertFalse(throttle.allowAttempt("1.2.3.4"))
        assertTrue(throttle.allowAttempt("5.6.7.8"), "other addresses are not affected")
        now += 60_000L
        assertTrue(throttle.allowAttempt("1.2.3.4"))
    }

    @Test
    fun `five wrong passwords lock the account for five minutes`() {
        val throttle = throttle()
        repeat(4) { throttle.recordFailure("Anudd") }
        assertFalse(throttle.isAccountLocked("anudd"))
        throttle.recordFailure("anudd ")
        assertTrue(throttle.isAccountLocked("ANUDD"))
        now += 5 * 60_000L - 1
        assertTrue(throttle.isAccountLocked("anudd"))
        now += 1
        assertFalse(throttle.isAccountLocked("anudd"))
    }

    @Test
    fun `a correct password forgets earlier failures`() {
        val throttle = throttle()
        repeat(4) { throttle.recordFailure("anudd") }
        throttle.recordSuccess("anudd")
        repeat(4) { throttle.recordFailure("anudd") }
        assertFalse(throttle.isAccountLocked("anudd"))
    }

    @Test
    fun `old failures fall out of the window`() {
        val throttle = throttle()
        repeat(4) { throttle.recordFailure("anudd") }
        now += 5 * 60_000L
        throttle.recordFailure("anudd")
        assertFalse(throttle.isAccountLocked("anudd"))
    }

    @Test
    fun `an address can create five accounts an hour`() {
        val throttle = throttle()
        repeat(5) { assertTrue(throttle.allowRegistration("1.2.3.4")) }
        assertFalse(throttle.allowRegistration("1.2.3.4"))
        now += 60 * 60_000L
        throttle.purge()
        assertTrue(throttle.allowRegistration("1.2.3.4"))
    }
}
