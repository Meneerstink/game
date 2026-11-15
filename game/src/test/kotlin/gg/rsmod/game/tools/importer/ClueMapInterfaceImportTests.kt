package gg.rsmod.game.tools.importer
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import kotlin.test.*
class ClueMapInterfaceImportTests {
    private val source=ObjectMapper().readTree(File("../docs/CLUE/cache/map-interfaces.json"))
    @Test fun `all nine source maps preserve their full widget hierarchy and every model camera`() {
        for(group in ClueInterfaceImportTool.MAP_GROUPS) {
            val rows=source[group.toString()]
            val models=rows.map { it["modelId"].asInt() }.distinct().associateWith { it+70000 }
            val sprites=rows.filter { it["type"].asInt()==5 }.map { it["spriteId"].asInt() }.associateWith { it+2000 } + mapOf(539 to 2539,540 to 2540)
            val components=ClueInterfaceImportTool.components(group,rows,mapOf(494 to 100,495 to 101,496 to 102,497 to 103),models,sprites)
            assertEquals(rows.size(),components.size)
            for(c in components) {
                val r=rows[c.id.toString()]
                assertEquals(r["originalX"].asInt(),c.x)
                assertEquals(r["originalY"].asInt(),c.y)
                val parent=r["parentId"].asInt()
                assertEquals(if(parent<0)-1 else parent and 65535,c.parent)
                if(parent>=0)assertTrue(components.any { it.id==(parent and 65535) })
                if(c.type==6) {
                    assertEquals(r["modelId"].asInt()+70000,c.model)
                    assertEquals(r["rotationX"].asInt(),c.xan)
                    assertEquals(r["rotationZ"].asInt(),c.yan)
                    assertEquals(r["rotationY"].asInt(),c.zan)
                    assertEquals(r["modelZoom"].asInt(),c.zoom)
                }
                OsrsInterfacePortTool.encode(c) // fail on an unsupported narrowing/field
            }
        }
    }
    @Test fun `a missing component cannot silently shift the source map child identities`() {
        val rows=source["351"].deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().also { it.remove("4") }
        assertFailsWith<IllegalArgumentException> { ClueInterfaceImportTool.components(351,rows,emptyMap(),emptyMap(),emptyMap()) }
    }
    @Test fun `a missing final close child is rejected instead of becoming an unclosable map`() {
        val rows=source["351"].deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().also { it.remove("59") }
        assertFailsWith<IllegalArgumentException> { ClueInterfaceImportTool.components(351,rows,emptyMap(),emptyMap(),emptyMap()) }
    }
}
