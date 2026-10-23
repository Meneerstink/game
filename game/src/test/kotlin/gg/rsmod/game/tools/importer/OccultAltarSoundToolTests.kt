package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

/** N234 (owner 2026-10-23, answer 1 B): the POH Altar of the Occult hums softly, like the Runecrafting altar, in every variant. */
class OccultAltarSoundToolTests {
    private val caches = listOf(Paths.get("..", "data", "cache").toFile(), Paths.get("..", "..", "..", "file-server", "cache").toFile())

    @Test
    fun `every Altar of the Occult variant plays the Runecrafting altar hum in both caches`() {
        caches.forEach { cache ->
            val library = CacheLibrary(cache.toString())
            try {
                val runecrafting = OccultAltarSoundTool.decode(26847, library.data(OccultAltarSoundTool.LOC_INDEX, 26847 ushr 8, 26847 and 0xFF)!!)
                assertEquals(OccultAltarSoundTool.HUM to OccultAltarSoundTool.RANGE, runecrafting.ambientSound to runecrafting.ambientSoundRange, "the source of the hum")
                OccultAltarSoundTool.ALTARS.forEach { id ->
                    val def = OccultAltarSoundTool.decode(id, library.data(OccultAltarSoundTool.LOC_INDEX, id ushr 8, id and 0xFF)!!)
                    assertEquals(OccultAltarSoundTool.HUM to OccultAltarSoundTool.RANGE, def.ambientSound to def.ambientSoundRange, "$cache loc $id")
                }
            } finally {
                library.close()
            }
        }
    }
}
