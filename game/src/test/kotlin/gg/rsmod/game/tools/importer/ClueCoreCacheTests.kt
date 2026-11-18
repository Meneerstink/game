package gg.rsmod.game.tools.importer

import com.fasterxml.jackson.databind.ObjectMapper
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.ItemDef
import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.*

class ClueCoreCacheTests {
    private val proof = ObjectMapper().readTree(File(RspsPaths.data("../docs/CLUE/cache/core-source-items.json")))
    private fun source(id: Int): ModernItemDef {
        val row=proof[id.toString()]
        val raw=Base64.getDecoder().decode(row["rawBase64"].asText())
        val hash=MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(row["sha256"].asText(),hash)
        return ModernItemDefDecoder.decode(id,raw)
    }
    private fun native(): ByteArray = ByteArrayOutputStream().also { stream ->
        DataOutputStream(stream).apply {
            writeByte(1); writeShort(1337) // existing/imported inventory model must survive the identity patch
            writeByte(2); writeBytes("Legacy clue"); writeByte(0)
            writeByte(11)
            writeByte(12); writeInt(50)
            writeByte(35); writeBytes("Old option"); writeByte(0)
            writeByte(0)
        }
    }.toByteArray()

    @Test fun `all ten real core sources retain imported model and value while copying names menus and proven stacking`() {
        val ids=ClueCoreCacheTool.LEGACY.keys+setOf(19835,19836)
        for (id in ids) {
            val source=source(id); val before=native()
            val edited=ClueCoreCacheTool.patch(before,source)
            assertContentEquals(before.copyOf(3),edited.copyOf(3))
            val decoded=ItemDef(id).also { it.decode(Unpooled.wrappedBuffer(edited)) }
            assertEquals(ClueCoreCacheTool.clueName(source),decoded.name)
            assertEquals(source.inventoryOptions.toList(),decoded.inventoryMenu.toList())
            assertEquals(id in setOf(20546,20545,20544,20543,19836),decoded.stacks)
            assertEquals(50,decoded.cost)
            assertContentEquals(edited,ClueCoreCacheTool.patch(edited,source))
        }
    }

    @Test fun `real master source opcode160 does not crash the post-import rename or make the active clue stack`() {
        val master=source(19835)
        assertEquals(2,master.stackable) // real pinned bytes exercise the formerly rejected path
        assertTrue(File(RspsPaths.data("../docs/CLUE/sources/Clue_scroll_(master).wiki")).readText().contains("|stackable = No"))
        val decoded=ItemDef(30001).also { it.decode(Unpooled.wrappedBuffer(ClueCoreCacheTool.patch(native(),master))) }
        assertEquals("78 clue scroll",decoded.name)
        assertFalse(decoded.stacks)
    }

    @Test fun `the master-specific source exception does not reinterpret unknown stack flags generally`() {
        val different=source(2677).also { it.stackable=2 }
        assertFailsWith<IllegalArgumentException> { ClueCoreCacheTool.patch(native(),different) }
        different.stackable=3
        assertFailsWith<IllegalStateException> { ClueCoreCacheTool.patch(native(),different) }
    }
}
