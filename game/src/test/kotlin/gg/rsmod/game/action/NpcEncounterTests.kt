package gg.rsmod.game.action

import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.NpcEncounter
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.plugin.PluginRepository
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class NpcEncounterTests {
    private fun npc(id: Int, complete: Boolean, xp: Double): Npc = mockk<Npc>(relaxed = true).also {
        every { it.id } returns id
        every { it.attr } returns AttributeMap()
        every { it.combatDef } returns NpcCombatDef.DEFAULT.copy(completesEncounter = complete, slayerXp = xp)
    }

    @Test fun `intermediate forms produce no reward event and final form produces one even with duplicate delivery`() {
        val world = mockk<World>(relaxed = true)
        val repo = PluginRepository(world)
        val killer = mockk<Player>(relaxed = true)
        var awards = 0
        repo.bindNpcCompleted { _, _ -> awards++ }
        repo.executeNpcCompleted(killer, npc(1, false, 312.0))
        assertEquals(0, awards)
        val final = npc(2, true, 312.0)
        repo.executeNpcCompleted(killer, final)
        repo.executeNpcCompleted(killer, final)
        assertEquals(1, awards)
        final.attr.clear() // The same object after normal engine respawn.
        repo.executeNpcCompleted(killer, final)
        assertEquals(2, awards)
    }

    @Test fun `both phases retain Slayer XP and timeout cycling cannot farm extra XP`() {
        val normal = npc(1, false, 312.0)
        val final = npc(2, true, 312.0)
        NpcEncounter.transfer(normal, final)
        assertEquals(624.0, NpcEncounter.slayerXp(final))
        val reverted = npc(1, false, 312.0)
        NpcEncounter.transfer(final, reverted)
        val finalAgain = npc(2, true, 312.0)
        NpcEncounter.transfer(reverted, finalAgain)
        assertEquals(624.0, NpcEncounter.slayerXp(finalAgain))
    }

    @Test fun `a failed optional reward cannot suppress the next consumer or cause duplicate rewards`() {
        val repo = PluginRepository(mockk<World>(relaxed = true))
        var awarded = 0
        repo.bindNpcCompleted { _, _ -> error("injected consumer failure") }
        repo.bindNpcCompleted { _, _ -> awarded++ }
        val final = npc(2, true, 0.0)
        repo.executeNpcCompleted(mockk(relaxed = true), final)
        repo.executeNpcCompleted(mockk(relaxed = true), final)
        assertEquals(1, awarded)
    }
}
