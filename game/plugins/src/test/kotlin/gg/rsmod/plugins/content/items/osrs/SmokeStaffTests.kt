package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.magic.MagicStaves
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OSRS-IMPORT Mystic smoke staff: rune supply and the standard-spellbook bonus against the wiki and the DPS calculator. */
class SmokeStaffTests {
    @Test
    fun `the staff supplies air and fire runes and nothing else`() {
        val supplying = MagicStaves.values().filter { Items.MYSTIC_SMOKE_STAFF in it.staves }.map { it.runeId }.toSet()
        assertEquals(setOf(Items.AIR_RUNE, Items.FIRE_RUNE), supplying)
    }

    @Test
    fun `the 10 percent accuracy and damage bonus is limited to standard spellbook spells`() {
        assertEquals(10, SmokeStaves.ACCURACY_PERCENT)
        assertEquals(0.10, SmokeStaves.DAMAGE_BONUS)
        assertTrue(SmokeStaves.appliesTo(CombatSpell.FIRE_SURGE))
        assertTrue(SmokeStaves.appliesTo(CombatSpell.ENTANGLE), "curse spells included")
        assertFalse(SmokeStaves.appliesTo(CombatSpell.ICE_BARRAGE), "Ancient Magicks excluded")
        assertFalse(SmokeStaves.appliesTo(null), "powered staff built-in spells excluded")
        val formula = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/MagicCombatFormula.kt").readText()
        assertTrue("SmokeStaves.magicDamageBonus(pawn, spell)" in formula, "part of the additive magic damage bonus")
        val smoke = formula.indexOf("SmokeStaves.accuracyMultiplier(player, player.attr[Combat.CASTING_SPELL])")
        assertTrue(smoke in 0 until formula.indexOf("Tomes.accuracyMultiplier(player, target, player.attr[Combat.CASTING_SPELL])"), "before the tome factor")
    }
}
