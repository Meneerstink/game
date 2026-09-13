package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RCV-011 home minimap floors, roster-wide over every modern overlay definition: the importer's overlay match keeps
 * whether the floor has a minimap colour, picks an identical local definition whenever one exists, and sends the
 * Ferox path overlays to the local definitions that draw them.
 */
class FeroxFloorMatchTests {
    @Test
    fun `overlay match keeps minimap colour for every modern overlay`() {
        val modernOverlays = ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { it.files(2, 4) }.mapValues { ModernFloorDefs.decodeOverlay(it.value) }
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            val locals = FeroxImportTool.decodeLocalFloors(library, Rev667RegionProbeTool.OVERLAY_GROUP) { Rev667FloorCodec.decodeOverlay(it) }
            modernOverlays.forEach { (id, def) ->
                val best = locals.getValue(FeroxImportTool.bestOverlay(def, locals))
                assertEquals(def.secondaryRgb == -1, best.blendRgb == -1, "modern overlay $id minimap colour presence")
                if (locals.values.any { FeroxImportTool.overlayScore(def, it) == 0L }) {
                    assertEquals(0L, FeroxImportTool.overlayScore(def, best), "modern overlay $id has an identical local definition")
                }
            }
            val ferox = listOf(41, 54, 60, 99, 160).associateWith { FeroxImportTool.bestOverlay(modernOverlays.getValue(it), locals) }
            assertEquals(mapOf(41 to 41, 54 to 54, 60 to 60, 99 to 58, 160 to 160), ferox)
        } finally {
            library.close()
        }
    }
}
