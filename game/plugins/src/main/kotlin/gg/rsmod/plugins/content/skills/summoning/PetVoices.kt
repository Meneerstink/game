package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
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

    fun install(
        rows: List<Row>,
        variants: List<Pets.Variant>,
    ) {
        val start = rows.filter { it.line.isEmpty() }
        talk = variants.mapNotNull { v -> rowFor(start, v)?.let { v.npc to it.localSynth } }.toMap()
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

    /** Talk-to on a pet: its voice once, from the pet, heard by the player and the players around it. */
    fun play(
        player: Player,
        npc: Npc,
    ) {
        val sound = talk[npc.id] ?: return
        FamiliarAudio.play(player, sound, volume = VOLUME, at = npc.tile)
    }
}
