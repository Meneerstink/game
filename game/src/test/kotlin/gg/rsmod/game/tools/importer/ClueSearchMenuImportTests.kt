package gg.rsmod.game.tools.importer

import com.fasterxml.jackson.databind.ObjectMapper
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.ObjectDef
import io.netty.buffer.Unpooled
import java.io.File
import java.util.Base64
import kotlin.test.*

class ClueSearchMenuImportTests {
    private val proof = ObjectMapper().readTree(File(RspsPaths.data("../docs/CLUE/cache/search-bucket.json")))
    private val before = Base64.getDecoder().decode(proof["native"]["rawBase64"].asText())
    private val source = ModernObjectDef.decode(9568,Base64.getDecoder().decode(proof["source"]["rawBase64"].asText()))

    @Test fun `actual bucket menu correction preserves all original bytes and decodes in the native object reader`() {
        val edited = ClueSearchMenuCacheTool.patch(before,source)
        assertContentEquals(before.copyOf(before.size-1),edited.copyOf(before.size-1))
        val decoded = ObjectDef(9568).also { it.decode(Unpooled.wrappedBuffer(edited)) }
        assertEquals("Bucket",decoded.name)
        assertEquals(listOf("Search",null,null,null,null),decoded.options.map { it?.takeUnless(String::isBlank) })
        val old = Rev667LocType.decode(9568,before)
        val next = Rev667LocType.decode(9568,edited)
        assertEquals(old.modelsByShape,next.modelsByShape)
        assertEquals(old.animation,next.animation)
        assertEquals(old.sizeX,next.sizeX); assertEquals(old.sizeZ,next.sizeZ)
        assertContentEquals(edited,ClueSearchMenuCacheTool.patch(edited,source))
    }

    @Test fun `a different source object cannot authorize the patch`() {
        val wrong = ModernObjectDef.decode(9569,Base64.getDecoder().decode(proof["source"]["rawBase64"].asText()))
        assertFailsWith<IllegalArgumentException> { ClueSearchMenuCacheTool.patch(before,wrong) }
    }
}
