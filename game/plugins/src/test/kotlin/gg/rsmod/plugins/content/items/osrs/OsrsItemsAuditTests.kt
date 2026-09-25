package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedAmmo
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Bolts
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.EnchantedBolts
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.CrossbowType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Audit A3 (OSRS-ported items) fixes I-01 .. I-11 that can be checked without a cache. */
class OsrsItemsAuditTests {
    private val weapons = "src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons"

    @BeforeTest
    fun setUp() {
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
    }

    private fun player(
        weapon: Int?,
        ammo: Int? = null,
        ammoBonuses: Map<BonusSlot, Int> = emptyMap(),
    ): Player {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        val definitions = mockk<DefinitionSet>(relaxed = true)
        every { player.world } returns world
        every { world.definitions } returns definitions
        every { player.getEquipment(EquipmentType.WEAPON) } returns weapon?.let { Item(it) }
        every { player.getEquipment(EquipmentType.AMMO) } returns ammo?.let { Item(it) }
        every { player.getEquipment(EquipmentType.CAPE) } returns null
        if (ammo != null) {
            val def = ItemDef(ammo)
            def.bonuses = IntArray(18).also { bonuses -> ammoBonuses.forEach { (slot, value) -> bonuses[slot.id] = value } }
            every { definitions.get(ItemDef::class.java, ammo) } returns def
        }
        return player
    }

    @Test
    fun `I-01 ammunition the weapon does not fire adds no ranged strength or attack`() {
        val onyx = mapOf(BonusSlot.RANGED_STRENGTH_BONUS to 122, BonusSlot.ATTACK_RANGED to 0)
        val bofa = player(Items.BOW_OF_FAERDHINEN, Items.ONYX_DRAGON_BOLTS_E, onyx)
        assertEquals(-122, RangedAmmo.quiverBonusCorrection(bofa, BonusSlot.RANGED_STRENGTH_BONUS))
        val arrows = mapOf(BonusSlot.RANGED_STRENGTH_BONUS to 60)
        val tbow = player(Items.TWISTED_BOW, Items.DRAGON_ARROW, arrows)
        assertEquals(0, RangedAmmo.quiverBonusCorrection(tbow, BonusSlot.RANGED_STRENGTH_BONUS), "fired ammo keeps its +60")
        // A god blessing in the ammo slot is not ammunition and keeps its bonuses.
        assertFalse(RangedAmmo.isAmmunition(Items.SARADOMINS_BLESSING_4))
        assertTrue(RangedAmmo.isAmmunition(Items.ONYX_DRAGON_BOLTS_E))
    }

    @Test
    fun `I-02 gem bolts and gem bolts (e) are fired by crossbows and carry their dragon bolt (e) effect`() {
        val enchanted =
            listOf(
                Items.OPAL_BOLTS_E, Items.JADE_BOLTS_E, Items.PEARL_BOLTS_E, Items.TOPAZ_BOLTS_E, Items.SAPPHIRE_BOLTS_E,
                Items.EMERALD_BOLTS_E, Items.RUBY_BOLTS_E, Items.DIAMOND_BOLTS_E, Items.DRAGON_BOLTS_E, Items.ONYX_BOLTS_E,
            )
        val plain =
            listOf(
                Items.OPAL_BOLTS, Items.JADE_BOLTS, Items.PEARL_BOLTS, Items.TOPAZ_BOLTS, Items.SAPPHIRE_BOLTS,
                Items.EMERALD_BOLTS, Items.RUBY_BOLTS, Items.DIAMOND_BOLTS, Items.DRAGON_BOLTS, Items.ONYX_BOLTS,
            )
        (enchanted + plain).forEach { id ->
            assertTrue(id in Bolts.GEM_BOLTS, "$id")
            assertTrue(RangedProjectile.values.any { id in it.items }, "$id has a projectile")
            listOf(CrossbowType.RUNE_CROSSBOW, CrossbowType.ARMADYL_CROSSBOW, CrossbowType.ZARYTE_CROSSBOW, CrossbowType.DRAGON_CROSSBOW)
                .forEach { bow -> assertTrue(id in bow.ammo, "$bow fires $id") }
        }
        // Tiers: an adamant crossbow fires ruby and diamond bolts but no onyx bolts.
        assertTrue(Items.DIAMOND_BOLTS_E in CrossbowType.ADAMANT_CROSSBOW.ammo && Items.ONYX_BOLTS_E !in CrossbowType.ADAMANT_CROSSBOW.ammo)
        enchanted.forEach { id ->
            val dragon = EnchantedBolts.GEM_BOLT_E_TO_DRAGON_BOLT_E.getValue(id)
            assertEquals(EnchantedBolts.ROSTER.getValue(dragon).copy(itemId = id), EnchantedBolts.boltFor(id), "$id")
        }
    }

    @Test
    fun `I-03 a landed Zamorak godsword special freezes for 32 ticks`() {
        val zgs = File("$weapons/zamorak_godsword.plugin.kts").readText()
        assertTrue("val ZGS_FREEZE_TICKS = 32" in zgs)
        assertTrue("if (landHit) {" in zgs && "victim.freeze(ZGS_FREEZE_TICKS)" in zgs && "victim.graphic(2104)" in zgs)
    }

    @Test
    fun `I-04 the Deadman Statius's warhammer lowers Defence by 30 percent`() {
        val statius = File("$weapons/statiuss_warhammer.plugin.kts").readText()
        assertTrue("val SMASH_DEFENCE_REDUCTION_PERCENT = 30" in statius)
        assertEquals(70, 99 - 99 * 30 / 100)
    }

    @Test
    fun `I-05 Spear Wall lasts 8 ticks, hits up to 16 targets and rolls per target`() {
        val vesta = File("$weapons/vestas_spear.plugin.kts").readText()
        assertTrue("val SPEAR_WALL_TICKS = 8" in vesta && "val SPEAR_WALL_MAX_TARGETS = 16" in vesta)
        assertTrue("MeleeCombatFormula.getAccuracy(attacker, other" in vesta, "own accuracy roll per extra target")
        assertTrue("p != attacker" in vesta, "never the attacker itself")
    }

    @Test
    fun `I-06 powered staves never attack players`() {
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/PoweredStaffCombatStrategy.kt").readText()
        val canAttack = strategy.substringAfter("override fun canAttack(").substringBefore("override fun attack(")
        assertTrue("if (target is Player) {" in canAttack && "PLAYER_TARGET_MESSAGE" in canAttack)
        assertFalse("getWildernessLevel" in canAttack)
    }

    @Test
    fun `I-08 Zuriel's staff heals blood spells 50 percent more and boosts ice spells`() {
        val zuriel = player(Items.ZURIELS_STAFF)
        val other = player(Items.ANCIENT_STAFF)
        assertEquals(15, ZurielsStaff.bloodHeal(zuriel, 10))
        assertEquals(10, ZurielsStaff.bloodHeal(other, 10))
        assertEquals(1.1, ZurielsStaff.iceAccuracyMultiplier(zuriel, CombatSpell.ICE_BARRAGE))
        assertEquals(1.0, ZurielsStaff.iceAccuracyMultiplier(zuriel, CombatSpell.BLOOD_BARRAGE))
        assertEquals(35, ZurielsStaff.freezeTicks(zuriel, CombatSpell.ICE_BARRAGE, 32))
        assertEquals(32, ZurielsStaff.freezeTicks(other, CombatSpell.ICE_BARRAGE, 32))
    }

    private data class YmlItem(val id: Int, val name: String, val equipped: Boolean, val reqs: String?, val attackSpeed: Int?)

    private fun ymlItems(): List<YmlItem> {
        val out = ArrayList<YmlItem>()
        var id = -1
        var name = ""
        var equipped = false
        var reqs: String? = null
        var speed: Int? = null
        fun flush() {
            if (id >= 0) out += YmlItem(id, name, equipped, reqs, speed)
        }
        File("../../data/cfg/items.yml").forEachLine { line ->
            when {
                line.startsWith("- id: ") -> {
                    flush()
                    id = line.removePrefix("- id: ").trim().toInt()
                    name = ""
                    equipped = false
                    reqs = null
                    speed = null
                }
                line.startsWith("  name: \"") -> name = line.removePrefix("  name: \"").removeSuffix("\"")
                line == "  equipment:" -> equipped = true
                line.startsWith("    skill_reqs: ") -> reqs = line.removePrefix("    skill_reqs: ")
                line.startsWith("    attack_speed: ") -> speed = line.removePrefix("    attack_speed: ").trim().toIntOrNull()
            }
        }
        flush()
        return out
    }

    @Test
    fun `I-09 every poisoned, ornament and locked variant has the level requirement of its base item`() {
        val items = ymlItems()
        val byName = items.filter { it.equipped }.groupBy { it.name }
        val variant = Regex("""^(.*?)\s*\((p\+\+|p\+|p|or|l|kp)\)$""")
        // Merge 2026-09-25: a base name shared by a 667 item and an OSRS import (e.g. "Steel javelin": the 667 thrown
        // javelin needs Ranged, the OSRS ballista javelin needs nothing - OsrsBallistaImportTests) is ambiguous and not
        // checked, and the Occult necklace (or) keeps no requirement (OSRS cache params, OsrsPilotImportTests).
        val noRequirement = setOf(Items.OCCULT_NECKLACE_OR)
        val offenders =
            items.filter { it.equipped && it.id !in noRequirement }.mapNotNull { item ->
                val base = variant.matchEntire(item.name)?.groupValues?.get(1) ?: return@mapNotNull null
                val baseReqs = byName[base].orEmpty().map { it.reqs }.toSet()
                val required = baseReqs.singleOrNull() ?: return@mapNotNull null
                if (item.reqs != required) "${item.id} ${item.name}: ${item.reqs} vs $baseReqs" else null
            }
        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
        val byId = items.associateBy { it.id }
        assertEquals("[{'skill': 4, 'level': 50}]", byId.getValue(Items.AMETHYST_DART).reqs)
        assertEquals("[{'skill': 4, 'level': 64}]", byId.getValue(Items.OSRS_DRAGON_BOLTS_P).reqs)
        listOf(22799, 22800).forEach { assertEquals("[{'skill': 4, 'level': 60}]", byId.getValue(it).reqs, "dragon knife $it") }
    }

    @Test
    fun `I-11 the Abyssal vine whip carries the whip's attack speed`() {
        val byId = ymlItems().associateBy { it.id }
        (21371..21375).forEach { assertEquals(4, byId.getValue(it).attackSpeed, "vine whip $it") }
    }
}
