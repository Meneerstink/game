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

/** OSRS-IMPORT batch "sanguinesti": Sanguinesti staff stats, requirement and client params against the OSRS Wiki. */
class OsrsSanguinestiImportTests {
    private val staves = listOf(Items.SANGUINESTI_STAFF, Items.SANGUINESTI_STAFF_UNCHARGED)

    @Test
    fun `both staves have the wiki bonuses, speed 4, the staff weapon type and 82 Magic`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val yml = root.filter { it.path("id").asInt() in Items.SANGUINESTI_STAFF..Items.SANGUINESTI_STAFF_UNCHARGED_NOTED }.associateBy { it.path("id").asInt() }
        staves.forEach { id ->
            val eq = yml.getValue(id).path("equipment")
            assertEquals(25, eq.path("attack_magic").asInt(), "$id magic attack")
            assertEquals(-4, eq.path("attack_ranged").asInt(), "$id ranged attack")
            assertEquals(listOf(2, 3, 1, 15), listOf("defence_stab", "defence_slash", "defence_crush", "defence_magic").map { eq.path(it).asInt() }, "$id defences")
            assertEquals(4, eq.path("attack_speed").asInt(), "$id speed")
            assertEquals(1, eq.path("weapon_type").asInt(), "$id staff weapon type")
            assertEquals(mapOf(6 to 82), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }, "$id: wiki 82 Magic to wield")
        }
        assertTrue(yml.getValue(Items.SANGUINESTI_STAFF_UNCHARGED_NOTED).path("equipment").isNull)
    }

    @Test
    fun `the cache gives both staves the 667 staff class and only the charged staff a worn Check`() {
        val store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val definitions = DefinitionSet()
            definitions.load(store, ItemDef::class.java)
            staves.forEach { id ->
                val def = definitions.get(ItemDef::class.java, id)
                assertEquals(28, def.params[644], "$id render animation")
                assertEquals(1, def.params[686], "$id staff style set")
                assertEquals(82, def.params[750], "$id client requirement")
            }
            assertEquals(listOf("Check"), definitions.get(ItemDef::class.java, Items.SANGUINESTI_STAFF).equipmentMenu.filterNotNull())
            assertEquals(emptyList(), definitions.get(ItemDef::class.java, Items.SANGUINESTI_STAFF_UNCHARGED).equipmentMenu.filterNotNull())
            assertTrue("Charge" in definitions.get(ItemDef::class.java, Items.SANGUINESTI_STAFF_UNCHARGED).inventoryMenu.toList())
        } finally {
            store.close()
        }
    }
}
