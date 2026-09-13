package gg.rsmod.plugins.content.mechanics.exchange

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** RCV-011: GE buy limits (whole Void table vs the 667 cache, every limited item capped) and the History screen 643. */
class GrandExchangeBuyLimitTests {
    private fun newService(clock: () -> Long): Pair<GrandExchangeService, File> {
        val file = File(Files.createTempDirectory("ge").toFile(), "offers.json")
        return GrandExchangeService(file, clock) to file
    }

    private fun norm(text: String) = text.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun subsequence(
        small: String,
        big: String,
    ): Boolean {
        var i = 0
        for (c in big) if (i < small.length && small[i] == c) i++
        return i == small.length
    }

    @Test
    fun `buy limit table equals the Void item data and every id is the same 667 item`() {
        val voidData = Paths.get("..", "..", "..", "..", "Donors", "void", "data").toFile()
        assertTrue(voidData.isDirectory, "Void donor data not found at ${voidData.absolutePath}")
        val voidRows = mutableMapOf<Int, Pair<Int, String>>()
        voidData.walkTopDown().filter { it.name.endsWith(".items.toml") }.forEach { file ->
            var key: String? = null
            var id: Int? = null
            var limit: Int? = null
            fun flush() {
                if (key != null && id != null && limit != null) voidRows[id!!] = limit!! to key!!
            }
            file.forEachLine { line ->
                val section = Regex("^\\[([^\\].]+)]").find(line)
                when {
                    section != null -> {
                        flush()
                        key = section.groupValues[1]
                        id = null
                        limit = null
                    }
                    line.startsWith("id = ") -> id = line.removePrefix("id = ").trim().toIntOrNull()
                    line.startsWith("limit = ") -> limit = line.removePrefix("limit = ").trim().toIntOrNull()
                }
            }
            flush()
        }
        val table = GeBuyLimits.table()
        assertEquals(voidRows.keys - EXCLUDED, table.keys, "table ids == Void ids minus the recorded conflict")
        voidRows.filterKeys { it !in EXCLUDED }.forEach { (id, row) -> assertEquals(row.first, table[id], "limit of $id (${row.second})") }

        val wrong =
            voidRows.filterKeys { it !in EXCLUDED }.mapNotNull { (id, row) ->
                val def = DEFINITIONS.getNullable(ItemDef::class.java, id) ?: return@mapNotNull "$id ${row.second}: absent from the 667 cache"
                if (def.noted) return@mapNotNull "$id ${row.second}: noted in the 667 cache"
                // Every word of the cache name (charge/dose numbers ignored) is spelled, in order, inside the Void key:
                // tolerates "Zamorak platebody" = rune_platebody_zamorak and "Ahrim's hood 0" = ahrims_hood_broken,
                // while a drifted id (an unrelated item) still fails.
                val words = def.name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && !it.all(Char::isDigit) }
                val key = norm(row.second)
                if (id in REVIEWED_ALIASES || words.all { subsequence(it, key) }) null else "$id void=${row.second} cache='${def.name}'"
            }
        assertTrue(wrong.isEmpty(), "${wrong.size} rows:\n" + wrong.take(60).joinToString("\n"))
        REVIEWED_ALIASES.forEach { assertTrue(it in table, "reviewed alias $it is a table row") }
        assertEquals("Nardah teleport", DEFINITIONS.get(ItemDef::class.java, 19475).name, "19475 conflict still holds")
        assertEquals("pollnivneach_teleport", voidRows[19475]?.second)
    }

    @Test
    fun `every limited item caps its buyer and resets after four hours`() {
        var now = 1_000_000L
        GeBuyLimits.table().forEach { (itemId, limit) ->
            val ledger = GeBuyLedger { now }
            val book = mutableListOf<GrandExchangeOffer>()
            var id = 1L
            fun offer(
                user: String,
                type: OfferType,
                quantity: Int,
            ) = GrandExchangeOffer(id++, user, type, itemId, 1, quantity).also { book.add(it) }

            offer("seller", OfferType.SELL, limit + 2)
            val buy = offer("buyer", OfferType.BUY, limit + 1)
            GrandExchangeBook.match(book, buy, ledger)
            assertEquals(limit, buy.quantityFilled, "item $itemId limit $limit")
            assertEquals(OfferStatus.ACTIVE, buy.status, "item $itemId rests at its limit")

            val other = offer("other", OfferType.BUY, 1)
            GrandExchangeBook.match(book, other, ledger)
            assertEquals(1, other.quantityFilled, "item $itemId: limits are per buyer")

            now += GeBuyLimits.WINDOW_MS - 1
            val early = offer("buyer", OfferType.BUY, 1)
            GrandExchangeBook.match(book, early, ledger)
            assertEquals(0, early.quantityFilled, "item $itemId: still inside the window")
            now += 1
            assertEquals(limit, ledger.remaining("buyer", itemId), "item $itemId: window over")
            now += 1
        }
    }

    @Test
    fun `a sell skips a limited buyer, resting buys fill only up to the limit, system liquidity is capped`() {
        val saved = GeBuyLimits.table()
        try {
            GeBuyLimits.set(mapOf(Items.ABYSSAL_WHIP to 10, Items.FEATHER to 5))
            val ledger = GeBuyLedger { 0L }
            val book = mutableListOf<GrandExchangeOffer>()
            val capped = GrandExchangeOffer(1, "capped", OfferType.BUY, Items.ABYSSAL_WHIP, 100, 20, createdAtMs = 1).also { book.add(it) }
            ledger.record("capped", Items.ABYSSAL_WHIP, 10)
            val next = GrandExchangeOffer(2, "next", OfferType.BUY, Items.ABYSSAL_WHIP, 90, 3, createdAtMs = 2).also { book.add(it) }
            val sell = GrandExchangeOffer(3, "seller", OfferType.SELL, Items.ABYSSAL_WHIP, 90, 3).also { book.add(it) }
            val fills = GrandExchangeBook.match(book, sell, ledger)
            assertEquals(0, capped.quantityFilled, "best-priced buyer is at the limit and is skipped")
            assertEquals(3, next.quantityFilled)
            assertEquals(listOf(2L), fills.map { it.buyOfferId })
            assertEquals(270L, sell.coinsTraded)

            val feathers = GrandExchangeOffer(4, "f", OfferType.BUY, Items.FEATHER, 5, 100)
            val system = GrandExchangeBook.match(mutableListOf(feathers), feathers, ledger)
            assertEquals(5, feathers.quantityFilled, "system liquidity obeys the limit")
            assertEquals(true, system.single().fromSystem)
            assertEquals(OfferStatus.ACTIVE, feathers.status)
            assertEquals(10L, feathers.coinsTraded)
        } finally {
            GeBuyLimits.set(saved)
        }
    }

    @Test
    fun `finished offers enter the history newest first, five kept, persisted, wording from Void`() {
        val (service, file) = newService { 0L }
        fun trade(
            price: Int,
            quantity: Int,
        ) {
            val sell = service.submit("s", OfferType.SELL, Items.LOBSTER, price, quantity)!!.first
            val buy = service.submit("b", OfferType.BUY, Items.LOBSTER, price, quantity)!!.first
            service.takePart("s", sell.id, coins = true)
            service.takePart("b", buy.id, coins = false)
            service.takePart("b", buy.id, coins = true)
            assertTrue(service.releaseIfDrained("s", sell.id))
            assertTrue(service.releaseIfDrained("b", buy.id))
        }
        (1..6).forEach { trade(price = 1000 * it, quantity = it) }
        val unfilled = service.submit("b", OfferType.BUY, Items.LOBSTER, 1, 1)!!.first
        service.cancel("b", unfilled.id)
        service.takePart("b", unfilled.id, coins = true)
        assertTrue(service.releaseIfDrained("b", unfilled.id))

        val bought = service.historyFor("b")
        assertEquals(5, bought.size, "an aborted offer that traded nothing is not history; five rows kept")
        assertEquals(listOf(6, 5, 4, 3, 2), bought.map { it.amount }, "newest first")
        assertEquals(GeHistoryEntry(Items.LOBSTER, 6, 36_000, sold = false), bought.first())
        assertTrue(service.historyFor("s").all { it.sold })

        val restarted = GrandExchangeService(file) { 0L }.also { it.load() }
        assertEquals(bought, restarted.historyFor("b"))

        val rows = GrandExchangeHistory.rows(listOf(bought.first(), service.historyFor("s").first())) { "Lobster" }
        assertEquals(GrandExchangeHistory.Row("You bought", "Lobster", "6", "It cost you<br>36,000 gp"), rows[0])
        assertEquals(GrandExchangeHistory.Row("You sold", "Lobster", "6", "You got<br>36,000 gp"), rows[1])
        assertEquals(GrandExchangeHistory.Row("", "", "", ""), rows[4])
    }

    @Test
    fun `history screen components exist in the 667 cache and clerks offer History`() {
        val H = GrandExchangeHistory
        (H.TYPE + H.AMOUNT + H.NAME + H.PRICE).forEach { assertNotNull(LIBRARY.data(3, H.INTERFACE, it), "643:$it missing") }
        assertEquals(20, (H.TYPE + H.AMOUNT + H.NAME + H.PRICE).distinct().size)
        listOf(Npcs.GRAND_EXCHANGE_CLERK, Npcs.GRAND_EXCHANGE_CLERK_2240, Npcs.GRAND_EXCHANGE_CLERK_2241, Npcs.GRAND_EXCHANGE_CLERK_2593).forEach { id ->
            val options = DEFINITIONS.get(NpcDef::class.java, id).options.filterNotNull()
            assertTrue("History" in options, "clerk $id options $options")
        }
    }

    companion object {
        /** SOURCE_CONFLICT: Void `pollnivneach_teleport` is "Nardah teleport" in the 667 cache. */
        val EXCLUDED = setOf(19475)

        /**
         * Rows whose 667 name is not a letter-subsequence of the Void key, each reviewed by hand on 2026-09-13 as the same
         * item under another spelling (e.g. stripy pirate shirts, "1/2 plain pizza" = plain_pizza_half, "Swamp lizard" =
         * green_salamander, beer "M1" = keg 1, BA wave tickets, "Adamantite limbs" = adamant_limbs).
         */
        val REVIEWED_ALIASES =
            setOf(
                7110, 7122, 7128, 7134, 13358, 13360, 13362, 13961, 13963, 15465, 15466, 15467, 15468, 15469, 15470, 15471,
                15472, 15473, 12995, 13007, 7386, 7388, 10330, 10332, 10354, 10356, 10358, 10360, 10392, 6629, 4823, 2997, 9865,
                9866, 9867, 5851, 5853, 5855, 5857, 5867, 5869, 5871, 5875, 5877, 5879, 5881, 5883, 5885, 5887, 5889, 5891, 5893,
                5895, 5897, 5915, 5917, 5919, 5921, 5923, 5925, 5927, 5929, 1893, 1899, 2291, 2295, 2299, 2303, 2331, 2333, 2335,
                7180, 7190, 7200, 7210, 7220, 1683, 1702, 10149, 1011, 3182, 9381, 9429, 9431, 9465, 12136, 12138, 12225, 12248,
                12465, 12666, 12667,
            )

        private val DEFINITIONS = DefinitionSet()
        private lateinit var LIBRARY: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            LIBRARY = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(LIBRARY)
            GeBuyLimits.load(Paths.get("..", "..", "data", "cfg", "ge", "buy-limits.json").toFile())
        }

        @AfterClass
        @JvmStatic
        fun close() {
            GeBuyLimits.set(emptyMap())
            LIBRARY.close()
        }
    }
}
