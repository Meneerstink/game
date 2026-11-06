package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import gg.rsmod.plugins.api.ext.setInterfaceEvents

/**
 * N462: OSRS toa_midraid_loot (777) "Supply Bundles" - the Helpful Spirit's choice after two and four paths: Life / Chaos / Power, each
 * shown with its items (inv 807/808/809), one "Choose" per bundle (CS2 toa_midraidloot_init 6777 / draw_bundle 6778 / drawbutton 6779,
 * rebuilt statically by OsrsInterfacePortTool). One choice per player per checkpoint (OSRS Wiki Tombs of Amascut "Helpful Spirit").
 * Component ids: OSRS 777:0..12; steelborder of 777:2 = 13..24 (close 24); per bundle thinbox + 9 item slots + button sprites + text:
 * bundle 1 items 27..35, bundle 2 items 48..56, bundle 3 items 69..77; buttons are OSRS 777:6 / 9 / 12 (op 1 "Choose").
 */
object ToaSuppliesUi {
    const val CLOSE = 24
    val BUTTONS = intArrayOf(6, 9, 12)
    val ITEM_FIRST = intArrayOf(27, 48, 69)
    const val SLOTS = 9
    private const val EVENTS_OP1 = 0x2

    /** What [open] showed; the click calls [onChoose] with the bundle index (0 Life, 1 Chaos, 2 Power). */
    private val PENDING = AttributeKey<(Int) -> Unit>()

    fun open(player: Player, bundles: List<List<Pair<Int, Int>>>, onChoose: (Int) -> Unit) {
        val iface = ToaInterfaces.of(ToaInterfaces.SUPPLIES) ?: return
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(iface, CLOSE, -1..-1, EVENTS_OP1)
        BUTTONS.forEach { player.setInterfaceEvents(iface, it, -1..-1, EVENTS_OP1) }
        for (b in 0 until 3) {
            val items = bundles.getOrElse(b) { emptyList() }
            for (s in 0 until SLOTS) {
                val c = ITEM_FIRST[b] + s
                val item = items.getOrNull(s)
                if (item == null) { player.setComponentHidden(iface, c, true); continue }
                player.setComponentItem(iface, c, item.first, item.second)
                player.setComponentHidden(iface, c, false)
            }
        }
        player.attr[PENDING] = onChoose
    }

    fun click(player: Player, component: Int) {
        val iface = ToaInterfaces.of(ToaInterfaces.SUPPLIES) ?: return
        if (component == CLOSE) { player.closeInterface(iface); return }
        val bundle = BUTTONS.indexOf(component)
        if (bundle < 0) return
        val choose = player.attr[PENDING] ?: return
        player.attr.remove(PENDING) // exactly one choice, also on a double click
        player.closeInterface(iface)
        choose(bundle)
    }
}
