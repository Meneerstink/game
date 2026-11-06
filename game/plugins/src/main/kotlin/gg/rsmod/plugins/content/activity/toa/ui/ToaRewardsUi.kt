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
 * N462: OSRS toa_chests (771) "Tombs of Amascut" - the personal reward chest: the chest model, the items and the buttons Take-all (771:6,
 * op 1), Bank-all (771:4, op 6) and Discard-all (771:8, op 6) (OSRS cache 2686 interface 771, actions from 30-interface-componenten.tsv).
 * Component ids: OSRS 771:0..10; steelborder of 771:1 = 11..22 (close 22); item slots 23..28 (layout ONZEKER, see OsrsInterfacePortTool).
 */
object ToaRewardsUi {
    const val BANK_ALL = 4
    const val TAKE_ALL = 6
    const val DISCARD_ALL = 8
    const val CLOSE = 22
    const val ITEMS = 23
    const val SLOTS = 6
    private const val OP1 = 0x2
    private const val OP6 = 1 shl 6

    enum class Action { TAKE, BANK, DISCARD }

    private val HANDLER = AttributeKey<(Action) -> Unit>()

    fun open(player: Player, items: List<Pair<Int, Int>>, onAction: (Action) -> Unit) {
        val iface = ToaInterfaces.of(ToaInterfaces.REWARDS) ?: return
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(iface, CLOSE, -1..-1, OP1)
        player.setInterfaceEvents(iface, TAKE_ALL, -1..-1, OP1)
        player.setInterfaceEvents(iface, BANK_ALL, -1..-1, OP6)
        player.setInterfaceEvents(iface, DISCARD_ALL, -1..-1, OP6)
        refresh(player, items)
        player.attr[HANDLER] = onAction
    }

    fun refresh(player: Player, items: List<Pair<Int, Int>>) {
        val iface = ToaInterfaces.of(ToaInterfaces.REWARDS) ?: return
        for (s in 0 until SLOTS) {
            val item = items.getOrNull(s)
            if (item == null) { player.setComponentHidden(iface, ITEMS + s, true); continue }
            player.setComponentItem(iface, ITEMS + s, item.first, item.second)
            player.setComponentHidden(iface, ITEMS + s, false)
        }
    }

    fun click(player: Player, component: Int) {
        val iface = ToaInterfaces.of(ToaInterfaces.REWARDS) ?: return
        val action = when (component) {
            CLOSE -> { player.closeInterface(iface); return }
            TAKE_ALL -> Action.TAKE
            BANK_ALL -> Action.BANK
            DISCARD_ALL -> Action.DISCARD
            else -> return
        }
        player.attr[HANDLER]?.invoke(action)
    }
}
