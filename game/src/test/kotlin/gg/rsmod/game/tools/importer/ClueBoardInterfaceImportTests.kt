package gg.rsmod.game.tools.importer

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import kotlin.test.*

class ClueBoardInterfaceImportTests {
    private val root = ObjectMapper().readTree(File(System.getProperty("rsps.data"),"../docs/CLUE/cache/board-interfaces.json"))
    private val fonts = mapOf(497 to 494)
    private val models = mapOf(3044 to 70000,16344 to 70001)
    private val sprites = mapOf(535 to 9000,536 to 9001,541 to 9002,542 to 9003)
    @Test fun `source papers retain all22components both models raw cameras parents and close hover`() {
        var count=0
        for ((group,size) in mapOf(221 to 14,625 to 8)) {
            val components=ClueInterfaceImportTool.components(group,root[group.toString()],fonts,models,sprites)
            assertEquals(size,components.size);count+=size
            val close=components.single { it.id==if(group==221)0 else 2 }
            assertEquals(listOf("Close"),close.ops)
            assertEquals(listOf(44,-2147483645,if(group==221)9001 else 9003),close.mouseOver)
            assertEquals(listOf(44,-2147483645,if(group==221)9000 else 9002),close.mouseLeave)
            val model=components.single { it.type==6 }
            val source=root[group.toString()][model.id.toString()]
            assertEquals(source["rotationX"].asInt(),model.xan)
            assertEquals(source["rotationZ"].asInt(),model.yan)
            assertEquals(source["rotationY"].asInt(),model.zan)
        }
        assertEquals(22,count)
    }
    @Test fun `a missing final child or self reference to another board cannot be accepted`() {
        val missing=root["221"].deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>();missing.remove("13")
        assertFailsWith<IllegalArgumentException> { ClueInterfaceImportTool.components(221,missing,fonts,models,sprites) }
        val wrong=root["221"].deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        (wrong["0"]["onMouseOverListener"] as com.fasterxml.jackson.databind.node.ArrayNode).set(1,ObjectMapper().nodeFactory.numberNode(123))
        assertFailsWith<IllegalArgumentException> { ClueInterfaceImportTool.components(221,wrong,fonts,models,sprites) }
    }
}
