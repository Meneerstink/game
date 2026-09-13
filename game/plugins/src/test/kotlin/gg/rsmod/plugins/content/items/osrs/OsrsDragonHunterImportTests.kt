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
import gg.rsmod.plugins.content.combat.formula.Draconic
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT batch "dragonhunter" (tx-20260913-225240): Dragon hunter crossbow, Dragon hunter lance and Dragon warhammer
 * in the real cache and items.yml, the draconic target list and the Smash special. Expected values: OSRS Wiki pages.
 */
class OsrsDragonHunterImportTests {
    private fun reqs(id: Int) = YML.getValue(id).path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }

    @Test
    fun `the three weapons match their OSRS item pages`() {
        val dhcb = YML.getValue(Items.DRAGON_HUNTER_CROSSBOW).path("equipment")
        assertEquals(95, dhcb.path("attack_ranged").asInt())
        assertEquals(6, dhcb.path("attack_speed").asInt())
        assertEquals(-1, dhcb.path("equip_type").asInt(), "one-handed")
        assertEquals(17, dhcb.path("weapon_type").asInt())
        assertEquals(mapOf(4 to 70), reqs(Items.DRAGON_HUNTER_CROSSBOW))

        val lance = YML.getValue(Items.DRAGON_HUNTER_LANCE).path("equipment")
        assertEquals(listOf(85, 65, 65), listOf("attack_stab", "attack_slash", "attack_crush").map { lance.path(it).asInt() })
        assertEquals(70, lance.path("melee_strength").asInt())
        assertEquals(4, lance.path("attack_speed").asInt())
        assertEquals(14, lance.path("weapon_type").asInt())
        assertEquals(mapOf(0 to 78), reqs(Items.DRAGON_HUNTER_LANCE), "wiki: 78 Attack (cache 75 is outdated)")

        val dwh = YML.getValue(Items.DRAGON_WARHAMMER).path("equipment")
        assertEquals(listOf(-4, -4, 95), listOf("attack_stab", "attack_slash", "attack_crush").map { dwh.path(it).asInt() })
        assertEquals(85, dwh.path("melee_strength").asInt())
        assertEquals(6, dwh.path("attack_speed").asInt())
        assertEquals(10, dwh.path("weapon_type").asInt())
        assertEquals(mapOf(2 to 60), reqs(Items.DRAGON_WARHAMMER))
        val params = ItemDefCodec.describeOpcodes(CacheItemProbeTool.itemData(STORE, Items.DRAGON_WARHAMMER)!!).first { it.startsWith("249=") }
        assertTrue("000002af00000001" in params, "special attack bar param 687 = 1: $params")

        mapOf(
            Items.DRAGON_HUNTER_CROSSBOW to Items.DRAGON_HUNTER_CROSSBOW_NOTED, Items.DRAGON_HUNTER_LANCE to Items.DRAGON_HUNTER_LANCE_NOTED,
            Items.DRAGON_WARHAMMER to Items.DRAGON_WARHAMMER_NOTED,
        ).forEach { (base, note) -> assertEquals(base, DEFINITIONS.get(ItemDef::class.java, note).noteLinkId, "note $note") }
    }

    @Test
    fun `the dragon hunter crossbow fires bolts up to and including dragon bolts only`() {
        val dhcb = gg.rsmod.plugins.content.combat.strategy.ranged.weapon.CrossbowType.DRAGON_HUNTER_CROSSBOW
        assertEquals(Items.DRAGON_HUNTER_CROSSBOW, dhcb.item)
        (gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Bolts.OSRS_DRAGON_BOLT_FAMILY.toList() + Items.DRAGON_BOLTS + Items.RUNITE_BOLTS + Items.BRONZE_BOLTS)
            .forEach { assertTrue(it in dhcb.ammo, "accepts $it") }
        listOf(Items.RUNE_ARROW, Items.DRAGON_ARROW, Items.OSRS_DRAGON_JAVELIN).forEach { assertTrue(it !in dhcb.ammo, "rejects $it") }
    }

    @Test
    fun `the draconic list follows the wiki attribute page including its exclusions`() {
        listOf("black dragon", "frost dragon", "king black dragon", "skeletal wyvern", "iron dragon", "baby blue dragon").forEach {
            assertTrue(it in Draconic.NAMES, it)
        }
        assertEquals(setOf("elvarg", "revenant dragon", "zulrah"), Draconic.EXCLUDED)
        assertTrue(Draconic.NAMES.none { it in Draconic.EXCLUDED })
    }

    @Test
    fun `dragonbane multipliers match the item pages and are wired into both formulas`() {
        assertEquals(1.30, gg.rsmod.plugins.content.combat.formula.TargetModifiers.DHCB_ACCURACY)
        assertEquals(1.25, gg.rsmod.plugins.content.combat.formula.TargetModifiers.DHCB_DAMAGE)
        assertEquals(1.20, gg.rsmod.plugins.content.combat.formula.TargetModifiers.LANCE_ACCURACY)
        assertEquals(1.20, gg.rsmod.plugins.content.combat.formula.TargetModifiers.LANCE_DAMAGE)
        val melee = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/MeleeCombatFormula.kt").readText()
        assertTrue("TargetModifiers.meleeDragonbaneDamage(player, target)" in melee && "TargetModifiers.meleeDragonbaneAccuracy(player, target)" in melee)
        val modifiers = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/TargetModifiers.kt").readText()
        assertTrue("multiplier *= DHCB_ACCURACY" in modifiers && "multiplier *= DHCB_DAMAGE" in modifiers)
    }

    @Test
    fun `Smash costs 50 percent, deals 50 percent more damage and drains 30 percent of current Defence rounded down`() {
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/dragon_warhammer.plugin.kts").readText()
        assertTrue("SpecialAttacks.register(50, Items.DRAGON_WARHAMMER)" in script)
        assertTrue("specialAttackMultiplier = 1.5" in script)
        assertTrue("landHit && dealt > 0" in script, "the hit has to deal damage")
        assertTrue("fun smashReduction(level: Int): Int = level * 3 / 10" in script)
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
            YML = root.filter { it.path("id").asInt() >= Items.DRAGON_HUNTER_CROSSBOW }.associateBy { it.path("id").asInt() }
        }

        @AfterClass
        @JvmStatic
        fun close() {
            STORE.close()
        }
    }
}
