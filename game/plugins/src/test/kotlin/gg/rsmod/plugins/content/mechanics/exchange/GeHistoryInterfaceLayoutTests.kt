package gg.rsmod.plugins.content.mechanics.exchange

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.GeHistoryInterfaceImportTool
import gg.rsmod.game.tools.importer.InterfaceHookProbeTool
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E6 (owner 2026-10-04): the GE Trade History showed only the newest 5 of the 20 kept trades because the 667 screen 643 has
 * five fixed rows. [GeHistoryInterfaceImportTool] rebuilds them as a 20-row scroll list; these tests build it from the real
 * cache (the 667 layout, or the tool's own once applied) and check every row, its ops carrier and the scrollbar.
 */
class GeHistoryInterfaceLayoutTests {
    private val tool = GeHistoryInterfaceImportTool

    private fun current(): Map<Int, ByteArray> {
        val ids = LIBRARY.index(tool.INDEX_INTERFACES).archive(tool.INTERFACE_ID)!!.fileIds().toList()
        return ids.associateWith { LIBRARY.data(tool.INDEX_INTERFACES, tool.INTERFACE_ID, it)!! }
    }

    @Test
    fun `the cache holds the 667 five-row screen or the tool's list`() {
        val current = current()
        assertTrue(current.size == tool.LEGACY_COMPONENT_COUNT || current.size == tool.COMPONENT_COUNT, "643 has ${current.size} components")
        // The templates the tool copies: row-0 texts (25 / 30 / 35 / 40) and the separator line 45 (sprite 1074).
        listOf(25, 30, 35, 40, tool.HEADER_QUANTITY, tool.HEADER_PRICE).forEach { id ->
            assertTrue(tool.isLegacyUnnamed(current.getValue(id)) && tool.typeOf(current.getValue(id)) == 4, "643:$id is a legacy text")
        }
        assertEquals(1074, InterfaceHookProbeTool.componentSprite(current.getValue(tool.LEGACY_SEPARATOR_FIRST)))
    }

    @Test
    fun `twenty rows sit in one scroll layer with their own ops carrier`() {
        val current = current()
        val built = tool.build(current)
        val merged = current + built
        assertEquals((0 until tool.COMPONENT_COUNT).toList(), merged.keys.sorted(), "component ids 0..${tool.COMPONENT_COUNT - 1} without gaps")
        (0..22).forEach { assertTrue(it !in built, "643:$it (frame, title, headers) is not touched") }

        val list = built.getValue(tool.LIST)
        assertEquals(listOf(0, tool.LIST_X, tool.LIST_Y, tool.LIST_WIDTH, tool.LIST_HEIGHT, -1), listOf(tool.typeOf(list), tool.x(list), tool.y(list), tool.width(list), tool.height(list), tool.parent(list)))
        val scrollbar = built.getValue(tool.SCROLLBAR)
        assertEquals(listOf(tool.LIST_X + tool.LIST_WIDTH, tool.LIST_Y, tool.SCROLLBAR_WIDTH, tool.LIST_HEIGHT, -1), listOf(tool.x(scrollbar), tool.y(scrollbar), tool.width(scrollbar), tool.height(scrollbar), tool.parent(scrollbar)))
        assertEquals(listOf<Any>(30, tool.EVENT_COM, (tool.INTERFACE_ID shl 16) or tool.LIST), InterfaceHookProbeTool.componentHooks(scrollbar)["onLoad"], "667 scrollbar script 30 on load")

        // Inside the window: below the header bar line (643:15 at y 84, line 13..15), above the bottom border (643:6), left of the right border.
        assertTrue(tool.LIST_Y >= tool.y(current.getValue(15)) + 16)
        assertTrue(tool.LIST_Y + tool.LIST_HEIGHT <= tool.y(current.getValue(6)))
        assertTrue(tool.LIST_X + tool.LIST_WIDTH + tool.SCROLLBAR_WIDTH <= 493)

        val templates: List<Pair<(Int) -> Int, Int>> =
            listOf(
                { r: Int -> tool.type(r) } to 25,
                { r: Int -> tool.name(r) } to 35,
                { r: Int -> tool.amount(r) } to 30,
                { r: Int -> tool.price(r) } to 40,
            )
        for (row in 0 until tool.ROWS) {
            val top = tool.rowTop(row)
            listOf(tool.type(row), tool.name(row), tool.amount(row), tool.price(row), tool.separator(row), tool.hit(row)).forEach { id ->
                val bytes = built.getValue(id)
                assertEquals(tool.LIST, tool.parent(bytes), "643:$id (row $row) is inside the list")
                assertTrue(tool.x(bytes) >= 0 && tool.x(bytes) + tool.width(bytes) <= tool.LIST_WIDTH, "643:$id (row $row) inside the list width")
            }
            templates.forEach { (idOf, template) ->
                val bytes = built.getValue(idOf(row))
                assertEquals(4, tool.typeOf(bytes))
                assertTrue(tool.y(bytes) >= top && tool.y(bytes) + tool.height(bytes) <= top + tool.ROW_HEIGHT, "643:${idOf(row)} inside row $row")
                val tail = current.getValue(template).copyOfRange(tool.HEADER_BYTES, current.getValue(template).size)
                assertTrue(tail.contentEquals(bytes.copyOfRange(tool.HEADER_BYTES, bytes.size)), "643:${idOf(row)} keeps the 667 row-0 font, colour and alignment")
            }
            // The separator's line (sprite 1074 rows 16..17) is the row's last two pixels.
            val separator = built.getValue(tool.separator(row))
            assertEquals(top + tool.ROW_HEIGHT - 2, tool.y(separator) + 16)
            // The hit layers tile the list: full width, one row each, no gaps.
            val hit = built.getValue(tool.hit(row))
            assertEquals(listOf(0, 0, top, tool.LIST_WIDTH, tool.ROW_HEIGHT), listOf(tool.typeOf(hit), tool.x(hit), tool.y(hit), tool.width(hit), tool.height(hit)))
        }
        // Columns do not overlap; the moved headers stay centred over their columns.
        val columns = listOf(tool.TYPE_COLUMN, tool.NAME_COLUMN, tool.AMOUNT_COLUMN, tool.PRICE_COLUMN)
        columns.zipWithNext().forEach { (a, b) -> assertTrue(a.x + a.width <= b.x, "columns overlap at ${b.x}") }
        listOf(tool.HEADER_QUANTITY to tool.AMOUNT_COLUMN, tool.HEADER_PRICE to tool.PRICE_COLUMN).forEach { (header, column) ->
            val bytes = built.getValue(header)
            assertEquals(tool.LIST_X + column.x + column.width / 2, tool.x(bytes) + tool.width(bytes) / 2, "643:$header centred over its column")
            assertEquals(-1, tool.parent(bytes))
        }
        // Every id the server addresses is a distinct component.
        val ids = (0 until tool.ROWS).flatMap { listOf(tool.type(it), tool.name(it), tool.amount(it), tool.price(it), tool.separator(it), tool.hit(it)) }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `a re-run rebuilds the same bytes`() {
        val built = tool.build(current())
        val again = tool.build(current() + built)
        assertEquals(built.keys, again.keys)
        built.forEach { (id, bytes) -> assertTrue(bytes.contentEquals(again.getValue(id)), "643:$id changes on a re-run") }
    }

    @Test
    fun `the list scrolls over the trades there are`() {
        assertEquals(tool.LIST_HEIGHT, tool.scrollHeight(0))
        assertEquals(tool.LIST_HEIGHT, tool.scrollHeight(tool.VISIBLE_ROWS))
        assertEquals(7 * tool.ROW_HEIGHT, tool.scrollHeight(7))
        assertEquals(tool.ROWS * tool.ROW_HEIGHT, tool.scrollHeight(25))
        assertEquals(GrandExchangeHistory.KEPT, tool.ROWS)
    }

    companion object {
        private lateinit var LIBRARY: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            LIBRARY = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        }

        @AfterClass
        @JvmStatic
        fun close() {
            LIBRARY.close()
        }
    }
}
