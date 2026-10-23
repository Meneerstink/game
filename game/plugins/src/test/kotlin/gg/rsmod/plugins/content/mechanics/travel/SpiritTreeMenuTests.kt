package gg.rsmod.plugins.content.mechanics.travel

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.SpiritTreeInterfaceTool
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A28 (owner 2026-10-10: the spirit tree menu looked odd, wanted exact OSRS): the OSRS menu (OSRS Wiki "Spirit tree interface.png")
 * is "Spirit Tree Locations" with one centred "<key>: <name>" line per tree, the tree you stand at greyed, the last line Cancel.
 */
class SpiritTreeMenuTests {
    @Test
    fun `the menu lists the trees as OSRS numbers them, ending with Cancel`() {
        assertEquals(
            listOf("1: Tree Gnome Village", "2: Gnome Stronghold", "3: Battlefield of Khazard", "4: Grand Exchange", "5: Mobilising Armies", "6: Cancel"),
            SpiritTrees.lines(null),
        )
    }

    @Test
    fun `the tree you stand at is greyed`() {
        val lines = SpiritTrees.lines(SpiritTrees.STATIONS[1])
        assertEquals("<col=5f5f5f>2: Gnome Stronghold", lines[1])
        assertTrue(lines.filterIndexed { i, _ -> i != 1 }.none { it.startsWith("<col=") })
    }

    @Test
    fun `the OSRS title and rows exist in 864 of both caches, centred and hidden until filled`() {
        assertEquals(ScrollMenu.TITLE, SpiritTreeInterfaceTool.TITLE)
        assertEquals(ScrollMenu.FIRST_ROW, SpiritTreeInterfaceTool.FIRST_ROW)
        assertEquals(ScrollMenu.ROWS, SpiritTreeInterfaceTool.ROWS)
        for (cache in listOf("../../data/cache", "../../../../file-server/cache").filter { File(it).isDirectory }) {
            val library = CacheLibrary(cache)
            try {
                val files = library.index(3).archive(864)!!.fileIds().toSet()
                assertTrue((SpiritTreeInterfaceTool.TITLE until SpiritTreeInterfaceTool.FIRST_ROW + SpiritTreeInterfaceTool.ROWS).all { it in files }, "$cache: 864 has $files")
            } finally {
                library.close()
            }
        }
    }
}
