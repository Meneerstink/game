package gg.rsmod.game.message.encoder

import gg.rsmod.game.message.MessageEncoder
import gg.rsmod.game.message.impl.MessageFriendChannelMessage
import gg.rsmod.net.packet.DataType
import gg.rsmod.net.packet.GamePacketBuilder
import gg.rsmod.util.Base37

/**
 * Encodes `MESSAGE_FRIENDCHANNEL` (ServerProt 40, variable-byte).
 *
 * Read off the client's own reader (`ServerConnectionReader`, the `MESSAGE_FRIENDCHANNEL` branch):
 *
 * ```
 * g1     1 when a separate unfiltered sender name follows
 * gjstr  sender display name
 * [gjstr sender unfiltered name]
 * g8     channel name, base-37 encoded
 * g2     message id, high 16 bits
 * g3     message id, low 24 bits
 * g1     sender rank
 * WordPack  the text: a smart length followed by huffman-compressed cp1252
 * ```
 *
 * The id is not decoration: the client keeps the last hundred it has seen and drops a line whose id
 * it recognises, which is how a message that reaches a player twice is shown once. Its ring starts
 * out full of zeroes, so zero is never a usable id - see `FriendsChat.nextMessageId`.
 *
 * The text uses the same smart-length-plus-huffman framing as `PublicChatEncoder`, because
 * `WordPack.decode` is what reads both.
 */
class MessageFriendChannelEncoder : MessageEncoder<MessageFriendChannelMessage>() {
    override fun extract(
        message: MessageFriendChannelMessage,
        key: String,
    ): Number = throw Exception("Unhandled value key.")

    override fun extractBytes(
        message: MessageFriendChannelMessage,
        key: String,
    ): ByteArray =
        when (key) {
            "message" -> {
                val buf = GamePacketBuilder()

                buf.put(DataType.BYTE, 0)
                buf.putString(message.sender)
                buf.put(DataType.LONG, Base37.encode(message.channel))
                buf.put(DataType.SHORT, (message.id shr 24) and 0xFFFF)
                buf.put(DataType.TRI_BYTE, message.id and 0xFFFFFF)
                buf.put(DataType.BYTE, message.rank)

                val compressed = ByteArray(256)
                val offset = message.world.huffman.compress(message.text, compressed)
                buf.putSmart(message.text.length)
                buf.putBytes(compressed, 0, offset)

                val data = ByteArray(buf.byteBuf.readableBytes())
                buf.byteBuf.readBytes(data)
                data
            }
            else -> throw Exception("Unhandled value key.")
        }
}
