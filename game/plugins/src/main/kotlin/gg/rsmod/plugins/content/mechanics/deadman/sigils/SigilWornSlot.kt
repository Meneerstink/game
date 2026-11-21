package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.setComponentItem

/**
 * SIGIL-WORN-ICOON (owner 21-11, approved design screens/SIGILS/mock/sigil_venster_v3.png panel 2): the sigil slot in the Worn Items
 * tab, as in OSRS Deadman. The slot is three appended components of interface 387 (game tool `SigilWornSlotInterfaceTool`: layer 51,
 * background 52, icon 53 with the baked op "View"). The icon is a sigil item the server sets after every mount of the tab (the client
 * drops server-set state on a re-mount, ROOT_CAUSES #143); a click opens the Attuned Sigils window. No right-click menu of its own
 * (no OSRS source): attune / unattune stay on the item and in the window.
 */
object SigilWornSlot {
    const val INTERFACE_ID = 387

    /** `SigilWornSlotInterfaceTool.ICON`. */
    const val ICON = 53

    /** The slot shows the first attuned sigil; with none attuned the plain sigil the owner's OSRS picture shows (tier 1, combat). */
    val EMPTY_ICON = Sigil.CONSISTENCY.unattunedId

    fun iconItem(player: Player): Int = Sigils.attuned(player).firstOrNull()?.attunedId ?: EMPTY_ICON

    /** Sends the slot's icon: after the tab is mounted and whenever the attuned sigils change. */
    fun refresh(player: Player) {
        player.setComponentItem(INTERFACE_ID, ICON, iconItem(player), 1)
    }

    /**
     * After the Worn Items tab is mounted (the one mount hook of 387 lives in dizanas_quiver.plugin.kts). The slot's place is the
     * cache's (OSRS: left of the head slot, in the weapon column); the Dizana's quiver slot 387:48 stands right of the head slot, so
     * the two never meet (seen in the test client 21-11, screens/SIGILS/run2/worn_quiver2.png).
     */
    fun mount(player: Player) {
        refresh(player)
    }

    fun click(player: Player) {
        AttunedSigilsUi.open(player)
    }
}
