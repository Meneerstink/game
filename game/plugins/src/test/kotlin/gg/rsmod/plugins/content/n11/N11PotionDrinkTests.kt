package gg.rsmod.plugins.content.n11

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.POTION_DELAY
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.potion.*
import io.mockk.every
import io.mockk.verify
import kotlin.test.*

class N11PotionDrinkTests {
    private fun prepare(f: N11Fixture) {
        every { f.definitions.getAll<gg.rsmod.game.fs.def.ItemDef>(gg.rsmod.game.fs.def.ItemDef::class.java) } answers { f.defs }
        Potion.values().forEach { f.menu(it.item, "Drink", "", "Empty") }
        listOf(4842, 4844, 4846, 4848).forEachIndexed { index, id -> f.def(id).name = "Relicym's balm (${4 - index})" }
    }

    @Test fun `compiled balm Drink consumes all doses to vial and reports dose or finish`() {
        N11Fixture().use { f ->
            prepare(f)
            val messages = mutableListOf<String>()
            every { f.player.write(*varargAll<Message> { (it as? MessageGameMessage)?.let { m -> messages.add(m.message) }; true }) } returns Unit
            f.load("items.potion.PotionDrinking_plugin")
            f.inventory[0] = Item(Items.RELICYMS_BALM_4)
            val chain = listOf(4842, 4844, 4846, 4848, Items.VIAL)
            chain.zipWithNext().forEachIndexed { index, (id, next) ->
                assertTrue(f.repo.executeItem(f.player, id, 1))
                assertEquals(next, f.inventory[0]?.id)
                assertTrue(messages.contains("You drink some of your relicym's balm."))
                assertTrue(messages.contains(if (next == Items.VIAL) "You have finished your potion." else if (index == 2) "You have 1 dose of potion left." else "You have ${3 - index} doses of potion left."))
                assertTrue(f.timers.has(POTION_DELAY))
                f.timers.remove(POTION_DELAY)
            }
            verify(exactly = 0) { f.skills.alterCurrentLevel(any(), any(), any()) }
            verify(exactly = 0) { f.player.alterLifepoints(any(), any()) }
        }
    }

    @Test fun `rapid clicks and stale slots cannot spend two doses or consume a noted balm`() {
        N11Fixture().use { f ->
            prepare(f)
            f.inventory[0] = Item(4844)
            f.inventory[1] = Item(4844)
            Potions.drinkAt(f.player, Potion.RELICYMS_BALM3, 0)
            Potions.drinkAt(f.player, Potion.RELICYMS_BALM3, 0)
            Potions.drinkAt(f.player, Potion.RELICYMS_BALM3, 1)
            Potions.drinkAt(f.player, Potion.RELICYMS_BALM2, 0)
            assertEquals(4846, f.inventory[0]?.id)
            assertEquals(4844, f.inventory[1]?.id)
            f.timers.remove(POTION_DELAY)
            f.inventory[0] = Item(4845)
            Potions.drinkAt(f.player, Potion.RELICYMS_BALM3, 0)
            assertEquals(4845, f.inventory[0]?.id)
            assertEquals(4844, f.inventory[1]?.id)
            assertFalse(f.timers.has(POTION_DELAY))
            assertTrue(Potion.values().none { it.item in listOf(4843, 4845, 4847, 4849, Items.VIAL) })
            assertTrue(Potion.values().none { it.item in RemovedPotions.itemIds })
        }
    }

    @Test fun `compiled blamish oil refuses with Wiki dialogue and keeps oil without delay`() {
        N11Fixture().use { f ->
            f.menu(Items.BLAMISH_OIL, "Drink")
            f.load("items.food.Consumables_plugin")
            val oil = Item(Items.BLAMISH_OIL)
            f.inventory[0] = oil
            assertTrue(f.repo.executeItem(f.player, oil.id, 1))
            f.queues.cycle()
            assertSame(oil, f.inventory[0])
            assertFalse(f.timers.has(POTION_DELAY))
            verify { f.player.write(*varargAny<Message> { it is gg.rsmod.game.message.impl.IfSetTextMessage && it.text == "You know, I'd really rather not." }) }
            verify(exactly = 0) { f.player.animate(any(), any()) }
            verify(exactly = 0) { f.player.alterLifepoints(any(), any()) }
        }
    }
}
