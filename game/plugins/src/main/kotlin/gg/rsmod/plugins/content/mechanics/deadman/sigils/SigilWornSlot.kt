package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.setComponentItem

/** OSRS 387:29, scripts 5235/5237: the first occupied attunement slot, otherwise enum 4040 key 53. */
object SigilWornSlot {
    const val INTERFACE_ID = 387
    const val LAYER = 51

    /** `SigilWornSlotInterfaceTool.ICON`. */
    const val ICON = 53

    /** OSRS enum 4040 key 53 = 25991, unattuned Sigil of Resilience; not Consistency. */
    val EMPTY_ICON = Sigil.RESILIENCE.unattunedId

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
