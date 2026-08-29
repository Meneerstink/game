package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef

/**
 * Supplies the value used to rank item stacks for death-item-risk protection
 * (the [DeathItemRiskCalculator.protectedItemCount] highest-value stacks are
 * kept). Kept as a small, swappable abstraction rather than hard-wiring death
 * logic to one cache/store-value field, so a future Grand Exchange or other
 * pricing system can be substituted in without touching
 * [DeathItemRiskCalculator] or anything that calls it.
 */
fun interface ItemRiskValueProvider {
    fun getValue(itemId: Int): Long
}

/**
 * The default [ItemRiskValueProvider], backed by each item's cache-defined
 * [ItemDef.cost]. This mirrors the same fallback
 * [gg.rsmod.plugins.content.mechanics.shops.ItemCurrency] already uses when
 * no dedicated pricing system is available. It is *not* a Grand Exchange or
 * live market-price implementation - the Grand Exchange is explicitly out of
 * scope for this milestone - just the safest existing value source in the
 * codebase, wrapped behind [ItemRiskValueProvider] so it can be replaced
 * later without changing any death-resolution code.
 */
class ItemDefCostValueProvider(private val definitions: DefinitionSet) : ItemRiskValueProvider {
    override fun getValue(itemId: Int): Long = definitions.get(ItemDef::class.java, itemId).cost.toLong()
}
