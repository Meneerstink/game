package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentSprite
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.content.activity.toa.ToaLobby
import gg.rsmod.plugins.content.activity.toa.ToaParty

/**
 * N462: OSRS toa_partydetails (774) - the party's invocations tab, the window where the leader sets the raid level (OSRS Wiki Tombs of
 * Amascut "Invocations"; CS2 toa_partydetails_init 6615, script6749/6750 tiles, toa_invocations_side_panel_update 6770).
 *
 * Component ids: OSRS 774:0..100 are the same here; OsrsInterfacePortTool adds after them, in this order: 9 thinboxes x 2 (101..118),
 * the steelborder of 774:3 (119..130: title 120, close 130) and 44 invocation tiles x 3 (131 + 3i click rect, +1 icon, +2 name).
 */
object ToaPartyDetailsUi {
    const val MEMBERS_TAB = 6
    const val APPLICANTS_TAB = 7
    const val INVOCATIONS_TAB = 8
    const val SUMMARY_TAB = 9
    const val MEMBERS = 17
    const val APPLICANTS = 33
    const val INVOCATIONS = 49
    const val SUMMARY = 53
    const val SIDE_PANEL = 57
    const val RAID_LEVEL_INVOCATIONS = 90
    const val INVOCATIONS_ACTIVE = 92
    const val TITLE = 120
    const val CLOSE = 130
    const val TILES = 131
    private const val EVENTS_OP1 = 0x2
    private const val ACTIVE_COLOUR = 0xd39f34
    private const val INACTIVE_COLOUR = 0x9f9f9f

    fun tile(index: Int): Int = TILES + 3 * index

    /** Tile click rect -> invocation index, or -1. */
    fun invocationOf(component: Int): Int =
        if (component >= TILES && (component - TILES) % 3 == 0 && (component - TILES) / 3 < ToaInvocationTable.ENTRIES.size) (component - TILES) / 3 else -1

    fun open(player: Player, party: ToaParty) {
        val iface = ToaInterfaces.of(ToaInterfaces.PARTY_DETAILS) ?: return
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(iface, CLOSE, -1..-1, EVENTS_OP1)
        // Invocations tab shown, like the OSRS party window opened at the board (the other tabs come with the party list port).
        listOf(MEMBERS, APPLICANTS, SUMMARY).forEach { player.setComponentHidden(iface, it, true) }
        player.setComponentHidden(iface, INVOCATIONS, false)
        player.setComponentHidden(iface, SIDE_PANEL, false)
        val leaderName = online(player, party.leader)?.username
        player.setComponentText(iface, TITLE, if (leaderName != null) "Party of $leaderName" else "Party Details")
        ToaInvocationTable.ENTRIES.indices.forEach { player.setInterfaceEvents(iface, tile(it), -1..-1, EVENTS_OP1) }
        refresh(player, party)
    }

    fun refresh(player: Player, party: ToaParty) {
        val iface = ToaInterfaces.of(ToaInterfaces.PARTY_DETAILS) ?: return
        val selected = party.settings.invocations
        ToaInvocationTable.ENTRIES.forEachIndexed { i, e ->
            val on = e.name in selected
            iconSprite(e.order, on)?.let { player.setComponentSprite(iface, tile(i) + 1, it) }
            player.setComponentText(iface, tile(i) + 2, "<col=%06x>%s</col>".format(if (on) ACTIVE_COLOUR else INACTIVE_COLOUR, e.name))
        }
        player.setComponentText(iface, RAID_LEVEL_INVOCATIONS, ToaInvocationTable.raidLevel(selected).toString())
        player.setComponentText(iface, INVOCATIONS_ACTIVE, selected.size.toString())
    }

    /** OSRS struct params 1297 (active) / 1298 (inactive) per invocation, ported as 667 sprites (ToaInterfaces). */
    private fun iconSprite(order: Int, on: Boolean): Int? = ICONS.getOrNull(order)?.let { ToaInterfaces.sprite(if (on) it.first else it.second) }

    /** OSRS sprite of param 1297 (active) / 1298 (inactive) per enum-4664 order (cache 2686 structs, ChatGPT-A 10-invocations.tsv). */
    private val ICONS: List<Pair<Int, Int>> =
        listOf(
            4592 to 4616, 4592 to 4616, 4592 to 4616, 4592 to 4616, 4593 to 4617, 4593 to 4617, 4593 to 4617, 4593 to 4617, 4594 to 4618,
            4594 to 4618, 4594 to 4618, 4598 to 4622, 4595 to 4619, 4595 to 4619, 4595 to 4619, 4596 to 4620, 4596 to 4620, 4603 to 4627,
            4597 to 4621, 4604 to 4628, 4587 to 4611, 4587 to 4611, 4587 to 4611, 4587 to 4611, 4587 to 4611, 4590 to 4614, 4590 to 4614,
            4590 to 4614, 4590 to 4614, 4588 to 4612, 4588 to 4612, 4588 to 4612, 4588 to 4612, 4589 to 4613, 4589 to 4613, 4589 to 4613,
            4589 to 4613, 4589 to 4613, 4591 to 4615, 4591 to 4615, 4591 to 4615, 4591 to 4615, 4591 to 4615, 4591 to 4615,
        )

    private fun online(player: Player, uid: gg.rsmod.game.model.PlayerUID): Player? =
        player.world.players.entries.filterNotNull().firstOrNull { it.uid == uid }

    fun click(player: Player, component: Int) {
        val iface = ToaInterfaces.of(ToaInterfaces.PARTY_DETAILS) ?: return
        if (component == CLOSE) { player.closeInterface(iface); return }
        val index = invocationOf(component)
        if (index < 0) return
        val party = ToaLobby.of(player.world).party(player.uid) ?: return
        if (party.leader != player.uid || party.started) return
        val next = ToaInvocationTable.toggle(party.settings.invocations, ToaInvocationTable.ENTRIES[index].name)
        party.settings = party.settings.copy(invocations = next, raidLevel = ToaInvocationTable.raidLevel(next))
        // Every member with the window open sees the change, like OSRS (the board is shared party state).
        party.members.mapNotNull { online(player, it) }.forEach { refresh(it, party) }
    }
}
