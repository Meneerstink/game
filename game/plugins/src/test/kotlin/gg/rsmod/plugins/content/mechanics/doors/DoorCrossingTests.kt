package gg.rsmod.plugins.content.mechanics.doors

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.Landing
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.testutil.RealMapWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-03 00:26 ("noclip again", Barrows tunnels, stuck at 3562,9710 with no step possible): every "walk through the
 * door" script derived the far side from the door's rotation the wrong way round and stepped, with collision off, to a neighbour
 * of the door tile instead of across its face. One rule now ([DoorCrossing]); checked against the 667 client's wall rule
 * (rotation 0 west, 1 north, 2 east, 3 south) and the real Barrows door the owner got stuck at.
 */
class DoorCrossingTests {
    private val wall = ObjectType.LENGTHWISE_WALL.value
    private val door = Tile(3562, 9711)

    @Test
    fun `a wall door is crossed between its own tile and the tile its face looks at`() {
        // Rotation 2: the door sits on the east face of 3562,9711 - crossed along x with 3563,9711.
        assertEquals(door, DoorCrossing.destination(Tile(3563, 9711), door, wall, 2))
        assertEquals(Tile(3563, 9711), DoorCrossing.destination(door, door, wall, 2))
        assertEquals(Tile(3561, 9711), DoorCrossing.destination(door, door, wall, 0))
        assertEquals(door, DoorCrossing.destination(Tile(3561, 9711), door, wall, 0))
        assertEquals(Tile(3562, 9712), DoorCrossing.destination(door, door, wall, 1))
        assertEquals(door, DoorCrossing.destination(Tile(3562, 9712), door, wall, 1))
        assertEquals(Tile(3562, 9710), DoorCrossing.destination(door, door, wall, 3))
        assertEquals(door, DoorCrossing.destination(Tile(3562, 9710), door, wall, 3))
    }

    @Test
    fun `from anywhere else a wall door is no crossing`() {
        // The old math sent a player standing south of an east-facing door to 3562,9710 - along the wall, into the pocket.
        assertNull(DoorCrossing.destination(Tile(3562, 9710), door, wall, 2))
        assertNull(DoorCrossing.destination(Tile(3563, 9710), door, wall, 2))
        assertNull(DoorCrossing.destination(Tile(3567, 9710), door, wall, 2))
        assertNull(DoorCrossing.destination(Tile(3563, 9711, 1), door, wall, 2))
    }

    @Test
    fun `a free-standing door or gate is crossed to the mirrored tile from a cardinal neighbour only`() {
        val gate = Tile(3000, 3000)
        val type = ObjectType.INTERACTABLE.value
        assertEquals(Tile(3001, 3000), DoorCrossing.destination(Tile(2999, 3000), gate, type, 0))
        assertEquals(Tile(3000, 2999), DoorCrossing.destination(Tile(3000, 3001), gate, type, 3))
        assertNull(DoorCrossing.destination(Tile(2999, 2999), gate, type, 0))
        assertNull(DoorCrossing.destination(Tile(2998, 3000), gate, type, 0))
    }

    @Test
    fun `the Barrows door the owner got stuck at is an east-facing wall whose crossing stays on open corridor tiles`() {
        val world = RealMapWorld.world
        val doors = world.chunks.get(door, true)!!.getEntities<GameObject>(door, EntityType.STATIC_OBJECT).filter { it.type == wall }
        val tunnelDoor = doors.firstOrNull { it.id in 6716..6731 || it.id in 6735..6750 }
        assertTrue("a Barrows tunnel door at ${door.x},${door.z}: $doors", tunnelDoor != null)
        assertEquals(2, tunnelDoor!!.rot)
        val outside = Tile(3563, 9711)
        assertEquals(outside, DoorCrossing.destination(door, tunnelDoor))
        assertEquals(door, DoorCrossing.destination(outside, tunnelDoor))
        // 3562,9710, where the old crossing put the owner, is a one-tile pocket; both real sides open onto the corridor.
        assertEquals(1, Landing.pocket(world.collision, Tile(3562, 9710)).size)
        assertTrue(Landing.pocket(world.collision, outside).size >= Landing.OPEN_GROUND)
    }
}
