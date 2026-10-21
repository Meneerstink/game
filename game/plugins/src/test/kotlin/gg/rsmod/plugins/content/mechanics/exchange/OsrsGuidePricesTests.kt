package gg.rsmod.plugins.content.mechanics.exchange

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RCV-012 decision "GE guide price seeds = OSRS values" (the Jagex guide by name stays the second-last source) and A14: every
 * exchangeable 667 item is worth the OSRS hourly average of its OSRS item id ([OsrsPriceBook]); only items OSRS does not have keep the
 * cache value. The whole exchangeable roster is checked.
 */
class OsrsGuidePricesTests {
    companion object {
        /** Lower bound of items priced from OSRS (id or name); the 13 September name-only rule priced 3,414. */
        const val OSRS_PRICED_MIN = 3_500
    }

    private val table = OsrsGuidePrices.load(Paths.get("..", "..", "data", "cfg", "ge", "osrs-ge-guide-prices.json").toFile())
    private var library: CacheLibrary? = null

    @AfterTest
    fun close() {
        library?.close()
    }

    @Test
    fun `the snapshot is the wiki GEPrices module stored verbatim`() {
        assertEquals("13 September 2026 06:31:59 (UTC)", table.snapshot)
        assertEquals(4564, table.byName.size, "item entries (the two %LAST_UPDATE% keys excluded)")
        assertEquals(809253, table.price("Abyssal whip"))
        assertEquals(97, table.price("Lobster"))
        assertEquals(3719, table.price("Dragon bones"))
        assertEquals(38315, table.price("Rune platebody"))
        assertEquals(table.price("Abyssal whip"), table.price("abyssal WHIP"), "names match ignoring case")
        assertEquals(null, table.price("Coins"))
        assertEquals(109, table.price("Strength potion (4)"), "667 dose suffix spacing matches OSRS Strength potion(4)")
        assertEquals(6832, table.price("Prayer potion (3)"))
        assertEquals(null, table.price(""), "nameless items are never seeded")
        assertEquals(null, table.price("Cannonball"), "OSRS renames are not aliased")
    }

    @Test
    fun `every exchangeable item is priced by its OSRS item id, otherwise the cache value`() {
        // A14 / T11: the old assertions pinned how many items match the 13 September snapshot BY NAME (3,414 of 12,652; the other 9,000
        // fell back to the cache value, Karil's top among them). The price is now the OSRS hourly average of the item's OSRS id: a live
        // price can never be asserted, so this checks the rule for the whole exchangeable roster against the checked-in fallback data.
        val lib = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()).also { library = it }
        val definitions = DefinitionSet()
        definitions.load(lib, ItemDef::class.java)
        // The server applies `tradeable` from items.yml at boot (same loader as GrandExchangeInterfaceTests).
        var id = -1
        Paths.get("..", "..", "data", "cfg", "items.yml").toFile().forEachLine { line ->
            when {
                line.startsWith("- id: ") -> id = line.removePrefix("- id: ").trim().toInt()
                line.startsWith("  tradeable: ") && id >= 0 ->
                    definitions.getNullable(ItemDef::class.java, id)?.tradeable = line.removePrefix("  tradeable: ").trim() == "true"
            }
        }
        val book = OsrsPriceBook.checkedIn()
        @Suppress("UNCHECKED_CAST")
        val items = (definitions.getAll(ItemDef::class.java) as Map<Int, ItemDef>).values.filter { GrandExchangeInterface.exchangeable(it) }
        val cacheValue = mutableListOf<String>()
        var osrs = 0
        items.sortedBy { it.id }.forEach { def ->
            val price = book.price(def)
            assertEquals(price ?: def.cost, OsrsGuidePrices.seed(def, book), "${def.id} ${def.name}")
            if (price != null) osrs++ else cacheValue += listOf(def.id, def.name, def.cost).joinToString("\t")
        }
        File("build").mkdirs()
        File("build/osrs-guide-price-unmatched.txt").writeText(cacheValue.joinToString(System.lineSeparator()))
        println("OSRS_GUIDE_PRICES exchangeable=${items.size} osrsPriced=$osrs cacheValue=${cacheValue.size}")
        assertTrue(items.size > 12_000, "exchangeable items (tradeable, unnoted, not coins): ${items.size}")
        assertTrue(osrs > OSRS_PRICED_MIN, "items priced from OSRS by id or name ($osrs); the rest use the cache value, see build/osrs-guide-price-unmatched.txt")
        val whip = items.first { it.id == Items.ABYSSAL_WHIP }
        assertEquals(book.hourly[Items.ABYSSAL_WHIP], OsrsGuidePrices.seed(whip, book), "the whip is its OSRS hourly average")
        // An OSRS price is never 0, and the OSRS rule never turns a cache value into 0. (Cache dummies that are tradeable with cost 0 and no
        // OSRS price exist today - 22029 Pharaoh shendyt, 8972 Bowl of red water ...; that is a data gap outside A14, see team\findings.)
        val zeroWithOsrsPrice = items.filter { book.price(it) == null && it.cost > 0 && OsrsGuidePrices.seed(it, book) <= 0 } +
            items.filter { book.price(it) != null && OsrsGuidePrices.seed(it, book) <= 0 }
        assertTrue(zeroWithOsrsPrice.isEmpty(), "items priced 0 although OSRS or the cache has a value: ${zeroWithOsrsPrice.take(10).map { "${it.id} ${it.name}" }}")
    }

    @Test
    fun `the Grand Exchange selection uses the OSRS seed before any trade`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/exchange/GrandExchangeInterface.kt").readText()
        assertTrue("service.guidePrice(def.id, OsrsGuidePrices.seed(def))" in source)
    }
}
