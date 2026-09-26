package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute

/**
 * The Death's Office fee ("Death's Office", "Death Changes" news post): 5% of the Grand Exchange value for every item worth
 * 100,000 or more, free below that ("Fee: X coins each" in `death_office_redraw`). Owner 2026-09-26: the gravestone itself
 * is free, so there is no gravestone fee any more.
 *
 * An item marked [ItemAttribute.DEATH_FEE_FREE] costs nothing: only items saved before 2026-09-26 still carry it (familiar
 * cargo Death held for free, gravestone items whose fee was paid) - they stay free.
 */
object DeathFees {
    fun isFeeFree(item: Item): Boolean = item.getAttr(ItemAttribute.DEATH_FEE_FREE) == 1

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
