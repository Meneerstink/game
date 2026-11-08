package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ToaOverlayTexturePatchTests {
    @get:Rule val scratch = TemporaryFolder()
    private val patch = ToaOverlayTexturePatchTool.Patch(301, 0x993333)
    // Every currently supported non-texture opcode, deliberately in noncanonical order.
    private val fields = byteArrayOf(1, 0x99.toByte(), 0x33, 0x33, 5, 7, 1, 2, 3, 8, 9, 1, 2, 10, 11, 7, 12, 13, 3, 2, 1, 14, 6, 16, 9)

    @Test
    fun patchesOnlyTextureBytesAndIsIdempotentForPlainNarrowAndWideDefinitions() {
        listOf(byteArrayOf(), byteArrayOf(2, 18), byteArrayOf(3, 1, 44), byteArrayOf(2, 18, 3, 1, 44)).forEach { texture ->
            val result = ToaOverlayTexturePatchTool.patchOverlay(fields + texture + byteArrayOf(0), patch)
            assertArrayEquals(fields + byteArrayOf(3, 1, 45, 0), result)
            assertArrayEquals(result, ToaOverlayTexturePatchTool.patchOverlay(result, patch))
            val def = Rev667FloorCodec.decodeOverlay(result)
            assertEquals(301, def.texture)
            assertEquals(patch.keepRgb, def.rgb)
            assertFalse(def.occludes)
            assertEquals(0x010203, def.blendRgb)
        }
    }

    @Test
    fun rejectsWrongRgbMalformedOpcodesAndTrailingData() {
        listOf(byteArrayOf(1, 1, 2, 3, 0), fields + byteArrayOf(17, 0), fields + byteArrayOf(0, 1),
            fields + byteArrayOf(3, 1), fields).forEach { bytes ->
            try {
                ToaOverlayTexturePatchTool.patchOverlay(bytes, patch)
                fail("invalid overlay accepted")
            } catch (expected: RuntimeException) { /* refuses before any transaction */ }
        }
    }

    @Test
    fun censusRefusesSignedHighOverlayOnEveryOutsidePlaneAndUnderwater() {
        for (plane in 0 until 4) for (name in listOf("m1_1", "um1_1")) {
            val map = Rev667TileMap()
            map.tiles[plane][63][63].overlayId = 251.toByte().toInt() // config slot 250, signed tile byte
            val bytes = Rev667TileCodec.encode(map)
            assertEquals(1, ToaOverlayTexturePatchTool.census(sequenceOf("m52_142" to bytes), setOf(250)))
            try {
                ToaOverlayTexturePatchTool.census(sequenceOf(name to bytes), setOf(250))
                fail("outside use accepted")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message!!.contains("overlay 250"))
                assertTrue(expected.message!!.contains("plane=$plane"))
            }
        }
    }

    @Test
    fun dualFixturePlanDoesNotWriteThenApplyVerifiesAndRerunIsNoOp() {
        val targets = listOf(fixture("game"), fixture("fs"))
        val original = read(targets.first())
        val journals = scratch.newFolder("journals")
        val tx = ToaOverlayTexturePatchTool.plan(targets, mapOf(250 to patch), journals)
        val preflight = tx.preflight()
        assertTrue(preflight.all { it.outcome == MutationOutcome.REPLACE })
        assertFalse(tx.journalDir.exists())
        targets.forEach { assertArrayEquals(original, read(it)) }
        tx.apply(preflight)
        assertTrue(tx.verify().isEmpty())
        targets.forEach { assertEquals(301, Rev667FloorCodec.decodeOverlay(read(it)).texture) }
        val again = ToaOverlayTexturePatchTool.plan(targets, mapOf(250 to patch), journals)
        assertTrue(again.preflight().all { it.outcome == MutationOutcome.NO_OP })
    }

    @Test
    fun secondTargetOutsideUseOrDivergentDefinitionBlocksTheWholePlan() {
        val first = fixture("good")
        val original = read(first)
        val outside = fixture("outside", outsideUse = true)
        val divergent = fixture("different", extraField = byteArrayOf(5))
        for (second in listOf(outside, divergent)) {
            try {
                ToaOverlayTexturePatchTool.plan(listOf(first, second), mapOf(250 to patch), scratch.newFolder())
                fail("unsafe second target accepted")
            } catch (expected: IllegalStateException) { /* fail closed */ }
            assertArrayEquals(original, read(first))
        }
    }

    private fun fixture(name: String, outsideUse: Boolean = false, extraField: ByteArray = byteArrayOf()): String {
        val dir = scratch.newFolder(name)
        File(dir, "main_file_cache.dat2").createNewFile()
        File(dir, "main_file_cache.idx255").createNewFile()
        val library = CacheLibrary.create(dir.absolutePath)
        try {
            // Named indices: the map archive must keep its name hash ("m52_142"), or the tool cannot find any terrain.
            while (!library.exists(9)) library.createIndex(com.displee.compress.CompressionType.GZIP, 6, 0, true, false)
            library.put(2, 4, 250, fields + extraField + byteArrayOf(0))
            library.put(9, 301, 0, byteArrayOf(1))
            val map = Rev667TileMap()
            map.tiles[3][63][63].overlayId = 251
            library.put(5, if (outsideUse) "m1_1" else "m52_142", Rev667TileCodec.encode(map))
            library.update()
        } finally { library.close() }
        return dir.absolutePath
    }

    private fun read(target: String): ByteArray {
        val library = CacheLibrary(target)
        return try { library.data(2, 4, 250)!! } finally { library.close() }
    }
}
