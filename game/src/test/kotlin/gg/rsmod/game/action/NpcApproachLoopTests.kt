package gg.rsmod.game.action

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.FACING_PAWN_ATTR
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.collision.CollisionUpdate
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.path.strategy.SimplePathFindingStrategy
import gg.rsmod.game.model.region.ChunkSet
import io.mockk.every
import io.mockk.mockk
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N448 (owner 2026-10-30, Pest Control: "pathing issues met t attacken van npcs creert een lus"). Test client tick by tick: a
 * shifter and the player attacking it stepped towards each other's old tile every tick (diagonal again, or on one tile), and npcs
 * beside the Void Knight got "no route" every tick and then dropped their target (N417).
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

    private fun player(tile: Tile, facing: Pawn?, moving: Boolean): Player {
        val attr = AttributeMap().also { m -> if (facing != null) m[FACING_PAWN_ATTR] = WeakReference(facing) }
        return mockk<Player>(relaxed = true).also {
            every { it.tile } returns tile
            every { it.getSize() } returns 1
            every { it.world } returns world
            every { it.attr } returns attr
            every { it.hasMoveDestination() } returns moving
        }
    }

    @Test
    fun `the npc holds while the player attacking it walks in close, and chases otherwise`() {
        val shifter = npc(Tile(3201, 3201))
        assertTrue(PawnPathAction.holdsForApproachingPlayer(shifter, player(Tile(3200, 3200), shifter, moving = true)), "diagonal, player walking in")
        assertTrue(PawnPathAction.holdsForApproachingPlayer(shifter, player(Tile(3199, 3201), shifter, moving = true)), "two tiles apart")
        assertTrue(PawnPathAction.holdsForApproachingPlayer(shifter, player(Tile(3201, 3201), shifter, moving = true)), "on one tile")
        assertFalse(PawnPathAction.holdsForApproachingPlayer(shifter, player(Tile(3200, 3200), shifter, moving = false)), "player stands still")
        assertFalse(PawnPathAction.holdsForApproachingPlayer(shifter, player(Tile(3200, 3200), null, moving = true)), "player walks away")
        assertFalse(PawnPathAction.holdsForApproachingPlayer(shifter, player(Tile(3197, 3201), shifter, moving = true)), "still far")
        val other = npc(Tile(3190, 3190))
        assertFalse(PawnPathAction.holdsForApproachingPlayer(shifter, player(Tile(3200, 3200), other, moving = true)), "player attacks another npc")
        assertFalse(PawnPathAction.holdsForApproachingPlayer(player(Tile(3201, 3201), null, true), player(Tile(3200, 3200), null, true)), "players dance as in OSRS")
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
