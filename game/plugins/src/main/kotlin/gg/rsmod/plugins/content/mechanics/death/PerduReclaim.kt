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
    const val MAX_RECORDS = 200

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

    fun has(player: Player): Boolean = offers(player).isNotEmpty()

    /** One line of Perdu's list: a destroyed item ([record]) or an OSRS Lost Property item ([PerduLostProperty]); price 0 = a lost pet. */
    data class Offer(
        val itemId: Int,
        val amount: Int,
        val price: Long,
        val record: Reclaim? = null,
        val property: PerduLostProperty.Entry? = null,
    ) {
        val free: Boolean get() = price <= 0
    }

    fun offers(player: Player): List<Offer> =
        // N509 (owner 2026-11-01): Perdu only sells back untradeables. A record of a tradeable item (a noted potion the death rules took
        // for an untradeable before notes inherited their item's tradeability) stays in the save but is never offered.
        records(player).filter { !tradeable(player, it.itemId) }.map { Offer(it.itemId, it.amount, it.price, record = it) } +
            PerduLostProperty.available(player).map { Offer(it.itemId, 1, it.price, property = it) }

    private fun tradeable(
        player: Player,
        itemId: Int,
    ): Boolean = RepairPrices.itemDef(player.world.definitions, itemId)?.let { it.tradeable || it.noted } == true

    /** What Perdu sells in his Lost Property window (N382): every lost item with a price. */
    fun paidOffers(player: Player): List<Offer> = offers(player).filter { !it.free }

    /** N414: the lost pets he gives back for free, when talked to. */
    fun freeOffers(player: Player): List<Offer> = offers(player).filter { it.free }

    /** N250 (owner 2026-10-24: "trade zegt dat je niks hebt"): Perdu says why he has nothing - OSRS sells an item back only when it is lost. */
    suspend fun nothingToReturn(task: QueueTask) {
        val player = task.player
        val kept = PerduLostProperty.stillOwned(player).firstOrNull()
        if (kept != null) {
            task.chatNpc("Your ${name(player, kept.first.itemId).replaceFirstChar { it.lowercase() }} isn't lost - it's in ${kept.second}. I only return things you've really lost.", wrap = true)
        } else {
            task.chatNpc("I don't have anything of yours at the moment. If you lose something valuable, come and see me.", wrap = true)
        }
    }

    /** Talk-to: Perdu hands back the player's lost pets and burden trinkets (free), [PER_PAGE] per page; returns whether he had any. */
    suspend fun returnPets(task: QueueTask): Boolean {
        val player = task.player
        PerduLostProperty.observe(player)
        val free = freeOffers(player)
        if (free.isEmpty()) return false
        // N423: a lost burden trinket comes back free too (never lost) - the words fit what he holds, not only pets.
        val onlyPets = free.all { gg.rsmod.plugins.content.skills.summoning.Pets.isPetItem(it.itemId) }
        task.chatNpc(
            if (onlyPets) "I've been looking after some of your pets. You can have them back, free of charge." else "I've been looking after some of your things. You can have them back, free of charge.",
            wrap = true,
        )
        var page = 0
        while (true) {
            val all = freeOffers(player)
            if (all.isEmpty()) return true
            val pages = (all.size + PER_PAGE - 1) / PER_PAGE
            page = page.coerceIn(0, pages - 1)
            val shown = all.drop(page * PER_PAGE).take(PER_PAGE)
            val labels = shown.map { name(player, it.itemId) }.toMutableList()
            labels += if (pages > 1) "More..." else "Nothing, thanks."
            val choice = task.options(*labels.toTypedArray(), title = if (onlyPets) "Which pet would you like back?" else "What would you like back?")
            if (choice == labels.size) {
                if (pages > 1) {
                    page = (page + 1) % pages
                    continue
                }
                return true
            }
            val offer = shown.getOrNull(choice - 1) ?: return true
            task.chatNpc(claim(player, offer) ?: "There you go. Try to hold on to it this time.", wrap = true)
            return true
        }
    }

    /**
     * Hands [offer] back to [player], paying its price from the carried coins first, then the bank (OSRS Wiki "Perdu": Lost
     * Property takes "coins from either inventory or directly from the bank"). Returns null when it was handed over, else what
     * Perdu says instead. The shop window ([PerduShop]) and the pet dialogue share it.
     */
    fun claim(
        player: Player,
        offer: Offer,
    ): String? {
        // Re-read: the list may have changed while the window or the dialogue was open.
        val current = records(player).toMutableList()
        val index = offer.record?.let { current.indexOf(it) } ?: -1
        if (offer.record != null && index < 0) return "I don't have that any more."
        if (offer.property != null && offer.property !in PerduLostProperty.available(player)) return "You don't need that back - you still have it."
        // OSRS Wiki "Perdu" (25 Feb 2026): "Perdu will no longer sell you an item that you don't have space to carry."
        if (player.inventory.freeSlotCount < 1) return "You'll need a free inventory space for that."
        val consumes = offer.property?.consumes
        if (consumes != null && player.inventory.getItemCount(consumes) < 1) return "I'll need ${name(player, consumes).replaceFirstChar { it.lowercase() }} for that one."
        val due = offer.price.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val carried = player.inventory.getItemCount(Items.COINS_995)
        val banked = player.bank.getItemCount(Items.COINS_995)
        if (carried.toLong() + banked < due) return "That will be ${priceText(offer.price)}, which you don't have."
        val fromInventory = minOf(due, carried)
        val fromBank = due - fromInventory
        if (fromInventory > 0 && !player.inventory.remove(Items.COINS_995, fromInventory, assureFullRemoval = true).hasSucceeded()) return "Something went wrong - try again."
        if (fromBank > 0 && !player.bank.remove(Items.COINS_995, fromBank, assureFullRemoval = true).hasSucceeded()) {
            if (fromInventory > 0) player.inventory.add(Items.COINS_995, fromInventory)
            return "Something went wrong - try again."
        }
        fun refund() {
            if (fromInventory > 0) player.inventory.add(Items.COINS_995, fromInventory)
            if (fromBank > 0) player.bank.add(Items.COINS_995, fromBank)
        }
        if (consumes != null && !player.inventory.remove(consumes, 1, assureFullRemoval = true).hasSucceeded()) {
            refund()
            return "I'll need ${name(player, consumes).replaceFirstChar { it.lowercase() }} for that one."
        }
        if (!player.inventory.add(offer.itemId, offer.amount, assureFullInsertion = true).hasSucceeded()) {
            // Refund: nothing may be lost between the payment and the hand-over.
            refund()
            if (consumes != null) player.inventory.add(consumes, 1)
            return "You'll need a free inventory space for that."
        }
        if (offer.record != null) {
            current.removeAt(index)
            store(player, current)
        }
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.ITEM) { "perdu returned ${offer.itemId}x${offer.amount} to ${player.username} for ${offer.price}" }
        return null
    }
    /** "1,500 coins", or "free" (N414: a lost pet). */
    fun priceText(price: Long): String = if (price <= 0) "free" else "${"%,d".format(java.util.Locale.US, price)} coins"

    private fun name(
        player: Player,
        id: Int,
    ): String = player.world.definitions.getNullable(ItemDef::class.java, id)?.name ?: "Item $id"
}
