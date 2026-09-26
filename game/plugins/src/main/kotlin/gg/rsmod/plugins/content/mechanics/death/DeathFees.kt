package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute

/**
 * The OSRS reclamation fees, exactly as the OSRS client computes what it shows:
 *
 *  - **Gravestone** (clientscript `gravestone_generic_window_set`, OSRS Wiki "Death/Item Recovery Fees"): every reclaimed
 *    item is charged by its own value - no fee under 100,000, 1,000 coins from 100,000, 10,000 from 1,000,000 and 100,000
 *    from 10,000,000 - multiplied by the stack size, and the whole gravestone never costs more than 500,000.
 *  - **Death's Office** ("Death's Office", "Death Changes" news post): 5% of the Grand Exchange value for every item worth
 *    100,000 or more, free below that ("Fee: X coins each" in `death_office_redraw`).
 *
 * An item marked [ItemAttribute.DEATH_FEE_FREE] costs nothing: familiar cargo Death holds for free (owner override) and
 * gravestone items whose fee has already been paid ("Unlock").
 */
object DeathFees {
    fun isFeeFree(item: Item): Boolean = item.getAttr(ItemAttribute.DEATH_FEE_FREE) == 1

    /** The gravestone fee for one unit worth [unitValue]. */
    fun graveUnitFee(
        unitValue: Long,
        config: DeathsDomainConfig = DeathsDomainConfig.current,
    ): Int = config.graveFeeTiers.lastOrNull { unitValue >= it.first }?.second ?: 0

    /** The gravestone fee of one stack: unit fee x amount, capped like the client caps each stack's label. */
    fun graveStackFee(
        item: Item,
        value: ItemRiskValueProvider,
        config: DeathsDomainConfig = DeathsDomainConfig.current,
    ): Long {
        if (isFeeFree(item)) return 0L
        val unit = graveUnitFee(value.getValue(item.id), config).toLong()
        return minOf(unit * item.amount, config.graveFeeCap.toLong())
    }

    /** The fee to unlock every chargeable item of a gravestone: the sum of the stack fees, capped at 500,000. */
    fun graveFee(
        items: Iterable<Item>,
        value: ItemRiskValueProvider,
        config: DeathsDomainConfig = DeathsDomainConfig.current,
    ): Int {
        var total = 0L
        for (item in items) {
            total += graveStackFee(item, value, config)
            if (total >= config.graveFeeCap) return config.graveFeeCap
        }
        return total.toInt()
    }

    /** Death's Office fee per unit of [item] (0 when the item is free or worth less than 100,000). */
    fun officeUnitFee(
        item: Item,
        value: ItemRiskValueProvider,
        config: DeathsDomainConfig = DeathsDomainConfig.current,
    ): Int {
        if (isFeeFree(item)) return 0
        val unit = value.getValue(item.id)
        if (unit < config.officeFreeBelow) return 0
        return (unit * config.officeFeePercent / 100L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Death's Office fee for [amount] units of [item], as a Long so a huge stack never overflows. */
    fun officeFee(
        item: Item,
        amount: Int,
        value: ItemRiskValueProvider,
        config: DeathsDomainConfig = DeathsDomainConfig.current,
    ): Long = officeUnitFee(item, value, config).toLong() * amount
}
