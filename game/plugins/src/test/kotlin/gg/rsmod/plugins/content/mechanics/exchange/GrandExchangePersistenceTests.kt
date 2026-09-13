package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * RCV-010 C3 deepening: offers, their slots, escrow and the guide-price history survive a server restart (a new
 * GrandExchangeService loading the same files), and the restored book keeps enforcing the 6-slot limit.
 */
class GrandExchangePersistenceTests {
    @Test
    fun `offers slots escrow and guide prices survive a restart`() {
        val dir = Files.createTempDirectory("ge-persist").toFile()
        val file = File(dir, "offers.json")
        val first = GrandExchangeService(file)
        repeat(GrandExchangeService.SLOTS) { first.submit("alice", OfferType.BUY, Items.ABYSSAL_WHIP, 100, 2) }
        first.submit("bob", OfferType.SELL, Items.ABYSSAL_WHIP, 90, 3, slot = 4)
        first.cancel("alice", first.offerInSlot("alice", 5)!!.id)
        val before = (0 until GrandExchangeService.SLOTS).map { first.offerInSlot("alice", it) }
        val bobBefore = first.offerInSlot("bob", 4)!!
        val guideBefore = first.guidePrice(Items.ABYSSAL_WHIP, 1)

        val restarted = GrandExchangeService(file)
        restarted.load()
        (0 until GrandExchangeService.SLOTS).forEach { slot ->
            val a = before[slot]!!
            val b = assertNotNull(restarted.offerInSlot("alice", slot), "slot $slot lost on restart")
            assertEquals(listOf(a.id, a.type, a.itemId, a.pricePerItem, a.totalQuantity, a.quantityFilled, a.status, a.collectableCoins, a.collectableItems),
                listOf(b.id, b.type, b.itemId, b.pricePerItem, b.totalQuantity, b.quantityFilled, b.status, b.collectableCoins, b.collectableItems), "slot $slot")
        }
        val bobAfter = assertNotNull(restarted.offerInSlot("bob", 4))
        assertEquals(bobBefore.collectableCoins, bobAfter.collectableCoins)
        assertEquals(guideBefore, restarted.guidePrice(Items.ABYSSAL_WHIP, 1), "guide price history persisted")
        assertNull(restarted.submit("alice", OfferType.BUY, Items.ABYSSAL_WHIP, 100, 1), "6-slot limit still enforced after restart")
        val next = assertNotNull(restarted.submit("carol", OfferType.BUY, Items.LOBSTER, 5, 1))
        assertEquals(true, next.first.id > before.maxOf { it!!.id } && next.first.id > bobBefore.id, "ids keep increasing after restart")
    }
}
