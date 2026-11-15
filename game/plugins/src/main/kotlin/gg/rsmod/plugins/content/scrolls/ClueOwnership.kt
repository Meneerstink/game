package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.areas.watson.MimicRecovery

/** Reward caskets never block clues. Graves and Death's Office do (Wiki, all six clue pages). */
object ClueOwnership {
    /** Includes the Mimic IRS, whose persistent deathbank is separate from Death's Office. */
    fun stored(player: Player): List<Item> =
        listOf(player.inventory, player.bank, player.gravestone, player.deathRecovery).flatMap { it.filterNotNull() } + MimicRecovery.items(player)

    fun tier(item: Int): ClueScrollTier? = ClueScrollTier.values().firstOrNull { it.scrollId >= 0 && it.scrollId == item }

    fun owns(player: Player, tier: ClueScrollTier): Boolean = tier.scrollId >= 0 &&
        stored(player).any { it.id == tier.scrollId }

    fun mayReceive(player: Player, item: Int): Boolean = tier(item)?.let { !owns(player, it) } ?: true
}
