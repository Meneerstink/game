package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.ItemContents
import gg.rsmod.plugins.content.skills.farming.ProduceContainers
import java.io.File
import kotlin.test.*

class N11ContentsTests {
    @Test fun `every sack and net quantity fills removes and empties without losing produce`() {
        ProduceContainers.families.forEach { family ->
            (1..10).forEach { count ->
                N11Fixture().use { f ->
                    f.inventory[0] = Item(family.id(count))
                    assertNotNull(ProduceContainers.operate(f.inventory, 0, family.id(count), "Remove-one"))
                    assertEquals(family.id(count - 1), f.inventory[0]?.id)
                    assertEquals(1, f.inventory.getItemCount(family.produce))
                    assertNull(ProduceContainers.operate(f.inventory, 0, family.id(count - 1), "Fill", family.produce))
                    assertEquals(family.id(count), f.inventory[0]?.id)
                    assertEquals(0, f.inventory.getItemCount(family.produce))
                    ProduceContainers.operate(f.inventory, 0, family.id(count), "Empty")
                    assertEquals(family.empty, f.inventory[0]?.id)
                    assertEquals(count, f.inventory.getItemCount(family.produce))
                }
            }
        }
    }

    @Test fun `full inventory refuses and partial Empty preserves every remaining item for every family`() {
        ProduceContainers.families.forEach { family ->
            N11Fixture().use { f ->
                f.inventory.items.indices.forEach { f.inventory[it] = Item(995) }
                f.inventory[0] = Item(family.id(10))
                assertEquals(ItemContents.NO_SPACE, ProduceContainers.operate(f.inventory, 0, family.id(10), "Empty"))
                assertEquals(ItemContents.NO_SPACE, ProduceContainers.operate(f.inventory, 0, family.id(10), "Remove-one"))
                f.inventory[27] = null
                assertNull(ProduceContainers.operate(f.inventory, 0, family.id(10), "Empty"))
                assertEquals(family.id(9), f.inventory[0]?.id)
                assertEquals(1, f.inventory.getItemCount(family.produce))
                ProduceContainers.operate(f.inventory, 0, family.id(10), "Empty")
                assertEquals(1, f.inventory.getItemCount(family.produce), "stale repeat gives nothing twice")
            }
        }
    }

    @Test fun `empty full wrong species and notes cannot discard or transmute produce`() {
        N11Fixture().use { f ->
            val potato = ProduceContainers.sacks.first { it.produce == Items.POTATO }
            f.inventory[0] = Item(potato.id(1))
            f.inventory[1] = Item(Items.ONION)
            assertNotNull(ProduceContainers.operate(f.inventory, 0, potato.id(1), "Fill", Items.ONION))
            assertEquals(potato.id(1), f.inventory[0]?.id)
            f.inventory[1] = Item(Items.POTATO_NOTED, 10)
            assertNotNull(ProduceContainers.operate(f.inventory, 0, potato.id(1), "Fill", Items.POTATO_NOTED))
            assertEquals(10, f.inventory.getItemCount(Items.POTATO_NOTED))
            f.inventory[0] = Item(Items.EMPTY_SACK)
            assertNotNull(ProduceContainers.operate(f.inventory, 0, Items.EMPTY_SACK, "Empty"))
            f.inventory[0] = Item(potato.id(10))
            assertEquals("The potato sack is already full.", ProduceContainers.operate(f.inventory, 0, potato.id(10), "Fill"))
            assertEquals(Items.POTATO_NOTED, f.inventory[1]?.id)
        }
    }

    @Test fun `nettle tea uses the existing empty bowl family`() {
        val rows = File("../../data/cfg/item_empty.tsv").readLines().filter { !it.startsWith("#") && it.isNotBlank() }
            .associate { it.split('\t').let { c -> c[0].toInt() to c[1].toInt() } }
        assertEquals(Items.BOWL, rows[Items.NETTLE_TEA])
        assertEquals(Items.BOWL, rows[4240])
    }
}
