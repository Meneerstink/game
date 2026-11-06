package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentText

/**
 * N462: OSRS toa_lobby (773) - the overlay in the Jaltevas lobby: header "No Party" / "Party" / "Step Inside Now!" (CS2
 * toa_lobby_update_header 6613 on %toa_client_partystatus 0 / 1 / 2) and the party's names under it (773:5).
 * "Step Inside Now!" flashes in OSRS (tob_hud_nameflasher); 667 has no server colour message - shown in the base orange. ONZEKER (flash).
 */
object ToaLobbyHudUi {
    const val HEADER = 4
    const val NAMES = 5

    enum class Status { NO_PARTY, PARTY, STEP_INSIDE }

    fun open(player: Player) {
        val iface = ToaInterfaces.of(ToaInterfaces.LOBBY) ?: return
        player.openInterface(iface, InterfaceDestination.PVP_OVERLAY)
    }

    fun close(player: Player) { ToaInterfaces.of(ToaInterfaces.LOBBY)?.let { player.closeInterface(it) } }

    fun draw(player: Player, status: Status, names: List<String>) {
        val iface = ToaInterfaces.of(ToaInterfaces.LOBBY) ?: return
        val header = when (status) { Status.NO_PARTY -> "No Party"; Status.PARTY -> "Party"; Status.STEP_INSIDE -> "Step Inside Now!" }
        player.setComponentText(iface, HEADER, "<col=ff981f>$header</col>")
        player.setComponentText(iface, NAMES, names.joinToString("<br>"))
    }
}
