package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.SeqSoundProbeTool
import gg.rsmod.plugins.content.combat.audio.NpcCombatAudio
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Familiar audio (owner failure F6).
 *
 * The owner hears no familiar sounds and has refused the earlier conclusion - "5 of 78 familiars
 * carry audio, the other 73 are authentically silent" - as a final answer. Reopening it against the
 * client source rather than only against the cache changed the picture in two ways worth pinning:
 *
 * * The claim that this engine "decodes npc sound fields but has no playback for them" was wrong
 *   about whose job it is. Npc ambient audio is entirely client-side here: `NPCList` calls
 *   `SoundManager.addSounds` for every npc whose `NPCType.hasSounds()` holds, and
 *   `NPCEntity.currentSound` picks ready / walk / run / crawl from the npc type against the
 *   `BASType` animation actually playing. The server neither can nor should send it.
 * * Sequence-attached audio is likewise client-side, through `EntityAnimator.newFrame` into
 *   `Static431.method5827`. That method reveals the gating the cache census could not: a sound
 *   whose range field (`soundInfo[frame][0] & 0x1F`) is zero plays **only** for the local player,
 *   and any positional one is muted by the client's *background* volume rather than its
 *   sound-effect volume.
 *
 * Void and Novite contain no explicit arrival SFX. The permitted fallback, 2009Scape's
 * `Familiar.call()`, sends generic summon sound 188 on both first arrival and later calls and
 * explicitly leaves individual first-summon sounds as TODO. The implementation follows that exact
 * boundary: sound 188 roster-wide, never an invented per-familiar table. Dismiss remains driven by
 * its real sequence; only Phoenix's dismiss sequence carries an attached cache sound.
 *
 * What is testable here, and what these tests do, is that the audio the cache genuinely does carry
 * is still present. A cache import that dropped these groups would make the five audible familiars
 * silent with nothing else failing.
 */
class SummoningAudioTests {
    @Test
    fun `shared familiar server cues use the owner approved five percent lower volume`() {
        assertEquals(116, FamiliarAudio.SERVER_SOUND_VOLUME)
    }

    /**
     * Every sound id reached from the sequences the 78 familiars actually play, recovered by
     * `SeqSoundProbeTool` and recorded in `C:\RSPS\summoning_refs\familiar_seq_sounds.txt`, plus
     * the npc-level ambient ids Spirit scorpion (6837) carries on its `NpcDef`.
     *
     * These are read from the cache, never chosen: the point of the test is that they keep
     * resolving, not that they are the right ones to have picked.
     */
    private val familiarSoundIds =
        mapOf(
            "Spirit scorpion walk 6253" to listOf(7032, 7033, 7029, 7030, 7031),
            "Spirit scorpion npc ambient" to listOf(2901, 4334),
            "Phoenix idle/walk/combat" to listOf(5776, 5753, 5808, 5779, 5774, 5801),
            // Revision-667 (openrs2 #1473) seq 4915/4916 sounds; the earlier 7776/7781 came from
            // non-667 sequence bytes, restored by VampyreBatSeqRestoreTool (owner decision 2026-09-13).
            "Vampyre bat" to listOf(6647, 6649),
            "Pack yak 5782" to listOf(6192, 6274),
            "Granite crab" to listOf(3795, 3785, 3778),
            "Void summoning_special_cast / summoning_renew" to listOf(SummoningSpecialMoves.SPECIAL_CAST_SOUND, 4214),
            "2009Scape generic familiar summon and call fallback" to listOf(Familiar.ARRIVAL_SOUND),
        )

    @Test
    fun `every familiar sound the cache references is still present in the synth sound index`() {
        familiarSoundIds.forEach { (source, ids) ->
            ids.forEach { id ->
                val data = store.data(SYNTH_SOUNDS_INDEX, id, 0)
                assertNotNull(data, "$source references sound $id, which is absent from the cache")
                assertTrue(data!!.isNotEmpty(), "$source references sound $id, which is present but empty")
            }
        }
    }

    /**
     * The two audio indices the client reads - `Js5Archive.SYNTH_SOUNDS = 4` and
     * `Js5Archive.VORBIS = 14`. A missing index would silence everything, not just familiars, and
     * is worth telling apart from a missing individual sound.
     */
    @Test
    fun `both audio indices the client reads are present`() {
        listOf(SYNTH_SOUNDS_INDEX, VORBIS_INDEX).forEach { index ->
            val archives = store.index(index).archiveIds()
            assertTrue(archives.isNotEmpty(), "audio index $index is empty or absent")
        }
    }

    @Test
    fun `Phoenix spawn and despawn sequences retain their attached cache sounds`() {
        assertEquals(
            listOf(
                SeqSoundProbeTool.FrameSound(5, 5776, 1, listOf(5826, 5770, 5787, 5782)),
                SeqSoundProbeTool.FrameSound(9, 5753, 1, listOf(5764, 5796)),
            ),
            SeqSoundProbeTool.seq(store, 11095)?.sounds,
        )
        assertEquals(
            listOf(
                SeqSoundProbeTool.FrameSound(0, 5753, 1, listOf(5764, 5796)),
                SeqSoundProbeTool.FrameSound(1, 5776, 1, listOf(5826, 5770, 5787, 5782)),
            ),
            SeqSoundProbeTool.seq(store, 11096)?.sounds,
        )
    }

    @Test
    fun `QC3 familiar hit and death cues are wired through the shared NPC combat route`() {
        val table = Paths.get("..", "..", "data", "cfg", "npcs", "combat-sounds.json").toFile()
        NpcCombatAudio.load(table)
        val expected =
            mapOf(
                6807 to (4298 to 4303), 7332 to (4600 to 4615), 6832 to (4308 to 4313),
                6838 to (4305 to 4317), 7362 to (4669 to 4703), 6848 to (4188 to 4230),
                6872 to (4134 to 4162), 7354 to (4716 to -1), 6846 to (4184 to 4210),
                7371 to (4625 to 4629), 7368 to (4606 to 4649), 7334 to (4672 to 4690),
                7352 to (4651 to 4634), 6854 to (4338 to 4335), 6856 to (4338 to 4335),
                6858 to (4338 to 4335), 6860 to (4338 to 4335), 6862 to (4338 to 4335),
                6864 to (4338 to 4335), 6868 to (4212 to 4229), 6834 to (4324 to 4307),
                7378 to (4641 to 4597), 6993 to (4242 to 4252), 7364 to (4702 to 4652),
                7366 to (4687 to 4664), 7338 to (4674 to 4696), 6810 to (4191 to 4169),
                6866 to (4318 to 4284), 6821 to (4221 to 4267), 6803 to (4283 to 4327),
                6828 to (4234 to 4192), 6890 to (4328 to 4289), 6816 to (4287 to 4281),
                6814 to (4198 to 4147), 6840 to (4232 to 4206), 7346 to (4632 to 4692),
                6850 to (4216 to 4201), 6799 to (4306 to 4285), 7336 to (4667 to 4628),
                7348 to (4636 to 4658), 6801 to (4294 to 4295), 7356 to (4604 to 4688),
                7360 to (4684 to 4635), 7358 to (4607 to 4663), 6812 to (4223 to 4257),
                6805 to (4323 to 4291), 7342 to (4714 to 4710), 7330 to (4715 to 4673),
                6823 to (4135 to 4195), 7340 to (4697 to 4642), 6870 to (4163 to 4243),
                7350 to (4660 to 4717), 7376 to (4612 to 4666), 6874 to (4175 to 4207),
                7344 to (4704 to 4668),
            )
        expected.forEach { (npcId, sounds) ->
            val row = NpcCombatAudio.rowFor(npcId)
            assertNotNull(row, "QC3 familiar $npcId has no shared combat-audio row")
            assertEquals(sounds.first, row!!.defend, "QC3 hit cue mismatch for familiar $npcId")
            assertEquals(sounds.second, row.death, "QC3 death cue mismatch for familiar $npcId")
            listOf(row.defend, row.death).filter { it >= 0 }.forEach { soundId ->
                val data = store.data(SYNTH_SOUNDS_INDEX, soundId, 0)
                assertNotNull(data, "QC3 familiar $npcId references missing sound $soundId")
                assertTrue(data!!.isNotEmpty(), "QC3 familiar $npcId references empty sound $soundId")
            }
        }
    }

    @Test
    fun `QC3 familiar spawn cues are wired only to actual summon arrival presentation`() {
        val expected =
            mapOf(
                SummoningPouchData.SPIRIT_TZ_KIH to 4677,
                SummoningPouchData.KARAMTHULHU_OVERLORD to 4254,
                SummoningPouchData.VOID_TORCHER to 4694,
                SummoningPouchData.PYRELORD to 4620,
                SummoningPouchData.OBSIDIAN_GOLEM to 4682,
                SummoningPouchData.FIRE_TITAN to 4699,
                SummoningPouchData.ICE_TITAN to 4706,
                SummoningPouchData.MOSS_TITAN to 4626,
                SummoningPouchData.LAVA_TITAN to 4647,
                SummoningPouchData.SWAMP_TITAN to 4683,
                SummoningPouchData.GEYSER_TITAN to 4659,
                SummoningPouchData.ABYSSAL_TITAN to 4656,
                SummoningPouchData.IRON_TITAN to 4646,
                SummoningPouchData.STEEL_TITAN to 4638,
            )
        assertEquals(expected, expected.keys.associateWith(FamiliarAudio::spawnSound))
        expected.values.forEach { soundId ->
            val data = store.data(SYNTH_SOUNDS_INDEX, soundId, 0)
            assertNotNull(data, "QC3 familiar spawn sound $soundId is missing from the cache")
            assertTrue(data!!.isNotEmpty(), "QC3 familiar spawn sound $soundId is empty")
        }
    }

    companion object {
        private const val SYNTH_SOUNDS_INDEX = 4
        private const val VORBIS_INDEX = 14

        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        }

        @AfterClass
        @JvmStatic
        fun closeCache() {
            store.close()
        }
    }
}
