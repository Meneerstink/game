package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.game.model.Tile

/** Shared by the wearable item and the POH directory: never land outside the GE south gate (N507). */
object JewelleryDestinations {
    val RING_OF_WEALTH = linkedMapOf(
        "Miscellania" to Tile(2528, 3859, 0),
        "Grand Exchange" to Tile(3164, 3495, 0),
    )
}
