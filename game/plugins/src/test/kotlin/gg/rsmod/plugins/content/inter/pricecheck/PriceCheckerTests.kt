package gg.rsmod.plugins.content.inter.pricecheck

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Coverage for the only part of the Price Checker that is arithmetic rather than interface wiring:
 * what a checked item is declared to be worth.
 *
 * The screen publishes those figures in varcs 700..728, which carry ints, while a checked stack can
 * be large enough that its value does not fit in one. Everything below is about that boundary, and
 * about a note being worth what the item is rather than nothing.
 *
 * The interface flow itself needs a client and is not exercised here.
 */
class PriceCheckerTests {
    @Test
    fun `an empty slot is worth nothing`() {
        assertEquals(0, PriceChecker.value(definitions, null))
    }

    @Test
    fun `an item value scales with its amount`() {
        val single = PriceChecker.value(definitions, Item(WHIP, 1))

        assertNotEquals(0, single, "the test item needs a non-zero cost for this to prove anything")
        assertEquals(single * 3, PriceChecker.value(definitions, Item(WHIP, 3)))
    }

    @Test
    fun `an OSRS-matched item uses the guide-price seed instead of the cache cost`() {
        assertEquals(809253, PriceChecker.value(definitions, Item(WHIP, 1)))
    }

    @Test
    fun `a noted item is worth what the item is worth`() {
        assertEquals(
            PriceChecker.value(definitions, Item(WHIP, 3)),
            PriceChecker.value(definitions, Item(NOTED_WHIP, 3)),
        )
    }

    @Test
    fun `a stack too valuable to fit in a varc reads as the ceiling, not as a negative number`() {
        // cost * Int.MAX_VALUE overflows an int many times over; the player must see a ceiling
        // rather than a wrapped, negative "total value".
        val value = PriceChecker.value(definitions, Item(WHIP, Int.MAX_VALUE))

        assertEquals(Int.MAX_VALUE, value)
    }

    @Test
    fun `the total is the sum of the slots`() {
        val container = ItemContainer(definitions, PriceChecker.CAPACITY, ContainerStackType.NORMAL)
        container.add(WHIP, 1)
        container.add(NOTED_WHIP, 2)

        assertEquals(
            PriceChecker.value(definitions, Item(WHIP, 3)),
            PriceChecker.total(definitions, container),
        )
    }

    @Test
    fun `a total too large to fit in a varc reads as the ceiling, not as a negative number`() {
        val container = ItemContainer(definitions, PriceChecker.CAPACITY, ContainerStackType.STACK)
        container.add(WHIP, Int.MAX_VALUE)
        container.add(NOTED_WHIP, Int.MAX_VALUE)

        assertEquals(Int.MAX_VALUE, PriceChecker.total(definitions, container))
    }

    @Test
    fun `the value varcs the screen reads all fit inside the per-slot range`() {
        // Clientscript 2185 is a 28-case switch over varcs 700..727, with 728 as the total. A
        // container larger than 28 slots would silently write over the total.
        assertTrue(PriceChecker.CAPACITY == 28)
    }

    @Test
    fun `the live grid uses the Grand Exchange market valuation`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/inter/pricecheck/PriceChecker.kt").readText()

        assertTrue("value(player, container[slot])" in source)
        assertTrue("total(player, container)" in source)
        assertTrue("GrandExchangeService::class.java" in source)
        assertTrue("OsrsGuidePrices.seed" in source)
    }

    companion object {
        /** Abyssal whip and its noted form - the pair the container tests in this module use. */
        private const val WHIP = 4151
        private const val NOTED_WHIP = 4152

        private val definitions = DefinitionSet()

        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            // Tests run with the module directory (game/plugins) as their working directory, so
            // the cache is two levels up at game/game/data/cache - the same path every other
            // cache-backed test in this module uses.
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())

            definitions.loadAll(store)

            assertNotEquals(0, definitions.getCount(ItemDef::class.java))
        }
    }
}
