package gg.rsmod.plugins.content.mechanics.exchange

import com.google.gson.JsonParser
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A14: the daily OSRS price update ([OsrsPriceSync]) and the price book it feeds, on fixtures only (a live price can never be asserted).
 */
class OsrsPriceSyncTests {
    private lateinit var before: OsrsPriceBook

    @BeforeTest
    fun remember() {
        before = OsrsGuidePrices.book
    }

    @AfterTest
    fun restore() {
        OsrsGuidePrices.book = before
    }

    private fun hourJson(
        count: Int,
        extra: String = "",
    ): String {
        val entries = (1..count).joinToString(",") { """"$it":{"avgHighPrice":${it * 10.0},"highPriceVolume":3,"avgLowPrice":${it * 10.0},"lowPriceVolume":1}""" }
        return """{"data":{$entries${if (extra.isEmpty()) "" else ",$extra"}},"timestamp":1792573200}"""
    }

    private fun tempFile() = File(Files.createTempDirectory("ge-prices").toFile(), "osrs-prices.json")

    @Test
    fun `the price is the volume-weighted mean of the average high and low price`() {
        // OSRS Wiki /1h 2026-10-21 4736: avgHigh 821844.26 (43 trades), avgLow 802281.43 (7 trades)
        assertEquals(819_105,OsrsPriceSync.weighted(821_844.26, 43, 802_281.43, 7))
        assertEquals(1000, OsrsPriceSync.weighted(1000.0, 5, null, 0), "one side only")
        assertEquals(900, OsrsPriceSync.weighted(null, 0, 900.0, 4), "one side only")
        assertEquals(150, OsrsPriceSync.weighted(100.0, 0, 200.0, 0), "no volume at all: plain mean")
        assertNull(OsrsPriceSync.weighted(null, 0, null, 0))
        assertEquals(1, OsrsPriceSync.weighted(0.2, 1, 0.2, 1), "never 0")
        assertEquals(Int.MAX_VALUE, OsrsPriceSync.weighted(9.0e12, 1, 9.0e12, 1), "clamped to the Int range")
    }

    @Test
    fun `an item without an hourly trade takes the latest trade, then yesterday's price`() {
        val hour = JsonParser().parse("""{"data":{"1":{"avgHighPrice":null,"highPriceVolume":0,"avgLowPrice":null,"lowPriceVolume":0},"2":{"avgHighPrice":50.0,"highPriceVolume":1,"avgLowPrice":50.0,"lowPriceVolume":1}}}""").asJsonObject
        val latest = JsonParser().parse("""{"data":{"1":{"high":300,"highTime":1,"low":100,"lowTime":1},"3":{"high":null,"highTime":null,"low":null,"lowTime":null}}}""").asJsonObject
        val prices = OsrsPriceSync.compute(hour, latest, previous = mapOf(3 to 777, 4 to 888))
        assertEquals(200, prices[1], "mean of the latest high and low")
        assertEquals(50, prices[2])
        assertEquals(777, prices[3], "never traded: yesterday's price")
        assertEquals(888, prices[4], "missing from today's lists: yesterday's price")
    }

    @Test
    fun `a refresh stores the prices and swaps them into the book, a second one inside a day does nothing`() {
        val file = tempFile()
        val book = OsrsGuidePrices.book
        val requests = mutableListOf<String>()
        val get: (String) -> String = { route ->
            requests += route
            if (route == "/1h") hourJson(1500) else """{"data":{}}"""
        }
        assertTrue(OsrsPriceSync.refresh(file, now = 1_000L, get = get))
        assertEquals(listOf("/1h", "/latest"), requests)
        assertEquals(1500 * 10, OsrsGuidePrices.book.hourly[1500])
        assertEquals(book.idMap, OsrsGuidePrices.book.idMap, "the id map is not touched")
        val stored = OsrsPriceSync.readStoredPrices(file)!!
        assertEquals(1792573200L, stored.timestamp)
        assertEquals(1500 * 10, stored.prices[1500])

        requests.clear()
        assertFalse(OsrsPriceSync.refresh(file, now = 1_000L + OsrsPriceSync.INTERVAL_MS - 1, get = get))
        assertTrue(requests.isEmpty(), "still inside the day: no request")
        assertTrue(OsrsPriceSync.refresh(file, now = 1_000L + OsrsPriceSync.INTERVAL_MS, get = get))
    }

    @Test
    fun `a failed or broken fetch keeps yesterday's prices, never 0`() {
        val file = tempFile()
        assertTrue(OsrsPriceSync.refresh(file, now = 0L, get = { if (it == "/1h") hourJson(1500) else """{"data":{}}""" }))
        val good = OsrsGuidePrices.book

        assertFalse(OsrsPriceSync.refresh(file, now = OsrsPriceSync.INTERVAL_MS, get = { throw java.io.IOException("offline") }))
        assertEquals(good, OsrsGuidePrices.book, "offline: same book")
        assertFalse(OsrsPriceSync.refresh(file, now = OsrsPriceSync.INTERVAL_MS, get = { "<html>blocked</html>" }), "not JSON")
        assertFalse(OsrsPriceSync.refresh(file, now = OsrsPriceSync.INTERVAL_MS, get = { hourJson(10) }), "a list of ten items is a broken answer")
        assertEquals(good, OsrsGuidePrices.book)
        assertEquals(1500 * 10, OsrsPriceSync.readStoredPrices(file)!!.prices[1500], "the stored file is untouched")
        assertTrue(OsrsGuidePrices.book.hourly.values.all { it > 0 })
    }

    @Test
    fun `a restart loads the stored prices on top of the checked-in ones`() {
        val file = tempFile()
        OsrsPriceSync.writeStored(file, mapOf(4736 to 123_456), timestamp = 5L, now = 9L)
        val book = OsrsPriceBook.checkedIn(file)
        assertEquals(123_456, book.hourly[4736])
        assertNotNull(book.hourly[4151], "an id the stored list lacks keeps the checked-in price")
    }

    @Test
    fun `the lookup order is the id map, then the OSRS name, then the guide by name, otherwise the cache value`() {
        fun def(
            id: Int,
            name: String,
            cost: Int,
        ): ItemDef = ItemDef(id).also { it.name = name; it.cost = cost }
        val book =
            OsrsPriceBook(
                hourly = mapOf(100 to 5000, 200 to 7000),
                idMap = mapOf(1 to 100),
                osrsNames = mapOf(100 to "Leathertop", 200 to "Plain thing"),
                guide = OsrsGuidePrices.Table(mapOf("Guide only" to 321), "fixture"),
            )
        assertEquals(5000, OsrsGuidePrices.seed(def(1, "Renamed top", 50), book), "id map: a renamed item")
        assertEquals(7000, OsrsGuidePrices.seed(def(2, "plain thing", 50), book), "same name, different id")
        assertEquals(321, OsrsGuidePrices.seed(def(3, "Guide only", 50), book), "the Jagex guide by name")
        assertEquals(50, OsrsGuidePrices.seed(def(4, "Steel titan pouch", 50), book), "667-only: cache value")
        assertNull(OsrsGuidePrices.osrsPrice(def(4, "Steel titan pouch", 50), book))
        val noPrice = OsrsPriceBook(emptyMap(), mapOf(1 to 100), mapOf(100 to "Leathertop"), OsrsGuidePrices.Table(emptyMap(), ""))
        assertEquals(50, OsrsGuidePrices.seed(def(1, "Renamed top", 50), noPrice), "an OSRS item without any price: cache value")
    }

    @Test
    fun `player trades no longer move the price of an item OSRS has, but still steer a 667-only item`() {
        OsrsGuidePrices.book =
            OsrsPriceBook(
                hourly = mapOf(100 to 1000),
                idMap = mapOf(4736 to 100),
                osrsNames = mapOf(100 to "Leathertop"),
                guide = OsrsGuidePrices.Table(emptyMap(), "fixture"),
            )
        val service = GrandExchangeService(File(Files.createTempDirectory("ge-a14").toFile(), "offers.json"))
        val definitions = mockk<DefinitionSet>(relaxed = true)
        every { definitions.getNullable(ItemDef::class.java, 4736) } returns ItemDef(4736).also { it.name = "Karil's top"; it.cost = 50 }
        every { definitions.getNullable(ItemDef::class.java, 77777) } returns ItemDef(77777).also { it.name = "Pouch"; it.cost = 1000 }
        service.useDefinitions(definitions)
        (1..3).forEach { i ->
            listOf(4736, 77777).forEach { item ->
                service.submit("s$i", OfferType.SELL, item, 1040, 1)
                service.submit("b$i", OfferType.BUY, item, 1040, 1)
            }
        }
        assertEquals(1000, service.guidePrice(4736, 1000), "OSRS price: trades do not move it")
        assertEquals(1040, service.guidePrice(77777, 1000), "667-only item: the trade median steers it")
    }
}
