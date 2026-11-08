package gg.rsmod.game.tools.importer

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ToaHdOverlayTests {
    private fun proposals() = JsonParser().parse(
        sequenceOf(File("tools/n601_material_proposals.json"), File("../tools/n601_material_proposals.json"))
            .first { it.isFile }.readText(),
    ).asJsonObject

    @Test
    fun approvedJsonIsWiredToOnlyTheHdRetileBatchWithExceptionsExcluded() {
        val json = proposals()
        val expected = json.getAsJsonObject("overlay_overrides").entrySet()
            .associate { it.key.toInt() to it.value.asInt }.filterKeys { it !in setOf(36, 115, 189) }
        assertEquals(65, expected.size)
        assertEquals(expected, OsrsRegionImportTool.TOA_HD_OVERLAYS)
        val batch = OsrsRegionImportTool.BATCHES.getValue("toa-map-hd")
        assertEquals(expected, batch.overlayOverrides)
        assertTrue(batch.retileTargets)
        assertNull(batch.newOverlaySlots)
        assertNull(batch.newUnderlaySlots)
        assertEquals(13454, batch.target(13454))
        assertEquals((70 shl 8) or 108, batch.target(15700))
        assertEquals(21, ToaOverlayTexturePatchTool.TARGET_NAMES.size)
        val patches = json.getAsJsonObject("reserved_overlay_texture_patches").entrySet().associate {
            val value = it.value.asJsonObject
            it.key.toInt() to ToaOverlayTexturePatchTool.Patch(value.get("texture").asInt, value.get("keep_rgb").asString.toInt(16))
        }
        assertEquals(patches, ToaOverlayTexturePatchTool.PATCHES)
        assertTrue(OsrsRegionImportTool.BATCHES.getValue("toa-map-floors").overlayOverrides.isEmpty())
    }

    @Test
    fun fixtureTilesEncodeOverridesAsUnsignedIdPlusOneAndKeepFallbacksFlat() {
        val batch = OsrsRegionImportTool.BATCHES.getValue("toa-map-hd")
        val candidates = (batch.overlayOverrides.values.toSet() + setOf(51, 31, 251)).associateWith { id ->
            Rev667OverlayDef(id * 100, if (id in setOf(51, 31, 251)) -1 else 301, true, -1)
        }
        val map = Rev667TileMap()
        val cases = batch.overlayOverrides + mapOf(36 to 51, 115 to 31, 189 to 251)
        cases.entries.forEachIndexed { index, (sourceId, targetId) ->
            val source = ModernOverlayDef(targetId * 100, -1, true, -1)
            val eligible = if (sourceId in batch.overlayOverrides) candidates else candidates.filterValues { it.texture == -1 }
            val tile = map.tiles[0][index / 64][index % 64]
            tile.overlayId = OsrsRegionImportTool.resolveOverlay(sourceId + 1, source, batch, eligible)
            tile.height = 33
            tile.flags = 2
            tile.overlayShape = 3
            tile.overlayRotation = 2
        }
        map.trailing = byteArrayOf(0, 1, 2)
        val result = Rev667TileCodec.decode(Rev667TileCodec.encode(map))
        cases.entries.forEachIndexed { index, (_, targetId) ->
            val tile = result.tiles[0][index / 64][index % 64]
            assertEquals(targetId + 1, tile.overlayId and 0xFF)
            assertEquals(33, tile.height)
            assertEquals(2, tile.flags)
            assertEquals(3, tile.overlayShape)
            assertEquals(2, tile.overlayRotation)
        }
        assertArrayEquals(map.trailing, result.trailing)
    }

    @Test
    fun missingOverrideTargetRefusesInsteadOfWritingAnInvalidFloor() {
        try {
            OsrsRegionImportTool.resolveOverlay(13, ModernOverlayDef(0, -1, true, -1),
                OsrsRegionImportTool.BATCHES.getValue("toa-map-hd"), emptyMap())
            fail("missing target should refuse")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("missing"))
        }
    }

    @Test
    fun objectPlanNamesLocModelsTargetsAndUnresolvedMaterialsWithoutInventingIds() {
        val rows = ToaObjectTexturePlanTool.objectRows(46077, 5494, setOf(18, 96, 97, 116, 999),
            proposals().getAsJsonObject("object_texture_proposals"))
        assertEquals(5, rows.size)
        assertTrue(rows.first().contains("osrsLoc=46077 osrsModel=5494 sourceTexture=18 -> 667Texture=18"))
        assertTrue(rows.drop(1).all { it.contains("667Texture=UNRESOLVED") })
        assertTrue(ToaObjectTexturePlanTool.objectRows(45828, 45814, emptySet(), proposals()).isEmpty())
        try {
            ToaObjectTexturePlanTool.main(arrayOf("apply"))
            fail("object apply is deferred")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("deferred"))
        }
    }
}
