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

/** OSRS-IMPORT batch "blightedsacks": the four sacks are stackable, tradeable, unwearable items with their wiki names. */
class OsrsBlightedSacksImportTests {
    private val names =
        mapOf(
            Items.BLIGHTED_ANCIENT_ICE_SACK to "Blighted ancient ice sack",
            Items.BLIGHTED_ENTANGLE_SACK to "Blighted entangle sack",
            Items.BLIGHTED_TELEPORT_SPELL_SACK to "Blighted teleport spell sack",
            Items.BLIGHTED_VENGEANCE_SACK to "Blighted vengeance sack",
        )

    @Test
    fun `the cache holds the four sacks as stackable items`() {
        val store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val definitions = DefinitionSet()
            definitions.load(store, ItemDef::class.java)
            names.forEach { (id, name) ->
                val def = definitions.get(ItemDef::class.java, id)
                assertEquals(name, def.name)
                assertTrue(def.stackable, "$name is stackable (wiki)")
            }
        } finally {
            store.close()
        }
    }

    @Test
    fun `items yml lists the sacks as tradeable and not wearable`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val yml = root.filter { it.path("id").asInt() in names.keys }.associateBy { it.path("id").asInt() }
        assertEquals(names.keys, yml.keys)
        yml.values.forEach {
            assertTrue(it.path("tradeable").asBoolean(), "${it.path("name").asText()} tradeable (wiki)")
            assertTrue(it.path("equipment").isNull, "${it.path("name").asText()} is not wearable")
        }
    }
}
