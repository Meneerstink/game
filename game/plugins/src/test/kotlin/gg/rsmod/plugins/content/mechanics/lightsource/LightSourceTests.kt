package gg.rsmod.plugins.content.mechanics.lightsource

import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N419: every light source is lit from its filled, unlit item - never from an empty lamp or lantern. */
class LightSourceTests {
    private val empties =
        setOf(Items.CANDLE_LANTERN, Items.OIL_LANTERN, Items.OIL_LAMP_4525, Items.BULLSEYE_LANTERN_4546, Items.SAPPHIRE_LANTERN)

    @Test
    fun `no light source is lit from an empty lamp or lantern`() {
        LightSource.values().forEach { assertTrue(it.raw !in empties, "${it.name} lights from the empty item ${it.raw}") }
        assertEquals(Items.CANDLE_LANTERN_4529, LightSource.CANDLE_LANTERN.raw)
        assertEquals(Items.OIL_LANTERN_4537, LightSource.OIL_LANTERN.raw)
    }

    @Test
    fun `raw and lit items are all different`() {
        val raws = LightSource.values().map { it.raw }
        val lit = LightSource.values().map { it.product }
        assertEquals(raws.size, raws.toSet().size)
        assertEquals(lit.size, lit.toSet().size)
        assertTrue(raws.none { it in lit })
    }
}
