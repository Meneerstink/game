package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.ShopPageInterfaceImportTool as Tool
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-10-06 (bug 204956 / OWNER_QUESTIONS Q2 = A): the shop window 620 gets the 78 Store's Prev page / Page n / m / Next page
 * row. The plan must append only to the decoded 667 window, stay hidden for every other shop, and sit where it covers nothing.
 */
class ShopPageInterfaceImportToolTests {
    private val fonts = LootKeyInterfaceImportTool.Fonts(p11 = 494, p12 = 495, b12 = 496, q8 = 497)
    private val built = Tool.components(fonts).associateBy { it.id }

    @Test
    fun `the row is appended after the 667 window, encodes and is hidden by default`() {
        assertEquals((Tool.EXISTING_COMPONENTS until Tool.COMPONENT_COUNT).toList(), built.keys.sorted())
        built.values.forEach { assertTrue(LootKeyInterfaceImportTool.encode(it).isNotEmpty()) }
        val nav = built.getValue(Tool.NAV)
        assertTrue(nav.hidden, "the row must be baked hidden so non-paged shops keep the 667 window")
        assertEquals(Tool.WINDOW, nav.parent)
        listOf(Tool.PAGE_PREVIOUS, Tool.PAGE_TEXT, Tool.PAGE_NEXT).forEach { id ->
            val c = built.getValue(id)
            assertEquals(Tool.NAV, c.parent)
            assertTrue(c.x >= 0 && c.y >= 0 && c.x + c.width <= nav.width && c.y + c.height <= nav.height, "$id outside the row")
        }
        assertTrue(nav.x >= 0 && nav.x + nav.width <= Tool.WINDOW_WIDTH && nav.y + nav.height <= Tool.WINDOW_HEIGHT)
    }

    @Test
    fun `the row looks like the 78 Store page row`() {
        val store = StoreInterfaceImportTool.components(fonts).associateBy { it.id }
        listOf(
            Tool.PAGE_PREVIOUS to StoreInterfaceImportTool.PAGE_PREVIOUS,
            Tool.PAGE_TEXT to StoreInterfaceImportTool.PAGE_TEXT,
            Tool.PAGE_NEXT to StoreInterfaceImportTool.PAGE_NEXT,
        ).forEach { (ours, theirs) ->
            val a = built.getValue(ours)
            val b = store.getValue(theirs)
            assertEquals(b.text, a.text, "text of $ours")
            assertEquals(b.ops, a.ops, "ops of $ours")
            assertEquals(b.colour, a.colour, "colour of $ours")
            assertEquals(b.font, a.font, "font of $ours")
            assertEquals(b.alignX, a.alignX, "alignment of $ours")
            assertEquals(b.shadow, a.shadow)
        }
    }

    /** Both caches still hold the window the coordinates were measured on (the tool refuses otherwise). */
    @Test
    fun `both caches hold the measured 667 shop window`() {
        listOf("../data/cache", "../../../file-server/cache").map { File(it) }.filter { it.isDirectory }.forEach { dir ->
            val cache = CacheLibrary(dir.path)
            try {
                val ids = cache.index(3).archive(Tool.INTERFACE_ID)!!.fileIds().toList()
                assertTrue(ids == (0 until Tool.EXISTING_COMPONENTS).toList() || ids == (0 until Tool.COMPONENT_COUNT).toList(), "$dir: $ids")
                val window = (0 until Tool.EXISTING_COMPONENTS).map { cache.data(3, Tool.INTERFACE_ID, it)!! }
                assertEquals(Tool.EXISTING_WINDOW_SHA1, Tool.sha1(window), "$dir: 620:0..34 changed")
            } finally {
                cache.close()
            }
        }
    }

    /**
     * The row covers nothing of the 667 window: not the drawn text of the title (20), sample/stock headings (21, 22), "Key" (23) and
     * help line (24), not the close button (18) or the general-store icon (19), and not the stock / samples / scrollbar layers (25, 26,
     * 32) at their highest position. Text extents come from the cache's own font metrics (index 13), as the client measures them.
     */
    @Test
    fun `the row overlaps no drawn part of the 667 shop window`() {
        val cache = CacheLibrary(File("../data/cache").path)
        try {
            fun width(font: Int, text: String): Int {
                val metrics = cache.data(13, font, 0)!!
                return text.sumOf { metrics[2 + (it.code and 0xFF)].toInt() and 0xFF }
            }
            val drawn = mutableMapOf<Int, Tool.Box>()
            for (id in listOf(18, 19, 20, 21, 22, 23, 24, 25, 26, 32)) {
                val bytes = cache.data(3, Tool.INTERFACE_ID, id)!!
                val header = Tool.decodeHeader(bytes)
                val box = Tool.windowBox(header)
                drawn[id] =
                    if (header.type == 4) {
                        val font = ((bytes[header.end].toInt() and 0xFF) shl 8) or (bytes[header.end + 1].toInt() and 0xFF)
                        var p = header.end + 2
                        val text = StringBuilder()
                        while (bytes[p].toInt() != 0) text.append((bytes[p++].toInt() and 0xFF).toChar())
                        val alignX = bytes[p + 2].toInt()
                        val tw = width(font, text.toString())
                        val tx = when (alignX) { 0 -> box.x; 1 -> box.x + (box.width - tw) / 2; else -> box.x + box.width - tw }
                        Tool.Box(tx, box.y, tw, box.height)
                    } else {
                        box
                    }
            }
            val nav = built.getValue(Tool.NAV).let { Tool.Box(it.x, it.y, it.width, it.height) }
            val hits = drawn.filterValues { it.intersects(nav) }
            assertEquals(emptyMap<Int, Tool.Box>(), hits, "the page row $nav overlaps")
            // The decoded 667 positions the tool's documentation names.
            assertEquals(Tool.Box(20, 24, 119, 17), drawn[21], "Free sample items")
            assertEquals(40, drawn.getValue(25).y, "stock area top")

            // Every label fits its box (p12 widths from the cache).
            listOf(Tool.PAGE_PREVIOUS to "Prev page", Tool.PAGE_NEXT to "Next page", Tool.PAGE_TEXT to "Page 10 / 10").forEach { (id, text) ->
                assertTrue(width(fonts.p12, text) <= built.getValue(id).width, "'$text' does not fit $id")
            }
        } finally {
            cache.close()
        }
    }
}
