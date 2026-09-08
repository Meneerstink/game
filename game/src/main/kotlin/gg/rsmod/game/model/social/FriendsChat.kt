package gg.rsmod.game.model.social

import gg.rsmod.game.message.impl.MessageFriendChannelMessage
import gg.rsmod.game.message.impl.UpdateFriendChatChannelFullMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.util.Misc
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The rank a member holds in a friends-chat channel, as the client renders it.
 *
 * The client only uses the value to pick a crown: rank 1 draws `<img=0>`, ranks 2 and 3 draw
 * `<img=1>`, and everything below draws none. `player.friends` in this codebase is a plain list of
 * names with no per-friend rank storage, so only the two ranks that can actually be derived are
 * modelled here. Recruit/corporal/etc. need a ranked friends list first.
 */
object FriendsChatRank {
    const val GUEST = 0
    const val OWNER = 3
}

/**
 * One friends-chat channel, owned by a player and named after them.
 *
 * Membership is session state, not saved data: in RuneScape a channel exists for as long as someone
 * is standing in it, and everybody is dropped from it at logout.
 */
class FriendsChatChannel(
    val owner: String,
) {
    private val members = ArrayList<Player>()

    val name: String get() = Misc.formatForDisplay(owner)

    fun members(): List<Player> = synchronized(members) { members.toList() }

    fun isEmpty(): Boolean = synchronized(members) { members.isEmpty() }

    fun contains(player: Player): Boolean = synchronized(members) { members.any { it === player } }

    fun add(player: Player): Boolean =
        synchronized(members) {
            if (members.any { it === player }) {
                false
            } else {
                members.add(player)
            }
        }

    fun remove(player: Player): Boolean = synchronized(members) { members.removeIf { it === player } }

    fun rankOf(player: Player): Int =
        if (player.username.equals(owner, ignoreCase = true)) FriendsChatRank.OWNER else FriendsChatRank.GUEST
}

/**
 * The world's live friends-chat channels.
 *
 * The client's join flow (`FriendChat.join`, ClientProt `FRIENDS_CHAT_CHANGE`) sends the name of
 * the player whose channel is being joined, and expects the server to answer with
 * `UPDATE_FRIENDCHAT_CHANNEL_FULL`; a zero-length reply is how a channel is cleared. Nothing else
 * makes the Friends Chat tab populate, which is why joining appeared to do nothing.
 *
 * Channels are keyed by the owner's lowercased username. A channel is created on demand when
 * somebody joins it and discarded when the last member leaves, so there is no channel registry to
 * persist and no way for a stale channel to keep a logged-out [Player] alive.
 */
class FriendsChat {
    private val channels = ConcurrentHashMap<String, FriendsChatChannel>()

    private val messageIds = AtomicLong()

    /** The channel [player] is currently in, or null. */
    fun channelOf(player: Player): FriendsChatChannel? = channels.values.firstOrNull { it.contains(player) }

    /**
     * Moves [player] into the channel owned by [ownerName], leaving whatever channel they were in.
     *
     * @return true when the player is now in that channel.
     */
    fun join(
        player: Player,
        ownerName: String,
    ): Boolean {
        val key = Misc.formatForDisplay(ownerName).lowercase()
        if (key.isBlank()) {
            return false
        }
        val current = channelOf(player)
        if (current != null && current.owner.lowercase() == key) {
            return true
        }
        leave(player)

        val channel = channels.getOrPut(key) { FriendsChatChannel(Misc.formatForDisplay(ownerName)) }
        channel.add(player)
        broadcastChannelState(channel)
        return true
    }

    /**
     * Removes [player] from whatever channel they are in, telling everyone affected.
     *
     * @param notifyLeaver false when the player is on their way out of the world anyway and there
     * is no session left to clear the channel on.
     */
    fun leave(
        player: Player,
        notifyLeaver: Boolean = true,
    ) {
        val channel = channelOf(player) ?: return
        channel.remove(player)
        if (notifyLeaver) {
            player.write(UpdateFriendChatChannelFullMessage(channel = null))
        }
        if (channel.isEmpty()) {
            channels.remove(channel.owner.lowercase())
        } else {
            broadcastChannelState(channel)
        }
    }

    /** Re-sends the whole channel to every member, which is how the client learns of joins/leaves. */
    fun broadcastChannelState(channel: FriendsChatChannel) {
        channel.members().forEach { it.write(UpdateFriendChatChannelFullMessage(channel)) }
    }

    /**
     * Sends [text] from [sender] to everybody in [sender]'s channel.
     *
     * @return false when the sender is not in a channel, so the caller can say so.
     */
    fun talk(
        world: World,
        sender: Player,
        text: String,
    ): Boolean {
        val channel = channelOf(sender) ?: return false
        val id = nextMessageId()
        val rank = channel.rankOf(sender)
        val name = Misc.formatForDisplay(sender.username)

        channel.members().forEach { member ->
            member.write(
                MessageFriendChannelMessage(
                    world = world,
                    sender = name,
                    channel = channel.name,
                    rank = rank,
                    id = id,
                    text = text,
                ),
            )
        }
        return true
    }

    /**
     * A id no other recent line shares.
     *
     * The client drops a line whose id it has seen in the last hundred, and starts life with a ring
     * of zeroes, so ids run from 1 upwards. Only 40 bits of the id survive the packet - 16 bits and
     * then 24 - so the counter wraps there rather than silently colliding after a truncation.
     */
    private fun nextMessageId(): Long = 1L + (messageIds.getAndIncrement() % MAX_MESSAGE_ID)

    private companion object {
        private const val MAX_MESSAGE_ID = 0xFF_FFFF_FFFFL
    }
}
