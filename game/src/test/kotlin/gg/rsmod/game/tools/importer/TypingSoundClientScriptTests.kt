package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A19 class test: no text box of the client makes a sound while the player types (owner 2026-10-11, fart sound on backspace in
 * the chat; OSRS has no typing sound). The class is every text box: the shared editor clientscript 802 plus every script that
 * hands a key to it (chat 73, dialogs 112, search boxes ...; found from the cache, not listed by hand), and cache-wide no
 * script may reach the input-reject sound 4173. Checked in BOTH caches (game + file-server, what the client downloads) on the
 * script as shipped once the A19 [TypingSoundClientScriptPatchTool] transaction is applied (the patch is idempotent, so after
 * the cache window this is the installed script itself). Read-only.
 */
class TypingSoundClientScriptTests {
    private val caches = listOf(Paths.get("..", "data", "cache").toFile(), Paths.get("..", "..", "..", "file-server", "cache").toFile())

    /** Original 667 + "Bb" suffix script 802 (tx-20261004-092552) = the state before the A19 cache window. */
    private val preA19Sha1 = "2f8abf516ae534ea783e60dc9b0599fdaaba07d0"

    private fun scripts(cache: File): Map<Int, ByteArray> {
        val library = CacheLibrary(cache.toString())
        return try {
            val index = library.index(TypingSoundClientScriptPatchTool.CLIENTSCRIPT_INDEX)
            index.archiveIds().sorted().mapNotNull { id ->
                val data = library.data(TypingSoundClientScriptPatchTool.CLIENTSCRIPT_INDEX, id, 0) ?: return@mapNotNull null
                runCatching { ProductionTabClientScriptPatchTool.decode(data) }.getOrNull() ?: return@mapNotNull null
                id to data
            }.toMap()
        } finally {
            library.close()
        }
    }

    /** Scripts as the client gets them after the A19 transaction. */
    private fun served(installed: Map<Int, ByteArray>): Map<Int, ByteArray> =
        installed + (TypingSoundClientScriptPatchTool.SCRIPT to TypingSoundClientScriptPatchTool.patch(installed.getValue(TypingSoundClientScriptPatchTool.SCRIPT)))

    private fun typingSoundOffenders(scripts: Map<Int, ByteArray>): Pair<List<Int>, List<String>> {
        val textBoxes = listOf(TypingSoundClientScriptPatchTool.SCRIPT) +
            scripts.filter { (id, data) -> id != TypingSoundClientScriptPatchTool.SCRIPT && TypingSoundClientScriptPatchTool.SCRIPT in TypingSoundClientScriptPatchTool.gosubs(data) }.keys.sorted()
        val offenders = mutableListOf<String>()
        textBoxes.forEach { id ->
            TypingSoundClientScriptPatchTool.reachableSounds(scripts.getValue(id)).forEach { (at, sound) ->
                offenders += "text-box script $id plays sound $sound at instruction #$at"
            }
        }
        scripts.forEach { (id, data) ->
            TypingSoundClientScriptPatchTool.reachableSounds(data).filter { it.second == TypingSoundClientScriptPatchTool.REJECT_SOUND }.forEach { (at, _) ->
                if (id !in textBoxes) offenders += "script $id plays input-reject sound ${TypingSoundClientScriptPatchTool.REJECT_SOUND} at #$at"
            }
        }
        return textBoxes to offenders
    }

    @Test
    fun `no text box plays a typing or backspace sound in either cache`() {
        val report = mutableListOf<String>()
        caches.forEach { cache ->
            val (textBoxes, offenders) = typingSoundOffenders(served(scripts(cache)))
            println("A19 $cache text-box scripts=$textBoxes offenders=$offenders")
            assertTrue(textBoxes.containsAll(listOf(73, 112)), "chat (73) and the input dialogs (112) must hand keys to 802: $textBoxes")
            report += offenders.map { "$cache: $it" }
        }
        assertTrue(report.isEmpty(), "typing makes a sound (OSRS: none):\n" + report.joinToString("\n"))
    }

    @Test
    fun `installed caches are in sync and either patched or still the pre-A19 script`() {
        val installed = caches.map { cache -> scripts(cache).getValue(TypingSoundClientScriptPatchTool.SCRIPT) }
        assertContentEquals(installed[0], installed[1], "game and file-server cache must carry the same clientscript 802")
        val current = installed[0]
        val patched = TypingSoundClientScriptPatchTool.patch(current)
        val state = if (patched.contentEquals(current)) "APPLIED" else "PENDING cache window"
        println("A19 installed clientscript 802 sha1=${CacheItemProbeTool.sha1(current)} transaction=$state")
        assertTrue(
            patched.contentEquals(current) || CacheItemProbeTool.sha1(current) == preA19Sha1,
            "clientscript 802 was changed by something else than A19 (sha1 ${CacheItemProbeTool.sha1(current)}): re-check the typing sound",
        )
    }

    @Test
    fun `patch changes only the reject-flag test, keeps the B suffix and is idempotent with CountInputSuffixTool`() {
        val current = scripts(caches[0]).getValue(TypingSoundClientScriptPatchTool.SCRIPT)
        val before = ProductionTabClientScriptPatchTool.decode(current)
        val patched = TypingSoundClientScriptPatchTool.patch(current)
        val after = ProductionTabClientScriptPatchTool.decode(patched)

        assertEquals(before.size, after.size)
        assertEquals(current.size, patched.size)
        val changed = before.indices.filter { before[it] != after[it] }
        val expected = if (before[381].opcode == 0) emptyList() else listOf(381)
        assertEquals(expected, changed)
        assertEquals(0, after[381].opcode)
        assertEquals(0, after[381].intOperand)
        assertEquals(4173, after[384].intOperand)
        assertTrue(TypingSoundClientScriptPatchTool.reachableSounds(current).isNotEmpty() || expected.isEmpty())
        assertEquals(emptyList(), TypingSoundClientScriptPatchTool.reachableSounds(patched))

        // the E7 "Bb" amount suffix (CountInputSuffixTool, same script) survives, and the two patches commute
        assertTrue(after.any { it.stringOperand == "KkMmTtBb" } && after.any { it.stringOperand == "KkMmBb" }, "B suffix lost")
        assertContentEquals(patched, TypingSoundClientScriptPatchTool.patch(patched))
        assertContentEquals(patched, CountInputSuffixTool.patch(patched))
        assertContentEquals(TypingSoundClientScriptPatchTool.patch(CountInputSuffixTool.patch(current)), CountInputSuffixTool.patch(TypingSoundClientScriptPatchTool.patch(current)))
    }
}
