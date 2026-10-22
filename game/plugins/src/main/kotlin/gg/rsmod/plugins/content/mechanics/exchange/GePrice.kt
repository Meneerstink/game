package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.item.Item

/**
 * A14/A15: THE price function of the server ("what is this item worth"), used by every surface so they can never disagree
 * (ROOT_CAUSES #4; donors: Darkan EconomyPrices.getPrice(id), Novite ExchangePriceLoader.getAveragePrice(id)): the Grand Exchange
 * offer box, the price checker, the trade window (total and "Value"), the items kept on death and the risk skull, loot keys.
 *
 * The price of one item is [GrandExchangeService.guidePrice] of its unnoted form: the OSRS hourly average ([OsrsGuidePrices]) for an
 * item with an OSRS price, otherwise its exact cache value. Player trades never change either. A note is worth its unnoted item.
 */
object GePrice {
    /** The price of one [itemId] (a note is valued as its unnoted item); 0 for an id without a definition. */
    fun unit(
        definitions: DefinitionSet,
        exchange: GrandExchangeService?,
        itemId: Int,
    ): Int {
        val unnoted = Item(itemId, 1).toUnnoted(definitions).id
        val def = definitions.getNullable(ItemDef::class.java, unnoted) ?: return 0
        // N182 (owner 2026-10-22, "Mystic robe set 20 gp"): no GE item set has an OSRS price under its 667 id, so a set fell back to
        // its tiny cache value; a set without an OSRS price is worth the sum of its components (what exchanging it gives you).
        if (OsrsGuidePrices.osrsPrice(def) == null) {
            SET_COMPONENTS[unnoted]?.let { parts -> return parts.sumOf { unit(definitions, exchange, it).toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        }
        val seed = OsrsGuidePrices.seed(def)
        return exchange?.guidePrice(unnoted, seed) ?: seed
    }

    /** Every Grand Exchange item set and its components ([gg.rsmod.plugins.content.inter.ge.GeItemSet]). */
    private val SET_COMPONENTS: Map<Int, IntArray> by lazy {
        gg.rsmod.plugins.content.inter.ge.GeItemSet.values().associate { it.id to it.items }
    }

    fun unit(
        world: World,
        itemId: Int,
    ): Int = unit(world.definitions, exchange(world), itemId)

    /** The world's Grand Exchange; null when it has none (a bare world, or a stand-in service in tests) - the seed price applies. */
    private fun exchange(world: World): GrandExchangeService? = world.getService<gg.rsmod.game.service.Service>(GrandExchangeService::class.java) as? GrandExchangeService

    /** What the whole stack [item] is worth, in Long (a big stack times its price does not fit an Int). */
    fun stack(
        world: World,
        item: Item?,
    ): Long = if (item == null) 0L else unit(world, item.id).toLong().coerceAtLeast(0L) * item.amount.toLong().coerceAtLeast(0L)
}
