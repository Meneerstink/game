package gg.rsmod.net.codec.game

import gg.rsmod.net.packet.GamePacket
import gg.rsmod.net.packet.IPacketMetadata
import gg.rsmod.net.packet.PacketType
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Audit S-07: payloads of packets flagged `ignore: true` are skipped, not copied into a buffer
 * that nobody releases; the stream stays aligned so the next real packet decodes.
 */
class GamePacketDecoderIgnoreTests {
    private val metadata =
        object : IPacketMetadata {
            override fun getType(opcode: Int): PacketType? =
                when (opcode) {
                    IGNORED_VARIABLE -> PacketType.VARIABLE_BYTE
                    IGNORED_FIXED, REAL -> PacketType.FIXED
                    else -> null
                }

            override fun getLength(opcode: Int): Int =
                when (opcode) {
                    IGNORED_FIXED -> 6
                    REAL -> 2
                    else -> 0
                }

            override fun shouldIgnore(opcode: Int): Boolean = opcode == IGNORED_VARIABLE || opcode == IGNORED_FIXED
        }

    @Test
    fun `a thousand ignored packets produce nothing and the next packet still decodes`() {
        val channel = EmbeddedChannel(GamePacketDecoder(null, metadata))
        val input = Unpooled.buffer()
        repeat(1000) {
            input.writeByte(IGNORED_VARIABLE)
            input.writeByte(255)
            input.writeZero(255)
            input.writeByte(IGNORED_FIXED)
            input.writeZero(6)
        }
        input.writeByte(REAL)
        input.writeByte(0x12)
        input.writeByte(0x34)

        channel.writeInbound(input)

        val packet = channel.readInbound() as GamePacket
        try {
            assertEquals(REAL, packet.opcode)
            assertEquals(2, packet.payload.readableBytes())
            assertEquals(0x1234, packet.payload.readUnsignedShort())
        } finally {
            packet.payload.release()
        }
        assertNull(channel.readInbound())
        channel.finish()
    }

    @Test
    fun `an ignored packet split over two reads is skipped once it is complete`() {
        val channel = EmbeddedChannel(GamePacketDecoder(null, metadata))
        val first = Unpooled.buffer()
        first.writeByte(IGNORED_VARIABLE)
        first.writeByte(10)
        first.writeZero(4)
        channel.writeInbound(first)
        assertNull(channel.readInbound())

        val second = Unpooled.buffer()
        second.writeZero(6)
        second.writeByte(REAL)
        second.writeByte(0)
        second.writeByte(7)
        channel.writeInbound(second)

        val packet = channel.readInbound() as GamePacket
        try {
            assertEquals(REAL, packet.opcode)
            assertEquals(7, packet.payload.readUnsignedShort())
        } finally {
            packet.payload.release()
        }
        channel.finish()
    }

    private companion object {
        const val IGNORED_VARIABLE = 29
        const val IGNORED_FIXED = 87
        const val REAL = 7
    }
}
