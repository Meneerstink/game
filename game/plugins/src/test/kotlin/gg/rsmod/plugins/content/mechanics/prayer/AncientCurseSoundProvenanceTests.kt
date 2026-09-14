package gg.rsmod.plugins.content.mechanics.prayer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.SeqSoundProbeTool
import gg.rsmod.plugins.api.cfg.Sfx
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where curse audio actually comes from, proven against the production cache rather than reasoned
 * about (CURSES-2011, 2026-09-14; supersedes the RCV-002 "Novite 2662 on / 2663 off" model).
 *
 * ## The model this pins
 *
 * In this revision a sound can ride on a sequence frame ([SeqSoundProbeTool]) and then plays
 * client-side by itself when the animation or spot-animation renders. For the curse book that is
 * the *only* authentic audio: every curse graphic that has a sound carries it on its own sequence,
 * every player body sequence is silent, and the server sends no activation sound at all. The one
 * server-sent track is the explicit toggle-off 2663 (Novite `Prayer.java:493/499`, Void
 * `deactivate_prayer`).
 *
 * ## What was rejected, and why
 *
 *  * Novite/Matrix 2662 on every activation: a generic normal-book track (Improved Reflexes) played
 *    for every prayer in both books. The owner's live retest heard it as the same wrong extra sound
 *    on every curse, and the 2026-09-14 client trace shows it queued right before Turmoil's 2226,
 *    whose own sequence then queued the real 8111.
 *  * Darkan's `activateSound` column: Sap, every Leech and Soul Split all map to 2675 (Protect from
 *    Magic) and Protect Item, Berserker and Turmoil to 11000, which this test proves is not a sound
 *    in this cache at all.
 *  * Void's 125/126/127/1634 `curse_*` tracks: the *Curse* magic spell, chosen by name alone.
 */
class AncientCurseSoundProvenanceTests {
    private fun <T> withCache(block: (CacheLibrary) -> T): T {
        val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        return try {
            block(library)
        } finally {
            library.close()
        }
    }

    /** Index 4 is `Js5Archive.SYNTH_SOUNDS`; a sound id is a group and the sound is its file 0. */
    private val synthSoundIndex = 4

    /** Index 21 holds the spot-animation types in this cache (`reference/curses-audio-research/CurseAudioProbe.java`). */
    private val spotAnimIndex = 21

    /**
     * Graphic -> the sequence its spotanim type must reference -> the synth that sequence must carry.
     * Every entry is what the running code sends (see [AncientCurse]/[AncientCurses]).
     */
    private val graphicAudio =
        mapOf(
            AncientCurses.PROTECT_ITEM_ACTIVATION_GRAPHIC to (12568 to 8117),
            AncientCurse.SAP_WARRIOR.castGraphic!! to (12570 to 8115),
            AncientCurse.SAP_RANGER.castGraphic!! to (12570 to 8115),
            AncientCurse.SAP_MAGE.castGraphic!! to (12570 to 8115),
            AncientCurse.SAP_SPIRIT.castGraphic!! to (12570 to 8115),
            AncientCurses.TURMOIL_ACTIVATION_GRAPHIC to (12566 to 8111),
            AncientCurse.DEFLECT_MAGIC.reflectGraphic!! to (12574 to 8107),
            AncientCurse.DEFLECT_MISSILES.reflectGraphic!! to (12574 to 8107),
            AncientCurse.DEFLECT_MELEE.reflectGraphic!! to (12574 to 8107),
            AncientCurse.LEECH_ENERGY.castGraphic!! to (12576 to 8116),
            AncientCurse.LEECH_SPECIAL_ATTACK.castGraphic!! to (12576 to 8116),
            AncientCurses.WRATH_RING_GFX to (12581 to 8118),
            AncientCurse.BERSERKER.activationGraphic!! to (12590 to 8106),
        )

    /** Player body sequences the curse book plays; all silent, so the graphic is the only audio. */
    private val bodySequences =
        mapOf(
            12565 to "Turmoil activation",
            12567 to "Protect Item activation",
            12569 to "Sap cast",
            12573 to "Deflect reflect",
            12575 to "Leech cast",
            12589 to "Berserker activation",
        )

    private fun spotAnimSequence(library: CacheLibrary, graphic: Int): Int {
        val data = library.data(spotAnimIndex, graphic ushr 8, graphic and 0xff)
        assertNotNull("graphic $graphic is absent from spotanim index $spotAnimIndex", data)
        // Same opcode walk as the read-only research probe (op 2 = sequence).
        val buf = io.netty.buffer.Unpooled.wrappedBuffer(data)
        var seq = -1
        while (buf.isReadable) {
            when (val opcode = buf.readUnsignedByte().toInt()) {
                0 -> return seq
                2 -> seq = buf.readUnsignedShort()
                1, 4, 5, 6, 15 -> buf.readUnsignedShort()
                7, 8, 14 -> buf.readUnsignedByte()
                16 -> buf.readInt()
                9, 10, 11, 12, 13 -> {}
                40, 41 -> buf.skipBytes(buf.readUnsignedByte().toInt() * 4)
                else -> throw IllegalStateException("graphic $graphic: unknown spotanim opcode $opcode")
            }
        }
        return seq
    }

    @Test
    fun `every curse graphic with audio references the sequence that carries its synth in the production cache`() {
        withCache { library ->
            val offenders = mutableListOf<String>()
            graphicAudio.forEach { (graphic, expected) ->
                val (seqId, synth) = expected
                val actualSeq = spotAnimSequence(library, graphic)
                if (actualSeq != seqId) offenders += "graphic $graphic references seq $actualSeq, expected $seqId"
                val seq = SeqSoundProbeTool.seq(library, seqId)
                if (seq == null) {
                    offenders += "seq $seqId (graphic $graphic) absent"
                } else if (seq.sounds.none { it.soundId == synth }) {
                    offenders += "seq $seqId (graphic $graphic) sounds ${seq.sounds.map { it.soundId }} lack $synth"
                }
                val archive = library.index(synthSoundIndex).archive(synth)
                if (archive == null || !archive.fileIds().contains(0)) offenders += "synth $synth (graphic $graphic) absent"
            }
            assertEquals(emptyList<String>(), offenders)
        }
    }

    /**
     * The reason the server must NOT add an activation sound: the graphic already brings it. If a
     * body sequence ever gains a frame sound, a graphic-borne sound for the same event becomes a
     * duplicate that has to be reconsidered.
     */
    @Test
    fun `no curse body animation carries a frame sound, so the graphic sequence is the only audio`() {
        withCache { library ->
            bodySequences.forEach { (id, label) ->
                val seq = SeqSoundProbeTool.seq(library, id)
                assertNotNull("sequence $id ($label) is absent from this cache", seq)
                assertEquals(
                    "sequence $id ($label) gained frame sounds: ${seq!!.sounds}",
                    emptyList<SeqSoundProbeTool.FrameSound>(),
                    seq.sounds,
                )
            }
        }
    }

    /** The only server-sent curse track keeps its sourced id and is real in the production cache. */
    @Test
    fun `the explicit toggle-off sound is 2663 and exists`() {
        assertEquals(2663, Sfx.CANCEL_PRAYER)
        withCache { library ->
            val archive = library.index(synthSoundIndex).archive(Sfx.CANCEL_PRAYER)
            assertNotNull("2663 is absent from synth-sound index $synthSoundIndex", archive)
            assertTrue("2663 has no file 0", archive!!.fileIds().contains(0))
        }
    }

    /**
     * Darkan's substitution: 11000 is used there for Protect Item, Berserker and Turmoil. If it
     * ever becomes a real group in this cache the rejection above deserves a fresh look, so this
     * fails loudly rather than silently going stale.
     */
    @Test
    fun `the sound id Darkan substitutes for three curses does not exist here`() {
        withCache { library ->
            assertEquals(
                "11000 has appeared in the synth-sound index; re-check the rejected Darkan sound column",
                null,
                library.index(synthSoundIndex).archive(11000),
            )
        }
    }
}
