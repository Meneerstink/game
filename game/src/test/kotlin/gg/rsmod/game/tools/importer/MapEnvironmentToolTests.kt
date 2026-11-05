package gg.rsmod.game.tools.importer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/** N568: the opcode-0 environment record is cut exactly at its end (2011scape-client Environment.method8386 field layout). */
class MapEnvironmentToolTests {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun `the Edgeville Dungeon record ends after its fog depth and the light records after it stay out`() {
        // m48_156: 00 | flags 72 | 0x02 g2 0071 | 0x10 3x g2s 0000 ff9c 0000 | 0x20 g4 001b150d | 0x40 g2 0190 | then opcode 1 (lights)
        val trailing = hex("0072007100" + "00ff9c0000" + "001b150d0190" + "013f")
        assertContentEquals(hex("00720071" + "0000ff9c0000" + "001b150d" + "0190"), MapEnvironmentTool.opcode0Record(trailing))
    }

    @Test fun `no record when the trailing bytes are empty or start with another opcode`() {
        assertNull(MapEnvironmentTool.opcode0Record(ByteArray(0)))
        assertNull(MapEnvironmentTool.opcode0Record(hex("013f")))
    }
}
