package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Varbits
import gg.rsmod.plugins.api.ext.getVarbit

/** Shared by the wearable item and the POH directory: never land outside the GE south gate (N507). */
object JewelleryDestinations {
    val RING_OF_WEALTH = linkedMapOf(
        "Miscellania" to Tile(2528, 3859, 0),
        "Grand Exchange" to Tile(3164, 3495, 0),
    )

    /**
     * N537 stap 3a (census 25-11): the OSRS-import rings (Ring of wealth (5), the Deadman (i) rings) list two more worn teleports
     * that did nothing. OSRS Wiki: Falador = "the Falador Park entrance central to Falador"; Dondakan's rock only after Between a
     * Rock... ADAPTED (the wiki gives no tile): the walkable tiles in the park's north entrance and next to Dondakan (spawn 2824,10168).
     * Only the worn options use these: the 667 inventory Rub dialog keeps its two places.
     */
    val RING_OF_WEALTH_WORN_EXTRA = linkedMapOf(
        "Falador" to Tile(2994, 3375, 0),
        "Dondakan" to Tile(2826, 10168, 0),
    )

    /** Between a Rock... complete (quest list slot 19: varbit value 110). */
    const val BETWEEN_A_ROCK_COMPLETE = 110

    /** Why [player] may not use the ring's [destination] teleport, or null. */
    fun wealthRefusal(
        player: Player,
        destination: String,
    ): String? =
        if (destination == "Dondakan" && player.getVarbit(Varbits.BETWEEN_A_ROCK_PROGRESS) < BETWEEN_A_ROCK_COMPLETE) {
            "You need to have completed Between a Rock... to teleport to Dondakan's rock."
        } else {
            null
        }
}
