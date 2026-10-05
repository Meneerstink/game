package gg.rsmod.plugins.content.inter.bank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bank PIN Settings (owner 2026-10-04): Void's rules on the 667 interface 14 (clientscript 4146 reads varc 98: & 3 = no PIN /
 * PIN / coming soon, bit 10 = 7 days).
 */
class BankPinSettingsTests {
    @Test
    fun `easy guesses are refused like Void`() {
        assertTrue(BankPin.isEasyGuess(listOf(1, 1, 1, 1)))
        assertTrue(BankPin.isEasyGuess(listOf(1, 2, 3, 4)))
        assertTrue(BankPin.isEasyGuess(listOf(9, 8, 7, 6)))
        assertFalse(BankPin.isEasyGuess(listOf(1, 3, 3, 7)))
    }

    @Test
    fun `lockout escalates like Void`() {
        assertEquals(listOf(10, 10, 15, 1000), listOf(1, 2, 3, 4).map { BankPin.lockoutSeconds(it) })
    }

    @Test
    fun `sounds are the donor's and the settings screen is interface 14`() {
        assertEquals(listOf(1040, 1042, 2274, 2277), listOf(BankPin.SOUND_PENDING, BankPin.SOUND_CANCEL, BankPin.SOUND_SUCCESS, BankPin.SOUND_WRONG))
        assertEquals(14, BankPin.SETTINGS_INTERFACE_ID)
    }
}
