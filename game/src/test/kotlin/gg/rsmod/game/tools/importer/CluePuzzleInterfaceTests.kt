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

    @Test fun `frame camera translations stay in model space instead of moving the projection centre`() {
        val components=CluePuzzleInterfaceBake.components(source,pictures,models,mapOf(539 to 9000,540 to 9001))
        for (id in listOf(2,3)) {
            val frame=source[id.toString()]
            val mesh=ModelData(2,0,0)
            mesh.vertexX[0]=-94; mesh.vertexX[1]=90
            mesh.vertexY[0]=-8; mesh.vertexY[1]=4
            mesh.vertexZ[0]=-78; mesh.vertexZ[1]=106
            val x=mesh.vertexX.copyOf(); val y=mesh.vertexY.copyOf(); val z=mesh.vertexZ.copyOf()
            CluePuzzleInterfaceBake.adaptFrameModel(frame["modelId"].asInt(),mesh,source)
            repeat(2) { v ->
                assertEquals(x[v]+frame["offsetX2d"].asInt(),mesh.vertexX[v])
                assertEquals(y[v]+frame["offsetY2d"].asInt(),mesh.vertexY[v])
                assertEquals(z[v]+frame["offsetY2d"].asInt(),mesh.vertexZ[v])
            }
            assertEquals(0,components[id].originX)
            assertEquals(0,components[id].originY)
            assertEquals(frame["originalY"].asInt(),components[id].y)
        }
        val tile=ModelData(1,0,0).also { it.vertexZ[0]=64 }
        CluePuzzleInterfaceBake.adaptFrameModel(pictures.first().models.first(),tile,source)
        assertContentEquals(intArrayOf(64),tile.vertexZ)
        val backing=components[1]
        assertTrue(backing.filled)
        assertEquals(3611648,backing.colour)
        assertEquals(265,backing.w); assertEquals(265,backing.h)
        assertEquals(1,backing.xMode); assertEquals(1,backing.yMode)
    }

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
