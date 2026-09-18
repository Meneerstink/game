package gg.rsmod.game.message

import gg.rsmod.game.message.impl.MessageQuickChatPrivateMessage
import gg.rsmod.game.message.impl.MessageQuickChatPublicMessage
import gg.rsmod.game.message.impl.RawPayloadMessage
import gg.rsmod.net.packet.DataType
import gg.rsmod.net.packet.GamePacketReader

/** Encodes any [RawPayloadMessage]: its prebuilt body is the whole packet (`packets.yml` field `body`). */
class RawPayloadEncoder<T : RawPayloadMessage> : MessageEncoder<T>() {
    override fun extract(message: T, key: String): Number = throw Exception("Unhandled value key.")

    override fun extractBytes(message: T, key: String): ByteArray =
        when (key) {
            "body" -> message.body
            else -> throw Exception("Unhandled value key.")
        }
}

/** Longest phrase payload relayed; real phrases are a few bytes, anything larger is malformed. */
internal const val MAX_QUICKCHAT_PAYLOAD = 64

class MessageQuickChatPublicDecoder : MessageDecoder<MessageQuickChatPublicMessage>() {
    override fun decode(opcode: Int, opcodeIndex: Int, values: HashMap<String, Number>, stringValues: HashMap<String, String>): MessageQuickChatPublicMessage =
        throw RuntimeException()

    override fun decode(opcode: Int, structure: MessageStructure, reader: GamePacketReader): MessageQuickChatPublicMessage {
        val channel = reader.getUnsigned(DataType.BYTE).toInt()
        val payload = ByteArray(minOf(reader.readableBytes, MAX_QUICKCHAT_PAYLOAD))
        reader.getBytes(payload)
        return MessageQuickChatPublicMessage(channel, payload)
    }
}

class MessageQuickChatPrivateDecoder : MessageDecoder<MessageQuickChatPrivateMessage>() {
    override fun decode(opcode: Int, opcodeIndex: Int, values: HashMap<String, Number>, stringValues: HashMap<String, String>): MessageQuickChatPrivateMessage =
        throw RuntimeException()

    override fun decode(opcode: Int, structure: MessageStructure, reader: GamePacketReader): MessageQuickChatPrivateMessage {
        val username = reader.string
        val payload = ByteArray(minOf(reader.readableBytes, MAX_QUICKCHAT_PAYLOAD))
        reader.getBytes(payload)
        return MessageQuickChatPrivateMessage(username, payload)
    }
}
