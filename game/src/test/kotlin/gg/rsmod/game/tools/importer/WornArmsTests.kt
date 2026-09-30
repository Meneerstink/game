package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-09-30: the arms of the player (Eclipse / Masori / Crystal bodies) and the Quest Tutor's body were invisible.
 * Both were a worn outfit missing a part: the arms kit hidden by a body that brings no arms model of its own, and an npc
 * dressed with only the first wear model of an item whose torso is its second.
 */
class WornArmsTests {
    @Test
    fun `every imported OSRS item hides the arms kit only when it brings its own arms model`() {
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val wanted =
            try {
                val itemFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
                OsrsFxImportTool.importedItems(File(OsrsItemImportTool.ASSET_MAP)).mapNotNull { (upstream, local) ->
                    itemFiles[upstream]?.let { local to OsrsItemImportTool.removesArms(ModernItemDefDecoder.decode(upstream, it)) }
                }.toMap()
            } finally {
                reader.close()
            }
        val lines = File(OsrsArmsKitRepairTool.ITEMS_YML).readLines()
        val (_, stillWrong) = OsrsArmsKitRepairTool.rewrite(lines, wanted)
        assertTrue(stillWrong.isEmpty(), "items.yml remove_arms differs from the OSRS arms-model rule for $stillWrong")
        // The rule itself: Eclipse moon chestplate (no arms model) keeps the arms, Bandos chestplate (arms model 28826) hides them.
        assertEquals(false, wanted[23102])
        assertTrue(wanted.values.any { it }, "no imported body hides the arms any more")
    }

    @Test
    fun `the repair only touches remove_arms of the named items`() {
        val lines = listOf("- id: 1", "  equipment:", "    remove_arms: true", "- id: 2", "  equipment:", "    remove_arms: true")
        val (out, changed) = OsrsArmsKitRepairTool.rewrite(lines, mapOf(2 to false))
        assertEquals(listOf(2), changed)
        assertEquals(lines.take(5) + "    remove_arms: false", out)
    }

    @Test
    fun `the Quest Tutor wears every wear model of every item it is dressed in`() {
        val library = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
        try {
            QuestGuideNpcTool.WORN_ITEMS.forEach { item ->
                val models =
                    ItemDefCodec.describeOpcodes(library.data(19, item ushr 8, item and 0xFF)!!)
                        .map { it.split('=') }
                        .filter { it[0].toInt() in setOf(23, 24, 78) }
                        .map { it[1].toInt() }
                assertTrue(models.isNotEmpty(), "item $item has no male wear model")
                models.forEach { assertTrue(it in QuestGuideNpcTool.MODELS, "item $item wear model $it is missing from the Quest Tutor") }
            }
            assertTrue(151 !in QuestGuideNpcTool.MODELS, "the Mystic robe top hides the arms kit (remove_arms), as on a player")
        } finally {
            library.close()
        }
    }
}
