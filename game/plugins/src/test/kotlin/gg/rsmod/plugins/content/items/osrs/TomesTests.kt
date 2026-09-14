package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OSRS-IMPORT Tome of Fire / Tome of Water against the OSRS Wiki item pages. */
class TomesTests {
    @Test
    fun `pages add 20 charges each up to 1000 pages and the tome empties at 0`() {
        assertEquals(20_000, Tomes.MAX_CHARGES)
        val empty = Item(Items.TOME_OF_FIRE_EMPTY)
        assertEquals(0, Tomes.charges(empty))
        assertEquals(1_000, Tomes.pagesToAdd(empty, 5_000))
        val charged = Tomes.withCharges(empty, 3 * Tomes.CHARGES_PER_PAGE)
        assertEquals(Items.TOME_OF_FIRE, charged.id)
        assertEquals(60, Tomes.charges(charged))
        assertEquals(997, Tomes.pagesToAdd(charged, 5_000))
        assertEquals(Items.TOME_OF_WATER_EMPTY, Tomes.withCharges(Item(Items.TOME_OF_WATER), 0).id)
    }

    @Test
    fun `qualifying spells, charge use and the curse sets follow the item pages`() {
        assertEquals(setOf(CombatSpell.FIRE_STRIKE, CombatSpell.FIRE_BOLT, CombatSpell.FIRE_BLAST, CombatSpell.FIRE_WAVE, CombatSpell.FIRE_SURGE), Tomes.FIRE_SPELLS)
        assertEquals(5, Tomes.WATER_SPELLS.size)
        assertEquals(setOf(CombatSpell.CONFUSE, CombatSpell.WEAKEN, CombatSpell.CURSE, CombatSpell.VULNERABILITY, CombatSpell.ENFEEBLE, CombatSpell.STUN), Tomes.STAT_DRAIN_CURSES)
        assertTrue(CombatSpell.ENTANGLE in Tomes.CURSE_SPELLS && CombatSpell.BIND in Tomes.CURSE_SPELLS)
        assertTrue(Tomes.usesCharge(Tomes.Tome.FIRE, CombatSpell.FIRE_SURGE))
        assertFalse(Tomes.usesCharge(Tomes.Tome.FIRE, CombatSpell.WATER_BLAST))
        assertTrue(Tomes.usesCharge(Tomes.Tome.WATER, CombatSpell.ENTANGLE), "curse spells use a charge")
        assertFalse(Tomes.usesCharge(Tomes.Tome.WATER, CombatSpell.ICE_BARRAGE), "ice spells never use charges")
        assertEquals(Items.FIRE_RUNE, Tomes.Tome.FIRE.rune)
        assertEquals(Items.WATER_RUNE, Tomes.Tome.WATER.rune)
    }

    @Test
    fun `the magic formula, rune supply, charge use and drain boost are wired in`() {
        val formula = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/MagicCombatFormula.kt").readText()
        val additive = formula.indexOf("hit = Math.floor(Math.floor(hit) * (1.0 + additive))")
        val tome = formula.indexOf("Tomes.damageMultiplier(pawn, target, spell)")
        assertTrue(additive in 0 until tome, "tome after the additive magic damage (multiplicative)")
        assertTrue("Tomes.accuracyMultiplier(player, target, player.attr[Combat.CASTING_SPELL])" in formula)
        assertTrue("Tomes.suppliesRune(p, rune)" in File("src/main/kotlin/gg/rsmod/plugins/content/magic/MagicSpells.kt").readText())
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText()
        assertTrue("Tomes.afterCast(pawn, spell)" in strategy)
        assertTrue("Tomes.drainBoost(pawn, spell)" in strategy)
        assertTrue("val amount = (base * percent * boost / 100.0).toInt()" in strategy)
    }
}
