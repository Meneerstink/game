package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item

/** Shared reward/drop conversion, after the original clue roll and without changing non-clue loot. */
object ClueAcquisition {
    fun transform(player: Player, items: List<Item>): List<Item> = items.mapNotNull { item ->
        val tier = ClueOwnership.tier(item.id) ?: return@mapNotNull item
        ClueContainers.rewardId(player, tier)?.let { Item(it, 1) }
    }
}
