package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.plugins.content.items.books.Book
import kotlin.test.*

class ToaLoreBooksTests {
    @Test fun `six books, each with its wiki transcript, fit the book interface`() {
        assertEquals(listOf(27302, 27304, 27306, 27308, 27310, 27312), ToaLoreBooks.ALL.map { it.osrs })
        ToaLoreBooks.ALL.forEach { b ->
            assertTrue(b.sourceUrl.startsWith("https://oldschool.runescape.wiki/w/Transcript:") && "oldid=" in b.sourceUrl, b.title)
            assertFalse("{{" in b.text || "[[" in b.text, b.title)
            assertTrue(Book.fromText(b.title, b.text).pages.isNotEmpty(), b.title)
        }
        assertEquals(setOf(ToaPath.HET, ToaPath.APMEKEN, ToaPath.SCABARAS, ToaPath.CRONDIS), ToaLoreBooks.CAPTURE.keys)
    }
}
