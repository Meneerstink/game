package gg.rsmod.plugins.content.inter.attack

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setVarp

/**
 * @author Tom <rspsmods@gmail.com>
 */
object AttackTab {
    const val ATTACK_STYLE_VARP = 43
    const val DISABLE_AUTO_RETALIATE_VARP = 172

    private const val SPECIAL_ATTACK_ENERGY_VARP = 300
    const val SPECIAL_ATTACK_VARP = 301

    fun setEnergy(
        p: Player,
        amount: Int,
    ) {
        check(amount in 0..100)
        p.setVarp(SPECIAL_ATTACK_ENERGY_VARP, amount * 10)
    }

    fun getEnergy(p: Player): Int = p.getVarp(SPECIAL_ATTACK_ENERGY_VARP) / 10

    /**
     * A special attack's cost in the varp's own tenths. N462 ToA Liquid adrenaline (OSRS Wiki Liquid adrenaline?oldid=15355208:
     * "special attack costs are halved", 25% -> 12.5%, 75% -> 37.5%): half while [gg.rsmod.plugins.content.activity.toa.ToaConsumables.ADRENALINE_TIMER] runs.
     */
    fun specialCostTenths(p: Player, percent: Int): Int =
        if (p.timers.has(gg.rsmod.plugins.content.activity.toa.ToaConsumables.ADRENALINE_TIMER)) percent * 5 else percent * 10

    fun canAffordSpecial(p: Player, percent: Int): Boolean = p.getVarp(SPECIAL_ATTACK_ENERGY_VARP) >= specialCostTenths(p, percent)

    fun spendSpecial(p: Player, percent: Int) {
        p.setVarp(SPECIAL_ATTACK_ENERGY_VARP, (p.getVarp(SPECIAL_ATTACK_ENERGY_VARP) - specialCostTenths(p, percent)).coerceAtLeast(0))
    }

    fun refundSpecial(p: Player, percent: Int) {
        p.setVarp(SPECIAL_ATTACK_ENERGY_VARP, minOf(1000, p.getVarp(SPECIAL_ATTACK_ENERGY_VARP) + specialCostTenths(p, percent)))
    }

    fun disableSpecial(p: Player) {
        p.setVarp(SPECIAL_ATTACK_VARP, 0)
    }

    fun isSpecialEnabled(p: Player): Boolean = p.getVarp(SPECIAL_ATTACK_VARP) == 1
}
