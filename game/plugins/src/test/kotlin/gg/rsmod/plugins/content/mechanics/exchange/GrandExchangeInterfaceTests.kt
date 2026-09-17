package gg.rsmod.plugins.content.mechanics.exchange

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.tools.importer.InterfaceHookProbeTool
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RCV-010 C3: native 667 Grand Exchange screens, slot model, price range and escrow conservation. */
class GrandExchangeInterfaceTests {
    private fun newService(): GrandExchangeService = GrandExchangeService(File(Files.createTempDirectory("ge").toFile(), "offers.json"))

    @Test
    fun `every bound component carries the expected op label in the cache`() {
        val G = GrandExchangeInterface
        val expected = mutableMapOf<Int, String>()
        G.VIEW_OFFER.forEach { expected[it] = "Make Offer" }
        G.MAKE_BUY.forEach { expected[it] = "Make Buy Offer" }
        G.MAKE_SELL.forEach { expected[it] = "Make Sell Offer" }
        expected[G.BACK] = "Back"
        expected[G.DECREASE_QUANTITY] = "Decrease Quantity"
        expected[G.INCREASE_QUANTITY] = "Increase Quantity"
        expected[G.ADD_1] = "Add 1"
        expected[G.ADD_10] = "Add 10"
        expected[G.ADD_100] = "Add 100"
        expected[G.ADD_1000] = "Add 1000"
        expected[G.EDIT_QUANTITY] = "Edit Quantity"
        expected[G.DECREASE_PRICE] = "Decrease Price"
        expected[G.INCREASE_PRICE] = "Increase Price"
        expected[G.OFFER_GUIDE_PRICE] = "Offer Guide Price"
        expected[G.EDIT_PRICE] = "Edit Price"
        expected[G.PLUS_FIVE_PERCENT] = "Increase Price"
        expected[G.MINUS_FIVE_PERCENT] = "Decrease Price"
        expected[G.CONFIRM] = "Confirm Offer"
        expected[G.CHOOSE_ITEM] = "Choose Item"
        expected[G.ABORT] = "Abort Offer"
        val wrong =
            expected.mapNotNull { (component, label) ->
                val ops = InterfaceHookProbeTool.componentOps(LIBRARY.data(3, G.MAIN, component)!!)
                if (ops.firstOrNull() == label) null else "105:$component expected '$label' got $ops"
            }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
        // View boxes also carry Abort Offer as op2 (IF_BUTTON2 / opcode 64).
        G.VIEW_OFFER.forEach { assertEquals("Abort Offer", InterfaceHookProbeTool.componentOps(LIBRARY.data(3, G.MAIN, it)!!).getOrNull(1), "105:$it op2") }
        // Collect item boxes and the sell inventory / collection box layers exist.
        (G.COLLECT.map { G.MAIN to it } + (G.SELL_INVENTORY to G.SELL_INVENTORY_ITEMS) + G.COLLECTION_BOX_OFFERS.map { G.COLLECTION_BOX to it })
            .forEach { (i, c) -> assertNotNull(LIBRARY.data(3, i, c), "$i:$c missing") }
        assertEquals(GrandExchangeService.SLOTS, G.VIEW_OFFER.size)
        assertEquals(GrandExchangeService.SLOTS, G.MAKE_BUY.size)
        assertEquals(GrandExchangeService.SLOTS, G.MAKE_SELL.size)
        assertEquals(GrandExchangeService.SLOTS, G.COLLECTION_BOX_OFFERS.size)
    }

    @Test
    fun `status byte matches the client decode for every type and state`() {
        val G = GrandExchangeInterface
        assertEquals(0, G.status(null))
        for (type in OfferType.values()) {
            for (state in OfferStatus.values()) {
                val offer = GrandExchangeOffer(1, "a", type, Items.ABYSSAL_WHIP, 10, 5, status = state)
                val status = G.status(offer)
                assertEquals(if (type == OfferType.SELL) 8 else 0, status and 0x8, "$type/$state type bit")
                assertEquals(if (state == OfferStatus.ACTIVE) 2 else 5, status and 0x7, "$type/$state state")
            }
        }
        val filled = GrandExchangeOffer(1, "a", OfferType.SELL, Items.ABYSSAL_WHIP, 1000, 5, quantityFilled = 3)
        val msg = G.slotMessage(4, filled)
        assertEquals(listOf(4, 10, Items.ABYSSAL_WHIP, 1000, 5, 3, 3000), listOf(msg.slot, msg.status, msg.item, msg.price, msg.count, msg.completedCount, msg.completedGold))
    }

    @Test
    fun `price stays inside the five percent guide range and quantity follows Novite`() {
        val G = GrandExchangeInterface
        val sel = GeSelection(0, OfferType.BUY, itemId = Items.ABYSSAL_WHIP, quantity = 1, price = 1000, guide = 1000)
        assertEquals(950..1050, G.priceRange(1000))
        assertEquals(1050, G.adjustPrice(sel, G.PLUS_FIVE_PERCENT))
        assertEquals(950, G.adjustPrice(sel, G.MINUS_FIVE_PERCENT))
        assertEquals(1001, G.adjustPrice(sel, G.INCREASE_PRICE))
        sel.price = 1050
        assertEquals(1050, G.adjustPrice(sel, G.PLUS_FIVE_PERCENT), "clamped at the top of the range")
        assertEquals(1050, G.clampPrice(sel, 5000))
        assertEquals(950, G.clampPrice(sel, 1))
        assertEquals(1000, G.adjustPrice(sel, G.OFFER_GUIDE_PRICE))
        assertEquals(1001, G.adjustQuantity(sel, G.ADD_1000, 0))
        val sell = GeSelection(0, OfferType.SELL, itemId = Items.ABYSSAL_WHIP, quantity = 1, price = 1000, guide = 1000)
        assertEquals(7, G.adjustQuantity(sell, G.ADD_1000, 7), "sell: last add button sells everything owned")
        assertEquals(7, G.adjustQuantity(sell, G.ADD_100, 7), "sell never above what is owned")
        assertEquals(0, G.adjustQuantity(sell.copy(quantity = 0), G.DECREASE_QUANTITY, 7))
        assertEquals(G.MSG_CHOOSE_FIRST, G.validate(GeSelection(0, OfferType.BUY)))
        assertEquals(G.MSG_TOO_VALUABLE, G.validate(GeSelection(0, OfferType.BUY, itemId = 1, quantity = 3, price = Int.MAX_VALUE / 2, guide = Int.MAX_VALUE / 2)))
        assertNull(G.validate(sel))
    }

    @Test
    fun `six slots per player, abort and collect conserve escrow and free the slot`() {
        val service = newService()
        repeat(GrandExchangeService.SLOTS) { i ->
            val result = service.submit("a", OfferType.BUY, Items.ABYSSAL_WHIP, 100, 1)
            assertEquals(i, result!!.first.slot)
        }
        assertNull(service.submit("a", OfferType.BUY, Items.ABYSSAL_WHIP, 100, 1), "no seventh offer")
        assertNull(service.freeSlot("a"))
        assertNotNull(service.submit("b", OfferType.SELL, Items.ABYSSAL_WHIP, 90, 2, slot = 3), "slots are per player")

        // b's sell fills two of a's buys at the resting price (100): b is owed 200, a's first two offers complete.
        val aOffers = (0 until 6).map { service.offerInSlot("a", it)!! }
        val completed = aOffers.filter { it.status == OfferStatus.COMPLETED }
        assertEquals(2, completed.size)
        assertEquals(200L, service.offerInSlot("b", 3)!!.collectableCoins)

        val aborted = aOffers.first { it.status == OfferStatus.ACTIVE }
        service.cancel("a", aborted.id)
        assertEquals(100L, service.offerInSlot("a", aborted.slot)!!.collectableCoins, "abort escrows the unspent coins")
        assertFalse(service.releaseIfDrained("a", aborted.id), "slot held while coins are owed")
        assertEquals(100L, service.takePart("a", aborted.id, coins = true))
        assertTrue(service.releaseIfDrained("a", aborted.id))
        assertEquals(aborted.slot, service.freeSlot("a"))

        val done = completed.first()
        assertEquals(1L, service.takePart("a", done.id, coins = false))
        assertTrue(service.releaseIfDrained("a", done.id))
    }

    @Test
    fun `guide price comes from executed trades, otherwise the cache value`() {
        val service = newService()
        val whip = DEFINITIONS.get(ItemDef::class.java, Items.ABYSSAL_WHIP)
        assertEquals(whip.cost.coerceAtLeast(1), service.guidePrice(whip.id, whip.cost))
        service.submit("s", OfferType.SELL, whip.id, 1000, 1)
        service.submit("b", OfferType.BUY, whip.id, 1200, 1)
        assertEquals(1000, service.guidePrice(whip.id, whip.cost))
    }

    @Test
    fun `collect note rule and exchangeability over the item roster`() {
        val G = GrandExchangeInterface
        val lobster = DEFINITIONS.get(ItemDef::class.java, Items.LOBSTER)
        assertTrue(lobster.noteLinkId > 0)
        assertEquals(lobster.noteLinkId, G.collectedId(lobster, 5, op1 = true))
        assertEquals(lobster.id, G.collectedId(lobster, 5, op1 = false))
        assertEquals(lobster.id, G.collectedId(lobster, 1, op1 = true))
        assertEquals(lobster.noteLinkId, G.collectedId(lobster, 1, op1 = false))
        val coins = DEFINITIONS.get(ItemDef::class.java, Items.COINS_995)
        assertFalse(G.exchangeable(coins))
        // Every noted item maps back to an unnoted, never-noted item.
        val count = DEFINITIONS.getCount(ItemDef::class.java)
        val bad =
            (0 until count).mapNotNull { id ->
                val def = DEFINITIONS.getNullable(ItemDef::class.java, id) ?: return@mapNotNull null
                if (!def.noted) return@mapNotNull null
                val real = DEFINITIONS.getNullable(ItemDef::class.java, G.unnoted(def))
                if (real == null || real.noted) "noted $id -> ${G.unnoted(def)}" else null
            }
        assertTrue(bad.isEmpty(), bad.take(20).joinToString())
    }

    @Test
    fun `failed sell rollback preserves unnoted and noted inventory forms`() {
        val lobster = DEFINITIONS.get(ItemDef::class.java, Items.LOBSTER)
        assertEquals(
            listOf(Items.LOBSTER to 2, lobster.noteLinkId to 3),
            GrandExchangeInterface.restoredSellItems(lobster, unnotedRemoved = 2, notedRemoved = 3),
        )
        assertEquals(
            listOf(lobster.noteLinkId to 3),
            GrandExchangeInterface.restoredSellItems(lobster, unnotedRemoved = 0, notedRemoved = 3),
        )
    }

    @Test
    fun `grand exchange clerks expose the options the plugins bind`() {
        val clerks = listOf(Npcs.GRAND_EXCHANGE_CLERK, Npcs.GRAND_EXCHANGE_CLERK_2240, Npcs.GRAND_EXCHANGE_CLERK_2241, Npcs.GRAND_EXCHANGE_CLERK_2593)
        val report = clerks.map { id -> "$id=${DEFINITIONS.get(NpcDef::class.java, id).options.toList()}" }
        println("GE_CLERK_OPTIONS " + report.joinToString(" "))
        clerks.forEach { id ->
            val options = DEFINITIONS.get(NpcDef::class.java, id).options.filterNotNull().map { it.lowercase() }
            assertTrue("sets" in options, "clerk $id lacks Sets: $options")
        }
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var LIBRARY: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            val path = Paths.get("..", "..", "data", "cache").toFile().toString()
            LIBRARY = CacheLibrary(path)
            DEFINITIONS.loadAll(LIBRARY)
            var id = -1
            File(Paths.get("..", "..", "data", "cfg", "items.yml").toFile().path).forEachLine { line ->
                when {
                    line.startsWith("- id: ") -> id = line.removePrefix("- id: ").trim().toInt()
                    line.startsWith("  tradeable: ") && id >= 0 ->
                        DEFINITIONS.getNullable(ItemDef::class.java, id)?.tradeable = line.removePrefix("  tradeable: ").trim() == "true"
                }
            }
        }

        @AfterClass
        @JvmStatic
        fun close() {
            LIBRARY.close()
        }
    }
}
