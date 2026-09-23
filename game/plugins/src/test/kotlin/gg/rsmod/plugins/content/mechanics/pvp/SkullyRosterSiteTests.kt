package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-09-23 bank pass: every Skully stands against a wall facing into the bank with his Loot Chest beside him, the chest's
 * front facing the same way; Edgeville and Camelot use the owner-named desk spots.
 */
class SkullyRosterSiteTests {
    @Test
    fun `the chest front follows the Ferox orientation - rotation 2 faces south`() {
        assertEquals(0, SkullyRoster.chestRotation(Direction.NORTH))
        assertEquals(1, SkullyRoster.chestRotation(Direction.EAST))
        assertEquals(2, SkullyRoster.chestRotation(Direction.SOUTH))
        assertEquals(3, SkullyRoster.chestRotation(Direction.WEST))
    }

    @Test
    fun `every owner-placed chest is a cardinal neighbour of its Skully, never his own tile`() {
        SkullyRoster.SITES.filter { it.chest != null }.forEach { site ->
            val chest = site.chest!!
            val d = kotlin.math.abs(chest.x - site.wanted.x) + kotlin.math.abs(chest.z - site.wanted.z)
            assertEquals(1, d, "${site.label}: chest must be beside Skully")
            assertEquals(site.wanted.height, chest.height, site.label)
            assertTrue(site.face != null, "${site.label}: an owner-placed Skully needs a facing")
        }
    }

    @Test
    fun `Edgeville and Camelot use the removed desks`() {
        val edge = SkullyRoster.SITES.single { it.label == "Edgeville bank" }
        assertEquals(Tile(3092, 3488, 0), edge.wanted)
        assertEquals(SkullyRoster.SKULLY_MAX, edge.npcId)
        assertTrue(Tile(3092, 3488, 0) in edge.clear)
        val seers = SkullyRoster.SITES.single { it.label == "Seers' Village bank" }
        assertEquals(Tile(2721, 3491, 0), seers.wanted)
        assertEquals(SkullyRoster.SKULLY_BOB, seers.npcId)
        assertEquals(Direction.EAST, seers.face)
    }

    @Test
    fun `no two sites share a Skully or chest tile`() {
        val tiles = SkullyRoster.SITES.flatMap { listOfNotNull(it.wanted.takeIf { _ -> it.exact }, it.chest) }
        assertEquals(tiles.size, tiles.toSet().size)
    }
}
