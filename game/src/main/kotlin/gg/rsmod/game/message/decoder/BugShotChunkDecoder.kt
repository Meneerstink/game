package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.MessageStructure
import gg.rsmod.game.message.impl.BugShotChunkMessage
import gg.rsmod.net.packet.DataType
import gg.rsmod.net.packet.GamePacketReader

/**
 * T2: decodes a bug-screenshot piece as the 78 client writes it (RspsBugShot.writeChunk): p4 total, p4 offset, then the bytes
 * up to the end of the packet. The declarative structure in data/packets.yml has no "rest of the packet" byte field, so the
 * payload is read here (like [ClanJoinChatLeaveChatDecoder]).
 */
class BugShotChunkDecoder : MessageDecoder<BugShotChunkMessage>() {
    override fun decode(
        opcode: Int,
        structure: MessageStructure,
        reader: GamePacketReader,
    ): BugShotChunkMessage {
        val total = reader.getSigned(DataType.INT).toInt()
        val offset = reader.getSigned(DataType.INT).toInt()
        val data = ByteArray(reader.readableBytes.coerceAtLeast(0))
        reader.getBytes(data)
        return BugShotChunkMessage(total, offset, data)
    }

    override fun decode(
        opcode: Int,
        opcodeIndex: Int,
        values: HashMap<String, Number>,
        stringValues: HashMap<String, String>,
    ): BugShotChunkMessage = BugShotChunkMessage(values["total"]?.toInt() ?: 0, values["offset"]?.toInt() ?: 0, ByteArray(0))
}
