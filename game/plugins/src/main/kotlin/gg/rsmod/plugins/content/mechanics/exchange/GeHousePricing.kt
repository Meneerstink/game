package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.shop.PurchasePolicy
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.shops.ItemCurrency

/**
 * What the house deals at for one item: it sells to a buyer at [ask] and buys from a seller at [bid] (0: it does not
 * buy).
 */
data class GeHouseQuote(
    val ask: Int,
    val bid: Int,
)

/** What NPC coin shops do with an item: the cheapest any shop sells it for, the most any shop pays for it. */
data class GeShopBounds(
    val lowestSell: Int?,
    val highestBuy: Int?,
) {
    companion object {
        val NONE = GeShopBounds(null, null)
    }
}

/**
 * The house's prices (owner 2026-09-20: every exchangeable item can be bought from and sold to the house).
 *
 * Audit E-01: the house used to deal at the *guide* price, which players could steer by trading with themselves. It now
 * deals at the fixed OSRS seed ([OsrsGuidePrices.seed]): it sells at the seed and buys at the seed. The seller pays the
 * OSRS convenience fee ([GeTax]) on a house sale exactly as on any other sale ([GrandExchangeBook.match]), so a round
 * trip through the house never gains anything (ask >= seed >= bid, and the fee comes off the bid). The audit snapshot
 * had no [GeTax] and took 2 % off the bid as a stand-in for the fee; merged with the local [GeTax] that would charge
 * the fee twice, so the bid carries no spread of its own (merge 2026-09-25).
 *
 * Audit E-03: the seed is an OSRS price while shops and alchemy use the 667 cache value, so the house price is also held
 * to these rules (runtime clamps; the cache values themselves cannot be checked):
 *  - **ask >= ceil(0.6 * cost)** (high alchemy): buying from the house and high-alching never pays;
 *  - **ask >= the highest price any NPC coin shop pays for the item**: buying from the house and selling to a shop
 *    never pays (a general store pays 0.4 * cost, already below the alchemy floor);
 *  - **bid <= the lowest price any NPC coin shop sells the item for**: buying from a shop and selling to the house never
 *    pays;
 *  - **bid <= ask**.
 * The shop bounds come from the live shop registry when it can be read ([shopBounds]); without it only the alchemy
 * floor applies.
 */
object GeHousePricing {
    fun quote(
        seed: Int,
        cost: Int,
        shops: GeShopBounds = GeShopBounds.NONE,
    ): GeHouseQuote {
        val base = seed.toLong().coerceAtLeast(1L)
        val ask =
            maxOf(base, ItemCurrency.highAlchValue(cost), (shops.highestBuy ?: 0).toLong())
                .coerceIn(1L, Int.MAX_VALUE.toLong())
        var bid = base
        shops.lowestSell?.let { bid = minOf(bid, it.toLong()) }
        return GeHouseQuote(ask.toInt(), bid.coerceIn(0L, ask).toInt())
    }

    /** [quote] for [def], with the shop bounds of [world] when available. */
    fun quote(
        def: ItemDef,
        world: World?,
    ): GeHouseQuote = quote(OsrsGuidePrices.seed(def), def.cost, world?.let { shopBounds(it, def.id) } ?: GeShopBounds.NONE)

    @Volatile
    private var indexedShops = -1

    @Volatile
    private var index: Map<Int, GeShopBounds> = emptyMap()

    /** The shop bounds of unnoted [itemId]; rebuilt whenever the number of registered shops changed. */
    fun shopBounds(
        world: World,
        itemId: Int,
    ): GeShopBounds {
        val shops = allShops(world)
        if (shops.size != indexedShops) {
            index = index(world, shops)
            indexedShops = shops.size
        }
        return index[itemId] ?: GeShopBounds.NONE
    }

    /** Lowest sell / highest buy price per unnoted item over every coin shop's regular stock. */
    fun index(
        world: World,
        shops: Collection<Shop>,
    ): Map<Int, GeShopBounds> {
        val sells = HashMap<Int, Int>()
        val buys = HashMap<Int, Int>()
        for (shop in shops) {
            val currency = shop.currency as? ItemCurrency ?: continue
            if (currency.currencyItem != Items.COINS_995) continue
            for (shopItem in shop.items) {
                if (shopItem == null || shopItem.temporary) continue
                val def = world.definitions.getNullable(ItemDef::class.java, shopItem.item) ?: continue
                val unnoted = if (def.noted) def.noteLinkId else def.id
                val sells1 = currency.sellPriceOf(world, shopItem)
                if (sells1 > 0) sells.merge(unnoted, sells1) { a, b -> minOf(a, b) }
                if (shop.purchasePolicy != PurchasePolicy.BUY_NONE) {
                    val pays = currency.buyPriceOf(world, shopItem, unnoted, 0)
                    if (pays > 0) buys.merge(unnoted, pays) { a, b -> maxOf(a, b) }
                }
            }
        }
        return (sells.keys + buys.keys).associateWith { GeShopBounds(sells[it], buys[it]) }
    }

    /**
     * Every registered shop. The registry (`PluginRepository.shops`) is `internal` to the game module and has no public
     * listing, so it is read reflectively; any failure means "no shop bounds known" and only the alchemy floor applies.
     */
    @Suppress("UNCHECKED_CAST")
    fun allShops(world: World): Collection<Shop> =
        try {
            val repository = world.plugins
            val field = repository.javaClass.getDeclaredField("shops")
            field.isAccessible = true
            (field.get(repository) as? Map<String, Shop>)?.values?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
}
