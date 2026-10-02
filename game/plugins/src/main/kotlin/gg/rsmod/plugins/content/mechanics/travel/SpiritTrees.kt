package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.runClientScript
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

    val STATIONS =
        listOf(
            Station("Tree Gnome Village", Tile(2542, 3169, 0)),
            Station("Tree Gnome Stronghold", Tile(2462, 3444, 0)),
            Station("Battlefield of Khazard", Tile(2557, 3259, 0)),
            Station("North-east of the Grand Exchange in Varrock", Tile(3185, 3511, 0)),
            Station("Mobilising Armies", Tile(2416, 2851, 0)),
        )

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
        // -1 makes script 2141 build no list at all (its first test); 0 is no station's tile, so every station is listed.
        player.setVarp(CURRENT_TREE_VARP, origin?.let { packed(it.tile) } ?: 0)
        player.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        // The interface has no load hook for its list: the cache's builder script 2141 (no arguments) fills 864:7 and its scrollbar.
        player.runClientScript(BUILD_LIST_SCRIPT)
        player.setInterfaceEvents(INTERFACE, LIST, 0..8, 2)
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
