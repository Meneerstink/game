package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N258 (owner 2026-10-23: "NA" twice in Follower Details): the pet layer's growth and hunger counters are hidden for a pet. */
class SummoningPetColumnTests {
    /** The parent layer of a rev-667 component stream (Component.decode: version, type [+ name], 2+2+2+2+2, 4 mode bytes, layer). */
    private fun layerOf(b: ByteArray): Int {
        var p = 1
        val type = b[p++].toInt() and 0xFF
        if (type and 0x80 != 0) {
            while (b[p] != 0.toByte()) p++
            p++
        }
        p += 10 + 4
        return ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)
    }

    @Test
    fun `layer 71 holds exactly the growth and hunger icons and values, and a pet hides all of them`() {
        val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        val children =
            try {
                library.index(3).archive(662)!!.fileIds().filter { id -> library.data(3, 662, id)?.let { layerOf(it) } == 71 }
            } finally {
                library.close()
            }
        assertEquals(SummoningUi.PANEL_PET_COLUMN.toList(), children.sorted())
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/SummoningUi.kt").readText()
        assertTrue("PANEL_PET_COLUMN.forEach { player.setComponentHidden(PANEL, it, pet) }" in source.substringAfter("private fun refreshPanel("))
    }
}
