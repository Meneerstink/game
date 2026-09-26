package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.DEATH_COFFER_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items

/**
 * Pays a reclamation fee the OSRS way: "The fee is deducted from Death's Coffer, if available, or else from the bank"
 * (OSRS Wiki "Death/Item Recovery Fees"), and "Cash is always acceptable" (Death, first-death tutorial). The coffer is
 * drawn first, then coins carried, then coins in the bank. Nothing is taken unless the whole fee can be paid.
 */
object DeathPayment {
    fun coffer(player: Player): Int = player.attr[DEATH_COFFER_ATTR] ?: 0

    /** Everything the three sources can pay together. */
    fun available(player: Player): Long =
        coffer(player).toLong() + player.inventory.getItemCount(Items.COINS_995) + player.bank.getItemCount(Items.COINS_995)

    /**
     * Takes [fee] coins from the coffer, then the inventory, then the bank.
     * @return false (and nothing taken) when the three together cannot cover the fee.
     */
    fun pay(
        player: Player,
        fee: Long,
    ): Boolean {
        if (fee <= 0L) return true
        if (available(player) < fee) return false
        var left = fee
        val fromCoffer = minOf(left, coffer(player).toLong())
        if (fromCoffer > 0) {
            player.attr[DEATH_COFFER_ATTR] = (coffer(player) - fromCoffer).toInt()
            left -= fromCoffer
        }
        val fromInventory = minOf(left, player.inventory.getItemCount(Items.COINS_995).toLong()).toInt()
        if (fromInventory > 0) {
            player.inventory.remove(Item(Items.COINS_995, fromInventory), assureFullRemoval = true)
            left -= fromInventory
        }
        if (left > 0) {
            player.bank.remove(Item(Items.COINS_995, left.toInt()), assureFullRemoval = true)
        }
        return true
    }
}
