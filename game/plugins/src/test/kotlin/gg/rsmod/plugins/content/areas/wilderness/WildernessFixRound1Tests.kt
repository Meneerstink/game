package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.areas.wilderness.bosses.ArtioCombat
import gg.rsmod.plugins.content.areas.wilderness.bosses.CalvarionCombat
import gg.rsmod.plugins.content.areas.wilderness.bosses.OldWildernessBosses
import gg.rsmod.plugins.content.areas.wilderness.bosses.VenenatisCombat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N522 fix round 1 (2026-11-05): Slayer xp per OSRS Wiki infobox, Calvar'ion takes every style, the Wiki recoil-immune list. */
class WildernessFixRound1Tests {
    @Test fun `every wilderness boss and revenant gives its OSRS Wiki slayer xp`() {
        val f = WildernessBossTestSupport.Fixture()
        val defs = f.repo.allNpcCombatDefs()
        val expected = mapOf(
            OldWildernessBosses.CALLISTO to 312.0, // revision 14355607 (pre-2023 infobox)
            OldWildernessBosses.VETION to 312.0, OldWildernessBosses.VETION_REBORN to 312.0, // 624 for both forms
            ArtioCombat.NPC to 518.0, Npcs.CALVARION to 161.25, Npcs.CALVARION_ENRAGED to 161.25, // 322.5 for both forms
            VenenatisCombat.NPC to 956.2, VenenatisCombat.SPINDEL to 541.0, Npcs.SCORPIA to 260.0, Npcs.CHAOS_ELEMENTAL to 268.75,
            Npcs.CHAOS_FANATIC to 253.0, Npcs.CRAZY_ARCHAEOLOGIST to 275.0, Npcs.REVENANT_MALEDICTUS_OSRS to 920.0,
            Npcs.REVENANT_IMP_OSRS to 10.0, Npcs.REVENANT_GOBLIN_OSRS to 14.0, Npcs.REVENANT_PYREFIEND_OSRS to 48.0,
            Npcs.REVENANT_HOBGOBLIN_OSRS to 73.0, Npcs.REVENANT_CYCLOPS_OSRS to 119.0, Npcs.REVENANT_HELLHOUND_OSRS to 86.0,
            Npcs.REVENANT_DEMON_OSRS to 86.0, Npcs.REVENANT_ORK_OSRS to 115.0, Npcs.REVENANT_DARK_BEAST_OSRS to 157.0,
            Npcs.REVENANT_KNIGHT_OSRS to 168.0, Npcs.REVENANT_DRAGON_OSRS to 186.0,
        )
        expected.forEach { (id, xp) -> assertEquals(xp, defs.getValue(id).slayerXp, 1e-9, "slayer xp of npc $id") }
    }

    @Test fun `no wilderness boss stacks a fixed xp multiplier on the Wiki bonus formula`() {
        val defs = WildernessBossTestSupport.Fixture().repo.allNpcCombatDefs()
        listOf(OldWildernessBosses.CALLISTO, ArtioCombat.NPC, VenenatisCombat.NPC, Npcs.SCORPIA, Npcs.REVENANT_MALEDICTUS_OSRS)
            .forEach { assertEquals(1.0, defs.getValue(it).xpMultiplier, "npc $it") }
    }

    @Test fun `Calvar'ion takes stab, slash, ranged and magic damage once his hellhounds are gone`() {
        val f = WildernessBossTestSupport.Fixture()
        val npc = f.npc(CalvarionCombat.NORMAL, CalvarionCombat.SPAWN)
        listOf(HitType.MELEE, HitType.RANGE, HitType.MAGIC).forEach { type ->
            assertEquals(17, CalvarionCombat.modifyIncomingDamage(npc, f.player, type, 17), "$type")
        }
    }

    @Test fun `Artio, Calvar'ion and his hellhounds are recoil immune - the old Callisto and Vet'ion are not`() {
        listOf(ArtioCombat.NPC, CalvarionCombat.NORMAL, CalvarionCombat.ENRAGED, CalvarionCombat.HOUND, CalvarionCombat.GREATER_HOUND,
            VenenatisCombat.NPC, VenenatisCombat.SPIDERLING).forEach { assertTrue(VenenatisCombat.recoilImmune(it), "npc $it") }
        listOf(OldWildernessBosses.CALLISTO, OldWildernessBosses.VETION, Npcs.SCORPIA).forEach { assertFalse(VenenatisCombat.recoilImmune(it), "npc $it") }
    }
}
