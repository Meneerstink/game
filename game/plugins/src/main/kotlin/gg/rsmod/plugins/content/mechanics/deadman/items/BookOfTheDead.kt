package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.message

/** Book of the Dead rev15403336; shared memoir charges survive destruction (memoirs rev15397301). */
object BookOfTheDead {
    const val ITEM = DmmItems.BOOK_OF_THE_DEAD
    const val MAX_CHARGES = 250
    val CHARGES = AttributeKey<Int>(persistenceKey = "kharedst_memoirs_charges")

    fun charges(player: Player): Int = (player.attr[CHARGES] ?: 0).coerceIn(0, MAX_CHARGES)

    fun check(player: Player) {
        player.message("Charges: ${charges(player)}/$MAX_CHARGES")
    }

    /** Recheck the exact object after the destroy confirmation; a moved/replaced book is never removed. */
    fun destroy(player: Player, slot: Int, expected: Item): Boolean {
        if (slot !in player.inventory.rawItems.indices || expected.id != ITEM || player.inventory[slot] !== expected) return false
        player.inventory[slot] = null
        return true
    }
}
