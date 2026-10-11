package gg.rsmod.net.codec.game

import gg.rsmod.net.packet.GamePacket
import gg.rsmod.net.packet.IPacketMetadata
import gg.rsmod.net.packet.PacketType
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * A34 (owner 2026-10-11 19:35, live): the owner's clicks stopped doing anything - "sent message with no valid metadata" for
 * every packet after one point. A variable-length packet with length 0 (FRIENDS_CHAT_CHANGE with no name = "Leave chat",
 * FriendChat.java) left the decoder in LENGTH, so the next packet's opcode byte was read as a length and the stream (and the
 * ISAAC opcode cipher) never lined up again. And an unknown opcode left the session open but deaf.
 */
class GamePacketDecoderDesyncTests {
    private val metadata =
        object : IPacketMetadata {
            override fun getType(opcode: Int): PacketType? =
                when (opcode) {
                    FRIENDS_CHAT_CHANGE -> PacketType.VARIABLE_BYTE
                    REAL -> PacketType.FIXED
                    else -> null
                }

            override fun getLength(opcode: Int): Int = if (opcode == REAL) 2 else 0

            override fun shouldIgnore(opcode: Int): Boolean = false
        }

    @Test
    fun `an empty variable packet is complete and the next packet still decodes`() {
        val channel = EmbeddedChannel(GamePacketDecoder(null, metadata))
        val input = Unpooled.buffer()
        input.writeByte(FRIENDS_CHAT_CHANGE)
        input.writeByte(0)
        input.writeByte(REAL)
        input.writeByte(0x12)
        input.writeByte(0x34)

        channel.writeInbound(input)

        val leave = channel.readInbound() as GamePacket
        assertEquals(FRIENDS_CHAT_CHANGE, leave.opcode)
        assertEquals(0, leave.payload.readableBytes())
        val packet = channel.readInbound() as GamePacket
        try {
            assertEquals(REAL, packet.opcode)
            assertEquals(0x1234, packet.payload.readUnsignedShort())
        } finally {
            packet.payload.release()
        }
        assertNull(channel.readInbound())
        channel.finish()
    }

    @Test
    fun `an opcode no client can send closes the connection instead of leaving a deaf session`() {
        val channel = EmbeddedChannel(GamePacketDecoder(null, metadata))
        val input = Unpooled.buffer()
        input.writeByte(UNKNOWN)
        input.writeZero(5)

        channel.writeInbound(input)

        assertNull(channel.readInbound())
        assertFalse(channel.isOpen)
        channel.finish()
    }

    private companion object {
        /** ClientProt FRIENDS_CHAT_CHANGE (1, variable byte). */
        const val FRIENDS_CHAT_CHANGE = 1
        const val REAL = 7

        /** Not a revision-667 client opcode (ClientProtSizes has none above 93). */
        const val UNKNOWN = 200
    }
}
