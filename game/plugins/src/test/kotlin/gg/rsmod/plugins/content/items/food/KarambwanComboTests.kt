package gg.rsmod.plugins.content.items.food

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.FOOD_DELAY
import gg.rsmod.game.model.timer.POTION_DELAY
import gg.rsmod.game.model.timer.TimerMap
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Audit C-14: the OSRS combo order is food -> potion -> karambwan; a karambwan first blocks normal food that tick. */
class KarambwanComboTests {
    private fun player(): Player {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.definitions.get(ItemDef::class.java, any<Int>()) } returns mockk<ItemDef>(relaxed = true)
        every { player.world } returns world
        every { player.timers } returns TimerMap()
        return player
    }

    @Test
    fun `karambwan then shark is refused`() {
        val p = player()
        Foods.eat(p, Food.KARAMBWAN)
        assertTrue(p.timers.has(FOOD_DELAY))
        assertFalse(Foods.canEat(p, Food.SHARK))
    }

    @Test
    fun `shark then potion then karambwan is allowed`() {
        val p = player()
        Foods.eat(p, Food.SHARK)
        assertFalse(Foods.canEat(p, Food.SHARK))
        // A potion only checks POTION_DELAY; drinking it sets both delays (Potions.drinkAt).
        assertFalse(p.timers.has(POTION_DELAY))
        p.timers[POTION_DELAY] = 3
        p.timers[FOOD_DELAY] = 3
        assertTrue(Foods.canEat(p, Food.KARAMBWAN))
    }
}
