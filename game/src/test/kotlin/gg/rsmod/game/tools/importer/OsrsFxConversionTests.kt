package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * OSRS-IMPORT (e): the frame and base converters reproduce the real rev-667 bytes of animations that exist unchanged in both
 * caches (Ice Barrage impact 369, splash 85, onyx bolt proc 758 - translate, alpha, scale and rotation groups).
 */
class OsrsFxConversionTests {
    private fun firstFrame(seq: ByteArray): Int {
        val c = OsrsFxImportTool.Cursor(seq)
        while (true) {
            val op = c.u8()
            if (op == 1) {
                val n = c.u16()
                repeat(n) { c.u16() }
                val lo = c.u16()
                c.pos += (n - 1) * 2
                return lo + (c.u16() shl 16)
            }
            if (op == 2) c.u16() else error("unexpected opcode $op before frames")
        }
    }

    /** A 667 base as (prefix bytes up to and including the sizes, per-group sorted label lists); asserts nothing is left over. */
    private fun baseSections(base: ByteArray): Pair<List<Int>, List<List<Int>>> {
        val c = OsrsFxImportTool.Cursor(base)
        val count = c.u8()
        val prefixLength = 1 + count * 4 + count
        val prefix = base.take(prefixLength).map { it.toInt() and 0xFF }
        c.pos = 1 + count * 4
        val sizes = IntArray(count) { c.u8() }
        val maps = sizes.map { size -> List(size) { c.u8() }.sorted() }
        assertEquals(0, c.remaining, "base has trailing bytes")
        return prefix to maps
    }

    @Test
    fun `converted OSRS frames and bases equal the 667 bytes of shared animations`() {
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val library = CacheLibrary(File("../data/cache").toString())
        try {
            mapOf(369 to 1965, 85 to 653, 758 to 4452).forEach { (spotanim, seqId) ->
                val osrsSeq = reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, seqId)!!
                val osrsFrameId = firstFrame(osrsSeq)
                val osrsFrame = reader.file(0, osrsFrameId ushr 16, osrsFrameId and 0xFFFF)!!
                val baseId = ((osrsFrame[0].toInt() and 0xFF) shl 8) or (osrsFrame[1].toInt() and 0xFF)
                val osrsBase = reader.file(1, baseId, 0)!!
                val localSeq = library.data(20, seqId ushr 7, seqId and 0x7F)!!
                val localFrameId = firstFrame(localSeq)
                val localFrame = library.data(0, localFrameId ushr 16, localFrameId and 0xFFFF)!!
                assertContentEquals(localFrame, OsrsFxImportTool.convertFrame(osrsFrame, OsrsFxImportTool.baseTypes(osrsBase), baseId), "spotanim $spotanim frame")
                // Bases: identical count, types, 667 boolean/part-mask arrays and sizes; each group holds the same vertex labels
                // (the newer OSRS framemap stores some groups' labels in another order, which the transform does not depend on).
                assertEquals(baseSections(library.data(1, baseId, 0)!!), baseSections(OsrsFxImportTool.convertBase(osrsBase)), "spotanim $spotanim base")
            }
        } finally {
            library.close()
            reader.close()
        }
    }

    @Test
    fun `sequences re-encode to a stream the 667 decoder walks completely`() {
        val seq = OsrsFxImportTool.Seq(frameDurations = intArrayOf(2, 3), frames = intArrayOf((4000 shl 16) or 1, (4000 shl 16) or 2), loopOffset = 1, priority = 5)
        seq.sounds[1] = 10300 to 1
        val bytes = OsrsFxImportTool.encode667Seq(seq)
        assertContentEquals(intArrayOf((4000 shl 16) or 1, (4000 shl 16) or 2), OsrsFxImportTool.decode667SeqFrames(bytes))
        val spot = OsrsFxImportTool.decodeOsrsSpot(byteArrayOf(3, 0, 0, 0x1a, 0x51, 2, 0x1b, 0xac.toByte(), 8, 0, 0))
        assertEquals(6737, spot.model)
        assertEquals(7084, spot.seq)
        assertContentEquals(byteArrayOf(1, 0x12, 0x34, 2, 0x3a, 0x98.toByte(), 8, 0, 0), OsrsFxImportTool.encode667Spot(spot, 0x1234, 15000))
    }
}
