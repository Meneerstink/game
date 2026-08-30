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
