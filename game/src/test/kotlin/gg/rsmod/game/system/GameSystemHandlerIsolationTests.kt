package gg.rsmod.game.system

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** M0 interaction stability: one packet-handler failure must not abort the remaining message cycle. */
class GameSystemHandlerIsolationTests {
    @Test
    fun `handler exceptions are isolated to the current packet`() {
        val source =
            File("src/main/kotlin/gg/rsmod/game/system/GameSystem.kt").readText()
        val handleMessages = source.substringAfter("fun handleMessages()")

        // Audit T-01: every non-fatal Throwable (a handler's TODO()), not only Exception.
        assertTrue(
            "catch (e: Throwable)" in handleMessages && "rethrowIfFatal()" in handleMessages,
            "message handling must catch packet-handler exceptions per message",
        )
        assertTrue(
            "logger.error" in handleMessages,
            "packet-handler exceptions must remain observable in the server log",
        )
        assertTrue(
            handleMessages.indexOf("catch (e: Throwable)") < handleMessages.indexOf("finally"),
            "the per-message catch must wrap the handler before timing cleanup",
        )
    }
}
