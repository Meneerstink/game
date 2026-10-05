package gg.rsmod.game.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Owner 2026-10-04 (POH, priority 2): `toRotatedInteger` packs the source plane at bit 24 (the 667 client's dynamic-region word),
 * `fromRotatedHash` read it from bit 28, so every instance chunk copied its server objects and collision from source plane 0 -
 * a player-owned house (built from a style plane, default 2) had the walls of another floor than the one the client draws.
 */
class TileRotatedHashTests {
    @Test
    fun `the rotated hash keeps the source plane`() {
        for (height in 0 until Tile.TOTAL_HEIGHT_LEVELS) {
            for (rot in 0..3) {
                val source = Tile(1856, 5056, height)
                val back = Tile.fromRotatedHash(source.toRotatedInteger(rot))
                assertEquals(Tile(1856, 5056, height), back, "plane $height rot $rot")
            }
        }
    }
}
