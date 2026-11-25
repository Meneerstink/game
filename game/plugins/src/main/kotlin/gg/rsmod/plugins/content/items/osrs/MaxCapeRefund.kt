package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.grantOrRefund

/**
 * N410 deel 2 (owner 2026-11-25 'Max refund': 79,200 gp = 80%): OSRS Mac buys a Max cape back for 80% of its price when the
 * player hands over the cape AND the hood (OSRS Wiki "Max cape"); here 80% of the owner's 99,000 (max_cape.plugin.kts).
 * Only the plain wearable pair ([MaxCapes.MAX_CAPE] + [MaxCapes.MAX_HOOD]) from the backpack - a combined max cape is separated
 * first (knife). Dupe-safe: cape and hood leave first, the coins come after; if the coins don't fit, both are given back.
 */
object MaxCapeRefund {
    const val PRICE = 99_000
    const val REFUND = PRICE * 80 / 100

    fun hasPair(player: Player): Boolean =
        player.inventory.contains(MaxCapes.MAX_CAPE) && player.inventory.contains(MaxCapes.MAX_HOOD)

    /** Takes one cape + one hood and pays [REFUND] coins; false (nothing changed) without the pair or room for the coins. */
    fun sellBack(player: Player): Boolean {
        if (!hasPair(player)) return false
        val inventory = player.inventory
        if (!inventory.remove(MaxCapes.MAX_CAPE, 1, assureFullRemoval = true).hasSucceeded()) return false
        if (!inventory.remove(MaxCapes.MAX_HOOD, 1, assureFullRemoval = true).hasSucceeded()) {
            inventory.add(MaxCapes.MAX_CAPE, 1, assureFullInsertion = true)
            return false
        }
        return player.grantOrRefund(Items.COINS_995, REFUND, listOf(Item(MaxCapes.MAX_CAPE), Item(MaxCapes.MAX_HOOD)))
    }
}
