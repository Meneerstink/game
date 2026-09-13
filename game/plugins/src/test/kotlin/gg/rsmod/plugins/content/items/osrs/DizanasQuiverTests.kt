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
}
