package gg.rsmod.plugins.content.npcs

import gg.rsmod.game.model.World
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.plugin.PluginRepository
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Regression coverage for the audit that caught the Ardougne Watchmen roster (six npcs, real wiki
 * hitpoints entered without the *10 conversion), the Barrows brothers (same defect), and Kree'arra
 * (`2255` instead of `2250`) - see `NpcCombatScaleAudit`'s own doc comment for the full mechanism.
 */
class NpcCombatScaleAuditTests {
    @Test
    fun `passes when every lifepoints value is a positive multiple of ten`() {
        val world = worldWith(mapOf(1 to combatDef(250), 2 to combatDef(10), 3 to combatDef(2550)))

        NpcCombatScaleAudit.validate(world)
    }

    @Test
    fun `fails and names the offending npc when lifepoints is not a multiple of ten`() {
        val world = worldWith(mapOf(7344 to combatDef(2255)))

        val error = assertFailsWith<IllegalStateException> { NpcCombatScaleAudit.validate(world) }
        assertTrue(error.message!!.contains("7344"), "must name the offending npc id, not just fail generically")
        assertTrue(error.message!!.contains("2255"))
    }

    @Test
    fun `fails when lifepoints is zero or negative`() {
        val world = worldWith(mapOf(100 to combatDef(0), 200 to combatDef(-10)))

        val error = assertFailsWith<IllegalStateException> { NpcCombatScaleAudit.validate(world) }
        assertTrue(error.message!!.contains("100"))
        assertTrue(error.message!!.contains("200"))
    }

    @Test
    fun `reproduces the real Ardougne Watchmen and Barrows regressions this audit was built for`() {
        // Values exactly as found before the fix: real wiki HP entered with no *10 conversion.
        val world =
            worldWith(
                mapOf(
                    1 to combatDef(20), // Warrior woman
                    2 to combatDef(57), // Paladin
                    3 to combatDef(82), // Hero
                    4 to combatDef(52), // Knight of Ardougne
                    5 to combatDef(22), // Watchman
                    6 to combatDef(50), // Archer
                    7 to combatDef(255), // any Barrows brother, pre-fix
                ),
            )

        assertFailsWith<IllegalStateException> { NpcCombatScaleAudit.validate(world) }
    }

    private fun worldWith(defs: Map<Int, NpcCombatDef>): World {
        val world = mockk<World>(relaxed = true)
        val plugins = mockk<PluginRepository>(relaxed = true)
        every { world.plugins } returns plugins
        every { plugins.allNpcCombatDefs() } returns defs
        return world
    }

    private fun combatDef(lifepoints: Int): NpcCombatDef = NpcCombatDef.DEFAULT.copy(lifepoints = lifepoints)
}
