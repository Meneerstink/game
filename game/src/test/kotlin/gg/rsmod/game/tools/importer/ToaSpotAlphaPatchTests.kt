package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.SpotAnimDef
import io.netty.buffer.Unpooled
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ToaSpotAlphaPatchTests {
    @get:Rule val scratch = TemporaryFolder()
    // Opcode-looking payload bytes, including 242: preserve every byte of every supported field.
    private val fields = byteArrayOf(241.toByte(), 0, 1, 0xf2.toByte(), 0, 2, 0x43, 0xdc.toByte(), 4, 0, 128.toByte(),
        5, 0, 128.toByte(), 6, 0, 90, 7, 60, 8, 60, 9, 10, 11, 12, 13, 14, 242.toByte(), 15, 0, 242.toByte(),
        16, 0, 0, 0, 242.toByte(), 40, 1, 0, 242.toByte(), 0, 127, 41, 1, 0, 8, 0, 8)

    @Test fun markerPreservesWideModelSequenceLightingAndRecoloursAndIsIdempotent() {
        val after = ToaSpotAlphaPatchTool.patch(fields + byteArrayOf(0))
        assertArrayEquals(fields + byteArrayOf(242.toByte(), 0), after)
        assertArrayEquals(after, ToaSpotAlphaPatchTool.patch(after))
    }

    @Test fun corruptOrUnknownDefinitionsAreRefused() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 0), byteArrayOf(242.toByte()), byteArrayOf(0, 1), byteArrayOf(77, 0))) {
            try { ToaSpotAlphaPatchTool.patch(bytes); fail("invalid spot accepted") } catch (expected: RuntimeException) { }
        }
    }

    @Test fun serverDecoderConsumesMarkerWithoutChangingSequenceOrFollowingFields() {
        val def = SpotAnimDef(3381)
        val bytes = Unpooled.wrappedBuffer(byteArrayOf(242.toByte(), 2, 0x43, 0xdc.toByte(), 7, 60, 8, 60, 0))
        while (true) { val op = bytes.readUnsignedByte().toInt(); if (op == 0) break; def.decode(bytes, op) }
        assertTrue(def.osrsAlphaOrder)
        assertEquals(17372, def.sequence)
        assertEquals(0, bytes.readableBytes())
    }

    @Test fun dualCachePlanIsReadOnlyAndFixtureApplyVerifiesWithoutModelMutations() {
        val targets = listOf(fixture("game"), fixture("files"))
        val tx = ToaSpotAlphaPatchTool.plan(targets, mapOf(2114 to 3381), scratch.newFolder("journals"))
        assertEquals(1, tx.mutations.size)
        assertEquals(21, tx.mutations.single().indexId)
        val preflight = tx.preflight()
        assertTrue(preflight.all { it.outcome == MutationOutcome.REPLACE })
        assertFalse(tx.journalDir.exists())
        targets.forEach { assertArrayEquals(fields + byteArrayOf(0), read(it)) }
        tx.apply(preflight)
        assertTrue(tx.verify().isEmpty())
        targets.forEach { assertArrayEquals(fields + byteArrayOf(242.toByte(), 0), read(it)) }
        val again = ToaSpotAlphaPatchTool.plan(targets, mapOf(2114 to 3381), scratch.newFolder("again"))
        assertTrue(again.preflight().all { it.outcome == MutationOutcome.NO_OP })
    }

    @Test fun divergentSecondCacheRefusesTheWholePlan() {
        val targets = listOf(fixture("first"), fixture("second", byteArrayOf(7, 59, 0)))
        try {
            ToaSpotAlphaPatchTool.plan(targets, mapOf(2114 to 3381), scratch.newFolder("journal"))
            fail("divergent target accepted")
        } catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("differs between caches")) }
        assertArrayEquals(fields + byteArrayOf(0), read(targets.first()))
    }

    private fun fixture(name: String, bytes: ByteArray = fields + byteArrayOf(0)): String {
        val dir = scratch.newFolder(name)
        File(dir, "main_file_cache.dat2").createNewFile()
        File(dir, "main_file_cache.idx255").createNewFile()
        val cache = CacheLibrary.create(dir.absolutePath)
        try {
            while (!cache.exists(21)) cache.createIndex(com.displee.compress.CompressionType.GZIP, 6, 0, false, false)
            cache.put(21, 3381 ushr 8, 3381 and 255, bytes)
            cache.update()
        } finally { cache.close() }
        return dir.absolutePath
    }

    private fun read(target: String): ByteArray {
        val cache = CacheLibrary(target)
        return try { cache.data(21, 3381 ushr 8, 3381 and 255)!! } finally { cache.close() }
    }
}
