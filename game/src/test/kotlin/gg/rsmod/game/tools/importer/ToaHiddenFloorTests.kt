package gg.rsmod.game.tools.importer

import gg.rsmod.game.RspsPaths
import java.io.File
import kotlin.test.*

class ToaHiddenFloorTests {
    @Test fun `restore preserves partial mask shapes every other field and repeat applies`() {
        val source = Array(4) { Array(64) { Array(64) { ModernTile() } } }
        val map = Rev667TileMap()
        map.trailing = byteArrayOf(0, 1, 2, 127)
        for (p in 0..3) for (shape in 0..11) for (rot in 0..3) {
            source[p][shape][rot].apply { overlayId = 42; overlayPath = shape; overlayRotation = rot; height = 30 }
            map.tiles[p][shape][rot].apply { height = 30; underlayId = 55; flags = 2 or 8 }
        }
        source[0][63][63].overlayId = 124 // Coloured/minimap mask is a different case.
        map.tiles[0][63][63].apply { underlayId = 55; overlayId = 251; overlayShape = 7; overlayRotation = 2 }
        val bytes = Rev667TileCodec.encode(map)
        val (after, counts) = ToaHiddenFloorTool.restore(bytes, source)
        assertContentEquals(intArrayOf(48, 48, 48, 48), counts)
        for (p in 0..3) for (shape in 0..11) for (rot in 0..3) map.tiles[p][shape][rot].apply {
            overlayId = 42; overlayShape = shape; overlayRotation = rot
        }
        assertContentEquals(Rev667TileCodec.encode(map), after, "Only source masks and their source shape/rotation change")
        val repeated = ToaHiddenFloorTool.restore(after, source)
        assertContentEquals(after, repeated.first)
        assertContentEquals(IntArray(4), repeated.second)
    }

    @Test fun `conflicting overlay and changed height are rejected without changing input`() {
        val source = Array(4) { Array(64) { Array(64) { ModernTile() } } }
        source[0][0][0].overlayId = 42
        val map = Rev667TileMap()
        map.tiles[0][0][0].overlayId = 251
        val bytes = Rev667TileCodec.encode(map)
        assertFailsWith<IllegalStateException> { ToaHiddenFloorTool.restore(bytes, source) }
        assertContentEquals(bytes, Rev667TileCodec.encode(map))
        map.tiles[0][0][0].apply { overlayId = 0; height = 2 }
        assertFailsWith<IllegalStateException> { ToaHiddenFloorTool.restore(Rev667TileCodec.encode(map), source) }
    }

    @Test fun `only ToA source41 without secondary colour selects the existing native mask on reimport`() {
        val source = ModernOverlayDef(0xFF00FF, -1, false, -1)
        val native = Rev667OverlayDef(0xFF00FF, -1, false, -1)
        for ((_, batch) in OsrsRegionImportTool.BATCHES.filterKeys { it.startsWith("toa-") }) {
            assertTrue(ToaHiddenFloorTool.preserveMask(42, source, batch.squares))
            assertEquals(42, OsrsRegionImportTool.resolveOverlay(42, source, batch, mapOf(41 to native)))
        }
        assertFalse(ToaHiddenFloorTool.preserveMask(42, source, emptyList()))
        assertFalse(ToaHiddenFloorTool.preserveMask(42, source, listOf(13472)))
        assertFalse(ToaHiddenFloorTool.preserveMask(124, source, listOf(15696)))
        assertFalse(ToaHiddenFloorTool.preserveMask(42, ModernOverlayDef(0xFF00FF, -1, false, 123), listOf(15696)))
        assertFailsWith<IllegalStateException> { ToaHiddenFloorTool.validateNative(Rev667OverlayDef(0xFF00FF, -1, false, 123)) }
    }

    @Test fun `real source mask definition and its presence across all target maps stay explicit`() {
        ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache"))).use { source ->
            val def = ModernFloorDefs.decodeOverlay(requireNotNull(source.file(2, 4, 41)))
            assertTrue(ToaHiddenFloorTool.preserveMask(42, def, ToaHiddenFloorTool.TARGETS.keys.toList()))
            for (square in ToaHiddenFloorTool.TARGETS.keys) {
                val tiles = ModernRegion.decodeTiles(requireNotNull(source.file(5, square, 0)))
                val hasMask = tiles.any { plane -> plane.any { row -> row.any { it.overlayId == 42 } } }
                assertEquals(square !in setOf(14674, 15700), hasMask, "$square source41 census changed")
            }
        }
    }
}
