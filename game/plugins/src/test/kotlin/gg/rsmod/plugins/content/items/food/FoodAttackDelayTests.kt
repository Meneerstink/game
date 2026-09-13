package gg.rsmod.plugins.content.items.food

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ATTACK_DELAY
import gg.rsmod.game.model.timer.TimerMap
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * OSRS eating vs the attack timer (owner decision 2026-09-12: "exact OSRS"). OSRS Wiki Food: "Most
 * food will delay your next attack or food consumption by 3 ticks"; karambwan 2. Void
 * Eating.consume + EatingTest: the delay is added to the remaining attack delay mid-attack, and out
 * of combat no attack delay is started. Covers every [Food] entry.
 */
class FoodAttackDelayTests {
    private fun player(): Player {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.definitions.get(ItemDef::class.java, any<Int>()) } returns mockk<ItemDef>(relaxed = true)
        every { player.world } returns world
        every { player.timers } returns TimerMap()
        return player
    }

    @Test
    fun `eating mid-attack extends the remaining attack delay by the food delay for every food`() {
        Food.values.forEach { food ->
            val p = player()
            p.timers[ATTACK_DELAY] = 2

            Foods.eat(p, food)

            assertEquals(2 + food.tickDelay, p.timers[ATTACK_DELAY], "${food.name} mid-attack")
        }
    }

    @Test
    fun `eating out of combat starts no attack delay for every food`() {
        Food.values.forEach { food ->
            val p = player()

            Foods.eat(p, food)

            assertFalse(p.timers.has(ATTACK_DELAY), "${food.name} out of combat")
        }
    }

    @Test
    fun `shark then karambwan stacks like OSRS`() {
        val p = player()
        p.timers[ATTACK_DELAY] = 1

        Foods.eat(p, Food.SHARK)
        assertEquals(4, p.timers[ATTACK_DELAY])

        Foods.eat(p, Food.KARAMBWAN)
        assertEquals(6, p.timers[ATTACK_DELAY])
    }
}
