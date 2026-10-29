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
    fun `N256 - a pet with a voice makes it by itself every 8 to 12 minutes, the Bloodhound barks, a silent pet stays silent`() {
        // The first tick only plans; the voice comes when the planned cycle is reached, and the next one is planned again.
        assertEquals(false to 1_900, PetVoices.idleDue(null, 1_000) { 900 })
        assertEquals(false to 1_900, PetVoices.idleDue(1_900, 1_899) { 900 })
        assertEquals(true to 2_800, PetVoices.idleDue(1_900, 1_900) { 900 })
        assertEquals(800, PetVoices.IDLE_MIN_CYCLES, "8 minutes of 0.6 s cycles")
        assertEquals(1_200, PetVoices.IDLE_MAX_CYCLES, "12 minutes")
        PetVoices.install(PetVoices.parse(File("../../data/cfg/pets/pet_voices.tsv").readLines()), Pets.variants)
        assertEquals(Sfx.PET_DOG_BARK, PetVoices.idleVoiceOf(TranscriptDialogue.BLOODHOUND), "the Bloodhound's own bark")
        val hellpuppy = Pets.variants.first { it.pet == "Hellpuppy" }.npc
        assertEquals(PetVoices.voiceOf(hellpuppy), PetVoices.idleVoiceOf(hellpuppy), "a pet's idle voice is its Talk-to voice")
        // K02 2026-10-29 (owner: "elke osrs pet moet geluid hebben"): no OSRS pet is silent any more; the Kraken has the sea snake.
        assertEquals(Sfx.SEASNAKE_ATTACK, PetVoices.idleVoiceOf(Pets.variants.first { it.pet == "Pet Kraken" }.npc), "Pet Kraken")
        val tick = File("src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/Pets.kt").readText().substringAfter("fun tick(player: Player) {").substringBefore("\n    }")
        assertTrue("PetVoices.idle(player, npc)" in tick, "every follow tick of a pet")
    }

    @Test
    fun `N296 N257 N298 - every dog barks the same bark, every Loyalty store pet has its species' sound, a pet is heard when it appears`() {
        Pets.load(File("../../data/cfg/pets/pets.tsv"), File("../../data/cfg/pets/pet_tiers.tsv"), File("../../data/cfg/pets/pets_667.tsv"))
        PetVoices.install(PetVoices.parse(File("../../data/cfg/pets/pet_voices.tsv").readLines()), Pets.variants)
        // Dogs: the 667 dogs and puppies, the Hellpuppy and the Bloodhound - one bark, on Talk-to (the Bloodhound on its lines), idle, appearing.
        val dogs = Pets.variants.filter { it.pet in PetVoices.DOGS || (it.classic && it.name in PetVoices.DOGS) }
        assertTrue(dogs.size >= 25, "${dogs.size} dog followers")
        dogs.forEach { assertEquals(Sfx.PET_DOG_BARK, PetVoices.idleVoiceOf(it.npc), it.name) }
        assertNull(PetVoices.voiceOf(TranscriptDialogue.BLOODHOUND), "the Bloodhound barks on its own lines, not twice")
        // Every pet the Loyalty store sells (a follower item) has a voice of its own species.
        val loyalty = gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.ENTRIES
            .filter { it.shop == gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Shop.LOYALTY && Pets.isPetItem(it.purchaseItem) }
        assertTrue(loyalty.size >= 20, "${loyalty.size} Loyalty store pets")
        loyalty.forEach { e -> assertTrue(PetVoices.idleVoiceOf(Pets.variantOfItem(e.purchaseItem)!!.npc) != null, "${Pets.variantOfItem(e.purchaseItem)!!.name} has a sound") }
        // N299 (owner 2026-10-24): the jungle monkey's own calls, not the Varrock Museum display loop (its drone sounded like a train).
        assertEquals(Sfx.JUNGLE_MONKEY_1, PetVoices.voiceOf(Pets.variantOfItem(gg.rsmod.plugins.api.cfg.Items.BABY_MONKEY)!!.npc), "baby monkey: a monkey")
        // N295: the Baby icefiend is a follower now.
        assertEquals(7713, Pets.variantOfItem(gg.rsmod.plugins.api.cfg.Items.BABY_ICEFIEND)?.npc)
        // N298: every way a pet appears plays its voice.
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/Pets.kt").readText()
        for (fn in listOf("fun drop(", "fun call(", "fun restoreOnLogin(", "fun tick(")) {
            assertTrue("PetVoices.appear(" in source.substringAfter(fn).substringBefore("\n    }\n"), "$fn plays the voice")
        }
        // ... but a walking pet caught up behind a running owner (every dozen tiles) stays quiet: only after a teleport.
        val tick = source.substringAfter("fun tick(player: Player) {").substringBefore("\n    }\n")
        assertTrue("if (teleported) PetVoices.appear(p, n)" in tick)
        assertTrue(Pets.TELEPORT_DISTANCE > Familiar.FOLLOW_RECOVERY_DISTANCE)
    }

    @Test
    fun `the Bloodhound barks once on each line that says it barks, and nowhere else`() {
        assertEquals(Sfx.PET_DOG_BARK, TranscriptDialogue.lineSound(TranscriptDialogue.BLOODHOUND, "*Woof woof bark!* Elementary, it's due to the influence of the -SQUIRREL-!"))
        assertNull(TranscriptDialogue.lineSound(TranscriptDialogue.BLOODHOUND, "Walkies!"))
        // Every barking line of the OSRS transcript barks, also the ones that open with "*Bark" (were silent).
        listOf("*Woof! Bark yip woof!* Sure!", "*Bark! Woof bark bark.* Whatever is left", "*Bark bark woof!* You aren't funny.").forEach {
            assertEquals(Sfx.PET_DOG_BARK, TranscriptDialogue.lineSound(TranscriptDialogue.BLOODHOUND, it), it)
        }
        assertNull(TranscriptDialogue.lineSound(1, "*Woof woof bark!*"), "no bark lent to other npcs")
        val rows = com.google.gson.JsonParser().parse(File("../../data/cfg/npcs/transcript-dialogue.json").readText()).asJsonObject
        val bloodhound = rows[TranscriptDialogue.BLOODHOUND.toString()].asJsonObject
        assertEquals("Bloodhound", bloodhound["name"].asString)
        assertTrue("*Woof woof bark!*" in bloodhound["nodes"].toString(), "the transcript still has a barking line")
    }

    /** K02 N88 N405 (owner 2026-10-17/10-29: every OSRS pet makes a sound): every OSRS pet form has a voice. */
    @Test
    fun `every OSRS pet form has a voice`() {
        PetVoices.install(PetVoices.parse(File("../../data/cfg/pets/pet_voices.tsv").readLines()), Pets.variants)
        val silent = Pets.variants.filter { !it.classic && PetVoices.idleVoiceOf(it.npc) == null }.map { "${it.pet}/${it.name}" }
        assertTrue(silent.isEmpty(), "silent pets: $silent")
    }
}
