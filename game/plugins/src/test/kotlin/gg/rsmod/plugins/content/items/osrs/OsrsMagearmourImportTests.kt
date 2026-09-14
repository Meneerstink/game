package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.combine.CombinationData
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "magearmour" against the OSRS Wiki set and kit pages. */
class OsrsMagearmourImportTests {
    private val yml by lazy { ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile()).toList() }

    private fun reqsByName(name: String) =
        yml.filter { it.path("name").asText() == name && !it.path("equipment").isNull && !it.path("equipment").isMissingNode }
            .map { node -> node.path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() } }

    @Test
    fun `every kit piece combines, dismantles to its base and follows the sourced death rule`() {
        val pieces =
            listOf(
                Items.LIGHT_INFINITY_HAT, Items.LIGHT_INFINITY_TOP, Items.LIGHT_INFINITY_BOTTOMS, Items.DARK_INFINITY_HAT, Items.DARK_INFINITY_TOP,
                Items.DARK_INFINITY_BOTTOMS, Items.TWISTED_ANCESTRAL_HAT, Items.TWISTED_ANCESTRAL_ROBE_TOP, Items.TWISTED_ANCESTRAL_ROBE_BOTTOM,
                Items.ELDER_CHAOS_TOP_OR, Items.ELDER_CHAOS_ROBE_OR, Items.ELDER_CHAOS_HOOD_OR, Items.DAGONHAI_HAT_OR, Items.DAGONHAI_ROBE_TOP_OR,
                Items.DAGONHAI_ROBE_BOTTOM_OR,
            )
        pieces.forEach { piece ->
            val ornament = OsrsOrnamentKits.forOrnamented(piece) ?: error("$piece has no kit row")
            val combo = CombinationData.values().firstOrNull { it.resultItem == piece } ?: error("$piece has no combination")
            assertEquals(setOf(ornament.kit, ornament.base), combo.items.toSet(), "$piece combination")
        }
        // Tradeable colour kits: base + kit to the PKer; untradeable Bounty Hunter kits: no sourced rule (SOURCE_GAP).
        assertTrue(OsrsOrnamentKits.forPvpConversion(Items.TWISTED_ANCESTRAL_HAT) != null)
        assertTrue(OsrsOrnamentKits.forPvpConversion(Items.LIGHT_INFINITY_TOP) != null)
        assertNull(OsrsOrnamentKits.forPvpConversion(Items.ELDER_CHAOS_TOP_OR))
        assertNull(OsrsOrnamentKits.forPvpConversion(Items.DAGONHAI_HAT_OR))
    }

    @Test
    fun `wear requirements follow the wiki set pages`() {
        val infinity = mapOf(6 to 50, 1 to 25) // "Infinity robes require 25 Defence and 50 Magic to wear."
        listOf("Infinity hat", "Infinity top", "Infinity bottoms", "Light infinity hat", "Dark infinity bottoms").forEach { name ->
            reqsByName(name).forEach { assertEquals(infinity, it, name) }
        }
        val dagonhai = mapOf(6 to 70, 1 to 40) // "require level 70 Magic and 40 Defence to wear."
        listOf("Dagon'hai hat", "Dagon'hai robe top", "Dagon'hai robe bottom (or)").forEach { name -> reqsByName(name).forEach { assertEquals(dagonhai, it, name) } }
        assertEquals(listOf(mapOf(6 to 75, 1 to 65)), reqsByName("Ancestral hat"))
        assertEquals(listOf(mapOf(6 to 40)), reqsByName("Elder chaos top"))
    }
}
