package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/**
 * The spirit tree network (Q-017), one list for every route to it: the trees themselves (spirit_tree.plugin.kts) and the Trinket of
 * fairies (trinket_of_fairies.plugin.kts). Names and tiles decoded from this 667 cache: `ConfigDefProbeTool enum 2535` (menu text) and
 * `enum 2536` (packed tile), cross-checked against the static "Spirit tree" placements. Only the five permanent stations (enum indices
 * 0-4) exist: 5-7 need a player-grown tree and 8 an unlock counter, neither of which this server's Farming has.
 */
object SpiritTrees {
    data class Station(
        val label: String,
        val tile: Tile,
    )

    /** A28: the OSRS menu's names ("Gnome Stronghold", "Grand Exchange"); Mobilising Armies has no OSRS tree and keeps its 667 name. */
    val STATIONS =
        listOf(
            Station("Tree Gnome Village", Tile(2542, 3169, 0)),
            Station("Gnome Stronghold", Tile(2462, 3444, 0)),
            Station("Battlefield of Khazard", Tile(2557, 3259, 0)),
            Station("Grand Exchange", Tile(3185, 3511, 0)),
            Station("Mobilising Armies", Tile(2416, 2851, 0)),
        )

    /** The OSRS menu's line keys (enum 1401: 1-9, then A-Z). */
    private val KEYS = (1..9).map { it.toString() } + ('A'..'Z').map { it.toString() }

    /**
     * A28: the OSRS rows SpiritTreeInterfaceTool appended to 864 (title 864:9, rows 864:10-25); 864's own title, subtitle, rule,
     * list and scrollbar (3, 4, 5, 7, 8) are hidden.
     */
    const val FIRST_ROW = 10
    const val ROWS = 16
    private val OLD_PARTS = intArrayOf(3, 4, 5, 7, 8)
    private const val GREY = "<col=5f5f5f>"

    /** The menu lines: the stations, then Cancel; a station the player stands at is greyed (OSRS greys what cannot be picked). */
    fun lines(origin: Station?): List<String> =
        STATIONS.mapIndexed { i, s -> (if (s == origin) GREY else "") + "${KEYS[i]}: ${s.label}" } + "${KEYS[STATIONS.size]}: Cancel"

    private const val NEAREST_STATION_RADIUS = 30

    /**
     * The real destination menu, interface 864 ("Spirit Tree" / "Where would you like to go?"; owner 2026-10-02: interfaces, not chat
     * menus). Its list is built by the client itself (clientscript 2141 on 864:7, one row per entry of enum 2535 through script 942,
     * each row a text child with op "Teleport", slot = the enum index, script 2142); 2141 builds nothing while the varp is -1. Script 942
     * leaves out the entry whose enum-2536
     * tile equals varp [CURRENT_TREE_VARP] (the tree the player stands at) and shows 5-8 only for a grown tree (varbits 720/722/724,
     * 3959 >= 20) - never set here, matching [STATIONS].
     */
    const val INTERFACE = 864
    const val LIST = 7
    const val CURRENT_TREE_VARP = 1469
    const val BUILD_LIST_SCRIPT = 2141

    private val TYPE = gg.rsmod.game.model.attr.AttributeKey<TeleportType>()
    private val ORIGIN = gg.rsmod.game.model.attr.AttributeKey<Int>()

    /** Packed like enum 2536 (and the client's coords): plane << 28 | x << 14 | z. */
    private fun packed(tile: Tile) = (tile.height shl 28) or (tile.x shl 14) or tile.z

    /**
     * Opens the destination menu. At a tree the tree's own station is left out (client script 942 via varp 1469); from the trinket
     * (no tree) every station is offered. Spirit trees are teleports: Tele Block, the Deadman countdown and the Wilderness limit of
     * [type] apply when a row is picked ([select]).
     */
    fun open(
        player: Player,
        type: TeleportType = TeleportType.SPIRIT_TREE,
        atTree: Boolean = true,
    ) {
        val origin = if (atTree) STATIONS.minByOrNull { it.tile.getDistance(player.tile) }?.takeIf { it.tile.getDistance(player.tile) <= NEAREST_STATION_RADIUS } else null
        player.attr[TYPE] = type
        player.attr[ORIGIN] = origin?.let { STATIONS.indexOf(it) } ?: -1
        // -1 makes script 2141 build no 667 list at all (its first test): the OSRS rows below replace it.
        player.setVarp(CURRENT_TREE_VARP, -1)
        player.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        OLD_PARTS.forEach { player.setComponentHidden(INTERFACE, it, true) }
        val lines = lines(origin)
        for (r in 0 until ROWS) {
            val line = lines.getOrNull(r)
            if (line != null) player.setComponentText(INTERFACE, FIRST_ROW + r, line)
            player.setComponentHidden(INTERFACE, FIRST_ROW + r, line == null)
            if (line != null) player.setInterfaceEvents(INTERFACE, FIRST_ROW + r, -1..-1, 2)
        }
    }

    /** Line [row] of the OSRS menu was clicked: a station, or Cancel. */
    fun selectRow(
        player: Player,
        row: Int,
    ) {
        if (row == STATIONS.size) {
            player.closeInterface(INTERFACE)
            return
        }
        if (row == player.attr[ORIGIN]) return
        val dest = STATIONS.getOrNull(row) ?: return
        val type = player.attr[TYPE] ?: TeleportType.SPIRIT_TREE
        player.closeInterface(INTERFACE)
        player.canTeleport(type) {
            player.teleport(dest.tile, type)
        }
    }

    /** Row [slot] (the enum-2535 index) of the open menu was picked. */
    fun select(
        player: Player,
        slot: Int,
    ) {
        val dest = STATIONS.getOrNull(slot) ?: return
        if (player.getVarp(CURRENT_TREE_VARP) == packed(dest.tile)) return
        val type = player.attr[TYPE] ?: TeleportType.SPIRIT_TREE
        player.closeInterface(INTERFACE)
        player.canTeleport(type) {
            player.teleport(dest.tile, type)
        }
    }

    /** Kept for the callers that run inside a queue (tree options, Talk-to, the trinket). */
    @Suppress("UNUSED_PARAMETER")
    suspend fun travel(
        task: QueueTask,
        player: Player,
        type: TeleportType = TeleportType.SPIRIT_TREE,
        atTree: Boolean = true,
    ) = open(player, type, atTree)
}
