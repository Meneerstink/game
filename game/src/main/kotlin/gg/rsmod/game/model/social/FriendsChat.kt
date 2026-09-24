package gg.rsmod.game.model.social

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import gg.rsmod.game.message.impl.MessageFriendChannelMessage
import gg.rsmod.game.message.impl.UpdateFriendChatChannelFullMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.util.Misc
import gg.rsmod.util.io.AtomicFiles
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Friends-chat ranks exactly as the 667 client uses them: the channel member list (UPDATE_FRIENDCHAT_CHANNEL_FULL `g1b rank`), the
 * friend list (UPDATE_FRIENDLIST rank byte, set with ClientProt FRIEND_SETRANK) and the Friends Chat Setup menus of interface 1108
 * ("Anyone", "Any friends", "Recruit+" ... "General+", "Only me").
 */
object FriendsChatRank {
    const val GUEST = -1
    const val FRIEND = 0
    const val RECRUIT = 1
    const val GENERAL = 6
    const val OWNER = 7
    const val STAFF = 127

    /** Setup-menu labels by rank (the text 1108 shows for the current value). */
    fun label(rank: Int): String =
        when (rank) {
            GUEST -> "Anyone"
            FRIEND -> "Any friends"
            1 -> "Recruit+"
            2 -> "Corporal+"
            3 -> "Sergeant+"
            4 -> "Lieutenant+"
            5 -> "Captain+"
            6 -> "General+"
            else -> "Only me"
        }
}

/**
 * An owner's channel configuration, kept whether or not the owner is online (a channel can be joined while its owner is away).
 * [prefix] is the channel name; null = the channel is disabled. [friends] is the owner's friend list at their last change, so a
 * guest's "friend" rank can be decided while the owner is offline; [ranks] holds the ranks the owner gave friends in 1108.
 */
class FriendsChatSettings(
    var prefix: String? = null,
    var enterRank: Int = FriendsChatRank.GUEST,
    var talkRank: Int = FriendsChatRank.GUEST,
    var kickRank: Int = FriendsChatRank.OWNER,
    var lootShareRank: Int = FriendsChatRank.GUEST,
    /** CoinShare (Friends Chat Setup 1108): drops worth over 100,000 coins are split as coins among the sharers. */
    var coinShare: Boolean = false,
    var friends: MutableSet<String> = HashSet(),
    var ranks: MutableMap<String, Int> = HashMap(),
)

/**
 * One live friends-chat channel. Membership is session state: a channel exists while somebody is in it and everybody leaves it at
 * logout. Its name, kick rank and every member's rank come from the owner's [FriendsChatSettings].
 */
class FriendsChatChannel(
    val owner: String,
    private val chat: FriendsChat,
) {
    private val members = ArrayList<Player>()

    val ownerName: String get() = Misc.formatForDisplay(owner)

    /** The channel name the client shows: the owner's prefix. */
    val name: String get() = chat.settingsOf(owner)?.prefix ?: ownerName

    val kickRank: Int get() = chat.settingsOf(owner)?.kickRank ?: FriendsChatRank.OWNER

    fun members(): List<Player> = synchronized(members) { members.toList() }

    fun isEmpty(): Boolean = synchronized(members) { members.isEmpty() }

    fun size(): Int = synchronized(members) { members.size }

    fun contains(player: Player): Boolean = synchronized(members) { members.any { it === player } }

    fun add(player: Player): Boolean =
        synchronized(members) {
            if (members.any { it === player }) false else members.add(player)
        }

    fun remove(player: Player): Boolean = synchronized(members) { members.removeIf { it === player } }

    fun rankOf(player: Player): Int = chat.rankOf(owner, player)
}

/**
 * The world's friends-chat channels and every owner's channel settings.
 *
 * Joining (ClientProt FRIENDS_CHAT_CHANGE) needs a channel the owner has set up in Friends Chat Setup (1108) - "The channel you tried
 * to join does not exist." otherwise - and a rank at least the channel's enter rank. Talking needs the talk rank, kicking the kick rank
 * and a higher rank than the victim; a kicked player may not rejoin for an hour ([KICK_BAN_MILLIS]). Settings are saved to
 * [SETTINGS_FILE] on every change.
 */
class FriendsChat(private val settingsFile: File = File(SETTINGS_FILE)) {
    private val channels = ConcurrentHashMap<String, FriendsChatChannel>()
    private val settings = ConcurrentHashMap<String, FriendsChatSettings>()
    private val bans = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()
    private val messageIds = AtomicLong()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    init {
        runCatching {
            if (settingsFile.isFile) {
                val type = object : TypeToken<Map<String, FriendsChatSettings>>() {}.type
                val loaded: Map<String, FriendsChatSettings>? = gson.fromJson(settingsFile.readText(), type)
                loaded?.forEach { (owner, value) -> settings[owner.lowercase()] = value }
            }
        }
    }

    private fun key(name: String) = Misc.formatForDisplay(name).lowercase()

    private fun save() {
        runCatching {
            settingsFile.absoluteFile.parentFile?.mkdirs()
            AtomicFiles.writeText(settingsFile, gson.toJson(settings))
        }
    }

    fun settingsOf(owner: String): FriendsChatSettings? = settings[key(owner)]

    private fun settingsFor(owner: Player): FriendsChatSettings = settings.getOrPut(key(owner.username)) { FriendsChatSettings() }

    /** The rank [owner] gave [friend] in their friend list (0 = plain friend) - what UPDATE_FRIENDLIST sends. */
    fun friendRank(owner: String, friend: String): Int = settingsOf(owner)?.ranks?.get(key(friend)) ?: FriendsChatRank.FRIEND

    /** A member's rank in [owner]'s channel: owner 7, staff 127, a friend of the owner their given rank (0 = friend), else guest. */
    fun rankOf(owner: String, player: Player): Int {
        if (key(player.username) == key(owner)) return FriendsChatRank.OWNER
        if (player.privilege.id >= 2) return FriendsChatRank.STAFF
        val s = settingsOf(owner) ?: return FriendsChatRank.GUEST
        val friend = key(player.username)
        val ownerOnline = player.world.getPlayerForName(Misc.formatForDisplay(owner))
        val isFriend =
            if (ownerOnline != null) ownerOnline.friends.any { key(it) == friend } else friend in s.friends
        if (!isFriend) return FriendsChatRank.GUEST
        return s.ranks[friend] ?: FriendsChatRank.FRIEND
    }

    /** Keeps the owner's friend snapshot current (login, friend added/removed). */
    fun syncFriends(owner: Player) {
        val s = settingsOf(owner.username) ?: return
        s.friends = owner.friends.map { key(it) }.toMutableSet()
        s.ranks.keys.retainAll(s.friends)
        save()
        channels[key(owner.username)]?.let { broadcastChannelState(it) }
    }

    /** The live channel [owner] runs, if anybody is in it. */
    fun channelOwnedBy(owner: String): FriendsChatChannel? = channels[key(owner)]

    fun channelOf(player: Player): FriendsChatChannel? = channels.values.firstOrNull { it.contains(player) }

    /** Moves [player] into [ownerName]'s channel. @return true when the player is now in that channel. */
    fun join(
        player: Player,
        ownerName: String,
    ): Boolean {
        val key = key(ownerName)
        if (key.isBlank()) return false
        val current = channelOf(player)
        if (current != null && key(current.owner) == key) return true
        val s = settingsOf(ownerName)
        if (s?.prefix == null) {
            player.writeMessage("The channel you tried to join does not exist.")
            return false
        }
        bans[key]?.let { banned ->
            val until = banned[key(player.username)]
            if (until != null && until > System.currentTimeMillis()) {
                player.writeMessage("You are temporarily banned from this friends chat channel.")
                return false
            }
        }
        if (rankOf(ownerName, player) < s.enterRank) {
            player.writeMessage("You do not have a high enough rank to join this friends chat channel.")
            return false
        }
        val existing = channels[key]
        if (existing != null && existing.size() >= MAX_MEMBERS) {
            player.writeMessage("The channel is full.")
            return false
        }
        leave(player)
        val channel = channels.getOrPut(key) { FriendsChatChannel(Misc.formatForDisplay(ownerName), this) }
        channel.add(player)
        broadcastChannelState(channel)
        return true
    }

    fun leave(
        player: Player,
        notifyLeaver: Boolean = true,
    ) {
        val channel = channelOf(player) ?: return
        channel.remove(player)
        if (notifyLeaver) player.write(UpdateFriendChatChannelFullMessage(channel = null))
        if (channel.isEmpty()) channels.remove(key(channel.owner)) else broadcastChannelState(channel)
    }

    /** ClientProt CLAN_KICKUSER: [kicker] kicks [targetName] from the channel [kicker] is in, with a one-hour ban. */
    fun kick(
        kicker: Player,
        targetName: String,
    ) {
        val channel = channelOf(kicker) ?: return
        val target = channel.members().firstOrNull { key(it.username) == key(targetName) }
        val kickerRank = channel.rankOf(kicker)
        if (kickerRank < channel.kickRank) {
            kicker.writeMessage("You do not have a high enough rank to kick in this friends chat channel.")
            return
        }
        if (target == null) {
            kicker.writeMessage("$targetName is not in this friends chat channel.")
            return
        }
        if (channel.rankOf(target) >= kickerRank) {
            kicker.writeMessage("You cannot kick a player of equal or higher rank.")
            return
        }
        bans.getOrPut(key(channel.owner)) { ConcurrentHashMap() }[key(target.username)] = System.currentTimeMillis() + KICK_BAN_MILLIS
        channel.members().forEach { it.writeMessage("Attempting to kick/ban ${Misc.formatForDisplay(target.username)} from this friends chat channel...") }
        leave(target)
        target.writeMessage("You have been kicked from the channel.")
    }

    /** Friends Chat Setup: a new prefix ([prefix] null = disable the channel and empty it). */
    fun setPrefix(owner: Player, prefix: String?) {
        val s = settingsFor(owner)
        s.prefix = prefix
        s.friends = owner.friends.map { key(it) }.toMutableSet()
        save()
        val channel = channels[key(owner.username)] ?: return
        if (prefix == null) {
            channel.members().forEach { member ->
                leave(member)
                if (member !== owner) member.writeMessage("You have been removed from this channel.")
            }
        } else {
            broadcastChannelState(channel)
        }
    }

    fun setEnterRank(owner: Player, rank: Int) = update(owner) { enterRank = rank }

    fun setTalkRank(owner: Player, rank: Int) = update(owner) { talkRank = rank }

    fun setKickRank(owner: Player, rank: Int) = update(owner) { kickRank = rank }

    fun setLootShareRank(owner: Player, rank: Int) = update(owner) { lootShareRank = rank }

    fun setCoinShare(owner: Player, on: Boolean) = update(owner) { coinShare = on }

    /** ClientProt FRIEND_SETRANK: [owner] gives [friend] a rank (0 = plain friend .. 6 = General). */
    fun setFriendRank(owner: Player, friend: String, rank: Int) {
        if (owner.friends.none { key(it) == key(friend) }) return
        update(owner) {
            if (rank <= FriendsChatRank.FRIEND) ranks.remove(key(friend)) else ranks[key(friend)] = rank.coerceAtMost(FriendsChatRank.GENERAL)
        }
    }

    private fun update(owner: Player, change: FriendsChatSettings.() -> Unit) {
        val s = settingsFor(owner)
        s.change()
        s.friends = owner.friends.map { key(it) }.toMutableSet()
        save()
        channels[key(owner.username)]?.let { broadcastChannelState(it) }
    }

    fun broadcastChannelState(channel: FriendsChatChannel) {
        channel.members().forEach { it.write(UpdateFriendChatChannelFullMessage(channel)) }
    }

    fun talk(
        world: World,
        sender: Player,
        text: String,
    ): Boolean {
        val channel = channelOf(sender) ?: return false
        if (!mayTalk(channel, sender)) return true
        val id = nextMessageId()
        val name = Misc.formatForDisplay(sender.username)
        channel.members().forEach { member ->
            member.write(MessageFriendChannelMessage(world = world, sender = name, channel = channel.name, rank = crownOf(sender), id = id, text = text))
        }
        return true
    }

    private fun mayTalk(channel: FriendsChatChannel, sender: Player): Boolean {
        val required = settingsOf(channel.owner)?.talkRank ?: FriendsChatRank.GUEST
        if (channel.rankOf(sender) < required) {
            sender.writeMessage("You do not have a high enough rank to talk in this friends chat channel.")
            return false
        }
        return true
    }

    /**
     * The sender crown of a channel line. The client draws `<img=0>` for 1 and `<img=1>` for 2/3 - the player-moderator and staff
     * crowns - so this is the sender's privilege, never their channel rank (the old code gave every owner a staff crown).
     */
    fun crownOf(sender: Player): Int = sender.privilege.id.coerceIn(0, 2)

    fun talkQuickChat(
        world: World,
        sender: Player,
        payload: ByteArray,
    ): Boolean {
        val channel = channelOf(sender) ?: return false
        if (!mayTalk(channel, sender)) return true
        val id = nextMessageId()
        val body =
            gg.rsmod.game.message.handler.packetBody {
                put(gg.rsmod.net.packet.DataType.BYTE, 0)
                putString(Misc.formatForDisplay(sender.username))
                put(gg.rsmod.net.packet.DataType.LONG, gg.rsmod.util.Base37.encode(channel.name))
                put(gg.rsmod.net.packet.DataType.SHORT, (id shr 24) and 0xFFFF)
                put(gg.rsmod.net.packet.DataType.TRI_BYTE, id and 0xFFFFFF)
                put(gg.rsmod.net.packet.DataType.BYTE, crownOf(sender))
                putBytes(payload)
            }
        channel.members().forEach { it.write(gg.rsmod.game.message.impl.QuickChatFriendChannelOutMessage(body)) }
        return true
    }

    /** Ids run from 1 (the client's de-duplication ring starts full of zeroes) and wrap at the 40 bits the packet carries. */
    fun nextMessageId(): Long = 1L + (messageIds.getAndIncrement() % MAX_MESSAGE_ID)

    companion object {
        const val SETTINGS_FILE = "data/friends_chat.json"
        const val KICK_BAN_MILLIS = 60 * 60 * 1000L
        /** The client allocates `FriendChatUser[100]` and reads 255 as "discard this packet". */
        const val MAX_MEMBERS = 100
        private const val MAX_MESSAGE_ID = 0xFF_FFFF_FFFFL

        /** A valid channel prefix: 1-12 characters the client's base-37 channel name can hold (letters, digits, spaces). */
        fun validPrefix(text: String): String? {
            val trimmed = text.trim().replace(Regex("\\s+"), " ")
            if (trimmed.isEmpty() || trimmed.length > 12 || !trimmed.all { it.isLetterOrDigit() || it == ' ' }) return null
            return trimmed
        }
    }
}
