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
    // Cache enum 3715 (clan rank names): 101 Organiser, 102 Coordinator, 103 Overseer sit between Admin and Deputy Owner.
    OVERSEER(103, "Overseer"),
    COORDINATOR(102, "Coordinator"),
    ORGANISER(101, "Organiser"),
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
) {
    /**
     * Clan Settings (interface 1096) state, laid out like the 667 Novite donor's `Clan` (novite/rs/game/player/clans/Clan.java) and sent
     * as ClanSettings extra settings (VarClan 0 time zone, 1 motto, 2 forum thread, 3 recruiting/clan time/home world/flag).
     */
    var guestsCanTalk: Boolean = false
    var coinShare: Boolean = false
    val bans: MutableSet<String> = ConcurrentHashMap.newKeySet()
    var motto: String? = null
    var keywords: String? = null
    var threadId: String? = null
    /** Minutes from UTC, -720..720 in steps of 30 (cache enum 3711). */
    var timeZone: Int = 0
    var recruiting: Boolean = false
    var clanTime: Boolean = false
    var worldId: Int = 0
    var flag: Int = 0
    val jobs: MutableMap<String, Int> = ConcurrentHashMap()
    /** Per member: [ClanMemberFlag] bits (mute, keep / citadel / island bans). */
    val memberFlags: MutableMap<String, Int> = ConcurrentHashMap()
    val joined: MutableMap<String, Long> = ConcurrentHashMap()
    /** Rank value -> bit mask over [ClanPermission] (the Permissions page of 1096). */
    val permissions: MutableMap<Int, Int> = ConcurrentHashMap()

    fun key(name: String): String? = members.keys.firstOrNull { it.equals(name, ignoreCase = true) }

    fun permissionMask(rank: Int): Int = permissions[rank] ?: ClanPermission.defaultMask(rank)
}

/** Member flags shown on the Clanmates page of 1096 (Novite `ClanMember`: mute, ban from keep / citadel / island). */
object ClanMemberFlag {
    const val MUTED = 1
    const val BAN_KEEP = 2
    const val BAN_CITADEL = 4
    const val BAN_ISLAND = 8
}

/**
 * The Permissions page of 1096, in the order of its rows: each row's tick is client var (varc) [varc] shown on component [tick] and
 * toggled by clicking [row] (cache scripts 5135 / 5140). Talk and kick are rank thresholds (varc 1571 / 1570), not toggles.
 */
enum class ClanPermission(val varc: Int, val row: Int) {
    RECRUIT(1576, 508),
    LOCK_KEEP(1572, 559),
    LOCK_CITADEL(1574, 572),
    SIGNPOST(1584, 606),
    NOTICEBOARD(1583, 617),
    UPGRADE_BUILDING(1586, 628),
    DOWNGRADE_BUILDING(1587, 639),
    EDIT_BATTLEFIELD(1585, 650),
    TRANSFER_RESOURCES(1588, 661),
    BATTLEFIELD_EVENT(1577, 673),
    RATED_CLAN_WAR(1578, 684),
    CLAN_VOTE(1579, 696),
    PRIVATE_MEETING(1580, 709),
    PARTY_ROOM(1581, 721),
    GATHERING_OBJECTIVE(1589, 733),
    BUILD_TICK(1590, 745),
    ENTER_KEEP(1573, 764),
    ENTER_CITADEL(1575, 777),
    CITADEL_LANGUAGE(1649, 807),
    THEATRE(1582, 818),
    ;

    val bit: Int get() = 1 shl ordinal

    companion object {
        const val TALK_VARC = 1571
        const val TALK_ROW = 583
        const val KICK_VARC = 1570
        const val KICK_ROW = 595

        /** Admins and above may do everything; lower ranks may only enter the citadel and keep. */
        fun defaultMask(rank: Int): Int =
            if (rank >= ClanRank.ADMIN.value) values().fold(0) { m, p -> m or p.bit } else ENTER_KEEP.bit or ENTER_CITADEL.bit
    }
}

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
                        parts.getOrNull(4)?.toIntOrNull()?.let { clan.jobs[parts[2]] = it }
                        parts.getOrNull(5)?.toIntOrNull()?.let { clan.memberFlags[parts[2]] = it }
                        parts.getOrNull(6)?.toLongOrNull()?.let { clan.joined[parts[2]] = it }
                    }
                    "ban" -> clans[parts[1]]?.bans?.add(parts[2])
                    "perm" -> clans[parts[1]]?.let { c -> parts.getOrNull(3)?.toIntOrNull()?.let { c.permissions[parts[2].toInt()] = it } }
                    "set" -> clans[parts[1]]?.let { c -> applySetting(c, parts[2], parts.drop(3).joinToString("|")) }
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
                clan.members.forEach { (member, rank) ->
                    lines.add("member|${clan.name}|$member|$rank|${clan.jobs[member] ?: 0}|${clan.memberFlags[member] ?: 0}|${clan.joined[member] ?: 0}")
                }
                clan.bans.forEach { lines.add("ban|${clan.name}|$it") }
                clan.permissions.forEach { (rank, mask) -> lines.add("perm|${clan.name}|$rank|$mask") }
                settingsOf(clan).forEach { (key, value) -> lines.add("set|${clan.name}|$key|$value") }
            }
            gg.rsmod.util.io.AtomicFiles.writeText(file, lines.joinToString("\n"))
        }
    }

    /** Scalar settings persisted as `set|clan|key|value` lines (absent = default). */
    private fun settingsOf(clan: Clan): List<Pair<String, String>> =
        listOfNotNull(
            "guestsCanTalk" to (if (clan.guestsCanTalk) "1" else "0"),
            "coinShare" to (if (clan.coinShare) "1" else "0"),
            clan.motto?.let { "motto" to it },
            clan.keywords?.let { "keywords" to it },
            clan.threadId?.let { "thread" to it },
            "timeZone" to clan.timeZone.toString(),
            "recruiting" to (if (clan.recruiting) "1" else "0"),
            "clanTime" to (if (clan.clanTime) "1" else "0"),
            "world" to clan.worldId.toString(),
            "flag" to clan.flag.toString(),
        )

    private fun applySetting(clan: Clan, key: String, value: String) {
        when (key) {
            "guestsCanTalk" -> clan.guestsCanTalk = value == "1"
            "coinShare" -> clan.coinShare = value == "1"
            "motto" -> clan.motto = value
            "keywords" -> clan.keywords = value
            "thread" -> clan.threadId = value
            "timeZone" -> value.toIntOrNull()?.let { clan.timeZone = it }
            "recruiting" -> clan.recruiting = value == "1"
            "clanTime" -> clan.clanTime = value == "1"
            "world" -> value.toIntOrNull()?.let { clan.worldId = it }
            "flag" -> value.toIntOrNull()?.let { clan.flag = it }
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
        clan.joined[owner.username] = System.currentTimeMillis()
        clans[clean] = clan
        owner.attr[CLAN_ATTR] = clean
        save()
        owner.filterableMessage("You found the clan '$clean'.")
        connect(owner)
        refreshClanmateDots(owner)
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
        if (!mayRecruit(clan, inviter)) {
            inviter.filterableMessage("You don't have permission to invite.")
            return
        }
        if (clan.bans.any { it.equals(target.username, ignoreCase = true) }) {
            inviter.filterableMessage("This player has been banned from this clan.")
            return
        }
        if (target.attr[CLAN_ATTR] != null) {
            inviter.filterableMessage("${target.username} is already in a clan.")
            return
        }
        clan.members[target.username] = ClanRank.MEMBER
        clan.joined[target.username] = System.currentTimeMillis()
        target.attr[CLAN_ATTR] = clan.name
        save()
        stopListening(target)
        target.filterableMessage("You have joined the clan '${clan.name}'.")
        systemMessage(clan, "${Misc.formatForDisplay(target.username)} has joined the clan.")
        connect(target)
        refreshSettings(clan)
        refreshClanmateDots(target)
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
        refreshClanmateDots(player)
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
            !clan.allowGuests -> player.filterableMessage("This clan only allows clanmates to join their channel.")
            clan.bans.any { it.equals(player.username, ignoreCase = true) } -> player.filterableMessage("You have been banned from this channel.")
            (tempBans[clan.name]?.get(player.username.lowercase()) ?: 0L) > System.currentTimeMillis() ->
                player.filterableMessage("You have been banned from this channel.")
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
        val clan = talkChannel(sender, guest) ?: return false
        if (!mayTalk(clan, sender)) return true
        val id = 1L + (messageIds.getAndIncrement() % 0xFF_FFFF_FFFFL)
        val name = Misc.formatForDisplay(sender.username)
        val compressed = ByteArray(256)
        val length = sender.world.huffman.compress(text, compressed)
        listeners(clan, sender).forEach { member ->
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

    /** Quick chat into the own clan channel (quick-chat channel 2) or the guest channel (3): the same mute / rank rules as typed chat. */
    fun talkQuickChat(sender: Player, payload: ByteArray, guest: Boolean = false): Boolean {
        val clan = talkChannel(sender, guest) ?: return false
        if (!mayTalk(clan, sender)) return true
        val id = 1L + (messageIds.getAndIncrement() % 0xFF_FFFF_FFFFL)
        val name = Misc.formatForDisplay(sender.username)
        listeners(clan, sender).forEach { member ->
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

    /** The channel [sender] talks into ("//" own clan, "///" guest), or null when they are not in it. */
    private fun talkChannel(sender: Player, guest: Boolean): Clan? {
        load()
        val clan = (if (guest) guestOf[sender]?.let { clans[it] } else clanFor(sender)) ?: return null
        return clan.takeIf { channelUsers[it.name]?.contains(sender) == true }
    }

    /** Mute and talk rank; tells the sender why not. */
    private fun mayTalk(clan: Clan, sender: Player): Boolean {
        val rank = rankOf(clan, sender)
        if (rank != GUEST_RANK && (clan.memberFlags[clan.key(sender.username)] ?: 0) and ClanMemberFlag.MUTED != 0) {
            sender.filterableMessage("You have been muted in this clan channel.")
            return false
        }
        val allowed = if (rank == GUEST_RANK) clan.guestsCanTalk else clan.guestsCanTalk || rank >= clan.talkRank
        if (!allowed) sender.filterableMessage("You do not have a high enough rank to talk in this clan channel.")
        return allowed
    }

    /** Everybody in the channel except those who ignore [sender]. */
    private fun listeners(clan: Clan, sender: Player): List<Player> =
        channelUsers[clan.name]?.filterNot { gg.rsmod.game.model.social.FriendsChat.ignores(it, sender) } ?: emptyList()

    /** ClientProt CLANCHANNEL_KICKUSER: a guest removed from the channel by a member of kick rank. */
    fun kickGuest(kicker: Player, affined: Boolean, guestName: String) {
        val clan = (if (affined) clanFor(kicker) else guestOf[kicker]?.let { clans[it] }) ?: return
        if (rankOf(clan, kicker) < clan.kickRank) return kicker.filterableMessage("You do not have a high enough rank to kick from this clan channel.")
        val guest = channelUsers[clan.name]?.firstOrNull { it.username.equals(guestName, ignoreCase = true) && guestOf[it] == clan.name } ?: return
        // "Temporary kick/ban": the guest may not rejoin for an hour (Novite ClansManager.kickPlayerFromChat / connectToClan: 3600000 ms).
        tempBans.getOrPut(clan.name) { ConcurrentHashMap() }[guest.username.lowercase()] = System.currentTimeMillis() + TEMP_BAN_MILLIS
        stopListening(guest)
        guest.filterableMessage("You have been kicked from the guest clan chat channel.")
        kicker.filterableMessage("You have kicked ${Misc.formatForDisplay(guest.username)} from the clan chat channel.")
    }

    private val tempBans = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()
    private const val TEMP_BAN_MILLIS = 60 * 60 * 1000L

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
            put(DataType.BYTE, clan.bans.size.coerceAtMost(255))
            putString(clan.name)
            put(DataType.BYTE, if (clan.allowGuests) 1 else 0)
            // rankTalk -1 = "guests (and all ranks) can talk" (cache script 4295 ticks 1096:93 on ACTIVECLANSETTINGS_GETRANKTALK == -1).
            put(DataType.BYTE, talkRankOf(clan))
            put(DataType.BYTE, clan.kickRank)
            put(DataType.BYTE, ClanRank.OWNER.value)
            put(DataType.BYTE, if (clan.coinShare) 1 else 0)
            orderedMembers(clan).forEach { (name, rank) ->
                putString(Misc.formatForDisplay(name))
                put(DataType.BYTE, rank.value)
                put(DataType.INT, 0)
            }
            clan.bans.take(255).forEach { putString(Misc.formatForDisplay(it)) }
            // Extra settings `g4 id | type << 30` (0 int, 1 long, 2 string) - Novite ClansManager.generateClanSettingsDataBlock:
            // 0 time zone (minutes), 1 motto, 2 forum thread (base-36 long), 3 recruiting | clan time << 1 | world << 2 | flag << 10.
            val extras = mutableListOf<Triple<Int, Int, Any>>()
            if (clan.timeZone != 0) extras += Triple(0, 0, clan.timeZone)
            clan.motto?.let { extras += Triple(1, 2, it) }
            clan.threadId?.let { extras += Triple(2, 1, threadIdLong(it)) }
            val bits = (if (clan.recruiting) 1 else 0) or ((if (clan.clanTime) 1 else 0) shl 1) or (clan.worldId shl 2) or (clan.flag shl 10)
            if (bits != 0) extras += Triple(3, 0, bits)
            put(DataType.SHORT, extras.size)
            extras.forEach { (id, type, value) ->
                put(DataType.INT, id or (type shl 30))
                when (value) {
                    is Int -> put(DataType.INT, value)
                    is Long -> put(DataType.LONG, value)
                    is String -> putString(value)
                }
            }
        }

    /** The talk rank the client shows: -1 when guests (and every rank) may talk, else the minimum rank. */
    internal fun talkRankOf(clan: Clan): Int = if (clan.guestsCanTalk) GUEST_RANK else clan.talkRank

    /** Novite `ClansManager.convertToLong`: the forum thread id as a base-36 number (0-9, a-z). */
    internal fun threadIdLong(id: String): Long =
        id.lowercase().fold(0L) { acc, c -> acc * 36 + (if (c.isDigit()) c - '0' else if (c in 'a'..'z') c - 'a' + 10 else 0) }

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
            put(DataType.BYTE, talkRankOf(clan))
            put(DataType.SHORT, users.size)
            users.forEach { (name, rank) ->
                putString(name)
                put(DataType.BYTE, rank)
                put(DataType.SHORT, 1)
            }
        }

    // ---- Clan Chat tab (1110) and Clan Settings (1096) ---------------------------------------------------------------------------

    fun clanOfPlayer(player: Player): Clan? = clanFor(player)

    fun isListening(player: Player): Boolean = guestOf.containsKey(player)

    fun rankOfPlayer(player: Player): Int = clanFor(player)?.let { rankOf(it, player) } ?: GUEST_RANK

    /** Members in the order the ClanSettings packet lists them (by name) - the row index the 1096 member list sends back. */
    fun orderedMembers(clan: Clan): List<Pair<String, ClanRank>> = clan.members.entries.sortedBy { it.key.lowercase() }.map { it.key to it.value }

    /** The Recruit permission of the player's rank (Permissions page) - who may invite. */
    fun mayRecruit(clan: Clan, player: Player): Boolean {
        val rank = rankOf(clan, player)
        return rank >= ClanRank.ADMIN.value || clan.permissionMask(rank) and ClanPermission.RECRUIT.bit != 0
    }

    /** Settings page edits (admins and above, Novite `hasRankToEditSettings`). */
    fun editSettings(player: Player, change: Clan.() -> Unit): Boolean {
        val clan = clanFor(player) ?: return false
        if (rankOf(clan, player) < ClanRank.ADMIN.value) {
            player.filterableMessage("You need to be an admin or above to change the clan settings.")
            return false
        }
        clan.change()
        save()
        refreshSettings(clan)
        refreshChannel(clan)
        return true
    }

    /** Clan Chat tab "Join Clan Channel" for a member: join or leave the own channel. */
    fun toggleOwnChannel(player: Player) {
        val clan = clanFor(player) ?: return
        val users = channelUsers.getOrPut(clan.name) { ConcurrentHashMap.newKeySet() }
        if (player in users) {
            users.remove(player)
            player.write(ClanChannelFullMessage(byteArrayOf(1)))
            refreshChannel(clan)
            player.filterableMessage("You have left your clan's chat channel.")
        } else {
            connect(player)
            player.filterableMessage("Now talking in your clan channel. To talk, start each line of chat with //.")
        }
    }

    /** Add a name to the clan ban list (admins and above); a banned guest in the channel is removed. */
    fun ban(player: Player, name: String) {
        val clan = clanFor(player) ?: return player.filterableMessage("You're not in a clan.")
        val clean = name.trim()
        when {
            rankOf(clan, player) < ClanRank.ADMIN.value -> player.filterableMessage("You must be a clan admin to do that.")
            clean.isEmpty() -> return
            clan.key(clean) != null -> player.filterableMessage("You can't add a member of your clan to the ban list.")
            clan.bans.size >= 100 -> player.filterableMessage("The ban list is full.")
            clan.bans.any { it.equals(clean, ignoreCase = true) } -> player.filterableMessage("$clean is already on the ban list.")
            else -> {
                clan.bans += clean
                save()
                refreshSettings(clan)
                channelUsers[clan.name]?.firstOrNull { it.username.equals(clean, ignoreCase = true) && guestOf[it] == clan.name }?.let {
                    stopListening(it)
                    it.filterableMessage("You have been banned from this channel.")
                }
                player.filterableMessage("${Misc.formatForDisplay(clean)} has been added to the clan ban list.")
            }
        }
    }

    fun unban(player: Player, name: String) {
        val clan = clanFor(player) ?: return player.filterableMessage("You're not in a clan.")
        if (rankOf(clan, player) < ClanRank.ADMIN.value) return player.filterableMessage("You must be a clan admin to do that.")
        if (!clan.bans.removeIf { it.equals(name.trim(), ignoreCase = true) }) return player.filterableMessage("${name.trim()} is not on the ban list.")
        save()
        refreshSettings(clan)
        player.filterableMessage("${Misc.formatForDisplay(name.trim())} has been removed from the clan ban list.")
    }

    /** Clanmates page Save / Kick and the member toggles: [editor] edits [member] (rank below the editor's, admins and above). */
    fun editMember(editor: Player, member: String, rank: ClanRank?, job: Int?, flags: Int?): Boolean {
        val clan = clanFor(editor) ?: return false
        val key = clan.key(member) ?: return false
        val editorRank = rankOf(clan, editor)
        val current = clan.members.getValue(key)
        when {
            editorRank < ClanRank.ADMIN.value -> editor.filterableMessage("You need to be an admin or above to edit clanmates.")
            key.equals(editor.username, ignoreCase = true) && rank != null && rank != current ->
                editor.filterableMessage("You can't change your own rank.")
            !key.equals(editor.username, ignoreCase = true) && current.value >= editorRank ->
                editor.filterableMessage("You can only edit clanmates below your own rank.")
            rank != null && rank != current && (rank.value >= editorRank || rank == ClanRank.OWNER) ->
                editor.filterableMessage("You can only give ranks below your own.")
            else -> {
                rank?.let { clan.members[key] = it }
                job?.let { clan.jobs[key] = it }
                flags?.let { clan.memberFlags[key] = it }
                save()
                if (rank != null && rank != current) systemMessage(clan, "${Misc.formatForDisplay(key)} is now a ${rank.label}.")
                refreshSettings(clan)
                refreshChannel(clan)
                return true
            }
        }
        return false
    }

    fun kickMember(editor: Player, member: String) {
        val clan = clanFor(editor) ?: return
        val key = clan.key(member) ?: return
        val editorRank = rankOf(clan, editor)
        when {
            key.equals(editor.username, ignoreCase = true) -> editor.filterableMessage("You can't kick yourself!")
            clan.members.getValue(key) == ClanRank.OWNER -> editor.filterableMessage("You can't kick the clan owner!")
            editorRank < ClanRank.ADMIN.value || clan.members.getValue(key).value >= editorRank ->
                editor.filterableMessage("You can only kick clanmates below your own rank.")
            else -> {
                clan.members.remove(key)
                clan.jobs.remove(key)
                clan.memberFlags.remove(key)
                clan.joined.remove(key)
                save()
                editor.findOnline(key)?.let { kicked ->
                    kicked.attr.remove(CLAN_ATTR)
                    disconnect(kicked, clan, clear = true)
                    kicked.filterableMessage("You're no longer part of a clan.")
                    refreshClanmateDots(kicked)
                }
                systemMessage(clan, "${Misc.formatForDisplay(key)} has been kicked from the clan.")
                refreshSettings(clan)
            }
        }
    }

    /** Permissions page: [rank]'s [permission] on/off (editor must outrank [rank] and be an admin or above). */
    fun setPermission(editor: Player, rank: Int, permission: ClanPermission, on: Boolean): Boolean {
        val clan = clanFor(editor) ?: return false
        if (!mayEditRank(clan, editor, rank)) return false
        val mask = clan.permissionMask(rank)
        clan.permissions[rank] = if (on) mask or permission.bit else mask and permission.bit.inv()
        save()
        return true
    }

    /** Permissions page "minimum rank to talk / kick" set to [rank]. */
    fun setThreshold(editor: Player, rank: Int, talk: Boolean): Boolean {
        val clan = clanFor(editor) ?: return false
        if (!mayEditRank(clan, editor, rank)) return false
        if (talk) clan.talkRank = rank else clan.kickRank = rank
        save()
        refreshSettings(clan)
        refreshChannel(clan)
        return true
    }

    private fun mayEditRank(clan: Clan, editor: Player, rank: Int): Boolean {
        val editorRank = rankOf(clan, editor)
        if (editorRank < ClanRank.ADMIN.value || rank >= editorRank) {
            editor.filterableMessage("You may not alter this clan setting.")
            return false
        }
        return true
    }

    /**
     * Minimap clanmate dots: the player-update CLANMATE block is per observer, so a membership change re-raises it on the player and on
     * everybody around them (each observer then reads their own value).
     */
    fun refreshClanmateDots(player: Player) {
        player.addBlock(gg.rsmod.game.sync.block.UpdateBlockType.CLANMATE)
        player.world.players.forEach { other ->
            if (other !== player && other.tile.isWithinRadius(player.tile, 32)) other.addBlock(gg.rsmod.game.sync.block.UpdateBlockType.CLANMATE)
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
