package gg.rsmod.game.fs.def

import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Opcode 13 (`SeqType.soundInfo`) marks a sequence whose frames play sounds; the combat cues key off it. */
class AnimDefFrameSoundTests {
    @Test
    fun `a sequence with a frame sound reports it`() {
        val buf = Unpooled.buffer()
        buf.writeByte(13)
        buf.writeShort(2) // two frames
        buf.writeByte(0) // frame 0: no sound
        buf.writeByte(1) // frame 1: one sound
        buf.writeMedium((6642 shl 8) or (1 shl 5)) // id 6642, one loop, radius 0
        buf.writeByte(0) // end
        val def = AnimDef(426)
        def.decode(buf)
        assertTrue(def.hasFrameSounds)
    }

    @Test
    fun `a sequence without frame sounds stays silent`() {
        val buf = Unpooled.buffer()
        buf.writeByte(13)
        buf.writeShort(1)
        buf.writeByte(0)
        buf.writeByte(0)
        val def = AnimDef(4230)
        def.decode(buf)
        assertFalse(def.hasFrameSounds)
    }
}
