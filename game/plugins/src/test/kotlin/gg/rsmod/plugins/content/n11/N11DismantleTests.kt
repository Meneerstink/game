package gg.rsmod.plugins.content.n11

import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.ItemActionGuard
import kotlin.test.*

class N11DismantleTests {
    @Test fun `visage dismantles through shared helm route after confirmation even in a full inventory`() {
        N11Fixture().use { f ->
            f.def(Items.ZULRAHS_SCALES).stacks = true
            ItemActionGuard.install(f.repo)
            f.load("items.osrs.Serpentine_helm_plugin")
            repeat(28) { f.inventory[it] = Item(100000 + it) }
            f.inventory[0] = Item(23936)
            assertTrue(f.repo.executeItem(f.player, 23936, 4))
            f.queues.cycle()
            assertEquals(23936, f.inventory[0]?.id)
            f.queues.submitReturnValue(ResumePauseButtonMessage(94, 3, 3))
            f.queues.cycle()
            assertEquals(20_000, f.inventory.getItemCount(Items.ZULRAHS_SCALES))
            assertEquals(0, f.inventory.getItemCount(23936))
            f.repo.executeItem(f.player, 23936, 4)
            f.queues.cycle()
            assertEquals(20_000, f.inventory.getItemCount(Items.ZULRAHS_SCALES))
        }
    }

    @Test fun `visage retains an existing dismantle binding`() {
        N11Fixture().use { f ->
            f.repo.bindItem(23936, 4) {}
            f.load("items.osrs.Serpentine_helm_plugin")
        }
    }
}
