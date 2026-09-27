package gg.rsmod.game.fs.def

import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Owner 2026-09-27 (breach monsters stop following): a def without opcode 119 decoded walkMask -1, whose swimmer bit 0x4 made
 * every imported OSRS npc water-only after its first random walk.
 */
class NpcDefWalkMaskTests {
    @Test
    fun `a def without opcode 119 has no movement flags`() {
        val buf = Unpooled.buffer()
        buf.writeByte(0) // end
        val def = NpcDef(14468)
        def.decode(buf)
        assertEquals(0, def.walkMask)
        assertEquals(0, def.walkMask and 0x4)
    }

    @Test
    fun `opcode 119 still sets the mask`() {
        val buf = Unpooled.buffer()
        buf.writeByte(119)
        buf.writeByte(4)
        buf.writeByte(0)
        val def = NpcDef(1)
        def.decode(buf)
        assertEquals(4, def.walkMask)
    }
}
