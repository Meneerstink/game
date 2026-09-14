package gg.rsmod.plugins.content.inter.bank

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RCV-012.B17: the bank PIN keypad follows the revision-667 cache and Novite - digit cells on interface 759 mounted into 13:5
 * (`component / 4 - 1`), Exit on 13:25, keypad state reset with varbit 1010 and client script 1271 - instead of pause buttons on
 * the empty layers 13:6-15.
 */
class BankPinKeypadTests {
    @Test
    fun `every 759 digit cell maps to its digit and nothing else is a digit`() {
        assertEquals(listOf(4, 8, 12, 16, 20, 24, 28, 32, 36, 40), BankPin.DIGIT_COMPONENTS)
        assertEquals((0..9).toList(), BankPin.DIGIT_COMPONENTS.map { BankPin.digitForComponent(it) })
        listOf(0, 1, 3, 5, 6, 15, 25, 41, 44).forEach { assertNull(BankPin.digitForComponent(it), "component $it") }
    }

    @Test
    fun `presses are kept in click order, even several in one cycle, and exit is remembered`() {
        val input = BankPin.KeypadInput()
        assertFalse(input.hasDigit)
        assertNull(input.takeDigit())
        input.press(7)
        input.press(0)
        input.press(7)
        assertTrue(input.hasDigit)
        assertEquals(listOf(7, 0, 7), listOfNotNull(input.takeDigit(), input.takeDigit(), input.takeDigit()))
        assertFalse(input.hasDigit)
        assertNull(input.takeDigit())
        assertFalse(input.exited)
        input.exit()
        assertTrue(input.exited)
    }

    @Test
    fun `keypad opens the digit interface and resets the client state, and the buttons are bound`() {
        val pin = File("src/main/kotlin/gg/rsmod/plugins/content/inter/bank/BankPin.kt").readText()
        listOf(
            "player.openInterface(parent = PIN_INTERFACE_ID, child = KEYPAD_SLOT, interfaceId = DIGITS_INTERFACE_ID, type = 1)",
            "player.setVarbit(STAGE_VARBIT, 0)",
            "player.runClientScript(KEYPAD_SCRIPT, 1)",
            "player.setVarp(KEYPAD_VARP, 0)",
            "player.setVarc(KEYPAD_VARC, -1)",
        ).forEach { assertTrue(it in pin, it) }
        assertEquals(listOf(759, 5, 25, 13), listOf(BankPin.DIGITS_INTERFACE_ID, BankPin.KEYPAD_SLOT, BankPin.EXIT_COMPONENT, BankPin.PIN_INTERFACE_ID))
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/inter/bank/bank.plugin.kts").readText()
        assertTrue("on_button(interfaceId = BankPin.DIGITS_INTERFACE_ID, component = component)" in plugin)
        assertTrue("BankPin.pressDigit(player, component)" in plugin)
        assertTrue("on_button(interfaceId = BankPin.PIN_INTERFACE_ID, component = BankPin.EXIT_COMPONENT)" in plugin)
        assertTrue("ResumePauseButtonMessage" !in pin, "the keypad has no pause buttons")
    }
}
