package gg.rsmod.net.codec.game

import gg.rsmod.net.codec.StatefulFrameDecoder
import gg.rsmod.net.packet.GamePacket
import gg.rsmod.net.packet.IPacketMetadata
import gg.rsmod.net.packet.PacketType
import gg.rsmod.util.io.IsaacRandom
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import mu.KLogging

/**
 * @author Tom <rspsmods@gmail.com>
 */
class GamePacketDecoder(
    private val random: IsaacRandom?,
    private val packetMetadata: IPacketMetadata,
) : StatefulFrameDecoder<GameDecoderState>(GameDecoderState.OPCODE) {
    private var opcode = 0

    private var length = 0

    private var type = PacketType.FIXED

    private var ignore = false

    /** The last [RECENT] complete packets as "opcode/length", for the desync report. */
    private val recent = ArrayDeque<String>(RECENT)

    private fun remember(length: Int) {
        if (recent.size == RECENT) recent.removeFirst()
        recent.addLast("$opcode/$length")
    }

    override fun decode(
        ctx: ChannelHandlerContext,
        buf: ByteBuf,
        out: MutableList<Any>,
        state: GameDecoderState,
    ) {
        when (state) {
            GameDecoderState.OPCODE -> decodeOpcode(ctx, buf, out)
            GameDecoderState.LENGTH -> decodeLength(buf, out)
            GameDecoderState.PAYLOAD -> decodePayload(buf, out)
        }
    }

    private fun decodeOpcode(
        ctx: ChannelHandlerContext,
        buf: ByteBuf,
        out: MutableList<Any>,
    ) {
        if (buf.isReadable) {
            opcode = buf.readUnsignedByte().toInt() - (random?.nextInt() ?: 0) and 0xFF
            val packetType = packetMetadata.getType(opcode)
            if (packetType == null) {
                // Unregistered but real client packet: skip exactly its own bytes (ClientProtSizes) so the packets after it
                // and the ISAAC opcode stream stay aligned. Only a truly unknown opcode still drops the buffer.
                val size = ClientProtSizes.SIZES[opcode]
                if (size == null) {
                    // A1/A34 (owner 2026-10-11 19:35, "this may never happen, certainly not on a Deadman server"): an opcode no
                    // client can send means the stream is already misaligned, and the ISAAC opcode cipher with it - every
                    // later packet decoded as garbage, the player stood frozen in the world until he closed the client.
                    // Donor Void (LoginServer.readPackets): no decoder for the opcode -> log and disconnect. The client then
                    // runs its own connection-lost / reconnect, and the logout goes through the normal Deadman hold.
                    logger.error(
                        "Channel {} sent unknown opcode {} - stream desynchronised, disconnecting. Last packets (opcode/length, oldest first): {}",
                        ctx.channel(), opcode, recent.joinToString(" "),
                    )
                    buf.skipBytes(buf.readableBytes())
                    ctx.channel().close()
                    return
                }
                if (unhandledLogged.add(opcode)) {
                    logger.info("Skipping unregistered client packet {} ({} bytes) - no handler in packets.yml.", opcode, size)
                }
                ignore = true
                type =
                    when (size) {
                        ClientProtSizes.VARIABLE_BYTE -> PacketType.VARIABLE_BYTE
                        ClientProtSizes.VARIABLE_SHORT -> PacketType.VARIABLE_SHORT
                        else -> PacketType.FIXED
                    }
                if (type == PacketType.FIXED) {
                    length = size
                    if (length != 0) setState(GameDecoderState.PAYLOAD) else remember(0)
                } else {
                    setState(GameDecoderState.LENGTH)
                }
                return
            }
            type = packetType
            ignore = packetMetadata.shouldIgnore(opcode)

            when (type) {
                PacketType.FIXED -> {
                    length = packetMetadata.getLength(opcode)
                    if (length != 0) {
                        setState(GameDecoderState.PAYLOAD)
                    } else {
                        remember(0)
                        if (!ignore) out.add(GamePacket(opcode, type, Unpooled.EMPTY_BUFFER))
                    }
                }
                PacketType.VARIABLE_BYTE, PacketType.VARIABLE_SHORT -> setState(GameDecoderState.LENGTH)
                else -> throw IllegalStateException("Unhandled packet type $type for opcode $opcode.")
            }
        }
    }

    private fun decodeLength(
        buf: ByteBuf,
        out: MutableList<Any>,
    ) {
        // T2 AUDIT T2-93 (owner window, 2.18 MB screenshot upload): a TCP read that ended between the two length bytes of a
        // var-short packet threw "readerIndex(1) + length(2) exceeds writerIndex(2)" here and the player was kicked. Over the
        // internet a read may end at any byte. Donor 2009scape GameReadEvent.getPacketSize: wait until the whole length
        // header is there (1 byte var-byte, 2 bytes var-short); ByteToMessageDecoder calls again when more bytes arrive.
        val header = if (type == PacketType.VARIABLE_SHORT) 2 else 1
        if (buf.readableBytes() >= header) {
            length = if (type == PacketType.VARIABLE_SHORT) buf.readUnsignedShort() else buf.readUnsignedByte().toInt()
            if (length != 0) {
                setState(GameDecoderState.PAYLOAD)
            } else {
                // A zero-length variable packet is complete here: back to OPCODE (it stayed in LENGTH and read the next
                // packet's opcode byte as this one's length).
                setState(GameDecoderState.OPCODE)
                remember(0)
                if (!ignore) out.add(GamePacket(opcode, type, Unpooled.EMPTY_BUFFER))
            }
        }
    }

    private fun decodePayload(
        buf: ByteBuf,
        out: MutableList<Any>,
    ) {
        if (buf.readableBytes() >= length) {
            setState(GameDecoderState.OPCODE)
            remember(length)

            /**
             * If the packet isn't flagged as being a packet we should ignore,
             * we queue it up for our game to process the packet.
             *
             * Audit S-07: an ignored packet's payload used to be copied with readBytes() and then
             * dropped without release(), leaking one pooled buffer per ignored packet (opcode 29
             * spam with 255 bytes each ended in an OOM). Skip the bytes instead: nothing is
             * allocated, so there is nothing to release.
             */
            if (ignore) {
                buf.skipBytes(length)
            } else {
                out.add(GamePacket(opcode, type, buf.readBytes(length)))
            }
        }
    }

    companion object : KLogging() {
        /** Packets kept for the desync report. */
        private const val RECENT = 20

        /** Unregistered opcodes already reported, so a chatty packet logs once per server run. */
        private val unhandledLogged = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    }
}
