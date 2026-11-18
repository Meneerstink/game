package gg.rsmod.game.tools.importer

import gg.rsmod.game.fs.def.NpcDef
import io.netty.buffer.Unpooled
import kotlin.test.*

class CluePetDigCacheTests {
    private fun raw(name: String,store: Boolean=false,option4: String?=null): ByteArray {
        val out=java.io.ByteArrayOutputStream()
        fun str(op: Int,text: String) { out.write(op);out.write(text.toByteArray(Charsets.ISO_8859_1));out.write(0) }
        str(2,name);str(30,"Talk-to");if(store)str(31,"Store");str(32,"Pick-up");if(option4!=null)str(33,option4)
        out.write(12);out.write(1);out.write(0)
        return out.toByteArray()
    }
    @Test fun `both existing pet menus keep all original bytes and Store while adding only Dig`() {
        for((id,name) in CluePetDigCacheTool.PETS) {
            val old=raw(name,id==14844);val actual=CluePetDigCacheTool.patch(id,old)
            assertContentEquals(old.copyOf(old.size-1),actual.copyOf(old.size-1))
            val def=NpcDef(id).also { it.decode(Unpooled.wrappedBuffer(actual)) }
            assertEquals("Dig",def.options[3]);assertEquals("Talk-to",def.options[0]);assertEquals("Pick-up",def.options[2])
            if(id==14844)assertEquals("Store",def.options[1])
            assertContentEquals(actual,CluePetDigCacheTool.patch(id,actual))
        }
    }
    @Test fun `wrong identity existing option and truncated terminator are rejected before planning`() {
        assertFailsWith<IllegalArgumentException> { CluePetDigCacheTool.patch(14631,raw("Another dog")) }
        assertFailsWith<IllegalArgumentException> { CluePetDigCacheTool.patch(14631,raw("Bloodhound",option4="Keep")) }
        assertFailsWith<IllegalArgumentException> { CluePetDigCacheTool.patch(14631,raw("Bloodhound").dropLast(1).toByteArray()) }
    }
}
