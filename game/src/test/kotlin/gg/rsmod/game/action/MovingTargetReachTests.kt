package gg.rsmod.game.action

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.region.ChunkSet
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N214 (owner 2026-10-23: Pick-up pet / Trade npc - "blijven allebei naar elkaar toe lopen"): a target that moved but is in reach is
 * reached; before, "the npc moved" alone re-walked, and a follower moves every tick.
 */
class MovingTargetReachTests {
    private val world = mockk<World>(relaxed = true).also { every { it.collision } returns CollisionManager(ChunkSet(it), createChunksIfNeeded = true) }

    private fun pawn(tile: Tile, size: Int = 1): Pawn =
        mockk<Pawn>(relaxed = true).also {
            every { it.tile } returns tile
            every { it.getSize() } returns size
            every { it.world } returns world
        }

    @Test
    fun `a pet beside its owner is reached, also a 2x2 one, a diagonal or a far one is not`() {
        val owner = pawn(Tile(3200, 3200))
        assertTrue(PawnPathAction.reachedMovingTarget(owner, pawn(Tile(3201, 3200)), 1, false))
        assertTrue(PawnPathAction.reachedMovingTarget(owner, pawn(Tile(3198, 3199), size = 2), 1, false), "a 2x2 critter sharing an edge")
        assertFalse(PawnPathAction.reachedMovingTarget(owner, pawn(Tile(3201, 3201)), 1, false), "diagonal: no shared edge")
        assertFalse(PawnPathAction.reachedMovingTarget(owner, pawn(Tile(3202, 3200)), 1, false))
        assertFalse(PawnPathAction.reachedMovingTarget(owner, pawn(Tile(3200, 3200)), 1, false), "standing on it is not reach")
    }

    @Test
    fun `both re-walk spots ask for reach first`() {
        val source = File("src/main/kotlin/gg/rsmod/game/action/PawnPathAction.kt").readText()
        assertTrue("!other.tile.sameAs(initialTile) && !reachedMovingTarget(pawn, other," in source)
        val loop = source.substringAfter("while (!pawn.tile.sameAs(route.tail)) {").substringBefore("it.wait(1)")
        assertTrue("reachedMovingTarget(" in loop && loop.indexOf("reachedMovingTarget(") < loop.indexOf("return walkTo("), "in reach before routing again")
    }
}
