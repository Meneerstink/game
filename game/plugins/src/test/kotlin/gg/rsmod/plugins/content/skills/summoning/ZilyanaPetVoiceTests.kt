package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import io.mockk.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ZilyanaPetVoiceTests {
    @Test
    fun `Zilyana uses her shield once for appear, Talk-to and due idle, other pets keep their voice and volume`() {
        Pets.load(File("../../data/cfg/pets/pets.tsv"), File("../../data/cfg/pets/pet_tiers.tsv"), File("../../data/cfg/pets/pets_667.tsv"))
        val rows = PetVoices.parse(File("../../data/cfg/pets/pet_voices.tsv").readLines())
        val zilyana = Pets.variants.single { it.pet == "Pet Zilyana" }
        // Compare all other pets against the previously approved table; only Zilyana's row changes.
        PetVoices.install(rows.map { if (it.pet == "Pet Zilyana") it.copy(localSynth = 10523) else it }, Pets.variants)
        val previous = Pets.variants.associate { it.npc to PetVoices.idleVoiceOf(it.npc) }
        PetVoices.install(rows, Pets.variants)
        Pets.variants.filter { it.pet != "Pet Zilyana" }.forEach {
            assertEquals(previous[it.npc], PetVoices.idleVoiceOf(it.npc), it.name)
        }
        assertEquals(10280, PetVoices.voiceOf(zilyana.npc))
        assertEquals(10280, PetVoices.idleVoiceOf(zilyana.npc))
        mockkObject(FamiliarAudio)
        try {
            every { FamiliarAudio.play(any(), any(), any(), any(), any(), any(), any()) } just Runs
            val player = mockk<Player>(relaxed = true)
            val world = mockk<World>(relaxed = true)
            val npc = mockk<Npc>(relaxed = true)
            val tile = Tile(2440, 3086)
            var cycle = 100
            every { player.world } returns world
            every { player.attr } returns AttributeMap()
            every { world.currentCycle } answers { cycle }
            every { world.random(any<IntRange>()) } returns 900
            every { npc.id } returns zilyana.npc
            every { npc.tile } returns tile
            PetVoices.appear(player, npc)
            PetVoices.play(player, npc)
            PetVoices.idle(player, npc) // schedules, no voice yet
            verify(exactly = 2) { FamiliarAudio.play(player, 10280, 0, 256, 1, 255, tile) }
            cycle = 1000
            PetVoices.idle(player, npc)
            PetVoices.idle(player, npc) // same cycle cannot repeat
            verify(exactly = 3) { FamiliarAudio.play(player, 10280, 0, 256, 1, 255, tile) }
            val other = Pets.variants.first { it.pet == "Pet General Graardor" }
            every { npc.id } returns other.npc
            PetVoices.appear(player, npc)
            PetVoices.play(player, npc)
            verify(exactly = 2) { FamiliarAudio.play(player, previous.getValue(other.npc)!!, 0, 256, 1, 255, tile) }
            verify(exactly = 0) { FamiliarAudio.play(player, 10523, any(), any(), any(), any(), any()) }
        } finally {
            unmockkObject(FamiliarAudio)
        }
    }
}
