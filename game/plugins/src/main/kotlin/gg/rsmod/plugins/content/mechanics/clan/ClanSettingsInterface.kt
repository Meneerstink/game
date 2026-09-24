package gg.rsmod.plugins.content.mechanics.clan

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.util.Misc

/**
 * Clan Settings (interface 1096, owner 2026-09-24: "clansettings fix our clanchat fully"). The interface is server-driven: its cache
 * scripts render the Clanmates list from the ClanSettings packet and everything else from client vars (varc) the server sends, and
 * every click reaches the server as an IF_BUTTON. Component ids are this cache's own (1096 decoded 2026-09-24); the values follow the
 * 667 Novite donor (novite/rs/game/player/clans/ClansManager.java), whose own component ids belong to a different 1096 build.
 *
 * - Clanmates: rows are created by script 4301 under component [MEMBER_ROWS] with the member's packet index as slot ("Show details"
 *   -> script 4305 shows "Loading..." until the details varcs arrive). Details: varcstr 347 name, varc 1500 rank, 1501 job, 1564
 *   muted, 1565 keep ban, 1566 citadel ban, 1567 island ban, 1568 probation (first week). Rank / job dropdown entries are created
 *   under [RANK_OPTIONS] / [JOB_OPTIONS] with the enum key as slot (enum 3715 ranks). [SAVE] commits, [KICK] removes.
 * - Settings: [GUESTS_ENTER], [GUESTS_TALK], [RECRUITING], [CLAN_TIME] toggles; time zone ([TIMEZONE_OPTIONS], slot = enum 3711 key,
 *   minutes = (key - 72) * 10) and home world ([WORLD_OPTIONS], slot = world); motto / keywords / forum thread by input.
 * - Permissions: a rank tab ([RANK_TABS]) sends varc 1569 = rank plus one varc per [ClanPermission] row (script 5135 shows a tick when
 *   the varc is 1); clicking a row toggles it, the talk / kick rows set the minimum rank.
 */
object ClanSettingsInterface {
    const val INTERFACE = 1096
    const val MEMBER_ROWS = 39
    /*
     * Dropdowns (script 4497 -> 4499 -> 4506): the options are created as children of the dropdown component itself (its onLoad
     * passes itself as the list parent) with op1 "Select", child index = enum key. Rank 284 (enum 3714), time zone 248 (enum 3711),
     * home world 298 (enum 3700). The old 276 / 240 / 290 were a border piece, the time-zone open arrow and a border piece, so none of
     * these dropdowns ever reached the server.
     */
    const val RANK_OPTIONS = 284
    const val JOB_OPTIONS = 262
    const val TIMEZONE_OPTIONS = 248
    const val WORLD_OPTIONS = 298
    const val SELECT_FLAG = 371
    const val EDIT_MOTIF = 125
    const val KICK = 327
    const val SAVE = 340
    const val GUESTS_ENTER = 92
    const val GUESTS_TALK = 93
    const val RECRUITING = 94
    const val CLAN_TIME = 95
    const val EDIT_MOTTO = 136
    const val EDIT_KEYWORDS = 168
    const val EDIT_THREAD = 308

    /** Clanmates page toggles -> member flag (texts in the cache: "Ban from Citadel", "Ban from Keep", "Mute", "Ban from Island"). */
    val MEMBER_TOGGLES = mapOf(56 to ClanMemberFlag.BAN_CITADEL, 60 to ClanMemberFlag.BAN_KEEP, 62 to ClanMemberFlag.MUTED, 64 to ClanMemberFlag.BAN_ISLAND)

    /** Permissions page rank tabs (Recruit .. Deputy Owner) -> rank value. */
    val RANK_TABS: Map<Int, Int> =
        mapOf(420 to 0, 428 to 1, 436 to 2, 444 to 3, 452 to 4, 460 to 5, 468 to 100, 476 to 101, 484 to 102, 492 to 103, 500 to 125)

    private const val VARC_NAME = 347
    private const val VARC_RANK = 1500
    private const val VARC_JOB = 1501
    private const val VARC_MUTED = 1564
    private const val VARC_BAN_KEEP = 1565
    private const val VARC_BAN_CITADEL = 1566
    private const val VARC_BAN_ISLAND = 1567
    private const val VARC_PROBATION = 1568
    private const val VARC_SELECTED_RANK = 1569
    private const val PROBATION_MILLIS = 7L * 24 * 60 * 60 * 1000

    private val EDITING = AttributeKey<String>()
    private val PENDING_RANK = AttributeKey<Int>()
    private val PENDING_JOB = AttributeKey<Int>()
    private val PENDING_FLAGS = AttributeKey<Int>()
    private val SELECTED_RANK = AttributeKey<Int>()

    fun open(player: Player) {
        if (Clans.clanOfPlayer(player) == null) {
            player.message("You must be in a clan to do that.")
            return
        }
        clearEditing(player)
        player.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(INTERFACE, MEMBER_ROWS, 0..499, OP1)
        player.setInterfaceEvents(INTERFACE, TIMEZONE_OPTIONS, 0..144, OP1)
        player.setInterfaceEvents(INTERFACE, JOB_OPTIONS, 0..500, OP1)
        player.setInterfaceEvents(INTERFACE, RANK_OPTIONS, 0..127, OP1)
        player.setInterfaceEvents(INTERFACE, WORLD_OPTIONS, 0..200, OP1)
        selectRank(player, RANK_TABS.values.first())
    }

    fun clearEditing(player: Player) {
        player.attr.remove(EDITING)
        player.attr.remove(PENDING_RANK)
        player.attr.remove(PENDING_JOB)
        player.attr.remove(PENDING_FLAGS)
    }

    /** "Show details" on a Clanmates row. */
    fun showMember(player: Player, index: Int) {
        val clan = Clans.clanOfPlayer(player) ?: return
        val (name, _) = Clans.orderedMembers(clan).getOrNull(index) ?: return
        clearEditing(player)
        player.attr[EDITING] = name
        sendMember(player, clan, name)
    }

    private fun sendMember(player: Player, clan: Clan, name: String) {
        val rank = player.attr[PENDING_RANK] ?: clan.members[name]?.value ?: return
        val flags = player.attr[PENDING_FLAGS] ?: clan.memberFlags[name] ?: 0
        val joined = clan.joined[name] ?: 0L
        player.setVarcString(VARC_NAME, Misc.formatForDisplay(name))
        player.setVarc(VARC_JOB, player.attr[PENDING_JOB] ?: clan.jobs[name] ?: 0)
        player.setVarc(VARC_MUTED, if (flags and ClanMemberFlag.MUTED != 0) 1 else 0)
        player.setVarc(VARC_BAN_KEEP, if (flags and ClanMemberFlag.BAN_KEEP != 0) 1 else 0)
        player.setVarc(VARC_BAN_CITADEL, if (flags and ClanMemberFlag.BAN_CITADEL != 0) 1 else 0)
        player.setVarc(VARC_BAN_ISLAND, if (flags and ClanMemberFlag.BAN_ISLAND != 0) 1 else 0)
        player.setVarc(VARC_PROBATION, if (joined > 0 && System.currentTimeMillis() - joined < PROBATION_MILLIS) 1 else 0)
        // The rank varc last: its transmit re-runs the details script (4294 -> 4311), which then sees every value.
        player.setVarc(VARC_RANK, rank)
    }

    fun chooseRank(player: Player, rank: Int) {
        val clan = Clans.clanOfPlayer(player) ?: return
        val name = player.attr[EDITING] ?: return
        if (ClanRank.values().none { it.value == rank }) return
        player.attr[PENDING_RANK] = rank
        sendMember(player, clan, name)
    }

    fun chooseJob(player: Player, job: Int) {
        val clan = Clans.clanOfPlayer(player) ?: return
        val name = player.attr[EDITING] ?: return
        player.attr[PENDING_JOB] = job
        sendMember(player, clan, name)
    }

    fun toggleMemberFlag(player: Player, component: Int) {
        val clan = Clans.clanOfPlayer(player) ?: return
        val name = player.attr[EDITING] ?: return
        val flag = MEMBER_TOGGLES[component] ?: return
        player.attr[PENDING_FLAGS] = (player.attr[PENDING_FLAGS] ?: clan.memberFlags[name] ?: 0) xor flag
        sendMember(player, clan, name)
    }

    fun save(player: Player) {
        val clan = Clans.clanOfPlayer(player) ?: return
        val name = player.attr[EDITING] ?: return
        val rank = player.attr[PENDING_RANK]?.let { r -> ClanRank.values().firstOrNull { it.value == r } }
        val job = player.attr[PENDING_JOB]
        val flags = player.attr[PENDING_FLAGS]
        if (rank == null && job == null && flags == null) return
        val saved = Clans.editMember(player, name, rank, job, flags)
        player.attr.remove(PENDING_RANK)
        player.attr.remove(PENDING_JOB)
        player.attr.remove(PENDING_FLAGS)
        if (saved) player.message("Your changes to ${Misc.formatForDisplay(name)} have been saved.")
        Clans.clanOfPlayer(player)?.let { c -> if (c.key(name) != null) sendMember(player, c, name) }
    }

    fun kick(player: Player) {
        val name = player.attr[EDITING] ?: return
        Clans.kickMember(player, name)
        clearEditing(player)
    }

    /** Permissions page rank tab: the rank's ticks. */
    fun selectRank(player: Player, rank: Int) {
        val clan = Clans.clanOfPlayer(player) ?: return
        player.attr[SELECTED_RANK] = rank
        val mask = clan.permissionMask(rank)
        ClanPermission.values().forEach { p -> player.setVarc(p.varc, if (mask and p.bit != 0) 1 else 0) }
        player.setVarc(ClanPermission.TALK_VARC, if (rank >= clan.talkRank) 1 else 0)
        player.setVarc(ClanPermission.KICK_VARC, if (rank >= clan.kickRank) 1 else 0)
        player.setVarc(VARC_SELECTED_RANK, rank)
    }

    fun permissionRow(player: Player, row: Int) {
        val clan = Clans.clanOfPlayer(player) ?: return
        val rank = player.attr[SELECTED_RANK] ?: return
        when (row) {
            ClanPermission.TALK_ROW -> Clans.setThreshold(player, rank, talk = true)
            ClanPermission.KICK_ROW -> Clans.setThreshold(player, rank, talk = false)
            else -> {
                val permission = ClanPermission.values().firstOrNull { it.row == row } ?: return
                Clans.setPermission(player, rank, permission, clan.permissionMask(rank) and permission.bit == 0)
            }
        }
        // Re-send the whole rank so a refused click puts the tick back as well.
        selectRank(player, rank)
    }

    private const val OP1 = 2
}
