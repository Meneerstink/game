package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents

/**
 * N462: OSRS toa_midraidloot_bag (778) "Raid Supplies" - the supplies bag (inv 810): title 778:1, close 778:2 (op "Close"), 28 slots in a
 * 4-wide grid, each with Withdraw-1 / Withdraw-5 / Withdraw-All / Drop / Examine (CS2 toa_midraidloot_bag_init 6782 / draw 6784).
 * Component ids: OSRS 778:0..5; thinstonebox of 778:3 = 6..7; slots 8..35.
 */
object ToaBagUi {
    const val TITLE = 1
    const val CLOSE = 2
    const val SLOTS = 8
    const val SLOT_COUNT = 28
    private const val EVENTS_OPS = 0x2 or 0x4 or 0x8 or 0x10 or 0x20

    enum class Action { WITHDRAW_1, WITHDRAW_5, WITHDRAW_ALL, DROP, EXAMINE }

    private val HANDLER = AttributeKey<(Int, Action) -> Unit>()

    fun open(player: Player, contents: List<Pair<Int, Int>?>, onAction: (Int, Action) -> Unit) {
        val iface = ToaInterfaces.of(ToaInterfaces.BAG) ?: return
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        player.setComponentText(iface, TITLE, "Raid Supplies")
        player.setInterfaceEvents(iface, CLOSE, -1..-1, 0x2)
        for (s in 0 until SLOT_COUNT) player.setInterfaceEvents(iface, SLOTS + s, -1..-1, EVENTS_OPS)
        refresh(player, contents)
        player.attr[HANDLER] = onAction
    }

    fun refresh(player: Player, contents: List<Pair<Int, Int>?>) {
        val iface = ToaInterfaces.of(ToaInterfaces.BAG) ?: return
        for (s in 0 until SLOT_COUNT) {
            val item = contents.getOrNull(s)
            if (item == null) { player.setComponentHidden(iface, SLOTS + s, true); continue }
            player.setComponentItem(iface, SLOTS + s, item.first, item.second)
            player.setComponentHidden(iface, SLOTS + s, false)
        }
    }

    /** [option] 1..5 as SlayerRewardsUi.opOf maps the button opcode. */
    fun click(player: Player, component: Int, option: Int) {
        val iface = ToaInterfaces.of(ToaInterfaces.BAG) ?: return
        if (component == CLOSE) { player.closeInterface(iface); return }
        val slot = component - SLOTS
        if (slot !in 0 until SLOT_COUNT) return
        val action = Action.values().getOrNull(option - 1) ?: return
        player.attr[HANDLER]?.invoke(slot, action)
    }
}
