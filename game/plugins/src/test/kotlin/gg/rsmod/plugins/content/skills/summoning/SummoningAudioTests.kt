package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
 * What remains unproven is an explicit server-side sound for the summon/dismiss action itself.
 * `Familiar.summon` and `Familiar.dismiss` now do play the source-backed per-familiar spawn and
 * despawn animations, so the client has a genuine sequence through which attached audio can play.
 * The cache census found attached audio only for Phoenix's pair; the other pairs are silent in the
 * sequence data. No server sound id is invented for those silent pairs. The remaining question is
 * therefore a source/cache boundary and human listening check, not a missing animation hook.
 *
 * What is testable here, and what these tests do, is that the audio the cache genuinely does carry
 * is still present. A cache import that dropped these groups would make the five audible familiars
 * silent with nothing else failing.
 */
class SummoningAudioTests {
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
            "Vampyre bat" to listOf(7776, 7781),
            "Pack yak 5782" to listOf(6192, 6274),
            "Granite crab" to listOf(3795, 3785, 3778),
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
