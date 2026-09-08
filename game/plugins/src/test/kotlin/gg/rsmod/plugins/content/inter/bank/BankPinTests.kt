package gg.rsmod.plugins.content.inter.bank

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Coverage for the part of the bank PIN that decides whether the bank opens: what is stored, what
 * matches it, and when the PIN is asked for.
 *
 * The keypad flow itself is a suspending dialogue and is not exercised here; the security-relevant
 * behaviour - that the stored form is a salted hash, that a wrong PIN never matches, and that a PIN
 * survives into the next session while the *verification* does not - is.
 */
class BankPinTests {
    private val pin = listOf(6, 7, 8, 9)

    @Test
    fun `a player with no PIN is never asked for one`() {
        val player = newPlayer()

        assertFalse(BankPin.isSet(player))
        assertFalse(BankPin.required(player))
    }

    @Test
    fun `setting a PIN counts as knowing it, so the bank does not immediately ask for it again`() {
        val player = newPlayer()

        BankPin.store(player, pin)

        assertTrue(BankPin.isSet(player))
        assertFalse(BankPin.required(player))
    }

    @Test
    fun `a PIN carried over from a previous session has to be given again`() {
        // What a login looks like: the persisted attributes are back, the session verification isn't.
        val player = newPlayer()
        player.attr[BankPin.PIN_HASH] = BankPin.hash(pin, salt = "abcdef")
        player.attr[BankPin.PIN_SALT] = "abcdef"

        assertTrue(BankPin.required(player))
        assertTrue(BankPin.matches(player, pin))
        assertFalse(BankPin.matches(player, listOf(6, 7, 8, 0)))
    }

    @Test
    fun `the order of the buttons is part of the PIN`() {
        val player = newPlayer()

        BankPin.store(player, pin)

        assertFalse(BankPin.matches(player, pin.reversed()))
    }

    @Test
    fun `deleting a PIN stops the bank asking for it`() {
        val player = newPlayer()
        BankPin.store(player, pin)

        BankPin.clear(player)

        assertFalse(BankPin.isSet(player))
        assertFalse(BankPin.required(player))
        assertFalse(BankPin.matches(player, pin))
    }

    @Test
    fun `the stored form is a salted hash, not the PIN`() {
        val stored = BankPin.hash(pin, salt = "abcdef")

        assertEquals(64, stored.length)
        assertEquals(stored, BankPin.hash(pin, salt = "abcdef"))
        assertNotEquals(stored, BankPin.hash(pin, salt = "fedcba"))
        assertFalse(stored.contains("6789"))
    }

    @Test
    fun `two players with the same PIN do not have the same stored form`() {
        val first = newPlayer()
        val second = newPlayer()

        BankPin.store(first, pin)
        BankPin.store(second, pin)

        assertNotEquals(first.attr[BankPin.PIN_HASH], second.attr[BankPin.PIN_HASH])
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        return player
    }
}
