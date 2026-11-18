package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.message.impl.IfSetSpriteMessage
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import io.mockk.*
import kotlin.test.*

class TreasureTrailStashSettingTests {
    private val row = TreasureTrailStashSetting.Layout(26, 27, 8515, 8516)
    private fun player() = mockk<Player>(relaxed = true).also {
        every { it.attr } returns AttributeMap()
        every { it.interfaces.isVisible(261) } returns true
    }

    @BeforeTest fun setup() {
        mockkObject(TreasureTrailStashSetting)
        every { TreasureTrailStashSetting.layout } returns row
    }
    @AfterTest fun cleanup() { unmockkObject(TreasureTrailStashSetting) }

    @Test fun `setting defaults off and restores from the same account storage flag after relog and pane remount`() {
        val first = player(); val second = player()
        TreasureTrailStashSetting.sync(first)
        verify { first.write(IfSetSpriteMessage((261 shl 16) or 27, 8515)) }
        TreasureTrailStashSetting.toggle(first)
        assertEquals(true, first.attr[TreasureTrailStashes.TAKE_EQUIPPED])
        assertNull(second.attr[TreasureTrailStashes.TAKE_EQUIPPED])
        verify { first.write(IfSetSpriteMessage((261 shl 16) or 27, 8516)) }
        val relogged = player()
        relogged.attr[TreasureTrailStashes.TAKE_EQUIPPED] = first.attr[TreasureTrailStashes.TAKE_EQUIPPED]!!
        TreasureTrailStashSetting.sync(relogged)
        verify { relogged.write(IfSetSpriteMessage((261 shl 16) or 27, 8516)) }
        TreasureTrailStashSetting.toggle(relogged)
        assertEquals(false, relogged.attr[TreasureTrailStashes.TAKE_EQUIPPED])
        assertEquals(true, first.attr[TreasureTrailStashes.TAKE_EQUIPPED])
    }

    @Test fun `forged clicks on a closed pane or absent cache batch cannot enable equipment removal`() {
        val p = player()
        every { p.interfaces.isVisible(261) } returns false
        TreasureTrailStashSetting.toggle(p)
        assertNull(p.attr[TreasureTrailStashes.TAKE_EQUIPPED])
        every { p.interfaces.isVisible(261) } returns true
        every { TreasureTrailStashSetting.layout } returns null
        TreasureTrailStashSetting.toggle(p)
        TreasureTrailStashSetting.sync(p)
        assertNull(p.attr[TreasureTrailStashes.TAKE_EQUIPPED])
        verify(exactly = 0) { p.write(any()) }
    }

    @Test fun `unapplied colliding incomplete or unexpected selector mappings are rejected`() {
        val text = "# STASH setting fixture; applied=true\ninterface\t6245\t261\nbutton\t6245\t26\ncheckbox\t6245\t27\nlabel\t6245\t28\noff\t6245\t8515\non\t6245\t8516\n"
        assertEquals(row, TreasureTrailStashSetting.parseMapping(text))
        for (invalid in listOf(text.replace("applied=true", "applied=false"), text.replace("button\t6245\t26", "button\t6245\t25"),
            text.replace("on\t6245\t8516", "on\t6245\t8515"), text + "button\t6245\t30\n",
            text.replace("on\t6245\t8516\n", ""))) {
            assertFailsWith<IllegalArgumentException> { TreasureTrailStashSetting.parseMapping(invalid) }
        }
    }
}
