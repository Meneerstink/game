package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.combat.XpMode
import gg.rsmod.plugins.api.WeaponType
import gg.rsmod.plugins.api.ext.CombatStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/** N631: OSRS Wiki Weapons/Categories "Partisan" (Keris partisan) - Stab / Lunge / Pound (crush) / Block, not the dagger's Slash. */
class PartisanStyleTests {
    private fun style(s: CombatStyle) = AttackStyleData.getAttackStyleType(WeaponType.PARTISAN, s)!!

    @Test
    fun `partisan styles are the OSRS stab, lunge, pound and block`() {
        assertEquals(listOf(WeaponStyle.ACCURATE, StyleType.STAB, XpMode.ATTACK_XP), style(CombatStyle.FIRST).let { listOf(it.weaponStyle, it.styleType, it.xpMode) })
        assertEquals(listOf(WeaponStyle.AGGRESSIVE, StyleType.STAB, XpMode.STRENGTH_XP), style(CombatStyle.SECOND).let { listOf(it.weaponStyle, it.styleType, it.xpMode) })
        assertEquals(listOf(WeaponStyle.AGGRESSIVE, StyleType.CRUSH, XpMode.STRENGTH_XP), style(CombatStyle.THIRD).let { listOf(it.weaponStyle, it.styleType, it.xpMode) })
        assertEquals(listOf(WeaponStyle.DEFENSIVE, StyleType.STAB, XpMode.DEFENCE_XP), style(CombatStyle.FOURTH).let { listOf(it.weaponStyle, it.styleType, it.xpMode) })
    }

    @Test
    fun `partisan category matches the clientscript case written by the cache tool`() {
        assertEquals(28, WeaponType.PARTISAN.id)
    }
}
