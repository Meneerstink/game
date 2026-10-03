package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.testutil.RealMapWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Agility Pyramid (agility_pyramid.plugin.kts, owner priority #1 2026-10-03): on the real 667 map every crossing starts on a
 * usable tile in front of its obstacle and ends on a usable tile behind it, every fall lands on a usable tile one floor down,
 * and every stair leads onto a usable tile - for every placement the plugin serves.
 */
class AgilityPyramidLaneTests {
    private val world get() = RealMapWorld.world

    private fun loc(
        id: Int,
        t: Tile,
    ): GameObject =
        world.chunks.get(t, createIfNeeded = true)!!
            .getEntities<GameObject>(t, EntityType.STATIC_OBJECT).first { it.id == id }

    private fun usable(
        what: String,
        t: Tile,
    ) = assertTrue("$what: ${t.x},${t.z},${t.height} is not a usable tile", AgilityPyramid.usable(world, t))

    private fun fallsDown(
        what: String,
        from: Tile,
        target: Tile,
    ) {
        assertEquals("$what: falls exactly one floor", from.height - 1, target.height.let { if (target.regionId != from.regionId) from.height - 1 else it })
        val landing = AgilityPyramid.fallLanding(world, target)
        assertNotNull("$what: no usable fall tile near $target", landing)
        assertEquals("$what: the sourced fall tile itself is usable", target, landing)
    }

    @Test
    fun `ledges gaps and planks cross between usable ends and fall onto usable tiles`() {
        for ((name, lanes) in listOf("ledge" to AgilityPyramid.LEDGES, "gap" to AgilityPyramid.GAP_CROSSINGS, "plank" to AgilityPyramid.PLANKS)) {
            for (lane in lanes) {
                usable("$name ${lane.a}", lane.a)
                usable("$name ${lane.b}", lane.b)
                assertEquals("$name ${lane.a}: one plane", lane.a.height, lane.b.height)
                assertTrue("$name ${lane.a}: straight", lane.a.x == lane.b.x || lane.a.z == lane.b.z)
                fallsDown("$name ${lane.a}", lane.a, lane.fall)
            }
        }
    }

    @Test
    fun `jump gaps and low walls have a straight usable lane from both sides`() {
        val gaps =
            listOf(
                Tile(3356, 2847, 2), Tile(3364, 2833, 2), Tile(3370, 2841, 3), Tile(3040, 4697, 2), Tile(3048, 4695, 2), Tile(3046, 4697, 3),
            )
        val walls = listOf(Tile(3354, 2849, 1), Tile(3370, 2833, 2), Tile(3358, 2839, 3), Tile(3042, 4701, 2), Tile(3047, 4693, 2))
        for ((id, tiles) in listOf(10859 to gaps, 10865 to walls)) {
            for (t in tiles) {
                val obj = loc(id, t)
                val lanes =
                    listOf(t.transform(-2, 0), t.transform(3, 0), t.transform(0, -2), t.transform(0, 3))
                        .mapNotNull { AgilityPyramid.laneOver(world, obj, it) }
                assertTrue("$id@$t: no lane", lanes.isNotEmpty())
                // Both directions of the course: some lane starts where another lands.
                assertTrue("$id@$t: lanes only one way $lanes", lanes.any { a -> lanes.any { b -> a.start.sameAs(b.landing) } })
                lanes.forEach {
                    usable("$id@$t start", it.start)
                    usable("$id@$t landing", it.landing)
                }
                if (id == 10859) fallsDown("jump $t", t, AgilityPyramid.JUMP_FALLS.entries.first { it.key.sameAs(t) }.value)
            }
        }
    }

    @Test
    fun `stone blocks roll between usable tiles and moving blocks push onto usable tiles`() {
        for (block in AgilityPyramid.STONE_BLOCKS) {
            for ((near, far) in block.lanes) {
                usable("block ${block.corner} near", near)
                usable("block ${block.corner} far", far)
                assertEquals("block ${block.corner}: three tiles over the block", 3, near.getDistance(far))
                val back = near.add(block.forward, -1)
                val below = if (back.regionId == AgilityPyramid.REGION_TOP && back.height == 2) Tile(back.x + 320, back.z - 1856, 3) else back.transform(-1)
                assertNotNull("block ${block.corner}: no fall tile near $below", AgilityPyramid.fallLanding(world, below))
            }
        }
        for (block in AgilityPyramid.MOVING_BLOCKS) {
            for (t in block.tiles) {
                usable("moving block walkway", t)
                fallsDown("moving block push from $t", t, block.fall(t))
            }
        }
    }

    @Test
    fun `every reachable staircase starts and lands on usable tiles`() {
        val up = listOf(Tile(3354, 2831, 0), Tile(3356, 2833, 1), Tile(3358, 2835, 2), Tile(3360, 2837, 3), Tile(3042, 4695, 2))
        val down = listOf(Tile(3354, 2831, 1), Tile(3356, 2833, 2), Tile(3358, 2835, 3), Tile(3040, 4693, 2), Tile(3042, 4695, 3))
        for ((isUp, tiles) in listOf(true to up, false to down)) {
            for (t in tiles) {
                val obj = loc(if (isUp) 10857 else 10858, t)
                val front = AgilityPyramid.stairsFront(world, obj, t, isUp)
                val landing = AgilityPyramid.stairsLanding(world, obj, t, isUp)
                assertNotNull("stairs $t: no front", front)
                assertNotNull("stairs $t: no landing", landing)
                usable("stairs $t landing", landing!!)
            }
        }
    }

    @Test
    fun `the climbing rocks to the pyramid cross the rock band between usable tiles`() {
        for (x in listOf(3335, 3349)) {
            for (z in (if (x == 3335) 2826 else 2827)..2829) {
                val id = if (x == 3335) 16535 else 16536
                val c = pairCrossing(world, Tile(x, z), Tile(x + 2, z))
                assertNotNull("rocks $x,$z", c)
                usable("rocks $x,$z start", c!!.start)
                usable("rocks $x,$z landing", c.landing)
                // Every rock row has a straight lane over the band (16535: 2826-2829, 16536: 2827-2829 in this map).
                assertTrue("rocks $x,$z straight across the band", c.start.z == c.landing.z && Math.abs(c.start.x - c.landing.x) == 4)
                loc(id, Tile(x, z))
            }
        }
        usable("doorway base", AgilityPyramid.BASE)
    }
}
