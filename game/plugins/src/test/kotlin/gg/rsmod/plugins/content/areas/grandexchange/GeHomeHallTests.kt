package gg.rsmod.plugins.content.areas.grandexchange

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
        assertTrue(posts.all { it.dx in 0 until GeHomeHall.SIZE && it.dz in 0 until GeHomeHall.SIZE })
        assertFalse(posts.any { (it.dx == 10 && it.dz in 4..6) || (it.dz == 10 && it.dx in 4..6) })
    }

    @Test
    fun `hall Lucien is non combat and penguins are absent`() {
        val ids = GeHomeHall.SERVICE_POSTS.map { it.npc }.toSet()
        assertTrue(GeHomeHall.SERVICE_LUCIEN in ids)
        assertFalse(Npcs.LUCIEN in ids)
        assertTrue(ids.intersect(GeHomeHall.REMOVED_PENGUINS).isEmpty())
    }
}
