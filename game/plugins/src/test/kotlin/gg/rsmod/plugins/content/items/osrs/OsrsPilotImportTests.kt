package gg.rsmod.plugins.content.items.osrs

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.tools.importer.WornAppearanceRankTool
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.combine.CombinationData
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT pilot (tx-20260913-204038): the real production cache and `data/cfg/items.yml` agree
 * with the pinned OSRS build-240 definitions and the OSRS Wiki values recorded in
 * `C:\RSPS\OSRS_IMPORT_STATUS.md`, across the whole pilot roster rather than one exemplar.
 */
class OsrsPilotImportTests {
    private data class Expected(val id: Int, val name: String, val noteOf: Int? = null)

    private val roster =
        listOf(
            Expected(Items.OCCULT_NECKLACE, "Occult necklace"),
            Expected(Items.OCCULT_NECKLACE_NOTED, "Occult necklace", noteOf = Items.OCCULT_NECKLACE),
            Expected(Items.OCCULT_NECKLACE_OR, "Occult necklace (or)"),
            Expected(Items.OCCULT_ORNAMENT_KIT, "Occult ornament kit"),
            Expected(Items.OCCULT_ORNAMENT_KIT_NOTED, "Occult ornament kit", noteOf = Items.OCCULT_ORNAMENT_KIT),
            Expected(Items.AVERNIC_DEFENDER_HILT, "Avernic defender hilt"),
            Expected(Items.AVERNIC_DEFENDER_HILT_NOTED, "Avernic defender hilt", noteOf = Items.AVERNIC_DEFENDER_HILT),
            Expected(Items.AVERNIC_DEFENDER, "Avernic defender"),
            Expected(Items.AVERNIC_DEFENDER_BROKEN, "Avernic defender (broken)"),
            Expected(Items.BELLES_FOLLY, "Belle's folly"),
            Expected(Items.BELLES_FOLLY_NOTED, "Belle's folly", noteOf = Items.BELLES_FOLLY),
            Expected(Items.BELLES_FOLLY_TARNISHED, "Belle's folly (tarnished)"),
            Expected(Items.BELLES_FOLLY_TARNISHED_NOTED, "Belle's folly (tarnished)", noteOf = Items.BELLES_FOLLY_TARNISHED),
        )

    @Test
    fun `every pilot id exists in the cache and items yml with matching names and note links`() {
        roster.forEach { e ->
            val def = DEFINITIONS.get(ItemDef::class.java, e.id)
            val yml = YML[e.id] ?: error("items.yml has no entry for ${e.id} (${e.name})")
            assertEquals(e.name, yml.path("name").asText(), "items.yml name of ${e.id}")
            if (e.noteOf != null) {
                assertEquals(e.noteOf, def.noteLinkId, "note ${e.id} must link to ${e.noteOf}")
                assertEquals(e.id, DEFINITIONS.get(ItemDef::class.java, e.noteOf).noteLinkId, "base ${e.noteOf} must link to its note ${e.id}")
            } else {
                assertEquals(e.name, def.name, "cache name of ${e.id}")
            }
        }
    }

    @Test
    fun `wearable pilot items carry the OSRS stats and the client worn-list rank`() {
        // OSRS Wiki 2026-09-13 values; the cache params of build 240 carry the same numbers.
        assertBonuses(Items.OCCULT_NECKLACE, slot = 2, magicAttack = 12, prayer = 2, magicDamage = 5, reqs = mapOf(6 to 70))
        assertBonuses(Items.OCCULT_NECKLACE_OR, slot = 2, magicAttack = 12, prayer = 2, magicDamage = 5, reqs = emptyMap())
        assertBonuses(
            Items.AVERNIC_DEFENDER, slot = 5, stab = 30, slash = 29, crush = 28, magicAttack = -5, rangedAttack = -4,
            defStab = 30, defSlash = 29, defCrush = 28, defMagic = -5, defRanged = -4, strength = 8, reqs = mapOf(0 to 70, 1 to 70),
        )
        assertBonuses(Items.BELLES_FOLLY, slot = 3, stab = 100, slash = 78, crush = -3, defStab = 20, defSlash = 15, defCrush = -9, strength = 102, reqs = mapOf(0 to 65))
        assertEquals(5, YML.getValue(Items.BELLES_FOLLY).path("equipment").path("attack_speed").asInt())
        assertEquals(5, YML.getValue(Items.BELLES_FOLLY).path("equipment").path("weapon_type").asInt(), "stab sword uses the 667 sword style set")

        listOf(Items.OCCULT_NECKLACE, Items.OCCULT_NECKLACE_OR, Items.AVERNIC_DEFENDER, Items.BELLES_FOLLY).forEach { id ->
            val rank = WornAppearanceRankTool.rank(STORE, id)
            assertTrue(rank.targetQualifies, "$id must have worn models")
            assertEquals(rank.rank, YML.getValue(id).path("equipment").path("appearance_id").asInt(), "appearance_id of $id")
        }
        listOf(Items.OCCULT_ORNAMENT_KIT, Items.AVERNIC_DEFENDER_HILT, Items.AVERNIC_DEFENDER_BROKEN, Items.BELLES_FOLLY_TARNISHED).forEach { id ->
            assertTrue(YML.getValue(id).path("equipment").isNull, "$id is not wearable")
        }
    }

    @Test
    fun `combinations and dismantles are two-way`() {
        val avernic = CombinationData.AVERNIC_DEFENDER
        assertEquals(setOf(Items.AVERNIC_DEFENDER_HILT, Items.DRAGON_DEFENDER), avernic.items.toSet())
        assertEquals(Items.AVERNIC_DEFENDER, avernic.resultItem)
        val occult = CombinationData.OCCULT_NECKLACE_OR
        assertEquals(setOf(Items.OCCULT_ORNAMENT_KIT, Items.OCCULT_NECKLACE), occult.items.toSet())
        assertEquals(Items.OCCULT_NECKLACE_OR, occult.resultItem)
        assertTrue("Dismantle" in DEFINITIONS.get(ItemDef::class.java, Items.OCCULT_NECKLACE_OR).inventoryMenu)
        assertTrue("Dismantle" in DEFINITIONS.get(ItemDef::class.java, Items.AVERNIC_DEFENDER).inventoryMenu)
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/osrs_pilot_items.plugin.kts").readText()
        assertTrue("item = Items.OCCULT_NECKLACE_OR, option = \"Dismantle\"" in script)
        assertTrue("item = Items.AVERNIC_DEFENDER, option = \"Dismantle\"" in script)
    }

    @Suppress("LongParameterList")
    private fun assertBonuses(
        id: Int,
        slot: Int,
        stab: Int = 0,
        slash: Int = 0,
        crush: Int = 0,
        magicAttack: Int = 0,
        rangedAttack: Int = 0,
        defStab: Int = 0,
        defSlash: Int = 0,
        defCrush: Int = 0,
        defMagic: Int = 0,
        defRanged: Int = 0,
        strength: Int = 0,
        prayer: Int = 0,
        magicDamage: Int = 0,
        reqs: Map<Int, Int>,
    ) {
        val eq = YML.getValue(id).path("equipment")
        val expected =
            mapOf(
                "equip_slot" to slot, "attack_stab" to stab, "attack_slash" to slash, "attack_crush" to crush,
                "attack_magic" to magicAttack, "attack_ranged" to rangedAttack, "defence_stab" to defStab,
                "defence_slash" to defSlash, "defence_crush" to defCrush, "defence_magic" to defMagic,
                "defence_ranged" to defRanged, "melee_strength" to strength, "prayer" to prayer, "magic_damage" to magicDamage,
                "ranged_strength" to 0,
            )
        expected.forEach { (key, value) -> assertEquals(value, eq.path(key).asInt(), "$id $key") }
        val actualReqs = eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }
        assertEquals(reqs, actualReqs, "$id skill_reqs")
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var STORE: CacheLibrary
        private lateinit var YML: Map<Int, JsonNode>

        @BeforeClass
        @JvmStatic
        fun load() {
            STORE = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(STORE)
            val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            YML = root.filter { it.path("id").asInt() >= Items.OCCULT_NECKLACE }.associateBy { it.path("id").asInt() }
        }

        @AfterClass
        @JvmStatic
        fun close() {
            STORE.close()
        }
    }
}
