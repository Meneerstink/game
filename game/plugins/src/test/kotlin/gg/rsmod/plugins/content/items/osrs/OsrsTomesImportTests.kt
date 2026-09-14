package gg.rsmod.plugins.content.items.osrs

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "tomes": Tome of Fire / Water, their empty versions and pages against the wiki and the upstream cache. */
class OsrsTomesImportTests {
    private val tomes = listOf(Items.TOME_OF_FIRE, Items.TOME_OF_FIRE_EMPTY, Items.TOME_OF_WATER, Items.TOME_OF_WATER_EMPTY)

    @Test
    fun `every tome is a shield with +8 magic attack and defence and 50 Magic`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val yml = root.filter { it.path("id").asInt() in Items.TOME_OF_FIRE..Items.TOME_SOAKED_PAGE }.associateBy { it.path("id").asInt() }
        tomes.forEach { id ->
            val eq = yml.getValue(id).path("equipment")
            assertEquals(5, eq.path("equip_slot").asInt(), "$id shield slot")
            assertEquals(8, eq.path("attack_magic").asInt(), "$id magic attack")
            assertEquals(8, eq.path("defence_magic").asInt(), "$id magic defence")
            assertEquals(mapOf(6 to 50), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }, "$id: 50 Magic to wield")
        }
        listOf(Items.TOME_BURNT_PAGE, Items.TOME_SOAKED_PAGE).forEach { assertTrue(yml.getValue(it).path("equipment").isNull, "$it not wearable") }
        // "The tome is only tradeable when empty."
        assertEquals(false, yml.getValue(Items.TOME_OF_FIRE).path("tradeable").asBoolean())
        assertEquals(true, yml.getValue(Items.TOME_OF_FIRE_EMPTY).path("tradeable").asBoolean())
    }

    @Test
    fun `the cache carries the worn Check and Pages options and stackable pages`() {
        val store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val definitions = DefinitionSet()
            definitions.load(store, ItemDef::class.java)
            listOf(Items.TOME_OF_FIRE, Items.TOME_OF_WATER).forEach {
                assertEquals(listOf("Check", "Pages"), definitions.get(ItemDef::class.java, it).equipmentMenu.filterNotNull(), "$it worn menu")
            }
            listOf(Items.TOME_OF_FIRE_EMPTY, Items.TOME_OF_WATER_EMPTY).forEach {
                assertEquals(listOf("Pages"), definitions.get(ItemDef::class.java, it).equipmentMenu.filterNotNull(), "$it worn menu")
            }
            assertEquals("Burnt page", definitions.get(ItemDef::class.java, Items.TOME_BURNT_PAGE).name)
            assertEquals("Soaked page", definitions.get(ItemDef::class.java, Items.TOME_SOAKED_PAGE).name)
            assertTrue(definitions.get(ItemDef::class.java, Items.TOME_BURNT_PAGE).stackable)
            assertTrue(definitions.get(ItemDef::class.java, Items.TOME_SOAKED_PAGE).stackable)
        } finally {
            store.close()
        }
    }
}
