package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.ArchiveType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Phase B census gate (`RSPS_CURRENT_SPRINT.json`, owner-approved unattended engineering run
 * 2026-09-04): proves [ModelNamespaceCensusTool] against real, throwaway [CacheLibrary] instances -
 * never the two real production caches, per this pipeline's own "read production caches only for
 * evidence, never as a disposable adversarial-test fixture" rule.
 */
class ModelNamespaceCensusToolTests {
    @get:Rule
    val scratch = TemporaryFolder()

    @Test
    fun physicalPresenceIsCountedAndAgreesAcrossIdenticalCaches() {
        val (game, fileServer) = pairOfCaches()
        putModel(game, 100)
        putModel(fileServer, 100)

        val report = ModelNamespaceCensusTool.census(game, fileServer, noMappingFile())

        assertEquals(1, report.gamePhysicalCount)
        assertEquals(1, report.fileServerPhysicalCount)
        assertEquals("identical caches must show zero divergence", 0, report.physicalDivergentIds.size)
        assertEquals(100, report.highestPhysicalId)
    }

    @Test
    fun physicalDivergenceBetweenTargetCachesIsDetected() {
        val (game, fileServer) = pairOfCaches()
        putModel(game, 200)
        // file-server never receives model 200 - a genuine dual-cache divergence.

        val report = ModelNamespaceCensusTool.census(game, fileServer, noMappingFile())

        assertEquals(listOf(200), report.physicalDivergentIds)
    }

    @Test
    fun itemWornModelOpcodesAreTracedAsReferences() {
        val (game, fileServer) = pairOfCaches()
        // Item 5 exists purely as an item-definition entry (no model archive write needed for the
        // reference itself to be traced) referencing model 300 as its male worn model (opcode 23)
        // and model 301 as its inventory model (opcode 1).
        putItem(game, 5, tlv(1 to 301, 23 to 300))
        putItem(fileServer, 5, tlv(1 to 301, 23 to 300))
        putModel(game, 300)
        putModel(fileServer, 300)
        putModel(game, 301)
        putModel(fileServer, 301)

        val report = ModelNamespaceCensusTool.census(game, fileServer, noMappingFile())

        assertEquals(2, report.referencedCount)
        assertEquals(1, report.referencedByRole["item_male_worn_model"])
        assertEquals(1, report.referencedByRole["item_inventory_model"])
        assertEquals(301, report.highestReferencedId)
    }

    @Test
    fun spotanimModelOpcodeIsTracedAsAReference() {
        val (game, fileServer) = pairOfCaches()
        putSpotanim(game, 9, tlv(1 to 400))
        putSpotanim(fileServer, 9, tlv(1 to 400))
        putModel(game, 400)
        putModel(fileServer, 400)

        val report = ModelNamespaceCensusTool.census(game, fileServer, noMappingFile())

        assertEquals(1, report.referencedByRole["spotanim_model"])
        assertEquals(400, report.highestReferencedId)
    }

    @Test
    fun referencedButPhysicallyMissingIsFlaggedAsAnAnomalyNotAFreeHole() {
        val (game, fileServer) = pairOfCaches()
        // Item references model 500, but model 500 is never written into either cache's index 7 -
        // an orphaned/broken reference, which real production evidence (REFERENCED_BUT_MISSING_COUNT=0
        // against the real caches) proved does not currently exist for real content.
        putItem(game, 6, tlv(23 to 500))
        putItem(fileServer, 6, tlv(23 to 500))

        val report = ModelNamespaceCensusTool.census(game, fileServer, noMappingFile())

        assertEquals(listOf(500), report.referencedButMissing)
        assertTrue("id 500 must still count toward the proven ceiling", report.provenCeiling >= 500)
    }

    @Test
    fun mappedIdsAreReadFromTheRealAssetMapYamlShape() {
        val (game, fileServer) = pairOfCaches()
        val mapFile = scratch.newFile("asset-map.yml")
        mapFile.writeText(
            """
            imports:
              - local_item_id: 22326
                name: Twisted bow
                models:
                  - role: inventory
                    local_id: 65517
                  - role: male worn
                    local_id: 65518
            """.trimIndent(),
        )

        val report = ModelNamespaceCensusTool.census(game, fileServer, mapFile)

        assertEquals(2, report.mappedCount)
        assertEquals("Twisted bow|inventory", report.mappedIds[65517])
        assertEquals("Twisted bow|male worn", report.mappedIds[65518])
        assertEquals(65518, report.highestMappedId)
    }

    @Test
    fun missingAssetMapFileYieldsNoMappingsRatherThanFailing() {
        val (game, fileServer) = pairOfCaches()
        val report = ModelNamespaceCensusTool.census(game, fileServer, File(scratch.root, "does-not-exist.yml"))
        assertEquals(0, report.mappedCount)
    }

    @Test
    fun safeFreeRangeIsExactlyAboveTheProvenCeilingAndHolesBelowItAreExcluded() {
        val (game, fileServer) = pairOfCaches()
        putModel(game, 100)
        putModel(fileServer, 100)
        // Model 50 is never written - a hole strictly below the physical ceiling (100).

        val report = ModelNamespaceCensusTool.census(game, fileServer, noMappingFile())

        assertEquals(100, report.provenCeiling)
        assertEquals(101, report.safeFreeRange.first)
        assertEquals(ModelIdAllocator.MAX_MODEL_ID, report.safeFreeRange.last)
        assertTrue("hole 50 must never be reported as free capacity", 50 !in report.safeFreeRange)
        assertTrue("hole 50 must be reported as an unverified hole instead", 50 in report.unverifiedHolesBelowCeiling)
    }

    @Test
    fun anEmptyNamespaceReportsTheEntireRangeAsSafeFree() {
        val (game, fileServer) = pairOfCaches()
        val report = ModelNamespaceCensusTool.census(game, fileServer, noMappingFile())
        assertEquals(0, report.safeFreeRange.first)
        assertEquals(ModelIdAllocator.MAX_MODEL_ID, report.safeFreeRange.last)
        assertEquals(ModelIdAllocator.MAX_MODEL_ID + 1, report.safeFreeCount)
    }

    @Test
    fun rerunningTheCensusAgainstUnchangedCachesProducesTheSameReport() {
        val (game, fileServer) = pairOfCaches()
        putModel(game, 42)
        putModel(fileServer, 42)

        val first = ModelNamespaceCensusTool.census(game, fileServer, noMappingFile())
        val second = ModelNamespaceCensusTool.census(game, fileServer, noMappingFile())

        assertEquals(first.provenCeiling, second.provenCeiling)
        assertEquals(first.safeFreeRange, second.safeFreeRange)
        assertEquals(first.gamePhysicalCount, second.gamePhysicalCount)
    }

    @Test
    fun theCensusNeverWritesToEitherScratchCache() {
        val (game, fileServer) = pairOfCaches()
        putModel(game, 7)
        putModel(fileServer, 7)
        val beforeGame = File(game, "main_file_cache.dat2").readBytes()
        val beforeFileServer = File(fileServer, "main_file_cache.dat2").readBytes()

        ModelNamespaceCensusTool.census(game, fileServer, noMappingFile())

        assertTrue(
            "census must be strictly read-only",
            beforeGame.contentEquals(File(game, "main_file_cache.dat2").readBytes()) &&
                beforeFileServer.contentEquals(File(fileServer, "main_file_cache.dat2").readBytes()),
        )
    }

    // ---- scratch-cache helpers ------------------------------------------------------------------

    private fun pairOfCaches(): Pair<String, String> = scratchCache("game-cache") to scratchCache("file-server-cache")

    private fun scratchCache(name: String): String {
        val dir = scratch.newFolder(name)
        File(dir, "main_file_cache.dat2").createNewFile()
        File(dir, "main_file_cache.idx255").createNewFile()
        CacheLibrary.create(dir.absolutePath).use { library ->
            // Grow the index list up through SPOTANIM (21), the highest index this census reads,
            // exactly as CacheTransactionTests does for ITEM.
            while (!library.exists(ArchiveType.SPOTANIM.id)) {
                library.createIndex()
            }
            library.update()
        }
        return dir.absolutePath
    }

    private fun putModel(
        cachePath: String,
        modelId: Int,
        bytes: ByteArray = byteArrayOf(1, 2, 3),
    ) = CacheLibrary(cachePath).use {
        it.put(ModelNamespaceCensusTool.MODEL_INDEX, modelId, 0, bytes)
        it.update()
    }

    private fun putItem(
        cachePath: String,
        itemId: Int,
        bytes: ByteArray,
    ) = CacheLibrary(cachePath).use {
        it.put(ArchiveType.ITEM.id, itemId ushr 8, itemId and 0xFF, bytes)
        it.update()
    }

    private fun putSpotanim(
        cachePath: String,
        spotanimId: Int,
        bytes: ByteArray,
    ) = CacheLibrary(cachePath).use {
        it.put(ArchiveType.SPOTANIM.id, spotanimId ushr 8, spotanimId and 0xFF, bytes)
        it.update()
    }

    private fun noMappingFile(): File = File(scratch.root, "no-such-asset-map.yml")

    /** [CacheLibrary] has no built-in `Closeable`, so this mirrors CacheTransactionTests' own local `use`. */
    private fun <T> CacheLibrary.use(block: (CacheLibrary) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }

    /** A minimal opcode/2-byte-value TLV stream, matching the real item/spotanim opcode format exactly. */
    private fun tlv(vararg entries: Pair<Int, Int>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        entries.forEach { (opcode, value) ->
            out.write(opcode)
            out.write((value ushr 8) and 0xFF)
            out.write(value and 0xFF)
        }
        out.write(0)
        return out.toByteArray()
    }
}
