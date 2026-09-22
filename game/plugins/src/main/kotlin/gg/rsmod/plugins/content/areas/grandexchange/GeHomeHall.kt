package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.Tile

/** Shared geometry and ids of the Grand Exchange home hall (`ge_home_hall.plugin.kts`). */
object GeHomeHall {
    /** Npc 11679 (cache "Shop assistant"), renamed "Quartermaster" in both caches (tx-20260922-141234). */
    const val QUARTERMASTER = 11679

    /** South-west tile of the 11 x 11 hall. */
    const val X = 3152
    const val Z = 3470
    const val SIZE = 11

    /** Whether [tile] is inside the hall; its stalls are a deliberate layout the GE boot audit must not rearrange. */
    fun contains(tile: Tile): Boolean = tile.height == 0 && tile.x in X until X + SIZE && tile.z in Z until Z + SIZE
}
