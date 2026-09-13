package gg.rsmod.plugins.content.items.osrs

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.CrossbowType
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT batch "sunlight" (tx-20260913-230535): Hunters' sunlight crossbow, antler bolts and antlers in the real
 * cache and items.yml, the ammunition wiring and the antler fletching recipe. Expected values: OSRS Wiki item pages.
 */
class OsrsSunlightImportTests {
    @Test
    fun `the crossbow matches its item page including both wield requirements`() {
        val eq = YML.getValue(Items.HUNTERS_SUNLIGHT_CROSSBOW).path("equipment")
        assertEquals(79, eq.path("attack_ranged").asInt())
        assertEquals(4, eq.path("attack_speed").asInt())
        assertEquals(-1, eq.path("equip_type").asInt(), "one-handed")
        assertEquals(17, eq.path("weapon_type").asInt())
        assertEquals(mapOf(4 to 66, 21 to 50), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() })
        assertEquals(Items.HUNTERS_SUNLIGHT_CROSSBOW, DEFINITIONS.get(ItemDef::class.java, Items.HUNTERS_SUNLIGHT_CROSSBOW_NOTED).noteLinkId)
    }

    @Test
    fun `antler bolts have their ranged strength and fire only from the sunlight crossbow`() {
        mapOf(Items.SUNLIGHT_ANTLER_BOLTS to 55, Items.MOONLIGHT_ANTLER_BOLTS to 60).forEach { (id, strength) ->
            val eq = YML.getValue(id).path("equipment")
            assertEquals(strength, eq.path("ranged_strength").asInt(), "$id strength")
            assertEquals(13, eq.path("equip_slot").asInt(), "$id ammo slot")
            assertTrue(DEFINITIONS.get(ItemDef::class.java, id).stackable, "$id stacks")
            assertTrue(id in RangedProjectile.BOLTS.items, "$id bolt projectile")
        }
        val crossbow = CrossbowType.HUNTERS_SUNLIGHT_CROSSBOW
        assertEquals(setOf(Items.SUNLIGHT_ANTLER_BOLTS, Items.MOONLIGHT_ANTLER_BOLTS), crossbow.ammo.toSet())
        assertTrue(Items.KEBBIT_BOLTS !in crossbow.ammo, "cannot fire kebbit bolts")
        CrossbowType.values.filter { it != crossbow }.forEach { assertTrue(Items.SUNLIGHT_ANTLER_BOLTS !in it.ammo, "${it.name} rejects antler bolts") }
        assertEquals(Items.SUNLIGHT_ANTLER, DEFINITIONS.get(ItemDef::class.java, Items.SUNLIGHT_ANTLER_NOTED).noteLinkId)
        assertEquals(Items.MOONLIGHT_ANTLER, DEFINITIONS.get(ItemDef::class.java, Items.MOONLIGHT_ANTLER_NOTED).noteLinkId)
    }

    @Test
    fun `antler fletching and the attack range follow the wiki`() {
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/skills/fletching/antlerbolts/antler_bolts.plugin.kts").readText()
        assertTrue("AntlerRecipe(Items.SUNLIGHT_ANTLER, Items.SUNLIGHT_ANTLER_BOLTS, level = 62, experience = 10.0)" in script)
        assertTrue("AntlerRecipe(Items.MOONLIGHT_ANTLER, Items.MOONLIGHT_ANTLER_BOLTS, level = 72, experience = 12.1)" in script)
        assertTrue("val BOLTS_PER_ANTLER = 12" in script)
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText()
        assertTrue("Items.HUNTERS_SUNLIGHT_CROSSBOW -> 8" in strategy)
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
            YML = root.filter { it.path("id").asInt() >= Items.HUNTERS_SUNLIGHT_CROSSBOW }.associateBy { it.path("id").asInt() }
        }

        @AfterClass
        @JvmStatic
        fun close() {
            STORE.close()
        }
    }
}
