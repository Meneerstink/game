package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message
import gg.rsmod.game.model.social.FriendsChatChannel

/**
 * The whole state of the friends-chat channel the receiving player is in.
 *
 * A null [channel] encodes as a zero-length packet, which is how the client is told it is no longer
 * in any channel (`ServerConnectionReader`: `currentPacketSize == 0` clears `FriendChat`).
 *
 * The channel is re-sent in full on every membership change rather than through
 * `UPDATE_FRIENDCHAT_CHANNEL_SINGLEUSER` deltas. At this server's player count the whole list is a
 * few hundred bytes, and a full refresh cannot drift out of step with the real membership.
 *
 * @param worldId the world number reported for every member. Single-world server, so it is the same
 * for everyone; the client shows it beside each name.
 * @param worldName the world label shown beside each member.
 */
data class UpdateFriendChatChannelFullMessage(
    val channel: FriendsChatChannel?,
    val worldId: Int = 1,
    val worldName: String = "RSPS",
) : Message
