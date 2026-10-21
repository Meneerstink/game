package gg.rsmod.plugins.content.mechanics.trading.impl

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

    /**
     * The Grand Exchange value of [item] times its amount, in Long (A15, owner 2026-10-11 "trade screen doesnt show the correct grand
     * exchange value"; donors Darkan Trade.refreshTradeWealth / Novite Trade.getTradeWealth: wealth = market price * amount). [unitPrice] is
     * the server's one price function ([gg.rsmod.plugins.content.mechanics.exchange.GePrice.unit]); a note is valued as its unnoted item there.
     */
    fun value(
        item: Item?,
        unitPrice: (Int) -> Int,
    ): Long {
        if (item == null) return 0L
        return unitPrice(item.id).toLong().coerceAtLeast(0L) * item.amount.toLong().coerceAtLeast(0L)
    }

    /** Audit E-11: the total is summed in Long; `price * amount` in Int wrapped negative on a large stack. */
    fun value(
        container: ItemContainer,
        unitPrice: (Int) -> Int,
    ): Long = container.rawItems.sumOf { value(it, unitPrice) }

    /** The chat line of the trade window's "Value" option (donors Novite Trade.sendValue / Darkan Trade.sendValue). */
    fun valueMessage(
        name: String,
        price: Int,
    ): String = "$name: market price is ${String.format(java.util.Locale.US, "%,d", price)} coins."

    /** The 667 menu opcodes of the sixth option of an item grid (Offer, Offer-5, Offer-10, Offer-All, Offer-X, **Value**). */
    val VALUE_OPCODES = setOf(18, 91)

    /** The wealth varc is an Int: a value above Int.MAX_VALUE shows as Int.MAX_VALUE, never as a wrapped number. */
    fun displayValue(value: Long): Int = value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}
