package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.ATTACK_DELAY
import gg.rsmod.game.model.timer.FOOD_DELAY
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.food.*
import io.mockk.verify
import kotlin.test.*

class N11FoodTests {
    @Test fun `cake and sweetcorn use the compiled food route with delay and leftovers`() {
        N11Fixture().use { f ->
            Food.values.forEach { f.menu(it.item, "Eat") }
            InedibleFoods.refusals.keys.forEach { f.menu(it, "Eat") }
            f.load("items.food.Eating_plugin")
            f.inventory[0] = Item(Items.CELEBRATION_CAKE)
            f.inventory[1] = Item(Items.CELEBRATION_CAKE)
            f.timers[ATTACK_DELAY] = 2
            assertTrue(f.repo.executeItem(f.player, Items.CELEBRATION_CAKE, 1))
            f.select(1)
            f.repo.executeItem(f.player, Items.CELEBRATION_CAKE, 1)
            assertEquals(1, f.inventory.getItemCount(Items.CELEBRATION_CAKE), "rapid second click is refused")
            assertEquals(5, f.timers[ATTACK_DELAY])
            verify(exactly = 1) { f.player.alterLifepoints(20, 0) }
            f.timers.remove(FOOD_DELAY)
            f.select(0)
            f.inventory[0] = Item(Items.SWEETCORN_7088)
            f.repo.executeItem(f.player, Items.SWEETCORN_7088, 1)
            assertEquals(Items.BOWL, f.inventory[0]?.id)
            verify(exactly = 1) { f.player.alterLifepoints(10, 0) }
        }
    }

    @Test fun `whole fruit and rotten apple refuse without heal loss damage or delay`() {
        N11Fixture().use { f ->
            Food.values.forEach { f.menu(it.item, "Eat") }
            InedibleFoods.refusals.keys.forEach { f.menu(it, "Eat") }
            f.load("items.food.Eating_plugin")
            InedibleFoods.refusals.keys.forEach { id ->
                val held = Item(id)
                f.inventory[0] = held
                assertTrue(f.repo.executeItem(f.player, id, 1))
                assertSame(held, f.inventory[0])
            }
            assertFalse(f.timers.has(FOOD_DELAY))
            verify(exactly = 0) { f.player.alterLifepoints(any(), any()) }
            assertFalse(f.repo.hasItemOption(Items.CELEBRATION_CAKE_NOTED, 1))
            // A stale packet may not consume another slot's food.
            f.inventory[0] = Item(Items.CELEBRATION_CAKE_NOTED)
            f.inventory[1] = Item(Items.CELEBRATION_CAKE)
            f.repo.executeItem(f.player, Items.CELEBRATION_CAKE, 1)
            assertEquals(1, f.inventory.getItemCount(Items.CELEBRATION_CAKE))
        }
    }

    @Test fun `sweetcorn scales with base hitpoints`() {
        assertEquals(listOf(1, 2, 5, 10), listOf(1, 10, 40, 99).map(Foods::sweetcornHeal))
    }
}
