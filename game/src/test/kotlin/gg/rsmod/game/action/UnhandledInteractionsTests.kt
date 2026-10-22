package gg.rsmod.game.action

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnhandledInteractionsTests {
    private val tmp = java.io.File.createTempFile("unhandled-test", ".tsv").also { it.delete() }
    private val realFile = UnhandledInteractions.file

    // Every test works on its own temp file (the registry now reads its file back - N9), never on the repo's logs/.
    @kotlin.test.BeforeTest
    fun useTempFile() {
        UnhandledInteractions.file = { tmp }
        UnhandledInteractions.reset()
    }

    @kotlin.test.AfterTest
    fun restoreFile() {
        UnhandledInteractions.file = realFile
        UnhandledInteractions.reset()
        tmp.delete()
    }

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

    @Test
    fun `N9 - a restart reads the file back, merges old duplicates and keeps one row with a count`() {
        val tmp = java.io.File.createTempFile("unhandled", ".tsv")
        val old = UnhandledInteractions.file
        try {
            // An older build's file: no count column, the same button three times.
            val tab = "\t"
            val header = listOf("kind", "id", "transform", "name", "opt", "option", "type", "rot", "x", "z", "height").joinToString(tab)
            val row = listOf("button", "5", "5", "button", "0", "interface=679 opcode=81", "0", "0", "0", "0", "0").joinToString(tab)
            tmp.writeText(header + "\n" + (row + "\n").repeat(3))
            UnhandledInteractions.file = { tmp }
            UnhandledInteractions.reset()
            assertFalse(UnhandledInteractions.recordInteraction("button", 5, 0, "button", "interface=679 opcode=81"), "known row: no new line")
            UnhandledInteractions.reset() // the next "restart" reads what was written
            assertEquals(1, UnhandledInteractions.size())
            val lines = tmp.readLines()
            assertEquals(2, lines.size, "header + one row: $lines")
            assertTrue(lines[0].endsWith(tab + "count"))
            assertTrue(lines[1].endsWith(tab + "4"), "3 old + 1 new click: ${lines[1]}")
        } finally {
            UnhandledInteractions.file = old
            UnhandledInteractions.reset()
            tmp.delete()
        }
    }
}
