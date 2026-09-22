package gg.rsmod.game.tools.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreInterfaceImportToolTests {
    private val fonts = LootKeyInterfaceImportTool.Fonts(p11 = 1, p12 = 2, b12 = 3, q8 = 4)

    @Test
    fun `components are contiguous and encode`() {
        val components = StoreInterfaceImportTool.components(fonts)
        assertEquals((0 until StoreInterfaceImportTool.COMPONENT_COUNT).toList(), components.map { it.id })
        components.forEach { assertTrue(LootKeyInterfaceImportTool.encode(it).isNotEmpty()) }
    }

    @Test
    fun `every window child stays inside the window and the window fits the fixed main screen`() {
        assertTrue(StoreInterfaceImportTool.WIDTH <= 512 && StoreInterfaceImportTool.HEIGHT <= 334)
        val offenders =
            StoreInterfaceImportTool.components(fonts)
                .filter { it.parent == StoreInterfaceImportTool.WINDOW && it.resizeX == 0 && it.id > 10 }
                .filter { it.x < 0 || it.y < 0 || it.x + it.width > StoreInterfaceImportTool.WIDTH || it.y + it.height > StoreInterfaceImportTool.HEIGHT }
                .map { "${it.id} (${it.x},${it.y} ${it.width}x${it.height})" }
        assertEquals(emptyList<String>(), offenders)
    }

    /**
     * Owner screenshot 2026-09-19 ("shop ui.png"): at 5x the Dragon boots (g) were cut off. The client scales the model by
     * width * 512 / aspect (InterfaceManager model component), so the whole 36 x 32 inventory drawing must fit the preview box,
     * the enlargement must stay above 3x, and both axes must scale alike (no stretched items).
     */
    @Test
    fun `the preview shows the whole enlarged item without stretching it`() {
        val model = StoreInterfaceImportTool.components(fonts).single { it.id == StoreInterfaceImportTool.PREVIEW_MODEL }
        assertEquals(LootKeyInterfaceImportTool.TYPE_MODEL, model.type)
        assertEquals(1, model.resizeX)
        val scaleX = StoreInterfaceImportTool.PREVIEW_WIDTH.toDouble() / StoreInterfaceImportTool.PREVIEW_ASPECT_X
        val scaleY = StoreInterfaceImportTool.PREVIEW_HEIGHT.toDouble() / StoreInterfaceImportTool.PREVIEW_ASPECT_Y
        // Owner 2026-09-22: the client fits every item to this box (InterfaceManager.fitModelToBox, interface 1151), so the
        // cached scale is only the fallback; it must still fit a long weapon's ~48-unit diagonal inside the panel.
        assertTrue(48 * scaleY <= StoreInterfaceImportTool.PREVIEW_HEIGHT, "fallback scale crops long items: ${scaleY}x")
        assertTrue(36 * scaleX <= StoreInterfaceImportTool.PREVIEW_WIDTH && 32 * scaleY <= StoreInterfaceImportTool.PREVIEW_HEIGHT, "item cropped")
        assertTrue(kotlin.math.abs(scaleX - scaleY) < 0.1, "stretched: ${scaleX}x vs ${scaleY}x")
    }

    @Test
    fun `every shop has its own theme banner and outlines and only the first shop's are visible by default`() {
        val byId = StoreInterfaceImportTool.components(fonts).associateBy { it.id }
        for (i in 0 until StoreInterfaceImportTool.TAB_COUNT) {
            listOf(StoreInterfaceImportTool.BANNER_FIRST, StoreInterfaceImportTool.GRID_OUTLINE_FIRST, StoreInterfaceImportTool.PREVIEW_OUTLINE_FIRST).forEach { first ->
                val c = byId.getValue(first + i)
                assertEquals(StoreInterfaceImportTool.THEME_COLOURS[i], c.colour, "component ${c.id}")
                assertEquals(i != 0, c.hidden, "component ${c.id}")
            }
        }
    }

    @Test
    fun `the selection glow is drawn under the item and the outline over it for every slot`() {
        for (slot in 0 until StoreInterfaceImportTool.SLOT_COUNT) {
            assertTrue(StoreInterfaceImportTool.SELECT_GLOW_FIRST + slot < StoreInterfaceImportTool.SLOT_FIRST + slot)
            assertTrue(StoreInterfaceImportTool.SELECT_OUTLINE_FIRST + slot > StoreInterfaceImportTool.SLOT_FIRST + slot)
            assertTrue(StoreInterfaceImportTool.SLOT_BACKGROUND_FIRST + slot > StoreInterfaceImportTool.GRID_PANEL)
        }
    }
}
