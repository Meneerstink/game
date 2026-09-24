package gg.rsmod.plugins.content.skills.mining

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Objs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Desert Quarry (2026-09-24): sandstone and granite rocks mine by size, largest first, with the 667 Void levels and experience. */
class QuarryRockTests {
    @Test
    fun `sandstone and granite are minable with every size and its experience`() {
        assertEquals(35, RockType.SANDSTONE.level)
        assertEquals(45, RockType.GRANITE.level)
        assertEquals(
            listOf(Items.SANDSTONE_10KG to 60.0, Items.SANDSTONE_5KG to 50.0, Items.SANDSTONE_2KG to 40.0, Items.SANDSTONE_1KG to 30.0),
            RockType.SANDSTONE.products.map { it.item to it.experience },
        )
        assertEquals(
            listOf(Items.GRANITE_5KG to 75.0, Items.GRANITE_2KG to 60.0, Items.GRANITE_500G to 50.0),
            RockType.GRANITE.products.map { it.item to it.experience },
        )
        assertTrue(Objs.ROCKS_10946 in RockType.objects && Objs.ROCKS_10947 in RockType.objects)
        RockType.values.filter { it.products.isNotEmpty() }.forEach { rock ->
            assertTrue(rock.products.zipWithNext().all { (a, b) -> a.highChance <= b.highChance }, "${rock.name}: largest (rarest) first")
        }
    }
}
