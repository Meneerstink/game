package gg.rsmod.plugins.content.n11

import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.items.ItemActionGuard
import gg.rsmod.plugins.content.items.charges.ItemCharges
import kotlin.test.*

class N11RemoveChargeTests {
    // Testclient 2026-10-31: the question read "Are you sure you want to remove-charge this item?".
    @Test fun `the confirmation asks in plain words`() {
        assertEquals("Are you sure you want to remove the charges?", ItemActionGuard.question("remove-charge"))
        assertEquals("Are you sure you want to dismantle this item?", ItemActionGuard.question("dismantle"))
    }

    private fun load(f: N11Fixture) {
        // Partial masks are absent from the GE-only census fixture. Supply their real cache menu
        // before loading the script: string option registration resolves the slot at load time.
        ItemCharges.removable.keys.forEach { f.def(it).inventoryMenu[3] = "Remove-charge" }
        ItemActionGuard.install(f.repo)
        f.load("items.charges.Remove_charges_plugin")
    }
    private fun answer(f: N11Fixture, yes: Boolean) {
        f.queues.cycle()
        f.queues.submitReturnValue(ResumePauseButtonMessage(94, if (yes) 3 else 4, if (yes) 3 else 4))
        f.queues.cycle()
    }

    @Test fun `all native charged forms require shared confirmation and replace in a full inventory`() {
        ItemCharges.removable.forEach { (charged, empty) ->
            N11Fixture().use { f ->
                load(f)
                repeat(28) { f.inventory[it] = Item(100000 + it) }
                val held = Item(charged)
                f.inventory[0] = held
                f.repo.executeItem(f.player, charged, 4)
                assertSame(held, f.inventory[0])
                answer(f, false)
                assertSame(held, f.inventory[0])
                f.repo.executeItem(f.player, charged, 4)
                answer(f, true)
                assertEquals(empty, f.inventory[0]?.id)
                assertTrue(f.inventory[0]!!.attr.isEmpty())
                f.repo.executeItem(f.player, charged, 4)
                answer(f, true)
                assertEquals(empty, f.inventory[0]?.id)
                assertFalse(f.repo.hasItemOption(charged + 1, 4))
                assertFalse(f.repo.hasEquipmentOption(charged, 4))
            }
        }
    }

    @Test fun `all ten black mask charge counts are removable without binding noted or empty masks`() {
        assertEquals((8901..8919 step 2).toSet(), ItemCharges.removable.filterValues { it == 8921 }.keys)
        N11Fixture().use { f ->
            load(f)
            assertFalse(f.repo.hasItemOption(8921, 4))
            (8902..8920 step 2).forEach { assertFalse(f.repo.hasItemOption(it, 4)) }
        }
    }

    @Test fun `shared confirmation cannot remove a replacement identical item or follow a moved item`() {
        N11Fixture().use { f ->
            load(f)
            f.inventory[0] = Item(8901)
            f.repo.executeItem(f.player, 8901, 4)
            f.queues.cycle()
            val replacement = Item(8901)
            f.inventory[0] = replacement
            answer(f, true)
            assertSame(replacement, f.inventory[0])
            f.repo.executeItem(f.player, 8901, 4)
            f.queues.cycle()
            f.inventory[0] = null
            f.inventory[1] = replacement
            answer(f, true)
            assertSame(replacement, f.inventory[1])
        }
    }

    @Test fun `remove-charge uses uncharge semantics and retains preexisting bindings`() {
        assertEquals(ItemActionGuard.sound("uncharge"), ItemActionGuard.sound("remove-charge"))
        assertEquals(ItemActionGuard.detail("uncharge"), ItemActionGuard.detail("remove-charge"))
        N11Fixture().use { f ->
            f.repo.bindItem(8901, 4) {}
            load(f)
        }
    }
}
