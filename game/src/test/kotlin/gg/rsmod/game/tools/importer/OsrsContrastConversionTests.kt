package gg.rsmod.game.tools.importer

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Item opcode 114 unit conversion: OSRS lights with the raw byte (`contrast + 768`), the 667 client with `byte * 5`
 * (`ObjType.decode`), so the 667 byte is the OSRS value divided by 5 (evidence in `OsrsItemImportTool.rev667Contrast`).
 */
class OsrsContrastConversionTests {
    @Test
    fun `exact multiples of five convert without loss`() {
        assertEquals(0, OsrsItemImportTool.rev667Contrast(0))
        assertEquals(6, OsrsItemImportTool.rev667Contrast(30))
        assertEquals(-4, OsrsItemImportTool.rev667Contrast(-20))
    }

    @Test
    fun `other values round to the nearest representable contrast`() {
        assertEquals(1, OsrsItemImportTool.rev667Contrast(3))
        assertEquals(0, OsrsItemImportTool.rev667Contrast(2))
        assertEquals(25, OsrsItemImportTool.rev667Contrast(127))
        assertEquals(-26, OsrsItemImportTool.rev667Contrast(-128))
    }

    @Test
    fun `the result always fits the signed byte the 667 decoder reads`() {
        (-128..127).forEach { osrs ->
            val converted = OsrsItemImportTool.rev667Contrast(osrs)
            kotlin.test.assertTrue(converted in -128..127, "$osrs -> $converted")
            kotlin.test.assertTrue(kotlin.math.abs(converted * 5 - osrs) <= 2, "$osrs -> $converted is the nearest multiple")
        }
    }
}
