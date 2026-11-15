package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.areas.watson.MimicRecovery

/** Reward caskets never block clues. Graves and Death's Office do (Wiki, all six clue pages). */
object ClueOwnership {
    /** Includes the Mimic IRS, whose persistent deathbank is separate from Death's Office. */
    fun stored(player: Player): List<Item> =
        listOf(player.inventory, player.bank, player.gravestone, player.deathRecovery).flatMap { it.filterNotNull() } + MimicRecovery.items(player)

    /** Sherlock's elite challenge replaces the physical clue; Watson explicitly accepts this variant. */
    fun items(tier: ClueScrollTier): Set<Int> = buildSet {
        if (tier.scrollId >= 0) add(tier.scrollId)
        if (tier == ClueScrollTier.ELITE) ClueItems.local(12128)?.let { add(it) }
        TreasureTrailNumberChallenge.sources[tier]?.let(ClueItems::local)?.let { add(it) }
    }

    fun tier(item: Int): ClueScrollTier? = if (item < 0) null else ClueScrollTier.values().firstOrNull { item in items(it) }

    fun carried(player: Player, tier: ClueScrollTier): Item? = player.inventory.firstOrNull { it != null && ClueOwnership.tier(it.id) == tier }

    fun owns(player: Player, tier: ClueScrollTier): Boolean = tier.scrollId >= 0 &&
        stored(player).any { ClueOwnership.tier(it.id) == tier }

    fun mayReceive(player: Player, item: Int): Boolean = tier(item)?.let { !owns(player, it) } ?: true
}
