package gg.rsmod.plugins.content.mechanics.doors

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.StaticObject
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [VoidDoors] swing math against the parts of Void's door model that can be pinned without a live map:
 * a lone door opens and closes back onto its original tile and rotation, the open swing agrees with
 * [gg.rsmod.plugins.api.ext.openDoor]'s wall transform, and a neighbouring leaf turns a click into a
 * double-door plan. Double door and gate end positions remain PENDING_HUMAN_RETEST (visual).
 */
class VoidDoorsTests {
    private val closedId = 1000
    private val openedId = 1001
    private val closedToOpened = mapOf(closedId to openedId, 2000 to 2001)
    private val openedToClosed = mapOf(openedId to closedId, 2001 to 2000)
    private val wall = 0

    private fun def(name: String): ObjectDef =
        mockk<ObjectDef>(relaxed = true).also {
            every { it.name } returns name
            every { it.rotated } returns false
        }

    private val defs = mapOf(closedId to def("Door"), openedId to def("Door"), 2000 to def("Door"), 2001 to def("Door"))

    private fun plan(
        obj: GameObject,
        open: Boolean,
        world: Map<Tile, GameObject> = emptyMap(),
    ) = VoidDoors.plan(obj, open, closedToOpened, openedToClosed, { tile, _ -> world[tile] }, { defs[it] }, { false })

    @Test
    fun `a lone door opens and closes back to its original tile and rotation`() {
        for (rot in 0..3) {
            val start = StaticObject(closedId, wall, rot, Tile(3200, 3200))
            val opened = plan(start, open = true)!!.single()
            assertEquals(openedId, opened.id)
            val reopened = StaticObject(openedId, wall, opened.rot, opened.tile)
            val closed = plan(reopened, open = false)!!.single()
            assertEquals(closedId, closed.id)
            assertEquals(start.tile, closed.tile, "rotation $rot tile")
            assertEquals(rot, closed.rot, "rotation $rot")
        }
    }

    @Test
    fun `the lone-door open swing matches World openDoor's wall transform`() {
        // World.openDoor (non-diagonal, not inverted): rot 0 -> x-1, 1 -> z+1, 2 -> x+1, 3 -> z-1; rot + 1.
        val expected = mapOf(0 to Tile(3199, 3200), 1 to Tile(3200, 3201), 2 to Tile(3201, 3200), 3 to Tile(3200, 3199))
        for ((rot, tile) in expected) {
            val opened = plan(StaticObject(closedId, wall, rot, Tile(3200, 3200)), open = true)!!.single()
            assertEquals(tile, opened.tile, "rotation $rot")
            assertEquals((rot + 1) and 3, opened.rot)
        }
    }

    @Test
    fun `a neighbouring door leaf makes it a double door`() {
        val left = StaticObject(closedId, wall, 0, Tile(3200, 3200))
        val right = StaticObject(2000, wall, 0, Tile(3200, 3201))
        val result = plan(left, open = true, world = mapOf(right.tile to right))!!
        assertEquals(listOf(openedId, 2001), result.map { it.id })
    }

    @Test
    fun `an unpaired id is not swung`() {
        assertNull(plan(StaticObject(4242, wall, 0, Tile(3200, 3200)), open = true))
    }
}
