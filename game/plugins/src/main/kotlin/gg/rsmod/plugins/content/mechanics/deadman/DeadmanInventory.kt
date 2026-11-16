package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item

/** Stage a transaction on a deep copy; publish only after every insertion/removal succeeds. */
internal object DeadmanInventory {
    fun add(container: ItemContainer, item: Item): Boolean {
        if (item.amount <= 0) return false
        if (!item.hasAnyAttr()) {
            return container.add(item, applyCreationRedirect = false).hasSucceeded()
        }
        // Container.add(Item) discards attributes. Charged items must retain separate slots.
        if (container.freeSlotCount < item.amount) return false
        repeat(item.amount) { container[container.nextFreeSlot] = Item(item, 1) }
        return true
    }

    fun publish(from: ItemContainer, to: ItemContainer) {
        require(from.capacity == to.capacity)
        from.rawItems.indices.forEach { to[it] = from[it]?.let(::Item) }
    }
}
