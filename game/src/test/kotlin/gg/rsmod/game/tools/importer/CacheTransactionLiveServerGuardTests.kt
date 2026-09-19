package gg.rsmod.game.tools.importer

import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Owner report 2026-09-19 ("why every time my fileserver hangs"): a cache transaction applied while the servers run makes
 * the file-server serve a cache the client can no longer load. [CacheTransaction.apply] must refuse while port 50015 or
 * 50016 is listening, and only for the two live caches.
 */
class CacheTransactionLiveServerGuardTests {
    private fun transaction(target: String) = CacheTransaction(targets = listOf(target), mutations = emptyList())

    @Test
    fun `a live server port refuses a write to the live caches`() {
        val port = CacheTransaction.LIVE_PORTS.first()
        val listener =
            try {
                ServerSocket(port)
            } catch (_: Exception) {
                // The real server (or another agent's test) already holds the port: the guard is then proven live anyway.
                null
            }
        try {
            val failure =
                assertFailsWith<IllegalStateException> {
                    transaction("C:\\RSPS\\game\\game\\data\\cache").apply(emptyList())
                }
            assertTrue("the server is running" in failure.message!!, failure.message!!)
            assertTrue("50017" in failure.message!!, "the message names the shutdown command port")
        } finally {
            listener?.close()
        }
    }

    @Test
    fun `a temp cache is never guarded`() {
        val temp = createTempDir(prefix = "cache-guard-test").also { it.deleteOnExit() }
        // No mutations and a path outside the two live caches: apply runs through without touching a port.
        val result = transaction(temp.absolutePath).apply(emptyList())
        assertTrue(result.applied == 0 && result.skipped == 0, "nothing to write, nothing refused")
    }

    @Test
    fun `both live cache paths and both ports are guarded`() {
        assertTrue(CacheTransaction.LIVE_PORTS.containsAll(listOf(50015, 50016)))
        assertTrue(CacheTransaction.LIVE_CACHE_PATHS.any { it.endsWith("game/game/data/cache") })
        assertTrue(CacheTransaction.LIVE_CACHE_PATHS.any { it.endsWith("file-server/cache") })
    }
}
