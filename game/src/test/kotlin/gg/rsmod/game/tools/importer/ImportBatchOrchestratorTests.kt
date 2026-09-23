package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.ArchiveType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Phase C orchestrator gate (`RSPS_CURRENT_SPRINT.json`, owner-approved unattended engineering run
 * 2026-09-04): proves [ImportBatchOrchestrator] against real, throwaway [CacheLibrary] instances and
 * a real, throwaway asset-map file - never the two real production caches or the real
 * `OSRS_IMPORT_MASTER.yml`, per this pipeline's own "never use production state as an adversarial
 * test fixture" rule (see [CacheTransactionTests], [ModelNamespaceCensusToolTests]).
 *
 * Covers the required minimum adversarial scenarios from the governing run instruction's Section 10
 * that are actually [ImportBatchOrchestrator]'s own responsibility rather than already proven by
 * [CacheTransactionTests] (single/multi-model success, existing-mapping reuse, rerun idempotency,
 * intra-batch collision prevention, exactly-one-id-remaining, allocator exhaustion, unexpected
 * content at a mapped/candidate id, crash-after-verify-before-mapping-commit and recovery from it,
 * recovery idempotency, and final convergence after rerun).
 */
class ImportBatchOrchestratorTests {
    @get:Rule
    val scratch = TemporaryFolder()

    // ---- plan(): pure, no I/O ---------------------------------------------------------------

    @Test
    fun distinctModelsWithinOneBatchNeverCollideOnTheSameCandidateId() {
        val batch =
            listOf(
                item("upstream_item:1", "Item A", listOf(model("upstream_model:10", "inventory"))),
                item("upstream_item:2", "Item B", listOf(model("upstream_model:20", "inventory"))),
            )
        val plan = ImportBatchOrchestrator.plan(FAKE_TARGETS, batch, EMPTY_MAPPING, nextFreeItemId = 100, modelSafeFreeStart = 500, modelSafeFreeEnd = 510)

        val modelIds = plan.items.flatMap { it.models }.map { it.localId }
        assertEquals("both new models must get distinct ids", 2, modelIds.toSet().size)
        assertEquals(listOf(500, 501), modelIds.sorted())
        assertEquals("items must get distinct, contiguous ids starting at nextFreeItemId", listOf(100, 101), plan.items.map { it.localId }.sorted())
    }

    @Test
    fun exactlyOneRemainingModelIdAllowsExactlyOneNewModelAndThenExhausts() {
        val batch = listOf(item("upstream_item:1", "Item A", listOf(model("upstream_model:10", "inventory"))))
        val plan = ImportBatchOrchestrator.plan(FAKE_TARGETS, batch, EMPTY_MAPPING, nextFreeItemId = 1, modelSafeFreeStart = 900, modelSafeFreeEnd = 900)
        assertEquals(900, plan.items.single().models.single().localId)

        val secondModelInSameBatch =
            listOf(item("upstream_item:1", "Item A", listOf(model("upstream_model:10", "inventory"), model("upstream_model:11", "male worn"))))
        val exhaustion =
            runCatching {
                ImportBatchOrchestrator.plan(FAKE_TARGETS, secondModelInSameBatch, EMPTY_MAPPING, nextFreeItemId = 1, modelSafeFreeStart = 900, modelSafeFreeEnd = 900)
            }.exceptionOrNull()
        assertTrue("a genuine capacity blocker must fail loudly, not silently widen the range: $exhaustion", exhaustion is IllegalStateException)
    }

    @Test
    fun existingMappingIsReusedNotReallocated() {
        val existing =
            ImportBatchOrchestrator.ExistingMapping(
                itemLocalIdBySourceIdentity = mapOf("upstream_item:1" to 22326),
                modelLocalIdByMappingKey = mapOf(ModelIdAllocator.mappingKey("upstream_model:10", "inventory") to 65517),
                mappedItemSourceIdentities = setOf("upstream_item:1"),
            )
        val batch = listOf(item("upstream_item:1", "Item A", listOf(model("upstream_model:10", "inventory"))))
        val plan = ImportBatchOrchestrator.plan(FAKE_TARGETS, batch, existing, nextFreeItemId = 999, modelSafeFreeStart = 900, modelSafeFreeEnd = 910)

        assertEquals("an already-mapped item must keep its existing id, never a fresh one", 22326, plan.items.single().localId)
        assertEquals("an already-mapped model must keep its existing id", 65517, plan.items.single().models.single().localId)
    }

    @Test
    fun aMeshSharedByTwoItemsOfOneBatchIsAllocatedAndWrittenOnce() {
        val batch =
            listOf(
                item("upstream_item:1", "Avernic defender", listOf(model("upstream_model:10", "model"), model("upstream_model:11", "model"))),
                item("upstream_item:2", "Avernic defender (broken)", listOf(model("upstream_model:10", "model"))),
            )
        val plan =
            ImportBatchOrchestrator.plan(FAKE_TARGETS, batch, EMPTY_MAPPING, nextFreeItemId = 100, modelSafeFreeStart = 0, modelSafeFreeEnd = 0, modelCandidates = listOf(40000, 40007, 65530))

        assertEquals(listOf(40000, 40007), plan.items[0].models.map { it.localId })
        assertEquals("the second item must reference the already-allocated mesh", listOf(40000), plan.items[1].models.map { it.localId })
        val modelWrites = plan.transaction.mutations.filter { it.indexId == ModelConvertTool.MODEL_INDEX }.map { it.groupId }
        assertEquals("each distinct mesh is written exactly once", listOf(40000, 40007), modelWrites)
    }

    @Test
    fun candidateAllocationIsExhaustionSafeAndNeverFallsBackToTheRange() {
        val batch = listOf(item("upstream_item:1", "Item A", listOf(model("upstream_model:10", "model"), model("upstream_model:11", "model"))))
        val exhaustion =
            runCatching {
                ImportBatchOrchestrator.plan(FAKE_TARGETS, batch, EMPTY_MAPPING, nextFreeItemId = 1, modelSafeFreeStart = 900, modelSafeFreeEnd = 910, modelCandidates = listOf(40000))
            }.exceptionOrNull()
        assertTrue("one candidate cannot hold two meshes: $exhaustion", exhaustion is IllegalStateException)
    }

    @Test
    fun anEmptyBatchAndDuplicateSourceIdentitiesAreRefused() {
        val empty = runCatching { ImportBatchOrchestrator.plan(FAKE_TARGETS, emptyList(), EMPTY_MAPPING, 1, 900, 910) }.exceptionOrNull()
        assertTrue(empty is IllegalArgumentException)

        val dup =
            listOf(
                item("upstream_item:1", "Item A", emptyList()),
                item("upstream_item:1", "Item A again", emptyList()),
            )
        val duplicate = runCatching { ImportBatchOrchestrator.plan(FAKE_TARGETS, dup, EMPTY_MAPPING, 1, 900, 910) }.exceptionOrNull()
        assertTrue(duplicate is IllegalArgumentException)
    }

    // ---- finalizeDurableMapping(): idempotent text append ------------------------------------

    @Test
    fun finalizingAppendsExactlyOnceAndSkipsAlreadyMappedIdentities() {
        val assetMap = scratch.newFile("asset-map.yml")
        assetMap.writeText("imports:\n  - local_item_id: 1\n    name: Existing\n    upstream_item_id: 999\n")
        val newItem = ImportBatchOrchestrator.MappedItem("upstream_item:5", "New Item", 200, listOf(ImportBatchOrchestrator.MappedModel("upstream_model:50", "inventory", 900)))
        val alreadyMapped = ImportBatchOrchestrator.MappedItem("upstream_item:999", "Existing", 1, emptyList())
        val existing = ImportBatchOrchestrator.ExistingMapping(emptyMap(), emptyMap(), setOf("upstream_item:999"))

        val appended = ImportBatchOrchestrator.finalizeDurableMapping(assetMap, listOf(alreadyMapped, newItem), existing)

        assertEquals("only the genuinely-new item should be appended", listOf(newItem), appended)
        val reread = ImportBatchOrchestrator.readExistingMapping(assetMap)
        assertEquals(200, reread.itemLocalIdBySourceIdentity["upstream_item:5"])
        assertEquals(900, reread.modelLocalIdByMappingKey[ModelIdAllocator.mappingKey("upstream_model:50", "inventory")])
        assertEquals("the original hand-authored entry must still be present, untouched", 1, reread.itemLocalIdBySourceIdentity["upstream_item:999"])

        // Calling again with the same item (now genuinely already mapped) must be a true no-op.
        val second = ImportBatchOrchestrator.finalizeDurableMapping(assetMap, listOf(newItem), reread)
        assertEquals(emptyList<ImportBatchOrchestrator.MappedItem>(), second)
    }

    // ---- orchestrate(): full lifecycle against real scratch caches ---------------------------

    @Test
    fun aFreshBatchAppliesToBothCachesAndFinalizesTheMapping() {
        val (game, fileServer) = pairOfCaches()
        val assetMap = emptyAssetMap()

        val result =
            ImportBatchOrchestrator.orchestrate(
                targets = listOf(game, fileServer),
                batch = listOf(item("upstream_item:1", "Rune dagger", listOf(model("upstream_model:10", "inventory")))),
                assetMapFile = assetMap,
                modelSafeFreeStart = 500,
                modelSafeFreeEnd = 510,
                apply = true,
            )

        assertEquals(1, result.newlyFinalized.size)
        checkNotNull(result.applyResult)
        assertEquals("one model write + one item write, on each of two targets = 4", 4, result.applyResult!!.applied)

        val mapping = ImportBatchOrchestrator.readExistingMapping(assetMap)
        val itemId = mapping.itemLocalIdBySourceIdentity.getValue("upstream_item:1")
        val modelId = mapping.modelLocalIdByMappingKey.getValue(ModelIdAllocator.mappingKey("upstream_model:10", "inventory"))
        listOf(game, fileServer).forEach { target ->
            val library = CacheLibrary(target)
            try {
                assertTrue("model must decode as a real rev-667 mesh on $target", runCatching { Rev667ModelDecoder.decode(library.data(ModelConvertTool.MODEL_INDEX, modelId)!!) }.isSuccess)
                assertTrue("item bytes must be present on $target", library.data(ArchiveType.ITEM.id, itemId ushr 8, itemId and 0xFF) != null)
            } finally {
                library.close()
            }
        }
    }

    @Test
    fun rerunningTheExactSameBatchIsIdempotent() {
        val (game, fileServer) = pairOfCaches()
        val assetMap = emptyAssetMap()
        fun batch() = listOf(item("upstream_item:1", "Rune dagger", listOf(model("upstream_model:10", "inventory"))))

        val first = ImportBatchOrchestrator.orchestrate(listOf(game, fileServer), batch(), assetMap, 500, 510, apply = true)
        assertEquals(1, first.newlyFinalized.size)
        val mappingAfterFirst = ImportBatchOrchestrator.readExistingMapping(assetMap)

        val second = ImportBatchOrchestrator.orchestrate(listOf(game, fileServer), batch(), assetMap, 500, 510, apply = true)

        assertEquals("a rerun must finalize nothing new", 0, second.newlyFinalized.size)
        assertEquals("a rerun must recover nothing (the first run finished cleanly)", 0, second.recoveredAndFinalized.size)
        assertEquals("a rerun must write zero bytes - every location already NO_OPs", 0, second.applyResult!!.applied)
        val mappingAfterSecond = ImportBatchOrchestrator.readExistingMapping(assetMap)
        assertEquals("no new ids and no duplicates on rerun", mappingAfterFirst.itemLocalIdBySourceIdentity, mappingAfterSecond.itemLocalIdBySourceIdentity)
        assertEquals(mappingAfterFirst.modelLocalIdByMappingKey, mappingAfterSecond.modelLocalIdByMappingKey)
    }

    /**
     * The real first production batch (Berserker ring (i)/Warrior ring (i), 2026-09-04) targets
     * pre-existing rev-667 item ids that already carry real content - not a fresh id like every other
     * test above. [ItemSource.expectedCurrentItemSha1] is what makes that a legitimate REPLACE instead
     * of an always-refused CONFLICT.
     */
    @Test
    fun anItemSourceWithAPinnedExpectedShaReplacesExistingContentInPlace() {
        val (game, fileServer) = pairOfCaches()
        val assetMap = emptyAssetMap()
        val existingBytes = "OLD_STUB_BYTES".toByteArray()
        listOf(game, fileServer).forEach { target ->
            CacheLibrary(target).use { it.put(ArchiveType.ITEM.id, 0, 42, existingBytes); it.update() }
        }
        // Pre-seed the id this batch must reuse, exactly as readExistingMapping would for an item
        // already recorded elsewhere in the durable mapping - without this the plan would try 100.
        val existing = ImportBatchOrchestrator.ExistingMapping(mapOf("upstream_item:1" to 42), emptyMap(), emptySet())
        val batch =
            listOf(
                ImportBatchOrchestrator.ItemSource(
                    sourceIdentity = "upstream_item:1",
                    name = "Existing stub",
                    models = listOf(model("upstream_model:10", "inventory")),
                    buildItemBytes = { "NEW_BYTES".toByteArray() },
                    expectedCurrentItemSha1 = CacheItemProbeTool.sha1(existingBytes),
                ),
            )

        val plan = ImportBatchOrchestrator.plan(listOf(game, fileServer), batch, existing, nextFreeItemId = 100, modelSafeFreeStart = 500, modelSafeFreeEnd = 510)
        val preflight = plan.transaction.preflight()
        assertEquals("a pinned expectedCurrentSha1 must classify as REPLACE, not CONFLICT", emptyList<String>(), plan.transaction.blockingErrors(preflight))

        plan.transaction.apply(preflight)
        assertEquals(emptyList<String>(), plan.transaction.verify())
        listOf(game, fileServer).forEach { target ->
            CacheLibrary(target).use { assertArrayEquals("NEW_BYTES".toByteArray(), it.data(ArchiveType.ITEM.id, 0, 42)) }
        }
    }

    /** Without the pin, the same in-place edit must still refuse rather than silently overwrite. */
    @Test
    fun anItemSourceWithNoPinnedShaRefusesToOverwriteExistingContent() {
        val (game, fileServer) = pairOfCaches()
        listOf(game, fileServer).forEach { target ->
            CacheLibrary(target).use { it.put(ArchiveType.ITEM.id, 0, 42, "OLD_STUB_BYTES".toByteArray()); it.update() }
        }
        val existing = ImportBatchOrchestrator.ExistingMapping(mapOf("upstream_item:1" to 42), emptyMap(), emptySet())
        val batch =
            listOf(
                ImportBatchOrchestrator.ItemSource(
                    sourceIdentity = "upstream_item:1",
                    name = "Existing stub",
                    models = emptyList(),
                    buildItemBytes = { "NEW_BYTES".toByteArray() },
                ),
            )

        val plan = ImportBatchOrchestrator.plan(listOf(game, fileServer), batch, existing, nextFreeItemId = 100, modelSafeFreeStart = 500, modelSafeFreeEnd = 510)
        val preflight = plan.transaction.preflight()
        val errors = plan.transaction.blockingErrors(preflight)
        assertTrue("an un-pinned edit against real existing content must still be refused as CONFLICT: $errors", errors.any { it.contains("CONFLICT") })
    }

    @Test
    fun anUndecodableModelBlocksTheWholeBatchAndFinalizesNothing() {
        val (game, fileServer) = pairOfCaches()
        val assetMap = emptyAssetMap()
        val batch = listOf(item("upstream_item:1", "Broken", listOf(ImportBatchOrchestrator.ModelSource("upstream_model:10", "inventory", byteArrayOf(1, 2, 3)))))

        val failure = runCatching { ImportBatchOrchestrator.orchestrate(listOf(game, fileServer), batch, assetMap, 500, 510, apply = true) }.exceptionOrNull()

        assertTrue("junk model bytes must fail loudly instead of landing", failure != null && failure.message!!.contains("MODEL_UNDECODABLE"))
        assertEquals("a rolled-back batch must not appear in the durable mapping", ImportBatchOrchestrator.ExistingMapping(emptyMap(), emptyMap(), emptySet()), ImportBatchOrchestrator.readExistingMapping(assetMap))
    }

    @Test
    fun unexpectedContentAtAFreshCandidateModelIdBlocksTheBatchBeforeAnyWrite() {
        // Item ids cannot exhibit this scenario by construction - nextFreeItemId() is always the
        // first hole, which by definition holds no data yet. Models can: ModelIdAllocator picks the
        // next candidate in the proven-safe range purely arithmetically, without consulting cache
        // content, so a namespace census that is even slightly stale (or a range mis-set by a
        // caller) can hand out a candidate that is already, unexpectedly, occupied - identically on
        // both targets, so this is not merely the divergence CacheTransaction's own preflight would
        // otherwise not need to explain.
        val (game, fileServer) = pairOfCaches()
        val assetMap = emptyAssetMap()
        listOf(game, fileServer).forEach { target ->
            CacheLibrary(target).use { it.put(ModelConvertTool.MODEL_INDEX, 500, 0, "SOMETHING ELSE".toByteArray()); it.update() }
        }

        val batch = listOf(item("upstream_item:1", "New item", listOf(model("upstream_model:10", "inventory"))))
        val failure = runCatching { ImportBatchOrchestrator.orchestrate(listOf(game, fileServer), batch, assetMap, 500, 510, apply = true) }.exceptionOrNull()

        assertTrue("unexplained existing content at a supposedly-fresh candidate id must block, not overwrite: $failure", failure != null && failure.message!!.contains("CONFLICT"))
        assertEquals("a blocked-at-preflight batch must never reach apply()", ImportBatchOrchestrator.ExistingMapping(emptyMap(), emptyMap(), emptySet()), ImportBatchOrchestrator.readExistingMapping(assetMap))
        CacheLibrary(fileServer).use {
            assertArrayEquals(
                "the pre-existing content must never be touched by a blocked batch",
                "SOMETHING ELSE".toByteArray(),
                it.data(ModelConvertTool.MODEL_INDEX, 500, 0),
            )
        }
    }

    @Test
    fun crashAfterVerifyBeforeMappingCommitRecoversTheExactSameIds() {
        val (game, fileServer) = pairOfCaches()
        val assetMap = emptyAssetMap()
        val journalRoot = scratch.newFolder("journal-crash-window")
        val batch = listOf(item("upstream_item:1", "Rune dagger", listOf(model("upstream_model:10", "inventory"))))

        // Simulate the crash window: plan + apply + verify succeed (exactly what a real
        // CacheTransaction guarantees), but the process is killed before finalizeDurableMapping runs.
        val existing = ImportBatchOrchestrator.readExistingMapping(assetMap)
        val plan = ImportBatchOrchestrator.plan(listOf(game, fileServer), batch, existing, ImportBatchOrchestrator.nextFreeItemId(listOf(game, fileServer)), 500, 510, journalRoot)
        val appliedPlan = plan.transaction.preflight()
        plan.transaction.apply(appliedPlan)
        assertEquals(emptyList<String>(), plan.transaction.verify())
        // No finalizeDurableMapping call here - this is the crash.

        val recovered = ImportBatchOrchestrator.recoverUnfinalized(listOf(game, fileServer), emptySet(), journalRoot)

        assertEquals(1, recovered.size)
        assertEquals("upstream_item:1", recovered.single().sourceIdentity)
        assertEquals("recovery must report the SAME id the crashed run actually wrote, never a freshly re-allocated one", plan.items.single().localId, recovered.single().localId)
        assertEquals(plan.items.single().models.single().localId, recovered.single().models.single().localId)

        ImportBatchOrchestrator.finalizeDurableMapping(assetMap, recovered, existing)
        val mappingAfterRecovery = ImportBatchOrchestrator.readExistingMapping(assetMap)
        assertEquals(plan.items.single().localId, mappingAfterRecovery.itemLocalIdBySourceIdentity["upstream_item:1"])

        // Final convergence: a fresh plan() for the identical batch must now reuse the recovered id
        // rather than allocate a second one for content that already exists.
        val rerunPlan = ImportBatchOrchestrator.plan(listOf(game, fileServer), batch, mappingAfterRecovery, ImportBatchOrchestrator.nextFreeItemId(listOf(game, fileServer)), 500, 510)
        assertEquals(plan.items.single().localId, rerunPlan.items.single().localId)
        assertEquals(0, rerunPlan.transaction.preflight().count { it.outcome != MutationOutcome.NO_OP })
    }

    @Test
    fun recoveryIgnoresAnAlreadyFinalizedIdentityEvenThoughItsJournalEvidenceStillExistsOnDisk() {
        val (game, fileServer) = pairOfCaches()
        val journalRoot = scratch.newFolder("journal-already-finalized")
        val batch = listOf(item("upstream_item:1", "Rune dagger", listOf(model("upstream_model:10", "inventory"))))

        val plan = ImportBatchOrchestrator.plan(listOf(game, fileServer), batch, EMPTY_MAPPING, 1, 500, 510, journalRoot)
        plan.transaction.apply()
        assertEquals(emptyList<String>(), plan.transaction.verify())

        val recovered = ImportBatchOrchestrator.recoverUnfinalized(listOf(game, fileServer), setOf("upstream_item:1"), journalRoot)
        assertEquals("an identity the caller already knows is durably mapped must never be re-recovered", emptyList<ImportBatchOrchestrator.MappedItem>(), recovered)
    }

    @Test
    fun aTransactionThatNeverAppliedLeavesNothingToRecover() {
        val (game, fileServer) = pairOfCaches()
        val journalRoot = scratch.newFolder("journal-never-applied")
        val batch = listOf(item("upstream_item:1", "Rune dagger", listOf(model("upstream_model:10", "inventory"))))

        // preflight() only reads; apply() is what writes plan.txt into the journal. Never calling it
        // must leave the journal directory empty of evidence, not merely empty of committed bytes.
        ImportBatchOrchestrator.plan(listOf(game, fileServer), batch, EMPTY_MAPPING, 1, 500, 510, journalRoot).transaction.preflight()

        assertEquals(emptyList<ImportBatchOrchestrator.MappedItem>(), ImportBatchOrchestrator.recoverUnfinalized(listOf(game, fileServer), emptySet(), journalRoot))
    }

    // ---- fixtures --------------------------------------------------------------------------

    private fun item(
        sourceIdentity: String,
        name: String,
        models: List<ImportBatchOrchestrator.ModelSource>,
    ) = ImportBatchOrchestrator.ItemSource(sourceIdentity, name, models) { "ITEM_BYTES_FOR_$sourceIdentity".toByteArray() }

    private fun model(
        sourceIdentity: String,
        role: String,
    ) = ImportBatchOrchestrator.ModelSource(sourceIdentity, role, validModelBytes())

    /** A minimal but genuinely-decodable rev-667 mesh, so decode-verification tests exercise the real reader. */
    private fun validModelBytes(): ByteArray {
        val model = ModelData(vertexCount = 3, faceCount = 1, texSpaceCount = 0)
        model.vertexX[1] = 10
        model.vertexY[2] = 10
        model.faceA[0] = 0
        model.faceB[0] = 1
        model.faceC[0] = 2
        model.faceColour[0] = 1
        return Rev667ModelEncoder.encode(model)
    }

    private fun pairOfCaches(): Pair<String, String> = scratchCache("game-cache") to scratchCache("file-server-cache")

    private fun scratchCache(name: String): String {
        val dir = scratch.newFolder(name)
        File(dir, "main_file_cache.dat2").createNewFile()
        File(dir, "main_file_cache.idx255").createNewFile()
        CacheLibrary.create(dir.absolutePath).use { library ->
            while (!library.exists(ArchiveType.SPOTANIM.id)) {
                library.createIndex()
            }
            library.update()
        }
        return dir.absolutePath
    }

    private fun emptyAssetMap(): File {
        val file = scratch.newFile("asset-map-${scratch.root.list()?.size}.yml")
        // A block-style empty list, not `imports: []` - a later plain-text block-sequence append
        // (see finalizeDurableMapping) is only valid YAML following the block style, never the flow one.
        file.writeText("imports:\n")
        return file
    }

    private fun <T> CacheLibrary.use(block: (CacheLibrary) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }

    private companion object {
        val FAKE_TARGETS = listOf("target-a", "target-b")
        val EMPTY_MAPPING = ImportBatchOrchestrator.ExistingMapping(emptyMap(), emptyMap(), emptySet())
    }

    init {
        assertNotEquals("", CacheTransaction.DEFAULT_JOURNAL_ROOT)
    }
}
