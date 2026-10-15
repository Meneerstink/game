package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.MessageStructure
import gg.rsmod.game.message.impl.BugShotChunkMessage
import gg.rsmod.net.packet.DataType
import gg.rsmod.net.packet.GamePacketReader

/**
 * T2: decodes a bug-screenshot piece as the 78 client writes it (RspsBugShot.writeChunk): p1 stem length, the report stem
 * (ASCII), p4 total, p4 offset, then the bytes up to the end of the packet. The declarative structure in data/packets.yml
 * has no "rest of the packet" byte field, so the payload is read here (like [ClanJoinChatLeaveChatDecoder]).
 * Never throws (GameSystem.receiveMessage has no catch on this path): a cut packet decodes as total -1, which
 * BugShotUploads refuses; the round-2 layout (p4 total first, its first byte 0) decodes with an empty stem - never a report.
 */
class BugShotChunkDecoder : MessageDecoder<BugShotChunkMessage>() {
    override fun decode(
        opcode: Int,
        structure: MessageStructure,
        reader: GamePacketReader,
    ): BugShotChunkMessage {
        if (reader.readableBytes < 1) {
            return BugShotChunkMessage("", -1, 0, ByteArray(0))
        }
        val length = reader.getUnsigned(DataType.BYTE).toInt() and 0xFF
        val stemBytes = ByteArray(minOf(length, reader.readableBytes))
        reader.getBytes(stemBytes)
        val stem = String(stemBytes, Charsets.ISO_8859_1)
        if (stemBytes.size < length || reader.readableBytes < 8) {
            return BugShotChunkMessage(stem, -1, 0, ByteArray(0))
        }
        val total = reader.getSigned(DataType.INT).toInt()
        val offset = reader.getSigned(DataType.INT).toInt()
        val data = ByteArray(reader.readableBytes.coerceAtLeast(0))
        reader.getBytes(data)
        return BugShotChunkMessage(stem, total, offset, data)
    }

    override fun decode(
        opcode: Int,
        opcodeIndex: Int,
        values: HashMap<String, Number>,
        stringValues: HashMap<String, String>,
    ): BugShotChunkMessage = BugShotChunkMessage(stringValues["stem"] ?: "", values["total"]?.toInt() ?: 0, values["offset"]?.toInt() ?: 0, ByteArray(0))
}
