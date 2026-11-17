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
        assertEquals("Safe Deposit Box", main.components.first { it.id == (18) }.text)
        assertEquals(listOf("Close"), main.components.first { it.id == (30) }.ops)
        assertEquals((0 until 10).map { it%5*39 to it/5*38 }, main.components.drop(31).map { it.x to it.y })
        assertEquals((0 until 28).map { 16+it%4*42 to 4+it/4*36 }, side.components.drop(3).map { it.x to it.y })
        assertTrue(main.components.drop(31).all { it.ops.take(5) == listOf("Withdraw-1","Withdraw-5","Withdraw-10","Withdraw-X","Withdraw-All") })
        assertTrue(side.components.drop(3).all { it.ops[3] == "Deposit-X" })
        assertTrue((820..832).all { it in main.sprites })
    }

    @Test fun `runtime reward grid and inventory slots use one append only server component contract`() {
        val main = OsrsInterfacePortTool.port(819, root["819"], fonts, runtimeWidgets = true)
        val side = OsrsInterfacePortTool.port(806, root["806"], fonts, runtimeWidgets = true)
        assertEquals(211, main.components.size); assertEquals(83, side.components.size)
        assertEquals("Deadman Reward Store", main.components.first { it.id == (Dmm2WindowPort.REWARD_TITLE) }.text)
        assertEquals(listOf("Close"), main.components.first { it.id == (Dmm2WindowPort.REWARD_CLOSE) }.ops)
        repeat(7) { i ->
            val first = Dmm2WindowPort.REWARD_FIRST+i*Dmm2WindowPort.REWARD_STRIDE
            assertEquals(listOf("Select","Buy-1","Buy-5","Buy-10","Buy-50"), main.components.first { it.id == (first) }.ops.take(5))
            assertEquals(5,main.components.first { it.id == (first+10) }.type)
            assertEquals(4,main.components.first { it.id == (first+11) }.type)
        }
        assertTrue(side.components.slice(14..41).all { it.ops.take(5) == listOf("Select","Sell-1","Sell-5","Sell-10","Sell-50") })
        assertEquals(311, main.components.first { it.id == (38) }.scrollH)
        assertTrue((3524..3531).all { it in main.sprites })
        listOf(main,side).forEach { port ->
            assertEquals(port.components.indices.toList(), port.components.map { it.id }.sorted())
            port.components.forEach { component ->
                assertTrue(component.parent == -1 || component.parent in port.components.indices)
                assertTrue(OsrsInterfacePortTool.encode(component).isNotEmpty())
            }
        }
    }

    @Test fun `desktop controls and compact card geometry are decoded source equivalents`() {
        val main = OsrsInterfacePortTool.port(819,root["819"],fonts,runtimeWidgets=true)
        fun c(id: Int) = main.components.single { it.id == id }
        assertEquals(4735,c(Dmm2WindowPort.VIEW_ICON).sprite); assertEquals(11,c(Dmm2WindowPort.VIEW_ICON).parent)
        assertEquals(8386,c(Dmm2WindowPort.HELP_ICON).sprite); assertEquals(12,c(Dmm2WindowPort.HELP_ICON).parent)
        assertEquals(788,c(Dmm2WindowPort.FILTER_ARROW).sprite); assertEquals(125,c(Dmm2WindowPort.FILTER_ARROW).opacity)
        assertEquals("No filters available",c(Dmm2WindowPort.FILTER_LABEL).text)
        assertEquals(0x9f9f9f,c(Dmm2WindowPort.FILTER_LABEL).colour)
        assertEquals(26,c(13).w); assertEquals(1,c(13).wMode); assertTrue(c(13).ops.isEmpty())
        assertEquals(218,c(10).w); assertTrue(setOf(4735,4737,8386,8387,788).all { it in main.sprites })
        assertEquals(listOf("Close"),c(Dmm2WindowPort.INFO_FRAME_CLOSE).ops)
        repeat(7) { index ->
            val normal = Dmm2WindowPort.cardLayout(false,index)
            val expanded = Dmm2WindowPort.cardLayout(true,index)
            assertEquals(109,normal[0].w); assertEquals(72,normal[0].h)
            assertEquals(49,expanded[0].w); assertEquals(48,expanded[0].h)
            assertEquals(4,expanded[0].y); assertTrue(expanded[0].x+expanded[0].w<=437)
        }
    }

    @Test fun `layout bridge preserves all nine geometry arguments in native 667 CS2 format`() {
        val input = java.io.DataInputStream(java.io.ByteArrayInputStream(Dmm2WindowPort.layoutScript()))
        assertEquals(0,input.readUnsignedByte())
        listOf(1,2,7,8,0).forEach { assertEquals(33,input.readUnsignedShort()); assertEquals(it,input.readInt()) }
        assertEquals(2000,input.readUnsignedShort()); assertEquals(0,input.readUnsignedByte())
        listOf(3,4,5,6,0).forEach { assertEquals(33,input.readUnsignedShort()); assertEquals(it,input.readInt()) }
        assertEquals(2001,input.readUnsignedShort()); assertEquals(0,input.readUnsignedByte())
        assertEquals(21,input.readUnsignedShort()); assertEquals(0,input.readUnsignedByte())
        assertEquals(13,input.readInt())
        assertEquals(listOf(9,0,0,9,0,0),List(6) { input.readUnsignedShort() })
        assertEquals(0,input.readUnsignedByte()); assertEquals(1,input.readUnsignedShort()); assertEquals(-1,input.read())
    }

    @Test fun `control bridge updates the source View caption and graphic without a fixed local id`() {
        val input = java.io.DataInputStream(java.io.ByteArrayInputStream(Dmm2WindowPort.controlScript()))
        assertEquals(0,input.readUnsignedByte())
        listOf(0 to 1,35 to 0,33 to 0).forEach { (op,value) -> assertEquals(op,input.readUnsignedShort()); assertEquals(value,input.readInt()) }
        assertEquals(2300,input.readUnsignedShort()); assertEquals(0,input.readUnsignedByte())
        listOf(2,1).forEach { assertEquals(33,input.readUnsignedShort()); assertEquals(it,input.readInt()) }
        assertEquals(2105,input.readUnsignedShort()); assertEquals(0,input.readUnsignedByte())
        assertEquals(21,input.readUnsignedShort()); assertEquals(0,input.readUnsignedByte())
        assertEquals(8,input.readInt()); assertEquals(listOf(3,1,0,3,1,0),List(6) { input.readUnsignedShort() })
        assertEquals(0,input.readUnsignedByte()); assertEquals(1,input.readUnsignedShort()); assertEquals(-1,input.read())
    }
}
