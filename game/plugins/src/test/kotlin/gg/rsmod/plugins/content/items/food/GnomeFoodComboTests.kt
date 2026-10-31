package gg.rsmod.plugins.content.items.food

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OSRS Wiki "Fast foods" (2026-10-31): every gnome dish - batta, crunchies, bowl, its premade copy and its 667 alias - and toad's legs
 * combo-eat with a 2-tick attack delay and a 3-tick eat delay. N11 bundle 5 added the aliases that way; the originals were plain food.
 */
class GnomeFoodComboTests {
    private val gnome = Regex("BATTA|CRUN|WORM_HOLE|VEG_BALL|CHOC_BOMB|CHOCOLATE_BOMB|TOADS_LEGS|PREMADE_TTL")

    @Test
    fun `every gnome dish and toad's legs is a fast combo food`() {
        val dishes = Food.values().filter { gnome.containsMatchIn(it.name) }
        assertEquals(39, dishes.size, dishes.joinToString { it.name })
        val wrong = dishes.filterNot { it.comboFood && it.attackDelay == 2 && it.tickDelay == 3 }
        assertEquals(emptyList(), wrong.map { it.name })
    }

    @Test
    fun `toad crunchies heal 8 like their premade and 667 copies`() {
        assertEquals(80, Food.TOAD_CRUNCHIES.heal)
        assertEquals(80, Food.PREMADE_TD_CRUNCH.heal)
        assertEquals(80, Food.TOAD_CRUNCHIES_9538.heal)
    }
}
