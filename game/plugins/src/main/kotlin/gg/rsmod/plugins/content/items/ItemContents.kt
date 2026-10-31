package gg.rsmod.plugins.content.items

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item

/** Stage the entire contents change before touching the real inventory. No yields between check and commit. */
object ItemContents {
    const val NO_SPACE = "You don't have enough inventory space."

    fun exchange(inventory: ItemContainer, slot: Int, expected: Int, outputs: List<Item>, requireFreeSlot: Boolean = false): Boolean {
        val held = inventory[slot] ?: return false
        if (held.id != expected || held.amount != 1) return false
        if (requireFreeSlot && inventory.isFull) return false
        val staged = ItemContainer(inventory)
        staged[slot] = null
        for (item in outputs) {
            if (!staged.add(item, assureFullInsertion = true, beginSlot = slot).hasSucceeded()) return false
        }
        for (i in 0 until inventory.capacity) {
            val before = inventory[i]
            val after = staged[i]
            if (before?.id != after?.id || before?.amount != after?.amount || before?.attr != after?.attr) inventory[i] = after
        }
        return true
    }
}
