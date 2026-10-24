package gg.rsmod.plugins.content.inter.gameframe

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.KeybindingInterfaceImportTool
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N294 (owner 2026-10-24: "de F1-knop heeft een streep erdoor"): the divider band under the Keybinding button is hidden. */
class KeybindingButtonTests {
    private fun u2(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    @Test
    fun `the Settings tab's divider band that ran through the F1 button is hidden every time the tab is mounted and at login`() {
        val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            // 261:20 is the 190 wide band at y 90 (rev-667 if1 header: x 4, y 6, width 8, height 10) that the button (y 86..126) covers.
            val band = library.data(3, KeybindingInterfaceImportTool.SETTINGS_INTERFACE, KeybindingUi.SETTINGS_DIVIDER)!!
            assertEquals(90, u2(band, 6))
            assertEquals(190, u2(band, 8))
            val button = library.data(3, KeybindingInterfaceImportTool.SETTINGS_INTERFACE, KeybindingInterfaceImportTool.SETTINGS_BUTTON)!!
            assertTrue(u2(button, 6) < 90 + u2(band, 10) && u2(button, 6) + u2(button, 10) > 90, "the button overlaps the band")
        } finally {
            library.close()
        }
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/inter/gameframe/keybinding.plugin.kts").readText()
        assertEquals(2, Regex("""setComponentHidden\(Ui\.SETTINGS_INTERFACE, KeybindingUi\.SETTINGS_DIVIDER, true\)""").findAll(plugin).count())
    }
}
