package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The dual-cache write transaction used by every step of the modern-content import pipeline
 * (`RSPS_DECISIONS.md` 2026-09-02 "STANDING OWNER AUTHORIZATION").
 *
 * This project has **two** physically separate copies of the same rev-667 cache - the game
 * server's `game/game/data/cache` and the client file-server's `file-server/cache` - and an
 * import is only correct when both end up carrying the same content. The previous pass proved the
 * failure mode this class exists to remove: an import was applied to the game cache and never
 * reached the file-server cache, leaving a silent half-import that no manifest, `Items.kt` entry
 * or status file recorded.
 *
 * Lifecycle (each stage is a separate method so a caller can stop at any point):
 *
 *  - [preflight] - read every target's current bytes at every planned location and classify the
 *    outcome without writing anything. A plan containing any [MutationOutcome.CONFLICT] or
 *    [MutationOutcome.MISSING_FOR_REMOVE] is refused by [apply].
 *  - [apply] - journal the pre-write bytes of every location that will actually change, then write
 *    all mutations to all targets.
 *  - [verify] - reopen every target from disk, re-read every mutated location, and check both that
 *    each target matches the intended bytes and that all targets agree with each other.
 *  - [rollback] - restore every journalled location from the journal, for when [apply] or [verify]
 *    fails part-way through.
 *
 * Backups: the baseline full-cache copies from 2026-09-02 are kept as-is and never touched. Each
 * transaction instead journals only the handful of archive entries it is about to overwrite, into
 * [journalRoot] (`C:\RSPS\import-journal` by default) - a location outside every Git repository, so
 * transaction binaries can never be accidentally staged.
 *
 * Idempotency: re-running the exact same transaction after a successful apply classifies every
 * location as [MutationOutcome.NO_OP], because the target already holds the intended bytes.
 */
class CacheTransaction(
    val targets: List<String>,
    val mutations: List<CacheMutation>,
    val journalRoot: File = File(DEFAULT_JOURNAL_ROOT),
    val id: String = newTransactionId(),
    /**
     * Test-only fault-injection seam: invoked with each target's path immediately before that
     * target is opened for writing, in [targets] order, and before anything else about that
     * target's write. Production callers must never set this - it exists only so
     * `CacheTransactionTests` can trigger a genuine, unmocked I/O failure on a later target after
     * an earlier target has already been durably mutated, which is otherwise impossible to force
     * deterministically from outside a single [apply] call.
     */
    internal val onBeforeTargetWrite: ((String) -> Unit)? = null,
) {
    /** Where this transaction's pre-write bytes and plan record live. */
    val journalDir: File = File(journalRoot, id)

    /**
     * Reads the current state of every (target, mutation) pair and classifies what applying it
     * would do. Writes nothing.
     */
    fun preflight(): List<PreflightEntry> =
        targets.flatMap { target ->
            withLibrary(target) { library ->
                mutations.map { mutation ->
                    val current = library.data(mutation.indexId, mutation.groupId, mutation.fileId, mutation.xtea)
                    PreflightEntry(
                        target = target,
                        mutation = mutation,
                        currentSha1 = current?.let { CacheItemProbeTool.sha1(it) },
                        currentSize = current?.size,
                        outcome = classify(mutation, current),
                    )
                }
            }
        }

    private fun classify(
        mutation: CacheMutation,
        current: ByteArray?,
    ): MutationOutcome {
        val intended = mutation.newBytes
        if (intended == null) {
            return if (current == null) MutationOutcome.MISSING_FOR_REMOVE else MutationOutcome.REMOVE
        }
        if (current == null) return MutationOutcome.CREATE
        if (current.contentEquals(intended)) return MutationOutcome.NO_OP
        // Existing-but-different content is only replaceable when the caller proved it knows what
        // is there, by pinning the exact bytes it expects to be replacing. Anything else is
        // unknown content and must never be silently overwritten.
        val expected = mutation.expectedCurrentSha1
        return if (expected != null && expected == CacheItemProbeTool.sha1(current)) {
            MutationOutcome.REPLACE
        } else {
            MutationOutcome.CONFLICT
        }
    }

    /** Hard errors in [plan] that must block any real write. */
    fun blockingErrors(plan: List<PreflightEntry>): List<String> =
        plan
            .filter { it.outcome == MutationOutcome.CONFLICT || it.outcome == MutationOutcome.MISSING_FOR_REMOVE }
            .map {
                "${it.outcome} at ${it.target} ${it.mutation.describeLocation()} (${it.mutation.label}): " +
                    "current sha1=${it.currentSha1 ?: "ABSENT"}, expectedCurrentSha1=${it.mutation.expectedCurrentSha1 ?: "none"}"
            }

    /**
     * Journals then applies every mutation to every target. Refuses to start if [preflight]
     * reports any blocking error, and skips locations already holding the intended bytes.
     *
     * On any failure part-way through, the journal written first is sufficient for [rollback] to
     * restore every target that was touched.
     */
    fun apply(plan: List<PreflightEntry> = preflight()): ApplyResult {
        val errors = blockingErrors(plan)
        check(errors.isEmpty()) {
            "Refusing to apply transaction $id - preflight has ${errors.size} blocking error(s):\n" +
                errors.joinToString("\n")
        }

        journalDir.mkdirs()
        writePlanRecord(plan)

        val changing = plan.filter { it.outcome != MutationOutcome.NO_OP }
        if (changing.isEmpty()) {
            return ApplyResult(id, applied = 0, skipped = plan.size, journalDir = journalDir)
        }

        // Journal first, for every target, before a single byte is written anywhere.
        changing.forEach { entry ->
            withLibrary(entry.target) { library ->
                val current = library.data(entry.mutation.indexId, entry.mutation.groupId, entry.mutation.fileId, entry.mutation.xtea)
                journalFile(entry.target, entry.mutation).writeBytes(current ?: ByteArray(0))
                if (current == null) {
                    // Distinguish "was absent" from "was an empty archive" so rollback can remove
                    // rather than write a zero-length entry.
                    absentMarker(entry.target, entry.mutation).writeText("absent")
                }
            }
        }

        // Locations actually written by this attempt, tracked as they happen rather than assumed
        // from `changing`, so a target-2 failure after target 1 succeeds knows exactly what needs
        // undoing - and nothing more, since target 2 itself never received a write to undo.
        val committed = mutableListOf<PreflightEntry>()
        try {
            var applied = 0
            targets.forEach { target ->
                val forTarget = changing.filter { it.target == target }
                if (forTarget.isEmpty()) return@forEach
                onBeforeTargetWrite?.invoke(target)
                withLibrary(target) { library ->
                    forTarget.forEach { entry ->
                        val mutation = entry.mutation
                        if (mutation.newBytes == null) {
                            library.remove(mutation.indexId, mutation.groupId, mutation.fileId)
                        } else {
                            library.put(mutation.indexId, mutation.groupId, mutation.fileId, mutation.newBytes, mutation.xtea)
                        }
                        committed += entry
                        applied++
                    }
                    library.update()
                }
            }
            return ApplyResult(id, applied = applied, skipped = plan.size - changing.size, journalDir = journalDir)
        } catch (failure: Exception) {
            // A target that threw mid-write must never be allowed to stand as a silent half-import:
            // roll back exactly what this attempt actually wrote (which may be every earlier target
            // in a >2-target transaction) before letting the failure propagate, so by the time the
            // caller observes the exception, every target is already back at its pre-transaction
            // bytes rather than left divergent. Rollback itself can fail too - e.g. the same
            // persistent fault (disk full, lock, permission) that broke the original write on a
            // target can also break restoring THAT target - so this must never claim a clean
            // recovery it did not actually achieve, and one target's rollback failure must not stop
            // an attempt to roll back every other committed target.
            val outcome = rollbackCommitted(committed)
            if (outcome.failedTargets.isEmpty()) {
                throw IllegalStateException(
                    "Transaction $id failed during apply after committing ${committed.size} of ${changing.size} " +
                        "planned write(s); automatically rolled back ${outcome.restored} committed location(s) " +
                        "from $journalDir. No target was left holding a false success state.",
                    failure,
                )
            } else {
                throw IllegalStateException(
                    "Transaction $id failed during apply after committing ${committed.size} of ${changing.size} " +
                        "planned write(s). Automatic rollback restored ${outcome.restored} location(s) but did " +
                        "NOT confirm restoration for: ${outcome.failedTargets.joinToString("; ")}. Those " +
                        "target(s) may still hold genuinely divergent bytes - do NOT treat this transaction as " +
                        "recovered. Re-run rollback() against $journalDir once the underlying fault is cleared, " +
                        "then re-verify, before starting any further transaction against these targets.",
                    failure,
                )
            }
        }
    }

    /** Outcome of [rollbackCommitted]: how many locations it actually restored, and which targets, if any, it could not confirm restoring. */
    private class RollbackOutcome(val restored: Int, val failedTargets: List<String>)

    /**
     * Restores exactly the locations in [committed] from the journal captured before this
     * attempt's writes began. Unlike [rollback], which restores every journalled location for a
     * transaction that fully applied, this only touches targets that actually received a write
     * this attempt - a target whose write never ran because an earlier target threw first already
     * holds its original bytes and must not be touched.
     *
     * Each target is rolled back independently: if restoring one target throws (for example the
     * exact same fault that broke its original write is still present), that is recorded rather
     * than propagated, so every other committed target still gets its own rollback attempt instead
     * of being left untouched by an unrelated failure.
     */
    private fun rollbackCommitted(committed: List<PreflightEntry>): RollbackOutcome {
        var restored = 0
        val failedTargets = mutableListOf<String>()
        committed.groupBy { it.target }.forEach { (target, entries) ->
            try {
                withLibrary(target) { library ->
                    entries.forEach { entry ->
                        val mutation = entry.mutation
                        if (absentMarker(target, mutation).isFile) {
                            library.remove(mutation.indexId, mutation.groupId, mutation.fileId)
                        } else {
                            library.put(mutation.indexId, mutation.groupId, mutation.fileId, journalFile(target, mutation).readBytes(), mutation.xtea)
                        }
                        restored++
                    }
                    library.update()
                }
            } catch (rollbackFailure: Exception) {
                failedTargets += "$target (${entries.size} location(s), rollback threw: $rollbackFailure)"
            }
        }
        return RollbackOutcome(restored, failedTargets)
    }

    /**
     * Reopens every target from disk and checks each mutated location holds exactly the intended
     * bytes, and that every target agrees with every other target. Returns an empty list when the
     * transaction is fully consistent.
     */
    fun verify(): List<String> {
        val problems = mutableListOf<String>()
        val perLocation = mutableMapOf<String, MutableMap<String, String?>>()

        targets.forEach { target ->
            withLibrary(target) { library ->
                mutations.forEach { mutation ->
                    val actual = library.data(mutation.indexId, mutation.groupId, mutation.fileId, mutation.xtea)
                    val actualSha = actual?.let { CacheItemProbeTool.sha1(it) }
                    val intendedSha = mutation.newBytes?.let { CacheItemProbeTool.sha1(it) }
                    if (actualSha != intendedSha) {
                        problems +=
                            "MISMATCH at $target ${mutation.describeLocation()} (${mutation.label}): " +
                                "expected sha1=${intendedSha ?: "ABSENT"}, actual sha1=${actualSha ?: "ABSENT"}"
                    }
                    perLocation.getOrPut(mutation.describeLocation()) { mutableMapOf() }[target] = actualSha
                }
            }
        }

        perLocation.forEach { (location, byTarget) ->
            if (byTarget.values.distinct().size > 1) {
                problems += "TARGET_DIVERGENCE at $location: " + byTarget.entries.joinToString { "${it.key}=${it.value ?: "ABSENT"}" }
            }
        }
        return problems
    }

    /**
     * Restores every location this transaction journalled back to its pre-transaction bytes.
     * Safe to call when [apply] failed part-way: locations that were never journalled are simply
     * not present in the journal and are left alone.
     */
    fun rollback(): Int {
        if (!journalDir.isDirectory) return 0
        var restored = 0
        targets.forEach { target ->
            val forTarget = mutations.filter { journalFile(target, it).isFile }
            if (forTarget.isEmpty()) return@forEach
            withLibrary(target) { library ->
                forTarget.forEach { mutation ->
                    if (absentMarker(target, mutation).isFile) {
                        library.remove(mutation.indexId, mutation.groupId, mutation.fileId)
                    } else {
                        library.put(mutation.indexId, mutation.groupId, mutation.fileId, journalFile(target, mutation).readBytes(), mutation.xtea)
                    }
                    restored++
                }
                library.update()
            }
        }
        return restored
    }

    private fun writePlanRecord(plan: List<PreflightEntry>) {
        val record = StringBuilder()
        record.appendLine("transaction_id=$id")
        record.appendLine("created=${Instant.now()}")
        targets.forEach { record.appendLine("target=$it") }
        plan.forEach { entry ->
            record.appendLine(
                "plan\ttarget=${entry.target}\tlocation=${entry.mutation.describeLocation()}\tlabel=${entry.mutation.label}" +
                    "\toutcome=${entry.outcome}\tcurrent_sha1=${entry.currentSha1 ?: "ABSENT"}" +
                    "\tintended_sha1=${entry.mutation.newBytes?.let { CacheItemProbeTool.sha1(it) } ?: "ABSENT"}",
            )
        }
        File(journalDir, "plan.txt").writeText(record.toString())
    }

    private fun journalFile(
        target: String,
        mutation: CacheMutation,
    ) = File(journalDir, "${targetSlug(target)}__${mutation.describeLocation()}.orig")

    private fun absentMarker(
        target: String,
        mutation: CacheMutation,
    ) = File(journalDir, "${targetSlug(target)}__${mutation.describeLocation()}.absent")

    private fun targetSlug(target: String) = target.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')

    private fun <T> withLibrary(
        path: String,
        block: (CacheLibrary) -> T,
    ): T {
        val library = CacheLibrary(path)
        try {
            return block(library)
        } finally {
            library.close()
        }
    }

    companion object {
        const val DEFAULT_JOURNAL_ROOT = "C:\\RSPS\\import-journal"

        private val ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

        fun newTransactionId(): String = "tx-${ID_FORMAT.format(Instant.now())}"
    }
}

/**
 * One planned write to a single cache location, identified the way
 * [com.displee.cache.CacheLibrary] addresses it: index, group (archive) and file.
 *
 * @param newBytes the intended content, or null to remove the entry entirely.
 * @param expectedCurrentSha1 required only when deliberately replacing existing, different
 *   content: the SHA-1 the caller has already verified is currently there. Without it, existing
 *   different content is treated as unknown and classified [MutationOutcome.CONFLICT].
 */
class CacheMutation(
    val indexId: Int,
    val groupId: Int,
    val fileId: Int,
    val newBytes: ByteArray?,
    val label: String,
    val expectedCurrentSha1: String? = null,
    /**
     * XTEA key of an encrypted group (rev-667 map `l<x>_<z>` groups). Used for every read of the
     * current bytes and every write, so preflight sees the real content (REPLACE, not CREATE), the
     * journal captures the real plaintext for rollback, and displee re-encrypts on write with the
     * same key - the server's xteas.json therefore needs no change. Null = unencrypted group.
     */
    val xtea: IntArray? = null,
) {
    fun describeLocation(): String = "idx${indexId}_grp${groupId}_file$fileId"

    companion object {
        /** Convenience builder for the item index, whose id maps to group `id ushr 8`, file `id and 0xFF`. */
        fun item(
            itemId: Int,
            newBytes: ByteArray?,
            label: String,
            expectedCurrentSha1: String? = null,
        ): CacheMutation =
            CacheMutation(
                indexId = gg.rsmod.game.fs.ArchiveType.ITEM.id,
                groupId = itemId ushr 8,
                fileId = itemId and 0xFF,
                newBytes = newBytes,
                label = label,
                expectedCurrentSha1 = expectedCurrentSha1,
            )
    }
}

enum class MutationOutcome {
    /** The location has no data and the mutation will create it. */
    CREATE,

    /** The location holds known, caller-pinned different bytes that will be replaced. */
    REPLACE,

    /** The location holds data and the mutation removes it. */
    REMOVE,

    /** The location already holds exactly the intended bytes - nothing to do. */
    NO_OP,

    /** The location holds unknown different bytes; refusing to overwrite. */
    CONFLICT,

    /** A removal was planned for a location that has no data. */
    MISSING_FOR_REMOVE,
}

class PreflightEntry(
    val target: String,
    val mutation: CacheMutation,
    val currentSha1: String?,
    val currentSize: Int?,
    val outcome: MutationOutcome,
) {
    override fun toString(): String =
        "$outcome\t$target\t${mutation.describeLocation()}\t${mutation.label}\t" +
            "current=${currentSha1 ?: "ABSENT"}(${currentSize ?: 0}b)\t" +
            "intended=${mutation.newBytes?.let { CacheItemProbeTool.sha1(it) } ?: "ABSENT"}(${mutation.newBytes?.size ?: 0}b)"
}

class ApplyResult(
    val transactionId: String,
    val applied: Int,
    val skipped: Int,
    val journalDir: File,
)
