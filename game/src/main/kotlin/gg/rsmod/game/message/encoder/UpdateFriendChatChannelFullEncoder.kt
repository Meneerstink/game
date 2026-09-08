package gg.rsmod.game.message.encoder

import gg.rsmod.game.message.MessageEncoder
import gg.rsmod.game.message.impl.UpdateFriendChatChannelFullMessage
import gg.rsmod.game.model.social.FriendsChatRank
import gg.rsmod.net.packet.DataType
import gg.rsmod.net.packet.GamePacketBuilder
import gg.rsmod.util.Base37
import gg.rsmod.util.Misc

/**
 * Encodes `UPDATE_FRIENDCHAT_CHANNEL_FULL` (ServerProt 12, variable-short).
 *
 * The layout is read directly off the client's own reader (`ServerConnectionReader`, the
 * `UPDATE_FRIENDCHAT_CHANNEL_FULL` branch):
 *
 * ```
 * gjstr  owner display name
 * g1     1 when a separate unfiltered owner name follows
 * [gjstr owner unfiltered name]
 * g8     channel name, base-37 encoded
 * g1b    kick rank
 * g1     member count; 255 means "ignore this packet"
 * per member:
 *   gjstr  display name
 *   g1     1 when a separate unfiltered name follows
 *   [gjstr unfiltered name]
 *   g2     world id
 *   g1b    rank
 *   gjstr  world name
 * ```
 *
 * The filtered/unfiltered pairs exist for censored names; nothing here censors names, so the flag
 * is always 0 and the client reuses the single name for both, exactly as it does for uncensored
 * names from a live server.
 *
 * The payload is emitted as one `BYTES` field because the member list is variable length, which the
 * declarative structure in `data/packets.yml` cannot express. `PublicChatEncoder` does the same for
 * its compressed text.
 */
class UpdateFriendChatChannelFullEncoder : MessageEncoder<UpdateFriendChatChannelFullMessage>() {
    override fun extract(
        message: UpdateFriendChatChannelFullMessage,
        key: String,
    ): Number = throw Exception("Unhandled value key.")

    override fun extractBytes(
        message: UpdateFriendChatChannelFullMessage,
        key: String,
    ): ByteArray =
        when (key) {
            "channel" -> {
                val buf = GamePacketBuilder()
                val channel = message.channel
                // A zero-length payload is the client's "you are in no channel" signal, so a null
                // channel deliberately writes nothing at all.
                if (channel != null) {
                    val members = channel.members()

                    buf.putString(channel.name)
                    buf.put(DataType.BYTE, 0)
                    buf.put(DataType.LONG, Base37.encode(channel.name))
                    // Only the owner can kick, because only the owner has a rank above guest.
                    buf.put(DataType.BYTE, FriendsChatRank.OWNER)
                    buf.put(DataType.BYTE, minOf(members.size, MAX_MEMBERS))

                    members.take(MAX_MEMBERS).forEach { member ->
                        buf.putString(Misc.formatForDisplay(member.username))
                        buf.put(DataType.BYTE, 0)
                        buf.put(DataType.SHORT, message.worldId)
                        buf.put(DataType.BYTE, channel.rankOf(member))
                        buf.putString(message.worldName)
                    }
                }

                val data = ByteArray(buf.byteBuf.readableBytes())
                buf.byteBuf.readBytes(data)
                data
            }
            else -> throw Exception("Unhandled value key.")
        }

    private companion object {
        /**
         * The client allocates a fixed `FriendChatUser[100]`, and reads 255 as "discard this
         * packet", so the count it is sent must stay below both.
         */
        private const val MAX_MEMBERS = 100
    }
}
