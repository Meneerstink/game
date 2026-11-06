package gg.rsmod.game.action

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.collision.CollisionUpdate
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.path.strategy.SimplePathFindingStrategy
import gg.rsmod.game.model.region.ChunkSet
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N448 (owner 2026-10-30, Pest Control: "pathing issues met t attacken van npcs creert een lus"). Test client tick by tick: a
 * shifter and the player attacking it stepped towards each other's old tile every tick (diagonal again, or on one tile), and npcs
 * beside the Void Knight got "no route" every tick and then dropped their target (N417).
 * 2026-11-06 (N245 nachtrun): the npc no longer HOLDS while the player walks in (holdsForApproachingPlayer, not OSRS) - every approach
 * is checked right before the pawn moves instead (PawnPathAction.beforeStep, InteractionApproachLoopTests in the plugins).
 */
class NpcApproachLoopTests {
    private val world = mockk<World>(relaxed = true).also { every { it.collision } returns CollisionManager(ChunkSet(it), createChunksIfNeeded = true) }

    private fun npc(tile: Tile): Npc =
        mockk<Npc>(relaxed = true).also {
            every { it.tile } returns tile
            every { it.getSize() } returns 1
            every { it.world } returns world
            every { it.walkMask } returns 0
        }

    private fun route(from: Tile, to: Tile) =
        SimplePathFindingStrategy(world.collision, npc(from)).calculateRoute(
            PathRequest.Builder()
                .setPoints(from, to)
                .setSourceSize(1, 1)
                .setTargetSize(1, 1)
                .setTouchRadius(1)
                .clipPathNodes(node = true, link = true)
                .clipDiagonalTiles()
                .clipOverlapTiles()
                .build(),
        )

    private fun block(tile: Tile, vararg directions: Direction) {
        val update = CollisionUpdate.Builder()
        update.setType(CollisionUpdate.Type.ADD)
        update.putTile(tile, false, *directions)
        world.collision.applyUpdate(update.build())
    }

    @Test
    fun `an npc beside a target on a blocked tile has arrived, across a wall it has not`() {
        val knight = Tile(3232, 3232)
        block(knight, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
        val beside = route(Tile(3232, 3231), knight)
        assertTrue(beside.success && beside.path.isEmpty(), "south of the knight: arrived without a step")

        val wallSide = Tile(3240, 3240)
        val wallTarget = Tile(3240, 3241)
        block(wallSide, Direction.NORTH)
        block(wallTarget, Direction.SOUTH)
        assertFalse(route(wallSide, wallTarget).let { it.success && it.path.isEmpty() }, "a wall between is no melee reach")
    }
}
