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

/**
 * The client writes `p1(pjstrlen(name))` then `pjstr(name)` for a join and a lone `p1(0)` for a leave (`FriendChat`). That first
 * byte is the var-byte frame header - `ClientMessage.create` writes none of its own, as MESSAGE_PUBLIC's `p1(0)` + `psize1` shows -
 * and `GamePacketDecoder.decodeLength` consumes it, so the decoder sees only the name (join) or nothing (leave). The old decoder
 * read that byte again, cutting the first letter off every channel name (owner 2026-09-24: friends chat "werkt niet").
 *
 * The optional trailing field is the whole reason this decoder overrides the declarative path -
 * reading a string off an empty buffer throws, and an exception here would kill the channel the
 * packet arrived on.
 */
class ClanJoinChatLeaveChatDecoderTests {
    @Test
    fun `a join carries the channel owner's name`() {
        assertEquals("Zezima", decode(join("Zezima")).name)
    }

    @Test
    fun `a leave carries no name and does not read past the payload`() {
        assertEquals("", decode(leave()).name)
    }

    @Test
    fun `a name is read whole, including its spaces`() {
        assertEquals("Mod Ash", decode(join("Mod Ash")).name)
    }

    private fun decode(payload: ByteArray) =
        ClanJoinChatLeaveChatDecoder().decode(
            opcode = OPCODE,
            structure = structure,
            reader = GamePacketReader(GamePacket(OPCODE, PacketType.VARIABLE_BYTE, Unpooled.wrappedBuffer(payload))),
        )

    /** The payload after framing: `pjstr(name)`. */
    private fun join(name: String): ByteArray = name.toByteArray() + 0

    /** A leave's frame has size 0: an empty payload. */
    private fun leave(): ByteArray = byteArrayOf()

    private companion object {
        private const val OPCODE = 1

        private val structure =
            MessageStructure(
                type = PacketType.VARIABLE_BYTE,
                opcodes = intArrayOf(OPCODE),
                length = -1,
                ignore = false,
                values = Object2ObjectLinkedOpenHashMap<String, MessageValue>(),
            )
    }
}
