package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.RspsPaths
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import java.io.File

/** CoX windows ported by OsrsInterfacePortTool 'cox' (data/cfg/cox/interfaces.tsv, written with the cache) - no id is guessed here. */
object CoxInterfaces {
    private val interfaces = HashMap<Int, Int>()

    init {
        val file = File(RspsPaths.data("cfg/cox/interfaces.tsv"))
        if (file.isFile) file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('\t')
            if (f[0] == "interface") interfaces[f[1].toInt()] = f[2].toInt()
        }
    }

    fun of(osrsGroup: Int): Int? = interfaces[osrsGroup]

    const val PARTY_LIST = 499
    const val PARTY_TAB = 500
    const val PARTY_DETAILS = 507
    const val POINTS = 513
    const val REWARDS = 539
}

/**
 * The CoX party tab (OSRS 500 'Raiding Party', in the chat-channel tab's place while in the chambers - tsps 'The party tab (500) replaces
 * the chat-channel tab'). Texts word for word from osrs-dumps@5cdf0f47 [proc,raids_sidepanel_data] (1540); components of the rebuilt
 * bottom (OsrsInterfacePortTool group 500): 22 data line, 23 progress line, 24 the leader's 'Start raid' stone button.
 * Progress (varbit 5425 raids_client_progress): 0 before the start, 1 upper level, 2 the next level, 4 the bottom (Olm), 5 complete.
 * ONZEKER: which value a two-floor (normal) raid sends at the Olm - [proc,raids_sidepanel_data] has 2 = 'lower level' (map pool not 2)
 * and 4 = 'the bottom'; the Olm's chamber is taken as 4.
 */
object CoxPartyTab {
    const val DATA = 22
    const val PROGRESS = 23
    const val START = 24

    fun progressText(progress: Int, leader: Boolean): String = when (progress) {
        0 -> if (leader) "" else "Waiting for your leader to begin the raid..."
        1 -> "Your party is exploring the upper level..."
        2 -> "Your party has reached the lower level..."
        3 -> "Your party has reached the lower level..."
        4 -> "Your party has reached the bottom..."
        else -> "Get out! Get out!<br>GET OUT!"
    }

    fun dataText(partySize: Int, highestCombat: Int, scaledSize: Int = partySize): String {
        var s = "Party size: <col=ffffff>$partySize</col>  Combat level: <col=ffffff>$highestCombat</col>"
        if (scaledSize > partySize) s += "<br>Scaled party size: <col=ffffff>$scaledSize</col>"
        return s
    }

    fun open(player: Player, raid: CoxRaid) {
        val iface = CoxInterfaces.of(CoxInterfaces.PARTY_TAB) ?: return
        player.openInterface(iface, InterfaceDestination.CLAN_CHAT_TAB)
        update(player, raid)
    }

    fun update(player: Player, raid: CoxRaid) {
        val iface = CoxInterfaces.of(CoxInterfaces.PARTY_TAB) ?: return
        val members = raid.world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) }
        val leader = player.username == raid.leader
        player.setComponentText(iface, DATA, dataText(members.size.coerceAtLeast(1), members.maxOfOrNull { it.combatLevel } ?: player.combatLevel))
        player.setComponentText(iface, PROGRESS, progressText(raid.progress, leader))
        player.setComponentHidden(iface, START, !(leader && raid.progress == 0))
        // Member rows (port tool: 8 rows from ROWS, 4 each: background, name, combat, skill total), sorted by name - the default sort
        // (%raids_listsort_details 0, [proc,raids_sidepanel_setsort]).
        val sorted = members.sortedBy { it.username.lowercase() }
        for (r in 0 until MAX_ROWS) {
            val m = sorted.getOrNull(r)
            val base = ROWS + 4 * r
            if (m == null) {
                (0..3).forEach { player.setComponentHidden(iface, base + it, true) }
                continue
            }
            player.setComponentText(iface, base + 1, m.username)
            player.setComponentText(iface, base + 2, m.combatLevel.toString())
            player.setComponentText(iface, base + 3, skillTotal(m).toString())
            (0..3).forEach { player.setComponentHidden(iface, base + it, false) }
        }
    }

    /** First member-row component of the rebuilt tab (OsrsInterfacePortTool group 500: bottom 20..34, rows from 35). */
    const val ROWS = 35
    const val MAX_ROWS = 8

    private fun skillTotal(p: Player): Int = (0 until p.skills.maxSkills).sumOf { p.skills.getMaxLevel(it) }

    /** Back to the normal chat-channel tab (1110) when leaving the chambers. */
    fun close(player: Player) {
        if (CoxInterfaces.of(CoxInterfaces.PARTY_TAB) == null) return
        player.openInterface(InterfaceDestination.CLAN_CHAT_TAB)
    }

    fun updateAll(raid: CoxRaid) {
        raid.world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) }.forEach { update(it, raid) }
    }
}
