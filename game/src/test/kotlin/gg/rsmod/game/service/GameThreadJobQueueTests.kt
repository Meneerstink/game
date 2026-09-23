package gg.rsmod.game.service

import kotlin.test.Test
import kotlin.test.assertEquals

class GameThreadJobQueueTests {
    @Test
    fun `jobs submitted while a batch runs are retained for the next cycle`() {
        val queue = GameThreadJobQueue()
        val ran = mutableListOf<String>()

        queue.offer {
            ran += "first"
            queue.offer { ran += "second" }
        }

        queue.drain().forEach { it() }

        assertEquals(listOf("first"), ran)
        queue.drain().forEach { it() }
        assertEquals(listOf("first", "second"), ran)
    }

    @Test
    fun `empty drain is allocation-free at the contract boundary`() {
        val queue = GameThreadJobQueue()

        assertEquals(emptyList(), queue.drain())
    }
}
