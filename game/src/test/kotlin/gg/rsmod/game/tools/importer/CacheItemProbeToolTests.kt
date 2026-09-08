package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.ArchiveType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Gate A7 of `RSPS_CURRENT_SPRINT.json`: proves item-index enumeration is read from the real
 * archive/file structure of the cache rather than from a bounded scan window.
 *
 * The previous implementation only looked for an orphan id in `maxContiguous+1..maxContiguous+512`,
 * which is silently blind to any hand-poked or corrupted id sitting further above the contiguous
 * block. [orphanFarBeyondTheOldFiveHundredAndTwelveIdWindowIsStillFound] proves the replacement -
 * [CacheItemProbeTool.allItemIds] - has no such blind spot, by placing an orphan well outside that
 * old window and showing it is still found.
 */
class CacheItemProbeToolTests {
    @get:Rule
    val scratch = TemporaryFolder()

    @Test
    fun orphanFarBeyondTheOldFiveHundredAndTwelveIdWindowIsStillFound() {
        // 10000 is nearly 20x further above the contiguous block (which ends at 2) than the old
        // maxContiguous+512 scan window ever looked - the exact class of gap that check could not see.
        val orphanId = 10000
        val library = buildScratchCache(contiguousIds = listOf(0, 1, 2), extraIds = listOf(orphanId))

        try {
            val allIds = CacheItemProbeTool.allItemIds(library)
            assertEquals("the complete real id set must be exactly what was written", listOf(0, 1, 2, orphanId), allIds)

            val max = CacheItemProbeTool.maxContiguousItemId(allIds)
            assertEquals("contiguous block must end at 2", 2, max)

            val orphans = allIds.filter { it > max }
            assertEquals("the far-away orphan must be found", listOf(orphanId), orphans)
        } finally {
            library.close()
        }
    }

    @Test
    fun aCleanContiguousIndexReportsNoOrphans() {
        val library = buildScratchCache(contiguousIds = listOf(0, 1, 2, 3, 4), extraIds = emptyList())

        try {
            val allIds = CacheItemProbeTool.allItemIds(library)
            val max = CacheItemProbeTool.maxContiguousItemId(allIds)
            assertEquals(4, max)
            assertTrue("a clean contiguous index must report no orphans", allIds.none { it > max })
        } finally {
            library.close()
        }
    }

    @Test
    fun anOrphanInAnEarlierArchiveGroupThanTheContiguousBlockEndsInIsStillFound() {
        // Item ids pack 256 per archive group (id ushr 8). Put the orphan in the SAME group as part
        // of the contiguous block's tail to prove enumeration reads whole groups correctly, not just
        // "one archive = one id".
        val orphanId = 260 // group 1, file 4 - group 1 also holds ids 256..258 contiguously below it.
        val library = buildScratchCache(contiguousIds = (0..258).toList(), extraIds = listOf(orphanId))

        try {
            val allIds = CacheItemProbeTool.allItemIds(library)
            val max = CacheItemProbeTool.maxContiguousItemId(allIds)
            assertEquals(258, max)
            assertEquals(listOf(orphanId), allIds.filter { it > max })
        } finally {
            library.close()
        }
    }

    /** A throwaway cache containing exactly [contiguousIds] union [extraIds] in the item index. */
    private fun buildScratchCache(
        contiguousIds: List<Int>,
        extraIds: List<Int>,
    ): CacheLibrary {
        val dir = scratch.newFolder("probe-cache-${scratch.root.list()?.size}")
        File(dir, "main_file_cache.dat2").createNewFile()
        File(dir, "main_file_cache.idx255").createNewFile()
        val library = CacheLibrary.create(dir.absolutePath)
        while (!library.exists(ArchiveType.ITEM.id)) {
            library.createIndex()
        }
        (contiguousIds + extraIds).forEach { id ->
            library.put(ArchiveType.ITEM.id, id ushr 8, id and 0xFF, "item-$id".toByteArray())
        }
        library.update()
        library.close()
        return CacheLibrary(dir.absolutePath)
    }
}
