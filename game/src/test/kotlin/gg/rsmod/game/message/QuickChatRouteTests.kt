package gg.rsmod.game.message

import gg.rsmod.game.message.handler.packetBody
import gg.rsmod.game.model.ChatFilterType
import gg.rsmod.game.model.social.PrivateMessagePolicy
import gg.rsmod.net.packet.DataType
import gg.rsmod.net.packet.GamePacket
import gg.rsmod.net.packet.GamePacketReader
import gg.rsmod.net.packet.PacketType
import io.netty.buffer.Unpooled
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Client ScriptRunner sends the phrase id and filler bytes after the channel/name. */
class QuickChatRouteTests {
    @Test
    fun `public and friends chat use the same phrase payload`() {
        for (channel in 0..3) {
            val phrase = byteArrayOf(0x12, 0x34, 0x56)
            val decoded = MessageQuickChatPublicDecoder().decode(30, structure(30), reader(30, byteArrayOf(channel.toByte()) + phrase))
            assertEquals(channel, decoded.channel)
            assertArrayEquals(phrase, decoded.payload)
        }
    }

    @Test
    fun `private phrase retains recipient and filler bytes`() {
        val phrase = byteArrayOf(0x12, 0x34, 0x56)
        val decoded = MessageQuickChatPrivateDecoder().decode(79, structure(79), reader(79, "Mod Ash".toByteArray() + byteArrayOf(0) + phrase))
        assertEquals("Mod Ash", decoded.username)
        assertArrayEquals(phrase, decoded.payload)
    }

    @Test
    fun `outbound quick chat packet body uses client reader field order`() {
        // ServerConnectionReader: MESSAGE_QUICKCHAT_PRIVATE reads filtered, name,
        // g2 id-high, g3 id-low, rank, then g2 phrase id and filler values.
        val body = packetBody {
            put(DataType.BYTE, 0)
            putString("Alice")
            put(DataType.SHORT, 0x1234)
            put(DataType.TRI_BYTE, 15)
            put(DataType.BYTE, 1)
            putBytes(byteArrayOf(0x34, 0x56, 0x78))
        }
        assertArrayEquals(byteArrayOf(0, 65, 108, 105, 99, 101, 0, 0x12, 0x34, 0, 0, 15, 1, 0x34, 0x56, 0x78), body)
    }

    @Test
    fun `quick private message uses the typed message sender status rule`() {
        ChatFilterType.values().forEach { status ->
            val expected = if (status == ChatFilterType.OFF) ChatFilterType.ON else status
            assertEquals(expected, PrivateMessagePolicy.senderPrivateStatusAfterSending(status))
        }
    }

    private fun reader(opcode: Int, body: ByteArray) =
        GamePacketReader(GamePacket(opcode, PacketType.VARIABLE_BYTE, Unpooled.wrappedBuffer(body)))

    private fun structure(opcode: Int) =
        MessageStructure(
            type = PacketType.VARIABLE_BYTE,
            opcodes = intArrayOf(opcode),
            length = -1,
            ignore = false,
            values = Object2ObjectLinkedOpenHashMap<String, MessageValue>(),
        )
}
