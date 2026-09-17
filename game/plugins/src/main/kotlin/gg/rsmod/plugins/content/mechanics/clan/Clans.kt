package gg.rsmod.plugins.content.mechanics.clan

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private fun Player.findOnline(name: String): Player? {
    var found: Player? = null
    world.players.forEach { if (it.username.equals(name, ignoreCase = true)) found = it }
    return found
}

/** Public wrapper of [findOnline], reused by full Clan Wars (`ClanWarsMatch`) to look up a challenge target. */
fun Player.findOnlinePlayer(name: String): Player? = findOnline(name)

enum class ClanRank {
    OWNER,
    DEPUTY,
    MEMBER,
}

data class Clan(
    val name: String,
    val members: MutableMap<String, ClanRank> = ConcurrentHashMap(),
)

/**
 * Basic clan membership, ranks, and team recognition (PROJECT_PLAN SS19: "should arrive
 * relatively early", ranks/permissions/Clan Wars formats left OPEN).
 *
 * ponytail: "clan chat" here is a `::cc` broadcast command to online members, not the real
 * dedicated Clan Chat tab (InterfaceDestination.CLAN_CHAT_TAB / interface 1110 is a known
 * id, but wiring its actual channel packet protocol is net-layer work out of scope for this
 * pass). Registry is a flat "clan|owner" / "member|clan|rank" text file
 * (`data/clans.txt`), fine at the ~20-50 player target - a real DB is a later upgrade.
 * Clan Wars (dedicated safe team-match mode) is NOT implemented this pass - see
 * IMPLEMENTATION_STATUS.md; [sameClan] is provided so Wilderness team-recognition logic
 * (e.g. "don't count this as an unprovoked attack against a clanmate") can already use it.
 */
object Clans {
    val CLAN_ATTR = AttributeKey<String>(persistenceKey = "clan_name")

    private val clans = ConcurrentHashMap<String, Clan>()
    private val file = File("data/clans.txt")

    @Volatile private var loaded = false

    private fun load() {
        if (loaded) return
        loaded = true
        if (file.exists()) {
            file.readLines().forEach { line ->
                val parts = line.split("|")
                when (parts.getOrNull(0)) {
                    "clan" -> clans.getOrPut(parts[1]) { Clan(parts[1]) }
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
                lines.add("clan|${clan.name}")
                clan.members.forEach { (member, rank) -> lines.add("member|${clan.name}|$member|$rank") }
            }
            gg.rsmod.util.io.AtomicFiles.writeText(file, lines.joinToString("\n"))
        }
    }

    fun create(
        owner: Player,
        name: String,
    ): Boolean {
        load()
        if (clans.containsKey(name)) {
            owner.filterableMessage("A clan named '$name' already exists.")
            return false
        }
        if (owner.attr[CLAN_ATTR] != null) {
            owner.filterableMessage("You're already in a clan.")
            return false
        }
        val clan = Clan(name)
        clan.members[owner.username] = ClanRank.OWNER
        clans[name] = clan
        owner.attr[CLAN_ATTR] = name
        save()
        owner.filterableMessage("You found the clan '$name'.")
        return true
    }

    fun invite(
        inviter: Player,
        target: Player,
    ) {
        load()
        val clanName = inviter.attr[CLAN_ATTR]
        val clan = clanName?.let { clans[it] }
        if (clan == null) {
            inviter.filterableMessage("You're not in a clan.")
            return
        }
        val rank = clan.members[inviter.username]
        if (rank != ClanRank.OWNER && rank != ClanRank.DEPUTY) {
            inviter.filterableMessage("Only the clan owner or a deputy can invite.")
            return
        }
        if (target.attr[CLAN_ATTR] != null) {
            inviter.filterableMessage("${target.username} is already in a clan.")
            return
        }
        clan.members[target.username] = ClanRank.MEMBER
        target.attr[CLAN_ATTR] = clan.name
        save()
        target.filterableMessage("You have joined the clan '${clan.name}'.")
        broadcast(clan, "${target.username} has joined the clan.", target)
    }

    fun leave(player: Player) {
        load()
        val clanName = player.attr[CLAN_ATTR] ?: return
        val clan = clans[clanName] ?: return
        clan.members.remove(player.username)
        player.attr.remove(CLAN_ATTR)
        save()
        player.filterableMessage("You have left the clan '$clanName'.")
        broadcast(clan, "${player.username} has left the clan.", player)
    }

    fun chat(
        sender: Player,
        message: String,
    ) {
        load()
        val clanName = sender.attr[CLAN_ATTR]
        val clan = clanName?.let { clans[it] }
        if (clan == null) {
            sender.filterableMessage("You're not in a clan.")
            return
        }
        broadcast(clan, "[${clan.name}] ${sender.username}: $message", sender)
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

    /** Every currently-online member of [name]'s clan - reused by full Clan Wars to build a war's team roster. */
    fun onlineMembers(
        anyOnlinePlayer: Player,
        name: String,
    ): List<Player> {
        load()
        val clan = clans[name] ?: return emptyList()
        return clan.members.keys.mapNotNull { anyOnlinePlayer.findOnline(it) }
    }

    private fun broadcast(
        clan: Clan,
        message: String,
        via: Player,
    ) {
        clan.members.keys.forEach { name -> via.findOnline(name)?.filterableMessage(message) }
    }
}
