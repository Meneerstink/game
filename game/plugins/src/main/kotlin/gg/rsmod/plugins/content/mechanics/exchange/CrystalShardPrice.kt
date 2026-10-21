package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.model.shop.ShopItem
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.areas.home.QuartermasterSupplies
import mu.KLogging

/**
 * N35: the coin price of a Crystal shard (22750). A shard has no Grand Exchange price in OSRS (untradeable); the owner's rule
 * (VRAGEN 9 B, 2026-10-16) is the OSRS Wiki value: the Enhanced crystal teleport seed price / 150 (the Amrod seed exchange:
 * 150 shards = one Enhanced crystal teleport seed).
 *
 * The seed is not in our 667 cache, so the price is read from the price book by its OSRS id: a valid positive hourly price
 * first, else a valid positive Jagex guide price, else no price at all ([of] = null) - never a default, never 1 coin and
 * never the cache value 10,000 (the OSRS item value, not the owner's rule). "Valid" is the [OsrsPriceBook.price] rule
 * (`> 0`) plus a seed price that still leaves at least 1 coin per shard after the integer division.
 */
object CrystalShardPrice : KLogging() {
    /** OSRS id of the Enhanced crystal teleport seed (`osrs-item-mapping.json`). */
    const val SEED_OSRS_ID = 23959

    /** The OSRS name of the seed, the guide-price key when the name map lacks the id. */
    const val SEED_NAME = "Enhanced crystal teleport seed"

    /** Shards that one Enhanced crystal teleport seed costs at the Amrod exchange (OSRS Wiki "Crystal shard"). */
    const val SHARDS_PER_SEED = 150

    /** Shards in stock; 10,000 = 50 full recharges (200 shards each) per restock. Team number, reversible. */
    const val STOCK = 10_000

    /** Coins per shard (Kotlin Int floor of seed / 150) or null when the book holds no valid seed price. */
    fun of(book: OsrsPriceBook = OsrsGuidePrices.book): Int? {
        fun perShard(seed: Int?): Int? = seed?.takeIf { it > 0 }?.div(SHARDS_PER_SEED)?.takeIf { it > 0 }
        return perShard(book.hourly[SEED_OSRS_ID])
            ?: perShard(book.guide.price(book.osrsNames[SEED_OSRS_ID] ?: SEED_NAME))
    }

    /**
     * The row Ava's Odds and Ends sells. Without a valid price the row stays but is not buyable: `sellPrice = 0` makes
     * `ItemCurrency.sellToPlayer` refuse ("This item is not currently available for purchase.") and log a trace PROBLEM, so
     * a broken price file is visible instead of the source silently vanishing. `sellPrice` is always explicit: null would
     * fall back to the cache value 10,000. `buyPrice = 0`: the shop never buys a shard back (untradeable, not a coin route).
     */
    fun shopItem(book: OsrsPriceBook = OsrsGuidePrices.book): ShopItem {
        val price = of(book)
        if (price == null) logger.warn { "PROBLEM Crystal shard has no valid Enhanced crystal teleport seed price (${book.label}); not buyable" }
        return ShopItem(
            Items.CRYSTAL_SHARD,
            STOCK,
            sellPrice = price ?: 0,
            buyPrice = 0,
            resupplyAmount = QuartermasterSupplies.resupplyAmount(STOCK),
        )
    }
}
