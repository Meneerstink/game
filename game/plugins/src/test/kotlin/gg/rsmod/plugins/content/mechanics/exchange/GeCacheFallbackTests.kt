package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.inter.pricecheck.PriceChecker
import gg.rsmod.plugins.content.mechanics.death.GuidePriceValueProvider
import gg.rsmod.plugins.content.mechanics.pvp.LootKeys
import gg.rsmod.plugins.content.mechanics.trading.impl.TradeItems
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A14: persisted trade prices must never override the owner's OSRS-or-cache valuation rule. */
class GeCacheFallbackTests {
    private lateinit var previous: OsrsPriceBook
    // Synthetic fixtures: unmapped 667 content, mapped OSRS content without a price, zero cost and the Int boundary.
    private val costs = mapOf(70001 to 1000, 70002 to 2300, 70003 to 0, 70004 to Int.MAX_VALUE)
    private val osrsItem = 70005
    private val note = 70006
    private val definitions = mockk<DefinitionSet>()

    @BeforeTest
    fun prepare() {
        previous = OsrsGuidePrices.book
        OsrsGuidePrices.book = OsrsPriceBook(
            hourly = mapOf(100 to 5000),
            idMap = mapOf(70002 to 200, osrsItem to 100),
            osrsNames = mapOf(100 to "OSRS fixture", 200 to "Unpriced OSRS fixture"),
            guide = OsrsGuidePrices.Table(emptyMap(), "fixture"),
        )
        (costs + (osrsItem to 50) + (note to 9999)).forEach { (id, cost) ->
            val def = ItemDef(id).also {
                it.name = "Fixture $id"
                it.cost = cost
                if (id == note) { it.noteTemplateId = 1; it.noteLinkId = 70001; it.stacks = true }
            }
            every { definitions.get(ItemDef::class.java, id) } returns def
            every { definitions.getNullable(ItemDef::class.java, id) } returns def
        }
    }

    @AfterTest
    fun restore() { OsrsGuidePrices.book = previous }

    private fun service(dir: File) = GrandExchangeService(File(dir, "offers.json")) { 0L }
    private fun directory() = Files.createTempDirectory("a14-cache-fallback").toFile()

    private fun assertConsumers(exchange: GrandExchangeService) {
        val world = mockk<World>()
        every { world.definitions } returns definitions
        every { world.getService(GrandExchangeService::class.java) } returns exchange
        val player = mockk<Player>()
        every { player.world } returns world
        val risk = GuidePriceValueProvider(world)
        (costs + (osrsItem to 5000) + (note to 1000)).forEach { (id, expected) ->
            val item = Item(id, 2)
            val stack = expected.toLong() * 2
            assertEquals(expected, GePrice.unit(world, id), "GE unit $id")
            assertEquals(stack, GePrice.stack(world, item), "GE stack $id")
            assertEquals(expected.toLong(), risk.getValue(id), "death ranking / risk skull $id")
            assertEquals(stack, LootKeys.value(world, listOf(item)), "loot key $id")
            assertEquals(stack.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), PriceChecker.value(player, item), "price checker $id")
            assertEquals(stack, TradeItems.value(item) { GePrice.unit(world, it) }, "trade item $id")
            val container = ItemContainer(definitions, 28, ContainerStackType.STACK)
            container.add(item)
            assertEquals(stack, TradeItems.value(container) { GePrice.unit(world, it) }, "trade total $id")
        }
        costs.forEach { (id, cost) -> assertEquals(cost, exchange.guidePrice(id, cost), "GE guide $id") }
        assertEquals(5000, exchange.guidePrice(osrsItem, 5000), "OSRS still wins over trade history")
    }

    @Test
    fun `an established guide index cannot override any valuation consumer including notes and zero costs`() {
        val dir = directory()
        File(dir, "guide_index.json").writeText((costs.keys + osrsItem).joinToString(",", "{", "}") {
            "\"$it\":{\"price\":9000,\"anchorPrice\":9000,\"anchorMs\":0,\"samples\":[]}"
        })
        val exchange = service(dir).also { it.useDefinitions(definitions); it.load() }
        assertConsumers(exchange)
        // Force a save, then load the exact same persisted index in a new service.
        exchange.submit("save", OfferType.BUY, 70001, 1, 1)
        assertTrue(File(dir, "guide_index.json").readText().contains("9000"), "established index really existed")
        assertConsumers(service(dir).also { it.useDefinitions(definitions); it.load() })
    }

    @Test
    fun `legacy guide price migration and restart cannot override any valuation consumer`() {
        val dir = directory()
        File(dir, "guide_prices.json").writeText((costs.keys + osrsItem).joinToString(",", "{", "}") { "\"$it\":[8000,9000,10000]" })
        val exchange = service(dir).also { it.useDefinitions(definitions); it.load() }
        assertConsumers(exchange)
        exchange.submit("save", OfferType.BUY, 70001, 1, 1)
        assertTrue(File(dir, "guide_index.json").isFile, "migration was persisted")
        assertConsumers(service(dir).also { it.useDefinitions(definitions); it.load() })
    }

    @Test
    fun `independent player trades cannot replace cache or OSRS prices on any valuation surface`() {
        val dir = directory()
        val exchange = service(dir)
        // No house definitions, so these fills must be between real independent accounts.
        (costs.keys + osrsItem).forEach { id ->
            repeat(3) { pair ->
                exchange.submit("seller-$id-$pair", OfferType.SELL, id, 9000, 1)
                val result = exchange.submit("buyer-$id-$pair", OfferType.BUY, id, 9000, 1)!!
                assertEquals(1, result.first.quantityFilled)
                assertTrue(result.second.none { it.fromSystem }, "player fill rather than house liquidity")
            }
        }
        exchange.useDefinitions(definitions)
        assertConsumers(exchange)
        assertConsumers(service(dir).also { it.useDefinitions(definitions); it.load() })
    }

    @Test
    fun `even without definitions a loaded median cannot replace the supplied authoritative fallback`() {
        val dir = directory()
        File(dir, "guide_index.json").writeText("""{"70001":{"price":9000,"samples":[]}}""")
        val exchange = service(dir).also { it.load() }
        assertEquals(1000, exchange.guidePrice(70001, 1000))
        assertEquals(0, exchange.guidePrice(70001, 0), "exact zero cache fallback")
    }
}
