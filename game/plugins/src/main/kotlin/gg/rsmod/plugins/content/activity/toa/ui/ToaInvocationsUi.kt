package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setInterfaceEvents

/**
 * N462: OSRS toa_invocations (776) - "Tombs of Amascut Invocations", the list the Invocation Board shows: every invocation with its
 * icon, name, effect and "Increases Raid Level by N." (CS2 toa_invocations_init 6609 / script6610, rebuilt statically by
 * OsrsInterfacePortTool). Component ids: OSRS 776:0..5; rows under 776:5 = 6..225 (5 per invocation); steelborder of 776:2 = 226..237
 * (title 227, close 237); thinbox of 776:3 = 238..239.
 */
object ToaInvocationsUi {
    const val CLOSE = 237
    private const val EVENTS_OP1 = 0x2

    fun open(player: Player) {
        val iface = ToaInterfaces.of(ToaInterfaces.INVOCATIONS) ?: return
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(iface, CLOSE, -1..-1, EVENTS_OP1)
    }

    fun click(player: Player, component: Int) {
        val iface = ToaInterfaces.of(ToaInterfaces.INVOCATIONS) ?: return
        if (component == CLOSE) player.closeInterface(iface)
    }
}
