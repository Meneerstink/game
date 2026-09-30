package gg.rsmod.game.tools.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Item opcodes 113/114 and npc opcodes 100/101 (ambient/contrast) from OSRS to 667 (evidence in
 * `OsrsItemImportTool.rev667Contrast` / `rev667Ambient`).
 *
 * Contrast: both clients decode `byte * 5` (open-osrs `ItemComposition`/`NPCComposition`, 2011scape-client
 * `ObjType`/`NPCType`), so the byte is copied. Ambient: OSRS adds it to the directional light, 667 multiplies the colour by
 * `(ambient + 64) / 64`, so it is rescaled to the same brightness ratio at the lit hemisphere's mean angle.
 */
class OsrsContrastConversionTests {
    @Test
    fun `contrast is the same byte in both clients`() {
        (-128..127).forEach { assertEquals(it, OsrsItemImportTool.rev667Contrast(it)) }
    }

    @Test
    fun `ambient zero stays zero`() {
        assertEquals(0, OsrsItemImportTool.rev667Ambient(0, 0, OsrsItemImportTool.ITEM_CONTRAST_BASE))
        assertEquals(0, OsrsItemImportTool.rev667Ambient(0, 7, OsrsItemImportTool.NPC_CONTRAST_BASE))
    }

    @Test
    fun `blighted super restore ambient -55 keeps the OSRS brightness ratio instead of going black`() {
        // OSRS item 24598: ambient -55, no contrast. OSRS ratio at cos 1/2: (9 + 42.67) / (64 + 42.67) = 0.484.
        val converted = OsrsItemImportTool.rev667Ambient(-55, 0, OsrsItemImportTool.ITEM_CONTRAST_BASE)
        assertEquals(-33, converted)
        val ratio667 = (converted + 64) / 64.0
        val ratioOsrs = (-55 + 64 + 32768.0 / 768) / (64 + 32768.0 / 768)
        assertTrue(kotlin.math.abs(ratio667 - ratioOsrs) < 0.01, "667 $ratio667 vs OSRS $ratioOsrs")
    }

    @Test
    fun `every OSRS ambient maps into the signed byte with the OSRS brightness ratio`() {
        for (base in listOf(OsrsItemImportTool.ITEM_CONTRAST_BASE, OsrsItemImportTool.NPC_CONTRAST_BASE)) {
            for (contrast in listOf(-20, 0, 3, 8, 30)) {
                for (ambient in -128..127) {
                    val converted = OsrsItemImportTool.rev667Ambient(ambient, contrast, base)
                    assertTrue(converted in -128..127)
                    assertTrue(kotlin.math.abs(converted) <= kotlin.math.abs(ambient), "$ambient -> $converted never amplifies")
                    val half = 32768.0 / (base + contrast * 5)
                    val ratioOsrs = (ambient + 64 + half) / (64 + half)
                    assertTrue(kotlin.math.abs((converted + 64) / 64.0 - ratioOsrs) <= 0.5 / 64 + 1e-9, "$ambient/$contrast/$base")
                }
            }
        }
    }
}
