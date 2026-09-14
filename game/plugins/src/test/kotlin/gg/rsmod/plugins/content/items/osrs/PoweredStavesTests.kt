package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OSRS-IMPORT powered staves (tridents): charges, caps, max hits, stance and routing against the OSRS Wiki. */
class PoweredStavesTests {
    @Test
    fun `every trident has the wiki cap, charge cost, refund and max hit offset`() {
        val staves = PoweredStaves.Staff.values().associateBy { it }
        assertEquals(2_500, staves.getValue(PoweredStaves.Staff.SEAS).maxCharges)
        assertEquals(2_500, staves.getValue(PoweredStaves.Staff.SWAMP).maxCharges)
        assertEquals(20_000, staves.getValue(PoweredStaves.Staff.SEAS_E).maxCharges)
        assertEquals(20_000, staves.getValue(PoweredStaves.Staff.SWAMP_E).maxCharges)
        PoweredStaves.Staff.values().forEach { staff ->
            val cost = staff.chargeCost.associate { it.id to it.amount }
            assertEquals(1, cost[Items.DEATH_RUNE], "$staff")
            assertEquals(1, cost[Items.CHAOS_RUNE], "$staff")
            assertEquals(5, cost[Items.FIRE_RUNE], "$staff")
            val swamp = staff == PoweredStaves.Staff.SWAMP || staff == PoweredStaves.Staff.SWAMP_E
            assertEquals(if (swamp) 1 else null, cost[Items.ZULRAHS_SCALES], "$staff scales")
            assertEquals(if (swamp) null else 10, cost[Items.COINS_995], "$staff coins")
            assertTrue(Items.COINS_995 !in staff.refunded, "coins are never refunded: $staff")
            assertEquals(if (swamp) 78 else 75, staff.requiredMagic)
            assertEquals(if (swamp) 0.25 else 0.0, staff.venomChance)
        }
        // Wiki: Seas 20 at 75 Magic, Swamp 24 at 78 Magic, never below 1.
        assertEquals(20, PoweredStaves.Staff.SEAS.baseMaxHit(75))
        assertEquals(28, PoweredStaves.Staff.SEAS.baseMaxHit(99))
        assertEquals(24, PoweredStaves.Staff.SWAMP.baseMaxHit(78))
        assertEquals(31, PoweredStaves.Staff.SWAMP_E.baseMaxHit(99))
        assertEquals(1, PoweredStaves.Staff.SEAS.baseMaxHit(3))
    }

    @Test
    fun `charges move the item between uncharged, charged and full and refunds follow the cost`() {
        val uncharged = Item(Items.UNCHARGED_TRIDENT)
        assertEquals(0, PoweredStaves.charges(uncharged))
        val some = PoweredStaves.withCharges(uncharged, 100)
        assertEquals(Items.TRIDENT_OF_THE_SEAS, some.id)
        assertEquals(100, PoweredStaves.charges(some))
        val full = PoweredStaves.withCharges(some, 2_500)
        assertEquals(Items.TRIDENT_OF_THE_SEAS_FULL, full.id)
        assertEquals(2_500, PoweredStaves.charges(full))
        val cast = PoweredStaves.withCharges(full, PoweredStaves.charges(full) - 1)
        assertEquals(Items.TRIDENT_OF_THE_SEAS, cast.id)
        assertEquals(2_499, PoweredStaves.charges(cast))
        assertEquals(Items.UNCHARGED_TRIDENT, PoweredStaves.withCharges(cast, 0).id)
        assertEquals(Items.TRIDENT_OF_THE_SWAMP_E, PoweredStaves.withCharges(Item(Items.UNCHARGED_TOXIC_TRIDENT_E), 20_000).id, "(e) has no full item")

        val inventory = mapOf(Items.DEATH_RUNE to 300, Items.CHAOS_RUNE to 1_000, Items.FIRE_RUNE to 1_000, Items.COINS_995 to 1_000_000)
        assertEquals(200, PoweredStaves.chargesAffordable(PoweredStaves.Staff.SEAS, 0) { inventory[it] ?: 0 }, "fire runes limit to 200")
        assertEquals(50, PoweredStaves.chargesAffordable(PoweredStaves.Staff.SEAS, 2_450) { inventory[it] ?: 0 }, "cap")
        assertEquals(0, PoweredStaves.chargesAffordable(PoweredStaves.Staff.SWAMP, 0) { inventory[it] ?: 0 }, "no scales")
        assertEquals(
            mapOf(Items.DEATH_RUNE to 7, Items.CHAOS_RUNE to 7, Items.FIRE_RUNE to 35, Items.ZULRAHS_SCALES to 7),
            PoweredStaves.refund(PoweredStaves.Staff.SWAMP, 7).associate { it.id to it.amount },
        )
        assertEquals(setOf(Items.DEATH_RUNE, Items.CHAOS_RUNE, Items.FIRE_RUNE), PoweredStaves.refund(PoweredStaves.Staff.SEAS_E, 3).map { it.id }.toSet())
        assertNull(PoweredStaves.staffFor(Items.MAGIC_FANG))
        assertEquals(Items.UNCHARGED_TOXIC_TRIDENT, PoweredStaves.TOXIC_UPGRADE[Items.UNCHARGED_TRIDENT])
    }

    @Test
    fun `the built-in spell is routed through the magic formula, strategy, speed and autocast rules`() {
        val formula = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/MagicCombatFormula.kt").readText()
        assertTrue("PoweredStaves.wielded(p)?.baseMaxHit(p.skills.getCurrentLevel(Skills.MAGIC))" in formula)
        assertTrue("effectiveLevel += 8.0 + gg.rsmod.plugins.content.items.osrs.PoweredStaves.stanceMagicBonus(player)" in formula)
        val configs = File("src/main/kotlin/gg/rsmod/plugins/content/combat/CombatConfigs.kt").readText()
        assertTrue("PoweredStaves.usingBuiltInSpell(pawn)) {\n            gg.rsmod.plugins.content.combat.strategy.PoweredStaffCombatStrategy" in configs)
        assertTrue("PoweredStaves.wielded(pawn) != null -> CombatClass.MAGIC" in configs)
        assertTrue("CombatClass.MAGIC && !gg.rsmod.plugins.content.items.osrs.PoweredStaves.usingBuiltInSpell(pawn)" in configs, "speed 4")
        assertTrue("PoweredStaves.wielded(pawn) == null" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/combat.plugin.kts").readText(), "no autocast")
        assertTrue("PoweredStaves.NO_AUTOCAST_MESSAGE" in File("src/main/kotlin/gg/rsmod/plugins/content/inter/magic/magic_tab.plugin.kts").readText())
        assertTrue("PoweredStaves.rollVenom(pawn, target)" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText(), "manual casts roll Swamp venom")
        assertEquals(7, PoweredStaves.ATTACK_RANGE)
        assertEquals(2.0, PoweredStaves.MAGIC_XP_PER_DAMAGE)
    }
}
