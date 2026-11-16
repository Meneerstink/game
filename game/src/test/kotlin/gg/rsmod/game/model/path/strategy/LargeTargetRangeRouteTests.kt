package gg.rsmod.game.model.path.strategy

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.region.ChunkSet
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CoX 16-11 (Vespula's 4x4 portal, 'I can't reach that!' 7 tiles north of it): a ranged route to a large target measures its reach from
 * the target's EDGE, as the attack check does (PawnPathAction.inReachWithoutMoving) - not from its south-west tile.
 */
class LargeTargetRangeRouteTests {
    private val world = mockk<World>(relaxed = true).also { every { it.collision } returns CollisionManager(ChunkSet(it), createChunksIfNeeded = true) }

    private fun route(from: Tile, target: Tile, size: Int, range: Int) =
        BFSPathFindingStrategy(world.collision, maxDistance = BFSPathFindingStrategy.PLAYER_MAX_DISTANCE, maxNodes = BFSPathFindingStrategy.PLAYER_MAX_NODES)
            .calculateRoute(
                PathRequest.Builder().setPoints(from, target).setSourceSize(1, 1).setTargetSize(size, size)
                    .setProjectilePath(true).setTouchRadius(range).clipPathNodes(node = true, link = true).build(),
            )

    private val portal = Tile(3200, 3200) // 4x4 body 3200..3203

    @Test
    fun `7 tiles north of a 4x4 edge is in range 8 without moving`() {
        val start = Tile(3201, 3210)
        val r = route(start, portal, 4, 8)
        assertTrue(r.success, "tail ${r.tail}")
        assertEquals(start, r.tail)
    }

    @Test
    fun `7 tiles east of a 4x4 edge is in range 8 without moving`() {
        val start = Tile(3210, 3201)
        val r = route(start, portal, 4, 8)
        assertTrue(r.success, "tail ${r.tail}")
        assertEquals(start, r.tail)
    }

    @Test
    fun `out of range north walks only until the edge gap is the range`() {
        val r = route(Tile(3201, 3215), portal, 4, 8)
        assertTrue(r.success, "tail ${r.tail}")
        assertEquals(3203 + 8, r.tail.z, "stops 8 tiles from the north edge, not 8 from the south-west tile")
    }

    @Test
    fun `south and west side and a 1x1 target keep their reach`() {
        assertEquals(Tile(3201, 3192), route(Tile(3201, 3192), portal, 4, 8).tail)
        val single = route(Tile(3200, 3212), portal, 1, 8)
        assertEquals(3208, single.tail.z)
    }
}
