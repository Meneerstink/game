package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RCV-011 home minimap, roster-wide: every imported Ferox loc (OSRS_IMPORT_MASTER.yml) whose OSRS definition has a
 * map scene or map area carries the pixel-proven 667 msi / mapelement in BOTH production caches, or its OSRS value is a
 * recorded SOURCE_BLOCKED one. A newly imported Ferox loc with an unmapped value fails here.
 */
class FeroxMinimapTests {
    @Test
    fun `every imported ferox loc carries its proven minimap scene and icon in both caches`() {
        val entries = FeroxMapDataProbeTool.feroxEntries().filter { it.mode == "IMPORT" }
        val modernDefs = ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { it.files(2, 6) }
        val expectedMsi = HashMap<Int, Int>()
        val expectedElement = HashMap<Int, Int>()
        entries.forEach { e ->
            val modern = ModernObjectDef.decode(e.upstream, modernDefs.getValue(e.upstream))
            val scene = modern.mapSceneId
            val area = modern.mapAreaId
            assertTrue(scene == -1 || scene in FeroxMinimapTool.SCENE_TO_MSI || scene in FeroxMinimapTool.BLOCKED_SCENES, "loc ${e.local}: OSRS map scene $scene unmapped")
            assertTrue(area == -1 || area in FeroxMinimapTool.AREA_TO_MAP_ELEMENT || area in FeroxMinimapTool.BLOCKED_AREAS, "loc ${e.local}: OSRS map area $area unmapped")
            FeroxMinimapTool.SCENE_TO_MSI[scene]?.let { expectedMsi[e.local] = it }
            FeroxMinimapTool.AREA_TO_MAP_ELEMENT[area]?.let { expectedElement[e.local] = it }
        }
        assertEquals(expectedMsi, FeroxMinimapTool.MSI)
        assertEquals(expectedElement, FeroxMinimapTool.MAP_ELEMENT)

        listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE).forEach { cache ->
            val library = CacheLibrary(cache)
            try {
                val locs = Rev667RegionProbeTool.locTypes(library)
                entries.forEach { e ->
                    val loc = locs.getValue(e.local)
                    assertEquals(expectedMsi[e.local] ?: -1, loc.msi, "$cache loc ${e.local} msi")
                    // The Ferox "Death's domain" carries the Death's Office icon DeathsOfficeMapImportTool gave every entrance (2026-09-26).
                    val element = if (e.local == DeathsOfficeMapImportTool.FEROX_ENTRANCE) DeathsOfficeMapImportTool.OFFICE_MAP_ELEMENT else expectedElement[e.local] ?: -1
                    assertEquals(element, loc.mapElement, "$cache loc ${e.local} mapelement")
                }
            } finally {
                library.close()
            }
        }
    }
}
