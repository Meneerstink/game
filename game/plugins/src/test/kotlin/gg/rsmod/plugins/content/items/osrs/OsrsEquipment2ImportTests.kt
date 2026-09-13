package gg.rsmod.plugins.content.items.osrs

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.tools.importer.WornAppearanceRankTool
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.combine.CombinationData
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT batch "equipment2" (tx-20260913-205509): cache, items.yml and item actions agree with
 * the pinned build-240 definitions and the OSRS Wiki values in `C:\RSPS\OSRS_IMPORT_STATUS.md`.
 */
class OsrsEquipment2ImportTests {
    /** name -> (id, noted id or null). */
    private val roster =
        linkedMapOf(
            "Necklace of anguish" to (Items.NECKLACE_OF_ANGUISH to Items.NECKLACE_OF_ANGUISH_NOTED),
            "Anguish ornament kit" to (Items.ANGUISH_ORNAMENT_KIT to Items.ANGUISH_ORNAMENT_KIT_NOTED),
            "Necklace of anguish (or)" to (Items.NECKLACE_OF_ANGUISH_OR to null),
            "Amulet of torture" to (Items.AMULET_OF_TORTURE to Items.AMULET_OF_TORTURE_NOTED),
            "Torture ornament kit" to (Items.TORTURE_ORNAMENT_KIT to Items.TORTURE_ORNAMENT_KIT_NOTED),
            "Amulet of torture (or)" to (Items.AMULET_OF_TORTURE_OR to null),
            "Tormented bracelet" to (Items.TORMENTED_BRACELET to Items.TORMENTED_BRACELET_NOTED),
            "Tormented ornament kit" to (Items.TORMENTED_ORNAMENT_KIT to Items.TORMENTED_ORNAMENT_KIT_NOTED),
            "Tormented bracelet (or)" to (Items.TORMENTED_BRACELET_OR to null),
            "Amulet of rancour" to (Items.AMULET_OF_RANCOUR to Items.AMULET_OF_RANCOUR_NOTED),
            "Amulet of rancour (s)" to (Items.AMULET_OF_RANCOUR_S to null),
            "Etched araxyte fang" to (Items.ETCHED_ARAXYTE_FANG to Items.ETCHED_ARAXYTE_FANG_NOTED),
            "Ferocious gloves" to (Items.FEROCIOUS_GLOVES to null),
            "Hydra leather" to (Items.HYDRA_LEATHER to Items.HYDRA_LEATHER_NOTED),
            "Zaryte vambraces" to (Items.ZARYTE_VAMBRACES to Items.ZARYTE_VAMBRACES_NOTED),
            "Zenyte shard" to (Items.ZENYTE_SHARD to Items.ZENYTE_SHARD_NOTED),
            "Uncut zenyte" to (Items.UNCUT_ZENYTE to Items.UNCUT_ZENYTE_NOTED),
            "Zenyte" to (Items.ZENYTE to Items.ZENYTE_NOTED),
            "Masori mask" to (Items.MASORI_MASK to Items.MASORI_MASK_NOTED),
            "Masori body" to (Items.MASORI_BODY to Items.MASORI_BODY_NOTED),
            "Masori chaps" to (Items.MASORI_CHAPS to Items.MASORI_CHAPS_NOTED),
            "Masori mask (f)" to (Items.MASORI_MASK_F to Items.MASORI_MASK_F_NOTED),
            "Masori body (f)" to (Items.MASORI_BODY_F to Items.MASORI_BODY_F_NOTED),
            "Masori chaps (f)" to (Items.MASORI_CHAPS_F to Items.MASORI_CHAPS_F_NOTED),
            "Armadylean plate" to (Items.ARMADYLEAN_PLATE to Items.ARMADYLEAN_PLATE_NOTED),
        )

    @Test
    fun `every batch item and note exists in cache and items yml`() {
        roster.forEach { (name, ids) ->
            val (id, noted) = ids
            assertEquals(name, DEFINITIONS.get(ItemDef::class.java, id).name, "cache name of $id")
            assertEquals(name, YML.getValue(id).path("name").asText(), "items.yml name of $id")
            if (noted != null) {
                assertEquals(id, DEFINITIONS.get(ItemDef::class.java, noted).noteLinkId, "note $noted -> $id")
                assertEquals(noted, DEFINITIONS.get(ItemDef::class.java, id).noteLinkId, "$id -> note $noted")
                assertEquals(name, YML.getValue(noted).path("name").asText())
            }
        }
    }

    @Test
    fun `wiki-verified stats and requirements`() {
        // OSRS Wiki 2026-09-13 item pages (Ferocious gloves, Zaryte vambraces, Amulet of rancour, Occult/Tormented magic damage).
        stats(Items.FEROCIOUS_GLOVES, "attack_stab" to 16, "attack_slash" to 16, "attack_crush" to 16, "attack_magic" to -16, "attack_ranged" to -16, "melee_strength" to 14)
        reqs(Items.FEROCIOUS_GLOVES, mapOf(Skills.ATTACK to 80, Skills.DEFENCE to 80))
        stats(
            Items.ZARYTE_VAMBRACES, "attack_stab" to -8, "attack_slash" to -8, "attack_crush" to -8, "attack_ranged" to 18,
            "defence_stab" to 8, "defence_slash" to 8, "defence_crush" to 8, "defence_magic" to 5, "defence_ranged" to 8,
            "ranged_strength" to 2, "prayer" to 1,
        )
        reqs(Items.ZARYTE_VAMBRACES, mapOf(Skills.RANGED to 80, Skills.DEFENCE to 45))
        stats(Items.AMULET_OF_RANCOUR, "attack_stab" to 25, "attack_slash" to 25, "attack_crush" to 25, "attack_magic" to -6, "attack_ranged" to -8, "melee_strength" to 12, "prayer" to 2)
        reqs(Items.AMULET_OF_RANCOUR, mapOf(HITPOINTS_SKILL_ID to 90))
        listOf(Items.TORMENTED_BRACELET, Items.TORMENTED_BRACELET_OR).forEach { stats(it, "attack_magic" to 10, "prayer" to 2, "magic_damage" to 5) }
        listOf(Items.NECKLACE_OF_ANGUISH, Items.NECKLACE_OF_ANGUISH_OR).forEach { stats(it, "attack_ranged" to 15, "ranged_strength" to 5, "prayer" to 2) }
        listOf(Items.AMULET_OF_TORTURE, Items.AMULET_OF_TORTURE_OR).forEach { stats(it, "attack_stab" to 15, "attack_slash" to 15, "attack_crush" to 15, "melee_strength" to 10, "prayer" to 2) }
    }

    @Test
    fun `worn items carry client ranks and hidden body parts`() {
        roster.values.map { it.first }.filter { !YML.getValue(it).path("equipment").isNull }.forEach { id ->
            val rank = WornAppearanceRankTool.rank(STORE, id)
            assertTrue(rank.targetQualifies, "$id must have worn models")
            assertEquals(rank.rank, YML.getValue(id).path("equipment").path("appearance_id").asInt(), "appearance_id of $id")
        }
        // Masori mask hides hair and jaw, bodies hide arms (OSRS wearPos2/3 8/11 and 6).
        listOf(Items.MASORI_MASK, Items.MASORI_MASK_F).forEach {
            val eq = YML.getValue(it).path("equipment")
            assertTrue(eq.path("remove_head").asBoolean() && eq.path("remove_beard").asBoolean(), "$it hides head and beard")
        }
        listOf(Items.MASORI_BODY, Items.MASORI_BODY_F).forEach { assertTrue(YML.getValue(it).path("equipment").path("remove_arms").asBoolean(), "$it hides arms") }
    }

    @Test
    fun `ornament kits combine and dismantle for the whole roster, rancour is a crafting upgrade`() {
        val kits = CombinationData.values().associateBy { it.resultItem }
        OsrsOrnamentKits.ALL.forEach { o ->
            assertEquals(setOf(o.kit, o.base), kits.getValue(o.ornamented).items.toSet(), "combine for ${o.ornamented}")
            assertTrue("Dismantle" in DEFINITIONS.get(ItemDef::class.java, o.ornamented).inventoryMenu, "${o.ornamented} has Dismantle")
        }
        val rancour = kits.getValue(Items.AMULET_OF_RANCOUR)
        assertEquals(setOf(Items.ETCHED_ARAXYTE_FANG, Items.AMULET_OF_TORTURE), rancour.items.toSet())
        assertEquals(Skills.CRAFTING, rancour.skill)
        assertEquals(86, rancour.levelRequired)
        assertEquals(500.0, rancour.experience)
    }

    private fun stats(
        id: Int,
        vararg expected: Pair<String, Int>,
    ) {
        val eq = YML.getValue(id).path("equipment")
        val all =
            listOf(
                "attack_stab", "attack_slash", "attack_crush", "attack_magic", "attack_ranged", "defence_stab", "defence_slash",
                "defence_crush", "defence_magic", "defence_ranged", "melee_strength", "ranged_strength", "prayer", "magic_damage",
            ).associateWith { 0 } + expected.toMap()
        all.forEach { (key, value) -> assertEquals(value, eq.path(key).asInt(), "$id $key") }
    }

    private fun reqs(
        id: Int,
        expected: Map<Int, Int>,
    ) {
        val actual = YML.getValue(id).path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }
        assertEquals(expected, actual, "$id skill_reqs")
    }

    companion object {
        /** Skill id 3 (Hitpoints in OSRS, Constitution in 667). */
        private const val HITPOINTS_SKILL_ID = 3
        private val DEFINITIONS = DefinitionSet()
        private lateinit var STORE: CacheLibrary
        private lateinit var YML: Map<Int, JsonNode>

        @BeforeClass
        @JvmStatic
        fun load() {
            STORE = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(STORE)
            val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            YML = root.filter { it.path("id").asInt() >= Items.NECKLACE_OF_ANGUISH }.associateBy { it.path("id").asInt() }
        }

        @AfterClass
        @JvmStatic
        fun close() {
            STORE.close()
        }
    }
}
