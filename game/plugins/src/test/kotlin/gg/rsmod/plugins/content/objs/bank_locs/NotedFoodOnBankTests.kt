package gg.rsmod.plugins.content.objs.bank_locs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.FOOD_DELAY
import gg.rsmod.game.model.timer.POTION_DELAY
import gg.rsmod.plugins.content.items.food.Food
import gg.rsmod.plugins.content.mechanics.deadman.DeadmanPlayerFixture
import gg.rsmod.plugins.content.mechanics.pvp.BankSecurity
import io.mockk.every
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** DMM-ARMAGEDDON D6 (owner 20-11-2026): noted food on a bank -> unnoted food + the post-bank eat/drink delay. */
class NotedFoodOnBankTests {
    private val food = Food.SHARK.item
    private val note = 90_001
    private val logsNote = 90_003

    private fun fixture(at: Tile = Tile(3100, 3600, 0)): DeadmanPlayerFixture =
        DeadmanPlayerFixture().also { f ->
            f.define(food)
            f.define(note, stackable = true).also { it.noteTemplateId = 799; it.noteLinkId = food }
            f.define(1511)
            f.define(logsNote, stackable = true).also { it.noteTemplateId = 799; it.noteLinkId = 1511 }
            every { f.definitions.getNullable(ItemDef::class.java, any()) } answers { f.defs[secondArg()] }
            every { f.player.tile } returns at
            every { f.player.inTransit() } returns false
        }

    @Test
    fun `noted food fills the free slots, the rest stays noted, and eating is blocked like after banking`() {
        val f = fixture()
        f.inventory.add(note, 40)
        f.inventory.add(1511, 20) // 20 slots used: 7 free + the note's own slot is kept (notes left over)
        assertEquals(NotedFoodOnBank.Result.UNNOTED, NotedFoodOnBank.use(f.player, Item(note, 40)))
        assertEquals(7L, f.count(food))
        assertEquals(33L, f.count(note))
        assertTrue(f.player.timers.has(FOOD_DELAY) && f.player.timers[FOOD_DELAY] >= BankSecurity.POST_BANK_CONSUME_BLOCK_TICKS)
        assertTrue(f.player.timers.has(POTION_DELAY))
    }

    @Test
    fun `a stack that fits is unnoted completely, using the note's own slot`() {
        val f = fixture()
        f.inventory.add(note, 28)
        assertEquals(NotedFoodOnBank.Result.UNNOTED, NotedFoodOnBank.use(f.player, Item(note, 28)))
        assertEquals(28L, f.count(food))
        assertEquals(0L, f.count(note))
    }

    @Test
    fun `a full inventory or a noted non-food item changes nothing`() {
        val f = fixture()
        f.inventory.add(note, 5)
        f.inventory.add(1511, 27)
        assertEquals(NotedFoodOnBank.Result.NO_SPACE, NotedFoodOnBank.use(f.player, Item(note, 5)))
        assertEquals(5L, f.count(note))
        val g = fixture()
        g.inventory.add(logsNote, 5)
        assertEquals(NotedFoodOnBank.Result.NOT_NOTED_FOOD, NotedFoodOnBank.use(g.player, Item(logsNote, 5)))
        assertEquals(5L, g.count(logsNote))
        assertFalse(g.player.timers.has(FOOD_DELAY))
    }
}
