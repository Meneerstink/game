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
    fun `OSRS convenience fee - 2 percent rounded down, 5M cap per item, sub-50 free, exempt list`() {
        assertEquals(0, GeTax.perItem(49))
        assertEquals(1, GeTax.perItem(50))
        assertEquals(1, GeTax.perItem(99))
        assertEquals(2_000, GeTax.perItem(100_000))
        assertEquals(5_000_000, GeTax.perItem(250_000_000))
        assertEquals(5_000_000, GeTax.perItem(Int.MAX_VALUE))
        listOf("Lobster", "Hammer", "Energy potion(4)", "Energy potion(1)", "Watering can", "Watering can(8)", "Ring of dueling(8)",
            "Games necklace(8)", "Varrock teleport", "Mind rune", "Old school bond").forEach { assertEquals(true, GeTax.exempt(it), it) }
        listOf("Ring of dueling(7)", "Games necklace(1)", "Abyssal whip", "Chocolate cake", "Raw lobster", "Super energy(4)", null)
            .forEach { assertEquals(false, GeTax.exempt(it), "$it") }
    }

    @Test
    fun `seller pays the fee on player trades and on house sales`() {
        val tax: (Int, Int) -> Int = { _, price -> GeTax.perItem(price) }
        val buy = GrandExchangeOffer(id = 1, username = "buyer", type = OfferType.BUY, itemId = 4151, pricePerItem = 1_000, totalQuantity = 3)
        val book = mutableListOf(buy)
        val sell = GrandExchangeOffer(id = 2, username = "seller", type = OfferType.SELL, itemId = 4151, pricePerItem = 900, totalQuantity = 3)
        book.add(sell)
        GrandExchangeBook.match(book, sell, taxPerItem = tax)
        assertEquals(2_940L, sell.collectableCoins, "sold at the resting buy price 1,000, minus 20 each")
        assertEquals(60L, sell.taxPaid)
        assertEquals(3_000L, sell.coinsTraded)
        assertEquals(0L, buy.collectableCoins, "buyers pay no fee")
    }

    @Test
    fun `house buys a low ask at its bid like a resting OSRS buy offer`() {
        // Audit E-01: the house deals from a quote (ask / bid), no longer from a single guide price.
        val house = GeHouseQuote(ask = 1_000, bid = 1_000)
        val sell = GrandExchangeOffer(id = 1, username = "seller", type = OfferType.SELL, itemId = 4151, pricePerItem = 1, totalQuantity = 2)
        val fills = GrandExchangeBook.match(mutableListOf(sell), sell, taxPerItem = { _, p -> GeTax.perItem(p) }, houseQuote = { house })
        assertEquals(1_000, fills.single().unitPrice)
        assertEquals(OfferStatus.COMPLETED, sell.status)
        assertEquals(1_960L, sell.collectableCoins)
        val high = GrandExchangeOffer(id = 2, username = "seller", type = OfferType.SELL, itemId = 4151, pricePerItem = 1_001, totalQuantity = 1)
        assertEquals(0, GrandExchangeBook.match(mutableListOf(high), high, houseQuote = { house }).size, "an ask above the bid rests")
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
