package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items

/**
 * What a collection attempt actually did, so the caller can say something truthful about it.
 *
 * [collected] counts offers paid out in full, [partial] offers that owed something the inventory had
 * no room for, and [nothingOwed] means the player has offers but none of them owe anything.
 * Separating the last two matters: they used to look identical from the outside - both simply said
 * nothing at all - which is a large part of why collecting appeared to be broken.
 */
data class CollectionOutcome(
    val collected: Int,
    val partial: Int,
    val nothingOwed: Boolean,
    val noOffers: Boolean,
)

/** What an offer still owes after the inventory has taken as much of it as it can hold. */
data class Leftover(
    val coins: Long,
    val items: Int,
)

/**
 * Paying out Grand Exchange proceeds, shared by every way of asking for them.
 *
 * There are two: the `::ge_collect` command and the `Collect` option that every bank booth, counter
 * and the Grand Exchange bank chest carries in the cache. Retail routes the second through the
 * collection-box interface, which this server deliberately does not implement (see the note at the
 * top of `grand_exchange.plugin.kts`), so the option pays straight into the inventory instead of
 * opening a screen. That is a visible departure from retail, and the honest one while the interface
 * is absent: the alternative leaves the option answering "Nothing interesting happens" while the
 * player's proceeds sit unreachable inside the offer.
 */
object GrandExchangeCollection {
    /**
     * Pays [player] everything they are owed, or only offer [offerId] when one is named.
     *
     * Anything that will not fit is handed straight back to the service and stays collectable, so a
     * full inventory can never destroy proceeds.
     */
    fun collect(
        player: Player,
        service: GrandExchangeService,
        offerId: Long? = null,
    ): CollectionOutcome {
        val username = usernameOf(player)
        val offers = service.offersFor(username)
        val targets = offerId?.let { listOf(it) } ?: offers.map { it.id }
        if (targets.isEmpty()) {
            return CollectionOutcome(collected = 0, partial = 0, nothingOwed = false, noOffers = true)
        }

        var collected = 0
        var partial = 0
        targets.forEach { id ->
            val (coins, items) = service.takeCollectable(username, id) ?: return@forEach
            val leftover =
                payOut(coins, items, offers.find { it.id == id }?.itemId) { item, amount ->
                    player.inventory.add(item = item, amount = amount).completed
                }
            if (leftover.coins > 0 || leftover.items > 0) {
                service.restoreCollectable(id, leftover.coins, leftover.items)
                partial++
            } else {
                collected++
            }
        }

        return CollectionOutcome(
            collected = collected,
            partial = partial,
            nothingOwed = collected == 0 && partial == 0,
            noOffers = false,
        )
    }

    /**
     * Hands [coins] and [items] of [itemId] to [deposit], which returns how much it took, and
     * reports what is left over.
     *
     * [itemId] is null only when the offer has already disappeared from under us; the items are then
     * reported as wholly left over rather than silently dropped, so the caller restores them.
     */
    fun payOut(
        coins: Long,
        items: Int,
        itemId: Int?,
        deposit: (item: Int, amount: Int) -> Int,
    ): Leftover {
        var leftoverCoins = coins
        var leftoverItems = items

        if (coins > 0) {
            /* A stack can only ever be Int.MAX_VALUE, so pay the rest out on the next collection. */
            val taken = deposit(Items.COINS_995, minOf(coins, Int.MAX_VALUE.toLong()).toInt())
            leftoverCoins = coins - taken
        }
        if (items > 0 && itemId != null) {
            leftoverItems = items - deposit(itemId, items)
        }

        return Leftover(leftoverCoins, leftoverItems)
    }

    /** The lines to show for [outcome], in order. */
    fun describe(outcome: CollectionOutcome): List<String> =
        when {
            outcome.noOffers -> listOf("You have no Grand Exchange offers.")
            outcome.nothingOwed -> listOf("You have nothing to collect.")
            outcome.partial > 0 && outcome.collected > 0 ->
                listOf(
                    "Collected your Grand Exchange proceeds.",
                    "You didn't have room for all of it - the rest is still waiting for you.",
                )
            outcome.partial > 0 ->
                listOf("You don't have enough inventory space - your proceeds are still waiting for you.")
            else -> listOf("Collected your Grand Exchange proceeds.")
        }

    /** The stable account key the offers are filed under - the login name, not the display name. */
    private fun usernameOf(player: Player): String = (player as Client).loginUsername
}
