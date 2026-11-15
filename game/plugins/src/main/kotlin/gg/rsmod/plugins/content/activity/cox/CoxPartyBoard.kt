package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText

/**
 * The recruiting board (RAIDS_RECRUITING_BOARD 29776 'Read', OSRS 499) and the party details (OSRS 507). Layout from the port
 * (OsrsInterfacePortTool 'cox', dossier COX.txt 21:1x): 499 rows 76..435 = 40 rows of 9 (background, members, '/ preferred size', leader,
 * combat, skill total, scaling, challenge, age), buttons Refresh 444 / Make party 455 / My party 466. 507: list 11, preference boxes 13
 * (size) / 17 (combat) / 21 (skill total) / 22 (scaling) / 23 (challenge) / 24 (map layout), buttons 34 join, 32 advertise, 33 disband,
 * 35 back, 36 refresh. Texts word for word from the Wiki's Recruiting board gallery (screens\COX\wiki-ref): 'Raiding Party of <leader>
 * (<n>)', 'Preferred party size: 4', 'Preferred combat level: 120', 'Preferred skill total: 1750', 'Scaling: 10', 'Map Layout: Large',
 * 'Advertise (14)'; '-' for an unset preference (499 row '1 | - | Kelsew | - | - | - | 0:07').
 * ONZEKER: the order of a 499 row's eight texts follows the port's component order (not yet seen in the client) - check at 23:00.
 */
object CoxPartyBoard {
    const val ROWS = 76
    const val ROW_SIZE = 9
    const val MAX_ROWS = 40
    const val REFRESH = 444
    const val MAKE_PARTY = 455
    const val MY_PARTY = 466

    const val D_LIST = 11
    const val D_SIZE = 13
    const val D_COMBAT = 17
    const val D_TOTAL = 21
    const val D_SCALING = 22
    const val D_CHALLENGE = 23
    const val D_LAYOUT = 24
    const val D_ADVERTISE = 32
    const val D_DISBAND = 33
    const val D_JOIN = 34
    const val D_BACK = 35
    const val D_REFRESH = 36

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
        for (r in 0 until MAX_ROWS) {
            val base = ROWS + ROW_SIZE * r
            val p = listed.getOrNull(r)
            if (p == null) { (0 until ROW_SIZE).forEach { player.setComponentHidden(iface, base + it, true) }; continue }
            (0 until ROW_SIZE).forEach { player.setComponentHidden(iface, base + it, false) }
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
            in ROWS until ROWS + ROW_SIZE * MAX_ROWS -> {
                val id = rowParty[player.username.lowercase()]?.getOrNull((component - ROWS) / ROW_SIZE) ?: return
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

    private fun drawDetails(player: Player, iface: Int, party: CoxParty) {
        // ONZEKER: title component not yet known in the 507 port - lands with the port (step 1 of the board work).
        player.setComponentText(iface, D_SIZE, "Preferred party size: ${pref(party.preferredSize)}")
        player.setComponentText(iface, D_COMBAT, "Preferred combat level: ${pref(party.preferredCombat)}")
        player.setComponentText(iface, D_TOTAL, "Preferred skill total: ${pref(party.preferredSkillTotal)}")
        player.setComponentText(iface, D_SCALING, "Scaling: ${pref(party.scaling)}")
        player.setComponentText(iface, D_LAYOUT, "Map Layout: Small")
        val leader = party.leader == player.username.lowercase()
        player.setComponentHidden(iface, D_ADVERTISE, !leader)
        player.setComponentHidden(iface, D_DISBAND, !leader)
        player.setComponentText(iface, D_ADVERTISE, "Advertise (${party.advertsLeft})")
    }

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
