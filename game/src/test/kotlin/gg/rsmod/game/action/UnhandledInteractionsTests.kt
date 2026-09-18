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
}
