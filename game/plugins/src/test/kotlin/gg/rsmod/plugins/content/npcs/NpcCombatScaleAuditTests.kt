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
 * Regression coverage for the runtime audit. Runtime definitions use real 1:1 hitpoints; the
 * historical x10 source conversion is tested at the DSL and bulk-loader boundaries instead.
 */
class NpcCombatScaleAuditTests {
    @Test
    fun `passes when every runtime lifepoints value is positive`() {
        val world = worldWith(mapOf(1 to combatDef(25), 2 to combatDef(1), 3 to combatDef(255)))

        NpcCombatScaleAudit.validate(world)
    }

    @Test
    fun `accepts a non-multiple runtime lifepoints value`() {
        val world = worldWith(mapOf(7344 to combatDef(2255)))

        NpcCombatScaleAudit.validate(world)
    }

    @Test
    fun `fails when lifepoints is zero or negative`() {
        val world = worldWith(mapOf(100 to combatDef(0), 200 to combatDef(-10)))

        val error = assertFailsWith<IllegalStateException> { NpcCombatScaleAudit.validate(world) }
        assertTrue(error.message!!.contains("100"))
        assertTrue(error.message!!.contains("200"))
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
