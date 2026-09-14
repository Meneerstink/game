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

/** OSRS-IMPORT batch "tridents": item stats, requirements and client params against the OSRS Wiki and the 667 staff class. */
class OsrsTridentsImportTests {
    private val seas = listOf(Items.TRIDENT_OF_THE_SEAS, Items.TRIDENT_OF_THE_SEAS_FULL, Items.UNCHARGED_TRIDENT, Items.TRIDENT_OF_THE_SEAS_E, Items.UNCHARGED_TRIDENT_E)
    private val swamp = listOf(Items.TRIDENT_OF_THE_SWAMP, Items.UNCHARGED_TOXIC_TRIDENT, Items.TRIDENT_OF_THE_SWAMP_E, Items.UNCHARGED_TOXIC_TRIDENT_E)

    @Test
    fun `every trident has the wiki bonuses, speed 4, the staff weapon type and its Magic requirement`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val yml = root.filter { it.path("id").asInt() in Items.TRIDENT_OF_THE_SEAS..Items.UNCHARGED_TOXIC_TRIDENT_E_NOTED }.associateBy { it.path("id").asInt() }
        (seas + swamp).forEach { id ->
            val eq = yml.getValue(id).path("equipment")
            val magicAttack = if (id in swamp) 25 else 15
            assertEquals(magicAttack, eq.path("attack_magic").asInt(), "$id magic attack")
            assertEquals(listOf(2, 3, 1, 15), listOf("defence_stab", "defence_slash", "defence_crush", "defence_magic").map { eq.path(it).asInt() }, "$id defences")
            assertEquals(4, eq.path("attack_speed").asInt(), "$id speed")
            assertEquals(3, eq.path("equip_slot").asInt())
            assertEquals(1, eq.path("weapon_type").asInt(), "$id staff weapon type")
            val required = if (id in swamp) 78 else 75
            assertEquals(mapOf(6 to required), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }, "$id requirement")
        }
        assertTrue(yml.getValue(Items.MAGIC_FANG).path("equipment").isNull, "the fang is not wearable")
    }

    @Test
    fun `the cache gives every trident the 667 staff style set, render animation and worn Check`() {
        val store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val definitions = DefinitionSet()
            definitions.load(store, ItemDef::class.java)
            (seas + swamp).forEach { id ->
                val def = definitions.get(ItemDef::class.java, id)
                assertEquals(28, def.params[644], "$id render animation (667 Staff of air)")
                assertEquals(1, def.params[686], "$id staff style set")
                assertEquals(listOf("Check"), def.equipmentMenu.filterNotNull(), "$id worn menu")
            }
            listOf(Items.TRIDENT_OF_THE_SEAS_FULL_NOTED, Items.UNCHARGED_TRIDENT_NOTED, Items.MAGIC_FANG_NOTED).forEach { id ->
                assertTrue(definitions.get(ItemDef::class.java, id).noted, "$id is a note")
            }
        } finally {
            store.close()
        }
    }
}
