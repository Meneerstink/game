package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.content.activity.toa.bosses.AkkhaCombat.Quadrant

/** Cache2686 square14676: plane1 walkable arena, source platform models45616..45639.
 * The floor's cross is at3681,5408 (a boundary between tiles); glyph face geometry gives the four marker tiles below.
 * This is separate from the unpublished NPC spawn location. See N601 B2 NACHTRUN 08-11, source model/terrain evidence.
 */
object AkkhaArena {
    val SOURCE_CENTRE = Tile(3681, 5408, 1)
    // South to north, z5398..5417: half of each even-width row of walkable plane1 tiles.
    private val HALF_WIDTHS = intArrayOf(2, 5, 6, 7, 8, 9, 9, 9, 10, 10, 10, 10, 9, 9, 9, 8, 7, 6, 5, 2)

    fun markerLoc(q: Quadrant): Int = when (q) {
        Quadrant.FIRE -> 45868
        Quadrant.LIGHTNING -> 45869
        Quadrant.ICE -> 45870
        Quadrant.SHADOW -> 45871
    }

    fun markerTile(centre: Tile, q: Quadrant): Tile = when (q) {
        // Source face-colour clusters: burn4651 x3679.016..3679.977/z5409..5410.016;
        // skull12/22 x3682.023..3683/z5409.023..5409.992; ice78/1121 at3679,5406; lightning8623/8640 at3682,5406.
        Quadrant.FIRE -> centre.transform(-2, 1)
        Quadrant.SHADOW -> centre.transform(1, 1)
        Quadrant.LIGHTNING -> centre.transform(1, -2)
        Quadrant.ICE -> centre.transform(-2, -2)
    }

    fun impactSpot(q: Quadrant): Int = when (q) {
        Quadrant.FIRE -> 2258
        Quadrant.SHADOW -> 2257
        Quadrant.LIGHTNING -> 2256
        Quadrant.ICE -> 2259
    }

    fun tiles(centre: Tile): List<Tile> = HALF_WIDTHS.flatMapIndexed { row, half ->
        (-half until half).map { dx -> centre.transform(dx, row - 10) }
    }

    fun blastTiles(centre: Tile, safe: Quadrant): List<Tile> = tiles(centre).filter { AkkhaCombat.quadrant(it, centre) != safe }
}
