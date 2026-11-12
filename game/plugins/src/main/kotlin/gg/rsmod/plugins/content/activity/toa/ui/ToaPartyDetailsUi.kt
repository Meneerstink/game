package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.inputInt
import gg.rsmod.plugins.api.ext.isInterfaceVisible
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentSprite
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.content.activity.toa.ToaChest
import gg.rsmod.plugins.content.activity.toa.ToaLobby
import gg.rsmod.plugins.content.activity.toa.ToaParty

/**
 * N462/N604: OSRS toa_partydetails (774) - "Party of <leader>" with the Members and Applicants tabs (CS2 toa_partydetails_init 6615,
 * toa_partydetails_addmember 6722, toa_partydetails_addapplicant 6727, toa_partydetails_cleartabs 6735, toa_partydetails_bottom_modify 6730).
 * Owner 11-06 A: no invocations - the Invocations/Summary tabs stay hidden; the leader picks the mode at the obelisk (Raid Level).
 *
 * Component ids (OsrsInterfacePortTool, tx-20261112-160734): OSRS 774:0..100; title 120, close 130; back 263; tab graphics 264..267
 * (members: left, middle, right, text) and 268..271 (applicants); member rows 292 + 12r and applicant rows 388 + 12r (background, op rect,
 * 10 cells: name, combat, attack, strength, ranged, magic, defence, hitpoints, prayer, completions); bottom button layers 484 refresh,
 * 495 apply, 506 withdraw, 517 leave, 528 apply (greyed, no op), 539 leader refresh, 550 disband, 561 unblock; completions texts 572
 * (member) / 573 (leader, op "Set"); raid level 73 / mode 74.
 */
object ToaPartyDetailsUi {
    const val MEMBERS_TAB = 6
    const val APPLICANTS_TAB = 7
    private const val INVOCATIONS_TAB = 8
    private const val SUMMARY_TAB = 9
    private const val MEMBERS = 17
    private const val APPLICANTS = 33
    private const val INVOCATIONS = 49
    private const val SUMMARY = 53
    private const val SIDE_PANEL = 57
    private const val RAID_LEVEL = 73
    private const val RAID_MODE = 74
    const val TITLE = 120
    const val CLOSE = 130
    const val BACK = 263
    private const val TAB_GRAPHICS = 264
    private const val MEMBER_ROWS = 292
    private const val APPLICANT_ROWS = 388
    const val REFRESH = 484
    const val APPLY = 495
    const val WITHDRAW = 506
    const val LEAVE = 517
    private const val APPLY_GREY = 528
    const val LEADER_REFRESH = 539
    const val DISBAND = 550
    const val UNBLOCK = 561
    private const val COMPLETIONS = 572
    const val LEADER_COMPLETIONS = 573
    /** Leader's "Set" on the mode text (owner 11-06 A: Entry / Normal / Expert instead of invocations). */
    const val MODE_SET = 574
    private const val EVENTS_OP1 = 0x2
    private const val EVENTS_OP1_2 = 0x6

    fun memberOp(r: Int): Int = MEMBER_ROWS + 12 * r + 1
    fun applicantOp(r: Int): Int = APPLICANT_ROWS + 12 * r + 1
    val BUTTONS = intArrayOf(CLOSE, BACK, MEMBERS_TAB, APPLICANTS_TAB, REFRESH, APPLY, WITHDRAW, LEAVE, LEADER_REFRESH, DISBAND, UNBLOCK,
        LEADER_COMPLETIONS, MODE_SET) + (0 until ToaParty.CAPACITY).map(::memberOp) + (0 until ToaParty.CAPACITY).map(::applicantOp)

    /** Party shown in this player's 774 and the selected tab (0 members, 1 applicants). */
    private val VIEWING = AttributeKey<Long>()
    private val TAB = AttributeKey<Int>()

    fun open(player: Player, party: ToaParty, tab: Int = 0) {
        val iface = ToaInterfaces.of(ToaInterfaces.PARTY_DETAILS) ?: return
        if (!player.isInterfaceVisible(iface) || player.attr[VIEWING] != party.id) {
            player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
            BUTTONS.forEach { player.setInterfaceEvents(iface, it, -1..-1, EVENTS_OP1) }
            (0 until ToaParty.CAPACITY).forEach { player.setInterfaceEvents(iface, applicantOp(it), -1..-1, EVENTS_OP1_2) }
            listOf(INVOCATIONS_TAB, SUMMARY_TAB, INVOCATIONS, SUMMARY, SIDE_PANEL).forEach { player.setComponentHidden(iface, it, true) }
        }
        player.attr[VIEWING] = party.id
        player.attr[TAB] = tab
        draw(player, party)
    }

    private fun draw(player: Player, party: ToaParty) {
        val iface = ToaInterfaces.of(ToaInterfaces.PARTY_DETAILS) ?: return
        val online = player.world.players.entries.filterNotNull().associateBy { it.uid }
        val leaderName = online[party.leader]?.username
        // [clientscript,toa_partydetails_addmember]: ~steelborder(frame, "Party of <leader>" | "Party Details").
        player.setComponentText(iface, TITLE, if (leaderName != null) "Party of $leaderName" else "Party Details")
        val tab = player.attr[TAB] ?: 0
        player.setComponentHidden(iface, MEMBERS, tab != 0)
        player.setComponentHidden(iface, APPLICANTS, tab != 1)
        // [proc,toa_partydetails_cleartabs]: "Members (<n>)", "Applicants (<n>)" - the count only when above 0.
        val members = party.members.mapNotNull(online::get)
        val applicants = party.applicants.mapNotNull(online::get)
        drawTab(player, iface, 0, if (members.isNotEmpty()) "Members (${members.size})" else "Members", tab == 0)
        drawTab(player, iface, 1, if (applicants.isNotEmpty()) "Applicants (${applicants.size})" else "Applicants", tab == 1)
        val leads = party.leader == player.uid
        drawRows(player, iface, MEMBER_ROWS, members, ops = leads)
        drawRows(player, iface, APPLICANT_ROWS, applicants, ops = leads)
        // [proc,toa_partydetails_bottom_modify]: leader Refresh | Disband | Unblock | Completions, others Refresh | action | Completions.
        val lobby = ToaLobby.of(player.world)
        val member = player.uid in party.members
        val applied = player.uid in party.applicants
        val canApply = !member && !applied && lobby.party(player.uid) == null && lobby.raid(player.uid) == null && party.canApply(player.uid)
        mapOf(REFRESH to !leads, APPLY to (!leads && canApply), WITHDRAW to (!leads && applied), LEAVE to (!leads && member),
            APPLY_GREY to (!leads && !member && !applied && !canApply), LEADER_REFRESH to leads, DISBAND to leads, UNBLOCK to leads,
            COMPLETIONS to !leads, LEADER_COMPLETIONS to leads, MODE_SET to leads).forEach { (component, shown) -> player.setComponentHidden(iface, component, !shown) }
        // [proc,script6741]: "<label>: <col=ffffff><n></col>" | leader "Set <label>" | "<label>: -".
        val wanted = party.preferredCompletions
        player.setComponentText(iface, if (leads) LEADER_COMPLETIONS else COMPLETIONS, when {
            wanted > 0 -> "Completions: <col=ffffff>$wanted</col>"
            leads -> "Set Completions"
            else -> "Completions: -"
        })
        player.setComponentText(iface, RAID_LEVEL, "Raid Level: ${party.settings.raidLevel}")
        player.setComponentText(iface, RAID_MODE, "Mode: ${ToaInvocationTable.mode(party.settings.raidLevel)}")
    }

    /** [proc,script3726]: selected tabs_modal_short,0/1 + 0xffa82f, otherwise ,2/,3 + 0xff981f. */
    private fun drawTab(player: Player, iface: Int, index: Int, label: String, selected: Boolean) {
        val base = TAB_GRAPHICS + 4 * index
        val cap = ToaInterfaces.sprite(if (selected) 2564 else 2566)
        val middle = ToaInterfaces.sprite(if (selected) 2565 else 2567)
        if (cap != null) { player.setComponentSprite(iface, base, cap); player.setComponentSprite(iface, base + 2, cap) }
        if (middle != null) player.setComponentSprite(iface, base + 1, middle)
        player.setComponentText(iface, base + 3, if (selected) "<col=ffa82f>$label</col>" else label)
    }

    /** [proc,script6724]/[proc,script6728]: name, combat, the seven stats and completions; the viewer's own row in white. */
    private fun drawRows(viewer: Player, iface: Int, base: Int, players: List<Player>, ops: Boolean) {
        for (r in 0 until ToaParty.CAPACITY) {
            val row = base + 12 * r
            val p = players.getOrNull(r)
            viewer.setComponentHidden(iface, row, p == null)
            viewer.setComponentHidden(iface, row + 1, p == null || !ops)
            val cells = if (p == null) List(10) { "" } else listOf(p.username, p.combatLevel.toString(),
                p.skills.getMaxLevel(Skills.ATTACK).toString(), p.skills.getMaxLevel(Skills.STRENGTH).toString(),
                p.skills.getMaxLevel(Skills.RANGED).toString(), p.skills.getMaxLevel(Skills.MAGIC).toString(),
                p.skills.getMaxLevel(Skills.DEFENCE).toString(), p.skills.getMaxLevel(Skills.CONSTITUTION).toString(),
                p.skills.getMaxLevel(Skills.PRAYER).toString(), (p.attr[ToaChest.COMPLETIONS] ?: 0).toString())
            cells.forEachIndexed { k, text ->
                viewer.setComponentHidden(iface, row + 2 + k, p == null)
                viewer.setComponentText(iface, row + 2 + k, if (p != null && p.uid == viewer.uid) "<col=ffffff>$text</col>" else text)
            }
        }
    }

    /** Redraws the window of everyone looking at [party] (the leader sees new applicants, applicants see Accept/Reject at once). */
    fun refreshViewers(player: Player, party: ToaParty) {
        val iface = ToaInterfaces.of(ToaInterfaces.PARTY_DETAILS) ?: return
        player.world.players.entries.filterNotNull()
            .filter { it.attr[VIEWING] == party.id && it.isInterfaceVisible(iface) }
            .forEach { draw(it, party) }
    }

    /** Everyone still looking at a party that is gone goes back to the party list. */
    private fun backToList(player: Player, partyId: Long) {
        val iface = ToaInterfaces.of(ToaInterfaces.PARTY_DETAILS) ?: return
        player.world.players.entries.filterNotNull()
            .filter { it.attr[VIEWING] == partyId && it.isInterfaceVisible(iface) }
            .forEach { ToaPartyListUi.open(it) }
    }

    fun click(player: Player, component: Int, op: Int) {
        val iface = ToaInterfaces.of(ToaInterfaces.PARTY_DETAILS) ?: return
        val lobby = ToaLobby.of(player.world)
        if (component == CLOSE) { player.closeInterface(iface); return }
        if (component == BACK) { ToaPartyListUi.open(player); return }
        val id = player.attr[VIEWING] ?: return
        val party = lobby.partyById(id)
        if (party == null || party.started) { ToaPartyListUi.open(player); return }
        when (component) {
            MEMBERS_TAB -> open(player, party, 0)
            APPLICANTS_TAB -> open(player, party, 1)
            REFRESH, LEADER_REFRESH -> draw(player, party)
            APPLY -> { lobby.apply(player.uid, party); refreshViewers(player, party) }
            WITHDRAW -> { lobby.withdraw(player.uid, party); refreshViewers(player, party) }
            LEAVE -> { lobby.leave(player.uid); refreshViewers(player, party); ToaPartyListUi.open(player) }
            DISBAND -> if (lobby.disband(player.uid)) backToList(player, id)
            UNBLOCK -> { party.unblock(player.uid); draw(player, party) }
            LEADER_COMPLETIONS -> if (party.leader == player.uid) player.queue(gg.rsmod.game.model.queue.TaskPriority.WEAK) { // WEAK: asked while this window is open (ROOT_CAUSES 92)
                // [clientscript,script6743] resumes a pause button; ONZEKER: the server's count prompt text (default "Enter amount").
                val amount = inputInt()
                if (lobby.partyById(id) === party && party.leader == player.uid && !party.started) {
                    party.preferredCompletions = amount.coerceAtLeast(0)
                    refreshViewers(player, party)
                }
            }
            MODE_SET -> if (party.leader == player.uid) player.queue(gg.rsmod.game.model.queue.TaskPriority.WEAK) { // WEAK: asked while this window is open (ROOT_CAUSES 92)
                // Same choice as before at the obelisk (owner 11-06 A); no chat line - OSRS shows the level in this window / the HUD.
                val modes = gg.rsmod.plugins.content.activity.toa.core.ToaMode.values()
                val pick = options(*modes.map { "${it.label} (raid level ${it.raidLevel})" }.toTypedArray(), title = "Raid Level")
                val mode = modes.getOrNull(pick - 1) ?: return@queue
                if (lobby.partyById(id) === party && party.leader == player.uid && !party.started) {
                    party.settings = gg.rsmod.plugins.content.activity.toa.ToaRaidSettings(raidLevel = mode.raidLevel)
                    refreshViewers(player, party)
                }
            }
            else -> {
                val memberRow = (0 until ToaParty.CAPACITY).firstOrNull { memberOp(it) == component }
                val applicantRow = (0 until ToaParty.CAPACITY).firstOrNull { applicantOp(it) == component }
                val online = player.world.players.entries.filterNotNull().associateBy { it.uid }
                if (memberRow != null) {
                    val target = party.members.mapNotNull(online::get).getOrNull(memberRow) ?: return
                    if (target.uid == player.uid) { lobby.leave(player.uid); refreshViewers(player, party); ToaPartyListUi.open(player) }
                    else if (lobby.kick(player.uid, target.uid)) refreshViewers(player, party)
                } else if (applicantRow != null) {
                    val target = party.applicants.mapNotNull(online::get).getOrNull(applicantRow) ?: return
                    if (op == 1) lobby.accept(player.uid, target.uid) else lobby.reject(player.uid, target.uid)
                    refreshViewers(player, party)
                }
            }
        }
    }
}
