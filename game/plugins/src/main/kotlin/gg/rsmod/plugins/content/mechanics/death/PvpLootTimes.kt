package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.getWildernessLevel

/**
 * N503 - how long the loot of a PvP death lies on the ground (owner 2026-11-01, D5/D7/D8, OSRS Deadman):
 *  - D5 a player killer: the first minute only the killer sees it, then everyone - 4 more minutes in the Wilderness,
 *    60 more minutes outside it;
 *  - D7 a skulled player killed by a guard, D8 a killer who already logged out (no killer to own it): everyone sees it
 *    at once, for the same total time as the public part above (Wilderness 4 minutes, elsewhere 60 minutes).
 * Ordinary drops keep the world's 1 + 1 minute ([GroundItem.publicCycles] / [GroundItem.despawnCycles] stay null).
 * An untradeable item never turns public (World.cycle) - it stays the owner's until it disappears.
 */
object PvpLootTimes {
    /** 1 minute: the killer's own time with the loot. */
    const val KILLER_ONLY_CYCLES = 100

    /** 4 minutes: how long Wilderness loot stays for everyone. */
    const val WILDERNESS_PUBLIC_CYCLES = 400

    /** 60 minutes: how long loot outside the Wilderness stays for everyone. */
    const val OUTSIDE_PUBLIC_CYCLES = 6000

    fun publicStretch(tile: Tile): Int = if (tile.getWildernessLevel() > 0) WILDERNESS_PUBLIC_CYCLES else OUTSIDE_PUBLIC_CYCLES

    /** The ground item for one stack of PvP death loot at [tile], owned by [killer] (null = nobody: public at once). */
    fun groundItem(item: Item, tile: Tile, killer: Player?): GroundItem {
        val ground = GroundItem(item, tile, killer)
        timed(ground, killer != null)
        return ground
    }

    /** Same for a plain id/amount (loot keys). */
    fun groundItem(id: Int, amount: Int, tile: Tile, killer: Player?): GroundItem {
        val ground = GroundItem(id, amount, tile, killer)
        timed(ground, killer != null)
        return ground
    }

    private fun timed(ground: GroundItem, ownedByKiller: Boolean) {
        val stretch = publicStretch(ground.tile)
        if (ownedByKiller) {
            ground.publicCycles = KILLER_ONLY_CYCLES
            ground.despawnCycles = KILLER_ONLY_CYCLES + stretch
        } else {
            ground.publicCycles = 0
            ground.despawnCycles = stretch
        }
    }
}
