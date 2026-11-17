package gg.rsmod.game.tools.importer

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import kotlin.test.*

class Dmm2UiPlanTests {
    private val root = ObjectMapper().readTree(File("../docs/dmm2-research/dmm2-interfaces.json"))
    private val fonts = mapOf(494 to 494, 495 to 495, 496 to 496, 497 to 497, 645 to 645)

    @Test fun `safebox preserves the proven quill font and never substitutes an unrelated font`() {
        val port = OsrsInterfacePortTool.port(230, root["230"], fonts)
        assertTrue(port.components.filter { it.id in listOf(13, 16) }.all { it.font == 645 })
        assertFailsWith<IllegalStateException> { OsrsInterfacePortTool.port(230, root["230"], fonts - 645) }
        assertTrue(port.reports.any { "script 703" in it }, "Unported stone frame must be explicit")
        assertTrue(port.components.any { "Deposit inventory" in it.ops })
    }

    @Test fun `reward store uses the proven omnishop layout and reports dynamic scripts rather than claiming a complete UI`() {
        assertEquals(listOf(230, 232, 819, 806), OsrsInterfacePortTool.DMM2_UI)
        assertFalse(645 in OsrsInterfacePortTool.DMM2_UI)
        val port = OsrsInterfacePortTool.port(819, root["819"], fonts)
        assertEquals(480, port.components.single { it.id == 1 }.w)
        assertEquals(314, port.components.single { it.id == 1 }.h)
        assertEquals(2, port.components.count { it.type == 4 && it.text == "Points" })
        assertTrue(port.extras.any { "thinbox" in it })
        assertEquals(12, OsrsInterfacePortTool.port(806, root["806"], fonts).components.take(12).size)
    }
}
