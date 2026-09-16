package gg.rsmod.plugins.api.ext

import gg.rsmod.game.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Deadman PvP guards plan (2026-09-16) regression coverage for the [Tile.getWildernessLevel]
 * root-cause fix: the previous formula had no upper bound on its `z > 6400` underground branch
 * and no final clamp, so a tile in a listed region but outside the real height ranges could
 * return a negative/garbage level (the owner-reported "-2 or other nonsense values"). Region/x/z
 * combinations below are derived from the real `Tile.regionId` formula
 * (`((x shr 6) shl 8) or (z shr 6)`) against actual entries in [wildernessRegionIds], not
 * invented coordinates - every region id asserted against here is a real member of that list.
 *
 * Census finding: every currently listed wilderness region id has a `regionId` low byte (the z
 * component) of either 55-61 (surface, real z 3520-3967) or 157-158 (underground, real z
 * 10048-10175) - none fall in the old formula's unbounded 3968-9919 gap, so the missing-client-
 * push bug fixed in `wilderness.plugin.kts` (nothing ever called `setComponentText` for the
 * level text) is the confirmed primary cause of the reported symptom; this formula fix is
 * additional hardening against any future/edge tile, not a reproduction of the exact live report.
 */
class TileExtWildernessLevelTests {
    @Test
    fun `surface floor at z=3524 is not in the Wilderness`() {
        val tile = Tile(3040, 3524, 0)
        assertEquals(12087, tile.regionId, "sanity check: must be a real listed wilderness region")
        assertEquals(0, tile.getWildernessLevel())
    }

    @Test
    fun `surface level 1 starts at z=3525`() {
        val tile = Tile(3040, 3525, 0)
        assertEquals(12087, tile.regionId)
        assertEquals(1, tile.getWildernessLevel())
    }

    @Test
    fun `surface level scales by 8 tiles per level deeper into the Wilderness`() {
        val tile = Tile(3040, 3680, 0)
        assertEquals(12089, tile.regionId)
        assertEquals(21, tile.getWildernessLevel())
    }

    @Test
    fun `underground copy at the low end of its real range returns a positive level`() {
        val tile = Tile(3040, 10112, 0)
        assertEquals(12190, tile.regionId)
        assertEquals(25, tile.getWildernessLevel())
    }

    @Test
    fun `underground copy at the high end of its real range returns a positive level`() {
        val tile = Tile(3040, 10175, 0)
        assertEquals(12190, tile.regionId)
        assertEquals(32, tile.getWildernessLevel())
    }

    @Test
    fun `a second underground region strip also resolves correctly`() {
        val tile = Tile(3100, 10048, 0)
        assertEquals(12445, tile.regionId)
        assertEquals(17, tile.getWildernessLevel())
    }

    @Test
    fun `a tile outside every listed wilderness region is never in the Wilderness`() {
        val tile = Tile(3200, 3300, 0)
        assertEquals(false, wildernessRegionIds.contains(tile.regionId), "sanity check: must not be a listed region")
        assertEquals(0, tile.getWildernessLevel())
    }

    @Test
    fun `the result is always clamped to a sane non-negative range`() {
        val tiles =
            listOf(
                Tile(3040, 3524, 0),
                Tile(3040, 3525, 0),
                Tile(3040, 3967, 0),
                Tile(3040, 10112, 0),
                Tile(3040, 10175, 0),
            )
        tiles.forEach { tile ->
            val level = tile.getWildernessLevel()
            assertEquals(level.coerceIn(0, 60), level, "level for $tile must already be within the clamped range")
        }
    }
}
