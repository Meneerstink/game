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
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Bolts
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.CrossbowType
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT batch "crossbows" (tx-20260913-220043): crossbows, materials and the whole OSRS dragon bolt family in the
 * real cache and items.yml, their stack (count) variants and the ammunition wiring.
 */
class OsrsCrossbowsImportTests {
    private val gemBolts =
        listOf(
            Items.OPAL_DRAGON_BOLTS, Items.JADE_DRAGON_BOLTS, Items.PEARL_DRAGON_BOLTS, Items.TOPAZ_DRAGON_BOLTS, Items.SAPPHIRE_DRAGON_BOLTS,
            Items.EMERALD_DRAGON_BOLTS, Items.RUBY_DRAGON_BOLTS, Items.DIAMOND_DRAGON_BOLTS, Items.DRAGONSTONE_DRAGON_BOLTS, Items.ONYX_DRAGON_BOLTS,
        )
    private val enchantedBolts =
        listOf(
            Items.OPAL_DRAGON_BOLTS_E, Items.JADE_DRAGON_BOLTS_E, Items.PEARL_DRAGON_BOLTS_E, Items.TOPAZ_DRAGON_BOLTS_E, Items.SAPPHIRE_DRAGON_BOLTS_E,
            Items.EMERALD_DRAGON_BOLTS_E, Items.RUBY_DRAGON_BOLTS_E, Items.DIAMOND_DRAGON_BOLTS_E, Items.DRAGONSTONE_DRAGON_BOLTS_E, Items.ONYX_DRAGON_BOLTS_E,
        )
    private val allBolts = listOf(Items.OSRS_DRAGON_BOLTS, Items.OSRS_DRAGON_BOLTS_UNF) + gemBolts + enchantedBolts

    @Test
    fun `crossbows match the OSRS item pages`() {
        stats(Items.ARMADYL_CROSSBOW, attackRanged = 100, prayer = 1, requiredRanged = 70)
        stats(Items.ZARYTE_CROSSBOW, attackRanged = 110, prayer = 1, requiredRanged = 80, defences = listOf(14, 14, 12, 15, 16))
        stats(Items.DRAGON_CROSSBOW, attackRanged = 94, prayer = 0, requiredRanged = 64)
        listOf(Items.ARMADYL_CROSSBOW, Items.ZARYTE_CROSSBOW, Items.DRAGON_CROSSBOW).forEach {
            val eq = YML.getValue(it).path("equipment")
            assertEquals(6, eq.path("attack_speed").asInt(), "$it speed")
            assertEquals(17, eq.path("weapon_type").asInt(), "$it crossbow weapon type")
        }
        mapOf(
            Items.ARMADYL_CROSSBOW to Items.ARMADYL_CROSSBOW_NOTED, Items.ZARYTE_CROSSBOW to Items.ZARYTE_CROSSBOW_NOTED,
            Items.DRAGON_CROSSBOW to Items.DRAGON_CROSSBOW_NOTED, Items.NIHIL_HORN to Items.NIHIL_HORN_NOTED,
            Items.DRAGON_LIMBS to Items.DRAGON_LIMBS_NOTED, Items.MAGIC_STOCK to Items.MAGIC_STOCK_NOTED,
        ).forEach { (base, note) -> assertEquals(base, DEFINITIONS.get(ItemDef::class.java, note).noteLinkId, "note $note") }
        listOf(Items.DRAGON_LIMBS, Items.MAGIC_STOCK, Items.NIHIL_HORN, Items.NIHIL_SHARD, Items.DRAGON_CROSSBOW_U, Items.OSRS_DRAGON_BOLTS_UNF)
            .forEach { assertTrue(YML.getValue(it).path("equipment").isNull, "$it is not wearable") }
    }

    @Test
    fun `crossbows with a special show the special attack bar (client param 687)`() {
        // Param 687 = 1 reveals interface 884:19 (CS2 1136); the 667 Hand cannon, which has a special, carries it.
        listOf(Items.ARMADYL_CROSSBOW, Items.ZARYTE_CROSSBOW, Items.DRAGON_CROSSBOW).forEach { id ->
            val params = ItemDefCodec.describeOpcodes(CacheItemProbeTool.itemData(STORE, id) ?: error("no data for $id")).first { it.startsWith("249=") }
            assertTrue("000002af00000001" in params, "$id carries param 687 = 1: $params")
        }
    }

    @Test
    fun `every dragon bolt has +122 ranged strength, 64 Ranged and four imported stack variants`() {
        (listOf(Items.OSRS_DRAGON_BOLTS) + gemBolts + enchantedBolts).forEach { id ->
            val eq = YML.getValue(id).path("equipment")
            assertEquals(122, eq.path("ranged_strength").asInt(), "$id ranged strength")
            assertEquals(13, eq.path("equip_slot").asInt(), "$id ammo slot")
            assertEquals(mapOf(4 to 64), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }, "$id requirement")
        }
        allBolts.forEach { id ->
            val opcodes = ItemDefCodec.describeOpcodes(CacheItemProbeTool.itemData(STORE, id) ?: error("no data for $id"))
            val variants =
                (100..103).map { code ->
                    val hex = opcodes.first { it.startsWith("$code=") }.substringAfter("0x")
                    hex.substring(0, 4).toInt(16) to hex.substring(4, 8).toInt(16)
                }
            assertEquals(listOf(2, 3, 4, 5), variants.map { it.second }, "$id stack sizes")
            variants.forEach { (variant, _) ->
                assertTrue(variant in 22426..22534, "$id variant $variant is an imported count item")
                assertEquals("null", DEFINITIONS.get(ItemDef::class.java, variant).name, "count item $variant is nameless")
            }
        }
    }

    @Test
    fun `the new crossbows fire the whole dragon bolt family, the Rune crossbow does not`() {
        listOf(CrossbowType.ARMADYL_CROSSBOW, CrossbowType.ZARYTE_CROSSBOW, CrossbowType.DRAGON_CROSSBOW).forEach { crossbow ->
            (Bolts.OSRS_DRAGON_BOLT_FAMILY.toList() + Items.DRAGON_BOLTS + Items.RUNITE_BOLTS).forEach { bolt ->
                assertTrue(bolt in crossbow.ammo, "${crossbow.name} accepts $bolt")
            }
        }
        assertTrue(Items.OSRS_DRAGON_BOLTS !in CrossbowType.RUNE_CROSSBOW.ammo)
        // Owner decision (e): OSRS dragon bolts fly with the imported OSRS DRAGON_CROSSBOWBOLT_TRAVEL spotanim (fxpilot), not the 667 bolt.
        Bolts.OSRS_DRAGON_BOLT_FAMILY.forEach {
            assertTrue(it in RangedProjectile.OSRS_DRAGON_BOLTS.items, "$it has the OSRS dragon bolt projectile")
            assertTrue(it !in RangedProjectile.BOLTS.items, "$it is not also mapped to the 667 bolt projectile")
        }
        assertEquals(OsrsGfx.DRAGON_CROSSBOWBOLT_TRAVEL, RangedProjectile.OSRS_DRAGON_BOLTS.gfx)
        // ammo2 adds the poisoned OSRS dragon bolts to the same family (same crossbows, same projectile).
        val poisoned = listOf(Items.OSRS_DRAGON_BOLTS_P, Items.OSRS_DRAGON_BOLTS_P_PLUS, Items.OSRS_DRAGON_BOLTS_P_PLUS_PLUS)
        assertEquals((listOf(Items.OSRS_DRAGON_BOLTS) + gemBolts + enchantedBolts + poisoned).toSet(), Bolts.OSRS_DRAGON_BOLT_FAMILY.toSet())
    }

    private fun stats(
        id: Int,
        attackRanged: Int,
        prayer: Int,
        requiredRanged: Int,
        defences: List<Int> = listOf(0, 0, 0, 0, 0),
    ) {
        val eq = YML.getValue(id).path("equipment")
        assertEquals(attackRanged, eq.path("attack_ranged").asInt(), "$id ranged attack")
        assertEquals(prayer, eq.path("prayer").asInt(), "$id prayer")
        assertEquals(defences, listOf("defence_stab", "defence_slash", "defence_crush", "defence_magic", "defence_ranged").map { eq.path(it).asInt() }, "$id defences")
        assertEquals(mapOf(4 to requiredRanged), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }, "$id requirement")
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
            YML = root.filter { it.path("id").asInt() >= Items.ARMADYL_CROSSBOW }.associateBy { it.path("id").asInt() }
        }

        @AfterClass
        @JvmStatic
        fun close() {
            STORE.close()
        }
    }
}
