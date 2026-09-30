package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.player

/**
 * Perdu's Lost Property (owner 2026-09-30; OSRS Wiki "Perdu": "He returns stuff that you've lost, for a price", options Talk-to and
 * Trade - both in this cache's npc 14394). The untradeable death rules ([UntradeableDeathProtection]) already break an item and
 * leave it with the victim for Perdu to repair; the one fate that left nothing behind was [UntradeableFate.DESTROYED] (an unlocked
 * untradeable above level 20 Wilderness): the item vanished and no record of it was kept, so Perdu had nothing to give back.
 *
 * Now that fate records a reclaim ([record]): the item as it was (attributes kept) and its reclaim price - the same repair price
 * the death paid the killer ([RepairPrices.repairPrice]), one price source. Perdu's Trade (and Talk-to) lists them; paying the
 * price (carried coins first, then the bank, as OSRS) hands the item back and removes the record. The records persist with
 * the account.
 */
object PerduReclaim {
    private val SAVED = AttributeKey<String>(persistenceKey = "perdu_reclaims")

    /** Kept per account; the oldest record goes when a new one would exceed this. */
    const val MAX_RECORDS = 50

    /** Options per dialogue page (the options dialogue takes five; the last is "More" / "Cancel"). */
    private const val PER_PAGE = 4

    data class Reclaim(
        val itemId: Int,
        val amount: Int,
        val price: Long,
    )

    fun records(player: Player): List<Reclaim> =
        (player.attr[SAVED] ?: "").split(';').mapNotNull { part ->
            val f = part.split(':')
            if (f.size < 3) return@mapNotNull null
            val id = f[0].toIntOrNull() ?: return@mapNotNull null
            val amount = f[1].toIntOrNull() ?: return@mapNotNull null
            val price = f[2].toLongOrNull() ?: return@mapNotNull null
            Reclaim(id, amount, price)
        }

    private fun store(
        player: Player,
        records: List<Reclaim>,
    ) {
        if (records.isEmpty()) player.attr.remove(SAVED) else player.attr[SAVED] = records.takeLast(MAX_RECORDS).joinToString(";") { "${it.itemId}:${it.amount}:${it.price}" }
    }

    /** A destroyed untradeable: Perdu can give [item] back for [price] coins. */
    fun record(
        player: Player,
        item: Item,
        price: Long,
    ) {
        store(player, records(player) + Reclaim(item.id, item.amount, price.coerceAtLeast(0L)))
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.ITEM) { "perdu reclaim recorded for ${player.username}: ${item.id}x${item.amount} at $price" }
    }

    fun has(player: Player): Boolean = records(player).isNotEmpty()

    /** Perdu's Trade: lists the reclaims, [PER_PAGE] per page, and hands back the one chosen for its price. */
    suspend fun trade(task: QueueTask) {
        val player = task.player
        var page = 0
        while (true) {
            val all = records(player)
            if (all.isEmpty()) {
                task.chatNpc("I don't have anything of yours at the moment. If you lose something valuable, come and see me.", wrap = true)
                return
            }
            val pages = (all.size + PER_PAGE - 1) / PER_PAGE
            page = page.coerceIn(0, pages - 1)
            val shown = all.drop(page * PER_PAGE).take(PER_PAGE)
            val labels = shown.map { "${name(player, it.itemId)}${if (it.amount > 1) " x ${it.amount}" else ""} (${"%,d".format(it.price)} coins)" }.toMutableList()
            labels += if (pages > 1) "More..." else "Nothing, thanks."
            val choice = task.options(*labels.toTypedArray(), title = "Lost Property")
            if (choice == labels.size) {
                if (pages > 1) {
                    page = (page + 1) % pages
                    continue
                }
                return
            }
            val reclaim = shown.getOrNull(choice - 1) ?: return
            if (task.options("Pay ${"%,d".format(reclaim.price)} coins.", "No, thanks.", title = "Reclaim ${name(player, reclaim.itemId)}?") != 1) return
            claim(task, reclaim)
            return
        }
    }

    private suspend fun claim(
        task: QueueTask,
        reclaim: Reclaim,
    ) {
        val player = task.player
        // Re-read: the list may have changed while the dialogue was open.
        val current = records(player).toMutableList()
        val index = current.indexOf(reclaim)
        if (index < 0) return
        if (player.inventory.freeSlotCount < 1) {
            task.chatNpc("You'll need a free inventory space for that.")
            return
        }
        val due = reclaim.price.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        // OSRS Wiki "Perdu": Lost Property takes "coins from either inventory or directly from the bank" - carried coins first.
        val carried = player.inventory.getItemCount(Items.COINS_995)
        val banked = player.bank.getItemCount(Items.COINS_995)
        if (carried.toLong() + banked < due) {
            task.chatNpc("That will be ${"%,d".format(reclaim.price)} coins, which you don't have.")
            return
        }
        val fromInventory = minOf(due, carried)
        val fromBank = due - fromInventory
        if (fromInventory > 0 && !player.inventory.remove(Items.COINS_995, fromInventory, assureFullRemoval = true).hasSucceeded()) return
        if (fromBank > 0 && !player.bank.remove(Items.COINS_995, fromBank, assureFullRemoval = true).hasSucceeded()) {
            if (fromInventory > 0) player.inventory.add(Items.COINS_995, fromInventory)
            return
        }
        if (!player.inventory.add(reclaim.itemId, reclaim.amount, assureFullInsertion = true).hasSucceeded()) {
            // Refund: nothing may be lost between the payment and the hand-over.
            if (fromInventory > 0) player.inventory.add(Items.COINS_995, fromInventory)
            if (fromBank > 0) player.bank.add(Items.COINS_995, fromBank)
            task.chatNpc("You'll need a free inventory space for that.")
            return
        }
        current.removeAt(index)
        store(player, current)
        task.chatNpc("There you go. Try to hold on to it this time.")
    }

    private fun name(
        player: Player,
        id: Int,
    ): String = player.world.definitions.getNullable(ItemDef::class.java, id)?.name ?: "Item $id"
}
