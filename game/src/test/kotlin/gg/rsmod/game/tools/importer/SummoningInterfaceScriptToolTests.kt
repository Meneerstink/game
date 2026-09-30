package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [SummoningInterfaceScriptTool] against the real game cache, read-only (tx-20260930-193640): the Follower Details tab no
 * longer depends on the temporary VARC 823, and clientscript 5378 relabels the follower's dismiss entry.
 */
class SummoningInterfaceScriptToolTests {
    private fun script(id: Int): ByteArray {
        val library = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
        try {
            return requireNotNull(library.data(SummoningInterfaceScriptTool.CLIENTSCRIPT_INDEX, id, 0)) { "clientscript $id missing" }
        } finally {
            library.close()
        }
    }

    @Test
    fun `the tab strip script reads no varc 823 and the patch is idempotent`() {
        val tab = script(SummoningInterfaceScriptTool.TAB_SCRIPT)
        val code = ProductionTabClientScriptPatchTool.decode(tab)
        assertTrue(code.none { it.opcode == 42 && it.intOperand == 823 }, "clientscript 1766 still reads VARC 823")
        assertContentEquals(tab, SummoningInterfaceScriptTool.patchTabScript(tab))
        // The Follower Details branch itself is untouched: summon sprite and label on both panes.
        assertEquals(1200, code[460].intOperand)
        assertEquals("Follower Details", code[467].stringOperand)
    }

    @Test
    fun `the label script is in the cache exactly as the tool encodes it`() {
        val label = script(SummoningInterfaceScriptTool.LABEL_SCRIPT)
        assertContentEquals(SummoningInterfaceScriptTool.labelScript(), label)
        val code = ProductionTabClientScriptPatchTool.decode(label)
        val targets = code.filter { it.opcode == 0 && it.intOperand!! > 0xFFFF }.map { (it.intOperand!! ushr 16) to (it.intOperand!! and 0xFFFF) }
        assertEquals(listOf(747 to 11, 747 to 20, 662 to 51, 662 to 51), targets)
        assertEquals(4, code.count { it.opcode == 2300 })
    }
}
