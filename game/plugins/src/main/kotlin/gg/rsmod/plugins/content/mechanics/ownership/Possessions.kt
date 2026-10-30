package gg.rsmod.plugins.content.mechanics.ownership

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.ContainerKey
import gg.rsmod.game.model.entity.Player

/**
 * K01 deel 3 (2026-10-25): the one "does the player still have it somewhere?" check, for Perdu's Lost Property and lost pets
 * (N414) and for the pet duplicate rule (OSRS Wiki "Pet": a pet you already own gives nothing). OSRS Lost Property sells an item
 * back only when it is truly lost - "none of the item on them, in their bank or in the POH". Before, Perdu looked in the
 * inventory, equipment, bank, gravestone and Death's Office and the pets in the follower, inventory and bank only: an item in the
 * looting bag, a familiar's Beast of Burden or a Grand Exchange offer counted as lost, so it could be bought back twice.
 *
 * Every container the player has ([Player.containers]: inventory, equipment, bank, gravestone, Death's Office, the looting bag,
 * every Beast of Burden pack, the random event gift), the follower, and the Grand Exchange (an item a sell offer still holds or
 * one waiting to be collected). A trade in progress keeps the items in the real inventory until it completes, so it is covered.
 */
object Possessions {
    /** Where an item in [key] is, as Perdu says it ("Your ... isn't lost - it's in ..."). */
    fun label(key: ContainerKey): String =
        when {
            key.name == "inventory" -> "your inventory"
            key.name == "equipment" -> "your equipment"
            key.name == "bank" -> "your bank"
            key.name == "gravestone" -> "your gravestone"
            key.name == "death_recovery" -> "Death's Office"
            key.name == "looting_bag" -> "your looting bag"
            key.name.startsWith("bob_pet_") -> "your pet's pack"
            key.name.startsWith("bob_") -> "your familiar's pack"
            key.name == "random_event_gift" -> "your random event gift"
            else -> "your ${key.name.replace('_', ' ')}"
        }

    /** The containers searched, in the order Perdu names them. */
    private fun containers(player: Player): List<Pair<ContainerKey, ItemContainer>> {
        val order = listOf("inventory", "equipment", "bank", "looting_bag", "gravestone", "death_recovery")
        return player.containers.entries.map { it.key to it.value }
            .sortedBy { (key, _) -> order.indexOf(key.name).let { if (it < 0) order.size else it } }
    }

    /** Where [player] still has one of [ids] (noted forms count), or null when none of them is anywhere. */
    fun whereOwned(
        player: Player,
        ids: Set<Int>,
    ): String? {
        if (ids.isEmpty()) return null
        val definitions = player.world.definitions
        containers(player).firstOrNull { (_, container) ->
            container.rawItems.any { it != null && it.toUnnoted(definitions).id in ids }
        }?.let { return label(it.first) }
        // The pet that is out: its item is recorded on the player (it comes back with them at login), not in a container.
        val follower = gg.rsmod.plugins.content.skills.summoning.Pets.itemOf(player)
        if (follower != null && follower in ids) return "following you"
        if (onGrandExchange(player, ids)) return "the Grand Exchange"
        return null
    }

    fun owns(
        player: Player,
        ids: Set<Int>,
    ): Boolean = whereOwned(player, ids) != null

    /** How many of [id] (noted forms count) [player] has in all their containers together (N423: one trinket per bound pet). */
    fun count(
        player: Player,
        id: Int,
    ): Int {
        val definitions = player.world.definitions
        return player.containers.values.sumOf { container ->
            container.rawItems.sumOf { if (it != null && it.toUnnoted(definitions).id == id) it.amount else 0 }
        }
    }

    /** A sell offer still holding one of [ids], or one of [ids] bought or returned and not collected yet. */
    private fun onGrandExchange(
        player: Player,
        ids: Set<Int>,
    ): Boolean {
        // Only a logged-in account has Grand Exchange offers (a scripted actor or a test player has no login name).
        val username = runCatching { gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface.username(player) }.getOrNull() ?: return false
        val service = runCatching { gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface.service(player) }.getOrNull() ?: return false
        val definitions = player.world.definitions
        return service.offersFor(username).any { offer ->
            val id = gg.rsmod.game.model.item.Item(offer.itemId).toUnnoted(definitions).id
            id in ids &&
                (
                    offer.collectableItems > 0 ||
                        (
                            offer.type == gg.rsmod.plugins.content.mechanics.exchange.OfferType.SELL &&
                                offer.status == gg.rsmod.plugins.content.mechanics.exchange.OfferStatus.ACTIVE && offer.remaining > 0
                        )
                )
        }
    }
}
