package gg.rsmod.plugins.content.mechanics.exchange

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.shops.ItemCurrency
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Audit E-01 (wash trades, house fills, robust guide, house at the seed), E-03 (house price bounds), E-08 (escrow order). */
class GrandExchangeEconomyAuditTests {
    private fun newService(clock: () -> Long = { 0L }): Pair<GrandExchangeService, File> {
        val file = File(Files.createTempDirectory("ge-audit").toFile(), "offers.json")
        return GrandExchangeService(file, clock) to file
    }

    private fun drain(
        service: GrandExchangeService,
        user: String,
        offer: GrandExchangeOffer,
    ) {
        service.cancel(user, offer.id)
        service.takePart(user, offer.id, coins = true)
        service.takePart(user, offer.id, coins = false)
        assertTrue(service.releaseIfDrained(user, offer.id))
    }

    @Test
    fun `an account never fills against its own offer`() {
        val sell = GrandExchangeOffer(1, "alice", OfferType.SELL, Items.ABYSSAL_WHIP, 10, 1)
        val buy = GrandExchangeOffer(2, "alice", OfferType.BUY, Items.ABYSSAL_WHIP, 15, 1)
        val fills = GrandExchangeBook.match(mutableListOf(sell, buy), buy)
        assertTrue(fills.isEmpty())
        assertEquals(0, buy.quantityFilled)
        assertEquals(0, sell.quantityFilled)
    }

    @Test
    fun `100 wash trades of one account do not move the guide price`() {
        val (service, _) = newService()
        val guide = service.guidePrice(Items.ABYSSAL_WHIP, 1000)
        repeat(100) { i ->
            val price = 1050 + i * 50
            val sell = service.submit("alice", OfferType.SELL, Items.ABYSSAL_WHIP, price, 1)!!.first
            val buy = service.submit("alice", OfferType.BUY, Items.ABYSSAL_WHIP, price, 1)!!.first
            assertEquals(0, buy.quantityFilled, "wash trade $i must not fill")
            drain(service, "alice", sell)
            drain(service, "alice", buy)
        }
        assertEquals(guide, service.guidePrice(Items.ABYSSAL_WHIP, 1000))
    }

    @Test
    fun `one pair of accounts is one sample and cannot outvote the market`() {
        val (service, _) = newService()
        repeat(50) {
            val sell = service.submit("alice", OfferType.SELL, Items.ABYSSAL_WHIP, 9_000, 1)!!.first
            val buy = service.submit("bob", OfferType.BUY, Items.ABYSSAL_WHIP, 9_000, 1)!!.first
            assertEquals(1, buy.quantityFilled)
            service.takePart("alice", sell.id, coins = true)
            assertTrue(service.releaseIfDrained("alice", sell.id))
            service.takePart("bob", buy.id, coins = false)
            assertTrue(service.releaseIfDrained("bob", buy.id))
        }
        assertEquals(1000, service.guidePrice(Items.ABYSSAL_WHIP, 1000), "one pair never establishes a guide")
        (1..2).forEach { i ->
            service.submit("s$i", OfferType.SELL, Items.ABYSSAL_WHIP, 1_000, 1)
            service.submit("b$i", OfferType.BUY, Items.ABYSSAL_WHIP, 1_000, 1)
        }
        assertEquals(1000, service.guidePrice(Items.ABYSSAL_WHIP, 1000), "median of 9000, 1000, 1000")
    }

    @Test
    fun `a house fill does not change the guide price`() {
        val (service, _) = newService()
        val fallback = GeSystemLiquidity.UNIT_PRICE.getValue(Items.FEATHER)
        val (offer, fills) = service.submit("alice", OfferType.BUY, Items.FEATHER, 50, 100)!!
        assertTrue(fills.single().fromSystem)
        assertEquals(100, offer.quantityFilled)
        assertEquals(fallback, service.guidePrice(Items.FEATHER, fallback))
    }

    @Test
    fun `the guide moves at most 5 percent per hour`() {
        val index = GeGuideIndex()
        var now = 1_000_000L
        (1..GeGuidePrice.MIN_PAIRS).forEach { GeGuidePrice.record(index, GeTradeSample(2000, "p$it", now), seed = 1000, now = now) }
        assertEquals(1050, index.price)
        now += GeGuidePrice.WINDOW_MS - 1
        GeGuidePrice.record(index, GeTradeSample(2000, "q", now), seed = 1000, now = now)
        assertEquals(1050, index.price, "still inside the first hour")
        now += 1
        GeGuidePrice.record(index, GeTradeSample(2000, "r", now), seed = 1000, now = now)
        assertEquals(1103, index.price, "ceil(1050 * 1.05) after the hour")
        assertEquals(1500, GeGuidePrice.median(listOf(1000, 2000)))
        assertEquals(2000, GeGuidePrice.median(listOf(2000, 1000, 9000)))
    }

    @Test
    fun `the house deals at the OSRS seed and the convenience fee is its only spread`() {
        // Audit E-01 merged with the local OSRS convenience fee (GeTax): the seller pays the fee on every sale, house
        // sales included, so the bid itself carries no second 2 % (the fee's rounding and cap are tested with GeTax).
        val quote = GeHousePricing.quote(seed = 1000, cost = 0)
        assertEquals(GeHouseQuote(ask = 1000, bid = 1000), quote)
        assertTrue(quote.bid - GeTax.perItem(quote.bid) < quote.ask, "a round trip through the house loses the fee")
        assertEquals(GeHouseQuote(ask = 49, bid = 49), GeHousePricing.quote(seed = 49, cost = 0))
        assertEquals(Int.MAX_VALUE, GeHousePricing.quote(seed = Int.MAX_VALUE, cost = 0).bid)
    }

    @Test
    fun `the house price respects high alchemy and the NPC shops`() {
        // Rune arrow style: OSRS 42 gp, a far higher cache value.
        val arrow = GeHousePricing.quote(seed = 42, cost = 400)
        assertEquals(240, arrow.ask, "never sells below ceil(0.6 * cost)")
        assertTrue(arrow.bid <= arrow.ask)
        // Battlestaff style: a shop sells it below the OSRS price, another buys it back high.
        val staff = GeHousePricing.quote(seed = 7834, cost = 7000, shops = GeShopBounds(lowestSell = 7000, highestBuy = 8000))
        assertEquals(7000, staff.bid, "never buys above the cheapest shop price")
        assertEquals(8000, staff.ask, "never sells below what a shop pays")
    }

    @Test
    fun `the house buys at its bid and sells at its ask`() {
        val quote = GeHouseQuote(ask = 100, bid = 98)
        val cheapSell = GrandExchangeOffer(1, "s", OfferType.SELL, Items.ABYSSAL_WHIP, 50, 2)
        GrandExchangeBook.match(mutableListOf(cheapSell), cheapSell) { quote }
        assertEquals(196L, cheapSell.collectableCoins, "paid the house bid, not the low ask")
        val highSell = GrandExchangeOffer(2, "s", OfferType.SELL, Items.ABYSSAL_WHIP, 99, 1)
        assertTrue(GrandExchangeBook.match(mutableListOf(highSell), highSell) { quote }.isEmpty())
        val buy = GrandExchangeOffer(3, "b", OfferType.BUY, Items.ABYSSAL_WHIP, 120, 1)
        GrandExchangeBook.match(mutableListOf(buy), buy) { quote }
        assertEquals(20L, buy.collectableCoins, "overbid refunded above the ask")
        val lowBuy = GrandExchangeOffer(4, "b", OfferType.BUY, Items.ABYSSAL_WHIP, 99, 1)
        assertTrue(GrandExchangeBook.match(mutableListOf(lowBuy), lowBuy) { quote }.isEmpty())
    }

    @Test
    fun `an old guide_prices file still loads`() {
        val dir = Files.createTempDirectory("ge-legacy").toFile()
        File(dir, "guide_prices.json").writeText("{\"4151\": [900, 1000, 1100]}")
        val service = GrandExchangeService(File(dir, "offers.json")) { 0L }
        service.load()
        assertEquals(1000, service.guidePrice(Items.ABYSSAL_WHIP, 5))
        // Saved in the new format and still in the old one.
        service.submit("carol", OfferType.BUY, Items.LOBSTER, 1, 1)
        assertTrue(File(dir, "guide_index.json").isFile)
        assertTrue(File(dir, "guide_prices.json").readText().contains("4151"))
        val restarted = GrandExchangeService(File(dir, "offers.json")) { 0L }.also { it.load() }
        assertEquals(1000, restarted.guidePrice(Items.ABYSSAL_WHIP, 5))
    }

    @Test
    fun `escrow markers survive the save format and settle by the book`() {
        val raw = GeEscrow.encode("tok-1", listOf(Items.COINS_995 to 5000, 4151 to 0))
        assertEquals("tok-1;995:5000", raw)
        assertEquals("tok-1" to listOf(Items.COINS_995 to 5000), GeEscrow.decode(raw))
        assertNull(GeEscrow.decode("garbage"))
        assertEquals(emptyList<Pair<Int, Int>>(), GeEscrow.owed(raw) { it == "tok-1" }, "offer reached the book: nothing to give back")
        assertEquals(listOf(Items.COINS_995 to 5000), GeEscrow.owed(raw) { false }, "crash before the book write: refund")

        val (service, _) = newService()
        assertFalse(service.hasEscrow("dave", "tok-2"))
        assertNotNull(service.submit("dave", OfferType.BUY, Items.ABYSSAL_WHIP, 10, 1, escrowToken = "tok-2"))
        assertTrue(service.hasEscrow("dave", "tok-2"))
        assertFalse(service.hasEscrow("erin", "tok-2"))
    }

    @Test
    fun `a login after a crash before the book write gives the escrow back exactly once`() {
        val coinDef = ItemDef(Items.COINS_995).apply { name = "Coins"; stacks = true }
        val definitions = mockk<DefinitionSet>(relaxed = true)
        every { definitions.get(ItemDef::class.java, any()) } returns coinDef
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(definitions, 28, ContainerStackType.STACK)
        every { player.bank } returns ItemContainer(definitions, 800, ContainerStackType.STACK)
        val (service, _) = newService()

        // Crash between the marker save and the book write: the save has the marker, the book has no offer.
        player.attr[GeEscrow.PENDING] = GeEscrow.encode("lost", listOf(Items.COINS_995 to 7000))
        GeEscrow.reconcile(player, service, "frank")
        assertEquals(7000, player.inventory.getItemCount(Items.COINS_995))
        assertNull(player.attr[GeEscrow.PENDING])
        GeEscrow.reconcile(player, service, "frank")
        assertEquals(7000, player.inventory.getItemCount(Items.COINS_995), "never twice")

        // Crash after the book write: the offer exists, nothing is given back.
        service.submit("frank", OfferType.BUY, Items.ABYSSAL_WHIP, 10, 1, escrowToken = "placed")
        player.attr[GeEscrow.PENDING] = GeEscrow.encode("placed", listOf(Items.COINS_995 to 10))
        GeEscrow.reconcile(player, service, "frank")
        assertEquals(7000, player.inventory.getItemCount(Items.COINS_995))
        assertNull(player.attr[GeEscrow.PENDING])
    }

    @Test
    fun `every exchangeable item's house price obeys the alchemy floor`() {
        val table = OsrsGuidePrices.load(Paths.get("..", "..", "data", "cfg", "ge", "osrs-ge-guide-prices.json").toFile())
        val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val definitions = DefinitionSet()
            definitions.load(library, ItemDef::class.java)
            var raised = 0
            definitions.getAllKeys(ItemDef::class.java).forEach { id ->
                val def = definitions.get(ItemDef::class.java, id)
                if (def.noted || def.id == Items.COINS_995) return@forEach
                val seed = OsrsGuidePrices.seed(def, table)
                val quote = GeHousePricing.quote(seed, def.cost)
                assertTrue(quote.ask >= ItemCurrency.highAlchValue(def.cost), "${def.id} ${def.name}: ask ${quote.ask} < high alch")
                assertTrue(quote.bid in 0..quote.ask, "${def.id} ${def.name}: bid ${quote.bid} ask ${quote.ask}")
                if (quote.ask > seed) raised++
            }
            println("GE_HOUSE_ALCH_FLOOR items whose house ask was raised above the OSRS seed: $raised")
        } finally {
            library.close()
        }
    }
}
