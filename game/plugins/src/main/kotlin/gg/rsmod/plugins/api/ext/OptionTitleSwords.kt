package gg.rsmod.plugins.api.ext

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.quests.tab.N108Ui

/**
 * N499 (owner 2026-10-31 20:33 + 20:5x: "Your pet is carrying items. Pick it up?" lay over the two swords of the chatbox options;
 * "alle zwaard-titels nalopen"): the title of every chatbox options dialogue (interfaces 228-238, [options]) fits its swords.
 *
 * The 667 cache gives each options interface TWO sword pairs around the title (component 1, font q8_full, 480 wide, centred): a narrow
 * pair (a 337 wide layer, 350 in 236, swords 57 wide at both ends - shown) and a wide pair (a 476 wide layer - hidden). The server
 * never switched them, so a title wider than the narrow pair's inner space ran over its swords (the pet title: 265 px in 223). Now the
 * title is measured in the client's own font: the narrow swords while it fits, else the wide ones, else (wider than the wide pair's
 * space) none - nothing ever overlaps a sword, whatever the text (player names, amounts).
 */
object OptionTitleSwords {
    const val FONT = "q8_full"

    /** Free space kept on each side between the text and a sword tip. */
    const val MARGIN = 4

    /** The sword tips are 57 px wide each. */
    private const val SWORD = 57

    /** Options interface -> {narrow layer, its width, wide layer, its width} (cache 228-238, dumped 2026-10-31). */
    val LAYERS: Map<Int, IntArray> =
        mapOf(
            228 to intArrayOf(6, 337, 9, 476),
            230 to intArrayOf(7, 337, 10, 476),
            232 to intArrayOf(8, 337, 9, 476),
            234 to intArrayOf(9, 337, 10, 476),
            236 to intArrayOf(5, 350, 6, 476),
            238 to intArrayOf(8, 337, 9, 476),
        )

    enum class Swords { NARROW, WIDE, NONE }

    /** The most a title may be wide in a sword pair of [layerWidth]. */
    fun room(layerWidth: Int): Int = layerWidth - 2 * SWORD - 2 * MARGIN

    fun choose(
        interfaceId: Int,
        titleWidth: Int,
    ): Swords {
        val l = LAYERS[interfaceId] ?: return Swords.NARROW
        return when {
            titleWidth <= room(l[1]) -> Swords.NARROW
            titleWidth <= room(l[3]) -> Swords.WIDE
            else -> Swords.NONE
        }
    }

    /** After the title is written: the sword pair that fits it (both sent, the client keeps an open interface's states). */
    fun apply(
        player: Player,
        interfaceId: Int,
        title: String,
    ) {
        val l = LAYERS[interfaceId] ?: return
        val swords = choose(interfaceId, N108Ui.textWidth(player.world, FONT, title))
        player.setComponentHidden(interfaceId, l[0], swords != Swords.NARROW)
        player.setComponentHidden(interfaceId, l[2], swords != Swords.WIDE)
    }
}
