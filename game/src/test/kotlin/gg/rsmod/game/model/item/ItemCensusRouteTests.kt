package gg.rsmod.game.model.item

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N11: the item census follows the routes the inventory really takes (IfButton1Handler / OpHeld*). */
class ItemCensusRouteTests {
    @Test
    fun `Wear only equips from op 2 - a Wear on op 1 is a dead option (Ring of shadows, N422)`() {
        assertTrue(ItemCensus.engineRoute(2, "Wear"))
        assertTrue(ItemCensus.engineRoute(2, "Wield"))
        assertFalse(ItemCensus.engineRoute(1, "Wear"))
    }

    @Test
    fun `Drop only on op 5, Use and Examine anywhere`() {
        assertTrue(ItemCensus.engineRoute(5, "Drop"))
        assertFalse(ItemCensus.engineRoute(3, "Drop"))
        assertTrue(ItemCensus.engineRoute(4, "Use"))
        assertFalse(ItemCensus.engineRoute(5, "Destroy"))
    }

    @Test
    fun `a fifth-slot Destroy is plugin option 10`() {
        assertEquals(10, ItemCensus.pluginOption(5, "Destroy"))
        assertEquals(3, ItemCensus.pluginOption(3, "Destroy"))
        assertEquals(1, ItemCensus.pluginOption(1, "Eat"))
    }
}
