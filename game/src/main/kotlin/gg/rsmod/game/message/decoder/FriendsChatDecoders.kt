package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.MessageStructure
import gg.rsmod.game.message.impl.ClanChannelKickMessage
import gg.rsmod.game.message.impl.FriendSetRankMessage
import gg.rsmod.game.message.impl.FriendsChatKickMessage
import gg.rsmod.net.packet.DataType
import gg.rsmod.net.packet.GamePacketReader

/*
 * The three social packets below were never registered, so GamePacketDecoder skipped them and the Kick/ban button, the friend
 * ranks of Friends Chat Setup and the clan-channel kick did nothing. Their payloads are read by hand, byte for byte as the client
 * writes them (the var-byte size header is already consumed by the framing).
 */

class FriendsChatKickDecoder : MessageDecoder<FriendsChatKickMessage>() {
    override fun decode(opcode: Int, structure: MessageStructure, reader: GamePacketReader): FriendsChatKickMessage =
        FriendsChatKickMessage(if (reader.readableBytes > 0) reader.string else "")

    override fun decode(opcode: Int, opcodeIndex: Int, values: HashMap<String, Number>, stringValues: HashMap<String, String>) =
        FriendsChatKickMessage(stringValues["name"] ?: "")
}

class FriendSetRankDecoder : MessageDecoder<FriendSetRankMessage>() {
    override fun decode(opcode: Int, structure: MessageStructure, reader: GamePacketReader): FriendSetRankMessage {
        val name = reader.string
        // p1_alt2 writes (byte) -rank.
        val rank = if (reader.readableBytes > 0) -reader.getSigned(DataType.BYTE).toInt() else 0
        return FriendSetRankMessage(name, rank)
    }

    override fun decode(opcode: Int, opcodeIndex: Int, values: HashMap<String, Number>, stringValues: HashMap<String, String>) =
        FriendSetRankMessage(stringValues["name"] ?: "", 0)
}

class ClanChannelKickDecoder : MessageDecoder<ClanChannelKickMessage>() {
    override fun decode(opcode: Int, structure: MessageStructure, reader: GamePacketReader): ClanChannelKickMessage {
        val affined = reader.getUnsigned(DataType.BYTE).toInt() == 1
        val slot = reader.getUnsigned(DataType.SHORT).toInt()
        val name = if (reader.readableBytes > 0) reader.string else ""
        return ClanChannelKickMessage(affined, slot, name)
    }

    override fun decode(opcode: Int, opcodeIndex: Int, values: HashMap<String, Number>, stringValues: HashMap<String, String>) =
        ClanChannelKickMessage(false, 0, stringValues["name"] ?: "")
}

class ClanBanFromChannelDecoder : MessageDecoder<gg.rsmod.game.message.impl.ClanBanFromChannelMessage>() {
    override fun decode(opcode: Int, structure: MessageStructure, reader: GamePacketReader): gg.rsmod.game.message.impl.ClanBanFromChannelMessage {
        val slot = reader.getUnsigned(DataType.SHORT).toInt()
        val name = if (reader.readableBytes > 0) reader.string else ""
        return gg.rsmod.game.message.impl.ClanBanFromChannelMessage(slot, name)
    }

    override fun decode(opcode: Int, opcodeIndex: Int, values: HashMap<String, Number>, stringValues: HashMap<String, String>) =
        gg.rsmod.game.message.impl.ClanBanFromChannelMessage(0, stringValues["name"] ?: "")
}
