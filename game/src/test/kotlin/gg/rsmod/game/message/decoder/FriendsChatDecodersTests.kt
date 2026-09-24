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

/** Payloads exactly as the client writes them after framing (FriendChat.kick, FriendsList.setRank, Static525.kick). */
class FriendsChatDecodersTests {
    private fun reader(payload: ByteArray) = GamePacketReader(GamePacket(1, PacketType.VARIABLE_BYTE, Unpooled.wrappedBuffer(payload)))

    private val structure =
        MessageStructure(type = PacketType.VARIABLE_BYTE, opcodes = intArrayOf(1), length = -1, ignore = false, values = Object2ObjectLinkedOpenHashMap<String, MessageValue>())

    @Test
    fun `kick carries the member name`() {
        assertEquals("Mod Ash", FriendsChatKickDecoder().decode(32, structure, reader("Mod Ash".toByteArray() + 0)).name)
    }

    @Test
    fun `set rank reads the name and the negated rank byte`() {
        val message = FriendSetRankDecoder().decode(41, structure, reader("Zezima".toByteArray() + 0 + (-4).toByte()))
        assertEquals("Zezima", message.name)
        assertEquals(4, message.rank)
    }

    @Test
    fun `clan channel kick reads affined, slot and name`() {
        val message = ClanChannelKickDecoder().decode(60, structure, reader(byteArrayOf(1, 0, 7) + "Guest".toByteArray() + 0))
        assertEquals(true, message.affined)
        assertEquals(7, message.slot)
        assertEquals("Guest", message.name)
    }
}
