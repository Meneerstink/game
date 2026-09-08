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
 * The payload shapes here are the ones the client actually writes in `FriendChat`: a join is
 * `p1(pjstrlen(name))` followed by `pjstr(name)`, and a leave is a lone `p1(0)` with no name at all.
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

    /** `p1(Packet.pjstrlen(name))` - the name plus its terminator - then `pjstr(name)`. */
    private fun join(name: String): ByteArray = byteArrayOf((name.length + 1).toByte()) + name.toByteArray() + 0

    /** `p1(0)`, and nothing else. */
    private fun leave(): ByteArray = byteArrayOf(0)

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
