package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents

/**
 * N462: OSRS toa_partylist (772) "Tombs of Amascut Party List" at the Grouping Obelisk: one row per listed party - members "<n>/8",
 * leader name, completions ("-" grey when 0), active invocations, raid level, difficulty and age (CS2 toa_partylist_addline 6601 /
 * fillline 6602 / entry_init 6603, rebuilt statically by OsrsInterfacePortTool). Row op 1 "View Party:".
 * Component ids: OSRS 772:0..66; steelborder of 772:2 = 67..78 (close 78); thinboxes 79..82; rows 772:17+r with children 83+8r .. 90+8r
 * (background, members, name, completions, invocations, raid level, difficulty, age); "No parties are currently listed." = 443.
 */
object ToaPartyListUi {
    const val CLOSE = 78
    const val ROWS = 45
    private const val CHILDREN = 83
    private const val NONE = 443
    private const val EVENTS_OP1 = 0x2

    class Row(val leader: String, val members: Int, val completions: Int, val invocations: Int, val raidLevel: Int, val ageMinutes: Int)

    private val SHOWN = AttributeKey<(Int) -> Unit>()

    fun row(r: Int): Int = 17 + r

    fun open(player: Player, rows: List<Row>, onView: (Int) -> Unit) {
        val iface = ToaInterfaces.of(ToaInterfaces.PARTY_LIST) ?: return
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(iface, CLOSE, -1..-1, EVENTS_OP1)
        for (r in 0 until ROWS) {
            val row = rows.getOrNull(r)
            player.setComponentHidden(iface, row(r), row == null)
            if (row == null) continue
            player.setInterfaceEvents(iface, row(r), -1..-1, EVENTS_OP1)
            val c = CHILDREN + 8 * r
            player.setComponentText(iface, c + 1, "${row.members}/8")
            player.setComponentText(iface, c + 2, row.leader)
            player.setComponentText(iface, c + 3, if (row.completions != 0) row.completions.toString() else "<col=9f9f9f>-</col>")
            player.setComponentText(iface, c + 4, row.invocations.toString())
            player.setComponentText(iface, c + 5, row.raidLevel.toString())
            player.setComponentText(iface, c + 6, ToaInvocationTable.mode(row.raidLevel))
            player.setComponentText(iface, c + 7, if (row.ageMinutes < 1) "<1 min" else "${row.ageMinutes} min") // ONZEKER: raids_partylist_age_write format
        }
        player.setComponentHidden(iface, NONE, rows.isNotEmpty())
        player.attr[SHOWN] = onView
    }

    fun click(player: Player, component: Int) {
        val iface = ToaInterfaces.of(ToaInterfaces.PARTY_LIST) ?: return
        if (component == CLOSE) { player.closeInterface(iface); return }
        val r = component - 17
        if (r !in 0 until ROWS) return
        player.attr[SHOWN]?.invoke(r)
    }
}
