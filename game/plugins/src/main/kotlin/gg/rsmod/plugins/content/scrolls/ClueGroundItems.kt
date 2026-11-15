package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.GroundItem

/** Wiki clue scroll / Mimic / Scroll box: player-dropped clues last an hour, boxes three minutes. */
object ClueGroundItems {
    const val HOUR = 6000 // game ticks, 0.6 seconds each
    const val THREE_MINUTES = 300

    fun configure(item: GroundItem, playerDrop: Boolean = false): GroundItem {
        // Explicit death-loot timing belongs to D1-D20; never replace it here.
        if (item.despawnCycles != null) return item
        if (ClueContainers.boxTier(item.item) != null) item.despawnCycles = THREE_MINUTES
        else if (playerDrop && (ClueOwnership.tier(item.item) != null || item.item == ClueItems.local(23184)))
            item.despawnCycles = HOUR
        return item
    }
}
