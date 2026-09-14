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

/** OSRS-IMPORT batch "deadstaves" against the OSRS Wiki infoboxes and the imported cache definitions. */
class OsrsDeadStavesImportTests {
    private val staves = listOf(Items.STAFF_OF_THE_DEAD, Items.TOXIC_STAFF_UNCHARGED, Items.TOXIC_STAFF_OF_THE_DEAD)

    @Test
    fun `the three staves carry the wiki stats and 75 Attack plus 75 Magic`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val yml = root.filter { it.path("id").asInt() in staves }.associateBy { it.path("id").asInt() }
        staves.forEach { id ->
            val eq = yml.getValue(id).path("equipment")
            assertEquals(3, eq.path("equip_slot").asInt(), "$id weapon slot")
            assertEquals(55, eq.path("attack_stab").asInt(), "$id stab")
            assertEquals(70, eq.path("attack_slash").asInt(), "$id slash")
            assertEquals(72, eq.path("melee_strength").asInt(), "$id strength")
            assertEquals(15.0, eq.path("magic_damage").asDouble(), "$id magic damage")
            assertEquals(mapOf(0 to 75, 6 to 75), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }, "$id reqs")
        }
        assertEquals(17, yml.getValue(Items.STAFF_OF_THE_DEAD).path("equipment").path("attack_magic").asInt())
        assertEquals(17, yml.getValue(Items.TOXIC_STAFF_UNCHARGED).path("equipment").path("attack_magic").asInt())
        assertEquals(25, yml.getValue(Items.TOXIC_STAFF_OF_THE_DEAD).path("equipment").path("attack_magic").asInt(), "+8 when charged")
        assertEquals(false, yml.getValue(Items.TOXIC_STAFF_OF_THE_DEAD).path("tradeable").asBoolean())
        assertEquals(true, yml.getValue(Items.TOXIC_STAFF_UNCHARGED).path("tradeable").asBoolean())
    }

    @Test
    fun `the cache names, special bar and worn Check option`() {
        val store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val definitions = DefinitionSet()
            definitions.load(store, ItemDef::class.java)
            assertEquals("Staff of the Dead",definitions.get(ItemDef::class.java, Items.STAFF_OF_THE_DEAD).name)
            assertEquals("Toxic staff (uncharged)", definitions.get(ItemDef::class.java, Items.TOXIC_STAFF_UNCHARGED).name)
            assertEquals("Toxic staff of the dead", definitions.get(ItemDef::class.java, Items.TOXIC_STAFF_OF_THE_DEAD).name)
            assertEquals(listOf("Check"), definitions.get(ItemDef::class.java, Items.TOXIC_STAFF_OF_THE_DEAD).equipmentMenu.filterNotNull())
            assertEquals(Items.STAFF_OF_THE_DEAD, definitions.get(ItemDef::class.java, Items.STAFF_OF_THE_DEAD_NOTED).noteLinkId)
            assertEquals(Items.TOXIC_STAFF_UNCHARGED, definitions.get(ItemDef::class.java, Items.TOXIC_STAFF_UNCHARGED_NOTED).noteLinkId)
        } finally {
            store.close()
        }
    }
}
