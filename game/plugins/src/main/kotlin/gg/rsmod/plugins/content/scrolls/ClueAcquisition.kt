package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item

/** Shared reward/drop conversion, after the original clue roll and without changing non-clue loot. */
object ClueAcquisition {
    fun transform(player: Player, items: List<Item>): List<Item> {
        val pending = mutableMapOf<ClueScrollTier, Int>()
        return items.mapNotNull { item ->
            val tier = ClueOwnership.tier(item.id) ?: return@mapNotNull item
            val alreadyPlanned = pending[tier] ?: 0
            if (ClueContainers.count(player, tier).toLong() + alreadyPlanned >= ClueContainers.capacity(player, tier)) return@mapNotNull null
            ClueContainers.rewardId(player, tier)?.let {
                pending[tier] = alreadyPlanned + 1
                Item(it, 1)
            }
        }
    }
}
