package gg.rsmod.plugins.content.mechanics.trading.impl

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.inter.pricecheck.PriceChecker
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A15 (owner 2026-10-11: "trade screen doesnt show the correct grand exchange value"): the trade window's total is the one GE price
 * function (the number the price checker and the GE show), not the 667 cache cost.
 */
class TradeValueA15Tests {
    private var library: CacheLibrary? = null

    @AfterTest
    fun close() {
        library?.close()
    }

    @Test
    fun `the trade total of Karils top and Ahrims robe top is their Grand Exchange value, the same as the price checker`() {
        val lib = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()).also { library = it }
        val definitions = DefinitionSet()
        definitions.load(lib, ItemDef::class.java)
        val offer = ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        offer.add(4736, 1)
        offer.add(4712, 1)
        val expected = PriceChecker.value(definitions, Item(4736, 1)).toLong() + PriceChecker.value(definitions, Item(4712, 1))
        assertTrue(expected > 1_000_000, "the GE value of the two tops is over a million (cache value would be 100,000)")
        val exchangePrice = { id: Int -> gg.rsmod.plugins.content.mechanics.exchange.GePrice.unit(definitions, null, id) }
        assertEquals(expected, TradeItems.value(offer, exchangePrice), "trade total = price checker total")
    }
}
