package gg.rsmod.plugins.content.mechanics.music

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.CacheItemProbeTool
import gg.rsmod.game.tools.importer.ModernCacheReader
import gg.rsmod.game.tools.importer.OsrsMusicImportTool
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner answer Q11 (a): the OSRS track "The Enclave" (cache 680) as music index 1016 -> song group 1010 in both production caches. */
class OsrsEnclaveMusicImportTests {
    private fun string(value: ByteArray) = String(value, 0, value.size - 1, Charsets.ISO_8859_1)

    private fun int(value: ByteArray) = value.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }

    @Test
    fun `both caches hold the OSRS song and both music enums point index 1016 at it`() {
        val song = ModernCacheReader(File(OsrsMusicImportTool.SOURCE_CACHE)).use { it.file(6, 680, 0) }!!
        listOf("../../data/cache", "../../../../file-server/cache").forEach { path ->
            val library = CacheLibrary(File(path).canonicalPath)
            try {
                assertEquals(CacheItemProbeTool.sha1(song), CacheItemProbeTool.sha1(library.data(6, 1010, 0)!!), "$path song group 1010")
                val names = OsrsMusicImportTool.decode(library.data(17, 1345 ushr 8, 1345 and 0xFF)!!)
                val groups = OsrsMusicImportTool.decode(library.data(17, 1351 ushr 8, 1351 and 0xFF)!!)
                assertEquals("The Enclave", string(names.entries.getValue(1016)), path)
                assertEquals(1010, int(groups.entries.getValue(1016)), path)
                assertEquals(names.entries.keys, groups.entries.keys, "$path: every named track has a song group")
                assertEquals(1015, names.entries.size, path)
                assertTrue(names.arraySize > 1016 && groups.arraySize > 1016, path)
            } finally {
                library.close()
            }
        }
    }

    @Test
    fun `The Enclave unlocks and plays in both Ferox regions`() {
        val yml = File("../../data/cfg/music/music_by_region.yaml").readText()
        assertTrue("the_enclave:\n  index: 1016\n  areas:\n    - region: 12344\n    - region: 12600\n" in yml.replace("\r\n", "\n"))
        assertEquals(1, Regex("""(?m)^\s+index: 1016\s*$""").findAll(yml).count(), "index 1016 used once")
    }
}
