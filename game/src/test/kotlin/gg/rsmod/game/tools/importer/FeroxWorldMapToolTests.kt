package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N228 (owner 2026-10-23): Ferox Enclave on the world map. The record encoder must rebuild untouched squares byte for byte from
 * their landscape - then the rebuilt Ferox squares follow Jagex's own rules.
 */
class FeroxWorldMapToolTests {
    private val cache = Paths.get("..", "data", "cache").toFile()

    @Test
    fun `untouched map squares are rebuilt byte for byte`() {
        val library = CacheLibrary(cache.toString())
        try {
            val keys = Rev667RegionProbeTool.loadKeys(File("..", "data/xteas/xteas.json"))
            val def = FeroxWorldMapTool.objectDefs(library)
            val group = library.index(FeroxWorldMapTool.WORLD_MAP_INDEX).archive("main")!!
            val bytes = library.data(FeroxWorldMapTool.WORLD_MAP_INDEX, group.id, group.file("area")!!.id)!!
            val area = FeroxWorldMapTool.parse(bytes)
            listOf(48 to 55, 49 to 55, 50 to 55).forEach { (x, z) ->
                val record = area.records.single { it.square && it.x == x && it.z == z }
                val (tiles, locs) = FeroxWorldMapTool.landscape(library, keys, x, z)
                val rebuilt = FeroxWorldMapTool.encodeSquare(x, z, tiles, locs, area, def)
                assertEquals(bytes.copyOfRange(record.start, record.end).toList(), rebuilt.toList(), "square ${x}_$z")
            }
        } finally {
            library.close()
        }
    }

    @Test
    fun `the Ferox squares match the current landscape and carry the label`() {
        val library = CacheLibrary(cache.toString())
        try {
            val keys = Rev667RegionProbeTool.loadKeys(File("..", "data/xteas/xteas.json"))
            val def = FeroxWorldMapTool.objectDefs(library)
            val worldMap = library.index(FeroxWorldMapTool.WORLD_MAP_INDEX)
            val group = worldMap.archive("main")!!
            val bytes = library.data(FeroxWorldMapTool.WORLD_MAP_INDEX, group.id, group.file("area")!!.id)!!
            val area = FeroxWorldMapTool.parse(bytes)
            FeroxWorldMapTool.SQUARES.forEach { (x, z) ->
                val record = area.records.single { it.square && it.x == x && it.z == z }
                val (tiles, locs) = FeroxWorldMapTool.landscape(library, keys, x, z)
                assertTrue(bytes.copyOfRange(record.start, record.end).contentEquals(FeroxWorldMapTool.encodeSquare(x, z, tiles, locs, area, def)), "square ${x}_$z is the current landscape")
            }
            assertTrue(library.data(FeroxWorldMapTool.CONFIG_INDEX, FeroxWorldMapTool.MAP_ELEMENT_GROUP, FeroxWorldMapTool.LABEL_ELEMENT)!!.contentEquals(FeroxWorldMapTool.labelElement()))
            val statics = worldMap.archive("main_staticelements")!!
            val entry = MansionMapIconTool.staticEntry(FeroxWorldMapTool.LABEL_ELEMENT, FeroxWorldMapTool.LABEL_X, FeroxWorldMapTool.LABEL_Z, 0)
            assertTrue(statics.fileIds().any { library.data(FeroxWorldMapTool.WORLD_MAP_INDEX, statics.id, it)?.contentEquals(entry) == true }, "label entry")
        } finally {
            library.close()
        }
    }
}
