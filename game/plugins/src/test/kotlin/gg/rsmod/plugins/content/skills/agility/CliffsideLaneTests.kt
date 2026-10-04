package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.testutil.RealMapWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 154847 (2026-10-04, priority #1 noclip): every Trollheim Cliffside is climbed on Jagex's own lane - the 11449 (low) and
 * 11450 (high) wall pieces of this 667 map - which is exactly what the climbs 3393 (up) and 15239 (down) cover: 4 tiles, one storey.
 * Checked on the real map for every placement (Cliffside.kt for the measurements and the own-client check).
 */
class CliffsideLaneTests {
    private val world get() = RealMapWorld.world

    private fun loc(
        id: Int,
        x: Int,
        z: Int,
    ): GameObject {
        val tile = Tile(x, z, 0)
        return world.chunks.get(tile, createIfNeeded = true)!!
            .getEntities<GameObject>(tile, EntityType.STATIC_OBJECT).first { it.id == id }
    }

    @Test
    fun `every Cliffside lane is 4 tiles and one storey, high end at 11450, as the Jagex climbs need`() {
        val placements = listOf(listOf(3803, 2876, 3671), listOf(9303, 2855, 3663), listOf(9304, 2873, 3660), listOf(9305, 2894, 3672), listOf(9306, 2909, 3685))
        for ((id, x, z) in placements) {
            val lane = Cliffside.lane(world, loc(id, x, z))
            assertNotNull("$id@$x,$z has lane walls", lane)
            lane!!
            assertEquals("$id@$x,$z lane length", 4, lane.low.getDistance(lane.high))
            assertTrue("$id@$x,$z lane is straight", lane.low.x == lane.high.x || lane.low.z == lane.high.z)
            val low = TerrainHeight.of(world, lane.low)!!
            val high = TerrainHeight.of(world, lane.high)!!
            assertEquals("$id@$x,$z one storey between the ends (heights negative up)", 240, low - high)
            assertTrue("$id@$x,$z low end usable", isUsableLanding(world, lane.low))
            assertTrue("$id@$x,$z high end usable", isUsableLanding(world, lane.high))
            assertTrue("$id@$x,$z from the high end goes down", Cliffside.goesDown(lane, lane.high))
            assertFalse("$id@$x,$z from the low end goes up", Cliffside.goesDown(lane, lane.low))
        }
    }

    @Test
    fun `the owner's report spot climbs down from the plateau onto 2897,3674`() {
        val lane = Cliffside.lane(world, loc(9305, 2894, 3672))!!
        assertEquals(Tile(2893, 3674), Tile(lane.high.x, lane.high.z))
        assertEquals(Tile(2897, 3674), Tile(lane.low.x, lane.low.z))
        assertTrue(Cliffside.goesDown(lane, Tile(2892, 3676)))
    }

    @Test
    fun `levels are the RuneScape Wiki's per object`() {
        assertEquals(mapOf(9303 to 41, 9304 to 43, 9305 to 44, 9306 to 47, 3803 to 0), Cliffside.LEVELS)
    }
}
