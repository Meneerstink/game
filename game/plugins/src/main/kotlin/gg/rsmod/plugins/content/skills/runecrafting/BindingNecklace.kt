package gg.rsmod.plugins.content.skills.runecrafting

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.refreshBonuses

/**
 * Binding necklace (OSRS Wiki "Binding necklace", read 2026-09-17): "It has 16 uses (one use is one click on an altar) and will be destroyed
 * once all are used"; "binding necklace charges are stored per player, not per necklace". The disintegration message wording is not
 * quoted on the page (ADAPTED).
 */
object BindingNecklace {
    const val CHARGES = 16

    /** Uses left on the player's current binding necklace (absent = a fresh 16). */
    val USES_LEFT = AttributeKey<Int>(persistenceKey = "binding_necklace_uses_left")

    fun usesLeft(player: Player): Int = player.attr[USES_LEFT] ?: CHARGES

    fun useCharge(player: Player) {
        val left = usesLeft(player) - 1
        if (left > 0) {
            player.attr[USES_LEFT] = left
            return
        }
        player.attr.remove(USES_LEFT)
        if (player.equipment[EquipmentType.AMULET.id]?.id == Items.BINDING_NECKLACE) {
            player.equipment[EquipmentType.AMULET.id] = null
            player.refreshBonuses()
        }
        player.message("Your binding necklace has disintegrated.")
    }
}
