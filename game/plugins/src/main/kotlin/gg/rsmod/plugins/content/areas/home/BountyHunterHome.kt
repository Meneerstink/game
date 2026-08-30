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

    /**
     * R02.1/HOME_DESIGN_2.png ("De Herbouwde Ruïne"): how many tiles are cut off each of the 4
     * square corners to form the real octagonal ruin shape the confirmed design shows - not a
     * plain square. Kept as a real, buildable integer-tile shape (no new client assets needed):
     * the 4 straight edges shrink from the full [SAFE_RADIUS] span to a shorter flat wall, and
     * each corner becomes a 3-tile diagonal staircase instead of a right angle.
     */
    const val CORNER_CUT = 2
    const val BANK_OFFSET_X = 2
    const val BANK_OFFSET_Z = 0

    /**
     * The 8 vertices of the octagonal safe-zone perimeter - the exact same shape
     * `octagonRing` traces tile-by-tile for the real wall/collision boundary, so the safety
     * classification ([isSafe]) and the physical wall can never disagree about which tiles are
     * inside.
     */
    fun octagonVertices(home: Tile): Array<Tile> {
        val r = SAFE_RADIUS
        val c = CORNER_CUT
        return arrayOf(
            home.transform(-(r - c), r),
            home.transform(r - c, r),
            home.transform(r, r - c),
            home.transform(r, -(r - c)),
            home.transform(r - c, -r),
            home.transform(-(r - c), -r),
            home.transform(-r, -(r - c)),
            home.transform(-r, r - c),
        )
    }

    /**
     * Every real tile on the octagonal perimeter - the 4 shortened straight edges plus the 4
     * diagonal corner-cut staircases (each [CORNER_CUT] + 1 tiles). This is the actual wall/
     * collision boundary; [octagonVertices] is just its 8 corner points for the polygon test.
     */
    fun octagonRing(home: Tile): List<Tile> {
        val r = SAFE_RADIUS
        val c = CORNER_CUT
        val ring = LinkedHashSet<Tile>()

        for (x in -(r - c)..(r - c)) {
            ring.add(home.transform(x, r))
            ring.add(home.transform(x, -r))
        }
        for (z in -(r - c)..(r - c)) {
            ring.add(home.transform(r, z))
            ring.add(home.transform(-r, z))
        }
        for (i in 0..c) {
            ring.add(home.transform(r - c + i, r - i)) // NE cut
            ring.add(home.transform(r - i, -(r - c) - i)) // SE cut
            ring.add(home.transform(-(r - c) - i, -r + i)) // SW cut
            ring.add(home.transform(-r + i, (r - c) + i)) // NW cut
        }
        return ring.toList()
    }

    fun safeArea(home: Tile): SimplePolygonArea = SimplePolygonArea(octagonVertices(home))

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
