package gg.rsmod.game.model.path.strategy

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.region.ChunkSet
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N462 (07-11, ToA reward vault): the vault chest stands 24 tiles from the landing tile; the 20-tile search radius stopped every route
 * at its edge ('I can't reach that!'). Players search like OSRS (128x128 grid); npcs keep the old radius.
 */
class PlayerRouteRadiusTests {
    private val world = mockk<World>(relaxed = true).also { every { it.collision } returns CollisionManager(ChunkSet(it), createChunksIfNeeded = true) }

    private fun request(from: Tile, to: Tile) =
        PathRequest.Builder().setPoints(from, to).setSourceSize(1, 1).setTargetSize(2, 2)
            .clipPathNodes(node = true, link = true).clipDiagonalTiles().clipOverlapTiles().build()

    @Test
    fun `a player reaches a 2x2 chest 24 tiles away, an npc search still stops at 20`() {
        val start = Tile(3200, 3249)
        val chest = Tile(3207, 3225)
        val player = BFSPathFindingStrategy(world.collision, maxDistance = BFSPathFindingStrategy.PLAYER_MAX_DISTANCE, maxNodes = BFSPathFindingStrategy.PLAYER_MAX_NODES)
            .calculateRoute(request(start, chest))
        assertTrue(player.success, "player route tail ${player.tail}")
        val t = player.tail
        val besideX = (t.x == chest.x - 1 || t.x == chest.x + 2) && t.z in chest.z..chest.z + 1
        val besideZ = (t.z == chest.z - 1 || t.z == chest.z + 2) && t.x in chest.x..chest.x + 1
        assertTrue(besideX || besideZ, "ends beside the chest, not diagonal: $t")
        val npc = BFSPathFindingStrategy(world.collision).calculateRoute(request(start, chest))
        assertFalse(npc.success, "npc radius unchanged")
    }
}
