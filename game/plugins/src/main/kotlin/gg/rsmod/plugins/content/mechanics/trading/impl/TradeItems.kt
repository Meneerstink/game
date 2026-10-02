package gg.rsmod.plugins.content.mechanics.trading.impl

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.ext.moveEach

/**
 * The item bookkeeping of a [TradeSession], kept free of [gg.rsmod.game.model.entity.Player] so it can be unit tested.
 */
object TradeItems {
    /**
     * Audit E-04: moves up to [amount] of [itemId] from [from] to [to] one slot at a time, starting at [startSlot] and
     * wrapping round, and every moved slot keeps its *own* attributes.
     *
     * Offer-All / Remove-All used to move `amount` of the id with the attributes of the clicked item, so five rings of
     * suffering with one full charge became five full rings. A stack that is only partly moved keeps its attributes on
     * both halves (a stack has one attribute map).
     *
     * Returns the slots of [from] that were emptied (the trade screen marks those with a red flag).
     */
    fun moveEach(
        from: ItemContainer,
        to: ItemContainer,
        itemId: Int,
        amount: Int,
        startSlot: Int,
    ): List<Int> = from.moveEach(to, itemId, amount, startSlot).emptied

    /** Amount per (id, attributes): two items are only the same when their attributes are equal too. */
    fun totals(vararg containers: ItemContainer): Map<Pair<Int, Map<ItemAttribute, Int>>, Long> {
        val totals = HashMap<Pair<Int, Map<ItemAttribute, Int>>, Long>()
        containers.forEach { container ->
            container.rawItems.forEach { item ->
                if (item != null) totals.merge(item.id to item.attr.toMap(), item.amount.toLong(), Long::plus)
            }
        }
        return totals
    }

    /**
     * Audit E-04: [real] must hold exactly the [snapshot] inventory plus the [offer], compared per id *and* attributes,
     * so a charge change on an offered item (or a swapped exemplar) cancels the trade instead of being written back.
     */
    fun matchesSnapshot(
        real: ItemContainer,
        snapshot: ItemContainer,
        offer: ItemContainer,
    ): Boolean = totals(snapshot, offer) == totals(real)

    /** Cache value of [item] (its unnoted form) times its amount, in Long. */
    fun value(
        definitions: DefinitionSet,
        item: Item?,
    ): Long {
        if (item == null) return 0L
        val cost = definitions.get(ItemDef::class.java, item.toUnnoted(definitions).id).cost
        return cost.toLong().coerceAtLeast(0L) * item.amount.toLong().coerceAtLeast(0L)
    }

    /** Audit E-11: the total is summed in Long; `cost * amount` in Int wrapped negative on a large stack. */
    fun value(
        definitions: DefinitionSet,
        container: ItemContainer,
    ): Long = container.rawItems.sumOf { value(definitions, it) }

    /** The wealth varc is an Int: a value above Int.MAX_VALUE shows as Int.MAX_VALUE, never as a wrapped number. */
    fun displayValue(value: Long): Int = value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}
