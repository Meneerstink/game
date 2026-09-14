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

/** OSRS-IMPORT batch "crystal": Bow of Faerdhinen, crystal armour, seeds and shards against the wiki and the upstream cache. */
class OsrsCrystalImportTests {
    @Test
    fun `the bow and armour carry the wiki stats and requirements, inactive items none`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val yml = root.filter { it.path("id").asInt() in Items.BOW_OF_FAERDHINEN..Items.ENHANCED_CRYSTAL_WEAPON_SEED_NOTED }.associateBy { it.path("id").asInt() }
        // Wiki: charged +128 ranged attack / +106 ranged strength, inactive +0 / +0, speed 5, 80 Ranged + 70 Agility.
        listOf(Items.BOW_OF_FAERDHINEN, Items.BOW_OF_FAERDHINEN_C).forEach {
            val eq = yml.getValue(it).path("equipment")
            assertEquals(128, eq.path("attack_ranged").asInt(), "$it")
            assertEquals(106, eq.path("ranged_strength").asInt(), "$it")
        }
        val inactive = yml.getValue(Items.BOW_OF_FAERDHINEN_INACTIVE).path("equipment")
        assertEquals(0, inactive.path("attack_ranged").asInt())
        assertEquals(0, inactive.path("ranged_strength").asInt())
        CrystalEquipment.BOWFA.forEach { id ->
            val eq = yml.getValue(id).path("equipment")
            assertEquals(5, eq.path("attack_speed").asInt(), "$id speed")
            assertEquals(16, eq.path("weapon_type").asInt(), "$id bow weapon type")
            assertEquals(mapOf(4 to 80, 16 to 70), eq.path("skill_reqs").associate { r -> r.path("skill").asInt() to r.path("level").asInt() }, "$id requirements")
        }
        CrystalEquipment.ARMOUR.forEach { piece ->
            val active = yml.getValue(piece.active).path("equipment")
            assertEquals(mapOf(1 to 70, 16 to 50), active.path("skill_reqs").associate { r -> r.path("skill").asInt() to r.path("level").asInt() }, "${piece.active}")
            assertTrue(active.path("attack_ranged").asInt() > 0, "${piece.active} has bonuses")
            assertEquals(0, yml.getValue(piece.inactive).path("equipment").path("attack_ranged").asInt(), "${piece.inactive} has none")
        }
        listOf(Items.CRYSTAL_ARMOUR_SEED, Items.CRYSTAL_SHARD, Items.ENHANCED_CRYSTAL_WEAPON_SEED).forEach {
            assertTrue(yml.getValue(it).path("equipment").isNull, "$it is not wearable")
        }
    }

    @Test
    fun `the cache gives the bow the 667 crystal bow class and the charged items a worn Check`() {
        val store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val definitions = DefinitionSet()
            definitions.load(store, ItemDef::class.java)
            CrystalEquipment.BOWFA.forEach { assertEquals(16, definitions.get(ItemDef::class.java, it).params[686], "$it bow style set") }
            val checked = listOf(Items.BOW_OF_FAERDHINEN) + CrystalEquipment.ARMOUR.map { it.active }
            checked.forEach { assertEquals(listOf("Check"), definitions.get(ItemDef::class.java, it).equipmentMenu.filterNotNull(), "$it worn menu") }
            val shard = definitions.get(ItemDef::class.java, Items.CRYSTAL_SHARD)
            assertEquals("Crystal shard", shard.name)
            assertTrue(shard.stackable)
        } finally {
            store.close()
        }
    }
}
