package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.MessageStructure
import gg.rsmod.game.message.impl.ClanJoinChatLeaveChatMessage
import gg.rsmod.net.packet.DataType
import gg.rsmod.net.packet.GamePacketReader

/**
 * Decodes ClientProt `FRIENDS_CHAT_CHANGE`, which the client uses for both joining a friends-chat
 * channel and leaving one (`FriendChat.join` / `FriendChat.leave`).
 *
 * The payload is a length byte followed by the channel-owner name, except on leave, where the
 * client writes the length byte as `0` and no name at all. The declarative structure in
 * `data/packets.yml` has no way to say "this field is only present sometimes", and reading a string
 * off an empty buffer would throw, so the payload is read here instead.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class ClanJoinChatLeaveChatDecoder : MessageDecoder<ClanJoinChatLeaveChatMessage>() {
    override fun decode(
        opcode: Int,
        structure: MessageStructure,
        reader: GamePacketReader,
    ): ClanJoinChatLeaveChatMessage {
        val length = reader.getUnsigned(DataType.BYTE).toInt()
        val name = if (length > 0 && reader.readableBytes > 0) reader.string else ""
        return ClanJoinChatLeaveChatMessage(name)
    }

    override fun decode(
        opcode: Int,
        opcodeIndex: Int,
        values: HashMap<String, Number>,
        stringValues: HashMap<String, String>,
    ): ClanJoinChatLeaveChatMessage = ClanJoinChatLeaveChatMessage(stringValues["name"] ?: "")
}
