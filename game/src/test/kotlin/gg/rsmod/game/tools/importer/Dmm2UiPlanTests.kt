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

    @Test fun `runtime safebox builds the stoneframe and all physical slots with cache script geometry`() {
        val main = OsrsInterfacePortTool.port(230, root["230"], fonts, runtimeWidgets = true)
        val side = OsrsInterfacePortTool.port(232, root["232"], fonts, runtimeWidgets = true)
        assertEquals(41, main.components.size); assertEquals(31, side.components.size)
        assertEquals("Safe Deposit Box", main.components[18].text)
        assertEquals(listOf("Close"), main.components[30].ops)
        assertEquals((0 until 10).map { it%5*39 to it/5*38 }, main.components.drop(31).map { it.x to it.y })
        assertEquals((0 until 28).map { 16+it%4*42 to 4+it/4*36 }, side.components.drop(3).map { it.x to it.y })
        assertTrue(main.components.drop(31).all { it.ops.take(5) == listOf("Withdraw-1","Withdraw-5","Withdraw-10","Withdraw-X","Withdraw-All") })
        assertTrue(side.components.drop(3).all { it.ops[3] == "Deposit-X" })
        assertTrue((820..832).all { it in main.sprites })
    }

    @Test fun `runtime reward grid and inventory slots use one append only server component contract`() {
        val main = OsrsInterfacePortTool.port(819, root["819"], fonts, runtimeWidgets = true)
        val side = OsrsInterfacePortTool.port(806, root["806"], fonts, runtimeWidgets = true)
        assertEquals(193, main.components.size); assertEquals(83, side.components.size)
        assertEquals("Deadman Reward Store", main.components[Dmm2WindowPort.REWARD_TITLE].text)
        assertEquals(listOf("Close"), main.components[Dmm2WindowPort.REWARD_CLOSE].ops)
        repeat(7) { i ->
            val first = Dmm2WindowPort.REWARD_FIRST+i*Dmm2WindowPort.REWARD_STRIDE
            assertEquals(listOf("Select","Buy-1","Buy-5","Buy-10","Buy-50"), main.components[first].ops.take(5))
            assertEquals(5,main.components[first+10].type)
            assertEquals(4,main.components[first+11].type)
        }
        assertTrue(side.components.slice(14..41).all { it.ops.take(5) == listOf("Select","Sell-1","Sell-5","Sell-10","Sell-50") })
        assertEquals(311, main.components[38].scrollH)
        assertTrue((3524..3531).all { it in main.sprites })
        listOf(main,side).forEach { port ->
            assertEquals(port.components.indices.toList(), port.components.map { it.id })
            port.components.forEach { component ->
                assertTrue(component.parent == -1 || component.parent in port.components.indices)
                assertTrue(OsrsInterfacePortTool.encode(component).isNotEmpty())
            }
        }
    }
}
