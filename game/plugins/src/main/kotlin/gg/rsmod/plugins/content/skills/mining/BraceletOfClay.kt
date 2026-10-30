package gg.rsmod.plugins.content.skills.mining

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.api.ext.message

/**
 * K12 N430 (found by the N11 census 2026-10-30: the bracelet could be enchanted but did nothing, and its worn "Check" was dead).
 * OSRS Wiki "Bracelet of clay": while worn, clay mined comes out as soft clay; it holds 28 charges, one per clay, then crumbles.
 * Rule and wording from Darkan (Ore.CLAY / Mining.checkBracelet): the charges are the player's, a fresh bracelet counts 28.
 */
object BraceletOfClay {
    const val CHARGES = 28
    val CHARGES_ATTR = AttributeKey<Int>(persistenceKey = "bracelet_of_clay_charges")

    fun worn(player: Player): Boolean = player.hasEquipped(EquipmentType.GLOVES, Items.BRACELET_OF_CLAY)

    fun charges(player: Player): Int = player.attr[CHARGES_ATTR] ?: CHARGES

    fun checkMessage(player: Player): String = "Your bracelet has ${charges(player)} charges remaining."

    /** The item a mined clay becomes: soft clay while the bracelet is worn, spending one charge (crumbling after the last). */
    fun reward(player: Player): Int {
        if (!worn(player)) return Items.CLAY
        val left = charges(player) - 1
        if (left <= 0) {
            player.equipment.remove(Items.BRACELET_OF_CLAY)
            player.attr.remove(CHARGES_ATTR)
            player.message("Your bracelet of clay degrades into dust.")
        } else {
            player.attr[CHARGES_ATTR] = left
        }
        return Items.SOFT_CLAY
    }
}
