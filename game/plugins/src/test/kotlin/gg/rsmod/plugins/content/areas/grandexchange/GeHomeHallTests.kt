package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.Direction
import gg.rsmod.plugins.api.cfg.Npcs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeHomeHallTests {
    @Test
    fun `service posts are unique balanced and keep entrances clear`() {
        val posts = GeHomeHall.SERVICE_POSTS
        assertEquals(posts.size, posts.map { it.npc }.distinct().size)
        assertEquals(posts.size, posts.map { it.dx to it.dz }.distinct().size)
        assertTrue(posts.all { it.dx in 0 until GeHomeHall.WIDTH && it.dz in 0 until GeHomeHall.DEPTH })
        // Doorways (west/east dz 5..7, south dx 5..7) stay clear, and so do the four corners (armour).
        assertFalse(posts.any { (it.dx == 0 || it.dx == GeHomeHall.WIDTH - 1) && it.dz in 5..7 })
        assertFalse(posts.any { it.dz == 0 && it.dx in 5..7 })
        assertFalse(posts.any { (it.dx == 0 || it.dx == GeHomeHall.WIDTH - 1) && (it.dz == 0 || it.dz == GeHomeHall.DEPTH - 1) })
        // Every stall faces into the hall and the tile in front of it is not another stall.
        val taken = posts.map { it.dx to it.dz }.toSet()
        posts.forEach { post ->
            val front =
                when (post.facing) {
                    Direction.EAST -> post.dx + 1 to post.dz
                    Direction.WEST -> post.dx - 1 to post.dz
                    Direction.NORTH -> post.dx to post.dz + 1
                    else -> post.dx to post.dz - 1
                }
            assertTrue(front.first in 1 until GeHomeHall.WIDTH - 1 && front.second in 1 until GeHomeHall.DEPTH - 1, "${post.npc} faces a wall")
            assertFalse(front in taken, "${post.npc} is boxed in")
        }
    }

    @Test
    fun `hall Lucien is non combat and penguins are absent`() {
        val ids = GeHomeHall.SERVICE_POSTS.map { it.npc }.toSet()
        assertTrue(GeHomeHall.SERVICE_LUCIEN in ids)
        assertFalse(Npcs.LUCIEN in ids)
        assertTrue(ids.intersect(GeHomeHall.REMOVED_PENGUINS).isEmpty())
    }
}
