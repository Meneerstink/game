package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.combine.CombinationData
import java.io.File
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

    /** id -> (attack_magic, defence_magic, magic_damage as a whole percent, prayer). items.yml already stores magic_damage as whole percent (1.0 = 1%). */
    private fun bonuses(id: Int): List<Int> {
        val eq = yml.first { it.path("id").asInt() == id }.path("equipment")
        return listOf(eq.path("attack_magic").asInt(), eq.path("defence_magic").asInt(), eq.path("magic_damage").asDouble().toInt(), eq.path("prayer").asInt())
    }

    @Test
    fun `audit round 2026-09-17b remaining armour matches the sourced OSRS Wiki infobox bonuses exactly`() {
        // Swampbark (OSRS Wiki raw wikitext, audited 2026-09-17): amagic/dmagic/mdmg/prayer per piece.
        assertEquals(listOf(4, 5, 0, 0), bonuses(Items.SWAMPBARK_HELM), "Swampbark helm")
        assertEquals(listOf(15, 21, 0, 0), bonuses(Items.SWAMPBARK_BODY), "Swampbark body")
        assertEquals(listOf(10, 15, 0, 0), bonuses(Items.SWAMPBARK_LEGS), "Swampbark legs")
        assertEquals(listOf(3, 4, 0, 0), bonuses(Items.SWAMPBARK_GAUNTLETS), "Swampbark gauntlets")
        assertEquals(listOf(3, 4, 0, 0), bonuses(Items.SWAMPBARK_BOOTS), "Swampbark boots")
        // Elder chaos (base + (or), identical - ornament kits are cosmetic only).
        listOf(Items.ELDER_CHAOS_TOP to 10, Items.ELDER_CHAOS_TOP_OR to 10).forEach { (id, amagic) -> assertEquals(listOf(amagic, 8, 1, 0), bonuses(id), "$id") }
        listOf(Items.ELDER_CHAOS_ROBE to 6, Items.ELDER_CHAOS_ROBE_OR to 6).forEach { (id, amagic) -> assertEquals(listOf(amagic, 6, 1, 0), bonuses(id), "$id") }
        listOf(Items.ELDER_CHAOS_HOOD to 5, Items.ELDER_CHAOS_HOOD_OR to 5).forEach { (id, amagic) -> assertEquals(listOf(amagic, 4, 1, 0), bonuses(id), "$id") }
        // Infinity robes (pre-existing 667 content, re-confirmed against the wiki).
        assertEquals(listOf(22, 22, 1, 0), bonuses(Items.INFINITY_TOP), "Infinity top")
        assertEquals(listOf(6, 6, 1, 0), bonuses(Items.INFINITY_HAT), "Infinity hat")
        assertEquals(listOf(17, 17, 1, 0), bonuses(Items.INFINITY_BOTTOMS), "Infinity bottoms")
        assertEquals(listOf(5, 5, 0, 0), bonuses(Items.INFINITY_GLOVES), "Infinity gloves")
        assertEquals(listOf(5, 5, 0, 0), bonuses(Items.INFINITY_BOOTS), "Infinity boots")
        // Dagon'hai robe top/bottom: OSRS Wiki raw wikitext confirms (verified twice, independently, per side) a real
        // asymmetry between the base pieces (mdmg 1) and the (or) ornamented pieces (mdmg 0) - not a fetch error,
        // fixed in items.yml 2026-09-17b (was wrongly 1.0 on both (or) pieces before this audit).
        assertEquals(listOf(25, 21, 1, 2), bonuses(Items.DAGONHAI_ROBE_TOP), "Dagon'hai robe top")
        assertEquals(listOf(25, 21, 0, 2), bonuses(Items.DAGONHAI_ROBE_TOP_OR), "Dagon'hai robe top (or)")
        assertEquals(listOf(18, 14, 1, 2), bonuses(Items.DAGONHAI_ROBE_BOTTOM), "Dagon'hai robe bottom")
        assertEquals(listOf(18, 14, 0, 2), bonuses(Items.DAGONHAI_ROBE_BOTTOM_OR), "Dagon'hai robe bottom (or)")
    }

    @Test
    fun `Virtus robes match the wiki bonuses - the wear requirement deliberately keeps the native 667 cache's 80 80 80`() {
        // Virtus is pre-existing native 667 content (no `local_item_id` entry in OSRS_IMPORT_MASTER.yml), not a
        // freshly OSRS-imported item - unlike a fresh import, its wield requirement is baked into the actual
        // revision-667 client cache (params 749-758) and is enforced there regardless of what items.yml says, so
        // `ItemSkillRequirementsCacheAuditTests` requires items.yml to match the cache's own {1=80, 3=80, 6=80}
        // (Defence/Hitpoints/Magic) by default. The OSRS Wiki instead gives "level 78 in Magic and 75 Defence, no
        // Hitpoints" for the modern item - a real SOURCE_CONFLICT between the native cache and modern OSRS, same
        // category as this project's existing decision (d) overrides (Dagon'hai robes, Abyssal tentacle) in that
        // test file, but NOT resolved here: an audit-round attempt to "fix" this to the wiki value broke the cache
        // audit test, which is the correct, more specific arbiter for native (non-imported) equipment. Left at the
        // cache value; a genuine wiki override would need an explicit addition to `osrsOverrides` there, which is
        // an owner-level call this round did not make.
        val virtusReqs = mapOf(1 to 80, 3 to 80, 6 to 80)
        val intact = listOf(Items.VIRTUS_MASK, Items.VIRTUS_MASK_20161, Items.VIRTUS_ROBE_TOP, Items.VIRTUS_ROBE_TOP_20165, Items.VIRTUS_ROBE_LEGS, Items.VIRTUS_ROBE_LEGS_20169)
        val broken = listOf(Items.VIRTUS_MASK_BROKEN, Items.VIRTUS_ROBE_TOP_BROKEN, Items.VIRTUS_ROBE_LEGS_BROKEN)
        (intact + broken).forEach { assertEquals(virtusReqs, reqsById(it), "$it wear requirement") }
        assertEquals(listOf(8, 6, 2, 1), bonuses(Items.VIRTUS_MASK), "Virtus mask")
        assertEquals(listOf(35, 31, 2, 2), bonuses(Items.VIRTUS_ROBE_TOP), "Virtus robe top")
        assertEquals(listOf(26, 22, 2, 1), bonuses(Items.VIRTUS_ROBE_LEGS), "Virtus robe legs")
        // "Each piece of Virtus robes gives a 2% magic damage bonus... an additional 3% magic damage is given per
        // piece [with Ancient Magicks], taking the bonus to 5% per piece for a total of 15% for the full set."
        assertEquals(0.03, VirtusRobes.ANCIENT_MAGICKS_BONUS_PER_PIECE)
        assertEquals(setOf(Items.VIRTUS_MASK, Items.VIRTUS_MASK_20161, Items.VIRTUS_ROBE_TOP, Items.VIRTUS_ROBE_TOP_20165, Items.VIRTUS_ROBE_LEGS, Items.VIRTUS_ROBE_LEGS_20169), VirtusRobes.PIECES)
        val formula = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/MagicCombatFormula.kt").readText()
        assertTrue("VirtusRobes.ancientMagicksBonus(pawn, spell)" in formula, "the Ancient Magicks bonus must be wired into the max hit additive")
    }

    private fun reqsById(id: Int) = yml.first { it.path("id").asInt() == id }.path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }
}
