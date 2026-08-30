package gg.rsmod.plugins.content.mechanics.exchange

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Smallest-possible correctness net for [GrandExchangeBook.match]: full
 * player-vs-player fills, partial fills, price-improvement refunds, and the
 * system-liquidity backstop. Pure data in/out - no Player/World mocking
 * needed since the engine never touches inventories directly.
 */
class GrandExchangeBookTests {
    @Test
    fun `full match at resting sell price refunds buyer's price improvement`() {
        val sell = GrandExchangeOffer(id = 1, username = "seller", type = OfferType.SELL, itemId = 4151, pricePerItem = 10, totalQuantity = 5)
        val book = mutableListOf(sell)
        val buy = GrandExchangeOffer(id = 2, username = "buyer", type = OfferType.BUY, itemId = 4151, pricePerItem = 15, totalQuantity = 5)
        book.add(buy)

        val fills = GrandExchangeBook.match(book, buy)

        assertEquals(1, fills.size)
        assertEquals(5, buy.collectableItems)
        assertEquals(OfferStatus.COMPLETED, buy.status)
        assertEquals(25L, buy.collectableCoins) // (15-10) * 5 refunded
        assertEquals(50L, sell.collectableCoins) // 10 * 5
        assertEquals(OfferStatus.COMPLETED, sell.status)
    }

    @Test
    fun `partial fill leaves both offers active with correct remainders`() {
        val sell = GrandExchangeOffer(id = 1, username = "seller", type = OfferType.SELL, itemId = 4151, pricePerItem = 10, totalQuantity = 3)
        val book = mutableListOf(sell)
        val buy = GrandExchangeOffer(id = 2, username = "buyer", type = OfferType.BUY, itemId = 4151, pricePerItem = 10, totalQuantity = 10)
        book.add(buy)

        GrandExchangeBook.match(book, buy)

        assertEquals(3, buy.quantityFilled)
        assertEquals(7, buy.remaining)
        assertEquals(gg.rsmod.plugins.content.mechanics.exchange.OfferStatus.ACTIVE, buy.status)
        assertEquals(gg.rsmod.plugins.content.mechanics.exchange.OfferStatus.COMPLETED, sell.status)
    }

    @Test
    fun `unmatched buy falls back to system liquidity when price covers it`() {
        val buy =
            GrandExchangeOffer(
                id = 1,
                username = "buyer",
                type = OfferType.BUY,
                itemId = gg.rsmod.plugins.api.cfg.Items.FEATHER,
                pricePerItem = 5,
                totalQuantity = 100,
            )
        val fills = GrandExchangeBook.match(mutableListOf(buy), buy)

        assertEquals(1, fills.size)
        assertEquals(true, fills[0].fromSystem)
        assertEquals(100, buy.collectableItems)
        assertEquals(OfferStatus.COMPLETED, buy.status)
        assertEquals(300L, buy.collectableCoins) // (5-2) * 100 refunded
    }

    @Test
    fun `unmatched buy for a non-whitelisted item stays active and uncollectable`() {
        val buy = GrandExchangeOffer(id = 1, username = "buyer", type = OfferType.BUY, itemId = 4151, pricePerItem = 1000, totalQuantity = 1)
        val fills = GrandExchangeBook.match(mutableListOf(buy), buy)

        assertEquals(0, fills.size)
        assertEquals(0, buy.collectableItems)
        assertEquals(OfferStatus.ACTIVE, buy.status)
    }
}
