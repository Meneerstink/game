package gg.rsmod.game.tools.importer

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.entity.resolveTransformId
import io.netty.buffer.Unpooled
import kotlin.test.*

class ClueWatsonCacheTests {
    private fun variant(check: Boolean): ByteArray {
        val source = OsrsNpcProbeTool.OsrsNpc(if (check) 7304 else 7303).apply {
            name = "Watson"; ops[0] = "Talk-to"; if (check) ops[2] = "Check"
        }
        return OsrsNpcImportTool.encode667Npc(source, intArrayOf(12), intArrayOf(13), 4, mutableListOf())
    }

    @Test fun `one physical Watson retains source menu slots and private before after representations`() {
        val bytes = ClueWatsonCacheTool.variants(variant(false), variant(true), 2408, 15234, 15235)
        val buf = Unpooled.wrappedBuffer(bytes)
        val def = NpcDef(14406).also { it.decode(buf) }
        assertEquals(0, buf.readableBytes())
        assertEquals("Watson", def.name)
        assertEquals(4, def.basId)
        assertEquals(2408, def.varp)
        assertEquals(-1, def.varbit)
        assertEquals(15234, resolveTransformId(def.id, def.transforms, 0))
        assertEquals(15235, resolveTransformId(def.id, def.transforms, 1))
        assertEquals(15235, resolveTransformId(def.id, def.transforms, 99))
        val post = NpcDef(15235).also { it.decode(Unpooled.wrappedBuffer(variant(true))) }
        assertEquals("Check", post.options[2])
        System.getProperty("clue.fixtures")?.let { java.io.File(it).apply { mkdirs() }.resolve("watson-root.bin").writeBytes(bytes) }
    }

    @Test fun `invalid selectors aliased forms and wrong source options cannot produce a Watson root`() {
        assertFailsWith<IllegalArgumentException> { ClueWatsonCacheTool.variants(variant(false), variant(true), 10000, 15234, 15235) }
        assertFailsWith<IllegalArgumentException> { ClueWatsonCacheTool.variants(variant(false), variant(true), 2408, 15234, 15234) }
        assertFailsWith<IllegalArgumentException> { ClueWatsonCacheTool.variants(variant(false), variant(true), 2408, 14406, 15234) }
        assertFailsWith<IllegalArgumentException> { ClueWatsonCacheTool.variants(variant(false), variant(false), 2408, 15234, 15235) }
    }
}
