package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.content.activity.toa.ToaLobby
import gg.rsmod.plugins.content.activity.toa.ToaParty

/**
 * N462/N604: OSRS toa_partylist (772) "Tombs of Amascut Party List", opened by Inspect on the Grouping Obelisk (OSRS Wiki Grouping
 * Obelisk + its interface picture): one row per listed party - members "<n>/8", leader name, preferred completions ("-" grey when 0),
 * invocations, raid level, mode and age "m:ss" (CS2 toa_partylist_addline 6601 / fillline 6602 / raids_partylist_age_write 1575),
 * rebuilt statically by OsrsInterfacePortTool. Row op 1 "View Party:" opens that party's details (774). Buttons (script1558):
 * Refresh, Make Party | My Party ([proc,toa_partylist_setpartybutton]: My Party while %toa_mycontroller is set) and Friends/Clan Only
 * (pressed = only parties led by a friend or a member of your clan).
 * Component ids: OSRS 772:0..66; steelborder of 772:2 = 67..78 (close 78); thinboxes 79..82; rows 772:17+r with children 83+8r .. 90+8r
 * (background, members, name, completions, invocations, raid level, difficulty, age); "No parties are currently listed." = 443; sort
 * header graphics 444..457; button layers (click targets) 458 refresh, 469 Make Party, 480 My Party, 491 filter, 502 filter pressed.
 */
object ToaPartyListUi {
    const val CLOSE = 78
    const val ROWS = 45
    const val REFRESH = 458
    const val MAKE_PARTY = 469
    const val MY_PARTY = 480
    const val FILTER = 491
    const val FILTER_ON = 502
    val BUTTONS = intArrayOf(REFRESH, MAKE_PARTY, MY_PARTY, FILTER, FILTER_ON)
    private const val CHILDREN = 83
    private const val NONE = 443
    private const val EVENTS_OP1 = 0x2

    class Row(val partyId: Long, val leader: String, val members: Int, val completions: Int, val invocations: Int, val raidLevel: Int,
              val ageSeconds: Int)

    private val SHOWN = AttributeKey<List<Long>>()
    /** %toa_partylist_filter: Friends/Clan Only pressed. Per session, like the OSRS varp it is not a saved setting here. */
    private val FILTERED = AttributeKey<Boolean>()

    fun row(r: Int): Int = 17 + r

    /** Opens (or refreshes) the list from the lobby of [player]'s world. */
    fun open(player: Player) {
        val lobby = ToaLobby.of(player.world)
        val online = player.world.players.entries.filterNotNull().associateBy { it.uid }
        val filtered = player.attr[FILTERED] == true
        val rows = lobby.listed().mapNotNull { party ->
            val leader = online[party.leader] ?: return@mapNotNull null
            if (filtered && !friendOrClan(player, leader)) return@mapNotNull null
            Row(party.id, leader.username, party.members.size, party.preferredCompletions, party.settings.invocations.size,
                party.settings.raidLevel, ((System.currentTimeMillis() - party.createdAt) / 1000).toInt())
        }
        show(player, rows, member = lobby.party(player.uid) != null, filtered = filtered)
    }

    /** Draws [rows]; also used by the admin ::toaui test command with sample rows. */
    fun show(player: Player, rows: List<Row>, member: Boolean, filtered: Boolean) {
        val iface = ToaInterfaces.of(ToaInterfaces.PARTY_LIST) ?: return
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(iface, CLOSE, -1..-1, EVENTS_OP1)
        BUTTONS.forEach { player.setInterfaceEvents(iface, it, -1..-1, EVENTS_OP1) }
        player.setComponentHidden(iface, MAKE_PARTY, member)
        player.setComponentHidden(iface, MY_PARTY, !member)
        player.setComponentHidden(iface, FILTER, filtered)
        player.setComponentHidden(iface, FILTER_ON, !filtered)
        for (r in 0 until ROWS) {
            val row = rows.getOrNull(r)
            player.setComponentHidden(iface, row(r), row == null)
            if (row == null) continue
            player.setInterfaceEvents(iface, row(r), -1..-1, EVENTS_OP1)
            val c = CHILDREN + 8 * r
            player.setComponentText(iface, c + 1, "${row.members}/${ToaParty.CAPACITY}")
            player.setComponentText(iface, c + 2, row.leader)
            player.setComponentText(iface, c + 3, if (row.completions != 0) row.completions.toString() else "<col=9f9f9f>-</col>")
            player.setComponentText(iface, c + 4, row.invocations.toString())
            player.setComponentText(iface, c + 5, row.raidLevel.toString())
            player.setComponentText(iface, c + 6, ToaInvocationTable.mode(row.raidLevel))
            // [proc,raids_partylist_age_write] (1575): minutes ":" seconds with a leading zero below 10.
            player.setComponentText(iface, c + 7, "%d:%02d".format(row.ageSeconds / 60, row.ageSeconds % 60))
        }
        // [clientscript,toa_partylist_addline]: the empty-list line depends on %toa_partylist_filter.
        player.setComponentText(iface, NONE, if (filtered) "You have no friends or clanmates in a party." else "No parties are currently listed.")
        player.setComponentHidden(iface, NONE, rows.isNotEmpty())
        player.attr[SHOWN] = rows.map { it.partyId }
    }

    fun click(player: Player, component: Int) {
        val iface = ToaInterfaces.of(ToaInterfaces.PARTY_LIST) ?: return
        val lobby = ToaLobby.of(player.world)
        when (component) {
            CLOSE -> player.closeInterface(iface)
            REFRESH -> open(player)
            FILTER, FILTER_ON -> { player.attr[FILTERED] = player.attr[FILTERED] != true; open(player) }
            MAKE_PARTY, MY_PARTY -> {
                val party = lobby.party(player.uid) ?: lobby.create(player.uid) ?: return
                ToaPartyDetailsUi.open(player, party)
            }
            else -> {
                val r = component - 17
                if (r !in 0 until ROWS) return
                val party = player.attr[SHOWN]?.getOrNull(r)?.let(lobby::partyById)
                if (party == null || party.started) { open(player); return }
                ToaPartyDetailsUi.open(player, party)
            }
        }
    }

    /** Friends/Clan Only: the leader is on [player]'s friends list or in the same clan (Clans.clanOf). */
    private fun friendOrClan(player: Player, leader: Player): Boolean {
        if (leader.uid == player.uid) return true
        val display = gg.rsmod.util.Misc.formatForDisplay(leader.username)
        if (player.friends.any { gg.rsmod.util.Misc.formatForDisplay(it).equals(display, ignoreCase = true) }) return true
        val clan = gg.rsmod.plugins.content.mechanics.clan.Clans.clanOf(player)
        return clan != null && clan == gg.rsmod.plugins.content.mechanics.clan.Clans.clanOf(leader)
    }
}
