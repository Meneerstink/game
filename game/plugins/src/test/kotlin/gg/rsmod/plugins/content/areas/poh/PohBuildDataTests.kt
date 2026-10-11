package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.plugins.api.cfg.Items
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Night run 2026-10-10 (building mode): the buildable pieces carry the OSRS Wiki data, the theme edging covers OSRS's 60 garden
 * tiles with a piece for every space, and the house locations / rooms stay consistent.
 */
class PohBuildDataTests {
    @Test
    fun `dining tables are the OSRS table space list`() {
        val mahogany = PohBuild.DINING_TABLES.single { it.obj == 13298 }
        assertEquals(52, mahogany.level)
        assertEquals(840.0, mahogany.xp)
        assertEquals(listOf(Items.MAHOGANY_PLANK to 6), mahogany.materials)
        assertEquals(listOf(10, 22, 31, 38, 45, 52, 72), PohBuild.DINING_TABLES.map { it.level })
        assertEquals((13293..13299).toList(), PohBuild.DINING_TABLES.map { it.obj })
    }

    @Test
    fun `the otherworldly theme is OSRS level 75 and 316 xp`() {
        assertEquals(75, PohBuild.OTHERWORLDLY.level)
        assertEquals(316.0, PohBuild.OTHERWORLDLY.xp)
        assertTrue(PohBuild.OTHERWORLDLY.plant)
    }

    @Test
    fun `theme edging covers the OSRS garden tiles once each`() {
        val tiles = PlayerHouse.THEME_EDGES.map { it.x to it.z }
        assertEquals(60, tiles.size)
        assertEquals(tiles.size, tiles.toSet().size)
        assertTrue(tiles.all { (x, z) -> x in 0..7 && z in 0..7 })
        assertTrue(PlayerHouse.THEME_EDGES.all { it.space in PlayerHouse.OTHERWORLDLY_EDGES })
        // The 2x2 theme itself (6,6)-(7,7) keeps its corner pieces under it, the doorways stay walkable ground decoration.
        assertTrue(PlayerHouse.THEME_EDGES.all { it.rot in 0..3 })
    }

    @Test
    fun `house locations`() {
        val offered = PlayerHouse.Location.values().filter { it.offered }
        assertFalse(PlayerHouse.Location.YANILLE in offered)
        assertEquals(PlayerHouse.Location.values().size, PlayerHouse.Location.values().map { it.portal }.toSet().size)
        assertEquals(PlayerHouse.Location.RIMMINGTON, PlayerHouse.Location.values().first())
    }

    @Test
    fun `room plan has one room per chunk around the garden`() {
        val offsets = PlayerHouse.Room.values().map { it.dx to it.dz }
        assertEquals(offsets.size, offsets.toSet().size)
        assertTrue(PlayerHouse.Room.values().filter { it != PlayerHouse.Room.GARDEN }.all { kotlin.math.abs(it.dx) + kotlin.math.abs(it.dz) == 1 })
        assertEquals(5, PlayerHouse.ROOM_COUNT)
    }
}
