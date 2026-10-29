package gg.rsmod.plugins.content.skills.summoning

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** K02 N397: the OSRS pet chathead table - every row a pet follower of pets.tsv, its own OSRS sequence, every form of a pet alike. */
class PetChatheadsTests {
    private fun file(name: String) = listOf(File("../../data/cfg/pets/$name"), File("../data/cfg/pets/$name")).first { it.exists() }

    private val talk = PetChatheads.parse(file("pet_chatheads.tsv").readLines())
    private val pets = file("pets.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }

    @Test
    fun `every chathead row is an OSRS pet follower with an imported sequence`() {
        val followers = pets.map { it[3].toInt() }.toSet()
        assertTrue(talk.isNotEmpty())
        talk.forEach { (npc, seq) ->
            assertTrue(npc in followers, "npc $npc is no OSRS pet follower")
            assertTrue(seq > 0, "npc $npc sequence $seq")
        }
    }

    @Test
    fun `the pets with an OSRS chathead sequence have it on every form that shares the head`() {
        val byVariant = pets.groupBy({ it[1] }, { it[3].toInt() })
        listOf("Ikkle Hydra", "Rock golem", "Baby chinchompa", "Pet Snakeling", "Lil' Zik", "Little Nightmare", "Olmlet", "Noon", "Youngllef")
            .forEach { variant -> byVariant.getValue(variant).forEach { assertTrue(it in talk, "$variant ($it) has no chathead sequence") } }
        // A form with another head keeps still (Lil' Sot is not Lil' Zik).
        byVariant.getValue("Lil' Sot").forEach { assertEquals(null, talk[it], "Lil' Sot") }
    }
}
