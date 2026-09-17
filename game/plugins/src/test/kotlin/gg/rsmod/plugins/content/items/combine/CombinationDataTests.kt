package gg.rsmod.plugins.content.items.combine

import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.Test
import kotlin.test.assertEquals

/** OSRS-IMPORT additions to [CombinationData]: sourced recipes newly wired onto the existing generic combine engine. */
class CombinationDataTests {
    /**
     * OSRS Wiki "Heavy ballista": a 3-step assembly (limbs+frame, +spring, +monkey tail) handled by
     * `heavy_ballista.plugin.kts`, so there must be no single-click generic combine for it. The
     * ornament kit combine is the only ballista row that belongs on the generic engine.
     */
    @Test
    fun `heavy ballista is not a single generic combine, only its ornament kit is`() {
        assertEquals(
            emptyList(),
            CombinationData.values.filter { it.resultItem == Items.HEAVY_BALLISTA },
        )
        val ornament = CombinationData.HEAVY_BALLISTA_OR
        assertEquals(setOf(Items.HEAVY_BALLISTA_ORNAMENT_KIT, Items.HEAVY_BALLISTA), ornament.items.toSet())
        assertEquals(Items.HEAVY_BALLISTA_OR, ornament.resultItem)
    }

    @Test
    fun `every CombinationData row is bindable - items is never empty and resultItem is never one of its own inputs`() {
        CombinationData.values.forEach { def ->
            assert(def.items.isNotEmpty()) { "${def.name} has no input items" }
            assert(def.resultItem !in def.items) { "${def.name} would consume and produce the same item" }
        }
    }
}
