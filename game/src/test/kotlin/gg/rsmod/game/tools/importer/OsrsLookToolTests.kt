package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [OsrsLookTool]: E1 (the plain max cape / hood drew the 2011 RuneScape cape) and B6 (owner 2026-10-03 with Eclipse moon
 * chestplate + Barrows gloves: "OSRS armour invisible at the hands/wrists" - the 667-native gloves only cover the wrist, the
 * OSRS gauntlet covers the lower forearm that OSRS bodies leave to the hands slot).
 */
class OsrsLookToolTests {
    private fun gameCache() = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())

    /** A rev-667 item stream shaped like the 667 Max cape 20767 (models, camera, recolour + retexture, options, params). */
    private fun maxCape667(): ByteArray {
        val out = ByteArrayOutputStream()
        fun u8(v: Int) = out.write(v and 0xFF)
        fun u16(op: Int, v: Int) {
            u8(op)
            u8(v ushr 8)
            u8(v)
        }
        fun str(op: Int, s: String) {
            u8(op)
            out.write(s.toByteArray(Charsets.ISO_8859_1))
            u8(0)
        }
        u16(1, 65262)
        str(2, "Max cape")
        u16(7, 65531)
        u16(8, 24)
        u16(4, 1385)
        u16(6, 948)
        u16(5, 279)
        u8(12)
        listOf(0, 0x25, 0xC4, 0x18).forEach { u8(it) }
        u8(16)
        u16(23, 65300)
        u16(25, 65322)
        str(36, "Wear")
        str(37, "Features")
        str(35, "Teleports")
        u8(40)
        u8(1)
        listOf(0xFE, 0xBE, 0xFE, 0xBE).forEach { u8(it) }
        u8(41)
        u8(1)
        listOf(0x01, 0x67, 0x01, 0x00).forEach { u8(it) }
        u8(249)
        u8(1)
        u8(1)
        listOf(0, 2, 16).forEach { u8(it) } // param 528
        out.write("Home".toByteArray(Charsets.ISO_8859_1))
        u8(0)
        u8(0)
        return out.toByteArray()
    }

    /** The OSRS Max cape 13280 look, as decoded from the pinned OSRS cache (values read 2026-10-04). */
    private fun osrsMaxCape() =
        ModernItemDef(13280).apply {
            name = "Max cape"
            inventoryModel = 29630
            zoom2d = 2232
            xan2d = 687
            yan2d = 27
            yOffset2d = -5
            maleModel0 = 29616
            femaleModel0 = 29624
            wearPos1 = 1
        }

    private val imported = mapOf(29630 to 44251, 29616 to 44252, 29624 to 44258)

    @Test
    fun `a relook replaces the meshes, camera and 667 recolours and keeps name, options, cost and params`() {
        val before = maxCape667()
        val after = OsrsLookTool.relook(before, osrsMaxCape()) { imported.getValue(it) }
        val ops = OsrsLookTool.payloads(after)
        assertEquals("44251", ops[1])
        assertEquals("44252", ops[23])
        assertEquals("44258", ops[25])
        assertEquals(listOf("2232", "687", "27", "0", "65531"), listOf(4, 5, 6, 7, 8).map { ops[it] })
        assertFalse(40 in ops, "the 667 cape's recolour table would repaint the OSRS mesh")
        assertFalse(41 in ops, "the 667 cape's retexture table would repaint the OSRS mesh")
        assertEquals(OsrsLookTool.untouched(before), OsrsLookTool.untouched(after))
        assertEquals("Max cape", ItemDefCodec.readName(after))
        assertTrue(OsrsLookTool.untouched(after).any { it.startsWith("249=") }, "params (worn teleport options) kept")
        assertEquals(after.toList(), OsrsLookTool.relook(after, osrsMaxCape()) { imported.getValue(it) }.toList(), "a second relook changes nothing")
    }

    @Test
    fun `an identical recolour table is kept, a same-size OSRS one is written exactly, another size is refused`() {
        val withSame = osrsMaxCape().apply {
            colorFind = intArrayOf(65214)
            colorReplace = intArrayOf(65214)
        }
        assertEquals("0x01febefebe", OsrsLookTool.payloads(OsrsLookTool.relook(maxCape667(), withSame) { imported.getValue(it) })[40])
        // N242: the OSRS table of the same size replaces the 667 one byte for byte (Magic potion's liquid colour).
        val different = osrsMaxCape().apply {
            colorFind = intArrayOf(668)
            colorReplace = intArrayOf(115)
        }
        assertEquals("0x01029c0073", OsrsLookTool.payloads(OsrsLookTool.relook(maxCape667(), different) { imported.getValue(it) })[40])
        val bigger = osrsMaxCape().apply {
            colorFind = intArrayOf(668, 669)
            colorReplace = intArrayOf(115, 116)
        }
        assertFailsWith<IllegalStateException> { OsrsLookTool.relook(maxCape667(), bigger) { imported.getValue(it) } }
    }

    @Test
    fun `hide flags follow the OSRS wear positions and only the named items change`() {
        val hood = ModernItemDef(13281).apply { wearPos2 = 8 }
        assertEquals(mapOf("remove_head" to true, "remove_beard" to false, "remove_arms" to false), OsrsLookTool.hideFlags(hood))
        val lines =
            listOf(
                "- id: 20768", "  name: \"Max hood\"", "  equipment:", "    remove_head: false", "    remove_beard: false", "    remove_arms: false",
                "- id: 20769", "  equipment:", "    remove_head: false",
            )
        val (out, changed) = OsrsLookTool.rewriteFlags(lines, mapOf(20768 to OsrsLookTool.hideFlags(hood)))
        assertEquals(listOf("20768 remove_head -> true"), changed)
        assertEquals("    remove_head: true", out[3])
        assertEquals("    remove_head: false", out[8])
    }

    @Test
    fun `the look list names each 667 item once and never an imported OSRS item`() {
        assertEquals(OsrsLookTool.LOOKS.size, OsrsLookTool.LOOKS.map { it.local }.toSet().size)
        val imported = OsrsFxImportTool.importedItems(File(OsrsItemImportTool.ASSET_MAP)).values.toSet()
        assertTrue(OsrsLookTool.LOOKS.none { it.local in imported }, "the importer owns the look of imported items")
        assertTrue(OsrsLookTool.LOOKS.any { it.local == 20767 } && OsrsLookTool.LOOKS.any { it.local == 20768 }, "E1")
        // Owner 2026-10-06 "Original 667 look": the 667 gloves keep their own look.
        assertTrue(OsrsLookTool.LOOKS.none { it.local in RESTORED_667 }, "Barrows gloves / Void knight gloves / Regen bracelet stay 667")
    }

    /**
     * Guard after `OsrsLookTool --apply` (cache state): every listed item draws exactly its OSRS look - the tool has nothing
     * left to do.
     */
    @Test
    fun `every listed 667 item draws its OSRS look in the game cache`() {
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val library = gameCache()
        try {
            val itemFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
            val mapping = ImportBatchOrchestrator.readExistingMapping(File(OsrsItemImportTool.ASSET_MAP)).modelLocalIdByMappingKey
            val missing = mutableListOf<String>()
            OsrsLookTool.LOOKS.forEach { look ->
                val def = ModernItemDefDecoder.decode(look.upstream, itemFiles.getValue(look.upstream))
                val current = CacheItemProbeTool.itemData(library, look.local)!!
                val relooked =
                    runCatching {
                        OsrsLookTool.relook(current, def) { model ->
                            mapping[ModelIdAllocator.mappingKey("upstream_model:$model", OsrsLookTool.MODEL_ROLE)] ?: error("upstream model $model not imported")
                        }
                    }.getOrElse { missing += "${look.local} ${look.localName}: ${it.message}"; return@forEach }
                if (!relooked.contentEquals(current)) missing += "${look.local} ${look.localName} still draws its 667 look"
            }
            assertTrue(missing.isEmpty(), "run OsrsLookTool --apply: $missing")
        } finally {
            library.close()
            reader.close()
        }
    }

    /**
     * Owner 2026-10-06 "Original 667 look" (cache state): Barrows gloves, Void knight gloves and Regen bracelet hold their original
     * 667 item definitions again - byte for byte the files OsrsLookTool replaced (journal tx-20261003-235150).
     */
    @Test
    fun `Barrows gloves, Void knight gloves and Regen bracelet hold their original 667 definition`() {
        val journal = Paths.get("..", "..", "..", "import-journal", "tx-20261003-235150").toFile()
        val library = gameCache()
        try {
            val changed =
                RESTORED_667.filter { id ->
                    val orig = File(journal, "C_RSPS_game_game_data_cache__idx19_grp${id shr 8}_file${id and 0xFF}.orig").readBytes()
                    !orig.contentEquals(CacheItemProbeTool.itemData(library, id)!!)
                }
            assertTrue(changed.isEmpty(), "not the original 667 definition (run CacheJournalRestoreTool): $changed")
        } finally {
            library.close()
        }
    }

    private companion object {
        val RESTORED_667 = setOf(7462, 8842, 11133)
    }
}
