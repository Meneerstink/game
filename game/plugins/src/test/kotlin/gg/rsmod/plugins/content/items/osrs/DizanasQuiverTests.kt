package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "quiver" (tx-20260913-232047): Dizana's quiver stats and Dizana's Sunfire against the OSRS Wiki. */
class DizanasQuiverTests {
    @Test
    fun `all six quivers have +18 ranged attack, +3 ranged strength and 75 Ranged`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val yml = root.filter { it.path("id").asInt() in Items.DIZANAS_QUIVER_UNCHARGED..Items.BLESSED_DIZANAS_QUIVER_L }.associateBy { it.path("id").asInt() }
        assertEquals(6, yml.size)
        yml.values.forEach {
            val eq = it.path("equipment")
            assertEquals(1, eq.path("equip_slot").asInt())
            assertEquals(18, eq.path("attack_ranged").asInt())
            assertEquals(3, eq.path("ranged_strength").asInt())
            assertEquals(mapOf(4 to 75), eq.path("skill_reqs").associate { r -> r.path("skill").asInt() to r.path("level").asInt() })
        }
    }

    @Test
    fun `splinters charge one for one up to 20000 and turn the uncharged quiver into the charged one`() {
        val first = DizanasQuiver.charge(Item(Items.DIZANAS_QUIVER_UNCHARGED), 150)
        assertEquals(150, first.added)
        assertEquals(Items.DIZANAS_QUIVER, first.result.id)
        assertEquals(150, DizanasQuiver.charges(first.result))
        val full = DizanasQuiver.charge(first.result, 50_000)
        assertEquals(20_000 - 150, full.added)
        assertEquals(20_000, DizanasQuiver.charges(full.result))
        assertEquals(0, DizanasQuiver.charge(full.result, 1).added)
        assertEquals(Items.DIZANAS_QUIVER_L, DizanasQuiver.charge(Item(Items.DIZANAS_QUIVER_L_UNCHARGED), 1).result.id)
        assertEquals(0, DizanasQuiver.charge(Item(Items.BLESSED_DIZANAS_QUIVER), 5).added, "blessed quivers take no charges")
    }

    @Test
    fun `Sunfire is active while charged or blessed and a shot uses a charge one time in three`() {
        val charged = DizanasQuiver.charge(Item(Items.DIZANAS_QUIVER_UNCHARGED), 2).result
        assertTrue(DizanasQuiver.sunfireActive(charged))
        assertTrue(DizanasQuiver.sunfireActive(Item(Items.BLESSED_DIZANAS_QUIVER_L)))
        assertFalse(DizanasQuiver.sunfireActive(Item(Items.DIZANAS_QUIVER_UNCHARGED)))
        assertFalse(DizanasQuiver.sunfireActive(null))
        assertSame(charged, DizanasQuiver.spendShot(charged, 0.34), "a roll of 1/3 or more keeps the charge")
        val one = DizanasQuiver.spendShot(charged, 0.33)
        assertEquals(1, DizanasQuiver.charges(one))
        assertEquals(Items.DIZANAS_QUIVER_UNCHARGED, DizanasQuiver.spendShot(one, 0.0).id)
        val blessed = Item(Items.BLESSED_DIZANAS_QUIVER)
        assertSame(blessed, DizanasQuiver.spendShot(blessed, 0.0))
        assertEquals(10, DizanasQuiver.ACCURACY_BONUS)
        assertEquals(1, DizanasQuiver.STRENGTH_BONUS)
    }

    @Test
    fun `Sunfire covers arrows and bolts only and is read by the formula, the strength bonus and both shot paths`() {
        assertTrue(DizanasQuiver.isArrowOrBolt(Items.RUNE_ARROW))
        assertTrue(DizanasQuiver.isArrowOrBolt(Items.OSRS_DRAGON_BOLTS))
        assertFalse(DizanasQuiver.isArrowOrBolt(Items.OSRS_DRAGON_JAVELIN))
        assertFalse(DizanasQuiver.isArrowOrBolt(Items.DRAGON_DART))
        assertTrue("DizanasQuiver.accuracyBonus(pawn)" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/RangedCombatFormula.kt").readText())
        assertTrue("DizanasQuiver.strengthBonus(this)" in File("src/main/kotlin/gg/rsmod/plugins/api/ext/PlayerExt.kt").readText())
        assertTrue("DizanasQuiver.afterShot(pawn)" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText())
        assertTrue("DizanasQuiver.afterShot(player)" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/SpecialAttackSupport.kt").readText())
    }

    @Test
    fun `the second ammunition slot holds one type of arrows or bolts and keeps the charges`() {
        val charged = DizanasQuiver.charge(Item(Items.DIZANAS_QUIVER_UNCHARGED), 40).result
        assertEquals(DizanasQuiver.FillResult.NothingWorn, DizanasQuiver.fill(charged, null))
        assertEquals("You have nothing in your worn quiver to fill your Dizana's Quiver with.", DizanasQuiver.NOTHING_TO_FILL_MESSAGE)
        assertEquals(DizanasQuiver.FillResult.NotArrowOrBolt, DizanasQuiver.fill(charged, Item(Items.DRAGON_DART, 10)))
        assertEquals(DizanasQuiver.FillResult.NotArrowOrBolt, DizanasQuiver.fill(charged, Item(Items.OSRS_DRAGON_JAVELIN, 10)))
        val filled = DizanasQuiver.fill(charged, Item(Items.RUNE_ARROW, 500)) as DizanasQuiver.FillResult.Filled
        assertEquals(500, filled.moved)
        assertEquals(Item(Items.RUNE_ARROW, 500).id, DizanasQuiver.storedAmmo(filled.quiver)!!.id)
        assertEquals(500, DizanasQuiver.storedAmmo(filled.quiver)!!.amount)
        assertEquals(40, DizanasQuiver.charges(filled.quiver), "charges survive filling")
        val more = DizanasQuiver.fill(filled.quiver, Item(Items.RUNE_ARROW, 25)) as DizanasQuiver.FillResult.Filled
        assertEquals(525, DizanasQuiver.storedAmmo(more.quiver)!!.amount)
        assertEquals(DizanasQuiver.FillResult.DifferentAmmo, DizanasQuiver.fill(more.quiver, Item(Items.OSRS_DRAGON_BOLTS, 5)))
        assertEquals(null, DizanasQuiver.storedAmmo(DizanasQuiver.withStored(more.quiver, Items.RUNE_ARROW, 0)), "emptied")
        assertEquals(null, DizanasQuiver.storedAmmo(Item(Items.RUNE_ARROW, 5)), "only quivers store ammo")
        assertEquals(525, DizanasQuiver.storedAmmo(DizanasQuiver.spendShot(more.quiver, 0.0))!!.amount, "a charge roll keeps the stored ammo")
        assertEquals(6, DizanasQuiver.QUIVERS.size)
    }

    @Test
    fun `the real ammo slot fires first and every ammo path reads the resolved ammunition`() {
        val ranged = "src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/ranged/RangedAmmo.kt"
        val source = File(ranged).readText()
        assertTrue("if (slot != null && slot.id in valid) return Fired(slot, false)" in source, "ammo slot prioritised")
        assertTrue("DizanasQuiver.storedAmmo(player.getEquipment(EquipmentType.CAPE))" in source, "only a worn quiver")
        assertTrue(
            gg.rsmod.plugins.content.combat.strategy.ranged.RangedAmmo.validAmmo(Items.ZARYTE_CROSSBOW)!!.contains(Items.OSRS_DRAGON_BOLTS),
        )
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText()
        assertTrue("RangedAmmo.fired(pawn)" in strategy && "RangedAmmo.consume(pawn, fired, amount)" in strategy)
        val support = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/SpecialAttackSupport.kt").readText()
        assertTrue("RangedAmmo.fired(player)" in support && "RangedAmmo.consume(player, fired!!, 1)" in support)
        assertTrue("RangedAmmo.quiverBonusCorrection(this, BonusSlot.RANGED_STRENGTH_BONUS)" in File("src/main/kotlin/gg/rsmod/plugins/api/ext/PlayerExt.kt").readText())
        assertTrue("RangedAmmo.quiverBonusCorrection(pawn, BonusSlot.ATTACK_RANGED)" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/RangedCombatFormula.kt").readText())
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/dizanas_quiver.plugin.kts").readText()
        assertTrue("on_equipment_option(item = quiverId, option = \"Fill\")" in plugin)
        assertTrue("DizanasQuiver.NOTHING_TO_FILL_MESSAGE" in plugin)
    }

    @Test
    fun `every quiver has the worn Fill option and the charged ones keep the cache's worn Check`() {
        val store = com.displee.cache.CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val definitions = gg.rsmod.game.fs.DefinitionSet()
            definitions.load(store, gg.rsmod.game.fs.def.ItemDef::class.java)
            DizanasQuiver.QUIVERS.forEach { id ->
                val menu = definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id).equipmentMenu.filterNotNull()
                val expected = if (id == Items.DIZANAS_QUIVER || id == Items.DIZANAS_QUIVER_L) listOf("Check", "Fill") else listOf("Fill")
                assertEquals(expected, menu, "worn menu of $id")
            }
        } finally {
            store.close()
        }
    }
}
