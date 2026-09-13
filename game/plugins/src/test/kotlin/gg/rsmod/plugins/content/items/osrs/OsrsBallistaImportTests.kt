package gg.rsmod.plugins.content.items.osrs

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.tools.importer.CacheItemProbeTool
import gg.rsmod.game.tools.importer.ItemDefCodec
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Javelins
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.CrossbowType
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT batch "ballista" (tx-20260913-222518): Heavy ballista, its materials, javelin tips and the OSRS javelins, in
 * the real cache and items.yml, plus the ammunition wiring. Expected values are the OSRS Wiki item pages.
 */
class OsrsBallistaImportTests {
    /** OSRS Wiki javelin pages: ranged strength per tier. */
    private val tiers =
        listOf(
            Javelins.OSRS_BRONZE_JAVELINS to 25, Javelins.OSRS_IRON_JAVELINS to 42, Javelins.OSRS_STEEL_JAVELINS to 64,
            Javelins.OSRS_MITHRIL_JAVELINS to 85, Javelins.OSRS_ADAMANT_JAVELINS to 102, Javelins.OSRS_RUNE_JAVELINS to 124,
            Javelins.OSRS_AMETHYST_JAVELINS to 135, Javelins.OSRS_DRAGON_JAVELINS to 150,
        )

    @Test
    fun `heavy ballista matches the OSRS item page`() {
        val eq = YML.getValue(Items.HEAVY_BALLISTA).path("equipment")
        assertEquals(125, eq.path("attack_ranged").asInt())
        assertEquals(15, eq.path("ranged_strength").asInt())
        assertEquals(7, eq.path("attack_speed").asInt())
        assertEquals(3, eq.path("equip_slot").asInt())
        assertEquals(5, eq.path("equip_type").asInt(), "two-handed")
        assertEquals(17, eq.path("weapon_type").asInt())
        // 75 Ranged only: the cache's Defence 33 is the effective level from Daero's training, not a wield requirement.
        assertEquals(mapOf(4 to 75), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() })
        val params = ItemDefCodec.describeOpcodes(CacheItemProbeTool.itemData(STORE, Items.HEAVY_BALLISTA)!!).first { it.startsWith("249=") }
        assertTrue("000002af00000001" in params, "special attack bar param 687 = 1: $params")
        mapOf(
            Items.HEAVY_BALLISTA to Items.HEAVY_BALLISTA_NOTED, Items.HEAVY_FRAME to Items.HEAVY_FRAME_NOTED,
            Items.BALLISTA_LIMBS to Items.BALLISTA_LIMBS_NOTED, Items.BALLISTA_SPRING to Items.BALLISTA_SPRING_NOTED,
            Items.MONKEY_TAIL to Items.MONKEY_TAIL_NOTED,
        ).forEach { (base, note) -> assertEquals(base, DEFINITIONS.get(ItemDef::class.java, note).noteLinkId, "note $note") }
    }

    @Test
    fun `every OSRS javelin is ammunition with its tier's ranged strength and no requirement`() {
        assertEquals(32, Javelins.BALLISTA_JAVELINS.distinct().size)
        tiers.forEach { (ids, strength) ->
            ids.forEach { id ->
                val eq = YML.getValue(id).path("equipment")
                assertEquals(strength, eq.path("ranged_strength").asInt(), "$id ranged strength")
                assertEquals(13, eq.path("equip_slot").asInt(), "$id ammo slot")
                assertTrue(eq.path("skill_reqs").isMissingNode, "$id has no wield requirement")
                assertTrue(DEFINITIONS.get(ItemDef::class.java, id).name.startsWith(YML.getValue(ids[0]).path("name").asText()), "$id name")
            }
        }
        listOf(
            Items.JAVELIN_SHAFT, Items.BRONZE_JAVELIN_TIPS, Items.IRON_JAVELIN_TIPS, Items.STEEL_JAVELIN_TIPS, Items.MITHRIL_JAVELIN_TIPS,
            Items.ADAMANT_JAVELIN_TIPS, Items.RUNE_JAVELIN_TIPS, Items.DRAGON_JAVELIN_TIPS, Items.AMETHYST_JAVELIN_TIPS,
            Items.HEAVY_FRAME, Items.BALLISTA_LIMBS, Items.BALLISTA_SPRING, Items.MONKEY_TAIL,
        ).forEach { assertTrue(YML.getValue(it).path("equipment").isNull, "$it is not wearable") }
    }

    @Test
    fun `the ballista fires only OSRS javelins and the 667 thrown javelins are unchanged`() {
        val ballista = CrossbowType.HEAVY_BALLISTA
        Javelins.BALLISTA_JAVELINS.forEach { javelin ->
            assertTrue(javelin in ballista.ammo, "ballista accepts $javelin")
            assertTrue(javelin !in Javelins.JAVELINS, "$javelin is not a 667 thrown javelin")
            assertTrue(RangedProjectile.values.any { javelin in it.items && it.type == gg.rsmod.plugins.api.ProjectileType.JAVELIN }, "$javelin has a javelin projectile")
        }
        Javelins.JAVELINS.forEach { assertTrue(it !in ballista.ammo, "ballista rejects 667 thrown javelin $it") }
        assertTrue(Items.OSRS_DRAGON_BOLTS !in ballista.ammo)
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
            YML = root.filter { it.path("id").asInt() >= Items.HEAVY_BALLISTA }.associateBy { it.path("id").asInt() }
        }

        @AfterClass
        @JvmStatic
        fun close() {
            STORE.close()
        }
    }
}
