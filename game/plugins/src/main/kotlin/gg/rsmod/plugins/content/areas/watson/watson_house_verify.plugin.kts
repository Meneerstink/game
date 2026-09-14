package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject

/**
 * Owner answer Q10: boot-time check of the imported Watson's house square (map square 6455, WatsonHouseImportTool tx-20260914-135504),
 * against the REAL loaded region: the Strange casket (OSRS loc 34733 -> local 62743) stands where the imported OSRS map places it, 1645,3569
 * plane 1 (cache read-back of l25_55; the OSRS Wiki map pin 1646,3570 is one tile off), and at least one cardinal neighbour is standable.
 * Unlike home_verify this only reports (the square is new content); it proves the server loads the square, not client rendering.
 */
val STRANGE_CASKET = 62743
val strangeCasketTile = Tile(1645, 3569, 1)

on_world_init {
    val chunk = world.chunks.get(strangeCasketTile, createIfNeeded = true)!!
    val casketPresent =
        chunk.getEntities<GameObject>(strangeCasketTile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT).any { it.id == STRANGE_CASKET }
    val standableNeighbours =
        Direction.NESW.map { strangeCasketTile.step(it) }.filter { tile -> !Direction.NESW.all { world.collision.isBlocked(tile, it, projectile = false) } }
    if (casketPresent && standableNeighbours.isNotEmpty()) {
        println("watson_verify: OK - Strange casket $STRANGE_CASKET at $strangeCasketTile, standable neighbours $standableNeighbours")
    } else {
        println("watson_verify: FAILED - casketPresent=$casketPresent standableNeighbours=$standableNeighbours at $strangeCasketTile")
    }
}
