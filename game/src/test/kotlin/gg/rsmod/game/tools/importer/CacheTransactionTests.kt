package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.ArchiveType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Gate A7 of `RSPS_CURRENT_SPRINT.json`: prove the import transaction's failure paths by running
 * them, rather than by reading [CacheTransaction] and asserting it looks right.
 *
 * Every test builds its own pair of throwaway caches through the same
 * [com.displee.cache.CacheLibrary] the real pipeline writes with, so the lifecycle is exercised
 * against real on-disk cache containers - archive headers, index files, `update()` and reopen
 * included - without ever touching (or copying 239 MB of) the two real target caches.
 *
 * The three behaviours under test are the ones that make a half-import impossible:
 *
 *  - unknown existing content is refused instead of overwritten ([conflictingContentIsRefused]),
 *  - a completed transaction re-runs as a no-op instead of rewriting ([rerunOfAnAppliedTransactionIsANoOp]),
 *  - a transaction whose verification fails can be rolled back to its exact pre-transaction bytes
 *    on every target ([rollbackAfterAnInducedVerifyFailureRestoresBothCaches]).
 */
class CacheTransactionTests {
    @get:Rule
    val scratch = TemporaryFolder()

    /** Any id in the item index; the transaction addresses it exactly as the real pipeline does. */
    private val itemId = 22326
    private val original = "ORIGINAL-DONOR-CLONE".toByteArray()
    private val replacement = "REPLACEMENT-REAL-IMPORT".toByteArray()
    private val foreign = "SOMEBODY-ELSES-WORK".toByteArray()

    @Test
    fun conflictingContentIsRefused() {
        val targets = scratchTargets(original)
        val transaction = transactionFor(targets, replacement, expectedCurrentSha1 = null)

        val plan = transaction.preflight()
        assertEquals("both targets should be classified", 2, plan.size)
        assertTrue(
            "existing unknown content must classify as CONFLICT, got ${plan.map { it.outcome }}",
            plan.all { it.outcome == MutationOutcome.CONFLICT },
        )
        assertEquals("every conflict must be a blocking error", 2, transaction.blockingErrors(plan).size)

        val refusal = runCatching { transaction.apply(plan) }.exceptionOrNull()
        assertTrue("apply must refuse a conflicting plan, got $refusal", refusal is IllegalStateException)

        targets.forEach { assertArrayEquals("$it must be untouched", original, readItem(it)) }
        assertTrue("a refused transaction must not journal anything", !transaction.journalDir.exists())
    }

    @Test
    fun aWrongExpectedSha1IsAlsoAConflict() {
        val targets = scratchTargets(original)
        // Pinning the sha1 of the wrong content is exactly the "I think I know what is there, but I
        // am reading a stale note" case, and must be refused just as hard as pinning nothing.
        val transaction = transactionFor(targets, replacement, expectedCurrentSha1 = CacheItemProbeTool.sha1(foreign))

        assertTrue(transaction.preflight().all { it.outcome == MutationOutcome.CONFLICT })
        targets.forEach { assertArrayEquals("$it must be untouched", original, readItem(it)) }
    }

    @Test
    fun rerunOfAnAppliedTransactionIsANoOp() {
        val targets = scratchTargets(original)
        val pinned = CacheItemProbeTool.sha1(original)

        val first = transactionFor(targets, replacement, pinned, id = "tx-test-noop-1")
        assertTrue(first.preflight().all { it.outcome == MutationOutcome.REPLACE })
        val applied = first.apply()
        assertEquals("one write per target", 2, applied.applied)
        assertEquals("nothing should have been skipped", 0, applied.skipped)
        assertEquals("a verified transaction reports no problems", emptyList<String>(), first.verify())
        targets.forEach { assertArrayEquals("$it should hold the replacement", replacement, readItem(it)) }

        // The identical transaction, run again - the idempotency the pipeline relies on when a run
        // is interrupted and restarted, as this sprint's own run was.
        val second = transactionFor(targets, replacement, pinned, id = "tx-test-noop-2")
        val rerunPlan = second.preflight()
        assertTrue(
            "a completed transaction must re-classify as NO_OP, got ${rerunPlan.map { it.outcome }}",
            rerunPlan.all { it.outcome == MutationOutcome.NO_OP },
        )
        val rerun = second.apply(rerunPlan)
        assertEquals("a no-op rerun must write nothing", 0, rerun.applied)
        assertEquals("a no-op rerun must skip every location", 2, rerun.skipped)
        assertEquals(emptyList<String>(), second.verify())
        targets.forEach { assertArrayEquals("$it should still hold the replacement", replacement, readItem(it)) }
    }

    @Test
    fun rollbackAfterAnInducedVerifyFailureRestoresBothCaches() {
        val targets = scratchTargets(original)
        val transaction = transactionFor(targets, replacement, CacheItemProbeTool.sha1(original), id = "tx-test-rollback")
        transaction.apply()
        assertEquals(emptyList<String>(), transaction.verify())

        // Induce the exact failure this class exists to catch: one target diverges after the write.
        // Doing it out-of-band (rather than through the transaction) is what makes this a test of
        // verify + rollback rather than of the code that produced the divergence.
        writeItem(targets[1], foreign)

        val problems = transaction.verify()
        assertEquals("verify should report a mismatch and a divergence, got $problems", 2, problems.size)
        assertTrue("expected a MISMATCH, got $problems", problems.any { it.startsWith("MISMATCH at ${targets[1]}") })
        assertTrue("expected a TARGET_DIVERGENCE, got $problems", problems.any { it.startsWith("TARGET_DIVERGENCE") })

        assertEquals("rollback must restore every journalled location", 2, transaction.rollback())
        targets.forEach { assertArrayEquals("$it must be back to its pre-transaction bytes", original, readItem(it)) }

        // And the restored state is genuinely the pre-transaction state, not merely equal bytes in
        // one place: a fresh preflight pinned to the original sha1 classifies as REPLACE again.
        val after = transactionFor(targets, replacement, CacheItemProbeTool.sha1(original), id = "tx-test-rollback-after")
        assertTrue(after.preflight().all { it.outcome == MutationOutcome.REPLACE })
    }

    @Test
    fun aGenuineFailureWritingTarget2AfterTarget1IsMutatedRollsBackBothCaches() {
        val targets = scratchTargets(original)
        val target1 = targets[0]
        val target2 = targets[1]
        val target2Dat2 = File(target2, "main_file_cache.dat2")
        assertTrue("precondition: target 2's data file must exist before it is broken", target2Dat2.isFile)

        // Real, unmocked fault injection: right before CacheTransaction opens target 2 for writing
        // (i.e. strictly after target 1's write has already completed), strip write access from
        // target 2's underlying data file. com.displee's CacheLibrary always opens its container
        // files as `RandomAccessFile(file, "rw")`, so this makes the NEXT open of target 2 for
        // writing throw a genuine java.io exception - not a mock, not a pre-seeded conflict, and not
        // a failure that could instead be reached by throwing before target 1's write.
        var target1WasWritableWhenTarget2Broke = true
        val transaction =
            CacheTransaction(
                targets = targets,
                mutations =
                    listOf(
                        CacheMutation.item(
                            itemId = itemId,
                            newBytes = replacement,
                            label = "A7 mid-apply failure test",
                            expectedCurrentSha1 = CacheItemProbeTool.sha1(original),
                        ),
                    ),
                journalRoot = scratch.newFolder("journal-mid-apply"),
                id = "tx-test-mid-apply-failure",
                onBeforeTargetWrite = { target ->
                    if (target == target2) {
                        target1WasWritableWhenTarget2Broke = File(target1, "main_file_cache.dat2").canWrite()
                        assertTrue("target 2 must still be genuinely readable/writable up to this point", target2Dat2.setWritable(false))
                    }
                },
            )

        val plan = transaction.preflight()
        assertTrue("both targets must be replaceable before the induced failure", plan.all { it.outcome == MutationOutcome.REPLACE })

        try {
            val failure = runCatching { transaction.apply(plan) }.exceptionOrNull()
            assertTrue("apply must fail loudly instead of returning a false success", failure is IllegalStateException)
            assertTrue(
                "the failure must report that it already rolled back, not merely that it failed: $failure",
                failure!!.message!!.contains("rolled back", ignoreCase = true),
            )
            // The real underlying I/O failure must still be reachable, proving this was a genuine
            // exception and not a synthetic message.
            assertTrue("the wrapped cause must be the real I/O failure", failure.cause is java.io.IOException || failure.cause is java.io.FileNotFoundException)

            assertTrue("target 1 must have genuinely been open for writing before target 2 broke", target1WasWritableWhenTarget2Broke)

            // Lift the injected fault now that it has done its job. Target 2's real content was never
            // touched by the transaction (its write never started), so restoring write access here is
            // purely undoing the test's own interference, not part of anything under test.
            assertTrue(target2Dat2.setWritable(true))

            // No false success: target 1 was mutated for real and then rolled back, target 2 was
            // never mutated at all because its write never started. Both must be back at the exact
            // pre-transaction bytes - not "close enough", not "target 2 untouched, target 1 stuck on
            // the new value".
            targets.forEach { assertArrayEquals("$it must be back to its pre-transaction bytes", original, readItem(it)) }

            // Recovery barrier (task 3): the interrupted transaction must not leave the caches in an
            // ambiguous state that a later import could misread. A fresh transaction pinned to the
            // original bytes classifies REPLACE again on every target - proving the real state is
            // unambiguously the pre-transaction state, not something a later importer would need to
            // guess about or that would silently NO_OP against stale expectations.
            val after =
                transactionFor(targets, replacement, CacheItemProbeTool.sha1(original), id = "tx-test-mid-apply-failure-after")
            assertTrue(
                "post-recovery preflight must classify REPLACE on every target, proving no ambiguous half-state remains",
                after.preflight().all { it.outcome == MutationOutcome.REPLACE },
            )
        } finally {
            // Restore write access so JUnit's TemporaryFolder can clean up the scratch directory.
            target2Dat2.setWritable(true)
        }
    }

    @Test
    fun rollbackFailureOnOneCommittedTargetDoesNotPreventRollbackOfOthersAndIsReportedHonestly() {
        // The single-target-2 fault test above cannot exercise this: it breaks target 2 BEFORE
        // CacheLibrary(target2) is even constructed, so target 2 never enters `committed` and
        // rollbackCommitted() never has to retry restoring a target whose fault is still live. Here,
        // three targets are used so a real, unmocked fault can strike target 1 - which already
        // completed a genuine write and is therefore in `committed` - at the exact moment target 3's
        // write fails, simulating a persistent fault (disk-level condition, lock, permission) that
        // outlives the original failure and defeats the very first attempt to undo it.
        val targets = scratchTargets(original, names = listOf("game-cache", "file-server-cache", "third-cache"))
        val target1 = targets[0]
        val target2 = targets[1]
        val target3 = targets[2]
        val target1Dat2 = File(target1, "main_file_cache.dat2")
        val target3Dat2 = File(target3, "main_file_cache.dat2")

        val transaction =
            CacheTransaction(
                targets = targets,
                mutations =
                    listOf(
                        CacheMutation.item(
                            itemId = itemId,
                            newBytes = replacement,
                            label = "A7 rollback-of-rollback-failure test",
                            expectedCurrentSha1 = CacheItemProbeTool.sha1(original),
                        ),
                    ),
                journalRoot = scratch.newFolder("journal-rollback-failure"),
                id = "tx-test-rollback-failure",
                onBeforeTargetWrite = { target ->
                    if (target == target3) {
                        assertTrue(target3Dat2.setWritable(false))
                        assertTrue(target1Dat2.setWritable(false))
                    }
                },
            )

        val plan = transaction.preflight()
        assertTrue(plan.all { it.outcome == MutationOutcome.REPLACE })

        try {
            val failure = runCatching { transaction.apply(plan) }.exceptionOrNull()
            assertTrue("apply must fail loudly", failure is IllegalStateException)
            assertTrue(
                "message must honestly report the unconfirmed target instead of falsely claiming full " +
                    "recovery: $failure",
                failure!!.message!!.contains("did NOT confirm restoration", ignoreCase = true) &&
                    failure.message!!.contains(target1),
            )
            assertTrue(
                "message must not also make the clean-recovery claim reserved for a fully successful rollback",
                !failure.message!!.contains("No target was left holding a false success state"),
            )

            // Target 2 was committed alongside target 1, and target 1's rollback failing must not
            // have stopped target 2 from being rolled back.
            assertArrayEquals(
                "target 2 must still have been rolled back despite target 1's rollback failing",
                original,
                readItem(target2),
            )

            // Target 1 is exactly what the honest report promised: still genuinely divergent, because
            // its rollback attempt really did fail. The previous unguarded rollbackCommitted() could
            // not report this - its own uncaught exception would have discarded the fact entirely.
            // Restoring write access first is only about permission, not content: displee opens
            // every container file "rw" even for reads, so target1Dat2 must be writable again before
            // readItem() can open it at all - but that alone doesn't change a single byte already on
            // disk, so this read still genuinely proves the fault-blocked rollback never touched it.
            assertTrue(target1Dat2.setWritable(true))
            assertArrayEquals(
                "target 1 must still hold the unrolled-back replacement - the fault genuinely prevented recovery",
                replacement,
                readItem(target1),
            )

            // Now that the fault has genuinely cleared, recovery is still reachable - this is not a
            // permanently stuck transaction. All three targets have a journal entry (journalling
            // happens for every changing target before any write is attempted), so the public
            // rollback() restores all three; for target 2 and target 3 that is a harmless re-write of
            // bytes they already held. target3Dat2 was also broken at the same moment as target1Dat2
            // and never got a chance to recover on its own, so it needs the same restore here.
            assertTrue(target3Dat2.setWritable(true))
            assertEquals(
                "the public rollback() must still finish what apply()'s internal rollback could not",
                3,
                transaction.rollback(),
            )
            assertArrayEquals("target 1 must now be back to its pre-transaction bytes", original, readItem(target1))
            targets.forEach { assertArrayEquals("$it must be back to its pre-transaction bytes", original, readItem(it)) }
        } finally {
            target1Dat2.setWritable(true)
            target3Dat2.setWritable(true)
        }
    }

    @Test
    fun rollbackOfACreateRemovesTheEntryRatherThanLeavingItEmpty() {
        val targets = scratchTargets(null)
        val transaction = transactionFor(targets, replacement, expectedCurrentSha1 = null, id = "tx-test-create")

        assertTrue(transaction.preflight().all { it.outcome == MutationOutcome.CREATE })
        transaction.apply()
        assertEquals(emptyList<String>(), transaction.verify())

        assertEquals(2, transaction.rollback())
        targets.forEach {
            assertEquals("$it must be back to having no entry at all, not a zero-length one", null, readItem(it))
        }
    }

    @Test
    fun removingSomethingThatIsNotThereIsBlockedRatherThanSilentlySucceeding() {
        val targets = scratchTargets(null)
        val transaction = transactionFor(targets, newBytes = null, expectedCurrentSha1 = null)

        val plan = transaction.preflight()
        assertTrue(plan.all { it.outcome == MutationOutcome.MISSING_FOR_REMOVE })
        assertEquals(2, transaction.blockingErrors(plan).size)
        assertTrue(runCatching { transaction.apply(plan) }.exceptionOrNull() is IllegalStateException)
    }

    /** Q10 pipeline extension: a new map square is a NAMED group; create, verify, re-run and roll back through the transaction. */
    @Test
    fun aNamedGroupIsCreatedWithItsNameAndRolledBackWhole() {
        val targets = scratchTargets(seed = null)
        val mapIndex = 5
        targets.forEach { target ->
            CacheLibrary(target).use {
                // The real rev-667 map index is named; a scratch index has to be flagged the same way.
                it.index(mapIndex).flagMask(com.displee.cache.index.ReferenceTable.FLAG_NAME)
                it.put(mapIndex, "m1_1", "EXISTING-SQUARE".toByteArray())
                it.update()
            }
        }
        val groupId = CacheLibrary(targets[0]).use { lib -> lib.index(mapIndex).archiveIds().maxOrNull()!! + 1 }
        val bytes = "NEW-SQUARE-TILES".toByteArray()
        fun transaction(id: String) =
            CacheTransaction(
                targets = targets,
                mutations = listOf(CacheMutation(mapIndex, groupId, 0, bytes, "named create test", groupName = "m25_55")),
                journalRoot = scratch.newFolder("journal-named-$id"),
                id = id,
            )

        val first = transaction("tx-test-named-1")
        assertTrue(first.preflight().all { it.outcome == MutationOutcome.CREATE })
        assertEquals(2, first.apply().applied)
        assertEquals(emptyList<String>(), first.verify())
        targets.forEach { target ->
            CacheLibrary(target).use { lib ->
                assertEquals("$target: the name resolves to the created group", groupId, lib.index(mapIndex).archive("m25_55")?.id)
                assertEquals("m25_55".hashCode(), lib.index(mapIndex).archive(groupId)!!.hashName)
                assertArrayEquals("$target holds the bytes", bytes, lib.data(mapIndex, groupId, 0))
            }
        }
        assertTrue("a re-run is a no-op", transaction("tx-test-named-2").preflight().all { it.outcome == MutationOutcome.NO_OP })

        assertEquals(2, first.rollback())
        targets.forEach { target ->
            CacheLibrary(target).use { lib ->
                assertEquals("$target: the created group is gone", null, lib.index(mapIndex).archive("m25_55"))
                assertEquals(null, lib.data(mapIndex, groupId, 0))
                assertEquals("$target: the existing square is untouched", "EXISTING-SQUARE", lib.data(mapIndex, "m1_1")?.let { String(it) })
            }
        }
    }

    @Test
    fun aGroupNameAlreadyCarriedByAnotherGroupIsAConflict() {
        val targets = scratchTargets(seed = null)
        targets.forEach { target ->
            CacheLibrary(target).use {
                it.index(5).flagMask(com.displee.cache.index.ReferenceTable.FLAG_NAME)
                it.put(5, "m1_1", "EXISTING-SQUARE".toByteArray())
                it.update()
            }
        }
        val otherId = CacheLibrary(targets[0]).use { lib -> lib.index(5).archiveIds().maxOrNull()!! + 1 }
        val transaction =
            CacheTransaction(
                targets = targets,
                mutations = listOf(CacheMutation(5, otherId, 0, "X".toByteArray(), "taken name", groupName = "m1_1")),
                journalRoot = scratch.newFolder("journal-taken"),
                id = "tx-test-named-taken",
            )
        val plan = transaction.preflight()
        assertTrue("a taken name must be a CONFLICT, got ${plan.map { it.outcome }}", plan.all { it.outcome == MutationOutcome.CONFLICT })
        assertTrue(runCatching { transaction.apply(plan) }.exceptionOrNull() is IllegalStateException)
    }

    private fun transactionFor(
        targets: List<String>,
        newBytes: ByteArray?,
        expectedCurrentSha1: String?,
        id: String = CacheTransaction.newTransactionId(),
    ): CacheTransaction =
        CacheTransaction(
            targets = targets,
            mutations =
                listOf(
                    CacheMutation.item(
                        itemId = itemId,
                        newBytes = newBytes,
                        label = "A7 transaction test",
                        expectedCurrentSha1 = expectedCurrentSha1,
                    ),
                ),
            journalRoot = scratch.newFolder("journal-${scratch.root.list()?.size}"),
            id = id,
        )

    /** Independent scratch caches, standing in for the game and file-server pair (or more). */
    private fun scratchTargets(
        seed: ByteArray?,
        names: List<String> = listOf("game-cache", "file-server-cache"),
    ): List<String> =
        names.map { name ->
            val dir = scratch.newFolder(name)
            // CacheLibrary opens an existing cache and will not conjure one: it fails with
            // FileNotFoundException unless the two container files it seeks into already exist.
            // Empty ones are a valid zero-archive cache, which createIndex() then grows.
            File(dir, "main_file_cache.dat2").createNewFile()
            File(dir, "main_file_cache.idx255").createNewFile()
            CacheLibrary.create(dir.absolutePath).use { library ->
                // createIndex() appends the next index id, so the item index is reached by creating
                // every index below it - the same on-disk shape the real caches have.
                while (!library.exists(ArchiveType.ITEM.id)) {
                    library.createIndex()
                }
                if (seed != null) {
                    library.put(ArchiveType.ITEM.id, itemId ushr 8, itemId and 0xFF, seed)
                }
                library.update()
            }
            dir.absolutePath
        }

    private fun readItem(target: String): ByteArray? =
        CacheLibrary(target).use { it.data(ArchiveType.ITEM.id, itemId ushr 8, itemId and 0xFF) }

    private fun writeItem(
        target: String,
        bytes: ByteArray,
    ) = CacheLibrary(target).use {
        it.put(ArchiveType.ITEM.id, itemId ushr 8, itemId and 0xFF, bytes)
        it.update()
    }

    private fun <T> CacheLibrary.use(block: (CacheLibrary) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }

    private fun assertArrayEquals(
        message: String,
        expected: ByteArray?,
        actual: ByteArray?,
    ) {
        assertEquals(message, expected?.toList(), actual?.toList())
    }

    init {
        // Guards against a future refactor moving the journal default back inside a repository,
        // where transaction binaries could be staged by accident.
        assertNotEquals("", CacheTransaction.DEFAULT_JOURNAL_ROOT)
        assertTrue(
            "the default journal root must stay outside every Git repository",
            !File(CacheTransaction.DEFAULT_JOURNAL_ROOT).absolutePath.replace('\\', '/').contains("/RSPS/game/"),
        )
    }
}
