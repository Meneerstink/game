package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item

/**
 * Item moves for the gravestone and Death's Office containers. Unlike `ItemContainer.add`, a stack only ever merges with
 * a stack of the same id *and the same attributes*: a fee-free (paid or familiar) stack and a chargeable stack of the same
 * item must stay apart, or one of them would silently change price. Nothing is ever dropped here - every method reports
 * how much it actually moved and the caller decides what happens to the rest.
 */
object DeathStorage {
    fun stackable(
        definitions: DefinitionSet,
        itemId: Int,
    ): Boolean = definitions.getNullable(ItemDef::class.java, itemId)?.stackable == true

    /** How many units of [item] [container] can take right now. */
    fun room(
        definitions: DefinitionSet,
        container: ItemContainer,
        item: Item,
    ): Int {
        if (!stackable(definitions, item.id)) return minOf(item.amount, container.freeSlotCount)
        val existing = findStack(container, item)
        if (existing >= 0) return minOf(item.amount.toLong(), Int.MAX_VALUE.toLong() - container[existing]!!.amount).toInt()
        return if (container.freeSlotCount > 0) item.amount else 0
    }

    /** Inserts as much of [item] as fits (attributes kept); returns the amount inserted. */
    fun insert(
        definitions: DefinitionSet,
        container: ItemContainer,
        item: Item,
    ): Int {
        if (item.amount <= 0) return 0
        if (stackable(definitions, item.id)) {
            val existing = findStack(container, item)
            if (existing >= 0) {
                val current = container[existing]!!
                val add = minOf(item.amount.toLong(), Int.MAX_VALUE.toLong() - current.amount).toInt()
                if (add > 0) container[existing] = Item(current.id, current.amount + add).copyAttr(current)
                return add
            }
            val slot = firstFree(container)
            if (slot < 0) return 0
            container[slot] = Item(item.id, item.amount).copyAttr(item)
            return item.amount
        }
        var inserted = 0
        while (inserted < item.amount) {
            val slot = firstFree(container)
            if (slot < 0) break
            container[slot] = Item(item.id, 1).copyAttr(item)
            inserted++
        }
        return inserted
    }

    /** Removes [amount] from the stack in [slot] (or the whole stack); returns what was removed, attributes kept. */
    fun takeFromSlot(
        container: ItemContainer,
        slot: Int,
        amount: Int,
    ): Item? {
        val current = container[slot] ?: return null
        val take = minOf(amount, current.amount)
        if (take <= 0) return null
        container[slot] = if (take == current.amount) null else Item(current.id, current.amount - take).copyAttr(current)
        return Item(current.id, take).copyAttr(current)
    }

    private fun findStack(
        container: ItemContainer,
        item: Item,
    ): Int {
        for (slot in 0 until container.capacity) {
            val other = container[slot] ?: continue
            if (other.id == item.id && other.attr == item.attr) return slot
        }
        return -1
    }

    private fun firstFree(container: ItemContainer): Int {
        for (slot in 0 until container.capacity) {
            if (container[slot] == null) return slot
        }
        return -1
    }
}
