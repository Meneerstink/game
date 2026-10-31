package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.ATTACK_DELAY
import gg.rsmod.game.model.timer.FOOD_DELAY
import gg.rsmod.game.model.timer.COMBO_FOOD_DELAY
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.food.*
import io.mockk.verify
import io.mockk.every
import kotlin.test.*

class N11FoodTests {
    private val geMeals = mapOf(9527 to 11, 9529 to 11, 9531 to 11, 9533 to 11, 9535 to 11,
        9538 to 8, 9540 to 7, 9542 to 8, 9544 to 7, 9547 to 12, 9549 to 12, 9553 to 15)

    @Test fun `every GE gnome meal heals correctly through the compiled food route and cannot double eat`() {
        geMeals.forEach { (id, hp) ->
            N11Fixture().use { f ->
                Food.values.forEach { f.menu(it.item, "Eat") }
                InedibleFoods.refusals.keys.forEach { f.menu(it, "Eat") }
                f.load("items.food.Eating_plugin")
                f.inventory[0] = Item(id)
                f.inventory[1] = Item(id)
                f.timers[ATTACK_DELAY] = 4
                assertTrue(f.repo.executeItem(f.player, id, 1))
                f.select(1)
                f.repo.executeItem(f.player, id, 1)
                assertEquals(1, f.inventory.getItemCount(id))
                assertEquals(6, f.timers[ATTACK_DELAY], "gnome attack delay is two ticks")
                assertEquals(3, f.timers[COMBO_FOOD_DELAY])
                assertEquals(3, f.timers[FOOD_DELAY])
                verify(exactly = 1) { f.player.alterLifepoints(hp, 0) }
                assertFalse(f.repo.hasItemOption(id + 1, 1))
            }
        }
    }

    @Test fun `shark then gnome meal is a combo and gnome then shark is refused`() {
        N11Fixture().use { f ->
            Food.values.forEach { f.menu(it.item, "Eat") }
            InedibleFoods.refusals.keys.forEach { f.menu(it, "Eat") }
            f.load("items.food.Eating_plugin")
            f.inventory[0] = Item(Items.SHARK)
            f.inventory[1] = Item(9529)
            f.inventory[2] = Item(Items.SHARK)
            f.timers[ATTACK_DELAY] = 4
            f.repo.executeItem(f.player, Items.SHARK, 1)
            f.select(1)
            f.repo.executeItem(f.player, 9529, 1)
            f.select(2)
            f.repo.executeItem(f.player, Items.SHARK, 1)
            assertEquals(9, f.timers[ATTACK_DELAY])
            assertEquals(Items.SHARK, f.inventory[2]?.id)
        }
    }

    @Test fun `Gout tuber restores twelve HP and fifty energy while roasted chompy heals ten`() {
        N11Fixture().use { f ->
            Food.values.forEach { f.menu(it.item, "Eat") }
            InedibleFoods.refusals.keys.forEach { f.menu(it, "Eat") }
            f.load("items.food.Eating_plugin")
            every { f.player.runEnergy } returns 70.0
            f.inventory[0] = Item(6311)
            f.repo.executeItem(f.player, 6311, 1)
            verify { f.player.alterLifepoints(12, 0) }
            verify { f.player.runEnergy = 100.0 }
            f.timers.remove(FOOD_DELAY)
            f.inventory[0] = Item(7228)
            f.repo.executeItem(f.player, 7228, 1)
            verify { f.player.alterLifepoints(10, 0) }
        }
    }

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
