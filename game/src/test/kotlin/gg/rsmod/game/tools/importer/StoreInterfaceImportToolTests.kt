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

    @Test
    fun `the preview model is enlarged five times through its aspect ratio`() {
        val model = StoreInterfaceImportTool.components(fonts).single { it.id == StoreInterfaceImportTool.PREVIEW_MODEL }
        assertEquals(LootKeyInterfaceImportTool.TYPE_MODEL, model.type)
        assertEquals(1, model.resizeX)
        assertEquals(StoreInterfaceImportTool.PREVIEW_WIDTH / StoreInterfaceImportTool.PREVIEW_ASPECT_X, 5)
    }
}
