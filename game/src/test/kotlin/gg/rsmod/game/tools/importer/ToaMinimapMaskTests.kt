package gg.rsmod.game.tools.importer

import gg.rsmod.game.RspsPaths
import java.io.File
import kotlin.test.*

class ToaMinimapMaskTests {
    @Test fun `all pinned secondary masks match the real source and survive every ToA reimport route`() {
        ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache"))).use { reader ->
            val definitions = mutableMapOf<Int, ModernOverlayDef>()
            fun overlay(id: Int) = definitions.getOrPut(id) { ModernFloorDefs.decodeOverlay(requireNotNull(reader.file(2, 4, id))) }
            for ((id, mask) in ToaMinimapMaskTool.MASKS) {
                val source = overlay(id)
                val candidates = mapOf(mask.local to Rev667OverlayDef(0xff00ff, -1, false, mask.colour))
                for ((_, batch) in OsrsRegionImportTool.BATCHES.filterKeys { it.startsWith("toa-") }) {
                    assertEquals(mask.local + 1, OsrsRegionImportTool.resolveOverlay(id + 1, source, batch, candidates))
                }
                assertNull(ToaMinimapMaskTool.local(id + 1, source, emptyList()))
                assertNull(ToaMinimapMaskTool.local(id + 1, source, listOf(13472)))
                assertFailsWith<IllegalStateException> {
                    ToaMinimapMaskTool.local(id + 1, ModernOverlayDef(0xff00ff, -1, false, mask.colour xor 1), listOf(15696))
                }
                assertFailsWith<IllegalStateException> {
                    OsrsRegionImportTool.resolveOverlay(id + 1, source, OsrsRegionImportTool.BATCHES.getValue("toa-map-hd"), emptyMap())
                }
            }
            val used = ToaMinimapMaskTool.TARGETS.keys.flatMap { square ->
                ModernRegion.decodeTiles(requireNotNull(reader.file(5, square, 0))).flatMap { plane ->
                    plane.flatMap { row -> row.map { it.overlayId - 1 } }
                }
            }.filter { id ->
                if (id < 0) false else overlay(id).let {
                    it.rgb == 0xff00ff && it.texture == -1 && it.secondaryRgb != -1
                }
            }.toSet()
            assertEquals(used, ToaMinimapMaskTool.MASKS.keys, "Every used secondary-colour mask is covered")
        }
    }

    @Test fun `definitions preserve native transparent rendering fields and only add the source minimap colour`() {
        val template = byteArrayOf(1, 0xff.toByte(), 0, 0xff.toByte(), 5, 11, 127, 0)
        for (mask in ToaMinimapMaskTool.MASKS.values) {
            val bytes = ToaMinimapMaskTool.definition(template, mask.colour)
            assertContentEquals(template.copyOf(template.size - 1), bytes.copyOf(template.size - 1))
            assertContentEquals(byteArrayOf(7, (mask.colour shr 16).toByte(), (mask.colour shr 8).toByte(), mask.colour.toByte(), 0),
                bytes.copyOfRange(template.size - 1, bytes.size))
            ToaMinimapMaskTool.validateNative(mask.local, Rev667FloorCodec.decodeOverlay(bytes))
        }
        assertContentEquals(byteArrayOf(1, 0xff.toByte(), 0, 0xff.toByte(), 5, 11, 127, 0), template)
        assertFailsWith<IllegalStateException> { ToaMinimapMaskTool.definition(byteArrayOf(1, 0, 0, 0, 0), 123) }
    }

    @Test fun `all source shapes rotations and planes preserve every unrelated terrain byte and repeat safely`() {
        val source = Array(4) { Array(64) { Array(64) { ModernTile() } } }
        val map = Rev667TileMap()
        map.trailing = byteArrayOf(0, 1, 2, 127)
        for ((index, id) in ToaMinimapMaskTool.MASKS.keys.withIndex()) for (p in 0..3) for (shape in 0..11) for (rot in 0..3) {
            val x = index * 4 + shape / 3
            val z = (shape % 3) * 4 + rot
            source[p][x][z].apply { overlayId = id + 1; overlayPath = shape; overlayRotation = rot; height = 30 }
            map.tiles[p][x][z].apply { height = 30; underlayId = 55; flags = 2 or 8 }
        }
        map.tiles[0][63][63].apply { overlayId = 42; overlayShape = 7; overlayRotation = 2; underlayId = 55 }
        val bytes = Rev667TileCodec.encode(map)
        val (after, count) = ToaMinimapMaskTool.restore(bytes, source)
        assertEquals(12 * 4 * 12 * 4, count)
        for (p in 0..3) for (x in 0..63) for (z in 0..63) {
            val src = source[p][x][z]
            val mask = ToaMinimapMaskTool.MASKS[src.overlayId - 1] ?: continue
            map.tiles[p][x][z].apply { overlayId = mask.local + 1; overlayShape = src.overlayPath; overlayRotation = src.overlayRotation }
        }
        assertContentEquals(Rev667TileCodec.encode(map), after)
        assertContentEquals(after, ToaMinimapMaskTool.restore(after, source).first)
        assertEquals(0, ToaMinimapMaskTool.restore(after, source).second)
    }

    @Test fun `occupied slots outside exact restored source tiles refuse instead of damaging other areas`() {
        val mask = ToaMinimapMaskTool.MASKS.getValue(123)
        val src = ModernTile().apply { overlayId = 124; overlayPath = 7; overlayRotation = 2; height = 30 }
        val dst = Rev667Tile().apply { overlayId = mask.local + 1; overlayShape = 7; overlayRotation = 2; height = 30 }
        ToaMinimapMaskTool.validateUse(mask.local + 1, src, dst, true)
        ToaMinimapMaskTool.validateUse(64, null, dst, false) // Existing native63 is used by unrelated maps, never rewritten.
        assertFailsWith<IllegalStateException> { ToaMinimapMaskTool.validateUse(mask.local + 1, null, dst, true) }
        assertFailsWith<IllegalStateException> { ToaMinimapMaskTool.validateUse(mask.local + 1, src, dst, false) }
        src.overlayId = 73
        assertFailsWith<IllegalStateException> { ToaMinimapMaskTool.validateUse(mask.local + 1, src, dst, true) }
        src.overlayId = 124; dst.overlayRotation = 1
        assertFailsWith<IllegalStateException> { ToaMinimapMaskTool.validateUse(mask.local + 1, src, dst, true) }
        dst.overlayRotation = 2; dst.height = 29
        assertFailsWith<IllegalStateException> { ToaMinimapMaskTool.validateUse(mask.local + 1, src, dst, true) }
        val source = Array(4) { Array(64) { Array(64) { ModernTile() } } }
        source[0][0][0] = src
        val map = Rev667TileMap()
        map.tiles[0][0][0].apply { height = 30; overlayId = 251 }
        assertFailsWith<IllegalStateException> { ToaMinimapMaskTool.restore(Rev667TileCodec.encode(map), source) }
        map.tiles[0][0][0].apply { height = 29; overlayId = 0 }
        assertFailsWith<IllegalStateException> { ToaMinimapMaskTool.restore(Rev667TileCodec.encode(map), source) }
    }
}
