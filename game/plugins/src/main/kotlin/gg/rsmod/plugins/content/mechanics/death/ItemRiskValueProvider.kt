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

/**
 * The production [ItemRiskValueProvider] (owner 2026-09-18, "death system exactly RuneScape"):
 * items kept on death and the risked-value skull tier rank by the Grand Exchange guide price -
 * this server's own exchange ([gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeService],
 * seeded with the OSRS guide prices, [gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices])
 * - exactly the value the Price Checker and loot-key "worth" already show, so the three surfaces
 * can never disagree. A noted item is valued as its unnoted item; an item OSRS never had keeps
 * its cache value (OsrsGuidePrices.seed's SOURCE_GAP fallback).
 */
class GuidePriceValueProvider(private val world: gg.rsmod.game.model.World) : ItemRiskValueProvider {
    private val definitions get() = world.definitions
    private val exchange by lazy { world.getService(gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeService::class.java) }

    override fun getValue(itemId: Int): Long {
        val unnoted = gg.rsmod.game.model.item.Item(itemId, 1).toUnnoted(definitions).id
        val def = definitions.getNullable(ItemDef::class.java, unnoted) ?: return 0L
        val seed = gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices.seed(def)
        return (exchange?.guidePrice(unnoted, seed) ?: seed).toLong()
    }
}
