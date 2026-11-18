package gg.rsmod.game.model

import gg.rsmod.game.model.entity.Player
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N602 (2026-11-15): loc/ground-item options are accepted for every tile of the client's loaded 104 x 104 map, not
 * only within a Euclidean 32-tile radius. Tiles measured 08-11 in the ToA Crondis room: from 6807,155 / 6808,155 /
 * 6807,152 the waterfall op on 6820,186 was silently dropped (33.6 / 33.2 / 36.4 tiles); from 6811,156 it walked.
 */
class BuildAreaInteractionTests {
    private val waterfall = Tile(6820, 186, 0)

    /** The base the server sends for [player] (PlayerPreSynchronizationTask: chunk - 6, in tiles). */
    private fun baseFor(player: Tile) = Coordinate(((player.x shr 3) - 6) shl 3, ((player.z shr 3) - 6) shl 3, player.height)

    @Test fun `the Crondis corner clicks that did nothing are inside the loaded map`() {
        for (from in listOf(Tile(6807, 155, 0), Tile(6808, 155, 0), Tile(6807, 152, 0))) {
            assertFalse(waterfall.viewableFrom(from, Player.TILE_VIEW_DISTANCE), "old gate refused $from")
            assertTrue(Player.inBuildArea(baseFor(from), from, waterfall), "build area accepts $from")
        }
    }

    @Test fun `tiles that already worked keep working`() {
        val from = Tile(6811, 156, 0)
        assertTrue(Player.inBuildArea(baseFor(from), from, waterfall))
        assertTrue(Player.inBuildArea(baseFor(from), from, from))
    }

    @Test fun `outside the loaded map, another floor, or no map yet stays refused`() {
        val from = Tile(3222, 3218, 0)
        val base = baseFor(from)
        assertTrue(Player.inBuildArea(base, from, Tile(base.x, base.z, 0)))
        assertTrue(Player.inBuildArea(base, from, Tile(base.x + 103, base.z + 103, 0)))
        assertFalse(Player.inBuildArea(base, from, Tile(base.x + 104, base.z, 0)))
        assertFalse(Player.inBuildArea(base, from, Tile(base.x - 1, base.z, 0)))
        assertFalse(Player.inBuildArea(base, from, Tile(3222, 3219, 1)))
        // Before the first map build the old 32-tile radius applies.
        assertTrue(Player.inBuildArea(null, from, Tile(3222, 3250, 0)))
        assertFalse(Player.inBuildArea(null, from, Tile(3222, 3251, 0)))
    }
}
