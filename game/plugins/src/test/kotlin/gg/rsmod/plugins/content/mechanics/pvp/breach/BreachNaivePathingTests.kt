package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionFlag
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.path.strategy.NaivePathFindingStrategy
import gg.rsmod.game.model.region.ChunkSet
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Breach conformance 2026-09-27 (A2): OSRS npc movement - OSRS Wiki "Pathfinding": follow mode, "identical to the pathing of
 * NPCs", "naively paths diagonally to the end tile and then straight if there's no diagonals left". Open ground (no collision).
 */
class BreachNaivePathingTests {
    private val chunks = ChunkSet(mockk<World>(relaxed = true))
    private val collision = CollisionManager(chunks)
    private val walker = mockk<Pawn>(relaxed = true).also { every { it.walkMask } returns 0 }

    private fun follow(
        start: Tile,
        size: Int,
        target: Tile,
    ) = NaivePathFindingStrategy(collision, walker).calculateRoute(
        PathRequest.Builder().setPoints(start, target).setSourceSize(size, size).setTargetSize(1, 1)
            .setProjectilePath(false).setTouchRadius(1).clipPathNodes(node = true, link = true).clipDiagonalTiles().clipOverlapTiles().build(),
    )

    /** Side by side (not corner to corner, not overlapping) - the melee position. */
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
    fun `an npc goes diagonally first and straight once one axis is lined up`() {
        val r = follow(Tile(3200, 3500, 0), 1, Tile(3206, 3503, 0))
        val steps = listOf(Tile(3200, 3500, 0)) + r.path.toList()
        // 3 diagonal steps (the z distance to the south side of the target is 2 plus the corner), then straight east
        val kinds = steps.zipWithNext { a, b -> if (a.x != b.x && a.z != b.z) "d" else "s" }
        assertTrue(kinds.first() == "d", "first step is not diagonal: $steps")
        assertTrue(kinds.dropWhile { it == "d" }.all { it == "s" }, "a diagonal step after a straight one: $steps")
        assertTrue(r.success && borders(r.tail, 1, Tile(3206, 3503, 0)), "ends at ${r.tail}")
    }

    @Test
    fun `a 2x2 to 5x5 monster ends side by side with its target, never on it or at a corner`() {
        val target = Tile(3212, 3507, 0)
        for (size in 1..5) {
            val r = follow(Tile(3200, 3500, 0), size, target)
            assertTrue(r.success, "size $size did not arrive (tail ${r.tail})")
            assertTrue(borders(r.tail, size, target), "size $size ends at ${r.tail}")
        }
    }

    @Test
    fun `an npc diagonal to its target takes one straight step`() {
        val r = follow(Tile(3199, 3501, 0), 1, Tile(3200, 3500, 0))
        assertEquals(1, r.path.size, "path ${r.path}")
        assertTrue(borders(r.tail, 1, Tile(3200, 3500, 0)))
    }

    @Test
    fun `a plain walk ends on its tile`() {
        val r =
            NaivePathFindingStrategy(collision, walker).calculateRoute(
                PathRequest.Builder().setPoints(Tile(3200, 3500, 0), Tile(3204, 3509, 0)).setSourceSize(2, 2).setTargetSize(0, 0).build(),
            )
        assertTrue(r.success)
        assertEquals(Tile(3204, 3509, 0), r.tail)
    }

    /** A solid object (tree, rock, table): CollisionUpdate flags only its own tile, on every side. */
    private fun solid(tile: Tile) {
        val matrix = chunks.get(tile, createIfNeeded = true)!!.getMatrix(tile.height)
        listOf(CollisionFlag.PAWN_NORTH, CollisionFlag.PAWN_EAST, CollisionFlag.PAWN_SOUTH, CollisionFlag.PAWN_WEST)
            .forEach { matrix.addFlag(tile.x % 8, tile.z % 8, it) }
    }

    @Test
    fun `no npc walks into solid scenery`() {
        // Owner 2026-09-27 "breachmonsters kunnen allemaal noclippen": only the tile being left was asked, and it never knows.
        val target = Tile(3212, 3500, 0)
        for (x in 3202..3206) for (z in 3496..3504) solid(Tile(x, z, 0))
        for (size in 1..5) {
            val start = Tile(3196 - size, 3500, 0)
            val r = follow(start, size, target)
            val corners = listOf(start) + r.path.toList()
            for (c in corners) {
                for (dx in 0 until size) for (dz in 0 until size) {
                    assertTrue(c.x + dx !in 3202..3206 || c.z + dz !in 3496..3504, "size $size entered the rock at $c: ${r.path}")
                }
            }
        }
    }
}