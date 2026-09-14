package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.combine.CombinationData
import gg.rsmod.plugins.content.magic.MagicStaves
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "magegeara" against the OSRS Wiki item pages. */
class OsrsMagegearAImportTests {
    private val yml by lazy {
        ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            .filter { it.path("id").asInt() in Items.MALEDICTION_WARD..Items.VENATOR_RING_NOTED }.associateBy { it.path("id").asInt() }
    }

    private fun reqs(id: Int) = yml.getValue(id).path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }

    @Test
    fun `avernic treads take each pair of boots once in any order and give them back`() {
        var treads = Items.AVERNIC_TREADS
        listOf(AvernicTreads.Boots.ETERNAL, AvernicTreads.Boots.PRIMORDIAL, AvernicTreads.Boots.PEGASIAN).forEach { boots ->
            treads = AvernicTreads.upgraded(treads, boots)!!
        }
        assertEquals(Items.AVERNIC_TREADS_MAX, treads)
        assertNull(AvernicTreads.upgraded(Items.AVERNIC_TREADS_PR, AvernicTreads.Boots.PRIMORDIAL), "a pair is applied once")
        assertEquals(Items.AVERNIC_TREADS_PR_ET, AvernicTreads.upgraded(Items.AVERNIC_TREADS_ET, AvernicTreads.Boots.PRIMORDIAL))
        assertEquals(setOf(Items.PRIMORDIAL_BOOTS, Items.PEGASIAN_BOOTS, Items.ETERNAL_BOOTS), AvernicTreads.appliedBoots(Items.AVERNIC_TREADS_MAX).toSet())
        assertEquals(4_000, AvernicTreads.TEARS_PER_PAIR)
        assertTrue("AvernicTreads.appliedBoots(slotItem.item.id)" in File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/PvpDeathBreakables.kt").readText())
    }

    @Test
    fun `kodai wand, requirements and recipes`() {
        assertTrue(Items.KODAI_WAND in MagicStaves.WATER_RUNE.staves, "unlimited water runes")
        assertEquals(0.15, KodaiWand.RUNE_SAVE_CHANCE)
        assertTrue("KodaiWand.savesRunes(p)" in File("src/main/kotlin/gg/rsmod/plugins/content/magic/MagicSpells.kt").readText())
        assertEquals(mapOf(6 to 80), reqs(Items.KODAI_WAND))
        assertEquals(mapOf(1 to 60), reqs(Items.MALEDICTION_WARD))
        assertEquals(mapOf(1 to 60), reqs(Items.ODIUM_WARD_OR), "the (or) ward keeps the 60 Defence requirement")
        assertEquals(mapOf(4 to 80, 1 to 80), reqs(Items.AVERNIC_TREADS_MAX).filterKeys { it == 4 || it == 1 })
        val combos = CombinationData.values.associateBy { it.resultItem }
        assertEquals(setOf(Items.KODAI_INSIGNIA, Items.MASTER_WAND), combos.getValue(Items.KODAI_WAND).items.toSet())
        assertEquals(80, combos.getValue(Items.SEERS_ICON).levelRequired)
        assertEquals(400.0, combos.getValue(Items.ARCHER_ICON).experience)
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/osrs_magegear.plugin.kts").readText()
        assertTrue("player.inventory.remove(Items.SOUL_RUNE, 10_000)" in plugin && "player.addXp(Skills.PRAYER, 260.0)" in plugin)
        assertTrue("player.inventory.remove(Items.BLOOD_RUNE, 500)" in plugin && "player.addXp(Skills.RUNECRAFTING, 200.0)" in plugin)
        assertTrue(OsrsOrnamentKits.forOrnamented(Items.ELIDINIS_WARD_OR)?.base == Items.ELIDINIS_WARD_F)
        assertNull(OsrsOrnamentKits.forPvpConversion(Items.ELIDINIS_WARD_OR))
    }
}
