package gg.rsmod.game.tools.importer

import io.netty.buffer.Unpooled
import kotlin.test.*

class ArchiveItemDecoderTests {
    @Test fun `holding whitelist and bronzeman flag retain following options and parameters`() {
        val b = Unpooled.buffer()
        b.writeByte(161).writeShort(2).writeShort(15139).writeShort(15140)
        b.writeByte(251).writeByte(36).writeBytes("Wield".toByteArray()).writeByte(0)
        b.writeByte(249).writeByte(1).writeByte(0).writeMedium(299).writeInt(150).writeByte(0)
        val bytes = ByteArray(b.readableBytes()); b.readBytes(bytes); b.release()
        val d = ModernItemDefDecoder.decode(34725, bytes)
        assertEquals(listOf(15139, 15140), d.holdingWhitelist.toList())
        assertTrue(d.bronzeman)
        assertEquals("Wield", d.inventoryOptions[1])
        assertEquals(150, d.params[299])
        assertEquals(0, d.trailingBytes)
    }
}
