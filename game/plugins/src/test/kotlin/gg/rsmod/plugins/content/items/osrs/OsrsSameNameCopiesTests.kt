package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.tools.importer.ModernCacheReader
import gg.rsmod.game.tools.importer.ModernItemDefDecoder
import gg.rsmod.game.tools.importer.OsrsItemImportTool
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner answer 2026-09-14: same-name duplicate copies map to the main item and are never imported (whole copy roster). */
class OsrsSameNameCopiesTests {
    private val spelling =
        mapOf(
            "3rd Age robe top" to "Third-age robe top", "3rd Age robe" to "Third-age robe", "3rd Age mage hat" to "Third-age mage hat",
            "Ahrim's robetop" to "Ahrim's robe top", "Ahrim's robeskirt" to "Ahrim's robe skirt", "Mage's book" to "Mages' book",
            "Seers ring (i)" to "Seers' ring (i)",
        )

    private fun localNames(): Map<Int, String> {
        val lines = File("../../data/cfg/items.yml").readLines()
        val out = HashMap<Int, String>()
        lines.forEachIndexed { i, line ->
            Regex("""^- id: (\d+)$""").find(line)?.let { m ->
                out[m.groupValues[1].toInt()] = lines[i + 1].trim().removePrefix("name: \"").removeSuffix("\"")
            }
        }
        return out
    }

    @Test
    fun `every copy has the OSRS name of its local main item`() {
        val local = localNames()
        val offenders = mutableListOf<String>()
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
            val files = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
            fun osrsName(id: Int) = ModernItemDefDecoder.decode(id, files.getValue(id)).name
            OsrsItemImportTool.SAME_NAME_COPIES.forEach { (copy, main) ->
                val expected = osrsName(copy).let { spelling[it] ?: it }
                if (local[main] != expected) offenders += "copy $copy '${osrsName(copy)}' -> $main '${local[main]}'"
            }
            OsrsItemImportTool.SAME_NAME_COPIES_WITHOUT_MAIN.keys.forEach { copy ->
                val name = osrsName(copy)
                if (local.values.any { it.equals(name, ignoreCase = true) }) offenders += "copy $copy '$name' has a main item after all"
            }
        }
        assertEquals(emptyList(), offenders)
    }

    @Test
    fun `no import batch contains a same-name copy`() {
        val copies = OsrsItemImportTool.SAME_NAME_COPIES.keys + OsrsItemImportTool.SAME_NAME_COPIES_WITHOUT_MAIN.keys
        val offenders = OsrsItemImportTool.BATCHES.flatMap { (batch, specs) -> specs.filter { it.upstreamId in copies }.map { "$batch: ${it.upstreamId}" } }
        assertEquals(emptyList(), offenders)
        assertTrue(28310 !in copies && 28313 !in copies, "the real Venator / Magus rings are main items")
    }
}
