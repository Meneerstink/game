package gg.rsmod.plugins.content.skills.cooking

import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.mockk
import kotlin.test.*

class SpicyStewTests {
    private fun inventory() = ItemContainer(mockk(relaxed = true), 28, ContainerStackType.NORMAL)

    @Test fun `full inventory doses each colour independently and stops at three without spending a fourth`() {
        val inv = inventory()
        for (slot in 0 until inv.capacity) inv[slot] = Item(Items.COINS_995)
        inv[0] = Item(Items.STEW)
        for (colour in SpicyStew.Colour.values()) {
            inv[1] = Item(colour.fourDose)
            repeat(3) { dose ->
                assertTrue(SpicyStew.addDose(inv, 0, 1))
                assertEquals(dose + 1, SpicyStew.doses(inv[0]!!, colour))
                assertEquals(colour.fourDose + dose + 1, inv[1]?.id)
            }
            assertFalse(SpicyStew.addDose(inv, 0, 1))
            assertEquals(colour.fourDose + 3, inv[1]?.id)
        }
        assertEquals(listOf("Red: A lot", "Orange: A lot", "Brown: A lot", "Yellow: A lot"), SpicyStew.smell(inv[0]!!))
        assertEquals(28, inv.count { it != null })
    }

    @Test fun `smell is read only and recipe follows item copies while new unseasoned stew has no fabricated doses`() {
        val inv = inventory()
        inv[4] = Item(Items.STEW)
        inv[8] = Item(Items.RED_SPICE_1)
        assertTrue(SpicyStew.addDose(inv, 4, 8))
        assertEquals(Items.EMPTY_SPICE_SHAKER, inv[8]?.id)
        inv[8] = Item(Items.ORANGE_SPICE_4)
        repeat(2) { assertTrue(SpicyStew.addDose(inv, 4, 8)) }
        val copied = Item(inv[4]!!)
        val saved = copied.attr.toMap()
        repeat(2) { assertEquals(listOf("Red: A little", "Orange: Some"), SpicyStew.smell(copied)) }
        assertEquals(saved, copied.attr)
        assertTrue(SpicyStew.smell(Item(Items.SPICY_STEW)).isEmpty())
        inv[5] = Item(Items.STEW)
        assertFalse(SpicyStew.addDose(inv, 5, 8))
        assertEquals(Items.STEW, inv[5]?.id)
        inv[4] = null
        assertTrue(SpicyStew.addDose(inv, 5, 8))
        assertEquals(0, SpicyStew.doses(inv[5]!!, SpicyStew.Colour.RED))
        assertEquals(1, SpicyStew.doses(inv[5]!!, SpicyStew.Colour.ORANGE))
    }
}
