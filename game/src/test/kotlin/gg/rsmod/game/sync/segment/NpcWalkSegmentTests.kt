package gg.rsmod.game.sync.segment

import gg.rsmod.net.packet.GamePacketBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RCV-012 B3/B4: the npc movement segment must decode exactly as the revision-667 client reads it
 * (2011scape-client `NPCList`, `NpcUpdate`): 2-bit type, WALK = 1 -> 3-bit direction; RUN_OR_CRAWL = 2 -> 1-bit flag,
 * flag 1 -> two 3-bit directions (Novite `LocalNPCUpdate` writes the same); then the 1-bit extended-info flag.
 * Roster: every walk direction x every run direction (and none), with and without extended info.
 */
class NpcWalkSegmentTests {
    private class BitReader(private val bytes: ByteArray) {
        private var bit = 0

        fun read(count: Int): Int {
            var value = 0
            repeat(count) {
                val b = (bytes[bit shr 3].toInt() shr (7 - (bit and 7))) and 1
                value = (value shl 1) or b
                bit++
            }
            return value
        }
    }

    private data class Decoded(val directions: List<Int>, val extendedInfo: Boolean)

    /** Mirrors the client's per-npc movement read after the leading "has update" bit. */
    private fun clientRead(reader: BitReader): Decoded =
        when (val type = reader.read(2)) {
            1 -> Decoded(listOf(reader.read(3)), reader.read(1) == 1)
            2 -> {
                val dirs = if (reader.read(1) == 1) listOf(reader.read(3), reader.read(3)) else listOf(reader.read(3))
                Decoded(dirs, reader.read(1) == 1)
            }
            else -> error("unexpected movement type $type")
        }

    private fun encode(segment: NpcWalkSegment): ByteArray {
        val buf = GamePacketBuilder()
        buf.switchToBitAccess()
        segment.encode(buf)
        buf.putBits(7, 0) // pad so the last partial byte is flushed
        buf.switchToByteAccess()
        val out = ByteArray(buf.byteBuf.readableBytes())
        buf.byteBuf.getBytes(buf.byteBuf.readerIndex(), out)
        return out
    }

    @Test
    fun `every walk and run direction decodes as the client reads it`() {
        for (ext in listOf(false, true)) {
            for (walk in 0..7) {
                val walked = clientRead(BitReader(encode(NpcWalkSegment(walk, -1, ext))))
                assertEquals(Decoded(listOf(walk), ext), walked, "walk=$walk ext=$ext")
                for (run in 0..7) {
                    val ran = clientRead(BitReader(encode(NpcWalkSegment(walk, run, ext))))
                    assertEquals(Decoded(listOf(walk, run), ext), ran, "walk=$walk run=$run ext=$ext must move the client two tiles")
                }
            }
        }
    }
}
