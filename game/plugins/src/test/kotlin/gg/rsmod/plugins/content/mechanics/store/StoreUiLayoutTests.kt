package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.game.tools.importer.StoreInterfaceImportTool
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-10-03 (bug 210545 + buglist C8): the Donator/Deadman/Loyalty store window grew (7x6 grid) and keeps its Prev/Next page
 * buttons. The server must drive whichever window the cache holds: every clickable component of both layouts is bound, and the
 * render code addresses grid and later components only through the live layout.
 */
class StoreUiLayoutTests {
    private val layouts = listOf(StoreInterfaceImportTool.CURRENT, StoreInterfaceImportTool.LEGACY)

    @Test
    fun `every button of both window layouts is bound, Prev page included`() {
        layouts.forEach { l ->
            listOf(l.pagePrevious, l.pageNext, l.carouselPrevious, l.carouselNext, l.buyLayer).forEach {
                assertTrue(it in StoreUi.CLICKABLE, "${l.columns}x${l.rows}: component $it not bound")
            }
            (0 until l.slotCount).forEach { assertTrue(l.slotFirst + it in StoreUi.CLICKABLE, "${l.columns}x${l.rows}: slot $it not bound") }
        }
        assertTrue(StoreUi.CLOSE in StoreUi.CLICKABLE)
        assertTrue(StoreInterfaceImportTool.CURRENT.slotCount > StoreInterfaceImportTool.LEGACY.slotCount, "more kits a page")
    }

    @Test
    fun `an unknown cache window falls back to the legacy layout, the built one to the current`() {
        assertEquals(StoreInterfaceImportTool.LEGACY, StoreInterfaceImportTool.layoutFor(0))
        assertEquals(StoreInterfaceImportTool.CURRENT, StoreInterfaceImportTool.layoutFor(StoreInterfaceImportTool.COMPONENT_COUNT))
    }

    /** The grid and everything after it moved; a fixed constant in StoreUi would address the wrong component in one of the caches. */
    @Test
    fun `StoreUi uses no fixed grid or later component id`() {
        val text = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/store/StoreUi.kt").readText()
        val moved =
            listOf(
                "SLOT_COUNT", "SLOT_FIRST", "SLOT_BACKGROUND_FIRST", "SELECT_GLOW_FIRST", "SELECT_OUTLINE_FIRST", "PAGE_PREVIOUS",
                "PAGE_TEXT", "PAGE_NEXT", "PREVIEW_MODEL", "CAROUSEL_PREVIOUS", "CAROUSEL_NEXT", "RESULT_NAME", "KIT_NAME",
                "REQUIREMENT", "COSMETIC", "PRICE_ICON", "PRICE", "BUY_LAYER", "BUY_GRAPHIC", "BUY_TEXT", "EMPTY_TEXT",
            )
        val offenders = moved.filter { Regex("""Ui\.$it\b""").containsMatchIn(text) }
        assertEquals(emptyList<String>(), offenders, "StoreUi must use layout(player) for these")
    }
}
