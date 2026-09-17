package gg.rsmod.plugins.content.skills.fletching.crossbows

import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT dragon crossbow (OSRS Wiki "Dragon crossbow", fetched 2026-09-16): stock/limbs/stringing all require
 * Fletching 78; "cut a magic stock ... granting 70 Fletching experience"; "add dragon limbs to the stock with a
 * hammer in their inventory, granting 135 experience"; "string the crossbow with a crossbow string, granting 70
 * experience". Only the dragon tier needs a hammer - every other tier is unaffected.
 */
class CrossbowDataTests {
    @Test
    fun `the dragon crossbow tier matches the sourced materials, levels and experience, and is the only tier requiring a hammer`() {
        val dragon = CrossbowData.DRAGON
        assertEquals(Items.MAGIC_STOCK, dragon.stock)
        assertEquals(Items.DRAGON_LIMBS, dragon.limbs)
        assertEquals(Items.DRAGON_CROSSBOW_U, dragon.unstrung)
        assertEquals(Items.DRAGON_CROSSBOW, dragon.strung)
        assertEquals(78, dragon.assembleLevelRequirement)
        assertEquals(135.0, dragon.assembleExperience)
        assertEquals(78, dragon.stringLevelRequirement)
        assertEquals(70.0, dragon.stringExperience)
        assertTrue(dragon.requiresHammer)

        assertEquals(
            emptyList<CrossbowData>(),
            CrossbowData.values.filter { it != CrossbowData.DRAGON && it.requiresHammer },
            "no other tier should require a hammer",
        )
    }

    @Test
    fun `every tier's stock, limbs, unstrung and strung ids are distinct and correctly indexed`() {
        assertEquals(CrossbowData.values.size, CrossbowData.byUnstrung.size, "no two tiers share an unstrung id")
        assertEquals(CrossbowData.values.size, CrossbowData.byStrung.size, "no two tiers share a strung id")
        assertEquals(CrossbowData.DRAGON, CrossbowData.byUnstrung[Items.DRAGON_CROSSBOW_U])
        assertEquals(CrossbowData.DRAGON, CrossbowData.byStrung[Items.DRAGON_CROSSBOW])
        assertFalse(CrossbowData.byStrung.containsKey(Items.ZARYTE_CROSSBOW), "the Zaryte upgrade is not a fletched tier")
    }
}
