package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.GRAVESTONE_TILE_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute

/** N591/V6a: one synchronous transaction. Paid grave items enter existing office storage fee-free. */
object EarlyDeathCollection {
    class Quote internal constructor(val generation: Long, val tile: Int?, val items: List<Item?>, val fee: Long)
    enum class Outcome { COLLECTED, STALE, NO_ROOM, CANNOT_AFFORD }

    private fun snapshot(player: Player): List<Item?> = player.gravestone.items.map { it?.let { Item(it.id, it.amount).copyAttr(it) } }

    fun quote(player: Player, value: ItemRiskValueProvider): Quote? {
        if (!Gravestone.exists(player) || Gravestone.ticksLeft(player) <= 0 || player.gravestone.isEmpty) return null
        val items = snapshot(player)
        return Quote(player.attr[Gravestone.GENERATION] ?: 0L, player.attr[GRAVESTONE_TILE_ATTR], items,
            DeathFees.earlyCollectionFee(items.filterNotNull(), value))
    }

    fun collect(player: Player, quote: Quote): Outcome {
        if (!Gravestone.exists(player) || Gravestone.ticksLeft(player) <= 0 ||
            quote.generation != (player.attr[Gravestone.GENERATION] ?: 0L) || quote.tile != player.attr[GRAVESTONE_TILE_ATTR]) return Outcome.STALE
        val current = snapshot(player)
        if (current.size != quote.items.size || current.indices.any { i ->
                val a = current[i]; val b = quote.items[i]
                a?.id != b?.id || a?.amount != b?.amount || a?.attr != b?.attr
            }) return Outcome.STALE
        val plan = ItemContainer(player.deathRecovery)
        for (item in current.filterNotNull()) {
            val paid = Item(item.id, item.amount).copyAttr(item).putAttr(ItemAttribute.DEATH_FEE_FREE, 1)
            if (DeathStorage.insert(player.world.definitions, plan, paid) != paid.amount) return Outcome.NO_ROOM
        }
        if (!DeathPayment.pay(player, quote.fee)) return Outcome.CANNOT_AFFORD
        // No suspension or fallible insert after payment. Item arrays are committed on the world thread before any save can run.
        for (i in 0 until plan.capacity) player.deathRecovery[i] = plan[i]
        for (i in 0 until player.gravestone.capacity) player.gravestone[i] = null
        Gravestone.clear(player)
        return Outcome.COLLECTED
    }
}
