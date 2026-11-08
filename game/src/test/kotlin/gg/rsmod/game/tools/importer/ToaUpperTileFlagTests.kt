package gg.rsmod.game.tools.importer

import org.junit.Assert.*
import org.junit.Test

class ToaUpperTileFlagTests {
    @Test fun scopesKeepLobbyAndNecropolisInPlaceAndMoveOnlyRaidSquares() {
        assertEquals("m52_142", ToaUpperTileFlagTool.targets("lobby").getValue(13454))
        val exterior = ToaUpperTileFlagTool.targets("necropolis")
        assertEquals(8, exterior.size)
        exterior.forEach { (square, name) -> assertEquals("m${square shr 8}_${square and 255}", name) }
        assertEquals("m64_108", ToaUpperTileFlagTool.targets("raid").getValue(14164))
        assertEquals(21, ToaUpperTileFlagTool.targets("all").size)
        try { ToaUpperTileFlagTool.targets("typo"); fail("unknown scope") } catch (_: IllegalStateException) { }
    }

    @Test fun restoresAllUpperPlanesWithoutChangingPlane0OtherBitsOrAnyHeightMaterialAndEnvironmentByte() {
        val original = Rev667TileMap()
        for (p in 0..3) for (x in 0..63) for (z in 0..63) original.tiles[p][x][z].apply {
            flags = 2 or (if (x % 2 == 0) 4 else 0)
            height = (x + z) % 256
            overlayId = 251; overlayRotation = 3; overlayShape = 4; underlayId = 52
        }
        original.tiles[2][0][0].flags = 8 // Already correct; do not count twice or clear source-absent bit8.
        original.trailing = byteArrayOf(0, 1, 2, 127)
        val bytes = Rev667TileCodec.encode(original)
        val settings: (Int, Int, Int) -> Int = { _, x, _ -> if (x < 2) 8 else 0 }
        val (patched, counts) = ToaUpperTileFlagTool.restore(bytes, settings)
        assertArrayEquals(intArrayOf(0, 128, 127, 128), counts)
        val result = Rev667TileCodec.decode(patched)
        for (p in 0..3) for (x in 0..63) for (z in 0..63) {
            val expected = Rev667Tile().apply { copyFrom(original.tiles[p][x][z]) }
            if (p > 0 && x < 2) expected.flags = expected.flags or 8
            val actual = result.tiles[p][x][z]
            assertEquals(expected.flags, actual.flags)
            // Everything except the intended flag must encode identically too.
            original.tiles[p][x][z].flags = expected.flags
        }
        assertArrayEquals(Rev667TileCodec.encode(original), patched)
        assertArrayEquals(patched, ToaUpperTileFlagTool.restore(patched, settings).first)
        assertArrayEquals(IntArray(4), ToaUpperTileFlagTool.restore(patched, settings).second)
    }
}
