package gg.rsmod.plugins.content.inter.bank

import gg.rsmod.game.message.impl.SynthSoundMessage
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * N137: the OSRS bank client scripts click (synth 2266 interface_select1) on their buttons; our 667 bank (762) never did.
 * bankmain_quantity_op only when the quantity changes, the placeholder toggle every time, item/note and swap/insert on every
 * switch, a tab only when it really changes, and the worn-items / bank view buttons.
 */
class BankClickSoundsTests {
    private fun player(): Player {
        val p = mockk<Player>(relaxed = true)
        every { p.attr } returns AttributeMap()
        return p
    }

    @Test
    fun `the quantity button clicks only when the quantity changes`() {
        val p = player()
        BankOptions.setQuantityMode(p, BankOptions.MODE_ONE)
        verify(exactly = 0) { p.write(match { it is SynthSoundMessage }) }
        BankOptions.setQuantityMode(p, BankOptions.MODE_ALL)
        verify(exactly = 1) { p.write(match { it is SynthSoundMessage && it.sound == 2266 }) }
    }

    @Test
    fun `the placeholder toggle always clicks`() {
        val p = player()
        BankOptions.setAlwaysPlaceholder(p, true)
        BankOptions.setAlwaysPlaceholder(p, false)
        verify(exactly = 2) { p.write(match { it is SynthSoundMessage && it.sound == 2266 }) }
    }

    @Test
    fun `item-note, swap-insert, the view buttons and a real tab change click`() {
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/inter/bank/bank.plugin.kts").readText()
        assertTrue(Regex("BankOptions\\.click\\(player\\) // N137: OSRS bankmain_swapinsert_op").containsMatchIn(plugin))
        assertTrue(Regex("BankOptions\\.click\\(player\\) // N137: OSRS bankmain_itemnote_op").containsMatchIn(plugin))
        assertTrue(Regex("BankOptions\\.click\\(player\\) // N137: OSRS bankmain_viewbuttons").findAll(plugin).count() == 2)
        val tabs = File("src/main/kotlin/gg/rsmod/plugins/content/inter/bank/BankTabs.kt").readText()
        assertTrue("if (player.getVarbit(SELECTED_TAB_VARBIT) != tab + 1) BankOptions.click(player)" in tabs)
    }
}
