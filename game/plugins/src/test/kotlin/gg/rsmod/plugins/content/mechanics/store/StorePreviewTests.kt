package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.skills.summoning.Pets
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-10-10 ("de preview van de pets kloppen niet ... liefst hoe die in game staat"): every pet the stores sell is previewed
 * as its npc. The item -> npc tables are the OSRS pet table and the revision-667 one ported from the Darkan donor.
 */
class StorePreviewTests {
    private val rs667 = StorePreview.parse667(File("../../data/cfg/pets/rs667_pets.tsv").readLines())
    private val osrs = Pets.parseTable(File("../../data/cfg/pets/pets.tsv").readLines()).associate { it.item to it.npc }

    @Test
    fun `the Darkan port maps the revision-667 pets to their npcs`() {
        assertEquals(3505, rs667[Items.HELLKITTEN])
        assertEquals(6901, rs667[Items.BABY_DRAGON]) // 12470 "Baby dragon" = the grown stage of hatchling 12469
        assertEquals(6903, rs667[Items.BABY_DRAGON_12472])
        assertEquals(6969, rs667[Items.BULLDOG_PUPPY_12522])
    }

    @Test
    fun `every store pet except the pet rock and the quest-only baby icefiend has an npc to show`() {
        val osrsPets = StorePets.entries(File("../../" + Pets.TABLE_PATH.removePrefix("./")), File("../../" + Pets.TIERS_PATH.removePrefix("./")))
        val shownAsPets = StoreCatalogue.ENTRIES.filter { it.category == "Pets" }.map { it.purchaseItem } + osrsPets.map { it.purchaseItem }
        assertTrue(osrsPets.isNotEmpty(), "OSRS pet stock")
        val withoutNpc = shownAsPets.filter { it !in rs667 && it !in osrs && it != Items.PET_ROCK && it != Items.BABY_ICEFIEND }
        assertEquals(emptyList(), withoutNpc, "store pets without an npc preview")
    }
}
