package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.LockState
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.combine.CombinationAction
import gg.rsmod.plugins.content.items.combine.CombinationData
import io.mockk.every
import io.mockk.verify
import kotlin.test.*

class N11CombineTests {
    private val recipe = CombinationData.ARMADYL_ORB

    private fun fixture() = N11Fixture().apply {
        // Exact census menu, S21776; no noted or worn definition/option exists for this stackable item.
        menu(Items.SHARDS_OF_ARMADYL, "Combine")
        def(Items.SHARDS_OF_ARMADYL).stacks = true
        every { skills.getCurrentLevel(Skills.CRAFTING) } returns 77
    }

    @Test fun `compiled shared route consumes exactly one hundred and a rapid stale click grants nothing`() {
        fixture().use { f ->
            f.load("items.combine.Item_combination_plugin")
            f.inventory[0] = Item(Items.SHARDS_OF_ARMADYL, 100)
            assertTrue(f.repo.executeItem(f.player, Items.SHARDS_OF_ARMADYL, 1))
            f.queues.cycle()
            assertEquals(0, f.inventory.getItemCount(Items.SHARDS_OF_ARMADYL))
            assertEquals(1, f.inventory.getItemCount(Items.ORB_OF_ARMADYL))
            f.repo.executeItem(f.player, Items.SHARDS_OF_ARMADYL, 1)
            f.queues.cycle()
            assertEquals(1, f.inventory.getItemCount(Items.ORB_OF_ARMADYL))
            verify(exactly = 1) { f.player.addXp(Skills.CRAFTING, 150.0, any(), any()) }
            verify { f.player.write(match<MessageGameMessage> { it.message == "You combine the shards into an orb." }) }
        }
    }

    @Test fun `missing shards or insufficient crafting never spends or awards XP`() {
        for ((count, level) in listOf(99 to 77, 100 to 76)) fixture().use { f ->
            every { f.skills.getCurrentLevel(Skills.CRAFTING) } returns level
            f.load("items.combine.Item_combination_plugin")
            f.inventory[0] = Item(Items.SHARDS_OF_ARMADYL, count)
            f.repo.executeItem(f.player, Items.SHARDS_OF_ARMADYL, 1)
            f.queues.cycle()
            assertEquals(count, f.inventory.getItemCount(Items.SHARDS_OF_ARMADYL))
            assertEquals(0, f.inventory.getItemCount(Items.ORB_OF_ARMADYL))
            verify(exactly = 0) { f.player.addXp(any(), any(), any(), any()) }
            if (count == 99) verify { f.player.write(match<MessageGameMessage> { it.message == "You need 100 shards to create an orb." }) }
        }
    }

    @Test fun `full inventory consumes an exact stack but preserves a larger stack when output cannot fit`() {
        for (count in listOf(100, 101, 200)) fixture().use { f ->
            f.inventory[0] = Item(Items.SHARDS_OF_ARMADYL, count)
            (1..27).forEach { f.inventory[it] = Item(Items.BUCKET) }
            assertEquals(count == 100, CombinationAction.combineStack(f.inventory, recipe))
            assertEquals(if (count == 100) 0 else count, f.inventory.getItemCount(Items.SHARDS_OF_ARMADYL))
            assertEquals(if (count == 100) 1 else 0, f.inventory.getItemCount(Items.ORB_OF_ARMADYL))
            assertEquals(27, f.inventory.getItemCount(Items.BUCKET))
            if (count > 100) {
                f.inventory[27] = null
                assertTrue(CombinationAction.combineStack(f.inventory, recipe))
                assertEquals(count - 100, f.inventory.getItemCount(Items.SHARDS_OF_ARMADYL))
            }
        }
    }

    @Test fun `compiled full inventory refusal retains shards and emits the shared space message without XP`() {
        fixture().use { f ->
            f.load("items.combine.Item_combination_plugin")
            f.inventory[0] = Item(Items.SHARDS_OF_ARMADYL, 101)
            (1..27).forEach { f.inventory[it] = Item(Items.BUCKET) }
            f.repo.executeItem(f.player, Items.SHARDS_OF_ARMADYL, 1)
            f.queues.cycle()
            assertEquals(101, f.inventory.getItemCount(Items.SHARDS_OF_ARMADYL))
            assertEquals(0, f.inventory.getItemCount(Items.ORB_OF_ARMADYL))
            verify(exactly = 0) { f.player.addXp(any(), any(), any(), any()) }
            verify { f.player.write(match<MessageGameMessage> { it.message == "You don't have enough inventory space." }) }
        }
    }

    @Test fun `equipment other item stale slot and locked click cannot spend an inventory stack elsewhere`() {
        fixture().use { f ->
            f.load("items.combine.Item_combination_plugin")
            f.equipment[0] = Item(Items.SHARDS_OF_ARMADYL, 100)
            f.inventory[1] = Item(Items.SHARDS_OF_ARMADYL, 100)
            f.inventory[0] = Item(Items.BUCKET)
            f.repo.executeItem(f.player, Items.SHARDS_OF_ARMADYL, 1)
            f.queues.cycle()
            assertEquals(100, f.inventory.getItemCount(Items.SHARDS_OF_ARMADYL))
            assertFalse(f.repo.hasEquipmentOption(Items.SHARDS_OF_ARMADYL, 1))
            f.select(1)
            every { f.player.lock } returns LockState.FULL
            f.repo.executeItem(f.player, Items.SHARDS_OF_ARMADYL, 1)
            f.queues.cycle()
            assertEquals(100, f.inventory.getItemCount(Items.SHARDS_OF_ARMADYL))
        }
    }

    @Test fun `moving the selected stack before queue dispatch cannot spend it in another slot`() {
        fixture().use { f ->
            f.load("items.combine.Item_combination_plugin")
            f.inventory[0] = Item(Items.SHARDS_OF_ARMADYL, 100)
            f.repo.executeItem(f.player, Items.SHARDS_OF_ARMADYL, 1)
            f.inventory[1] = f.inventory[0]
            f.inventory[0] = null
            f.queues.cycle()
            assertEquals(100, f.inventory.getItemCount(Items.SHARDS_OF_ARMADYL))
            assertEquals(0, f.inventory.getItemCount(Items.ORB_OF_ARMADYL))
            verify(exactly = 0) { f.player.addXp(any(), any(), any(), any()) }
        }
    }

    @Test fun `combat uses ordinary shared combine rules and two valid clicks spend two real batches`() {
        fixture().use { f ->
            f.load("items.combine.Item_combination_plugin")
            f.timers[ACTIVE_COMBAT_TIMER] = 10
            f.inventory[0] = Item(Items.SHARDS_OF_ARMADYL, 200)
            repeat(2) {
                f.repo.executeItem(f.player, Items.SHARDS_OF_ARMADYL, 1)
                f.queues.cycle()
            }
            assertEquals(0, f.inventory.getItemCount(Items.SHARDS_OF_ARMADYL))
            assertEquals(2, f.inventory.getItemCount(Items.ORB_OF_ARMADYL))
            verify(exactly = 2) { f.player.addXp(Skills.CRAFTING, 150.0, any(), any()) }
        }
    }

    @Test fun `prebound Combine is preserved instead of causing a duplicate plugin binding at boot`() {
        fixture().use { f ->
            var calls = 0
            f.repo.bindItem(Items.SHARDS_OF_ARMADYL, 1) { calls++ }
            f.load("items.combine.Item_combination_plugin")
            f.repo.executeItem(f.player, Items.SHARDS_OF_ARMADYL, 1)
            f.queues.cycle()
            assertEquals(1, calls)
        }
    }

    @Test fun `existing orb attachment still uses the shared two item recipe and requirement`() {
        fixture().use { f ->
            f.load("items.combine.Item_combination_plugin")
            f.inventory[0] = Item(Items.ORB_OF_ARMADYL)
            f.inventory[1] = Item(Items.BATTLESTAFF)
            assertTrue(f.repo.executeItemOnItem(f.player, Items.ORB_OF_ARMADYL, Items.BATTLESTAFF))
            f.queues.cycle()
            assertEquals(1, f.inventory.getItemCount(Items.ARMADYL_BATTLESTAFF))
            assertEquals(0, f.inventory.getItemCount(Items.ORB_OF_ARMADYL))
            assertEquals(0, f.inventory.getItemCount(Items.BATTLESTAFF))
            verify(exactly = 1) { f.player.addXp(Skills.CRAFTING, 150.0, any(), any()) }
        }
    }
}
