package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.ArchiveType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * WornAppearanceRankTool gate (`RSPS_CURRENT_SPRINT.json`, owner-approved unattended engineering
 * run 2026-09-04): proves [WornAppearanceRankTool.rank] against real, throwaway [CacheLibrary]
 * instances - never a production cache as a disposable test fixture, matching every other importer
 * tool test in this package.
 *
 * These synthetic cases are what this run's regression coverage is anchored to. The real production
 * cache was additionally spot-checked by hand against two low/mid-range vanilla items whose
 * `appearance_id` predates and is unrelated to any of this project's own imports: item 35
 * (Excalibur, recorded `appearance_id: 0`) and item 1127 (Rune platebody, recorded
 * `appearance_id: 240`) - this tool computed exactly 0 and exactly 240 for them, an exact match in
 * both cases. Item 22326 (Twisted bow, recorded `appearance_id: 6293`) was deliberately NOT used as
 * a regression anchor: this tool computes 5651 for it against the current production cache, a
 * genuine, evidenced SOURCE_CONFLICT documented in [WornAppearanceRankTool]'s class doc rather than
 * hidden behind a hardcoded assertion of either number.
 */
class WornAppearanceRankToolTests {
    @get:Rule
    val scratch = TemporaryFolder()

    @Test
    fun rankIsTheCountOfWearableItemsStrictlyBelowTheTarget() {
        val library = scratchCache()
        putItem(library, 0, wearable = false)
        putItem(library, 1, wearable = true) // counts
        putItem(library, 2, wearable = false)
        putItem(library, 3, wearable = true) // counts
        putItem(library, 4, wearable = true) // the target

        val result = WornAppearanceRankTool.rank(library, targetId = 4)

        assertEquals(2, result.rank)
        assertTrue("target itself has opcode 23/25 so must qualify", result.targetQualifies)
    }

    @Test
    fun nonWearableItemsNeverCountTowardRank() {
        val library = scratchCache()
        putItem(library, 0, wearable = false)
        putItem(library, 1, wearable = false)
        putItem(library, 2, wearable = false) // the target, itself not wearable

        val result = WornAppearanceRankTool.rank(library, targetId = 2)

        assertEquals(0, result.rank)
        assertFalse(result.targetQualifies)
    }

    @Test
    fun itemsAtOrAboveTheTargetIdNeverCountTowardItsOwnRank() {
        val library = scratchCache()
        putItem(library, 0, wearable = true) // below target - counts
        putItem(library, 5, wearable = true) // the target
        putItem(library, 6, wearable = true) // above target - must not count
        putItem(library, 7, wearable = true) // above target - must not count

        val result = WornAppearanceRankTool.rank(library, targetId = 5)

        assertEquals(1, result.rank)
    }

    @Test
    fun opcode25AloneIsSufficientToQualifyExactlyLikeOpcode23Alone() {
        val library = scratchCache()
        putItem(library, 0, maleWornModel = -1, maleWornModel2 = 900) // only opcode 25 present
        putItem(library, 1, maleWornModel = 900, maleWornModel2 = -1) // only opcode 23 present
        putItem(library, 2, wearable = false)

        val result = WornAppearanceRankTool.rank(library, targetId = 2)

        assertEquals("both opcode-23-only and opcode-25-only items must count", 2, result.rank)
    }

    @Test
    fun anItemWithNoDataAtAllIsSimplyAbsentFromTheProductionLoadAndNeverCounted() {
        // Mirrors the real client's list(id) returning a blank default ObjType (manwear=-1,
        // womanwear=-1) for an id with no archive data - see PlayerEntity.java's initWornObjIds()
        // and ObjTypeList.list(). DefinitionSet.load()'s `?: continue` skips ids with no data
        // entirely, which has exactly the same not-wearable effect for this tool's purposes.
        val library = scratchCache()
        putItem(library, 0, wearable = true)
        // id 1 deliberately has no archive data at all.
        putItem(library, 2, wearable = false) // the target

        val result = WornAppearanceRankTool.rank(library, targetId = 2)

        assertEquals(1, result.rank)
    }

    @Test
    fun targetWithNoDataAtAllReportsNullFieldsAndDoesNotQualify() {
        val library = scratchCache()
        putItem(library, 0, wearable = true)
        // id 1 (the target) is never written.

        val result = WornAppearanceRankTool.rank(library, targetId = 1)

        assertEquals(1, result.rank)
        assertFalse(result.targetQualifies)
        assertNull(result.targetManwear)
        assertNull(result.targetWomanwear)
    }

    @Test
    fun rerunningAgainstAnUnchangedCacheProducesTheSameRank() {
        val library = scratchCache()
        putItem(library, 0, wearable = true)
        putItem(library, 1, wearable = true)

        val first = WornAppearanceRankTool.rank(library, targetId = 1)
        val second = WornAppearanceRankTool.rank(library, targetId = 1)

        assertEquals(first.rank, second.rank)
        assertEquals(first.itemDefinitionCount, second.itemDefinitionCount)
    }

    // ---- scratch-cache helpers ------------------------------------------------------------------

    private fun scratchCache(): CacheLibrary {
        val dir = scratch.newFolder("item-cache-${System.nanoTime()}")
        File(dir, "main_file_cache.dat2").createNewFile()
        File(dir, "main_file_cache.idx255").createNewFile()
        CacheLibrary.create(dir.absolutePath).use { library ->
            while (!library.exists(ArchiveType.ITEM.id)) {
                library.createIndex()
            }
            library.update()
        }
        return CacheLibrary(dir.absolutePath)
    }

    /** Writes a minimal real item-def opcode stream: a name, plus opcode 23/25 only if [wearable]. */
    private fun putItem(
        library: CacheLibrary,
        itemId: Int,
        wearable: Boolean,
    ) {
        val maleWornModel = if (wearable) 500 else -1
        putItem(library, itemId, maleWornModel = maleWornModel, maleWornModel2 = -1)
    }

    /** Writes a minimal real item-def opcode stream with explicit opcode 23 (`maleWornModel`) / opcode
     * 25 (`maleWornModel2`) values; -1 means the opcode is omitted entirely, exactly like a real item
     * that was never given a worn model. */
    private fun putItem(
        library: CacheLibrary,
        itemId: Int,
        maleWornModel: Int,
        maleWornModel2: Int,
    ) {
        val out = java.io.ByteArrayOutputStream()
        out.write(2) // name
        "item $itemId".toByteArray(Charsets.ISO_8859_1).let { out.write(it) }
        out.write(0)
        if (maleWornModel >= 0) {
            out.write(23)
            out.write((maleWornModel ushr 8) and 0xFF)
            out.write(maleWornModel and 0xFF)
        }
        if (maleWornModel2 >= 0) {
            out.write(25)
            out.write((maleWornModel2 ushr 8) and 0xFF)
            out.write(maleWornModel2 and 0xFF)
        }
        out.write(0) // terminator
        library.put(ArchiveType.ITEM.id, itemId ushr 8, itemId and 0xFF, out.toByteArray())
        library.update()
    }

    /** [CacheLibrary] has no built-in `Closeable`, so this mirrors every other importer tool test's own local `use`. */
    private fun <T> CacheLibrary.use(block: (CacheLibrary) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}
