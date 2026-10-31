package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfSetTextMessage
import gg.rsmod.plugins.content.items.osrs.DragonfireShield
import gg.rsmod.plugins.api.ext.setComponentText
import io.mockk.verify
import kotlin.test.*

class N11InspectTests {
    @Test fun `native shield uses shared Inspect without changing the existing charge lifecycle`() {
        N11Fixture().use { f ->
            f.load("items.osrs.Dragonfire_shield_plugin")
            val shield = Item(11284).also { it.attr[ItemAttribute.CHARGES] = 7 }
            f.inventory[0] = shield
            assertNotNull(DragonfireShield.familyOf(11284))
            assertEquals("The shield has 7 charges.", DragonfireShield.inspectMessage(shield))
            assertEquals(0, DragonfireShield.charges(Item(11283)))
            assertEquals(11283, DragonfireShield.withCharges(Item(11283), 0).id)
            assertTrue(f.repo.executeItem(f.player, 11284, 3))
            f.queues.cycle()
            verify { f.player.setComponentText(519, 1, "The shield has 7 charges.") }
            assertSame(shield, f.inventory[0])
        }
    }

    @Test fun `Inspect ignores notes stale slots and a replaced item before the queue starts`() {
        N11Fixture().use { f ->
            f.load("items.osrs.Dragonfire_shield_plugin")
            f.inventory[0] = Item(11284)
            f.repo.executeItem(f.player, 11284, 3)
            f.inventory[0] = Item(11284)
            f.queues.cycle()
            verify(exactly = 0) { f.player.write(*varargAny<Message> { it is IfSetTextMessage && it.parent == 519 }) }
            f.inventory[0] = Item(11285)
            f.repo.executeItem(f.player, 11284, 3)
            f.queues.cycle()
            assertFalse(f.repo.hasItemOption(11285, 3))
        }
    }

    @Test fun `Inspect does not duplicate a prebound native shield option`() {
        N11Fixture().use { f ->
            f.repo.bindItem(11284, 3) {}
            f.load("items.osrs.Dragonfire_shield_plugin")
        }
    }
}
