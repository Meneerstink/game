package gg.rsmod.plugins.content.magic.jewelleryenchanting

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.magic.*
import io.mockk.verify
import kotlin.test.*

class JewelleryEnchantmentTests {
    private fun raw(f: N72Fixture, data: EnchantmentData, slot: Int = 0) {
        f.f.def(data.raw).name = data.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        f.inventory[slot] = Item(data.raw)
    }

    @Test fun `all four zenyte conversions consume exact runes and grant one110XP drop`() {
        for (data in EnchantmentData.values().filter { it.spell == SpellbookData.LVL_7_ENCHANT }) N72Fixture().use { f ->
            raw(f, data); f.runes(data.spell)
            assertTrue(JewelleryEnchantment.cast(f.player, data.spell, 0, data.raw))
            assertEquals(data.product, f.inventory[0]?.id)
            data.spell.runes.forEach { assertEquals(0, f.inventory.getItemCount(it.id)) }
            verify(exactly = 1) { f.player.addXp(Skills.MAGIC, 110.0) }
            verify { f.player.animate(721); f.player.graphic(any<gg.rsmod.game.model.Graphic>()) }
            assertFalse(JewelleryEnchantment.cast(f.player, data.spell, 0, data.raw))
        }
    }

    @Test fun `wrong book low level missing runes wrong target note and shifted import cannot consume anything`() {
        for (failure in 0..5) N72Fixture(if (failure == 0) Spellbook.ANCIENT else Spellbook.STANDARD).use { f ->
            val data = EnchantmentData.ZENYTE_RING
            raw(f, data); f.runes(data.spell)
            when (failure) {
                1 -> f.skills.setCurrentLevel(Skills.MAGIC, 92)
                2 -> f.inventory.remove(Items.SOUL_RUNE, 1)
                3 -> f.inventory[0] = Item(Items.GOLD_RING)
                4 -> f.f.def(data.raw).noteTemplateId = 799
                5 -> f.f.def(data.raw).name = "Some other imported ring"
            }
            val before = f.inventory.toMap()
            assertFalse(JewelleryEnchantment.cast(f.player, data.spell, 0, f.inventory[0]!!.id), "case$failure")
            assertEquals(before, f.inventory.toMap())
            verify(exactly = 0) { f.player.addXp(any(), any<Double>()) }
        }
    }

    @Test fun `full inventory and carried rune pouch work and a second same tick cast is refused`() {
        N72Fixture().use { f ->
            val data = EnchantmentData.ZENYTE_AMULET
            raw(f, data); raw(f, data, 1)
            f.inventory[2] = RunePouch.withContents(Item(Items.RUNE_POUCH), data.spell.runes.map { Item(it.id, it.amount * 2) })
            for (i in 3..27) f.inventory[i] = Item(Items.KNIFE)
            assertTrue(JewelleryEnchantment.cast(f.player, data.spell, 0, data.raw))
            assertFalse(JewelleryEnchantment.cast(f.player, data.spell, 1, data.raw))
            assertEquals(data.raw, f.inventory[1]?.id)
            assertEquals(28, f.inventory.occupiedSlotCount)
            assertEquals(data.spell.runes.map { it.id to it.amount }.toMap(), RunePouch.contents(f.inventory[2]!!).associate { it.id to it.amount })
        }
    }

    @Test fun `automatic enchanting rechecks the book and stops before a second payment`() {
        N72Fixture().use { f ->
            val data = EnchantmentData.ZENYTE_NECKLACE
            raw(f, data); raw(f, data, 1); f.runes(data.spell, 2)
            f.player.queue(TaskPriority.STRONG) { with(JewelleryEnchantment) { enchant(data.spell, 0) } }
            f.tick()
            assertEquals(data.product, f.inventory[0]?.id)
            f.player.setSpellbook(Spellbook.LUNAR)
            f.tick(9)
            assertEquals(data.raw, f.inventory[1]?.id)
            assertEquals(1, f.inventory.getItemCount(Items.COSMIC_RUNE))
            verify(exactly = 1) { f.player.addXp(Skills.MAGIC, 110.0) }
        }
    }

    @Test fun `native and submenu item packets use the same guarded cast`() {
        for ((parent, child) in listOf(192 to 102, JewelleryEnchantment.INTERFACE to 9)) N72Fixture().use { f ->
            val data = EnchantmentData.ZENYTE_BRACELET
            raw(f, data); f.runes(data.spell)
            f.f.load("magic.jewelleryenchanting.Enchantment_plugin")
            f.f.select(0)
            assertTrue(f.f.repo.executeSpellOnItem(f.player, (parent shl 16) or child))
            f.tick()
            assertEquals(data.product, f.inventory[0]?.id)
        }
    }

    @Test fun `submenu sends native inventory targeting masks for all seven spells`() {
        N72Fixture().use { f ->
            JewelleryEnchantment.open(f.player)
            for (component in 3..9) verify {
                f.player.write(gg.rsmod.game.message.impl.IfSetEventsMessage(
                    (JewelleryEnchantment.INTERFACE shl 16) or component, -1, -1, 0x010000))
            }
        }
    }
}
