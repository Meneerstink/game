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
 * The payload is the channel-owner name, except on leave, where the client sends a zero size and no
 * name at all. The declarative structure in
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
        // The var-byte size header is consumed by GamePacketDecoder, so the payload is only the name (none on leave). Reading a
        // "length" byte here used to eat the first letter of every channel name ("The channel you tried to join does not exist")
        // and threw on the empty leave payload.
        val name = if (reader.readableBytes > 0) reader.string else ""
        return ClanJoinChatLeaveChatMessage(name)
    }

    override fun decode(
        opcode: Int,
        opcodeIndex: Int,
        values: HashMap<String, Number>,
        stringValues: HashMap<String, String>,
    ): ClanJoinChatLeaveChatMessage = ClanJoinChatLeaveChatMessage(stringValues["name"] ?: "")
}
