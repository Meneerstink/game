package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File

/**
 * Lifecycle coordinator for a multi-item, multi-model import batch (`RSPS_CURRENT_SPRINT.json`
 * Phase C, owner-approved unattended engineering run 2026-09-04).
 *
 * This is deliberately **not** a second transaction system. Every real cache write still goes
 * through the existing, proven [CacheTransaction] (gate A7: PREFLIGHT -> JOURNAL -> APPLY -> VERIFY
 * -> ROLLBACK, closed, not redesigned here), and every model id still comes from the existing,
 * proven [ModelIdAllocator] (existing-mapping-first, deterministic, exhaustion-safe, unchanged
 * here). The responsibility split is exact:
 *
 *  - [ModelIdAllocator] decides WHERE a new model asset goes.
 *  - [CacheTransaction] decides HOW a write actually lands, safely, on both real caches.
 *  - [ImportBatchOrchestrator] only sequences those two proven pieces around a batch of items and
 *    models, and owns exactly one problem neither of them owns: keeping the durable
 *    `OSRS_IMPORT_MASTER.yml` mapping in lockstep with what the caches actually hold, even
 *    across a crash.
 *
 * ## The crash window
 *
 * [CacheTransaction] already proves the cache pair itself can never be left half-written - `apply()`
 * either commits every target or rolls every committed target back. What it does not (and should
 * not) know about is the durable mapping file that turns "these bytes are at this id" into "item X's
 * inventory model is permanently at this id". If the process is killed strictly after `apply()` +
 * `verify()` succeed but before [finalizeDurableMapping] runs, a naive rerun has no way to tell
 * "this batch already landed, reuse its ids" from "this batch never ran, allocate fresh ids" - it
 * would either leak a second set of ids for the same content, or (worse) see the already-correct
 * cache state as an unexplained conflict and refuse to proceed.
 *
 * The fix adds no second commit log. [CacheTransaction.apply] already writes a durable pre-write
 * plan record (`journalDir/plan.txt`) for every transaction, before a single byte is written, naming
 * every (target, location, intended sha1) triple. [recoverUnfinalized] uses exactly that existing
 * evidence: for each planned mutation this project's own [itemLabel]/[modelLabel] convention marks
 * as an import, it re-reads the *live* cache content at that location on every real target right now
 * and compares its sha1 to what the journal recorded as intended. Only when every target's current
 * content matches is the mutation treated as durably landed and folded into the mapping file - never
 * from trusted in-memory state, never by re-running [plan] (which could compute different ids than
 * what was actually written, if capacity or the item-id ceiling moved since the crash).
 */
object ImportBatchOrchestrator {
    // ---- input DTOs --------------------------------------------------------------------------

    /** One model asset a batch item needs written into index 7. */
    data class ModelSource(
        val sourceIdentity: String,
        val role: String,
        val bytes: ByteArray,
    ) {
        init {
            require('|' !in sourceIdentity && '|' !in role) {
                "sourceIdentity/role must not contain '|' (used as the journal-label delimiter): '$sourceIdentity' / '$role'"
            }
        }
    }

    /**
     * One item this batch wants to write. [buildItemBytes] is deferred rather than precomputed
     * because the item definition's own worn/inventory model opcodes must point at whatever local
     * ids [models] actually resolve to - which [plan] only knows once it has consulted
     * [ModelIdAllocator] for every model in this item.
     *
     * [expectedCurrentItemSha1] distinguishes this batch's two real shapes: null (the default) is
     * "this id is expected to be empty" - a genuinely new item, exactly what every batch up to and
     * including the first real production run used. A non-null value is "this id already holds real
     * content, and this batch is only replacing it in place" - e.g. an already-existing rev-667
     * cache stub (real name/bonuses/camera params already present, only the model opcode is being
     * pointed at a newly-imported real model) rather than a brand-new id. Without this,
     * [CacheMutation.item]'s own [MutationOutcome.classify] would always see occupied-but-different
     * content at that id as CONFLICT and [CacheTransaction.blockingErrors] would refuse to apply -
     * correctly so, since "never silently overwrite unknown content" is exactly the point; pinning
     * the expected current bytes is what turns that refusal into a legitimate, explicit REPLACE.
     */
    data class ItemSource(
        val sourceIdentity: String,
        val name: String,
        val models: List<ModelSource>,
        val expectedCurrentItemSha1: String? = null,
        // Kept last so existing/future callers can keep using Kotlin's trailing-lambda call syntax.
        val buildItemBytes: (modelLocalIdsBySourceIdentity: Map<String, Int>) -> ByteArray,
    ) {
        init {
            require('|' !in sourceIdentity && '|' !in name) {
                "sourceIdentity/name must not contain '|' (used as the journal-label delimiter): '$sourceIdentity' / '$name'"
            }
        }
    }

    // ---- planning output ----------------------------------------------------------------------

    data class PlannedModel(val source: ModelSource, val localId: Int)

    data class PlannedItem(val source: ItemSource, val localId: Int, val bytes: ByteArray, val models: List<PlannedModel>)

    class BatchPlan(val transaction: CacheTransaction, val items: List<PlannedItem>)

    // ---- durable-mapping DTOs (shared by the direct path and crash recovery) ------------------

    data class MappedModel(val sourceIdentity: String, val role: String, val localId: Int)

    data class MappedItem(val sourceIdentity: String, val name: String, val localId: Int, val models: List<MappedModel>)

    /** Everything [plan] and [finalizeDurableMapping] need to know already exists, read once from disk. */
    data class ExistingMapping(
        val itemLocalIdBySourceIdentity: Map<String, Int>,
        val modelLocalIdByMappingKey: Map<String, Int>,
        val mappedItemSourceIdentities: Set<String>,
    )

    // ---- plan: pure, no I/O other than what CacheTransaction itself will later perform ---------

    /**
     * Decides every id this batch needs and builds the single combined [CacheTransaction] that will
     * write them all - item definitions and models together, one journal, one apply, one verify,
     * exactly the mixed-mutation-list capability [CacheTransaction.mutations] already supports.
     * Writes nothing; [CacheTransaction.apply] is a separate, explicit later step.
     *
     * @param nextFreeItemId the strictly-contiguous next item id (`maxContiguousItemId(targets) + 1`,
     *   see [CacheItemProbeTool]) - items never use hole-reuse, unlike models.
     * @param modelSafeFreeStart/[modelSafeFreeEnd] a **freshly-computed** [ModelNamespaceCensusTool]
     *   `safeFreeRange`, never a cached or assumed value.
     */
    fun plan(
        targets: List<String>,
        batch: List<ItemSource>,
        existing: ExistingMapping,
        nextFreeItemId: Int,
        modelSafeFreeStart: Int,
        modelSafeFreeEnd: Int,
        journalRoot: File = File(CacheTransaction.DEFAULT_JOURNAL_ROOT),
        transactionId: String = CacheTransaction.newTransactionId(),
        modelCandidates: List<Int>? = null,
    ): BatchPlan {
        require(batch.isNotEmpty()) { "an empty batch has nothing to plan." }
        val identities = batch.map { it.sourceIdentity }
        require(identities.size == identities.toSet().size) { "duplicate sourceIdentity within one batch: $identities" }

        val batchReservedModelIds = mutableSetOf<Int>()
        // One upstream mesh shared by several items of the same batch (e.g. an item and its broken
        // variant) is allocated and written once; every later item reuses that id.
        val batchModelIds = mutableMapOf<String, Int>()
        val emittedModelKeys = mutableSetOf<String>()
        var freeItemId = nextFreeItemId
        val plannedItems = mutableListOf<PlannedItem>()
        val mutations = mutableListOf<CacheMutation>()

        for (item in batch) {
            val plannedModels =
                item.models.map { model ->
                    val key = ModelIdAllocator.mappingKey(model.sourceIdentity, model.role)
                    val localId =
                        batchModelIds.getOrPut(key) {
                            if (modelCandidates != null) {
                                ModelIdAllocator.resolveFromCandidates(
                                    sourceIdentity = model.sourceIdentity,
                                    role = model.role,
                                    existingMapping = existing.modelLocalIdByMappingKey,
                                    provenFreeCandidates = modelCandidates,
                                    batchReserved = batchReservedModelIds,
                                )
                            } else {
                                ModelIdAllocator.resolve(
                                    sourceIdentity = model.sourceIdentity,
                                    role = model.role,
                                    existingMapping = existing.modelLocalIdByMappingKey,
                                    provenSafeFreeStart = modelSafeFreeStart,
                                    provenSafeFreeEnd = modelSafeFreeEnd,
                                    batchReserved = batchReservedModelIds,
                                )
                            }
                        }
                    batchReservedModelIds += localId
                    PlannedModel(model, localId)
                }

            val itemLocalId = existing.itemLocalIdBySourceIdentity[item.sourceIdentity] ?: freeItemId.also { freeItemId++ }
            val modelLocalIdsBySourceIdentity = plannedModels.associate { it.source.sourceIdentity to it.localId }
            val itemBytes = item.buildItemBytes(modelLocalIdsBySourceIdentity)

            plannedModels.forEach { pm ->
                val key = ModelIdAllocator.mappingKey(pm.source.sourceIdentity, pm.source.role)
                if (!emittedModelKeys.add(key)) return@forEach
                mutations +=
                    CacheMutation(
                        indexId = ModelConvertTool.MODEL_INDEX,
                        groupId = pm.localId,
                        fileId = 0,
                        newBytes = pm.source.bytes,
                        label = modelLabel(item.sourceIdentity, pm.source),
                    )
            }
            mutations +=
                CacheMutation.item(
                    itemId = itemLocalId,
                    newBytes = itemBytes,
                    label = itemLabel(item),
                    expectedCurrentSha1 = item.expectedCurrentItemSha1,
                )

            plannedItems += PlannedItem(item, itemLocalId, itemBytes, plannedModels)
        }

        return BatchPlan(CacheTransaction(targets, mutations, journalRoot, transactionId), plannedItems)
    }

    fun toMappedItems(plan: BatchPlan): List<MappedItem> =
        plan.items.map { pi ->
            MappedItem(
                sourceIdentity = pi.source.sourceIdentity,
                name = pi.source.name,
                localId = pi.localId,
                models = pi.models.map { MappedModel(it.source.sourceIdentity, it.source.role, it.localId) },
            )
        }

    /** `maxContiguousItemId(targets) + 1`; refuses to guess if the real targets already disagree. */
    fun nextFreeItemId(targets: List<String>): Int {
        val perTarget =
            targets.map { path ->
                val library = CacheLibrary(path)
                try {
                    CacheItemProbeTool.maxContiguousItemId(library)
                } finally {
                    library.close()
                }
            }
        check(perTarget.toSet().size == 1) {
            "Targets disagree on max contiguous item id ($perTarget for $targets) - resolve this " +
                "divergence before planning a new batch; guessing which one is correct is not safe."
        }
        return perTarget.first() + 1
    }

    // ---- durable mapping: read ------------------------------------------------------------------

    /**
     * Reads `imports:` from [assetMapFile] using the exact same shape [ModelNamespaceCensusTool]
     * already reads, keyed by the `upstream_item:<id>` / `upstream_model:<id>` [ModelIdAllocator]
     * sourceIdentity convention already established for the Twisted Bow entry.
     */
    fun readExistingMapping(assetMapFile: File): ExistingMapping {
        if (!assetMapFile.isFile) return ExistingMapping(emptyMap(), emptyMap(), emptySet())
        val root: JsonNode = ObjectMapper(YAMLFactory()).readTree(assetMapFile) ?: return ExistingMapping(emptyMap(), emptyMap(), emptySet())

        val itemIds = mutableMapOf<String, Int>()
        val modelIds = mutableMapOf<String, Int>()
        val itemIdentities = mutableSetOf<String>()
        root.path("imports").forEach { import ->
            val upstreamItemId = import.path("upstream_item_id")
            val localItemId = import.path("local_item_id")
            if (upstreamItemId.isInt && localItemId.isInt) {
                val identity = "upstream_item:${upstreamItemId.asInt()}"
                itemIds[identity] = localItemId.asInt()
                itemIdentities += identity
            }
            import.path("models").forEach { model ->
                val upstreamModelId = model.path("upstream_id")
                val localModelId = model.path("local_id")
                val role = model.path("role").takeIf { it.isTextual }?.asText()
                if (upstreamModelId.isInt && localModelId.isInt && role != null) {
                    modelIds[ModelIdAllocator.mappingKey("upstream_model:${upstreamModelId.asInt()}", role)] = localModelId.asInt()
                }
            }
        }
        return ExistingMapping(itemIds, modelIds, itemIdentities)
    }

    // ---- durable mapping: write (idempotent, additive-only text append) -------------------------

    /**
     * Idempotently appends newly-confirmed [items] to [assetMapFile]'s `imports:` list.
     *
     * Deliberately does not parse-then-re-serialize the whole file: this file is hand-curated,
     * heavily commented production documentation (provenance narrative, per-param annotations,
     * census notes), and a generic YAML round-trip through a library that does not preserve comments
     * would silently destroy all of that. Instead this performs the same kind of structural edit a
     * human maintaining the file by hand would - a plain text append of one new block per item, in
     * the same shape the existing Twisted Bow entry already uses, at the end of the file. Only the
     * fields this project's own tooling actually reads back ([readExistingMapping],
     * [ModelNamespaceCensusTool.mappedModelIds]) are written.
     *
     * An item already present in [existing] is skipped, so calling this twice with the same [items]
     * (an idempotency-proof rerun, or a crash-recovery replay) is a genuine no-op the second time.
     */
    fun finalizeDurableMapping(
        assetMapFile: File,
        items: List<MappedItem>,
        existing: ExistingMapping,
    ): List<MappedItem> {
        val toAppend = items.filter { it.sourceIdentity !in existing.mappedItemSourceIdentities }
        if (toAppend.isEmpty()) return emptyList()
        require(assetMapFile.isFile) { "Refusing to append to a durable mapping file that does not exist yet: $assetMapFile" }

        val block = StringBuilder()
        toAppend.forEach { item ->
            block.append("  - local_item_id: ${item.localId}\n")
            block.append("    name: ${item.name}\n")
            block.append("    upstream_item_id: ${item.sourceIdentity.removePrefix("upstream_item:")}\n")
            block.append("    status: IMPORTED_BY_ORCHESTRATOR\n")
            if (item.models.isNotEmpty()) {
                block.append("    models:\n")
                item.models.forEach { model ->
                    block.append("      - role: ${model.role}\n")
                    block.append("        upstream_id: ${model.sourceIdentity.removePrefix("upstream_model:")}\n")
                    block.append("        local_id: ${model.localId}\n")
                }
            }
        }
        val text = assetMapFile.readText()
        assetMapFile.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
        return toAppend
    }

    // ---- crash-window recovery ------------------------------------------------------------------

    private data class Location(val indexId: Int, val groupId: Int, val fileId: Int)

    private data class JournalLine(val target: String, val location: Location, val label: String, val intendedSha1: String?)

    private val LOCATION_PATTERN = Regex("""idx(\d+)_grp(\d+)_file(\d+)""")

    /**
     * Re-derives every batch mutation whose content genuinely landed on every real target cache but
     * whose durable mapping was never committed - see the class doc for why this reads
     * [CacheTransaction]'s own `plan.txt` journal rather than inventing a second durable record.
     *
     * A mutation is only recovered when every entry in [targets] currently holds bytes whose SHA-1
     * matches what the journal recorded as intended - i.e. the write is proven complete everywhere,
     * not merely started. A transaction that never finished `apply()`, or whose own rollback already
     * restored a target, produces nothing here; a subsequent [plan] call is then free to allocate a
     * fresh id for that source exactly as if the interrupted attempt never happened, because nothing
     * was ever left mapped to it.
     *
     * If two genuinely different journalled locations both independently appear to have landed for
     * the same item `sourceIdentity` (which real [CacheTransaction] semantics should make impossible
     * - a second id landing where a real, different first id already committed would have to have
     * started from CONFLICT-free preflight against still-empty content), this refuses to silently
     * pick one and throws instead: that is an integrity anomaly for a human to look at, not something
     * this method may resolve on its own judgement.
     */
    fun recoverUnfinalized(
        targets: List<String>,
        alreadyMappedItemSourceIdentities: Set<String>,
        journalRoot: File = File(CacheTransaction.DEFAULT_JOURNAL_ROOT),
    ): List<MappedItem> {
        if (!journalRoot.isDirectory) return emptyList()

        val itemLines = mutableMapOf<String, MutableList<JournalLine>>()
        val modelLines = mutableMapOf<String, MutableList<JournalLine>>()

        journalRoot.listFiles { file -> file.isDirectory }?.forEach { txDir ->
            val planFile = File(txDir, "plan.txt")
            if (!planFile.isFile) return@forEach
            planFile.readLines().filter { it.startsWith("plan\t") }.forEach { raw ->
                val fields =
                    raw.removePrefix("plan\t").split('\t').mapNotNull { field ->
                        val eq = field.indexOf('=')
                        if (eq == -1) null else field.substring(0, eq) to field.substring(eq + 1)
                    }.toMap()
                val target = fields["target"] ?: return@forEach
                val rawLocation = fields["location"] ?: return@forEach
                val match = LOCATION_PATTERN.matchEntire(rawLocation) ?: return@forEach
                val label = fields["label"] ?: return@forEach
                if (!label.startsWith("import|")) return@forEach
                val intended = fields["intended_sha1"]?.takeIf { it != "ABSENT" }
                val location = Location(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
                val line = JournalLine(target, location, label, intended)
                val parts = label.split('|')
                when (parts.getOrNull(1)) {
                    "item" -> itemLines.getOrPut(parts[2]) { mutableListOf() } += line
                    "model" -> modelLines.getOrPut(parts[2]) { mutableListOf() } += line
                }
            }
        }

        // "Landed" is evaluated per exact (label, location) group, never blended across different
        // attempts that used different locations for the same source - see the anomaly check below.
        fun landedGroups(lines: List<JournalLine>): List<List<JournalLine>> =
            lines.groupBy { it.label to it.location }.values.filter { group ->
                val byTarget = group.associateBy { it.target }
                targets.all { target ->
                    val line = byTarget[target] ?: return@all false
                    val intendedSha1 = line.intendedSha1 ?: return@all false
                    val library = CacheLibrary(target)
                    try {
                        val actual = library.data(line.location.indexId, line.location.groupId, line.location.fileId)
                        actual != null && CacheItemProbeTool.sha1(actual) == intendedSha1
                    } finally {
                        library.close()
                    }
                }
            }

        val recovered = mutableListOf<MappedItem>()
        itemLines.forEach { (itemSourceIdentity, lines) ->
            if (itemSourceIdentity in alreadyMappedItemSourceIdentities) return@forEach
            val landed = landedGroups(lines)
            if (landed.isEmpty()) return@forEach
            check(landed.size == 1) {
                "Integrity anomaly: item sourceIdentity=$itemSourceIdentity has ${landed.size} distinct " +
                    "journalled locations that all independently appear fully landed on every target " +
                    "(${landed.map { it.first().location }}) - this needs a human to reconcile, it is not " +
                    "safe for recovery to silently choose one."
            }
            val group = landed.single()
            val labelParts = group.first().label.split('|') // import|item|<sourceIdentity>|<name>
            val name = labelParts.getOrElse(3) { "?" }
            val itemLocalId = (group.first().location.groupId shl 8) or group.first().location.fileId

            val models =
                (modelLines[itemSourceIdentity] ?: emptyList())
                    .groupBy { it.label to it.location }
                    .values
                    .filter { candidate ->
                        val byTarget = candidate.associateBy { it.target }
                        targets.all { target ->
                            val line = byTarget[target] ?: return@all false
                            val intendedSha1 = line.intendedSha1 ?: return@all false
                            val library = CacheLibrary(target)
                            try {
                                val actual = library.data(line.location.indexId, line.location.groupId, line.location.fileId)
                                actual != null && CacheItemProbeTool.sha1(actual) == intendedSha1
                            } finally {
                                library.close()
                            }
                        }
                    }
                    .map { candidate ->
                        val modelParts = candidate.first().label.split('|') // import|model|<itemIdentity>|<modelIdentity>|<role>
                        MappedModel(sourceIdentity = modelParts[3], role = modelParts[4], localId = candidate.first().location.groupId)
                    }

            recovered += MappedItem(itemSourceIdentity, name, itemLocalId, models)
        }
        return recovered
    }

    // ---- orchestration: composes the steps above with the real CacheTransaction lifecycle -------

    class OrchestrationResult(
        val transactionId: String,
        /** Recovered from an earlier crash before this call's own [plan] even ran. */
        val recoveredAndFinalized: List<MappedItem>,
        /** Newly applied and finalized by this call (empty for a dry run). */
        val newlyFinalized: List<MappedItem>,
        val applyResult: ApplyResult?,
    )

    /**
     * The full lifecycle in one call: RECOVER any unfinalized prior crash -> re-read the mapping ->
     * PLAN -> PREFLIGHT -> (if [apply]) APPLY -> VERIFY (including item-id contiguity) -> FINALIZE.
     * A verification failure rolls the transaction back and throws, exactly as
     * [ItemTransactionTool]/[ModelTransactionTool] already do - this adds no new failure behaviour,
     * only sequences the existing ones across a mixed item+model batch.
     */
    fun orchestrate(
        targets: List<String>,
        batch: List<ItemSource>,
        assetMapFile: File,
        modelSafeFreeStart: Int,
        modelSafeFreeEnd: Int,
        apply: Boolean,
        journalRoot: File = File(CacheTransaction.DEFAULT_JOURNAL_ROOT),
        modelCandidates: List<Int>? = null,
        onPlanned: (BatchPlan) -> Unit = {},
    ): OrchestrationResult {
        val beforeRecovery = readExistingMapping(assetMapFile)
        val recovered = recoverUnfinalized(targets, beforeRecovery.mappedItemSourceIdentities, journalRoot)
        if (recovered.isNotEmpty()) finalizeDurableMapping(assetMapFile, recovered, beforeRecovery)

        val existing = readExistingMapping(assetMapFile)
        val batchPlan =
            plan(
                targets, batch, existing, nextFreeItemId(targets), modelSafeFreeStart, modelSafeFreeEnd, journalRoot,
                modelCandidates = modelCandidates,
            )
        onPlanned(batchPlan)

        val preflight = batchPlan.transaction.preflight()
        val errors = batchPlan.transaction.blockingErrors(preflight)
        check(errors.isEmpty()) {
            "Refusing to apply batch ${batchPlan.transaction.id} - preflight has ${errors.size} blocking error(s):\n" +
                errors.joinToString("\n")
        }

        if (!apply) {
            return OrchestrationResult(batchPlan.transaction.id, recovered, emptyList(), null)
        }

        val applyResult = batchPlan.transaction.apply(preflight)
        val problems = batchPlan.transaction.verify().toMutableList()
        problems += contiguityProblems(targets)
        problems += modelDecodeProblems(targets, batchPlan.items.flatMap { it.models }.map { it.localId }.toSet())
        if (problems.isNotEmpty()) {
            batchPlan.transaction.rollback()
            error("Batch transaction ${batchPlan.transaction.id} failed verification and was rolled back:\n${problems.joinToString("\n")}")
        }

        val finalized = finalizeDurableMapping(assetMapFile, toMappedItems(batchPlan), existing)
        return OrchestrationResult(batchPlan.transaction.id, recovered, finalized, applyResult)
    }

    /**
     * Mirrors [ModelTransactionTool]'s own post-write decode check: every written model group is
     * re-read from disk through a fresh [CacheLibrary] and decoded with [Rev667ModelDecoder], the
     * port of the 667 client's own mesh reader, so a batch can never leave a cache holding model
     * bytes the client would choke on even though [CacheTransaction.verify]'s byte-equality check
     * passed.
     */
    private fun modelDecodeProblems(
        targets: List<String>,
        modelIds: Set<Int>,
    ): List<String> =
        targets.flatMap { target ->
            val library = CacheLibrary(target)
            try {
                modelIds.mapNotNull { groupId ->
                    val stored = library.data(ModelConvertTool.MODEL_INDEX, groupId)
                    when {
                        stored == null -> "MODEL_ABSENT at $target: index ${ModelConvertTool.MODEL_INDEX} group $groupId has no data after the write."
                        else ->
                            runCatching { Rev667ModelDecoder.decode(stored) }
                                .fold(onSuccess = { null }, onFailure = { "MODEL_UNDECODABLE at $target group $groupId: ${it.javaClass.simpleName}: ${it.message}" })
                    }
                }
            } finally {
                library.close()
            }
        }

    /** Mirrors [ItemTransactionTool]'s own post-write contiguity check; see [CacheItemProbeTool.maxContiguousItemId]. */
    private fun contiguityProblems(targets: List<String>): List<String> =
        targets.mapNotNull { target ->
            val library = CacheLibrary(target)
            try {
                val allIds = CacheItemProbeTool.allItemIds(library)
                val max = CacheItemProbeTool.maxContiguousItemId(allIds)
                val orphans = allIds.filter { it > max }
                if (orphans.isEmpty()) null else "ITEM_ID_GAP at $target: contiguous block ends at $max but ${orphans.size} orphan id(s) also have data, e.g. ${orphans.first()}."
            } finally {
                library.close()
            }
        }

    private fun modelLabel(
        itemSourceIdentity: String,
        model: ModelSource,
    ) = "import|model|$itemSourceIdentity|${model.sourceIdentity}|${model.role}"

    private fun itemLabel(item: ItemSource) = "import|item|${item.sourceIdentity}|${item.name}"
}
