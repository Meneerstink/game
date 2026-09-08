package gg.rsmod.game.tools.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase B allocator gate (`RSPS_CURRENT_SPRINT.json`, owner-approved unattended engineering run
 * 2026-09-04): every required invariant for [ModelIdAllocator.resolve] exercised directly, since the
 * allocator itself never touches a cache - its correctness is entirely a function of its inputs and
 * outputs.
 */
class ModelIdAllocatorTests {
    private val safeStart = 65520
    private val safeEnd = 65535

    @Test
    fun normalAllocationLandsInTheProvenSafeRange() {
        val id = ModelIdAllocator.resolve("upstream:1", "inventory", emptyMap(), safeStart, safeEnd)
        assertTrue("expected $id in $safeStart..$safeEnd", id in safeStart..safeEnd)
        assertEquals("first candidate in an empty batch is the range start", safeStart, id)
    }

    @Test
    fun existingMappingIsReusedInsteadOfAllocatingAnew() {
        val existing = mapOf(ModelIdAllocator.mappingKey("upstream:1", "inventory") to 65517)
        val id = ModelIdAllocator.resolve("upstream:1", "inventory", existing, safeStart, safeEnd)
        assertEquals("an already-committed mapping must win, even though it sits outside the proven-safe range", 65517, id)
    }

    @Test
    fun repeatedResolutionOfTheSameSourceReturnsTheSameId() {
        val first = ModelIdAllocator.resolve("upstream:42", "male worn", emptyMap(), safeStart, safeEnd)
        val existingAfterFirst = mapOf(ModelIdAllocator.mappingKey("upstream:42", "male worn") to first)
        val second = ModelIdAllocator.resolve("upstream:42", "male worn", existingAfterFirst, safeStart, safeEnd)
        assertEquals("resolving the same source twice, once its mapping is durable, must be idempotent", first, second)
    }

    @Test
    fun duplicateSourceImportDoesNotConsumeASecondId() {
        val existing = mapOf(ModelIdAllocator.mappingKey("upstream:1", "inventory") to 65520)
        // Simulate a rerun of an already-completed import: the batch-reserved set from a hypothetical
        // first attempt is irrelevant, because the mapping short-circuits before any candidate search.
        val id = ModelIdAllocator.resolve("upstream:1", "inventory", existing, safeStart, safeEnd, batchReserved = setOf(65521, 65522))
        assertEquals(65520, id)
    }

    @Test
    fun twoDifferentNewAssetsInOneBatchNeverReceiveTheSameId() {
        val first = ModelIdAllocator.resolve("upstream:A", "inventory", emptyMap(), safeStart, safeEnd)
        val second = ModelIdAllocator.resolve("upstream:B", "inventory", emptyMap(), safeStart, safeEnd, batchReserved = setOf(first))
        assertTrue("second candidate must differ from the first", second != first)
        assertEquals(safeStart + 1, second)
    }

    @Test
    fun physicalOrReferencedOrMappedIdsAreNeverInTheCandidateRangeByConstruction() {
        // The allocator trusts its caller's provenSafeFreeStart/End (a fresh census's
        // safeFreeRange), so a candidate can only ever land inside that range - proving this is
        // really a property of ModelNamespaceCensusTool's ceiling computation
        // (ModelNamespaceCensusToolTests), not of the allocator. This test only proves the allocator
        // never wanders outside the bounds it was given.
        val id = ModelIdAllocator.resolve("upstream:X", "inventory", emptyMap(), safeStart, safeEnd)
        assertTrue(id in safeStart..safeEnd)
    }

    @Test
    fun exactNamespaceUpperBoundaryIsUsable() {
        val id =
            ModelIdAllocator.resolve(
                "upstream:last",
                "inventory",
                emptyMap(),
                provenSafeFreeStart = ModelIdAllocator.MAX_MODEL_ID,
                provenSafeFreeEnd = ModelIdAllocator.MAX_MODEL_ID,
            )
        assertEquals(65535, id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRangeUpperBoundIsRejected() {
        ModelIdAllocator.resolve(
            "upstream:overflow",
            "inventory",
            emptyMap(),
            provenSafeFreeStart = 65535,
            provenSafeFreeEnd = ModelIdAllocator.MAX_MODEL_ID + 1,
        )
    }

    @Test
    fun oneSafeFreeIdRemainingStillWorks() {
        val id = ModelIdAllocator.resolve("upstream:tight", "inventory", emptyMap(), 65535, 65535)
        assertEquals(65535, id)
    }

    @Test(expected = IllegalStateException::class)
    fun zeroSafeFreeCapacityFailsExplicitly() {
        // An empty range (start > end) - exactly what a fully exhausted census reports.
        ModelIdAllocator.resolve("upstream:none-left", "inventory", emptyMap(), provenSafeFreeStart = 65536, provenSafeFreeEnd = 65535)
    }

    @Test(expected = IllegalStateException::class)
    fun fullyReservedRangeFailsExplicitlyEvenWhenNonEmpty() {
        // Capacity exists on paper (one id) but every id in it is already claimed this batch.
        ModelIdAllocator.resolve("upstream:crowded", "inventory", emptyMap(), safeStart, safeStart, batchReserved = setOf(safeStart))
    }

    @Test
    fun sameInputAndSameStateAlwaysProducesTheSamePlan() {
        val a = ModelIdAllocator.resolve("upstream:stable", "inventory", emptyMap(), safeStart, safeEnd)
        val b = ModelIdAllocator.resolve("upstream:stable", "inventory", emptyMap(), safeStart, safeEnd)
        assertEquals("no random selection, no unstable enumeration order", a, b)
    }

    @Test
    fun existingTwistedBowMappingsAreNeverReallocated() {
        val existing =
            mapOf(
                ModelIdAllocator.mappingKey("upstream_model:32799", "inventory") to 65517,
                ModelIdAllocator.mappingKey("upstream_model:32674", "male worn") to 65518,
                ModelIdAllocator.mappingKey("upstream_model:39561", "female worn") to 65519,
            )
        assertEquals(65517, ModelIdAllocator.resolve("upstream_model:32799", "inventory", existing, safeStart, safeEnd))
        assertEquals(65518, ModelIdAllocator.resolve("upstream_model:32674", "male worn", existing, safeStart, safeEnd))
        assertEquals(65519, ModelIdAllocator.resolve("upstream_model:39561", "female worn", existing, safeStart, safeEnd))
        // And a genuinely new asset resolved alongside them must still land in the untouched
        // proven-safe range, never anywhere near the existing Twisted Bow ids.
        val next = ModelIdAllocator.resolve("upstream_model:99999", "inventory", existing, safeStart, safeEnd)
        assertEquals(safeStart, next)
    }

    @Test
    fun heavilyReservedRangeStillAllocatesDeterministically() {
        val reserved = (safeStart until safeEnd).toSet() // every id but the last
        val id = ModelIdAllocator.resolve("upstream:last-slot", "inventory", emptyMap(), safeStart, safeEnd, batchReserved = reserved)
        assertEquals(safeEnd, id)
    }
}
