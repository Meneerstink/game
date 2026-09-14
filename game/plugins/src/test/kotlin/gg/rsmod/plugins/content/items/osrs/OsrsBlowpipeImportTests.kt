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
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Darts
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT batch "blowpipe" (tx-20260913-223749): Toxic blowpipe, empty blowpipe, Tanzanite fang, Zulrah's scales and
 * Amethyst dart in the real cache and items.yml, plus the special and combat wiring. Expected values: OSRS Wiki pages.
 */
class OsrsBlowpipeImportTests {
    @Test
    fun `toxic blowpipe and amethyst dart match the OSRS item pages`() {
        val pipe = YML.getValue(Items.TOXIC_BLOWPIPE).path("equipment")
        assertEquals(30, pipe.path("attack_ranged").asInt())
        assertEquals(20, pipe.path("ranged_strength").asInt())
        assertEquals(3, pipe.path("attack_speed").asInt())
        assertEquals(5, pipe.path("equip_type").asInt(), "two-handed")
        assertEquals(18, pipe.path("weapon_type").asInt())
        assertEquals(mapOf(4 to 75), pipe.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() })
        assertTrue(YML.getValue(Items.TOXIC_BLOWPIPE_EMPTY).path("equipment").isNull, "the empty blowpipe cannot be wielded")
        val params = ItemDefCodec.describeOpcodes(CacheItemProbeTool.itemData(STORE, Items.TOXIC_BLOWPIPE)!!).first { it.startsWith("249=") }
        assertTrue("000002af00000001" in params, "special attack bar param 687 = 1: $params")
        val dart = YML.getValue(Items.AMETHYST_DART).path("equipment")
        assertEquals(28, dart.path("ranged_strength").asInt())
        assertEquals(3, dart.path("equip_slot").asInt())
        mapOf(Items.TOXIC_BLOWPIPE_EMPTY to Items.TOXIC_BLOWPIPE_EMPTY_NOTED, Items.TANZANITE_FANG to Items.TANZANITE_FANG_NOTED)
            .forEach { (base, note) -> assertEquals(base, DEFINITIONS.get(ItemDef::class.java, note).noteLinkId, "note $note") }
        assertTrue(DEFINITIONS.get(ItemDef::class.java, Items.ZULRAHS_SCALES).stackable, "scales stack")
    }

    @Test
    fun `every loadable dart has a projectile and the amethyst dart is a thrown dart`() {
        Blowpipe.Dart.values().forEach { dart -> assertTrue(RangedProjectile.values.any { dart.itemId in it.items }, "${dart.name} projectile") }
        assertTrue(Items.AMETHYST_DART in Darts.DARTS)
    }

    @Test
    fun `Toxic Siphon and the combat strategy are wired to the blowpipe charge model`() {
        val special = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/toxic_blowpipe.plugin.kts").readText()
        // Batch blowpipes: the Blazing blowpipe ("a toxic blowpipe with a ... ornament kit") shares Toxic Siphon.
        assertTrue("SpecialAttacks.register(Blowpipe.SPECIAL_ENERGY, Items.TOXIC_BLOWPIPE, Items.BLAZING_BLOWPIPE)" in special)
        assertTrue("specialAttackMultiplier = Blowpipe.SIPHON_DAMAGE" in special && "specialAttackMultiplier = Blowpipe.SIPHON_ACCURACY" in special)
        assertTrue("Blowpipe.siphonHeal(dealt)" in special && "BlowpipeCombat.rollVenom" in special)
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText()
        assertTrue("BlowpipeCombat.fire(pawn, target)" in strategy && "BlowpipeCombat.rollVenom" in strategy)
        assertEquals(2, BlowpipeCombat.hitDelay(0, special = false))
        assertEquals(2, BlowpipeCombat.hitDelay(5, special = false))
        assertEquals(3, BlowpipeCombat.hitDelay(6, special = false))
        assertEquals(3, BlowpipeCombat.hitDelay(4, special = true))
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
            YML = root.filter { it.path("id").asInt() >= Items.TOXIC_BLOWPIPE }.associateBy { it.path("id").asInt() }
        }

        @AfterClass
        @JvmStatic
        fun close() {
            STORE.close()
        }
    }
}
