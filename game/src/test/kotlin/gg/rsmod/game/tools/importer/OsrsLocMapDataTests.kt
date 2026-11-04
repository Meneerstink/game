package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N529/N531 (GOUDEN REGEL, whole class): every imported OSRS loc of OSRS_IMPORT_MASTER.yml whose OSRS definition has a map scene
 * or map area carries the pixel-proven 667 msi / mapelement ([OsrsMapIconTable]) in BOTH production caches, or its OSRS value is
 * a recorded SOURCE_BLOCKED one. A new import that drops its minimap picture fails here; so does a future encodeFromModern change.
 */
class OsrsLocMapDataTests {
    @Test
    fun `every imported osrs loc carries its proven minimap scene and icon in both caches`() {
        val entries = OsrsLocMapDataTool.locEntries().distinctBy { it.local }
        assertTrue(entries.size > 1000, "OSRS_IMPORT_MASTER.yml loc_ids not parsed (${entries.size})")
        val modernDefs = ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { it.files(2, 6) }
        val sources = entries.associate { it.local to ModernObjectDef.decode(it.upstream, modernDefs.getValue(it.upstream)) }
        sources.forEach { (local, src) ->
            val scene = src.mapSceneId
            val area = src.mapAreaId
            assertTrue(scene == -1 || scene in OsrsMapIconTable.SCENE_TO_MSI || scene in OsrsMapIconTable.BLOCKED_SCENES, "loc $local: OSRS map scene $scene unmapped")
            assertTrue(area == -1 || area in OsrsMapIconTable.AREA_TO_MAP_ELEMENT || area in OsrsMapIconTable.BLOCKED_AREAS, "loc $local: OSRS map area $area unmapped")
        }
        listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE).forEach { cache ->
            val library = CacheLibrary(cache)
            try {
                val locs = Rev667RegionProbeTool.locTypes(library)
                sources.forEach { (local, src) ->
                    val loc = locs.getValue(local)
                    OsrsMapIconTable.SCENE_TO_MSI[src.mapSceneId]?.let { assertEquals(it, loc.msi, "$cache loc $local '${loc.name}' msi") }
                    OsrsMapIconTable.AREA_TO_MAP_ELEMENT[src.mapAreaId]?.let { assertEquals(it, loc.mapElement, "$cache loc $local '${loc.name}' mapelement") }
                }
            } finally {
                library.close()
            }
        }
    }

    @Test
    fun `encodeFromModern writes the proven map scene and map area`() {
        val src = ModernObjectDef(31555).apply {
            name = "Cavern"
            mapSceneId = 7
            mapAreaId = 53
        }
        val bytes = Rev667LocType.encodeFromModern(src, { it }, mutableListOf())
        val decoded = Rev667LocType.decode(31555, bytes)
        assertEquals(7, decoded.msi)
        assertEquals(607, decoded.mapElement)
        val dropped = mutableListOf<String>()
        val blocked = ModernObjectDef(1).apply { mapSceneId = 101 }
        assertEquals(-1, Rev667LocType.decode(1, Rev667LocType.encodeFromModern(blocked, { it }, dropped)).msi)
        assertTrue(dropped.any { "map scene 101" in it }, "blocked scene is reported: $dropped")
    }
}
