package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.plugins.api.Skills
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * RS Wiki "Geyser titan": "When spoken with, the familiar grants a 1 + 3% (rounded down) Ranged temporary level boost."
 * (2011 Knowledge Base: "Ranged Boost (1 + 3% of your level)"). Visible, on Interact - never an invisible boost.
 */
class FamiliarInteractBoostTests {
    @Test
    fun `geyser titan interact boost is 1 plus 3 percent of the base Ranged level, rounded down`() {
        assertEquals(99 + 1 + 2, SummoningBoosts.interactBoostLevel(SummoningPouchData.GEYSER_TITAN, Skills.RANGED, 99))
        assertEquals(70 + 1 + 2, SummoningBoosts.interactBoostLevel(SummoningPouchData.GEYSER_TITAN, Skills.RANGED, 70))
        assertEquals(1 + 1 + 0, SummoningBoosts.interactBoostLevel(SummoningPouchData.GEYSER_TITAN, Skills.RANGED, 1))
        assertNull(SummoningBoosts.interactBoostLevel(SummoningPouchData.GEYSER_TITAN, Skills.MAGIC, 99))
    }

    @Test
    fun `only the geyser titan gives an interact boost and none of it is invisible`() {
        val boosting = SummoningPouchData.values.filter { pouch -> (0 until 25).any { SummoningBoosts.interactBoostLevel(pouch, it, 99) != null } }
        assertEquals(listOf(SummoningPouchData.GEYSER_TITAN), boosting)
        assertEquals(false, SummoningPouchData.GEYSER_TITAN.npc in SummoningBoosts.boostedFamiliars)
    }
}
