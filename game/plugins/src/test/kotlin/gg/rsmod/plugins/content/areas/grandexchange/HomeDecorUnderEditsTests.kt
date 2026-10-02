package gg.rsmod.plugins.content.areas.grandexchange

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ROOT_CAUSES #144: the home dressing (home_decor.txt) is placed after the owner's developer-mode edits' boot pass and again
 * on every save of the file, so it must always go in under the edits - else every loc the owner removed or moved from it
 * comes back (2026-10-02: the casino he moved to the top floor stood on the first floor again and its card table walled off
 * the west staircase).
 */
class HomeDecorUnderEditsTests {
    private val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/areas/grandexchange/home_decor_live.plugin.kts").readText()
    private val edits = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/worldedit/WorldEdits.kt").readText()

    @Test
    fun `the dressing is only ever applied inside WorldEdits underEdits`() {
        val calls = Regex("""applyDesign\(""").findAll(plugin).count()
        // One definition and one call - the call inside underEdits.
        assertEquals(2, calls, "applyDesign is called somewhere else")
        assertTrue(Regex("""WorldEdits\.underEdits\(world\)\s*\{\s*summary = applyDesign\(design\)\s*}""").containsMatchIn(plugin))
    }

    @Test
    fun `underEdits undoes the edits, rebuilds, then applies them again`() {
        val body = edits.substringAfter("fun underEdits(").substringBefore("fun reload(")
        val undo = body.indexOf("undo(world)")
        val rebuild = body.indexOf("rebuild()")
        val reload = body.indexOf("reload(world)")
        assertTrue(undo in 0 until rebuild && rebuild < reload, "order undo -> rebuild -> reload")
    }
}
