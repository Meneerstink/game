package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText

/**
 * The recruiting board (RAIDS_RECRUITING_BOARD 29776 'Read', OSRS 499) and the party details (OSRS 507). Layout from the port
 * (OsrsInterfacePortTool 'cox', dossier COX.txt 21:1x): row r = container 499:16+r (op 1 'View party:', hidden until filled) holding
 * 76+9r..84+9r (background, members, '/ preferred size', leader, combat, skill total, scaling, challenge, age); buttons Refresh 444 (shown)
 * / Make party 455 / My party 466 (both hidden: the server shows the one that fits, [proc,raids_partylist_setpartybutton]). 507: list 11, preference boxes 13
 * (size) / 17 (combat) / 21 (skill total) / 22 (scaling) / 23 (challenge) / 24 (map layout), buttons 34 join, 32 advertise, 33 disband,
 * 35 back, 36 refresh. Texts word for word from the Wiki's Recruiting board gallery (screens\COX\wiki-ref): 'Raiding Party of <leader>
 * (<n>)', 'Preferred party size: 4', 'Preferred combat level: 120', 'Preferred skill total: 1750', 'Scaling: 10', 'Map Layout: Large',
 * 'Advertise (14)'; '-' for an unset preference (499 row '1 | - | Kelsew | - | - | - | 0:07').
 * ONZEKER: the order of a 499 row's eight texts follows the port's component order (not yet seen in the client) - check at 23:00.
 */
object CoxPartyBoard {
    const val ROW_CONTAINER = 16
    const val ROWS = 76
    const val ROW_SIZE = 9
    const val MAX_ROWS = 40
    const val REFRESH = 444
    const val MAKE_PARTY = 455
    const val MY_PARTY = 466

    /**
     * 507 after OsrsInterfacePortTool 'cox' (dry-run tx-20261115-210458): the OSRS button layers 32 advertise / 33 disband / 34 join /
     * 35 back / 36 refresh (hidden until shown) each hold a stone button whose click layer is D_* and whose label is D_* + 10; the
     * preference layers 13/17/21/22/23/24 hold the texts 64..69; steelborder title 53; member rows from 125 (5 each: background, name,
     * combat, total, kills) in the list 11.
     */
    const val D_TITLE = 53
    val D_PREF_LAYERS = intArrayOf(13, 17, 21, 22, 23, 24)
    const val D_PREF_TEXT = 64
    val D_BUTTON_LAYERS = intArrayOf(32, 33, 34, 35, 36)
    const val D_ADVERTISE = 70
    const val D_DISBAND = 81
    const val D_JOIN = 92
    const val D_BACK = 103
    const val D_REFRESH = 114
    const val D_LABEL = 10
    const val D_ROWS = 125
    const val D_ROW_SIZE = 5

    /** 'Advertise (14)': adverts a day (tsps: 15 in every capture, cache raids_daily_adverts). */
    const val DAILY_ADVERTS = 15

    /** An advert lasts until the party enters or is delisted; tsps lists 'age in ticks'. ONZEKER: no source for an expiry - none here. */
    const val ADVERT_CYCLES = Int.MAX_VALUE / 2

    private val viewing = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val rowParty = java.util.concurrent.ConcurrentHashMap<String, List<Long>>()

    fun age(ticks: Int): String {
        val seconds = ticks * 6 / 10
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    fun pref(value: Int): String = if (value <= 0) "-" else value.toString()

    fun openBoard(player: Player) {
        val iface = CoxInterfaces.of(CoxInterfaces.PARTY_LIST) ?: return
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        drawBoard(player, iface)
    }

    private fun drawBoard(player: Player, iface: Int) {
        val now = player.world.currentCycle
        val listed = CoxParties.listed(now).take(MAX_ROWS)
        rowParty[player.username.lowercase()] = listed.map { it.id }
        val mine = CoxParties.of(player.username) != null
        player.setComponentHidden(iface, REFRESH, false)
        player.setComponentHidden(iface, MAKE_PARTY, mine)
        player.setComponentHidden(iface, MY_PARTY, !mine)
        for (r in 0 until MAX_ROWS) {
            val base = ROWS + ROW_SIZE * r
            val p = listed.getOrNull(r)
            player.setComponentHidden(iface, ROW_CONTAINER + r, p == null)
            if (p == null) continue
            player.setComponentText(iface, base + 1, p.members.size.toString())
            player.setComponentText(iface, base + 2, if (p.preferredSize > 0) "/ ${p.preferredSize}" else "")
            player.setComponentText(iface, base + 3, displayName(player, p.leader))
            player.setComponentText(iface, base + 4, pref(p.preferredCombat))
            player.setComponentText(iface, base + 5, pref(p.preferredSkillTotal))
            player.setComponentText(iface, base + 6, pref(p.scaling))
            player.setComponentText(iface, base + 7, "")
            player.setComponentText(iface, base + 8, age(now - p.advertisedAt))
        }
    }

    private fun displayName(player: Player, key: String): String =
        player.world.players.entries.filterNotNull().firstOrNull { it.username.lowercase() == key }?.username ?: key

    fun clickBoard(player: Player, component: Int) {
        val iface = CoxInterfaces.of(CoxInterfaces.PARTY_LIST) ?: return
        when (component) {
            REFRESH -> drawBoard(player, iface)
            MAKE_PARTY -> {
                val party = CoxParties.of(player.username) ?: CoxParties.create(player.username)
                if (party != null) openDetails(player, party)
            }
            MY_PARTY -> CoxParties.of(player.username)?.let { openDetails(player, it) }
            in ROW_CONTAINER until ROW_CONTAINER + MAX_ROWS -> {
                val id = rowParty[player.username.lowercase()]?.getOrNull(component - ROW_CONTAINER) ?: return
                CoxParties.byId(id)?.let { openDetails(player, it) }
            }
        }
    }

    fun openDetails(player: Player, party: CoxParty) {
        val iface = CoxInterfaces.of(CoxInterfaces.PARTY_DETAILS) ?: return
        viewing[player.username.lowercase()] = party.id
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        drawDetails(player, iface, party)
    }

    /**
     * [clientscript,script1524] (osrs-dumps@5cdf0f47): leader (state 2) gets 'Advertise (<n>)' - 'De-list' while listed, grey
     * 'Advertise (0)' without adverts left - and red 'Disband'; a member (1) red 'Leave'; anyone else (0) green 'Join'; everybody 'Back' and
     * 'Refresh'. Preference texts per [proc,script1530]; member rows per [clientscript,raids_partydetails_addline] (name, combat, total, kc).
     */
    private fun drawDetails(player: Player, iface: Int, party: CoxParty) {
        val me = player.username.lowercase()
        val state = when { party.leader == me -> 2; party.isMember(me) -> 1; else -> 0 }
        val online = party.members.mapNotNull { m -> player.world.players.entries.filterNotNull().firstOrNull { it.username.lowercase() == m } }
        player.setComponentText(iface, D_TITLE, "Raiding Party of ${displayName(player, party.leader)} (${party.members.size})")
        val prefs = listOf(
            "Preferred party size: ${pref(party.preferredSize)}", "Preferred combat level: ${pref(party.preferredCombat)}",
            "Preferred skill total: ${pref(party.preferredSkillTotal)}", "Scaling: ${pref(party.scaling)}", "Challenge mode", "Map Layout: Small",
        )
        prefs.forEachIndexed { i, text ->
            player.setComponentHidden(iface, D_PREF_LAYERS[i], false)
            player.setComponentText(iface, D_PREF_TEXT + i, text)
        }
        val listed = party.listed(player.world.currentCycle)
        val advertise = when { listed -> "De-list"; party.advertsLeft > 0 -> "Advertise (${party.advertsLeft})"; else -> "<col=7f7f7f>Advertise (0)</col>" }
        val labels = mapOf(
            32 to advertise.takeIf { state == 2 }, 33 to "<col=ff0000>Disband</col>".takeIf { state == 2 },
            34 to when (state) { 1 -> "<col=ff0000>Leave</col>"; 0 -> "<col=00ff00>Join</col>"; else -> null },
            35 to "Back", 36 to "Refresh",
        )
        val clickLayers = mapOf(32 to D_ADVERTISE, 33 to D_DISBAND, 34 to D_JOIN, 35 to D_BACK, 36 to D_REFRESH)
        labels.forEach { (layer, label) ->
            player.setComponentHidden(iface, layer, label == null)
            if (label != null) player.setComponentText(iface, clickLayers.getValue(layer) + D_LABEL, label)
        }
        for (r in 0 until CoxParty.CAPACITY) {
            val base = D_ROWS + D_ROW_SIZE * r
            val key = party.members.getOrNull(r)
            (0 until D_ROW_SIZE).forEach { player.setComponentHidden(iface, base + it, key == null) }
            if (key == null) continue
            val p = online.firstOrNull { it.username.lowercase() == key }
            player.setComponentText(iface, base + 1, p?.username ?: key)
            player.setComponentText(iface, base + 2, p?.combatLevel?.toString() ?: "-")
            player.setComponentText(iface, base + 3, p?.let { totalLevel(it).toString() } ?: "-")
            player.setComponentText(iface, base + 4, p?.let { (it.attr[CoxRewards.COMPLETIONS] ?: 0).toString() } ?: "-")
        }
    }

    private fun totalLevel(p: Player): Int = (0 until p.skills.maxSkills).sumOf { p.skills.getMaxLevel(it) }

    fun clickDetails(player: Player, component: Int) {
        val iface = CoxInterfaces.of(CoxInterfaces.PARTY_DETAILS) ?: return
        val party = viewing[player.username.lowercase()]?.let { CoxParties.byId(it) } ?: return openBoard(player)
        val me = player.username
        when (component) {
            D_JOIN -> when {
                party.isMember(me) -> CoxParties.leave(me)
                CoxRaids.byId(party.raidId)?.started == true -> player.message("No-one may join the party after the raid begins.")
                CoxParties.of(me) != null -> return
                else -> party.join(me)
            }
            D_ADVERTISE -> if (party.leader == me.lowercase() && party.advertsLeft > 0 && !party.listed(player.world.currentCycle)) {
                party.advertsLeft--
                party.advertisedAt = player.world.currentCycle
                party.advertisedUntil = player.world.currentCycle + ADVERT_CYCLES
            } else if (party.leader == me.lowercase()) party.advertisedUntil = -1
            D_DISBAND -> if (party.leader == me.lowercase()) { CoxParties.disband(party); return openBoard(player) }
            D_BACK -> return openBoard(player)
            D_REFRESH -> Unit
            else -> return
        }
        if (CoxParties.byId(party.id) == null) return openBoard(player)
        drawDetails(player, iface, party)
    }
}
