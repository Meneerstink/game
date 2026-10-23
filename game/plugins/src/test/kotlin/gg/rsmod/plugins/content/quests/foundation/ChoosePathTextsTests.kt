package gg.rsmod.plugins.content.quests.foundation

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool
import gg.rsmod.game.tools.importer.QuestGuideInterfaceImportTool as L
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A39 (owner 2026-10-11: "choose your path maak er bijv van choose your deadman path ... het kan iets beter visueel en text"): the
 * window's Deadman wording, every line measured with the real font widths so none runs out of its box, and the subtitle clear of
 * the header divider's ruby (painted at y 39.5, radius 3.2).
 */
class ChoosePathTextsTests {
    private val cache = Paths.get("..", "..", "data", "cache").toFile().toString()

    @Test
    fun `Deadman wording that fits its boxes, under the divider`() {
        val library = CacheLibrary(cache)
        val fonts = LootKeyInterfaceImportTool.fonts(library)
        fun advances(id: Int) = library.data(13, id, 0)!!.let { d -> IntArray(256) { d[2 + it].toInt() and 0xFF } }
        val widths = mapOf(fonts.p12 to advances(fonts.p12), fonts.b12 to advances(fonts.b12))
        val comps = L.components(fonts)
        library.close()
        fun width(font: Int, line: String) = Regex("<[^>]*>").replace(line, "").sumOf { widths.getValue(font)[it.code and 0xFF] }

        val subtitle = comps.first { it.id == L.SUBTITLE }
        assertTrue("Deadman path" in subtitle.text, subtitle.text)
        assertTrue(subtitle.y > 39 + 4, "subtitle at y ${subtitle.y} sits on the divider ruby")
        assertTrue(subtitle.y + subtitle.height <= L.ROWS_Y + 1, "subtitle runs into the first row")
        subtitle.text.split("<br>").forEach { assertTrue(width(subtitle.font, it) <= subtitle.width, "subtitle line '$it': ${width(subtitle.font, it)} > ${subtitle.width}") }

        val dialog = comps.first { it.id == L.DIALOG_TEXT }
        assertTrue("Deadman path" in dialog.text, dialog.text)
        dialog.text.split("<br>").forEach { assertTrue(width(dialog.font, it) <= dialog.width, "dialog line '$it'") }

        val art = File("../src/main/java/gg/rsmod/game/tools/importer/ChoosePathArt.java").readText()
        assertTrue("\"CHOOSE YOUR DEADMAN PATH\"" in art, "painted title")
    }
}
