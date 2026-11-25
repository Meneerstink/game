package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.attr.INTERACTING_ITEM
import gg.rsmod.game.model.attr.OTHER_ITEM_ATTR
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.n11.N11Fixture
import java.lang.ref.WeakReference
import kotlin.test.*

class MaxCapeCombinationTests {
    private fun click(f: N11Fixture, first: Item, second: Item) {
        f.attrs[INTERACTING_ITEM] = WeakReference(first)
        f.attrs[OTHER_ITEM_ATTR] = WeakReference(second)
        assertTrue(f.repo.executeItemOnItem(f.player, first.id, second.id))
        f.queues.cycle()
    }

    private fun confirm(f: N11Fixture, yes: Boolean = true) {
        f.queues.submitReturnValue(ResumePauseButtonMessage(94, if (yes) 3 else 4, if (yes) 3 else 4))
        f.queues.cycle()
    }

    private fun parts(f: N11Fixture, variant: MaxCapes.Variant) {
        f.inventory[0] = Item(variant.component).also { it.attr[ItemAttribute.CHARGES] = 123 }
        f.inventory[1] = Item(MaxCapes.MAX_CAPE)
        f.inventory[2] = Item(MaxCapes.MAX_HOOD)
        f.inventory[3] = Item(Items.KNIFE)
        variant.tool?.let { f.inventory[4] = Item(it) }
    }

    @Test fun `all variants combine in a full backpack and knife restores exactly one of each component`() {
        for (variant in MaxCapes.VARIANTS) N11Fixture().use { f ->
            f.load("items.osrs.Osrs_max_capes_plugin")
            repeat(28) { f.inventory[it] = Item(Items.KNIFE) }
            parts(f, variant)
            click(f, f.inventory[0]!!, f.inventory[1]!!)
            confirm(f)
            assertEquals(27, f.inventory.occupiedSlotCount, "combine ${variant.cape}")
            assertEquals(variant.cape, f.inventory[0]?.id)
            assertEquals(variant.hood, f.inventory[2]?.id)
            assertEquals(123, f.inventory[0]?.attr?.get(ItemAttribute.CHARGES))
            f.queues.terminateTasks() // close the informational item message
            click(f, f.inventory[3]!!, f.inventory[0]!!)
            assertEquals(28, f.inventory.occupiedSlotCount, "split ${variant.cape}")
            assertEquals(1, f.inventory.getItemCount(variant.component))
            assertEquals(1, f.inventory.getItemCount(MaxCapes.MAX_CAPE))
            assertEquals(1, f.inventory.getItemCount(MaxCapes.MAX_HOOD))
            assertEquals(123, f.inventory[0]?.attr?.get(ItemAttribute.CHARGES))
            assertEquals(0, f.inventory.getItemCount(variant.cape))
        }
    }

    @Test fun `cancelling or replacing any confirmed input does not consume another copy`() {
        val variant = MaxCapes.forCape(Items.DIZANAS_MAX_CAPE)!!
        for (changed in -1..2) N11Fixture().use { f ->
            f.load("items.osrs.Osrs_max_capes_plugin")
            parts(f, variant)
            val originals = (0..2).map { f.inventory[it]!! }
            click(f, originals[0], originals[1])
            if (changed >= 0) f.inventory[changed] = Item(originals[changed].id)
            confirm(f, yes = changed >= 0)
            assertEquals(0, f.inventory.getItemCount(variant.cape))
            assertEquals(1, f.inventory.getItemCount(variant.component))
            assertEquals(1, f.inventory.getItemCount(MaxCapes.MAX_CAPE))
            assertEquals(1, f.inventory.getItemCount(MaxCapes.MAX_HOOD))
            for (slot in 0..2) if (slot != changed) assertSame(originals[slot], f.inventory[slot])
        }
    }

    @Test fun `combination refuses missing hood and needle without touching the clicked items`() {
        val variant = MaxCapes.forCape(Items.MASORI_ASSEMBLER_MAX_CAPE)!!
        for (missing in listOf(2, 4)) N11Fixture().use { f ->
            f.load("items.osrs.Osrs_max_capes_plugin")
            parts(f, variant)
            f.inventory[missing] = null
            val component = f.inventory[0]!!
            val cape = f.inventory[1]!!
            click(f, component, cape)
            assertSame(component, f.inventory[0])
            assertSame(cape, f.inventory[1])
            assertEquals(0, f.inventory.getItemCount(variant.cape))
        }
    }

    @Test fun `knife refuses a full backpack and repeated knife clicks cannot duplicate the cape`() {
        val variant = MaxCapes.forCape(Items.FIRE_MAX_CAPE)!!
        N11Fixture().use { f ->
            f.load("items.osrs.Osrs_max_capes_plugin")
            repeat(28) { f.inventory[it] = Item(Items.KNIFE) }
            val cape = Item(variant.cape)
            f.inventory[0] = cape
            f.inventory[1] = Item(variant.hood)
            click(f, f.inventory[2]!!, cape)
            assertSame(cape, f.inventory[0])
            f.inventory[27] = null
            click(f, f.inventory[2]!!, cape)
            repeat(5) { click(f, f.inventory[2]!!, cape) }
            assertEquals(1, f.inventory.getItemCount(variant.component))
            assertEquals(1, f.inventory.getItemCount(MaxCapes.MAX_CAPE))
            assertEquals(1, f.inventory.getItemCount(MaxCapes.MAX_HOOD))
            assertEquals(28, f.inventory.occupiedSlotCount)
        }
    }

    @Test fun `Masori kit alternative consumes one kit converts both items and leaves the needle`() {
        N11Fixture().use { f ->
            f.load("items.osrs.Osrs_max_capes_plugin")
            repeat(28) { f.inventory[it] = Item(Items.KNIFE) }
            f.inventory[0] = Item(Items.MASORI_CRAFTING_KIT)
            f.inventory[1] = Item(Items.ASSEMBLER_MAX_CAPE)
            f.inventory[2] = Item(Items.ASSEMBLER_MAX_HOOD)
            f.inventory[3] = Item(Items.NEEDLE)
            click(f, f.inventory[0]!!, f.inventory[1]!!)
            assertEquals(27, f.inventory.occupiedSlotCount)
            assertEquals(Items.MASORI_ASSEMBLER_MAX_CAPE, f.inventory[1]?.id)
            assertEquals(Items.MASORI_ASSEMBLER_MAX_HOOD, f.inventory[2]?.id)
            assertEquals(Items.NEEDLE, f.inventory[3]?.id)
            assertEquals(0, f.inventory.getItemCount(Items.MASORI_CRAFTING_KIT))
        }
    }
}
