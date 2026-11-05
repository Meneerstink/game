package gg.rsmod.game.tools.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** N543: the Protect Item cells get their own icon with the OSRS red ring (sprite 1193) over it; nothing else in the struct changes. */
class ProtectItemDeadmanIconCacheToolTests {
    private val tool = ProtectItemDeadmanIconCacheTool

    private fun image(w: Int, h: Int, f: (Int, Int) -> Int) = ProtectItemDeadmanIconCacheTool.Image(w, h, IntArray(w * h) { f(it % w, it / w) })

    @Test
    fun `a composite round-trips through the sprite encoder`() {
        val base = image(30, 29) { x, y -> if ((x + y) % 3 == 0) 0 else (0xFF shl 24) or (x % 5 * 40 shl 16) or (y % 5 * 40 shl 8) or 0x20 }
        val ring = image(32, 32) { x, y -> if (x == y || x + y == 31) (0xFF shl 24) or 0xC80000 else 0 }
        val comp = tool.composite(base, ring)
        val back = tool.decode(tool.encode(comp))
        assertEquals(32, back.width)
        assertEquals(32, back.height)
        assertTrue(comp.argb.contentEquals(back.argb), "same pixels after encode/decode")
        // the ring wins over the icon where both are drawn
        assertEquals(0xC80000, back.argb[31 * 32 + 0] and 0xFFFFFF)
    }

    @Test
    fun `black survives the palette (colour 0 is transparency)`() {
        val img = image(32, 32) { x, _ -> if (x < 16) (0xFF shl 24) else 0 }
        val back = tool.decode(tool.encode(img))
        assertTrue(back.argb.take(16).all { it != 0 }, "black pixels stay opaque")
    }

    @Test
    fun `struct gets the new icons only and a second run is a no-op`() {
        val hr = ProtectItemHighRiskCacheTool
        val struct = hr.encode(linkedMapOf(737 to 25, 735 to hr.SPRITE, 736 to hr.SPRITE, 734 to "Level 25<br>Protect Item<br>Does not work in Deadman Mode."))
        val once = tool.patched(struct, tool.spriteFor(0, false), tool.spriteFor(0, true))!!
        val params = hr.decode(once)
        assertEquals(8813, params[735])
        assertEquals(8814, params[736])
        assertEquals("Level 25<br>Protect Item<br>Does not work in Deadman Mode.", params[734])
        assertEquals(listOf(737, 735, 736, 734), params.keys.toList())
        assertNull(tool.patched(once, tool.spriteFor(0, false), tool.spriteFor(0, true)))
        assertNull(hr.patched(once, "Keep 1 extra item if you die"), "the High Risk tool never puts the skull back")
    }
}
