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
    /**
     * N129: the header balance fits its box at the largest balance (10 digits) for every currency, and the tagline fits beside it -
     * measured with the real font advance widths (cache index 13: b12 for the balance, p12 for the tagline).
     */
    @Test
    fun `header balance and tagline fit the info strip at 10 digits`() {
        val library = com.displee.cache.CacheLibrary(java.nio.file.Paths.get("..", "..", "data", "cache").toFile().toString())
        val fonts = gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool.fonts(library)
        fun advances(id: Int) = library.data(13, id, 0)!!.let { d -> IntArray(256) { d[2 + it].toInt() and 0xFF } }
        val b12 = advances(fonts.b12)
        val p12 = advances(fonts.p12)
        library.close()
        fun width(w: IntArray, text: String) = Regex("<[^>]*>").replace(text, "").sumOf { w[it.code and 0xFF] }
        for (currency in StoreCatalogue.Currency.values()) {
            val text = "${String.format(java.util.Locale.US, "%,d", Int.MAX_VALUE)} ${currency.plural}"
            assertTrue(width(b12, text) <= StoreInterfaceImportTool.BALANCE_WIDTH, "$text: ${width(b12, text)} px")
        }
        for ((shop, tagline) in StoreUi.TAGLINES) {
            assertTrue(width(p12, tagline) <= StoreInterfaceImportTool.TAGLINE_WIDTH, "$shop tagline: ${width(p12, tagline)} px")
        }
    }

    private val layouts = listOf(StoreInterfaceImportTool.CURRENT, StoreInterfaceImportTool.WIDE_7X7, StoreInterfaceImportTool.SECTIONS_7X6, StoreInterfaceImportTool.PREVIOUS, StoreInterfaceImportTool.LEGACY)

    @Test
    fun `every button of both window layouts is bound, Prev page included`() {
        layouts.forEach { l ->
            listOf(l.pagePrevious, l.pageNext, l.carouselPrevious, l.carouselNext, l.buyLayer).forEach {
                assertTrue(it in StoreUi.CLICKABLE, "${l.columns}x${l.rows}: component $it not bound")
            }
            (0 until l.slotCount).forEach { assertTrue(l.slotFirst + it in StoreUi.CLICKABLE, "${l.columns}x${l.rows}: slot $it not bound") }
            if (l.sections) {
                (0 until StoreInterfaceImportTool.SECTION_COUNT).forEach {
                    assertTrue(l.sectionFirst + it * StoreInterfaceImportTool.SECTION_STRIDE in StoreUi.CLICKABLE, "category button $it not bound")
                }
            }
        }
        assertTrue(StoreUi.CLOSE in StoreUi.CLICKABLE)
        assertTrue(StoreInterfaceImportTool.CURRENT.componentCount != StoreInterfaceImportTool.LEGACY.componentCount, "the layouts are told apart")
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
                "SECTION_FIRST", "OWNED", "TRADEABLE",
            )
        val offenders = moved.filter { Regex("""Ui\.$it\b""").containsMatchIn(text) }
        assertEquals(emptyList<String>(), offenders, "StoreUi must use layout(player) for these")
    }
}
