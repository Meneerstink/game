package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.content.npcs.TranscriptDialogue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * N70 / N88 (owner 10-16/10-17: "bloodhound pet no sounds", "OSRS PETS DO NOT MAKE ANY SOUND"), within the Council N88 decision:
 * the "Getting pet" sound on a new pet (synth 2320, proven) and the Bloodhound's bark on its own "*Woof woof bark!*" lines.
 */
class PetSoundsTests {
    @Test
    fun `a new pet plays the OSRS Getting pet sound, a duplicate or a lost one does not`() {
        assertEquals(2320, Pets.GETTING_PET_SOUND)
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/Pets.kt").readText()
        val guarded = source.substringAfter("if (outcome != Award.DUPLICATE && outcome != Award.LOST) {").substringBefore("\n        }")
        assertTrue("player.playSound(GETTING_PET_SOUND)" in guarded, "only for FOLLOWER / BACKPACK / BANK")
        assertEquals(1, Regex("""playSound\(GETTING_PET_SOUND\)""").findAll(source).count())
    }

    @Test
    fun `the Bloodhound barks once on each line that says it barks, and nowhere else`() {
        assertEquals(Sfx.PET_DOG_BARK, TranscriptDialogue.lineSound(TranscriptDialogue.BLOODHOUND, "*Woof woof bark!* Elementary, it's due to the influence of the -SQUIRREL-!"))
        assertNull(TranscriptDialogue.lineSound(TranscriptDialogue.BLOODHOUND, "Walkies!"))
        assertNull(TranscriptDialogue.lineSound(1, "*Woof woof bark!*"), "no bark lent to other npcs")
        val rows = com.google.gson.JsonParser().parse(File("../../data/cfg/npcs/transcript-dialogue.json").readText()).asJsonObject
        val bloodhound = rows[TranscriptDialogue.BLOODHOUND.toString()].asJsonObject
        assertEquals("Bloodhound", bloodhound["name"].asString)
        assertTrue("*Woof woof bark!*" in bloodhound["nodes"].toString(), "the transcript still has a barking line")
    }
}
