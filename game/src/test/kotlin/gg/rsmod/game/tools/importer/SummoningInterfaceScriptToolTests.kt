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

    /**
     * N425: clientscript 751 posed a pet's Follower Details head with enum 1275[127 - 50] and rewrote varbit 4282. The guard in
     * front of the selector read jumps past both poses for the pet value 127; the branch that used to land on the read lands on
     * the guard, and everything else in the script is unchanged.
     */
    @Test
    fun `clientscript 751 leaves a pet's head animation to the server`() {
        val panel = script(SummoningInterfaceScriptTool.PANEL_SCRIPT)
        val code = ClientScriptEditor.decode(panel).code
        val guard = code.indices.single { code[it].opcode == 25 && code[it].int == 4282 && code[it + 1].opcode == 0 && code[it + 1].int == 127 }
        assertEquals(8, code[guard + 2].opcode, "BRANCH_EQUALS")
        val resume = code[guard + 2].target!!
        val resumeAt = code.indexOf(resume)
        // Between the guard and its target: the selector read, both enum lookups, the varbit write and both IF_SETMODELANIM(662:1).
        val skipped = code.subList(guard + 3, resumeAt)
        assertEquals(2, skipped.count { it.opcode == 2110 })
        assertEquals(listOf(1275, 1276), skipped.filter { it.opcode == 0 && (it.int == 1275 || it.int == 1276) }.map { it.int })
        assertEquals(1, skipped.count { it.opcode == 27 && it.int == 4282 })
        assertTrue(code.none { it.target === code[guard + 3] }, "a branch still jumps past the guard straight to the selector read")
        assertTrue(code.subList(resumeAt, code.size).none { it.opcode == 2110 }, "751 sets 662:1's animation after the guard's target")
        assertContentEquals(panel, SummoningInterfaceScriptTool.patchPanelScript(panel))
    }

    /** N423: clientscript 5396 relabels 662:69 (op 1 and its onMouseRepeat 779 tooltip) - Store for an upgraded pet. */
    @Test
    fun `the store label script is in the cache exactly as the tool encodes it`() {
        val store = script(SummoningInterfaceScriptTool.STORE_LABEL_SCRIPT)
        assertContentEquals(SummoningInterfaceScriptTool.storeLabelScript(), store)
        val code = ProductionTabClientScriptPatchTool.decode(store)
        assertEquals(listOf(2300, 2412), code.filter { it.opcode >= 2000 }.map { it.opcode })
        assertEquals(2, code.count { it.opcode == 0 && it.intOperand == (662 shl 16 or 69) })
        assertEquals("IIIis", code.single { it.opcode == 3 }.stringOperand)
        // The tooltip hook keeps the cache's own script and arguments (662:69 onMouseRepeat=[779, event_com, 662:73, 662:73, 1, ...]).
        assertEquals(listOf(779, -2147483645, 662 shl 16 or 73, 662 shl 16 or 73, 1), code.subList(4, 9).map { it.intOperand })
    }

    /**
     * N427: for a follower enum 1279 does not know ("Animal": every pet) 751 wrote OC_NAME(varp 448) - the item's "Pet Kraken" -
     * into 662:54. That branch now lands after both name writes, so only a known familiar's enum name is still written by 751.
     */
    @Test
    fun `clientscript 751 writes no item name over a pet's name bar`() {
        val panel = script(SummoningInterfaceScriptTool.PANEL_SCRIPT)
        val code = ClientScriptEditor.decode(panel).code
        val at = code.indices.single { code[it].opcode == 3 && code[it].string == SummoningInterfaceScriptTool.UNKNOWN_FOLLOWER_NAME }
        assertEquals(87, code[at + 2].opcode, "BRANCH_IF_FALSE on the compare")
        val landing = code.indexOf(code[at + 2].target!!)
        // The landing is past both IF_SETTEXT(662:54); the reachable path from the compare to it holds only the known-name write.
        val writes = code.indices.filter { code[it].opcode == 2112 && code[it - 1].int == (662 shl 16 or 54) }
        assertEquals(2, writes.size)
        assertTrue(writes.all { it < landing }, "the 'Animal' branch lands before a name write: $writes vs $landing")
        assertEquals(writes[1] + 1, landing, "it lands right after the known-name write")
        assertContentEquals(panel, SummoningInterfaceScriptTool.patchPanelName(panel))
        assertContentEquals(panel, SummoningInterfaceScriptTool.patchPanelScript(panel), "the N425 guard is kept")
    }

    /**
     * Owner 2026-10-02 ("Summoning tab disappears"): 1364 hides a tab whose own container (script 8) is empty, and 662 lives
     * on slot 95. Every script that names tab 8's container must name slot 95's instead, in both panes, so the tab is shown
     * whenever the panel is mounted - and no script may still select or test 746:98 / 548:212 as tab 8's page.
     */
    @Test
    fun `tab 8 opens the Follower Details slot in every tab script`() {
        val remap = SummoningInterfaceScriptTool.TAB8_CONTAINER_REMAP
        SummoningInterfaceScriptTool.TAB8_CONTAINER_SCRIPTS.forEach { id ->
            val bytes = script(id)
            val pushes = ProductionTabClientScriptPatchTool.decode(bytes).filter { it.opcode == 0 }.map { it.intOperand }
            remap.forEach { (old, new) ->
                assertTrue(old !in pushes, "clientscript $id still names tab 8's own container ${old ushr 16}:${old and 0xFFFF}")
                assertEquals(2, pushes.count { it == new }, "clientscript $id: slot ${new ushr 16}:${new and 0xFFFF} should serve tab 8 and slot 95")
            }
            assertContentEquals(bytes, SummoningInterfaceScriptTool.remapTab8Container(bytes))
        }
    }
}
