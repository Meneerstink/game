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
 * about.
 *
 * ## The question this settles
 *
 * [AncientCurses] plays four sounds - curse activated, curse lifted, an activation firing on a
 * landed hit, and the effect landing - and the class used to record honestly that *which* event
 * each track accompanies was inference. Two things were unknown: whether those ids are even real
 * in this cache, and whether the server needs to send them at all, since in this revision a sound
 * can also ride on an animation frame ([SeqSoundProbeTool]) and would then play by itself.
 *
 * Both are now answered:
 *
 *  * **The ids are real.** 125/126/127/1634 are present synth-sound groups (index 4, one file each)
 *    in both `data/cache` and the file-server cache, which are byte-identical for this index. They
 *    are also the only four curse-named tracks in the generated [Sfx] table.
 *  * **The server is the only possible source.** Every sequence the curse book plays - 12565
 *    (Turmoil activation), 12569 (Sap), 12573 (the Deflect reflect) and 12575 (Leech) - decodes as
 *    a real `SeqType` and carries **no** frame sounds at all. So unlike the familiars, where a
 *    Phoenix's spawn animation brings its own audio, nothing about a curse is audible unless the
 *    server sends it.
 *
 * ## What was rejected, and why
 *
 * Darkan's `Prayer.java` carries an `activateSound` column for all twenty curses. It is a
 * substitution table, not a recovered one: Sap, every Leech and Soul Split all map to 2675, the
 * normal book's Protect from Magic sound, and Protect Item, Berserker and Turmoil map to 11000 -
 * which this test proves is not a sound in this cache at all. Novite's rev-667 `Prayer.java` is no
 * better a source for per-curse audio: it plays one generic pair for every prayer in both books
 * (2662 on, 2663 off) and has no per-curse sound of any kind.
 *
 * Superseded 2026-09-12 (RCV-002, owner live report of wrong/overlapping curse sounds): the four
 * "curse" tracks are Void's `curse_all`/`curse_impact`/`curse_cast` entries in `magic.sounds.toml`,
 * i.e. the *Curse* spell, not the prayer book, and were chosen by name alone. The curse book now
 * sends the donor-backed toggle pair instead - Novite rev-667 2662 on / 2663 off, with Void's
 * `deactivate_prayer` corroborating 2663 - and no impact sound, because none is sourced.
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

    private val curseSounds =
        mapOf(
            "curse activated (Novite Prayer.java:628)" to Sfx.IMPROVED_REFLEXES,
            "curse lifted (Novite Prayer.java:493, Void deactivate_prayer)" to Sfx.CANCEL_PRAYER,
        )

    /** The animations the curse book plays; named here so a silent-check failure says which. */
    private val curseSequences =
        mapOf(
            12565 to "Turmoil activation",
            12569 to "Sap cast",
            12573 to "Deflect reflect",
            12575 to "Leech cast",
        )

    @Test
    fun `every curse sound this project sends is a real synth sound in the production cache`() {
        withCache { library ->
            val index = library.index(synthSoundIndex)
            curseSounds.forEach { (event, id) ->
                val archive = index.archive(id)
                assertNotNull("sound $id ($event) is absent from synth-sound index $synthSoundIndex", archive)
                assertTrue("sound $id ($event) has no file 0", archive!!.fileIds().contains(0))
            }
        }
    }

    /**
     * Pins the exact ids as well as their existence, so a future edit cannot quietly swap one
     * curse-named track for another and still pass the existence check above.
     */
    @Test
    fun `the donor prayer toggle pair keeps its sourced ids`() {
        assertEquals(listOf(2662, 2663), curseSounds.values.toList())
        assertEquals(2662, Sfx.IMPROVED_REFLEXES)
        assertEquals(2663, Sfx.CANCEL_PRAYER)
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

    /**
     * The reason the server has to send curse audio at all. If a future cache edit ever attaches a
     * frame sound to one of these, this fails and the server-sent sound for that event becomes a
     * duplicate that has to be reconsidered.
     */
    @Test
    fun `no curse animation carries a frame sound, so nothing plays client-side on its own`() {
        withCache { library ->
            curseSequences.forEach { (id, label) ->
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
}
