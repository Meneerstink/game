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
}
