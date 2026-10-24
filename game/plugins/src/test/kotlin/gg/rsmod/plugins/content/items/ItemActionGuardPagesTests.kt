package gg.rsmod.plugins.content.items

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemActionGuardPagesTests {
    @Test
    fun `a long Inspect text is split over pages that fit the item box`() {
        // N347: the tier 1 Deadman emblem's Inspect lines, which ran over the box in one page.
        val lines =
            listOf(
                "<col=ef1020>Deadman emblem - tier 1 of 6.",
                "Worth 60 Deadman Points or a 25,000 XP lamp at the Emblem Trader.",
                "Next: a valid kill while you carry it makes it tier 2 (110 points). Your victim must risk at least 1M.",
                "It is always lost when you die to another player - Protect Item cannot save it.",
            )
        val pages = ItemActionGuard.itemBoxPages(lines)
        assertTrue(pages.size >= 2, "$pages")
        pages.forEach { page ->
            val rows = page.split("<br>")
            assertTrue(rows.size <= ItemActionGuard.ITEM_BOX_LINES, page)
            rows.forEach { assertTrue(it.replace(Regex("<[^>]+>"), "").length <= ItemActionGuard.ITEM_BOX_LINE_CHARS, it) }
        }
        val words = { s: String -> s.replace(Regex("<[^>]+>"), "").split(Regex("""\s+|<br>""")).filter { it.isNotEmpty() } }
        assertEquals(lines.flatMap(words), pages.flatMap { words(it.replace("<br>", " ")) })
    }

    @Test
    fun `a short text stays one page`() {
        assertEquals(listOf("Charges: 5"), ItemActionGuard.itemBoxPages(listOf("Charges: 5")))
    }
}
