package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
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
     * Owner report 2026-09-19 ("why every time my fileserver hangs"): both servers read their cache copy once at boot and
     * then serve it, so writing a transaction into those files while they run makes the file-server hand the client a cache
     * that no longer matches the index it loaded - the client hangs on "Loading...". A cache write is therefore refused
     * while the game-server (50015) or file-server (50016) is listening; stop them first (command port 50017 "shutdown").
     * [ALLOW_LIVE_ENV] exists for a deliberate override and for the tests, which run against temp caches, not the live ones.
     */
    private fun refuseWhileServersRun() {
        if (System.getenv(ALLOW_LIVE_ENV) != null) return
        if (targets.none { File(it).absolutePath.replace('\\', '/').let { p -> LIVE_CACHE_PATHS.any { live -> p.endsWith(live) } } }) return
        val live = LIVE_PORTS.filter { port -> isListening(port) }
        check(live.isEmpty()) {
            "Refusing to apply transaction $id: the server is running (port ${live.joinToString(", ")}). Both caches are read " +
                "at boot, so writing now makes the file-server serve a cache the client cannot load. Stop the servers first " +
                "(command port 50017 \"shutdown\"), or set $ALLOW_LIVE_ENV=1 to override."
        }
    }

    private fun isListening(port: Int): Boolean =
        try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 300)
                true
            }
        } catch (_: Exception) {
            false
        }

    /**
     * Reads the current state of every (target, mutation) pair and classifies what applying it
     * would do. Writes nothing.
     */
    fun preflight(): List<PreflightEntry> =
        targets.flatMap { target ->
            withLibrary(target) { library ->
                mutations.map { mutation ->
                    if (!library.exists(mutation.indexId)) {
                        // N462: model archive 37 before its first model - every location in it is absent.
                        return@map PreflightEntry(target, mutation, null, null, classify(mutation, null))
                    }
                    if (mutation.storedReference != null) {
                        val current = storedGroup(library, mutation)
                        val archive = library.index(mutation.indexId).archive(mutation.groupId, null, true)
                        // Raw restore preserves the existing single-file table and name; never apply it to another shape.
                        val compatible = archive != null && archive.files.keys == setOf(0)
                        val outcome = when {
                            !compatible || current == null -> MutationOutcome.CONFLICT
                            current.contentEquals(mutation.newBytes!!) && referenceOf(library, mutation) == mutation.storedReference -> MutationOutcome.NO_OP
                            CacheItemProbeTool.sha1(current) == mutation.expectedCurrentSha1 -> MutationOutcome.REPLACE
                            else -> MutationOutcome.CONFLICT
                        }
                        return@map PreflightEntry(target, mutation, current?.let { CacheItemProbeTool.sha1(it) }, current?.size, outcome)
                    }
                    pinnedUndecodable(library, mutation)?.let { stored ->
                        return@map PreflightEntry(target, mutation, CacheItemProbeTool.sha1(stored), stored.size, MutationOutcome.REPLACE)
                    }
                    val current =
                        try {
                            library.data(mutation.indexId, mutation.groupId, mutation.fileId, mutation.xtea)
                        } catch (e: Exception) {
                            // Only a mutation that declares its group undecodable gets here with garbage: anything that is not
                            // the pinned container is unknown content.
                            if (mutation.undecodableCurrentSha1 == null) throw e
                            return@map PreflightEntry(
                                target,
                                mutation,
                                storedGroup(library, mutation)?.let { CacheItemProbeTool.sha1(it) },
                                null,
                                MutationOutcome.CONFLICT,
                            )
                        }
                    if (current == null && mutation.undecodableCurrentSha1 != null) {
                        // displee returns null for a group it cannot decode: stored bytes that are not the pin are unknown content, never a CREATE.
                        storedGroup(library, mutation)?.let { stored ->
                            return@map PreflightEntry(target, mutation, CacheItemProbeTool.sha1(stored), stored.size, MutationOutcome.CONFLICT)
                        }
                    }
                    // A named group may only be created under a name no other group of the index already carries.
                    val nameTaken =
                        (
                            mutation.groupName != null && current == null &&
                                library.index(mutation.indexId).archive(mutation.groupName)?.let { it.id != mutation.groupId } == true
                        ) ||
                            (
                                mutation.groupNameHash != null && current == null &&
                                    library.index(mutation.indexId).archives().any { it.hashName == mutation.groupNameHash && it.id != mutation.groupId }
                            )
                    PreflightEntry(
                        target = target,
                        mutation = mutation,
                        currentSha1 = current?.let { CacheItemProbeTool.sha1(it) },
                        currentSize = current?.size,
                        outcome = if (nameTaken) MutationOutcome.CONFLICT else classify(mutation, current),
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
        refuseWhileServersRun()
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

        // Journal first, for every target, before a single byte is written anywhere. N462 (2026-11-06): one library per target for the
        // whole journal pass - opening the cache again for every entry made a 46,701-mutation import journal at ~8 entries/second (hours).
        changing.groupBy { it.target }.forEach { (target, entries) -> withLibrary(target) { library -> entries.forEach { entry ->
            run {
                (if (entry.mutation.storedReference != null) storedGroup(library, entry.mutation) else pinnedUndecodable(library, entry.mutation))?.let { stored ->
                    // N10: the old group cannot be decoded (no key), so the journal keeps it as stored plus its reference entry.
                    storedJournal(entry.target, entry.mutation).writeBytes(stored)
                    val archive = library.index(entry.mutation.indexId).archive(entry.mutation.groupId, null, true)!!
                    val whirlpool = archive.whirlpool?.joinToString("") { "%02x".format(it) } ?: ""
                    referenceJournal(entry.target, entry.mutation).writeText("crc=${archive.crc}\nrevision=${archive.revision}\nwhirlpool=$whirlpool\n")
                    return@run
                }
                val current = if (!library.exists(entry.mutation.indexId)) null
                    else library.data(entry.mutation.indexId, entry.mutation.groupId, entry.mutation.fileId, entry.mutation.xtea)
                journalFile(entry.target, entry.mutation).writeBytes(current ?: ByteArray(0))
                if (current == null) {
                    // Distinguish "was absent" from "was an empty archive" so rollback can remove
                    // rather than write a zero-length entry.
                    absentMarker(entry.target, entry.mutation).writeText("absent")
                }
            }
        } } }

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
                    if (forTarget.any { it.mutation.indexId == ModelArchive.MODELS_EXT }) ModelArchive.ensureExtIndex(library)
                    forTarget.forEach { entry ->
                        val mutation = entry.mutation
                        if (mutation.newBytes == null) {
                            library.remove(mutation.indexId, mutation.groupId, mutation.fileId)
                        } else if (mutation.storedReference != null) {
                            writeStored(library, mutation, mutation.newBytes, mutation.storedReference)
                        } else {
                            if (storedJournal(target, mutation).isFile) {
                                // The pinned undecodable group is replaced whole without decoding it first (displee would
                                // inflate the ciphertext): mark it read with no files, then the put below writes the new file.
                                library.index(mutation.indexId).archive(mutation.groupId, null, true)!!.let { archive ->
                                    archive.files.clear()
                                    archive.read = true
                                }
                            }
                            library.put(mutation.indexId, mutation.groupId, mutation.fileId, mutation.newBytes, mutation.xtea)
                            mutation.groupName?.let { name ->
                                val index = library.index(mutation.indexId)
                                index.archive(mutation.groupId)!!.hashName = index.toHash(name)
                            }
                            mutation.groupNameHash?.let { hash ->
                                library.index(mutation.indexId).archive(mutation.groupId)!!.hashName = hash
                            }
                            mutation.fileName?.let { name ->
                                val index = library.index(mutation.indexId)
                                index.archive(mutation.groupId)!!.file(mutation.fileId)!!.hashName = index.toHash(name)
                            }
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
                        if (storedJournal(target, mutation).isFile) {
                            restoreStored(library, target, mutation)
                        } else if (absentMarker(target, mutation).isFile) {
                            removeCreated(library, mutation)
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
                    val actual = if (!library.exists(mutation.indexId)) null
                        else if (mutation.storedReference != null) storedGroup(library, mutation)
                        else library.data(mutation.indexId, mutation.groupId, mutation.fileId, mutation.xtea)
                    val actualSha = actual?.let { CacheItemProbeTool.sha1(it) }
                    val intendedSha = mutation.newBytes?.let { CacheItemProbeTool.sha1(it) }
                    if (actualSha != intendedSha) {
                        problems +=
                            "MISMATCH at $target ${mutation.describeLocation()} (${mutation.label}): " +
                                "expected sha1=${intendedSha ?: "ABSENT"}, actual sha1=${actualSha ?: "ABSENT"}"
                    }
                    perLocation.getOrPut(mutation.describeLocation()) { mutableMapOf() }[target] = actualSha
                    if (mutation.storedReference != null && referenceOf(library, mutation) != mutation.storedReference) {
                        problems += "REFERENCE_MISMATCH at $target ${mutation.describeLocation()}: crc/revision/whirlpool differ"
                    }
                    if (mutation.groupName != null && mutation.newBytes != null) {
                        val named = library.index(mutation.indexId).archive(mutation.groupName)?.id
                        if (named != mutation.groupId) {
                            problems += "NAME_MISMATCH at $target ${mutation.describeLocation()}: group name '${mutation.groupName}' resolves to ${named ?: "nothing"}"
                        }
                    }
                    if (mutation.groupNameHash != null && mutation.newBytes != null) {
                        val hash = library.index(mutation.indexId).archive(mutation.groupId)?.hashName
                        if (hash != mutation.groupNameHash) {
                            problems += "NAME_MISMATCH at $target ${mutation.describeLocation()}: group name hash ${mutation.groupNameHash} not set (hash $hash)"
                        }
                    }
                    if (mutation.fileName != null && mutation.newBytes != null) {
                        val index = library.index(mutation.indexId)
                        val hash = index.archive(mutation.groupId)?.file(mutation.fileId)?.hashName
                        if (hash != index.toHash(mutation.fileName)) {
                            problems += "NAME_MISMATCH at $target ${mutation.describeLocation()}: file name '${mutation.fileName}' not set (hash $hash)"
                        }
                    }
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
            val forTarget = mutations.filter { journalFile(target, it).isFile || storedJournal(target, it).isFile }
            if (forTarget.isEmpty()) return@forEach
            withLibrary(target) { library ->
                forTarget.forEach { mutation ->
                    if (storedJournal(target, mutation).isFile) {
                        restoreStored(library, target, mutation)
                    } else if (absentMarker(target, mutation).isFile) {
                        removeCreated(library, mutation)
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

    /** Undoes a CREATE: a named group created by this transaction is removed whole (name included), otherwise only the file. */
    private fun removeCreated(
        library: CacheLibrary,
        mutation: CacheMutation,
    ) {
        if (mutation.groupName != null || mutation.groupNameHash != null) {
            library.remove(mutation.indexId, mutation.groupId)
        } else {
            library.remove(mutation.indexId, mutation.groupId, mutation.fileId)
        }
    }

    private fun storedJournal(
        target: String,
        mutation: CacheMutation,
    ) = File(journalDir, "${targetSlug(target)}__${mutation.describeLocation()}.stored")

    private fun referenceJournal(
        target: String,
        mutation: CacheMutation,
    ) = File(journalDir, "${targetSlug(target)}__${mutation.describeLocation()}.ref")

    /** The group exactly as the cache stores it (container + version trailer), read without decoding. */
    private fun storedGroup(
        library: CacheLibrary,
        mutation: CacheMutation,
    ): ByteArray? = library.index(mutation.indexId).readArchiveSector(mutation.groupId)?.data

    /** The stored group when the mutation pins it as undecodable ([CacheMutation.undecodableCurrentSha1]) and it still is exactly that. */
    private fun pinnedUndecodable(
        library: CacheLibrary,
        mutation: CacheMutation,
    ): ByteArray? {
        val pin = mutation.undecodableCurrentSha1 ?: return null
        return storedGroup(library, mutation)?.takeIf { CacheItemProbeTool.sha1(it) == pin }
    }

    /** Rollback of a pinned undecodable group: the stored container and its reference entry (crc, revision, whirlpool) come back as they were. */
    private fun restoreStored(
        library: CacheLibrary,
        target: String,
        mutation: CacheMutation,
    ) {
        writeStored(library, mutation, storedJournal(target, mutation).readBytes(), StoredCacheReference.read(referenceJournal(target, mutation)))
    }

    private fun referenceOf(library: CacheLibrary, mutation: CacheMutation): StoredCacheReference {
        val archive = library.index(mutation.indexId).archive(mutation.groupId, null, true)!!
        return StoredCacheReference(archive.crc, archive.revision, archive.whirlpool?.joinToString("") { "%02x".format(it) } ?: "")
    }

    /** Same raw-sector mechanism for in-process rollback and a later process restoring the persisted journal. */
    private fun writeStored(library: CacheLibrary, mutation: CacheMutation, bytes: ByteArray, reference: StoredCacheReference) {
        val index = library.index(mutation.indexId)
        check(index.writeArchiveSector(mutation.groupId, bytes)) {
            "could not restore the stored group ${mutation.describeLocation()}"
        }
        val archive = index.archive(mutation.groupId, null, true)!!
        archive.crc = reference.crc
        archive.revision = reference.revision
        archive.whirlpool = reference.whirlpool.takeIf { it.isNotEmpty() }?.let { hex ->
            ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
        // The file table stays (the write kept the same file ids, here file 0): only the content and the reference entry go back.
        archive.read = false
        archive.unFlag()
        index.flag()
    }

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
        val DEFAULT_JOURNAL_ROOT = RspsPaths.root("import-journal")

        /** Game-server and file-server ports; either one listening means the live caches are in use. */
        val LIVE_PORTS = listOf(50015, 50016)

        /** Only the two live caches are guarded; tests and probes work on temp copies. */
        val LIVE_CACHE_PATHS = listOf("RSPS/game/game/data/cache", "RSPS/file-server/cache")

        const val ALLOW_LIVE_ENV = "RSPS_ALLOW_LIVE_CACHE_WRITE"

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
    /** Archive 7 with a model id >= [ModelArchive.EXT_BASE] is stored as archive 37, group id - 65536 ([ModelArchive]). */
    indexId: Int,
    groupId: Int,
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
    /**
     * Name of a group this mutation CREATES in a named index (rev-667 map squares `m<x>_<z>` / `l<x>_<z>`, resolved by the client through
     * the name hash). Applied as the archive's hash name (displee `toHash`, = `String.hashCode`, e.g. m48_57 -> -1124605456); preflight
     * refuses a name another group already carries; rollback of the CREATE removes the whole group; verify checks the name resolves to
     * [groupId] on every target. Null = unnamed / existing group.
     */
    val groupName: String? = null,
    /**
     * Raw name hash of a group this mutation CREATES, for indexes looked up by an int key rather than a string: rev-667
     * client-script triggers are found by `js5.getgroupid(id)` with id = trigger | (key << 10) (ClientScriptList.trigger).
     * Same preflight / verify / rollback rules as [groupName].
     */
    val groupNameHash: Int? = null,
    /**
     * Name of the file this mutation writes, for indexes whose files are looked up by name (world map data: the client
     * reads `getfile("<area>", "area")`). Applied as the file's hash name; verify checks it. Null = keep / unnamed.
     */
    val fileName: String? = null,
    /**
     * N10: SHA-1 of the group AS STORED (container + version trailer, displee `readArchiveSector`) for a group whose current
     * content cannot be decoded - a rev-667 map loc file `l<x>_<z>` that is XTEA-encrypted while no key for it exists. The
     * stored bytes are the pin instead of [expectedCurrentSha1]: preflight classifies them REPLACE, the journal keeps them
     * as stored (plus the reference entry), the write replaces the group without decoding it, and rollback writes the stored
     * container back. After the write the group decodes normally, so a re-run is a NO_OP.
     */
    val undecodableCurrentSha1: String? = null,
    /** Journal restore only: [newBytes] is a complete stored single-file group; its current RAW SHA1 is [expectedCurrentSha1]. */
    val storedReference: StoredCacheReference? = null,
) {
    val indexId: Int = ModelArchive.indexOf(indexId, groupId)
    val groupId: Int = ModelArchive.groupOf(indexId, groupId)

    init {
        require(undecodableCurrentSha1 == null || newBytes != null) { "an undecodable group can only be replaced, not removed" }
        require(storedReference == null || (newBytes != null && fileId == 0 && xtea == null && undecodableCurrentSha1 == null &&
            groupName == null && groupNameHash == null && fileName == null)) { "raw restore requires an existing single-file group" }
    }

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

/** Reference metadata paired with a .stored backup; parse it before any write can start. */
data class StoredCacheReference(val crc: Int, val revision: Int, val whirlpool: String) {
    init {
        require(whirlpool.isEmpty() || whirlpool.matches(Regex("[0-9a-f]{128}"))) { "invalid journal whirlpool" }
    }

    companion object {
        fun read(file: File): StoredCacheReference {
            val fields = file.readLines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            return StoredCacheReference(fields.getValue("crc").toInt(), fields.getValue("revision").toInt(), fields.getValue("whirlpool"))
        }
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
