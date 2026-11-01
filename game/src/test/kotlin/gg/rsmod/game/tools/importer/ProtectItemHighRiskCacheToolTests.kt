package gg.rsmod.game.tools.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** N503 D1: the three Protect Item cells get the OSRS High Risk icon and the "does not work" line, every other param unchanged. */
class ProtectItemHighRiskCacheToolTests {
    private val normal =
        ProtectItemHighRiskCacheTool.encode(
            linkedMapOf(
                737 to 25,
                738 to "You need a Prayer level of at least 25 to use Protect Item.",
                735 to 123,
                736 to 143,
                734 to "Level 25<br>Protect Item<br>Keep 1 extra item if you die",
            ),
        )

    @Test
    fun `a struct round-trips and only the icon and the effect line change`() {
        val tool = ProtectItemHighRiskCacheTool
        val params = tool.decode(normal)
        assertTrue(tool.encode(params).contentEquals(normal))
        val patched = tool.decode(tool.patched(normal, "Keep 1 extra item if you die")!!)
        assertEquals(tool.SPRITE, patched[735])
        assertEquals(tool.SPRITE, patched[736])
        assertEquals("Level 25<br>Protect Item<br>Does not work in Deadman Mode.", patched[734])
        assertEquals(25, patched[737])
        assertEquals(params[738], patched[738])
        assertEquals(params.keys.toList(), patched.keys.toList(), "same params in the same order")
    }

    @Test
    fun `running it twice is a no-op`() {
        val tool = ProtectItemHighRiskCacheTool
        val once = tool.patched(normal, "Keep 1 extra item if you die")!!
        assertNull(tool.patched(once, "Keep 1 extra item if you die"))
    }
}
