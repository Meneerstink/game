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
        val seed = OsrsGuidePrices.seed(def)
        return exchange?.guidePrice(unnoted, seed) ?: seed
    }

    fun unit(
        world: World,
        itemId: Int,
    ): Int = unit(world.definitions, world.getService(GrandExchangeService::class.java), itemId)

    /** What the whole stack [item] is worth, in Long (a big stack times its price does not fit an Int). */
    fun stack(
        world: World,
        item: Item?,
    ): Long = if (item == null) 0L else unit(world, item.id).toLong().coerceAtLeast(0L) * item.amount.toLong().coerceAtLeast(0L)
}
