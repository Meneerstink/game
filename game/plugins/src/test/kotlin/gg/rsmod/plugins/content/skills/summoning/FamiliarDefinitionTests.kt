package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.plugins.api.cfg.Npcs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Deterministic schema checks for revision-667 familiar NPC mappings. */
class FamiliarDefinitionTests {
    @Test
    fun `roster keeps the corrected revision 667 familiar mappings`() {
        assertEquals(78, SummoningPouchData.values.size)
        assertEquals(Npcs.SPIRIT_TZKIH, SummoningPouchData.SPIRIT_TZ_KIH.npc)
        assertEquals(Npcs.VOID_SHIFTER, SummoningPouchData.VOID_SHIFTER.npc)
        assertEquals(Npcs.VOID_SPINNER, SummoningPouchData.VOID_SPINNER.npc)
        assertEquals(Npcs.PHOENIX_8575, SummoningPouchData.PHOENIX.npc)
        assertTrue(SummoningPouchData.values.all { it.npc > 0 })
        SummoningFamiliarDefinitions.validate()
        assertTrue(SummoningPouchData.values.all { SummoningFamiliarDefinitions.get(it).summonPoints > 0 && SummoningFamiliarDefinitions.get(it).durationMinutes > 0 })
    }
}
