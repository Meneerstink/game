package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Sfx
import java.io.File

/**
 * N88 part 3 (owner 2026-10-23: "elke osrs pet moet geluid hebben"; decisions in team\prep\N88\research\OVERZICHT.md): OSRS gives
 * its pets no voice, so a pet answers Talk-to once with Jagex's own sound of that creature or its boss (Callisto cub: Callisto's
 * roar), or of its family where the creature has none (Heron: bird_attack); Huberte also screeches on its "cute little thing" line.
 * Eight pets nothing can be sourced for stay silent. Table `data/cfg/pets/pet_voices.tsv`, written by OsrsPetImportTool `table`
 * from its VOICES (pet, variant or * for every variant, OSRS synth, local synth, Jagex name, transcript line or empty, source).
 */
object PetVoices {
    const val PATH = "./data/cfg/pets/pet_voices.tsv"

    /** Full volume, like every other pet cue ([Pets]). */
    private const val VOLUME = 255

    data class Row(val pet: String, val variant: String, val localSynth: Int, val name: String, val line: String)

    @Volatile
    private var talk: Map<Int, Int> = emptyMap()

    @Volatile
    private var lines: Map<Int, Map<String, Int>> = emptyMap()

    fun parse(text: List<String>): List<Row> =
        text.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val c = line.split('\t')
            require(c.size >= 6) { "bad pet voice line '$line'" }
            Row(c[0], c[1], c[3].trim().toInt(), c[4], c[5])
        }

    /** The row of [rows] for follower [variant]: its own variant row, else its pet's "*" row. */
    private fun rowFor(
        rows: List<Row>,
        variant: Pets.Variant,
    ): Row? = rows.firstOrNull { it.pet == variant.pet && it.variant == variant.name } ?: rows.firstOrNull { it.pet == variant.pet && it.variant == "*" }

    /**
     * N296 (owner 2026-10-24: "alle honden-pets hetzelfde blafgeluid"): every dog pet barks with the Bloodhound's bark - the 667
     * dogs and puppies and the Hellpuppy - on Talk-to, by itself now and then and when it appears.
     */
    val DOGS = setOf("Bloodhound", "Hellpuppy", "Terrier puppy", "Terrier", "Greyhound puppy", "Greyhound", "Labrador puppy", "Labrador",
        "Dalmatian puppy", "Dalmatian", "Sheepdog puppy", "Sheepdog", "Bulldog puppy", "Bulldog")

    /**
     * N257 (owner 2026-10-24: "alle pets in de loyalty store maken geen sounds ... geluiden per soort kiezen uit bestaande geluiden,
     * bv. baby monkey -> gorilla- of apengeluiden"): the 667 pets (Loyalty store and the other Summoning-era pets) by species, each
     * with an existing 667 sound of that creature. N299 (owner 2026-10-24: after the monkey call "een soort treingeluid"): the
     * Varrock Museum calls (VM_*) are display loops - VM_MONKEY lays its calls over a 2.5 s looping drone, VM_LIZARD repeats four
     * times over 3.4 s - so the creatures' own calls instead (lengths decoded from the cache, synth_duration_probe): the jungle
     * monkey's four short calls (1.2 s, no drone), the lizard's hiss (0.4 s), the penguin call of Cold War (1.6 s).
     */
    val CLASSIC: Map<String, Int> =
        mapOf(
            "Pet kitten" to Sfx.KITTENS_MEW, "Hell-kitten" to Sfx.KITTENS_MEW, "Pet cat" to Sfx.PURR, "Overgrown cat" to Sfx.PURR,
            "Clockwork cat" to Sfx.PURR, "Hellcat" to Sfx.CAT_HISS, "Overgrown hellcat" to Sfx.CAT_HISS,
            "Hatchling dragon" to Sfx.BABYDRAGON_ATTACK, "Baby dragon" to Sfx.BABYDRAGON_ATTACK,
            "Baby penguin" to Sfx.PENG_PENGUIN_CALL, "Penguin" to Sfx.PENG_PENGUIN_CALL,
            "Raven chick" to Sfx.BIRD_ATTACK, "Raven" to Sfx.BIRD_ATTACK, "Vulture chick" to Sfx.VULTURE_1, "Vulture" to Sfx.VULTURE_1,
            "Saradomin chick" to Sfx.BIRD_TWITTER_1, "Zamorak chick" to Sfx.BIRD_TWITTER_1, "Guthix chick" to Sfx.BIRD_TWITTER_1,
            "Saradomin bird" to Sfx.BIRD_ATTACK, "Zamorak bird" to Sfx.BIRD_ATTACK, "Guthix bird" to Sfx.BIRD_ATTACK,
            "Saradomin owl" to Sfx.OWL_PART_1, "Zamorak hawk" to Sfx.BIRD_ATTACK, "Guthix raptor" to Sfx.BIRD_ATTACK,
            "Baby raccoon" to Sfx.RAT_SQUEAK_1, "Raccoon" to Sfx.RAT_SQUEAK_1, "Baby squirrel" to Sfx.RAT_SQUEAK_2, "Squirrel" to Sfx.RAT_SQUEAK_2,
            "Baby gecko" to Sfx.LIZARD_ATTACK, "Gecko" to Sfx.LIZARD_ATTACK, "Baby chameleon" to Sfx.LIZARD_ATTACK, "Chameleon" to Sfx.LIZARD_ATTACK,
            "Baby monkey" to Sfx.JUNGLE_MONKEY_1, "Monkey" to Sfx.JUNGLE_MONKEY_1,
            "Baby giant crab" to Sfx.SMALL_CRAB_ATTACK, "Giant crab" to Sfx.ROCK_CRAB_ATTACK,
            "Baby platypus" to Sfx.QUACK, "Platypus" to Sfx.QUACK,
            "Baby icefiend" to Sfx.ICEFIEND_ATTACK,
        )

    /** The voice of a pet species that the table rows do not give: a dog's bark, a 667 pet's species sound. */
    fun speciesVoice(variant: Pets.Variant): Int? {
        val species = if (variant.classic) variant.name else variant.pet
        return when {
            species in DOGS || variant.pet in DOGS -> Sfx.PET_DOG_BARK
            variant.classic -> CLASSIC[species]
            else -> null
        }
    }

    fun install(
        rows: List<Row>,
        variants: List<Pets.Variant>,
    ) {
        val start = rows.filter { it.line.isEmpty() }
        // A dog barks whatever its row says; then the table's own voice; then the 667 species sound. A pet whose transcript lines
        // bark themselves (the Bloodhound) keeps that, so Talk-to does not bark twice; it still barks by itself and on appearing.
        talk = variants.filter { gg.rsmod.plugins.content.npcs.TranscriptDialogue.ownVoice(it.npc) == null }.mapNotNull { v -> (Sfx.PET_DOG_BARK.takeIf { v.pet in DOGS || (v.classic && v.name in DOGS) } ?: rowFor(start, v)?.localSynth ?: speciesVoice(v))?.let { v.npc to it } }.toMap()
        val onLine = rows.filter { it.line.isNotEmpty() }
        lines =
            variants.mapNotNull { v ->
                val own = onLine.filter { it.pet == v.pet && (it.variant == "*" || it.variant == v.name) }
                if (own.isEmpty()) null else v.npc to own.associate { it.line to it.localSynth }
            }.toMap()
    }

    fun load(file: File = File(PATH)): Int {
        if (!file.exists()) return 0
        install(parse(file.readLines()), Pets.variants)
        return talk.size
    }

    /** The Talk-to voice of follower npc [npc], or null for a silent pet / no pet. */
    fun voiceOf(npc: Int): Int? = talk[npc]

    /** The sound pet [npc] makes on transcript line [text] (Huberte's "*screeches*"), or null. */
    fun lineSound(
        npc: Int,
        text: String,
    ): Int? = lines[npc]?.entries?.firstOrNull { text.trim().startsWith(it.key, ignoreCase = true) }?.value

    /**
     * N256 (owner 2026-10-23: "blood hound pet blaft nu alleen als je met hem interact ... blaft bijvoorbeeld om de 10 minuten; hoe
     * zit het met de andere osrs pets"): every pet with a voice also makes it now and then by itself while it follows, after a random
     * 8 to 12 minutes ([IDLE_MIN_CYCLES]..[IDLE_MAX_CYCLES], game cycles of 0.6 s). The silent pets stay silent.
     */
    const val IDLE_MIN_CYCLES = 800
    const val IDLE_MAX_CYCLES = 1200

    private val NEXT_IDLE_CYCLE = AttributeKey<Int>()

    /** Whether the idle voice is due at [cycle], and the cycle the one after it is due ([roll] draws the wait). */
    fun idleDue(
        nextAt: Int?,
        cycle: Int,
        roll: () -> Int,
    ): Pair<Boolean, Int> =
        when {
            nextAt == null -> false to cycle + roll()
            cycle >= nextAt -> true to cycle + roll()
            else -> false to nextAt
        }

    /** The sound pet [npc] makes by itself: its Talk-to voice, else its transcript voice (the Bloodhound's bark), else none. */
    fun idleVoiceOf(npc: Int): Int? = talk[npc] ?: gg.rsmod.plugins.content.npcs.TranscriptDialogue.ownVoice(npc)

    /**
     * N298 (owner 2026-10-24: "alle pets sounds bij spawn moeten ze geluid maken als ze die hebben"): a pet with a voice makes it
     * when it appears - dropped from the inventory, out again after a login, called or brought along after a teleport.
     */
    fun appear(
        player: Player,
        npc: Npc,
    ) {
        val sound = idleVoiceOf(npc.id) ?: return
        FamiliarAudio.play(player, sound, volume = VOLUME, at = npc.tile)
    }

    /** One follow tick of [player]'s pet [npc]: its idle voice when it is due ([idleDue]). */
    fun idle(
        player: Player,
        npc: Npc,
    ) {
        val sound = idleVoiceOf(npc.id) ?: return
        val world = player.world
        val (due, next) = idleDue(player.attr[NEXT_IDLE_CYCLE], world.currentCycle) { world.random(IDLE_MIN_CYCLES..IDLE_MAX_CYCLES) }
        player.attr[NEXT_IDLE_CYCLE] = next
        if (due) FamiliarAudio.play(player, sound, volume = VOLUME, at = npc.tile)
    }

    /** Talk-to on a pet: its voice once, from the pet, heard by the player and the players around it. */
    fun play(
        player: Player,
        npc: Npc,
    ) {
        val sound = talk[npc.id] ?: return
        FamiliarAudio.play(player, sound, volume = VOLUME, at = npc.tile)
    }
}
