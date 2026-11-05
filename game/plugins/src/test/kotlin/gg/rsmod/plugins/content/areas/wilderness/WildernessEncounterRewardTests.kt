package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.plugins.content.areas.wilderness.bosses.*
import gg.rsmod.plugins.content.mechanics.prayer.RuinousBookDrops
import gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops
import io.mockk.*
import kotlin.test.*

/** Runs the actual registered reward scripts through the engine completion dispatcher. */
class WildernessEncounterRewardTests {
    @Test fun `phase one gives no burden or ruinous roll and final form gives one canonical boss roll`() {
        val f = WildernessBossTestSupport.Fixture()
        mockkObject(BurdenTrinketDrops, RuinousBookDrops)
        try {
            every { BurdenTrinketDrops.onKill(any(), any(), any(), any()) } just Runs
            every { RuinousBookDrops.onKill(any(), any(), any(), any()) } just Runs
            WildernessBossTestSupport.script("gg.rsmod.plugins.content.skills.summoning.Burden_trinkets_plugin", f.repo, f.world)
            WildernessBossTestSupport.script("gg.rsmod.plugins.content.mechanics.prayer.Ruinous_unlocks_plugin", f.repo, f.world)
            for ((normal, final) in listOf(OldWildernessBosses.VETION to OldWildernessBosses.VETION_REBORN,
                CalvarionCombat.NORMAL to CalvarionCombat.ENRAGED)) {
                val first = f.npc(normal)
                every { first.combatDef } returns f.repo.allNpcCombatDefs().getValue(normal)
                f.repo.executeNpcCompleted(f.player, first)
                verify(exactly = 0) { BurdenTrinketDrops.onKill(any(), normal, any(), any()) }
                verify(exactly = 0) { RuinousBookDrops.onKill(any(), normal, any(), any()) }
                val last = f.npc(final)
                every { last.combatDef } returns f.repo.allNpcCombatDefs().getValue(final)
                f.repo.executeNpcCompleted(f.player, last)
                f.repo.executeNpcCompleted(f.player, last)
                verify(exactly = 1) { BurdenTrinketDrops.onKill(f.player, normal, any(), any()) }
                verify(exactly = 1) { RuinousBookDrops.onKill(f.player, normal, any(), any()) }
            }
        } finally { unmockkObject(BurdenTrinketDrops, RuinousBookDrops) }
    }
}
