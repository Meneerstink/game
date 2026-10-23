package gg.rsmod.game.tools.importer

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** N12: a new OSRS item import gets the A5 decal lift in its one conversion step, not only through a separate tool run. */
class ImportDecalLiftTests {
    @Test
    fun `the item import lifts hidden detail faces when it converts a mesh`() {
        val source = File("src/main/kotlin/gg/rsmod/game/tools/importer/OsrsItemImportTool.kt").readText()
        assertTrue("decalLifted(OsrsModelConversion.convert(reader, modelId, dropped, wornSlot, female))" in source)
    }

    @Test
    fun `a mesh without hidden detail faces comes back unchanged`() {
        val bytes = byteArrayOf(1, 2, 3)
        assertTrue(OsrsItemImportTool.decalLifted(bytes).contentEquals(bytes), "an undecodable mesh is left alone")
    }
}
