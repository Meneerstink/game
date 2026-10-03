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
    fun `every window child stays inside the window`() {
        val offenders =
            StoreInterfaceImportTool.components(fonts)
                .filter { it.parent == StoreInterfaceImportTool.WINDOW && it.resizeX == 0 && it.id > 10 }
                .filter { it.x < 0 || it.y < 0 || it.x + it.width > StoreInterfaceImportTool.WIDTH || it.y + it.height > StoreInterfaceImportTool.HEIGHT }
                .map { "${it.id} (${it.x},${it.y} ${it.width}x${it.height})" }
        assertEquals(emptyList<String>(), offenders)
    }

    /**
     * Owner 2026-10-03 (bug 210545): the window ~8% larger than the 488x330 one and more kits a page than its 6x5 grid. The owner
     * plays resizable (gameframe 746); in fixed mode (512x334 main screen, window centred) only the painted outer frame may be
     * clipped - every button, text and slot must stay inside that viewport.
     */
    @Test
    fun `the window is 8 percent larger, shows more kits a page and stays usable in fixed mode`() {
        assertTrue(StoreInterfaceImportTool.WIDTH >= 488 * 108 / 100 && StoreInterfaceImportTool.HEIGHT >= 330 * 108 / 100, "window ${StoreInterfaceImportTool.WIDTH}x${StoreInterfaceImportTool.HEIGHT}")
        assertTrue(StoreInterfaceImportTool.SLOT_COUNT > 30, "kits a page: ${StoreInterfaceImportTool.SLOT_COUNT}")
        val dx = (StoreInterfaceImportTool.WIDTH - 512) / 2
        val dy = (StoreInterfaceImportTool.HEIGHT - 334) / 2
        val offenders =
            StoreInterfaceImportTool.components(fonts)
                .filter { it.parent == StoreInterfaceImportTool.WINDOW && it.id > StoreInterfaceImportTool.BACKGROUND_FIRST + StoreInterfaceImportTool.TAB_COUNT - 1 }
                .filter { it.x - dx < 0 || it.y - dy < 0 || it.x - dx + it.width > 512 || it.y - dy + it.height > 334 }
                .map { "${it.id} (${it.x},${it.y} ${it.width}x${it.height})" }
        assertEquals(emptyList<String>(), offenders, "clipped in fixed mode")
    }

    /** The server addresses components through [StoreInterfaceImportTool.Layout]; the built window and both layouts must agree. */
    @Test
    fun `the layouts match the built window and the legacy cache window`() {
        val current = StoreInterfaceImportTool.CURRENT
        assertEquals(StoreInterfaceImportTool.COMPONENT_COUNT, current.componentCount)
        assertEquals(StoreInterfaceImportTool.SLOT_FIRST, current.slotFirst)
        assertEquals(StoreInterfaceImportTool.PAGE_PREVIOUS, current.pagePrevious)
        assertEquals(StoreInterfaceImportTool.PAGE_NEXT, current.pageNext)
        assertEquals(StoreInterfaceImportTool.BUY_LAYER, current.buyLayer)
        assertEquals(StoreInterfaceImportTool.EMPTY_TEXT, current.emptyText)
        assertEquals(StoreInterfaceImportTool.PREVIEW_MODEL, current.previewModel)
        // The 2026-09-22 window as both caches hold it today: 152 components, Prev 135 / Next 137 / Buy 148 (owner trace 2026-10-03).
        val legacy = StoreInterfaceImportTool.LEGACY
        assertEquals(152, legacy.componentCount)
        assertEquals(135, legacy.pagePrevious)
        assertEquals(137, legacy.pageNext)
        assertEquals(148, legacy.buyLayer)
        assertEquals(current, StoreInterfaceImportTool.layoutFor(current.componentCount))
        assertEquals(legacy, StoreInterfaceImportTool.layoutFor(152))
        val byId = StoreInterfaceImportTool.components(fonts).associateBy { it.id }
        assertEquals("Prev page", byId.getValue(current.pagePrevious).text)
        assertEquals(listOf("Previous page"), byId.getValue(current.pagePrevious).ops)
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
    fun `every shop has its own painted window and only the first shop's is visible by default`() {
        val byId = StoreInterfaceImportTool.components(fonts).associateBy { it.id }
        for (i in 0 until StoreInterfaceImportTool.TAB_COUNT) {
            val c = byId.getValue(StoreInterfaceImportTool.BACKGROUND_FIRST + i)
            assertEquals(StoreArtTool.BACKGROUND_FIRST + i, c.graphic, "component ${c.id}")
            assertEquals(i != 0, c.hidden, "component ${c.id}")
            assertEquals(StoreInterfaceImportTool.WIDTH to StoreInterfaceImportTool.HEIGHT, c.width to c.height, "the window art is drawn 1:1")
        }
    }

    @Test
    fun `the selection glow is drawn under the item and the ring over it for every slot, all above the window art`() {
        for (slot in 0 until StoreInterfaceImportTool.SLOT_COUNT) {
            assertTrue(StoreInterfaceImportTool.SELECT_GLOW_FIRST + slot < StoreInterfaceImportTool.SLOT_FIRST + slot)
            assertTrue(StoreInterfaceImportTool.SELECT_OUTLINE_FIRST + slot > StoreInterfaceImportTool.SLOT_FIRST + slot)
            assertTrue(StoreInterfaceImportTool.SLOT_BACKGROUND_FIRST + slot > StoreInterfaceImportTool.BACKGROUND_FIRST + StoreInterfaceImportTool.TAB_COUNT - 1)
        }
    }

    /** Every painted sprite is drawn at its own size, so the client never stretches the art. */
    @Test
    fun `every art graphic matches its sprite size`() {
        val art = StoreArtTool.all()
        val offenders =
            StoreInterfaceImportTool.components(fonts)
                .filter { it.graphic in art.keys }
                .filter { art.getValue(it.graphic).let { image -> image.width != it.width || image.height != it.height } }
                .map { "${it.id} sprite ${it.graphic}" }
        assertEquals(emptyList<String>(), offenders)
    }

    /** The 667 sprite encoding round-trips: size, trailer and a visible pixel's alpha. */
    @Test
    fun `painted art encodes as a valid revision-667 sprite`() {
        val image = StoreArtTool.slot()
        val data = StoreArtTool.encode(image)
        val n = data.size
        assertEquals(1, ((data[n - 2].toInt() and 0xFF) shl 8) or (data[n - 1].toInt() and 0xFF))
        val p = n - 8 - 7
        assertEquals(image.width, ((data[p + 9].toInt() and 0xFF) shl 8) or (data[p + 10].toInt() and 0xFF))
        assertEquals(image.height, ((data[p + 11].toInt() and 0xFF) shl 8) or (data[p + 12].toInt() and 0xFF))
        assertEquals(0x2, data[0].toInt())
        val centre = (image.height / 2) * image.width + image.width / 2
        assertTrue(data[1 + centre].toInt() != 0, "opaque pixel uses a palette entry, not the transparent index 0")
    }
}