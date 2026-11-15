package gg.rsmod.plugins.content.areas.watson

import com.google.gson.Gson
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.DEATH_COFFER_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.addPreservingAttr

/** OSRS Item Retrieval Service: 90,000 coins, persists, lost on another unsafe death. */
object MimicRecovery {
    const val FEE = 90_000
    val SAVED = AttributeKey<String>(persistenceKey = "mimic_recovery_items")
    val PAID = AttributeKey<Boolean>(persistenceKey = "mimic_recovery_paid")
    private val gson = Gson()
    fun items(player: Player): List<Item> = player.attr[SAVED]?.let { gson.fromJson(it, Array<Item>::class.java).toList() } ?: emptyList()
    fun clear(player: Player) { player.attr.remove(SAVED); player.attr.remove(PAID) }
    fun store(player: Player, items: List<Item>) {
        player.attr[SAVED] = gson.toJson(items.map { Item(it) })
        player.attr.remove(PAID)
    }
    enum class Result { EMPTY, NO_COINS, NO_SPACE, RETRIEVED }

    /** Stage payment and item transfers together; partial retrieval pays only once. */
    fun retrieve(player: Player): Result {
        val stored = items(player)
        if (stored.isEmpty()) return Result.EMPTY
        val inventory = ItemContainer(player.inventory)
        val bank = ItemContainer(player.bank)
        var coffer = player.attr[DEATH_COFFER_ATTR] ?: 0
        val remaining = stored.map { Item(it) }.toMutableList()
        if (player.attr[PAID] != true) {
            val available = coffer.toLong() + bank.getItemCount(995) + inventory.getItemCount(995) + remaining.filter { it.id == 995 }.sumOf { it.amount.toLong() }
            if (available < FEE) return Result.NO_COINS
            var fee = FEE
            val fromCoffer = minOf(fee, coffer); coffer -= fromCoffer; fee -= fromCoffer
            // IRS Wiki: coffer, bank, then currency in the deathbank. Carried coins are also accepted.
            for (container in listOf(bank, inventory)) {
                val take = minOf(fee, container.getItemCount(995))
                if (take > 0) { container.remove(995, take, assureFullRemoval = true); fee -= take }
            }
            for (item in remaining) if (item.id == 995 && fee > 0) {
                val take = minOf(fee, item.amount); item.amount -= take; fee -= take
            }
            check(fee == 0)
        }
        val left = mutableListOf<Item>()
        var transferred = 0
        for (item in remaining.filter { it.amount > 0 }) {
            val added = inventory.addPreservingAttr(item, assureFullInsertion = false)
            transferred += added.completed
            if (added.getLeftOver() > 0) left += Item(item, added.getLeftOver())
        }
        if (transferred == 0 && left.isNotEmpty()) return Result.NO_SPACE
        fun commit(from: ItemContainer, to: ItemContainer) {
            for (slot in 0 until from.capacity) {
                val old = to[slot]; val next = from[slot]
                if (old?.id != next?.id || old?.amount != next?.amount || old?.attr != next?.attr) to[slot] = next
            }
        }
        commit(inventory, player.inventory); commit(bank, player.bank)
        player.attr[DEATH_COFFER_ATTR] = coffer
        if (left.isEmpty()) clear(player) else {
            player.attr[SAVED] = gson.toJson(left)
            player.attr[PAID] = true
        }
        return Result.RETRIEVED
    }
}
