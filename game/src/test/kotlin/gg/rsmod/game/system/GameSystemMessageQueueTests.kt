package gg.rsmod.game.system

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** M0 interaction-stability contract: packet bursts never throw from the Netty receive path. */
class GameSystemMessageQueueTests {
    @Test
    fun `bounded packet queue offers and traces overflow instead of using throwing add`() {
        val source =
            Files.readString(
                Path.of(
                    "src",
                    "main",
                    "kotlin",
                    "gg",
                    "rsmod",
                    "game",
                    "system",
                    "GameSystem.kt",
                ),
            )

        assertTrue("messages.offer(" in source)
        assertFalse("messages.add(" in source)
        assertTrue("packet queue full" in source)
        assertTrue("AvTrace.log" in source)
    }

    @Test
    fun `a packet burst accepts the bounded batch and drops only overflow`() {
        val queue = ArrayBlockingQueue<Int>(30)
        val accepted = (0 until 31).count { queue.offer(it) }

        assertEquals(30, accepted)
        assertEquals(30, queue.size)

        val handled = generateSequence { queue.poll() }.toList()
        assertEquals(30, handled.size)
        assertEquals((0 until 30).toList(), handled)
        assertTrue(queue.isEmpty())
    }
}
