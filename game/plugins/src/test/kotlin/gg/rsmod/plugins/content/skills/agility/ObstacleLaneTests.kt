package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.SceneryCrossing
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.testutil.RealMapWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner priority #1 2026-10-03 (obstacles / noclip, E17 "a player may never noclip", OB7): every rock, cliffside, stile, fence
 * and ditch segment of this 667 map is crossed on a fixed straight lane through its own footprint - from the tile right in front
 * of it to the tile right behind it - never from wherever the click left the player and never over other scenery. Checked on the
 * real map for every placement the handlers serve, from all four sides.
 */
class ObstacleLaneTests {
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

    private fun sides(obj: GameObject): List<Tile> {
        val def = obj.getDef(world.definitions)
        val w = def.getRotatedWidth(obj)
        val l = def.getRotatedLength(obj)
        val (x, z) = obj.tile.x to obj.tile.z
        return listOf(Tile(x - 2, z + l / 2), Tile(x + w + 1, z + l / 2), Tile(x + w / 2, z - 2), Tile(x + w / 2, z + l + 1))
    }

    private fun assertLane(
        what: String,
        obj: GameObject,
        c: Crossing?,
    ) {
        assertNotNull("$what: no lane", c)
        c!!
        assertTrue("$what: start ${c.start} usable", isUsableLanding(world, c.start))
        assertTrue("$what: landing ${c.landing} usable", isUsableLanding(world, c.landing))
        assertEquals("$what: one straight cardinal line", 0, if (c.start.x == c.landing.x || c.start.z == c.landing.z) 0 else 1)
        val foreign = SceneryCrossing.lineBlocks(world, c.start, c.landing, obj).filter { it.cause == SceneryCrossing.Cause.FOREIGN_LOC }
        assertTrue("$what: passes other scenery $foreign", foreign.isEmpty())
    }

    @Test
    fun `every Trollheim rock and cliffside has a lane across its height step from every side, down is high to low`() {
        // id, x, z, the side Void climbs down from (the high side), measured from this map's terrain
        val cliffs =
            listOf(
                listOf(3803, 2876, 3671), listOf(9303, 2855, 3663), listOf(9304, 2873, 3660), listOf(9305, 2894, 3672),
                listOf(9306, 2909, 3685), listOf(34877, 2876, 3619), listOf(34877, 2893, 3613), listOf(34878, 2874, 3595),
                listOf(34878, 2951, 3681), listOf(34889, 2931, 3680),
            )
        for ((id, x, z) in cliffs) {
            val obj = loc(id, x, z)
            val lanes = sides(obj).map { from -> cliffCrossing(world, obj, from) }
            lanes.forEachIndexed { i, c -> assertLane("$id@$x,$z side $i", obj, c) }
            // Whatever side the click comes from, the crossing runs across the cliff, between the same two sides.
            val ends = lanes.flatMap { listOf(it!!.start, it.landing) }.map { if (it.x in x until x + 4 && it.z in z until z + 4) "in" else "${it.x - x},${it.z - z}" }
            assertTrue("$id@$x,$z lanes stay on one axis: $ends", lanes.map { it!!.direction.isDiagonal() }.none { it })
            val down = lanes.first { goesDown(world, it!!) }!!
            assertTrue("$id@$x,$z goes down from the higher ground", TerrainHeight.of(world, down.start)!! < TerrainHeight.of(world, down.landing)!!)
        }
    }

    @Test
    fun `the Void down directions hold on this map`() {
        // Void Trollheim.kt: 3803 down westwards, 9303 down westwards, 9304 down southwards; 9305 / 9306 up westwards (down east).
        fun downDirection(
            id: Int,
            x: Int,
            z: Int,
        ) = sides(loc(id, x, z)).mapNotNull { cliffCrossing(world, loc(id, x, z), it) }.first { goesDown(world, it) }.direction
        assertEquals(gg.rsmod.game.model.Direction.WEST, downDirection(3803, 2876, 3671))
        assertEquals(gg.rsmod.game.model.Direction.WEST, downDirection(9303, 2855, 3663))
        assertEquals(gg.rsmod.game.model.Direction.SOUTH, downDirection(9304, 2873, 3660))
        assertEquals(gg.rsmod.game.model.Direction.EAST, downDirection(9305, 2894, 3672))
        assertEquals(gg.rsmod.game.model.Direction.EAST, downDirection(9306, 2909, 3685))
    }

    @Test
    fun `stiles, fences and the crumbling wall are crossed through the fence, never along it`() {
        val climbs =
            listOf(
                listOf(993, 2761, 3436), listOf(3730, 2817, 3562), listOf(7527, 3043, 3305), listOf(7527, 1632, 3570),
                listOf(34776, 2638, 3350), listOf(45205, 3197, 3276), listOf(48208, 3328, 3161), listOf(11844, 2935, 3355),
                listOf(18411, 3584, 3263), listOf(19222, 2371, 3620),
            )
        for ((id, x, z) in climbs) {
            val obj = loc(id, x, z)
            val def = obj.getDef(world.definitions)
            for (from in sides(obj)) {
                val c = longAxisCrossing(world, obj, from)
                assertLane("$id@$x,$z from $from", obj, c)
                if (def.getRotatedLength(obj) > def.getRotatedWidth(obj)) assertEquals("$id along z", c!!.start.x, c.landing.x)
                if (def.getRotatedWidth(obj) > def.getRotatedLength(obj)) assertEquals("$id along x", c!!.start.z, c.landing.z)
            }
        }
    }

    @Test
    fun `every Wilderness ditch segment is jumped across its short side`() {
        val segments =
            world.chunks.let {
                val found = ArrayList<GameObject>()
                for (x in 2940..3010 step 1) {
                    val t = Tile(x, 3521, 0)
                    it.get(t, createIfNeeded = true)!!.getEntities<GameObject>(t, EntityType.STATIC_OBJECT).filter { o -> o.id in 1440..1444 }.forEach { o -> found += o }
                }
                found
            }
        assertTrue("ditch segments found: ${segments.size}", segments.size >= 3)
        for (obj in segments) {
            for (from in sides(obj)) {
                val c = shortAxisCrossing(world, obj, from)
                assertLane("ditch ${obj.tile} from $from", obj, c)
                assertEquals("ditch ${obj.tile}: Void wall.y - 1 / wall.y + 2", 3, Math.abs(c!!.landing.z - c.start.z))
            }
        }
    }
}
