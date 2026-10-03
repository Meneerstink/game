package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E6 (owner 2026-10-03, "Grand Exchange exactly like OSRS"): bank coins pay for buy offers, the buy-limit window survives
 * a restart, held buy offers carry on after the window resets, and the screen routes keep their guards.
 */
class GrandExchangeOsrsParityTests {
    private fun withLimits(
        table: Map<Int, Int>,
        block: () -> Unit,
    ) {
        val saved = GeBuyLimits.table()
        try {
            GeBuyLimits.set(table)
            block()
        } finally {
            GeBuyLimits.set(saved)
        }
    }

    @Test
    fun `buy offers take inventory coins first, then the bank`() {
        val G = GrandExchangeInterface
        assertEquals(1_000 to 0, G.coinSplit(1_000, 5_000, 0))
        assertEquals(300 to 700, G.coinSplit(1_000, 300, 10_000), "the rest comes from the bank")
        assertEquals(0 to 1_000, G.coinSplit(1_000, 0, 1_000), "bank alone may pay")
        assertNull(G.coinSplit(1_000, 300, 699), "inventory and bank together are short")
        assertNull(G.coinSplit(0, 10, 10))
        assertEquals(Int.MAX_VALUE to 0, G.coinSplit(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE), "no Int overflow")
        val (inv, bank) = G.coinSplit(Int.MAX_VALUE, Int.MAX_VALUE - 5, Int.MAX_VALUE)!!
        assertEquals(Int.MAX_VALUE.toLong(), inv.toLong() + bank, "the split always adds up to the cost")
    }

    @Test
    fun `the buy-limit window survives a restart and a held buy offer resumes once it resets`() =
        withLimits(mapOf(Items.FEATHER to 5)) {
            var now = 10_000_000L
            val file = File(Files.createTempDirectory("ge").toFile(), "offers.json")
            val first = GrandExchangeService(file) { now }
            // The house sells feathers (whitelist quote, 2 gp); the buy limit holds the offer at 5 of 10.
            val (offer, fills) = first.submit("buyer", OfferType.BUY, Items.FEATHER, 2, 10)!!
            assertEquals(5, fills.sumOf { it.quantity })
            assertEquals(OfferStatus.ACTIVE, first.offer(offer.id)!!.status)
            assertEquals(0, first.buyAllowance("buyer", Items.FEATHER))

            // Restart: the ledger is read back, the limit is still used up.
            val restarted = GrandExchangeService(file) { now }
            restarted.load()
            assertEquals(0, restarted.buyAllowance("buyer", Items.FEATHER), "a restart must not reset the buy limit")
            assertTrue(restarted.resumeHeldOffers().isEmpty(), "nothing resumes inside the window")

            now += GeBuyLimits.WINDOW_MS - 1
            assertTrue(restarted.resumeHeldOffers().isEmpty(), "still one millisecond inside the window")
            now += 1
            assertTrue(restarted.resumeHeldOffers("someone else").isEmpty(), "a per-player pass leaves other offers alone")
            val resumed = restarted.resumeHeldOffers("buyer")
            assertEquals(5, resumed.sumOf { it.quantity }, "the held half fills once the 4-hour window has reset")
            val done = restarted.offer(offer.id)!!
            assertEquals(OfferStatus.COMPLETED, done.status)
            assertEquals(10, done.collectableItems)
            assertEquals(20L, done.coinsTraded)
            assertTrue(restarted.resumeHeldOffers().isEmpty(), "a completed offer never fills twice")
        }

    @Test
    fun `an expired ledger row is not written back`() =
        withLimits(mapOf(Items.FEATHER to 5)) {
            var now = 0L
            val ledger = GeBuyLedger { now }
            ledger.record("a", Items.FEATHER, 3)
            assertEquals(listOf(GeBuyRecord("a", Items.FEATHER, 3, 0L)), ledger.snapshot())
            now = GeBuyLimits.WINDOW_MS
            assertTrue(ledger.snapshot().isEmpty())
            val restored = GeBuyLedger { 1L }
            restored.restore(listOf(GeBuyRecord("a", Items.FEATHER, 4, 0L), GeBuyRecord("", Items.FEATHER, 4, 0L)))
            assertEquals(1, restored.remaining("a", Items.FEATHER))
            assertEquals(1, restored.snapshot().size, "a nameless row is dropped")
        }

    @Test
    fun `screen routes keep the bank PIN, placement guards and the return to the offer boxes`() {
        val iface = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/exchange/GrandExchangeInterface.kt").readText()
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/exchange/grand_exchange_interface.plugin.kts").readText()
        listOf("fun openMain(", "fun openCollectionBox(").forEach { head ->
            val body = iface.substringAfter(head).substringBefore("\n    fun ")
            assertTrue("BankPin.required(player)" in body, "$head must ask for the bank PIN first")
            assertTrue("resumeHeld(player, service)" in body, "$head must resume held offers")
        }
        val confirm = iface.substringAfter("fun confirm(").substringBefore("\n    }")
        assertTrue("placementRefused(player)" in confirm, "confirm must check the escrow guards")
        val guards = iface.substringAfter("fun placementRefused(").substringBefore("\n    }\n")
        listOf("RemoteAccess.refusal", "BankSecurity.denyBank", "PracticePvp.isHoldingTempGear").forEach {
            assertTrue(it in guards, "placementRefused lacks $it")
        }
        assertTrue("returnToIndex = true" in plugin, "collecting the last item on the offer screen returns to the offer boxes")
        assertTrue("service.resumeHeldOffers()" in plugin, "the world timer resumes held offers of offline owners")
    }
}
