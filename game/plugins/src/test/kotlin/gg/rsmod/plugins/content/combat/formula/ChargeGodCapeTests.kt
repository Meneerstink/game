package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N69 (owner 10-16: "magecape 2 cannot cast charge", wearing the Imbued Saradomin cape 22389): casting Charge needs no cape
 * (OSRS Wiki Charge; Darkan Magic.java:237-248; Void Charge.kt); the matching god cape - plain, imbued, imbued max, locked -
 * only decides whether the charged god spell hits up to 30 (OSRS Wiki DPS calculator isChargeSpellApplicable).
 */
class ChargeGodCapeTests {
    @Test
    fun `Charge casts without a god cape`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/magic/standard/standard_utility_spells.plugin.kts").readText()
        val charge = source.substringAfter("on_magic_spell_button(\"Charge\")").substringBefore("on_timer(")
        assertFalse("god cape to cast" in charge, "no cape requirement on the cast")
        assertFalse("EquipmentType.CAPE" in charge, "the cast does not look at the cape")
    }

    @Test
    fun `every god cape variant powers its own god spell, never another`() {
        val sara = listOf(Items.SARADOMIN_CAPE, Items.IMBUED_SARADOMIN_CAPE, Items.IMBUED_SARADOMIN_CAPE_L, Items.IMBUED_SARADOMIN_MAX_CAPE, Items.IMBUED_SARADOMIN_MAX_CAPE_L)
        val guthix = listOf(Items.GUTHIX_CAPE, Items.IMBUED_GUTHIX_CAPE, Items.IMBUED_GUTHIX_CAPE_L, Items.IMBUED_GUTHIX_MAX_CAPE, Items.IMBUED_GUTHIX_MAX_CAPE_L)
        val zammy = listOf(Items.ZAMORAK_CAPE, Items.IMBUED_ZAMORAK_CAPE, Items.IMBUED_ZAMORAK_CAPE_L, Items.IMBUED_ZAMORAK_MAX_CAPE, Items.IMBUED_ZAMORAK_MAX_CAPE_L)
        val bySpell = mapOf(CombatSpell.SARADOMIN_STRIKE to sara, CombatSpell.CLAWS_OF_GUTHIX to guthix, CombatSpell.FLAMES_OF_ZAMORAK to zammy)
        assertTrue(MagicCombatFormula.chargeApplies(CombatSpell.SARADOMIN_STRIKE, 22389), "the owner's Imbued Saradomin cape")
        for ((spell, capes) in bySpell) {
            capes.forEach { assertTrue(MagicCombatFormula.chargeApplies(spell, it), "$spell with cape $it") }
            (bySpell - spell).values.flatten().forEach { assertFalse(MagicCombatFormula.chargeApplies(spell, it), "$spell with another god's cape $it") }
            assertFalse(MagicCombatFormula.chargeApplies(spell, null), "$spell without a cape")
            assertFalse(MagicCombatFormula.chargeApplies(spell, Items.IMBUED_SARADOMIN_CAPE_BROKEN), "$spell with a broken cape")
        }
        assertFalse(MagicCombatFormula.chargeApplies(CombatSpell.FIRE_SURGE, Items.IMBUED_ZAMORAK_CAPE), "not a god spell")
    }
}
