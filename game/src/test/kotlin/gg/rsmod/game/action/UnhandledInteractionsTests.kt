package gg.rsmod.game.action

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnhandledInteractionsTests {
    @Test
    fun `a repeated unhandled click raises its count instead of adding a row`() {
        val key = UnhandledInteractions.Key(id = 999_001, transform = 999_001, option = 1, x = 3200, z = 3200, height = 0)
        val before = UnhandledInteractions.size()
        assertTrue(UnhandledInteractions.record(key, "Test door", "Open", 0, 0))
        assertFalse(UnhandledInteractions.record(key, "Test door", "Open", 0, 0))
        assertEquals(2, UnhandledInteractions.count(key))
        assertEquals(before + 1, UnhandledInteractions.size())
    }

    @Test
    fun `non-object interaction census also deduplicates by context`() {
        val before = UnhandledInteractions.size()
        assertTrue(UnhandledInteractions.recordInteraction("button", 999_002, 3, "Test button", "interface=548 opcode=61"))
        assertFalse(UnhandledInteractions.recordInteraction("button", 999_002, 3, "Test button", "interface=548 opcode=61"))
        assertEquals(before + 1, UnhandledInteractions.size())
    }
}
