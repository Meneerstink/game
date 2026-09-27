package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.path.strategy.BFSPathFindingStrategy
import gg.rsmod.game.model.region.ChunkSet
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Breach audit 2026-09-27, found by the in-engine test run (BreachSim): the npc route search for large and blocked monsters.
 * Open ground (no collision) around (3200, 3500); only the walker's size and the tiles other bodies cover matter here.
 */
class BreachPathingTests {
    private val collision = CollisionManager(ChunkSet(mockk<World>(relaxed = true)))

    private fun route(
        start: Tile,
        size: Int,
        target: Tile,
        bodies: Set<Tile>? = null,
    ) = BFSPathFindingStrategy(collision, bodies, null).calculateRoute(
        PathRequest.Builder().setPoints(start, target).setSourceSize(size, size).setTargetSize(1, 1)
            .setProjectilePath(false).setTouchRadius(1).clipPathNodes(node = true, link = true).clipDiagonalTiles().clipOverlapTiles().build(),
    )

    private fun borders(
        corner: Tile,
        size: Int,
        target: Tile,
    ): Boolean {
        val gx = maxOf(0, target.x - (corner.x + size - 1), corner.x - target.x)
        val gz = maxOf(0, target.z - (corner.z + size - 1), corner.z - target.z)
        return maxOf(gx, gz) == 1 && !(gx == 1 && gz == 1)
    }

    @Test
    fun `a 2x2 to 5x5 monster routes to a tile where its body touches the player, never onto him`() {
        val target = Tile(3210, 3502, 0)
        for (size in 2..5) {
            val r = route(Tile(3200, 3500, 0), size, target)
            assertTrue(r.success, "size $size found no route")
            assertTrue(borders(r.tail, size, target), "size $size ends at ${r.tail}, not next to the player")
        }
    }

    @Test
    fun `a route goes around a standing npc instead of through it`() {
        val target = Tile(3206, 3500, 0)
        val man = setOf(Tile(3203, 3500, 0), Tile(3203, 3501, 0))
        val r = route(Tile(3200, 3500, 0), 2, target, man)
        assertTrue(r.success)
        r.path.forEach { step ->
            for (x in 0..1) for (z in 0..1) assertTrue(step.transform(x, z) !in man, "step $step walks through the npc")
        }
    }
}
