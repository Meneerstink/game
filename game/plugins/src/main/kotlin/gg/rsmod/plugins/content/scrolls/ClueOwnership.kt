package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Player

/** Reward caskets never block clues. Graves and Death's Office do (Wiki, all six clue pages). */
object ClueOwnership {
    fun tier(item: Int): ClueScrollTier? = ClueScrollTier.values().firstOrNull { it.scrollId >= 0 && it.scrollId == item }

    fun owns(player: Player, tier: ClueScrollTier): Boolean = tier.scrollId >= 0 &&
        listOf(player.inventory, player.bank, player.gravestone, player.deathRecovery).any { it.contains(tier.scrollId) }

    fun mayReceive(player: Player, item: Int): Boolean = tier(item)?.let { !owns(player, it) } ?: true
}
