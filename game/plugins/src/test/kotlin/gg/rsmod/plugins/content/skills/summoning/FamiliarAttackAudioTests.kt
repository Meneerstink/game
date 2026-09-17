package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.SeqSoundProbeTool
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FamiliarAttackAudioTests {
    @Test
    fun `proven familiar attack audio is attached to the revision 667 attack sequence`() {
        assertEquals(5782, SummoningCombatDefinitions.get(SummoningPouchData.PACK_YAK).attackAnimation)
        assertEquals(8104, SummoningCombatDefinitions.get(SummoningPouchData.GRANITE_CRAB).attackAnimation)
        assertEquals(4915, SummoningCombatDefinitions.get(SummoningPouchData.VAMPYRE_BAT).attackAnimation)

        val cache = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            listOf(6192, 3795, 6647, 6649).forEach { sound ->
                assertNotNull(cache.data(4, sound, 0), "sequence-attached attack sound $sound is absent from the local cache")
            }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `all executable familiar attacks keep their sourced animation for cache dispatch`() {
        val executable = SummoningCombatDefinitions.values.filter { it.isExecutable }
        assertEquals(72, executable.size)
        assertTrue(executable.all { it.attackAnimation >= 0 })
    }

    @Test
    fun `steel titan normal attack has a source-backed server sound because its client assets are silent`() {
        assertEquals(8190, SummoningCombatDefinitions.get(SummoningPouchData.STEEL_TITAN).attackAnimation)
        assertEquals(4720, FamiliarCombat.STEEL_TITAN_ATTACK_SOUND)
        assertEquals(4616, FamiliarCombat.STEEL_TITAN_RANGED_SOUND)
        assertEquals(4670, FamiliarCombat.STEEL_TITAN_RANGED_IMPACT_SOUND)

        val cache = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            // The normal combat assets themselves have no attachment. The server must therefore
            // dispatch the exact `lore_steel_titan_attack` synth group rather than borrow one.
            assertEquals(emptyList(), assertNotNull(SeqSoundProbeTool.seq(cache, 8190)).sounds)
            listOf(
                FamiliarCombat.STEEL_TITAN_ATTACK_SOUND,
                FamiliarCombat.STEEL_TITAN_RANGED_SOUND,
                FamiliarCombat.STEEL_TITAN_RANGED_IMPACT_SOUND,
            ).forEach { sound ->
                val data = assertNotNull(cache.data(4, sound, 0), "Steel Titan normal-combat sound $sound is absent from the local cache")
                assertTrue(data.isNotEmpty(), "Steel Titan normal-combat sound $sound is empty in the local cache")
            }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `steel of legends source-backed sounds are present in the local synth cache`() {
        assertEquals(4680, SummoningSpecialMoves.STEEL_OF_LEGENDS_SCROLL_SOUND)
        assertEquals(4611, SummoningSpecialMoves.STEEL_TITAN_SPECIAL_ATTACK_SOUND)
        assertEquals(4653, SummoningSpecialMoves.STEEL_TITAN_SPECIAL_IMPACT_SOUND)

        val cache = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            listOf(
                SummoningSpecialMoves.STEEL_OF_LEGENDS_SCROLL_SOUND,
                SummoningSpecialMoves.STEEL_TITAN_SPECIAL_ATTACK_SOUND,
                SummoningSpecialMoves.STEEL_TITAN_SPECIAL_IMPACT_SOUND,
            ).forEach { sound ->
                val data = assertNotNull(cache.data(4, sound, 0), "Steel of Legends sound $sound is absent from the local cache")
                assertTrue(data.isNotEmpty(), "Steel of Legends sound $sound is empty in the local cache")
            }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `qc3 dedicated normal familiar attack cues resolve in the local synth cache`() {
        assertTrue(FamiliarCombat.NORMAL_ATTACK_SOUNDS.keys.all { SummoningCombatDefinitions.get(it).isExecutable })
        val cache = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            FamiliarCombat.NORMAL_ATTACK_SOUNDS.values
                .flatMap { listOfNotNull(it.attack, it.ranged, it.impact) }
                .distinct()
                .forEach { sound ->
                    val data = assertNotNull(cache.data(4, sound, 0), "QC3 familiar attack sound $sound is absent from the local cache")
                    assertTrue(data.isNotEmpty(), "QC3 familiar attack sound $sound is empty in the local cache")
                }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `qc3 dedicated special cues resolve in the local synth cache`() {
        val cache = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            SummoningSpecialMoves.SPECIAL_SOUND_CUES.values
                .flatMap { listOfNotNull(it.action, it.impact, it.secondaryAction, it.secondaryImpact) }
                .distinct()
                .forEach { sound ->
                    val data = assertNotNull(cache.data(4, sound, 0), "QC3 familiar special sound $sound is absent from the local cache")
                    assertTrue(data.isNotEmpty(), "QC3 familiar special sound $sound is empty in the local cache")
                }
        } finally {
            cache.close()
        }
    }
}
