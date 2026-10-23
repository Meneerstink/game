package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.api.ext.setVarp

/**
 * The OSRS parchment menu (OSRS interface 187 as the spirit tree's "Spirit Tree Locations" and the obelisk's "Select Obelisk
 * destination" show it: a quill title and one centred "<key>: <text>" line per choice), built on the 667 scroll 864 by
 * SpiritTreeInterfaceTool (A28, A22). The caller gives the title, the lines and what a picked line does.
 */
object ScrollMenu {
    const val INTERFACE = 864
    const val TITLE = 9
    const val FIRST_ROW = 10
    const val ROWS = 16

    /** 864's own title, subtitle, rule, list and scrollbar - hidden under the OSRS menu. */
    private val OLD_PARTS = intArrayOf(3, 4, 5, 7, 8)

    /** 864's list builder (clientscript 2141) builds nothing while this varp is -1. */
    private const val LIST_VARP = 1469

    /** The OSRS menu's line keys (enum 1401: 1-9, then A-Z). */
    val KEYS: List<String> = (1..9).map { it.toString() } + ('A'..'Z').map { it.toString() }

    const val GREY = "<col=5f5f5f>"

    private val HANDLER = AttributeKey<(Player, Int) -> Unit>()

    /** "<key>: <line>" for each line, keys 1-9 then A-Z. */
    fun keyed(lines: List<String>): List<String> = lines.mapIndexed { i, line -> "${KEYS[i]}: $line" }

    /** Opens the menu; [onPick] gets the row (0-based) of the clicked line. */
    fun open(
        player: Player,
        title: String,
        lines: List<String>,
        onPick: (Player, Int) -> Unit,
    ) {
        require(lines.size <= ROWS) { "at most $ROWS lines" }
        player.attr[HANDLER] = onPick
        player.setVarp(LIST_VARP, -1)
        player.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        OLD_PARTS.forEach { player.setComponentHidden(INTERFACE, it, true) }
        player.setComponentText(INTERFACE, TITLE, title)
        for (r in 0 until ROWS) {
            val line = lines.getOrNull(r)
            if (line != null) {
                player.setComponentText(INTERFACE, FIRST_ROW + r, line)
                player.setInterfaceEvents(INTERFACE, FIRST_ROW + r, -1..-1, 2)
            }
            player.setComponentHidden(INTERFACE, FIRST_ROW + r, line == null)
        }
    }

    /** Line [row] was clicked (scroll_menu.plugin.kts). */
    fun pick(
        player: Player,
        row: Int,
    ) {
        val handler = player.attr[HANDLER] ?: return
        handler(player, row)
    }

    fun close(player: Player) {
        player.attr.remove(HANDLER)
        player.closeInterface(INTERFACE)
    }
}
