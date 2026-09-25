package gg.rsmod.game.system

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Audit T-15: the packet buffer holds several cycles of input; at most `messages-per-cycle` packets are
 * handled per cycle and the rest wait for the next cycle instead of being dropped.
 */
class GameSystemPacketBufferTests {
    @Test
    fun `the buffer holds several cycles of packets`() {
        assertEquals(30 * GameSystem.PACKET_BUFFER_CYCLES, GameSystem.bufferCapacity(30))
        assertTrue(GameSystem.bufferCapacity(30) >= 40)
        assertEquals(GameSystem.PACKET_BUFFER_CYCLES, GameSystem.bufferCapacity(1))
    }

    @Test
    fun `40 packets are handled as 30 in the first cycle and 10 in the next`() {
        // The same bounded offer / per-cycle poll as GameSystem.receiveMessage and handleMessages.
        val perCycle = 30
        val queue = ArrayBlockingQueue<Int>(GameSystem.bufferCapacity(perCycle))
        val accepted = (0 until 40).count { queue.offer(it) }
        assertEquals(40, accepted, "no packet of the burst may be dropped")

        fun cycle(): List<Int> {
            val handled = mutableListOf<Int>()
            for (i in 0 until perCycle) {
                handled += queue.poll() ?: break
            }
            return handled
        }
        assertEquals((0 until 30).toList(), cycle())
        assertEquals((30 until 40).toList(), cycle())
        assertEquals(emptyList<Int>(), cycle())
    }

    @Test
    fun `the receive path sizes the buffer by bufferCapacity and handles at most messages-per-cycle`() {
        val source = Files.readString(Path.of("src", "main", "kotlin", "gg", "rsmod", "game", "system", "GameSystem.kt"))
        assertTrue("ArrayBlockingQueue<MessageHandle>(bufferCapacity(service.maxMessagesPerCycle))" in source)
        assertTrue("for (i in 0 until service.maxMessagesPerCycle)" in source)
    }
}
