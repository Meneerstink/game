package gg.rsmod.game.tools.importer

import com.fasterxml.jackson.databind.ObjectMapper
import gg.rsmod.game.RspsPaths
import java.io.File
import kotlin.test.*

class CluePuzzleInterfaceTests {
    private val mapper=ObjectMapper()
    private val source=mapper.readTree(File(RspsPaths.data("../docs/CLUE/cache/interfaces.json")))["306"]
    private val evidence=mapper.readTree(File(RspsPaths.data("../docs/CLUE/cache/puzzle-pictures.json")))
    private val pictures=CluePuzzleInterfaceBake.pictures(evidence["pictures"])
    private val models=(pictures.flatMap { it.models }+listOf(3438,4695)).distinct().associateWith { it+70000 }

    @Test fun `seven real pictures become source click regions and wide model layers without synthetic tile items`() {
        val components=CluePuzzleInterfaceBake.components(source,pictures,models,mapOf(539 to 9000,540 to 9001))
        assertEquals(6+25+168,components.size)
        for(picture in pictures.indices)for(piece in 0 until 24) {
            val c=components[CluePuzzleInterfaceBake.modelComponent(picture,piece)]
            assertEquals(models.getValue(pictures[picture].models[piece]),c.model)
            assertEquals(4,c.parent)
            assertTrue(c.hidden)
            assertTrue(c.ops.isEmpty())
            assertContentEquals(intArrayOf(513,0,0,1400),InterfaceHookProbeTool.componentModelCamera(OsrsInterfacePortTool.encode(c)))
        }
        for(cell in 0 until 25) {
            val c=components[CluePuzzleInterfaceBake.CLICK_FIRST+cell]
            assertEquals(cell%5*50,c.x);assertEquals(cell/5*50,c.y)
            assertEquals(49,c.w);assertEquals(49,c.h)
            assertEquals(listOf("Move"),c.ops)
            assertEquals("<col=ff9040>Sliding piece</col>",c.opBase)
        }
        for(c in components)OsrsInterfacePortTool.encode(c)
    }

    @Test fun `missing tile models partial pictures and quest selectors cannot become a blank playable puzzle`() {
        assertFailsWith<NoSuchElementException> {
            CluePuzzleInterfaceBake.components(source,pictures,models-pictures.first().models.first(),mapOf(539 to 9000,540 to 9001))
        }
        val bad=evidence["pictures"].deepCopy<com.fasterxml.jackson.databind.node.ArrayNode>().also { it.remove(6) }
        assertFailsWith<IllegalArgumentException> { CluePuzzleInterfaceBake.pictures(bad) }
        assertFailsWith<IllegalArgumentException> { CluePuzzleInterfaceBake.modelComponent(7,0) }
        assertFailsWith<IllegalArgumentException> { CluePuzzleInterfaceBake.modelComponent(0,24) }
    }
}
