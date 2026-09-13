package gg.rsmod.plugins.content.items.osrs

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.tools.importer.WornAppearanceRankTool
import gg.rsmod.plugins.api.cfg.Items
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT batch "capesrings" (tx-20260913-210358): Infernal cape, Mage Arena II imbued god capes,
 * Wilderness rings and Ring of suffering variants - cache, items.yml and client ranks agree for the whole roster.
 */
class OsrsCapesRingsImportTests {
    private val roster =
        linkedMapOf(
            Items.INFERNAL_CAPE to "Infernal cape",
            Items.INFERNAL_CAPE_BROKEN to "Infernal cape (broken)",
            Items.INFERNAL_CAPE_L to "Infernal cape (l)",
            Items.IMBUED_SARADOMIN_CAPE to "Imbued Saradomin cape",
            Items.IMBUED_GUTHIX_CAPE to "Imbued Guthix cape",
            Items.IMBUED_ZAMORAK_CAPE to "Imbued Zamorak cape",
            Items.IMBUED_SARADOMIN_CAPE_BROKEN to "Imbued Saradomin cape (broken)",
            Items.IMBUED_GUTHIX_CAPE_BROKEN to "Imbued Guthix cape (broken)",
            Items.IMBUED_ZAMORAK_CAPE_BROKEN to "Imbued Zamorak cape (broken)",
            Items.RING_OF_THE_GODS to "Ring of the gods",
            Items.TYRANNICAL_RING to "Tyrannical ring",
            Items.TREASONOUS_RING to "Treasonous ring",
            Items.RING_OF_THE_GODS_I to "Ring of the gods (i)",
            Items.TYRANNICAL_RING_I to "Tyrannical ring (i)",
            Items.TREASONOUS_RING_I to "Treasonous ring (i)",
            Items.RING_OF_SUFFERING to "Ring of suffering",
            Items.RING_OF_SUFFERING_I to "Ring of suffering (i)",
            Items.RING_OF_SUFFERING_R to "Ring of suffering (r)",
            Items.RING_OF_SUFFERING_RI to "Ring of suffering (ri)",
        )

    @Test
    fun `every item exists in cache and items yml and tradeable rings are noted`() {
        roster.forEach { (id, name) ->
            assertEquals(name, DEFINITIONS.get(ItemDef::class.java, id).name, "cache name of $id")
            assertEquals(name, YML.getValue(id).path("name").asText(), "items.yml name of $id")
        }
        mapOf(
            Items.RING_OF_THE_GODS to Items.RING_OF_THE_GODS_NOTED,
            Items.TYRANNICAL_RING to Items.TYRANNICAL_RING_NOTED,
            Items.TREASONOUS_RING to Items.TREASONOUS_RING_NOTED,
            Items.RING_OF_SUFFERING to Items.RING_OF_SUFFERING_NOTED,
        ).forEach { (base, note) ->
            assertEquals(note, DEFINITIONS.get(ItemDef::class.java, base).noteLinkId)
            assertEquals(base, DEFINITIONS.get(ItemDef::class.java, note).noteLinkId)
        }
        listOf(Items.INFERNAL_CAPE_BROKEN, Items.IMBUED_SARADOMIN_CAPE_BROKEN, Items.IMBUED_GUTHIX_CAPE_BROKEN, Items.IMBUED_ZAMORAK_CAPE_BROKEN)
            .forEach { assertTrue(YML.getValue(it).path("equipment").isNull, "$it is not wearable") }
    }

    @Test
    fun `stats match the OSRS item pages and the build-240 cache`() {
        // Infernal cape page: +4 melee attack, +1 magic/ranged attack, +12 all defences, +8 str, +2 prayer.
        listOf(Items.INFERNAL_CAPE, Items.INFERNAL_CAPE_L).forEach {
            stats(it, "attack_stab" to 4, "attack_slash" to 4, "attack_crush" to 4, "attack_magic" to 1, "attack_ranged" to 1,
                "defence_stab" to 12, "defence_slash" to 12, "defence_crush" to 12, "defence_magic" to 12, "defence_ranged" to 12,
                "melee_strength" to 8, "prayer" to 2)
        }
        // Imbued god cape page: +15 magic attack, 2 % magic damage, 75 Magic; defences from the cache params (3/3/3/15/0).
        listOf(Items.IMBUED_SARADOMIN_CAPE, Items.IMBUED_GUTHIX_CAPE, Items.IMBUED_ZAMORAK_CAPE).forEach {
            stats(it, "attack_magic" to 15, "defence_stab" to 3, "defence_slash" to 3, "defence_crush" to 3, "defence_magic" to 15, "magic_damage" to 2)
            assertEquals(mapOf(6 to 75), reqs(it))
        }
        // Ring of suffering page: (i) doubles the base ring (+20 defences, +4 prayer); (r)/(ri) share those stats.
        listOf(Items.RING_OF_SUFFERING, Items.RING_OF_SUFFERING_R).forEach { stats(it, *defences(10), "prayer" to 2) }
        listOf(Items.RING_OF_SUFFERING_I, Items.RING_OF_SUFFERING_RI).forEach { stats(it, *defences(20), "prayer" to 4) }
        stats(Items.TYRANNICAL_RING, "attack_crush" to 4, "defence_crush" to 4)
        stats(Items.TYRANNICAL_RING_I, "attack_crush" to 8, "defence_crush" to 8)
        stats(Items.TREASONOUS_RING, "attack_stab" to 4, "defence_stab" to 4)
        stats(Items.TREASONOUS_RING_I, "attack_stab" to 8, "defence_stab" to 8)
        stats(Items.RING_OF_THE_GODS, *defences(1), "prayer" to 4)
        stats(Items.RING_OF_THE_GODS_I, *defences(1), "prayer" to 8)
    }

    @Test
    fun `capes carry their client worn-list rank`() {
        listOf(Items.INFERNAL_CAPE, Items.INFERNAL_CAPE_L, Items.IMBUED_SARADOMIN_CAPE, Items.IMBUED_GUTHIX_CAPE, Items.IMBUED_ZAMORAK_CAPE).forEach { id ->
            val rank = WornAppearanceRankTool.rank(STORE, id)
            assertTrue(rank.targetQualifies, "$id must have worn models")
            assertEquals(rank.rank, YML.getValue(id).path("equipment").path("appearance_id").asInt(), "appearance_id of $id")
        }
    }

    private fun defences(value: Int) =
        arrayOf("defence_stab" to value, "defence_slash" to value, "defence_crush" to value, "defence_magic" to value, "defence_ranged" to value)

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

    private fun reqs(id: Int) = YML.getValue(id).path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }

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
            YML = root.filter { it.path("id").asInt() >= Items.INFERNAL_CAPE }.associateBy { it.path("id").asInt() }
        }

        @AfterClass
        @JvmStatic
        fun close() {
            STORE.close()
        }
    }
}
