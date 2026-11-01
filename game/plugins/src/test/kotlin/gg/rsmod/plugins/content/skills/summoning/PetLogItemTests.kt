package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.quests.tab.CollectionLogPages
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** N297 report P18: a pet variant received fills the pet's one Collection Log slot (OSRS), not a slot that does not exist. */
class PetLogItemTests {
    @Test
    fun `a red baby chinchompa fills the Baby chinchompa slot, an item with its own slot stays itself`() {
        Pets.load(File("../../data/cfg/pets/pets.tsv"), File("../../data/cfg/pets/pet_tiers.tsv"))
        val base = Pets.baseItem("Baby chinchompa")!!
        val variants = File("../../data/cfg/pets/pets.tsv").readLines().filter { it.startsWith("Baby chinchompa\t") }.map { it.split('\t')[2].toInt() }
        val player = mockk<Player>(relaxed = true)
        CollectionLogPages.install(listOf(CollectionLogPages.Page("pets", CollectionLogPages.Category.OTHER, "All Pets", emptyList(), listOf(base))))
        try {
            variants.forEach { assertEquals(base, Pets.logItem(player, "Baby chinchompa", it), "variant $it") }
            // A variant with a slot of its own keeps it.
            CollectionLogPages.install(listOf(CollectionLogPages.Page("pets", CollectionLogPages.Category.OTHER, "All Pets", emptyList(), listOf(base, variants.last()))))
            assertEquals(variants.last(), Pets.logItem(player, "Baby chinchompa", variants.last()))
        } finally {
            CollectionLogPages.reset()
        }
    }
}
