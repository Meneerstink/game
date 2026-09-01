package gg.rsmod.plugins.content.skills.summoning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SummoningCombatDefinitionTests {
    @Test
    fun `combat ledger covers every familiar and every fighting row is executable`() {
        SummoningCombatDefinitions.validate()
        assertEquals(78, SummoningCombatDefinitions.values.size)
        assertEquals(73, SummoningCombatDefinitions.combatValues.size)
        assertEquals(5, SummoningCombatDefinitions.values.count { !it.canFight })
        assertEquals(72, SummoningCombatDefinitions.executableCombatValues.size)
        assertEquals(SummoningPouchData.ALBINO_RAT, SummoningCombatDefinitions.blockedCombatValues.single().pouch)
        assertTrue(SummoningCombatDefinitions.executableCombatValues.all { it.maxHit > 0 && it.attackAnimation >= 0 })
    }

    @Test
    fun `official defensive-only and non-combat exceptions remain explicit`() {
        val defensive = setOf(
            SummoningPouchData.VOID_SPINNER,
            SummoningPouchData.BUNYIP,
            SummoningPouchData.UNICORN_STALLION,
            SummoningPouchData.PACK_YAK,
        )
        assertEquals(defensive, SummoningCombatDefinitions.values.filter { it.assistMode == FamiliarAssistMode.DEFENSIVE_ONLY }.map { it.pouch }.toSet())

        val nonCombat = setOf(
            SummoningPouchData.BEAVER,
            SummoningPouchData.MACAW,
            SummoningPouchData.MAGPIE,
            SummoningPouchData.IBIS,
            SummoningPouchData.FRUIT_BAT,
        )
        assertEquals(nonCombat, SummoningCombatDefinitions.values.filter { !it.canFight }.map { it.pouch }.toSet())
        assertFalse(SummoningCombatDefinitions.get(SummoningPouchData.PACK_YAK).assistMode == FamiliarAssistMode.ASSIST)
    }

    @Test
    fun `named combat representatives use their sourced style and data`() {
        val dreadfowl = SummoningCombatDefinitions.get(SummoningPouchData.DREADFOWL)
        assertEquals(FamiliarAttackStyle.MELEE, dreadfowl.style)
        assertTrue(dreadfowl.maxHit > 0)

        val steelTitan = SummoningCombatDefinitions.get(SummoningPouchData.STEEL_TITAN)
        assertEquals(FamiliarAttackStyle.RANGED, steelTitan.style)
        assertEquals(7, steelTitan.attackRange)
        assertTrue(steelTitan.projectile > 0)
        assertTrue(steelTitan.attackGraphic > 0)
    }
}
