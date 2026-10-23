package gg.rsmod.game.tools.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner 2026-10-11: the OSRS Keybinding window (interface 121) rebuilt as 667 interface 1181. */
class KeybindingInterfaceImportToolTests {
    private val fonts = LootKeyInterfaceImportTool.Fonts(p11 = 1, p12 = 2, b12 = 3, q8 = 4)

    @Test
    fun `components are contiguous and encode`() {
        val components = KeybindingInterfaceImportTool.build(fonts)
        assertEquals((0 until KeybindingInterfaceImportTool.COUNT).toList(), components.map { it.id })
        components.forEach { assertTrue(DeathsOfficeInterfaceImportTool.encode(it).isNotEmpty()) }
        KeybindingInterfaceImportTool.settingsButton(fonts).forEach { assertTrue(DeathsOfficeInterfaceImportTool.encode(it).isNotEmpty()) }
    }

    @Test
    fun `fourteen rows in three OSRS columns, all inside the content area`() {
        val tool = KeybindingInterfaceImportTool
        assertEquals(14, tool.ROWS.size)
        tool.ROWS.indices.forEach { i ->
            assertTrue(tool.rowX(i) + tool.ROW_W <= tool.CONTENT_W, "row $i x")
            assertTrue(tool.rowY(i) + tool.ROW_H <= tool.CONTENT_H, "row $i y")
        }
        assertEquals(listOf(20, 180, 340), tool.ROWS.indices.map { tool.rowX(it) }.distinct())
        assertTrue(tool.LIST_H <= tool.CONTENT_H, "the dropdown list fits the window")
    }
    @Test
    fun `N208 - the Settings tab button is the OSRS F1 key icon on a 667 stone, in both caches`() {
        val tool = KeybindingInterfaceImportTool
        for (cache in OsrsItemImportTool.TARGETS) {
            val library = com.displee.cache.CacheLibrary(cache)
            try {
                val button = library.data(3, tool.SETTINGS_INTERFACE, tool.SETTINGS_BUTTON)!!
                assertEquals(listOf(75, 86, 40, 40), listOf(GeHistoryInterfaceImportTool.x(button), GeHistoryInterfaceImportTool.y(button), GeHistoryInterfaceImportTool.width(button), GeHistoryInterfaceImportTool.height(button)), cache)
                val stone = library.data(3, tool.SETTINGS_INTERFACE, tool.SETTINGS_LABEL)!!
                val icon = library.data(3, tool.SETTINGS_INTERFACE, tool.SETTINGS_ICON)!!
                assertEquals(5, GeHistoryInterfaceImportTool.typeOf(stone), "$cache: no text label any more")
                assertEquals(5, GeHistoryInterfaceImportTool.typeOf(icon))
                val graphic = { b: ByteArray -> java.nio.ByteBuffer.wrap(b, 19, 4).int }
                assertEquals(tool.SETTINGS_STONE, graphic(stone))
                assertEquals(tool.sprite(1161), graphic(icon))
                assertTrue(library.data(8, tool.sprite(1161), 0) != null, "$cache: F1 key sprite imported")
            } finally {
                library.close()
            }
        }
    }
}
