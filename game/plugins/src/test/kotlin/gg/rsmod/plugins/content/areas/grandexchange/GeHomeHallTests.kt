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
        assertEquals(posts.size, posts.map { Triple(it.dx, it.dz, it.level) }.distinct().size)
        assertTrue(posts.all { it.dx in 0 until GeHomeHall.WIDTH && it.dz in 0 until GeHomeHall.DEPTH })
        // Doorways stay clear, and so do the four corners (armour).
        assertFalse(posts.any { (it.dx == 0 || it.dx == GeHomeHall.WIDTH - 1) && it.dz in GeHomeHall.SIDE_DOOR })
        assertFalse(posts.any { it.level == 0 && it.dz == 0 && it.dx in GeHomeHall.SOUTH_DOOR })
        assertFalse(posts.any { (it.dx == 0 || it.dx == GeHomeHall.WIDTH - 1) && (it.dz == 0 || it.dz == GeHomeHall.DEPTH - 1) })
        // Every stall faces into the hall and the tile in front of it is not another stall.
        val taken = posts.map { it.dx to it.dz }.toSet()
        posts.filter { it.level == 0 }.forEach { post ->
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
    fun `every stall's whole npc stands on free hall floor`() {
        // Azzanadra (2 x 2) once stood half inside the north wall (owner 2026-09-26).
        val design = HomeDecorFile.load(java.nio.file.Paths.get("..", "..", "data", "cfg", "home_decor.txt"))
        val stairTiles = GeHomeHall.STAIRS.flatMap { s -> (0..1).flatMap { dx -> (0..1).map { dz -> (s.base.x - GeHomeHall.X + dx) to (s.base.z - GeHomeHall.Z + dz) } } }.toSet()
        GeHomeHall.SERVICE_POSTS.forEach { post ->
            val footprint = (0 until post.size).flatMap { dx -> (0 until post.size).map { dz -> post.dx + dx to post.dz + dz } }
            footprint.forEach { (dx, dz) ->
                assertTrue(dx in 0 until GeHomeHall.WIDTH && dz in 0 until GeHomeHall.DEPTH, "${post.npc} reaches off the floor at $dx,$dz")
                assertFalse(post.level == 0 && (dx to dz) in stairTiles, "${post.npc} stands in a staircase")
                val tile = gg.rsmod.game.model.Tile(GeHomeHall.X + dx, GeHomeHall.Z + dz, post.level)
                val solid = design.placements.filter { it.tile == tile && it.type in 9..21 }
                assertTrue(solid.isEmpty(), "${post.npc} stands in $solid")
            }
        }
    }

    @Test
    fun `hall Lucien is non combat and penguins are absent`() {
        val ids = GeHomeHall.SERVICE_POSTS.map { it.npc }.toSet()
        assertTrue(GeHomeHall.SERVICE_LUCIEN in ids)
        assertFalse(Npcs.LUCIEN in ids)
        assertTrue(ids.intersect(GeHomeHall.REMOVED_PENGUINS).isEmpty())
    }

    @Test
    fun `spiral staircases keep clear of every ground floor stall`() {
        val stairTiles = GeHomeHall.STAIRS.flatMap { s -> (0..1).flatMap { dx -> (0..1).map { dz -> (s.base.x - GeHomeHall.X + dx) to (s.base.z - GeHomeHall.Z + dz) } } }.toSet()
        assertTrue(GeHomeHall.SERVICE_POSTS.filter { it.level == 0 }.none { (it.dx to it.dz) in stairTiles })
        GeHomeHall.STAIRS.forEach { s ->
            assertEquals(1, s.gallery.height)
            assertEquals(0, s.floor.height)
            assertFalse((s.floor.x - GeHomeHall.X to s.floor.z - GeHomeHall.Z) in stairTiles)
        }
    }
}
