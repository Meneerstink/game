package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.service.BackgroundIo
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Audit T-11: in the running server the Grand Exchange book is written on the background IO thread
 * instead of rewriting its files synchronously on the game thread for every offer mutation. The data is
 * snapshotted when it changes and written in order, so a restart still reads the latest book.
 */
class GrandExchangeBackgroundWriteTests {
    @Test
    fun `background writes keep every mutation and a restart reads the latest book`() {
        val dir = Files.createTempDirectory("ge-async").toFile()
        val file = File(dir, "offers.json")
        val first = GrandExchangeService(file).also { it.asyncWrites = true }
        first.submit("alice", OfferType.BUY, Items.ABYSSAL_WHIP, 100, 2)
        val offer = assertNotNull(first.offerInSlot("alice", 0))
        first.cancel("alice", offer.id)

        val restarted = GrandExchangeService(file)
        restarted.load() // waits for the queued writes
        val restored = assertNotNull(restarted.offerInSlot("alice", 0))
        val latest = assertNotNull(first.offerInSlot("alice", 0))
        assertEquals(latest.status, restored.status, "the last snapshot is the one on disk")
        assertEquals(latest.collectableCoins, restored.collectableCoins)
    }

    @Test
    fun `a service built outside the server writes synchronously`() {
        val dir = Files.createTempDirectory("ge-sync").toFile()
        val file = File(dir, "offers.json")
        GrandExchangeService(file).submit("bob", OfferType.BUY, Items.LOBSTER, 5, 1)
        assertTrue(file.isFile, "tests and tools see the file immediately")
        assertTrue(BackgroundIo.flush())
    }
}
