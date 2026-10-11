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

    /** Owner 2026-10-11 bank-size window: no panel or button overlaps another or the 6 px gold frame. */
    @Test
    fun `no panel or button overlaps another or the frame`() {
        val a = StoreArtTool
        val ui = StoreInterfaceImportTool
        val byId = ui.components(fonts).associateBy { it.id }
        val buy = byId.getValue(ui.BUY_LAYER)
        val close = byId.getValue(ui.CLOSE)
        val boxes =
            mapOf(
                "close" to intArrayOf(close.x, close.y, close.width, close.height),
                "tabs" to intArrayOf(a.TAB_X, a.TAB_Y, a.TAB_PITCH * 2 + a.TAB_WIDTH, a.TAB_HEIGHT),
                "categories" to intArrayOf(a.SECTION_X, a.SECTION_Y, a.SECTION_PITCH * 5 + a.SECTION_WIDTH, a.SECTION_HEIGHT),
                "strip" to intArrayOf(a.STRIP_X, a.STRIP_Y, a.STRIP_WIDTH, a.STRIP_HEIGHT),
                "grid" to intArrayOf(a.GRID_X, a.GRID_Y, a.GRID_WIDTH, a.GRID_HEIGHT),
                "pages" to intArrayOf(a.GRID_X, a.NAV_Y, a.GRID_WIDTH, a.NAV_HEIGHT),
                "showcase" to intArrayOf(a.SHOW_X, a.SHOW_Y, a.SHOW_WIDTH, a.SHOW_HEIGHT),
                "card" to intArrayOf(a.CARD_X, a.CARD_Y, a.CARD_WIDTH, a.CARD_HEIGHT),
                "buy" to intArrayOf(buy.x, buy.y, buy.width, buy.height),
            )
        boxes.forEach { (name, b) ->
            assertTrue(b[0] >= 6 && b[1] >= 6 && b[0] + b[2] <= ui.WIDTH - 6 && b[1] + b[3] <= ui.HEIGHT - 6, "$name ${b.toList()} touches the frame")
        }
        boxes.entries.toList().let { list ->
            for (i in list.indices) for (j in i + 1 until list.size) {
                val (n1, p) = list[i]
                val (n2, q) = list[j]
                val overlap = p[0] < q[0] + q[2] && q[0] < p[0] + p[2] && p[1] < q[1] + q[3] && q[1] < p[1] + p[3]
                assertTrue(!overlap, "$n1 ${p.toList()} overlaps $n2 ${q.toList()}")
            }
        }
        // every slot inside the grid panel
        (0 until ui.SLOT_COUNT).forEach {
            assertTrue(ui.slotX(it) + a.SLOT_SIZE <= a.GRID_X + a.GRID_WIDTH && ui.slotY(it) + a.SLOT_SIZE <= a.GRID_Y + a.GRID_HEIGHT, "slot $it")
        }
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
     * Owner 2026-10-03 (bug 210545): larger than the 488x330 window with more kits a page than its 6x5 grid. Owner 2026-10-10
     * ("betere leesbaarheid", multiple choice "Groter, alleen resizable"): larger again than the 527x360 one; it is built for the
     * resizable game view (the 512x334 fixed viewport clips it, the owner's choice), so it only has to fit the smallest resizable
     * client (765x503).
     */
    @Test
    fun `the window is the bank's size, so it fits the fixed and resizable modal slot, and shows 30 items a page`() {
        // Owner 2026-10-11: "stores moeten niet overlappen zeker niet in fixed mode" - the 512x334 game view (fixed 548:9, resizable 746).
        assertEquals(512, StoreInterfaceImportTool.WIDTH)
        assertEquals(334, StoreInterfaceImportTool.HEIGHT)
        assertEquals(0, StoreInterfaceImportTool.WINDOW_OFFSET_X)
        assertEquals(30, StoreInterfaceImportTool.SLOT_COUNT)
    }

    /** Owner 2026-10-10: category buttons in one row under the shop tabs, none overlapping a tab, the strip or another button. */
    @Test
    fun `the category buttons sit in one row between the shop tabs and the info strip`() {
        val byId = StoreInterfaceImportTool.components(fonts).associateBy { it.id }
        val buttons = (0 until StoreInterfaceImportTool.SECTION_COUNT).map { byId.getValue(StoreInterfaceImportTool.SECTION_FIRST + it * StoreInterfaceImportTool.SECTION_STRIDE) }
        buttons.forEach {
            assertEquals(listOf("View"), it.ops)
            assertTrue(it.y >= StoreArtTool.TAB_Y + StoreArtTool.TAB_HEIGHT && it.y + it.height <= StoreArtTool.STRIP_Y, "button ${it.id} at y=${it.y}")
        }
        buttons.zipWithNext().forEach { (a, b) -> assertTrue(a.x + a.width < b.x, "buttons ${a.id} and ${b.id} overlap") }
        assertTrue(buttons.last().x + buttons.last().width <= StoreArtTool.STRIP_X + StoreArtTool.STRIP_WIDTH)
        (0 until StoreInterfaceImportTool.SECTION_COUNT).forEach {
            val base = StoreInterfaceImportTool.SECTION_FIRST + it * StoreInterfaceImportTool.SECTION_STRIDE
            assertEquals(base, byId.getValue(base + StoreInterfaceImportTool.SECTION_GRAPHIC_OFFSET).parent)
            assertEquals(base, byId.getValue(base + StoreInterfaceImportTool.SECTION_TEXT_OFFSET).parent)
        }
    }

    /** Owner 2026-10-10 "onduidelijk wat je koopt": every card line has its own row inside the card, above the price band. */
    @Test
    fun `the card lines do not overlap each other or the price band`() {
        val byId = StoreInterfaceImportTool.components(fonts).associateBy { it.id }
        val lines =
            listOf(
                StoreInterfaceImportTool.RESULT_NAME, StoreInterfaceImportTool.KIT_NAME, StoreInterfaceImportTool.REQUIREMENT,
                StoreInterfaceImportTool.TRADEABLE, StoreInterfaceImportTool.COSMETIC,
            ).map { byId.getValue(it) }
        lines.zipWithNext().forEach { (a, b) -> assertTrue(a.y + a.height <= b.y, "card lines ${a.id} and ${b.id} overlap") }
        val price = byId.getValue(StoreInterfaceImportTool.PRICE)
        assertTrue(lines.last().y + lines.last().height <= price.y, "effect line overlaps the price")
        assertTrue(price.y + price.height <= StoreArtTool.CARD_Y + StoreArtTool.CARD_HEIGHT)
        assertTrue(lines.first().y >= StoreArtTool.CARD_Y)
        // The owned status shares the requirement's row, right of it.
        val requirement = byId.getValue(StoreInterfaceImportTool.REQUIREMENT)
        val owned = byId.getValue(StoreInterfaceImportTool.OWNED)
        assertEquals(requirement.y, owned.y)
        assertTrue(requirement.x + requirement.width <= owned.x)
        assertTrue(owned.x + owned.width <= StoreArtTool.CARD_X + StoreArtTool.CARD_WIDTH)
    }

    /** The npc preview clientscript: name byte, eight-byte trailer with four int args, the IF_SETNPCMODEL opcode inside. */
    @Test
    fun `the npc preview script encodes four int arguments and sets the npc model`() {
        val script = StoreInterfaceImportTool.npcPreviewScript()
        assertEquals(0, script[0].toInt())
        val text = script.toList()
        val setNpcModel = listOf(2203 ushr 8, 2203 and 0xFF).map { it.toByte() }
        assertTrue(text.windowed(2).any { it == setNpcModel }, "IF_SETNPCMODEL")
        val n = script.size
        // ... int locals 4, string 0, long 0, int args 4, string 0, long 0, switch count 0, section length 1
        val tail = script.copyOfRange(n - 15, n).map { it.toInt() and 0xFF }
        assertEquals(listOf(0, 4, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 1), tail)
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
        // The 2026-10-03 window: the same ids up to the empty text, no category buttons (what both caches hold until 2026-10-10).
        val previous = StoreInterfaceImportTool.PREVIOUS
        assertEquals(200, previous.componentCount)
        assertEquals(196, previous.buyLayer)
        // The first 2026-10-10 window: 7x6 with category buttons.
        assertEquals(220, StoreInterfaceImportTool.SECTIONS_7X6.componentCount)
        assertEquals(StoreInterfaceImportTool.SECTIONS_7X6, StoreInterfaceImportTool.layoutFor(220))
        // The 660x470 window of 2026-10-10/11 (what both caches hold until the bank-size window is applied): 7x7, 250 components.
        assertEquals(250, StoreInterfaceImportTool.WIDE_7X7.componentCount)
        assertEquals(StoreInterfaceImportTool.WIDE_7X7, StoreInterfaceImportTool.layoutFor(250))
        assertEquals(StoreInterfaceImportTool.COMPONENT_COUNT, current.componentCount)
        assertEquals(StoreInterfaceImportTool.SECTION_FIRST, current.sectionFirst)
        assertEquals(StoreInterfaceImportTool.OWNED, current.owned)
        assertEquals(StoreInterfaceImportTool.TRADEABLE, current.tradeable)
        assertEquals(current, StoreInterfaceImportTool.layoutFor(current.componentCount))
        assertEquals(previous, StoreInterfaceImportTool.layoutFor(200))
        assertEquals(legacy, StoreInterfaceImportTool.layoutFor(152))
        val byId = StoreInterfaceImportTool.components(fonts).associateBy { it.id }
        // Owner 2026-10-11: the labels are painted into the page buttons, drawn over the empty click areas.
        assertEquals("", byId.getValue(current.pagePrevious).text)
        assertEquals(listOf("Previous page"), byId.getValue(current.pagePrevious).ops)
        assertEquals(listOf("Next page"), byId.getValue(current.pageNext).ops)
        for ((area, graphic) in listOf(current.pagePrevious to current.pagePreviousGraphic, current.pageNext to current.pageNextGraphic)) {
            val a = byId.getValue(area)
            val g = byId.getValue(graphic)
            assertEquals(listOf(a.x, a.y, a.width, a.height), listOf(g.x, g.y, g.width, g.height), "button $graphic covers its click area")
            assertTrue(graphic > area, "the button is drawn over its click area")
        }
        assertTrue(!StoreInterfaceImportTool.SECTIONS_7X6.pageButtons && current.pageButtons)
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