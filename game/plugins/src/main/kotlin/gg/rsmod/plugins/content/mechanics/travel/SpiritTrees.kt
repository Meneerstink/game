package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.options
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
     * The destination menu and the teleport. At a tree the tree's own station is left out; from the trinket (no tree) every station is
     * offered. Spirit trees are teleports: Tele Block, the Deadman countdown and the Wilderness limit of [type] apply.
     */
    suspend fun travel(
        task: QueueTask,
        player: Player,
        type: TeleportType = TeleportType.SPIRIT_TREE,
        atTree: Boolean = true,
    ) {
        val origin = if (atTree) STATIONS.minByOrNull { it.tile.getDistance(player.tile) } else null
        val destinations =
            if (origin != null && origin.tile.getDistance(player.tile) <= NEAREST_STATION_RADIUS) {
                STATIONS.filter { it != origin }
            } else {
                STATIONS
            }
        val choice = task.options(*destinations.map { it.label }.toTypedArray(), title = "Where would you like to go?")
        if (choice < 1 || choice > destinations.size) {
            return
        }
        val dest = destinations[choice - 1]
        player.canTeleport(type) {
            player.teleport(dest.tile, type)
        }
    }
}
