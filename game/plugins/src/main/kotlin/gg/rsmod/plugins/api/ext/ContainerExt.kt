package gg.rsmod.plugins.api.ext

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.ItemTransaction
import gg.rsmod.game.model.item.Item

/**
 * Transfer [item] from [this] container to [to] container.
 *
 * @return
 * The removal [ItemTransaction].
 */
fun ItemContainer.transfer(
    to: ItemContainer,
    item: Item,
    fromSlot: Int = -1,
    toSlot: Int = -1,
    note: Boolean = false,
    unnote: Boolean = false,
): ItemTransaction? {
    check(item.amount > 0)

    /*
     * Get the maximum amount of the item that can be transferred.
     */
    val amount = Math.min(item.amount, getItemCount(item.id))

    /*
     * Copy the item with the corrected amount.
     */
    val copy = Item(item, amount)

    /*
     * If we're transferring the whole item, make sure to copy its attributes.
     */
    if (amount >= item.amount) {
        copy.copyAttr(item)
    }

    /*
     * Turn the initial item into its noted or unnoted form, depending on [note]
     * and [unnote].
     */
    val finalItem =
        if (note) {
            copy.toNoted(definitions)
        } else if (unnote) {
            copy.toUnnoted(definitions)
        } else {
            copy
        }

    // Audit E-06: a transfer moves an existing item, so the charged-item creation redirect must not change its id.
    val add = to.add(finalItem.id, finalItem.amount, assureFullInsertion = false, beginSlot = toSlot, applyCreationRedirect = false)
    if (add.completed == 0) {
        return null
    }

    val remove = remove(item.id, add.completed, assureFullRemoval = true, beginSlot = fromSlot)
    if (remove.completed == 0) {
        add.revert(to)
        return null
    }

    /*
     * The first item added to [to] should copy any attributes that were on
     * the initial copy of [item].
     */
    add.first().item.copyAttr(copy)

    return remove
}

/**
 * Adds [item] and carries its attributes (charges, degrade state) onto every slot the add created.
 * `ItemContainer.add(Item)` only copies id and amount, so a charged item handed over by trade or
 * given back by death recovery silently lost its charges.
 */
fun ItemContainer.addPreservingAttr(
    item: Item,
    assureFullInsertion: Boolean = true,
    beginSlot: Int = -1,
): ItemTransaction {
    // Audit E-06: the item already exists (trade, death recovery), so it keeps its id; its attributes follow below.
    val transaction =
        add(item.id, item.amount, assureFullInsertion = assureFullInsertion, beginSlot = beginSlot, applyCreationRedirect = false)
    if (item.hasAnyAttr()) {
        transaction.items.forEach { it.item.copyAttr(item) }
    }
    return transaction
}

fun ItemTransaction.revert(from: ItemContainer) {
    items.forEach {
        from.remove(item = it.item, beginSlot = it.slot)
    }
}
