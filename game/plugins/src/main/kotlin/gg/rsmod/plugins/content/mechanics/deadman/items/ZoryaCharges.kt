package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.items.osrs.PoweredStaves
import gg.rsmod.plugins.content.mechanics.deadman.DeadmanInventory

/** Rune transactions are staged on copies: failed inserts, stale prompts and shortages change nothing. */
object ZoryaCharges {
    fun charge(player: Player, slot: Int, expected: Item, requested: Int): Int {
        if (slot !in player.inventory.rawItems.indices || player.inventory[slot] !== expected || requested <= 0) return 0
        val staff = PoweredStaves.staffFor(expected.id)?.takeIf { it.zorya } ?: return 0
        val inventory = ItemContainer(player.inventory)
        val current = PoweredStaves.charges(expected)
        val added = minOf(requested, PoweredStaves.chargesAffordable(staff, current) { inventory.getItemCount(it) })
        if (added <= 0) return 0
        for (cost in staff.chargeCost) if (!inventory.remove(cost.id, cost.amount * added, assureFullRemoval = true).hasSucceeded()) return 0
        inventory[slot] = PoweredStaves.withCharges(expected, current + added)
        DeadmanInventory.publish(inventory, player.inventory)
        return added
    }

    fun uncharge(player: Player, slot: Int, expected: Item): Boolean {
        if (slot !in player.inventory.rawItems.indices || player.inventory[slot] !== expected) return false
        val staff = PoweredStaves.staffFor(expected.id)?.takeIf { it.zorya } ?: return false
        val charges = PoweredStaves.charges(expected)
        if (charges <= 0) return false
        val inventory = ItemContainer(player.inventory)
        inventory[slot] = PoweredStaves.withCharges(expected, 0)
        for (refund in PoweredStaves.refund(staff, charges)) if (!DeadmanInventory.add(inventory, refund)) return false
        DeadmanInventory.publish(inventory, player.inventory)
        return true
    }
}
