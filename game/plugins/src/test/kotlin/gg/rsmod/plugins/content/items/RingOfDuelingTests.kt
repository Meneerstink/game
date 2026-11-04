package gg.rsmod.plugins.content.items

import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.jewellery.RingOfDueling
import gg.rsmod.plugins.content.magic.TeleportType
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class RingOfDuelingTests {
    private fun player(): Player = mockk<Player>(relaxed = true).also {
        every { it.inventory } returns ItemContainer(mockk(relaxed = true), 28, ContainerStackType.NORMAL)
        every { it.equipment } returns ItemContainer(mockk(relaxed = true), 15, ContainerStackType.NORMAL)
    }

    @Test fun `all eight charge variants spend one and final charge disappears in either container`() {
        for (equipped in listOf(false, true)) for ((index, id) in RingOfDueling.ITEMS.withIndex()) {
            val p = player()
            val container = if (equipped) p.equipment else p.inventory
            val slot = if (equipped) EquipmentType.RING.id else 4
            val ring = Item(id)
            container[slot] = ring
            assertTrue(RingOfDueling.spend(p, ring, slot, equipped))
            assertEquals(RingOfDueling.ITEMS.getOrNull(index + 1), container[slot]?.id)
            assertFalse(RingOfDueling.spend(p, ring, slot, equipped), "replayed click must not spend twice")
        }
        assertEquals(Items.RING_OF_DUELLING_8, RingOfDueling.ITEMS.first())
        assertEquals(Items.RING_OF_DUELLING_1, RingOfDueling.ITEMS.last())
        assertEquals(20, TeleportType.FEROCIOUS_RING.wildLvlRestriction)
    }

    @Test fun `moving ring during countdown does not debit a different ring or grant a teleport`() {
        val p = player()
        val original = Item(Items.RING_OF_DUELLING_1)
        p.inventory[3] = Item(Items.RING_OF_DUELLING_1)
        p.inventory[4] = original
        assertFalse(RingOfDueling.spend(p, original, 3, false))
        assertEquals(Items.RING_OF_DUELLING_1, p.inventory[3]?.id)
        assertEquals(original, p.inventory[4])
        assertTrue("Ferox Enclave" in RingOfDueling.DESTINATIONS)
    }
}
