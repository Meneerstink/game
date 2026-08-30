package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.SimplePolygonArea
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getWildernessLevel

/**
 * The safe bank hub centred on [gg.rsmod.game.GameContext.home]. The actual
 * world coordinate remains configured once in game.yml; every caller derives
 * the same compact boundary from that configured tile.
 *
 * The one-tile step immediately beyond this rectangle is ordinary Wilderness,
 * so there is no additional transition-safe strip around the hub.
 */
object BountyHunterHome {
    const val SAFE_RADIUS = 5
    const val BANK_OFFSET_X = 2
    const val BANK_OFFSET_Z = 0

    fun safeArea(home: Tile): SimplePolygonArea =
        SimplePolygonArea(
            arrayOf(
                home.transform(-SAFE_RADIUS, -SAFE_RADIUS),
                home.transform(SAFE_RADIUS, SAFE_RADIUS),
            ),
        )

    fun bankTile(home: Tile): Tile = home.transform(BANK_OFFSET_X, BANK_OFFSET_Z)

    /**
     * R14.3: the four real exits, one at each cardinal edge of the safe boundary. Real object
     * placement/collision for the enclave's visible walls is not yet built (R02.1 gap, see
     * OWNER_TASK_STATUS.md) - these are the four verified exit points where the pass-through
     * gates in `home_gates.plugin.kts` are placed, and where the wall perimeter will
     * eventually connect between once it's built.
     */
    fun gateTiles(home: Tile): List<Tile> =
        listOf(
            home.transform(0, SAFE_RADIUS), // north
            home.transform(0, -SAFE_RADIUS), // south
            home.transform(SAFE_RADIUS, 0), // east
            home.transform(-SAFE_RADIUS, 0), // west
        )

    fun isSafe(tile: Tile, home: Tile): Boolean =
        tile.height == home.height && safeArea(home).containsTile(tile)

    fun isSafe(player: Player): Boolean = isSafe(player.tile, player.world.gameContext.home)

    fun isDangerousWilderness(tile: Tile, home: Tile): Boolean =
        tile.getWildernessLevel() > 0 && !isSafe(tile, home)

    fun isDangerousWilderness(player: Player): Boolean =
        isDangerousWilderness(player.tile, player.world.gameContext.home)

    fun canPlayersFight(attacker: Player, target: Player): Boolean =
        (isDangerousWilderness(attacker) && isDangerousWilderness(target)) ||
            gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp.areMatched(attacker, target)
}
