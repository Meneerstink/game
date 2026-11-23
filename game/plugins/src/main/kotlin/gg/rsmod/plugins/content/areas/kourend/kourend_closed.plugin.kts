package gg.rsmod.plugins.content.areas.kourend

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.CollisionUpdate

/*
 * PROJECT48 D2 (23-11): Great Kourend + Kebos Lowlands are imported (OsrsRegionImportTool 'kourend-kebos', 93 squares) but not
 * finished. Owner answer 5A (22-11): "the roads to the area stay CLOSED until the area is done". The only ways in today are the two
 * islands imported before - the CoX entrance (squares 4918/4919/5174/5175, fairy ring BLS 1295,3493) and Watson's house (square
 * 6455) - and since the import their edges touch the new land. So the outer ring of tiles of both islands is blocked like a solid
 * object (the same collision update as the Castle Wars barricades), on every plane. Remove this file when the area is opened.
 */
val closedIslands = listOf(
    intArrayOf(1216, 1343, 3456, 3583), // CoX entrance: squares x 19-20, y 54-55
    intArrayOf(1600, 1663, 3520, 3583), // Watson's house: square 6455
)

on_world_init {
    val update = CollisionUpdate.Builder()
    update.setType(CollisionUpdate.Type.ADD)
    var tiles = 0
    closedIslands.forEach { (x0, x1, y0, y1) ->
        for (z in 0..3) for (x in x0..x1) for (y in y0..y1) {
            if (x != x0 && x != x1 && y != y0 && y != y1) continue
            update.putTile(Tile(x, y, z), false, *Direction.NESW)
            tiles++
        }
    }
    world.collision.applyUpdate(update.build())
    logger.info("kourend_closed: {} edge tiles blocked (owner 5A: roads closed until the area is done)", tiles)
}
