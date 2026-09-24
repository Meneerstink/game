package gg.rsmod.plugins.content.mechanics.clan

import gg.rsmod.game.message.impl.ClanChannelFullMessage
import gg.rsmod.game.message.impl.ClanSettingsFullMessage
import gg.rsmod.game.message.impl.MessageClanChannelMessage
import gg.rsmod.game.message.impl.QuickChatClanChannelOutMessage
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.net.packet.DataType
import gg.rsmod.net.packet.GamePacketBuilder
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.util.Misc
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private fun Player.findOnline(name: String): Player? {
    var found: Player? = null
    world.players.forEach { if (it.username.equals(name, ignoreCase = true)) found = it }
    return found
}

/** Public wrapper of [findOnline], reused by full Clan Wars (`ClanWarsMatch`) to look up a challenge target. */
fun Player.findOnlinePlayer(name: String): Player? = findOnline(name)

/**
 * Clan ranks with the rev-667 clan rank values the client's clan tab (1110) and ClanSettings/ClanChannel packets carry
 * (Recruit 0 .. General 5, Admin 100, Deputy owner 125, Owner 126).
 */
enum class ClanRank(val value: Int, val label: String) {
    OWNER(126, "Owner"),
    DEPUTY(125, "Deputy Owner"),
    ADMIN(100, "Admin"),
    GENERAL(5, "General"),
    CAPTAIN(4, "Captain"),
    LIEUTENANT(3, "Lieutenant"),
    SERGEANT(2, "Sergeant"),
    CORPORAL(1, "Corporal"),
    MEMBER(0, "Recruit"),
    ;

    companion object {
        fun of(value: Int): ClanRank = values().firstOrNull { it.value == value } ?: MEMBER
    }
}

data class Clan(
    val name: String,
    val members: MutableMap<String, ClanRank> = ConcurrentHashMap(),
    var talkRank: Int = ClanRank.MEMBER.value,
    var kickRank: Int = ClanRank.ADMIN.value,
    /** Guests (non-members) may listen and talk in the channel. */
    var allowGuests: Boolean = true,
    val creation: Long = System.currentTimeMillis(),
)

/**
 * Clans with a real Clan Chat channel (owner 2026-09-24: "clanchat ... volledig werkend maken"). Before this the clan chat was a
 * `::cc` broadcast of game messages and the Clan Chat tab (1110) stayed empty, because the server never sent the clan packets.
 *
 * Protocol, read off the client's own decoders (`ServerConnectionReader`, `ClanSettings.decode`, `ClanChannel.decode`):
 *  - CLANSETTINGS_FULL (118) `g1 affined` + version-3 settings (display names, members with ranks, talk/kick ranks);
 *  - CLANCHANNEL_FULL (7) `g1 affined` + the channel (clan hash, update number, name, kick/talk rank, online users with rank and
 *    world); a body of only the affined byte clears it;
 *  - MESSAGE_CLANCHANNEL (138) `g1 affined, gjstr name, g2+g3 id, g1 crown, WordPack text` - the client drops a line whose channel
 *    it does not have, so the channel is always sent first.
 * A member's own clan is the *affined* channel ("//" in chat); a clan listened to as a guest is the *listened* channel ("///").
 * The registry stays the flat text file `data/clans.txt` (clan|name|talk|kick|guests, member|clan|name|RANK).
 */
object Clans {
    val CLAN_ATTR = AttributeKey<String>(persistenceKey = "clan_name")

    private val clans = ConcurrentHashMap<String, Clan>()
    private val file = File("data/clans.txt")

    /** Online members / guests currently in each clan's channel, by clan name. */
    private val channelUsers = ConcurrentHashMap<String, MutableSet<Player>>()
    private val guestOf = ConcurrentHashMap<Player, String>()
    private val updateNum = AtomicLong(1)
    private val messageIds = AtomicLong()

    @Volatile private var loaded = false

    private fun load() {
        if (loaded) return
        loaded = true
        if (file.exists()) {
            file.readLines().forEach { line ->
                val parts = line.split("|")
                when (parts.getOrNull(0)) {
                    "clan" -> {
                        val clan = clans.getOrPut(parts[1]) { Clan(parts[1]) }
                        parts.getOrNull(2)?.toIntOrNull()?.let { clan.talkRank = it }
                        parts.getOrNull(3)?.toIntOrNull()?.let { clan.kickRank = it }
                        parts.getOrNull(4)?.let { clan.allowGuests = it != "0" }
                    }
                    "member" -> {
                        val clan = clans.getOrPut(parts[1]) { Clan(parts[1]) }
                        val rank = runCatching { ClanRank.valueOf(parts[3]) }.getOrDefault(ClanRank.MEMBER)
                        clan.members[parts[2]] = rank
                    }
                }
            }
        }
    }

    private fun save() {
        load()
        runCatching {
            val lines = ArrayList<String>()
            clans.values.forEach { clan ->
                lines.add("clan|${clan.name}|${clan.talkRank}|${clan.kickRank}|${if (clan.allowGuests) 1 else 0}")
                clan.members.forEach { (member, rank) -> lines.add("member|${clan.name}|$member|$rank") }
            }
            gg.rsmod.util.io.AtomicFiles.writeText(file, lines.joinToString("\n"))
        }
    }

    private fun clanFor(player: Player): Clan? {
        load()
        return player.attr[CLAN_ATTR]?.let { clans[it] }
    }

    private fun rankOf(clan: Clan, player: Player): Int =
        clan.members.entries.firstOrNull { it.key.equals(player.username, ignoreCase = true) }?.value?.value ?: GUEST_RANK

    fun create(
        owner: Player,
        name: String,
    ): Boolean {
        load()
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 20 || !clean.all { it.isLetterOrDigit() || it == ' ' }) {
            owner.filterableMessage("A clan name is 1 to 20 letters, numbers or spaces.")
            return false
        }
        if (clans.keys.any { it.equals(clean, ignoreCase = true) }) {
            owner.filterableMessage("A clan named '$clean' already exists.")
            return false
        }
        if (owner.attr[CLAN_ATTR] != null) {
            owner.filterableMessage("You're already in a clan.")
            return false
        }
        val clan = Clan(clean)
        clan.members[owner.username] = ClanRank.OWNER
        clans[clean] = clan
        owner.attr[CLAN_ATTR] = clean
        save()
        owner.filterableMessage("You found the clan '$clean'.")
        connect(owner)
        return true
    }

    fun invite(
        inviter: Player,
        target: Player,
    ) {
        val clan = clanFor(inviter)
        if (clan == null) {
            inviter.filterableMessage("You're not in a clan.")
            return
        }
        if (rankOf(clan, inviter) < ClanRank.ADMIN.value) {
            inviter.filterableMessage("Only the clan owner, a deputy or an admin can invite.")
            return
        }
        if (target.attr[CLAN_ATTR] != null) {
            inviter.filterableMessage("${target.username} is already in a clan.")
            return
        }
        clan.members[target.username] = ClanRank.MEMBER
        target.attr[CLAN_ATTR] = clan.name
        save()
        stopListening(target)
        target.filterableMessage("You have joined the clan '${clan.name}'.")
        systemMessage(clan, "${Misc.formatForDisplay(target.username)} has joined the clan.")
        connect(target)
        refreshSettings(clan)
    }

    fun leave(player: Player) {
        val clan = clanFor(player) ?: return
        val wasOwner = rankOf(clan, player) == ClanRank.OWNER.value
        clan.members.keys.removeIf { it.equals(player.username, ignoreCase = true) }
        player.attr.remove(CLAN_ATTR)
        disconnect(player, clan, clear = true)
        if (clan.members.isEmpty()) {
            clans.remove(clan.name)
        } else if (wasOwner) {
            // The highest-ranked remaining member takes over, as a clan never stays without an owner.
            val heir = clan.members.maxByOrNull { it.value.value }!!.key
            clan.members[heir] = ClanRank.OWNER
            systemMessage(clan, "${Misc.formatForDisplay(heir)} is now the owner of the clan.")
        }
        save()
        player.filterableMessage("You have left the clan '${clan.name}'.")
        systemMessage(clan, "${Misc.formatForDisplay(player.username)} has left the clan.")
        refreshSettings(clan)
    }

    /** Owner/deputy/admin set a member's rank (below their own). */
    fun setRank(
        setter: Player,
        memberName: String,
        rank: ClanRank,
    ) {
        val clan = clanFor(setter) ?: return setter.filterableMessage("You're not in a clan.")
        val setterRank = rankOf(clan, setter)
        val entry = clan.members.entries.firstOrNull { it.key.equals(memberName, ignoreCase = true) }
        when {
            entry == null -> setter.filterableMessage("$memberName is not in your clan.")
            setterRank < ClanRank.ADMIN.value || entry.value.value >= setterRank || rank.value >= setterRank ->
                setter.filterableMessage("You can only change the rank of members below your own rank, up to below your own rank.")
            else -> {
                clan.members[entry.key] = rank
                save()
                systemMessage(clan, "${Misc.formatForDisplay(entry.key)} is now a ${rank.label}.")
                refreshSettings(clan)
                refreshChannel(clan)
            }
        }
    }

    /** Owner/deputy set who may talk (a clan rank value) and who may kick guests. */
    fun setTalkRank(owner: Player, rank: ClanRank) = updateSettings(owner) { talkRank = rank.value }

    fun setKickRank(owner: Player, rank: ClanRank) = updateSettings(owner) { kickRank = rank.value }

    fun setAllowGuests(owner: Player, allow: Boolean) = updateSettings(owner) { allowGuests = allow }

    private fun updateSettings(owner: Player, change: Clan.() -> Unit) {
        val clan = clanFor(owner) ?: return owner.filterableMessage("You're not in a clan.")
        if (rankOf(clan, owner) < ClanRank.DEPUTY.value) return owner.filterableMessage("Only the clan owner or a deputy can change the clan settings.")
        clan.change()
        save()
        refreshSettings(clan)
        refreshChannel(clan)
    }

    /** Login / "Join Clan Channel": the member's own clan channel and settings. */
    fun connect(player: Player) {
        val clan = clanFor(player) ?: return
        channelUsers.getOrPut(clan.name) { ConcurrentHashMap.newKeySet() }.add(player)
        player.write(ClanSettingsFullMessage(settingsBody(clan, affined = true)))
        refreshChannel(clan)
    }

    /** "Join Clan Channel" as a guest of another clan (the listened channel). */
    fun listen(player: Player, clanName: String) {
        load()
        val clan = clans.values.firstOrNull { it.name.equals(clanName.trim(), ignoreCase = true) }
        when {
            clan == null -> player.filterableMessage("That clan does not exist.")
            clan.name == player.attr[CLAN_ATTR] -> connect(player)
            !clan.allowGuests -> player.filterableMessage("That clan does not allow guests in its channel.")
            else -> {
                stopListening(player)
                guestOf[player] = clan.name
                channelUsers.getOrPut(clan.name) { ConcurrentHashMap.newKeySet() }.add(player)
                player.write(ClanSettingsFullMessage(settingsBody(clan, affined = false)))
                refreshChannel(clan)
                player.filterableMessage("Now talking in the clan channel of ${clan.name} as a guest. To talk, start each line with ///.")
            }
        }
    }

    fun stopListening(player: Player) {
        val clanName = guestOf.remove(player) ?: return
        val clan = clans[clanName]
        channelUsers[clanName]?.remove(player)
        player.write(ClanChannelFullMessage(byteArrayOf(0)))
        player.write(ClanSettingsFullMessage(byteArrayOf(0)))
        clan?.let { refreshChannel(it) }
    }

    /** Logout: leave both channels without writing to the closing session. */
    fun onLogout(player: Player) {
        guestOf.remove(player)?.let { name -> channelUsers[name]?.remove(player); clans[name]?.let { refreshChannel(it, except = player) } }
        val clan = clanFor(player) ?: return
        channelUsers[clan.name]?.remove(player)
        refreshChannel(clan, except = player)
    }

    private fun disconnect(player: Player, clan: Clan, clear: Boolean) {
        channelUsers[clan.name]?.remove(player)
        if (clear) {
            player.write(ClanChannelFullMessage(byteArrayOf(1)))
            player.write(ClanSettingsFullMessage(byteArrayOf(1)))
        }
        refreshChannel(clan)
    }

    /** "//" (affined) or "///" (guest) chat. @return true when handled (sent or refused with a reason). */
    fun talk(sender: Player, text: String, guest: Boolean): Boolean {
        load()
        val clan = (if (guest) guestOf[sender]?.let { clans[it] } else clanFor(sender)) ?: return false
        if (channelUsers[clan.name]?.contains(sender) != true) return false
        if (rankOf(clan, sender) < clan.talkRank && !(guest && clan.allowGuests)) {
            sender.filterableMessage("You do not have a high enough rank to talk in this clan channel.")
            return true
        }
        val id = 1L + (messageIds.getAndIncrement() % 0xFF_FFFF_FFFFL)
        val name = Misc.formatForDisplay(sender.username)
        val compressed = ByteArray(256)
        val length = sender.world.huffman.compress(text, compressed)
        channelUsers[clan.name]?.forEach { member ->
            val affined = member.attr[CLAN_ATTR] == clan.name
            member.write(
                MessageClanChannelMessage(
                    body {
                        put(DataType.BYTE, if (affined) 1 else 0)
                        putString(name)
                        put(DataType.SHORT, (id shr 24) and 0xFFFF)
                        put(DataType.TRI_BYTE, id and 0xFFFFFF)
                        put(DataType.BYTE, sender.privilege.id.coerceIn(0, 2))
                        putSmart(text.length)
                        putBytes(compressed, 0, length)
                    },
                ),
            )
        }
        return true
    }

    fun talkQuickChat(sender: Player, payload: ByteArray): Boolean {
        val clan = clanFor(sender) ?: return false
        if (channelUsers[clan.name]?.contains(sender) != true) return false
        val id = 1L + (messageIds.getAndIncrement() % 0xFF_FFFF_FFFFL)
        val name = Misc.formatForDisplay(sender.username)
        channelUsers[clan.name]?.forEach { member ->
            member.write(
                QuickChatClanChannelOutMessage(
                    body {
                        put(DataType.BYTE, if (member.attr[CLAN_ATTR] == clan.name) 1 else 0)
                        putString(name)
                        put(DataType.SHORT, (id shr 24) and 0xFFFF)
                        put(DataType.TRI_BYTE, id and 0xFFFFFF)
                        put(DataType.BYTE, sender.privilege.id.coerceIn(0, 2))
                        putBytes(payload)
                    },
                ),
            )
        }
        return true
    }

    /** ClientProt CLANCHANNEL_KICKUSER: a guest removed from the channel by a member of kick rank. */
    fun kickGuest(kicker: Player, affined: Boolean, guestName: String) {
        val clan = (if (affined) clanFor(kicker) else guestOf[kicker]?.let { clans[it] }) ?: return
        if (rankOf(clan, kicker) < clan.kickRank) return kicker.filterableMessage("You do not have a high enough rank to kick from this clan channel.")
        val guest = channelUsers[clan.name]?.firstOrNull { it.username.equals(guestName, ignoreCase = true) && guestOf[it] == clan.name } ?: return
        stopListening(guest)
        guest.filterableMessage("You have been kicked from the clan channel.")
    }

    /** The old `::cc` command, kept: it now talks in the real channel. */
    fun chat(
        sender: Player,
        message: String,
    ) {
        if (!talk(sender, message, guest = false)) sender.filterableMessage("You're not in a clan.")
    }

    fun sameClan(
        a: Player,
        b: Player,
    ): Boolean {
        val clanA = a.attr[CLAN_ATTR] ?: return false
        return clanA == b.attr[CLAN_ATTR]
    }

    fun clanOf(player: Player): String? = player.attr[CLAN_ATTR]

    fun exists(name: String): Boolean {
        load()
        return clans.containsKey(name)
    }

    fun onlineMembers(
        anyOnlinePlayer: Player,
        name: String,
    ): List<Player> {
        load()
        val clan = clans[name] ?: return emptyList()
        return clan.members.keys.mapNotNull { anyOnlinePlayer.findOnline(it) }
    }

    fun details(player: Player): List<String> {
        val clan = clanFor(player) ?: return listOf("You're not in a clan.")
        val online = channelUsers[clan.name]?.count { it.attr[CLAN_ATTR] == clan.name } ?: 0
        return listOf(
            "Clan: ${clan.name} - ${clan.members.size} members, $online online.",
            "Your rank: ${ClanRank.of(rankOf(clan, player)).label}. Talk: ${ClanRank.of(clan.talkRank).label}+, kick: ${ClanRank.of(clan.kickRank).label}+, guests ${if (clan.allowGuests) "allowed" else "not allowed"}.",
        )
    }

    private fun refreshSettings(clan: Clan) {
        channelUsers[clan.name]?.forEach { member ->
            member.write(ClanSettingsFullMessage(settingsBody(clan, affined = member.attr[CLAN_ATTR] == clan.name)))
        }
    }

    private fun refreshChannel(clan: Clan, except: Player? = null) {
        val users = channelUsers[clan.name]?.filter { it !== except && it.isOnline } ?: return
        val version = updateNum.incrementAndGet()
        users.forEach { member ->
            member.write(ClanChannelFullMessage(channelBody(clan, users, affined = member.attr[CLAN_ATTR] == clan.name, version)))
        }
    }

    /** ClanSettings version 3: flags 2 (display names), no hashes, members with rank and extra info, no bans, no extra settings. */
    private fun settingsBody(clan: Clan, affined: Boolean): ByteArray = encodeSettings(clan, affined, updateNum.incrementAndGet().toInt())

    internal fun encodeSettings(clan: Clan, affined: Boolean, update: Int): ByteArray =
        body {
            put(DataType.BYTE, if (affined) 1 else 0)
            put(DataType.BYTE, 3)
            put(DataType.BYTE, 2)
            put(DataType.INT, update)
            put(DataType.INT, 0)
            put(DataType.SHORT, clan.members.size)
            put(DataType.BYTE, 0)
            putString(clan.name)
            put(DataType.BYTE, if (clan.allowGuests) 1 else 0)
            put(DataType.BYTE, clan.talkRank)
            put(DataType.BYTE, clan.kickRank)
            put(DataType.BYTE, ClanRank.OWNER.value)
            put(DataType.BYTE, 0)
            clan.members.forEach { (name, rank) ->
                putString(Misc.formatForDisplay(name))
                put(DataType.BYTE, rank.value)
                put(DataType.INT, 0)
            }
            put(DataType.SHORT, 0)
        }

    /** ClanChannel: flags 2 (display names), clan hash, update number, name, kick/talk rank, online users (guests rank -1). */
    private fun channelBody(clan: Clan, users: List<Player>, affined: Boolean, version: Long): ByteArray =
        encodeChannel(clan, users.map { Misc.formatForDisplay(it.username) to rankOf(clan, it) }, affined, version)

    internal fun encodeChannel(clan: Clan, users: List<Pair<String, Int>>, affined: Boolean, version: Long): ByteArray =
        body {
            put(DataType.BYTE, if (affined) 1 else 0)
            put(DataType.BYTE, 2)
            put(DataType.LONG, clan.name.lowercase().hashCode().toLong() and 0xFFFFFFFFL)
            put(DataType.LONG, version)
            putString(clan.name)
            put(DataType.BYTE, 0)
            put(DataType.BYTE, clan.kickRank)
            put(DataType.BYTE, clan.talkRank)
            put(DataType.SHORT, users.size)
            users.forEach { (name, rank) ->
                putString(name)
                put(DataType.BYTE, rank)
                put(DataType.SHORT, 1)
            }
        }

    private fun systemMessage(clan: Clan, text: String) {
        channelUsers[clan.name]?.forEach { if (it.attr[CLAN_ATTR] == clan.name) it.filterableMessage("[${clan.name}] $text") }
    }

    private inline fun body(build: GamePacketBuilder.() -> Unit): ByteArray {
        val buf = GamePacketBuilder()
        buf.build()
        val data = ByteArray(buf.byteBuf.readableBytes())
        buf.byteBuf.readBytes(data)
        return data
    }

    /** The rank value the client gives a guest in a clan channel. */
    const val GUEST_RANK = -1
}
