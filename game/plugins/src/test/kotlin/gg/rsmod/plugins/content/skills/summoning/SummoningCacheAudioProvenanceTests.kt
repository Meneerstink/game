package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.SeqSoundProbeTool
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Summoning audio that the revision-667 cache attaches to sequences and spot animations.
 *
 * Every expected id below was decoded from the pristine openrs2 #1473 revision-667 cache
 * (`C:\RSPS\openrs2_667 sounds`) and compared byte-for-byte with the production caches; none was
 * copied from another revision. The sound index carries no group names, so Jagex `lore_*` names
 * cannot be mapped to ids from the cache itself - only attached audio is provable.
 *
 * Two rules are pinned: the specials and obelisk renew play the sourced animation/graphic whose
 * sequence carries the sound (so the client plays it), and every server-sent Summoning sound is
 * never also attached to the animation/graphic played at the same event (no double sound).
 */
class SummoningCacheAudioProvenanceTests {
    private fun seqSounds(seqId: Int): List<Int> {
        val seq = assertNotNull(SeqSoundProbeTool.seq(store, seqId), "sequence $seqId is absent from the cache")
        return seq.sounds.map { it.soundId }
    }

    /** SpotAnimationType opcode 1 = model (u16), opcode 2 = sequence (u16); archive = id ushr 8. */
    private fun spotSeq(gfxId: Int): Int {
        val data = assertNotNull(store.data(SPOT_INDEX, gfxId ushr 8, gfxId and 0xff), "graphic $gfxId is absent")
        var pos = 0
        while (pos < data.size) {
            when (data[pos++].toInt() and 0xff) {
                1 -> pos += 2
                2 -> return ((data[pos].toInt() and 0xff) shl 8) or (data[pos + 1].toInt() and 0xff)
                else -> return -1
            }
        }
        return -1
    }

    private fun gfxSounds(gfxId: Int): List<Int> = spotSeq(gfxId).let { if (it < 0) emptyList() else seqSounds(it) }

    @Test
    fun `Abyssal Drain plays Void's abyssal_drain presentation whose sequence carries its sounds`() {
        assertEquals(
            Triple(7672, 1422, 1423),
            SummoningSpecialMoves.directSpecialVisuals(SummoningScrollData.ABYSSAL_DRAIN_SCROLL),
        )
        assertEquals(listOf(6538, 6540), seqSounds(SummoningSpecialMoves.ABYSSAL_DRAIN_ANIMATION))
    }

    @Test
    fun `Explode plays chinchompa_explode graphic whose sequence carries its sound`() {
        assertEquals(7758, SummoningSpecialMoves.EXPLODE_ANIMATION)
        assertEquals(1364, SummoningSpecialMoves.EXPLODE_GRAPHIC)
        assertEquals(7757, spotSeq(SummoningSpecialMoves.EXPLODE_GRAPHIC))
        assertEquals(listOf(7164), gfxSounds(SummoningSpecialMoves.EXPLODE_GRAPHIC))
    }

    @Test
    fun `obelisk renew graphic carries its sound and the server renew sound is not duplicated`() {
        assertEquals(listOf(7579), gfxSounds(Familiar.RENEW_OBELISK_GRAPHIC))
        assertEquals(emptyList(), seqSounds(Familiar.RENEW_ANIMATION))
        assertEquals(emptyList(), gfxSounds(Familiar.RENEW_PLAYER_GRAPHIC))
        assertTrue(Familiar.RENEW_SOUND !in gfxSounds(Familiar.RENEW_OBELISK_GRAPHIC))
    }

    @Test
    fun `special cast sound is not also attached to the special cast animation or graphic`() {
        assertEquals(emptyList(), seqSounds(SummoningSpecialMoves.SPECIAL_CAST_ANIMATION))
        assertEquals(emptyList(), gfxSounds(SummoningSpecialMoves.SPECIAL_CAST_GRAPHIC))
    }

    @Test
    fun `arrival sound is not also attached to the size-sensitive arrival graphics`() {
        listOf(1314, 1315).forEach { gfx ->
            assertEquals(emptyList(), gfxSounds(gfx), "arrival graphic $gfx")
        }
    }

    @Test
    fun `Pack Yak attack sound 6192 is attached to its attack sequence, not sent by the server`() {
        val attack = SummoningCombatDefinitions.get(SummoningPouchData.PACK_YAK).attackAnimation
        assertEquals(5782, attack)
        val seq = assertNotNull(SeqSoundProbeTool.seq(store, attack))
        assertEquals(listOf(SeqSoundProbeTool.FrameSound(0, 6192, 1, listOf(6274))), seq.sounds)
        assertTrue(seq.vorbis, "seq 5782 plays its sound from the vorbis index")
        assertNotNull(store.data(VORBIS_INDEX, 6192, 0), "vorbis sound 6192 must be present")
    }

    @Test
    fun `Vampyre bat combat sequences carry their revision-667 synth sounds`() {
        val bat = SummoningCombatDefinitions.get(SummoningPouchData.VAMPYRE_BAT)
        assertEquals(Triple(4915, 4916, 4917), Triple(bat.attackAnimation, bat.blockAnimation, bat.deathAnimation))
        mapOf(4915 to listOf(6647, 6649), 4916 to listOf(6649, 6649), 4917 to emptyList()).forEach { (seqId, sounds) ->
            val seq = assertNotNull(SeqSoundProbeTool.seq(store, seqId))
            assertEquals(sounds, seq.sounds.map { it.soundId }, "seq $seqId sounds")
            assertTrue(!seq.vorbis, "seq $seqId must use the synth index as in revision 667")
        }
    }

    companion object {
        private const val SPOT_INDEX = 21
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
