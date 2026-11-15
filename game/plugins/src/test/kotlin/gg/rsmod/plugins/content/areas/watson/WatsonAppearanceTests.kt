package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.varp.VarpSet
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class WatsonAppearanceTests {
    private val row = WatsonAppearance.Layout(14406, 15234, 15235, 2408)
    private fun player() = mockk<Player>(relaxed = true).also {
        every { it.attr } returns AttributeMap()
        every { it.varps } returns VarpSet(setOf(row.control))
    }

    @Test fun `introduction changes one account only and relog reconstructs the proper menu`() {
        val first = player(); val second = player()
        WatsonAppearance.sync(first, row); WatsonAppearance.sync(second, row)
        first.attr[WatsonClues.INTRODUCED] = true
        WatsonAppearance.sync(first, row)
        assertEquals(1, first.varps.getState(row.control))
        assertEquals(0, second.varps.getState(row.control))
        val relog = player()
        relog.attr[WatsonClues.INTRODUCED] = first.attr[WatsonClues.INTRODUCED]!!
        WatsonAppearance.sync(relog, row)
        assertEquals(1, relog.varps.getState(row.control))
        first.attr[WatsonClues.INTRODUCED] = false
        WatsonAppearance.sync(first, row)
        assertEquals(0, first.varps.getState(row.control))
        assertEquals(1, relog.varps.getState(row.control))
    }

    @Test fun `unapplied plans and duplicate mapping identities cannot activate personal NPC forms`() {
        val text = "# CLUE Watson fixture; applied=true\nroot\t7303\t14406\nnpc\t7303\t15234\nnpc\t7304\t15235\ncontrol\t7303\t2408\n"
        assertEquals(row, WatsonAppearance.parse(text))
        assertFailsWith<IllegalArgumentException> { WatsonAppearance.parse(text.replace("applied=true", "applied=false")) }
        assertFailsWith<IllegalArgumentException> { WatsonAppearance.parse(text.replace("npc\t7304", "npc\t7303")) }
        assertFailsWith<IllegalArgumentException> { WatsonAppearance.parse(text.replace("15235", "14406")) }
    }
}
