package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageStructure
import gg.rsmod.game.message.MessageValue
import gg.rsmod.net.packet.GamePacket
import gg.rsmod.net.packet.GamePacketReader
import gg.rsmod.net.packet.PacketType
import io.netty.buffer.Unpooled
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import org.junit.Assert.assertEquals
import org.junit.Test

/** EVENT_KEYBOARD payload exactly as the client writes it after framing: n x (p1 keycode, p2 delay) (MainLogicManager). */
class KeyTypedDecoderTests {
    private fun reader(payload: ByteArray) = GamePacketReader(GamePacket(68, PacketType.VARIABLE_BYTE, Unpooled.wrappedBuffer(payload)))

    private val structure =
        MessageStructure(type = PacketType.VARIABLE_BYTE, opcodes = intArrayOf(68), length = -1, ignore = false, values = Object2ObjectLinkedOpenHashMap<String, MessageValue>())

    @Test
    fun `one key is read with its delay consumed`() {
        val reader = reader(byteArrayOf(83, 0, 12))
        val message = KeyTypedDecoder().decode(68, structure, reader)
        assertEquals(83, message.keycode)
        assertEquals(emptyList<Int>(), message.following)
        assertEquals(0, reader.readableBytes)
    }

    @Test
    fun `keys batched in one packet are all kept in order`() {
        // Space, then "1", then Escape in one client cycle - the old decoder kept only Space.
        val reader = reader(byteArrayOf(83, 0, 3, 16, 0, 1, 13, 1, 0))
        val message = KeyTypedDecoder().decode(68, structure, reader)
        assertEquals(83, message.keycode)
        assertEquals(listOf(16, 13), message.following)
        assertEquals(0, reader.readableBytes)
    }
}
