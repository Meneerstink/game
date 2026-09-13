package gg.rsmod.game.tools.importer

/**
 * The smallest allocator that satisfies Phase B's Strategy A decision
 * (`RSPS_CURRENT_SPRINT.json` / `RSPS_IMPORT_MANIFEST.yml`, owner-approved unattended engineering
 * run 2026-09-04): [ModelNamespaceCensusTool] proved the only ids this project can currently write a
 * *new* model into without risking an untraced NPC/object/identity-kit reference are the contiguous
 * run strictly above the proven ceiling (65520..65535 at the time of that census - 16 ids, following
 * on directly from the 3 the Twisted Bow import already consumed at 65517-65519). That range is
 * small but is exactly what the project's own explicitly-planned next phase needs (a first proof
 * batch of ~3-5 simple modern gear items), so a plain deterministic sequential allocator over it is
 * the whole allocator - no best-fit search, no fragmentation optimizer, no hole-reuse. Hole-reuse
 * (Strategy B) is intentionally NOT implemented: it would require extending
 * [ModelNamespaceCensusTool] to trace NPC/object/identity-kit model references first, which this
 * project does not yet have a proven decoder for.
 *
 * This class only ever *decides* a candidate id. It never writes a cache or a durable mapping -
 * that remains [CacheTransaction]/[ModelTransactionTool]'s and the caller's job respectively (see
 * [resolve]'s doc for the full allocation-vs-commit lifecycle this separation exists to protect).
 */
object ModelIdAllocator {
    /**
     * Decides the local model id a given `(sourceIdentity, role)` pair should use, or returns the
     * id it already durably owns.
     *
     * Lifecycle this method is one step of (the rest belongs to the caller, using
     * [CacheTransaction]/[ModelTransactionTool] unchanged - this method does not journal, write or
     * commit anything):
     * ```
     * resolve existing mapping -> determine candidate (THIS METHOD) -> provisional reservation
     *   (the caller's batchReserved set) -> CacheTransaction PREFLIGHT -> JOURNAL -> APPLY -> VERIFY
     *   -> durable mapping commit (the caller writes RSPS_IMPORT_ASSET_MAP.yml only AFTER `verify()`
     *   reports no problems)
     * ```
     * A failed or not-yet-applied transaction must never cause a durable mapping to be written for
     * the id this method returned - that is enforced by the caller, not by this method, since this
     * method has no way to know whether its caller's transaction ultimately succeeds.
     *
     * @param sourceIdentity a stable identifier for the upstream asset (e.g. `"upstream_item_id:1234"`
     *   or an explicit source hash) - stable so the SAME source resolves to the SAME local id on
     *   every rerun, matching an already-committed [existingMapping] entry exactly rather than
     *   allocating a second id for an asset already imported.
     * @param role a short label distinguishing multiple models the same source needs (e.g.
     *   `"inventory"`, `"male worn"`, `"female worn"`) - part of the mapping key, since one source
     *   item legitimately owns several distinct model ids.
     * @param existingMapping every durably committed `(sourceIdentity, role) -> local id` pair this
     *   project already knows about (in production, read from `RSPS_IMPORT_ASSET_MAP.yml`). Checked
     *   FIRST and unconditionally wins - an existing mapping is never re-allocated, and this method
     *   never validates or second-guesses it (a mapping pointing at unsafe/occupied/inconsistent
     *   state is an integrity conflict for the caller's census/transaction step to catch, not this
     *   method to silently paper over).
     * @param provenSafeFreeStart the first id proven safe by a **fresh** [ModelNamespaceCensusTool]
     *   run against both real target caches (its `Report.safeFreeRange.first`) - never a cached or
     *   assumed value, so a candidate is always computed against the namespace's actual current
     *   state.
     * @param provenSafeFreeEnd the last id in that same proven-safe range (`Report.safeFreeRange.last`).
     * @param batchReserved ids already handed out by an earlier [resolve] call within the same
     *   in-progress import batch but not yet durably mapped - required so two different new assets
     *   planned in one batch can never collide on the same candidate before either has committed.
     * @throws IllegalStateException if no id in the proven-safe range remains unreserved - a genuine
     *   capacity blocker (Strategy C territory), never silently widened into the unverified range
     *   below the census ceiling.
     */
    fun resolve(
        sourceIdentity: String,
        role: String,
        existingMapping: Map<String, Int>,
        provenSafeFreeStart: Int,
        provenSafeFreeEnd: Int,
        batchReserved: Set<Int> = emptySet(),
    ): Int {
        require(provenSafeFreeStart in MIN_MODEL_ID..MAX_MODEL_ID + 1) {
            "provenSafeFreeStart=$provenSafeFreeStart outside the valid unsigned-short namespace ($MIN_MODEL_ID..$MAX_MODEL_ID)."
        }
        require(provenSafeFreeEnd <= MAX_MODEL_ID) {
            "provenSafeFreeEnd=$provenSafeFreeEnd exceeds MAX_MODEL_ID=$MAX_MODEL_ID - rev-667 model ids are unsigned " +
                "shorts and cannot address anything above $MAX_MODEL_ID."
        }

        val key = mappingKey(sourceIdentity, role)
        existingMapping[key]?.let { return it }

        val candidate = (provenSafeFreeStart..provenSafeFreeEnd).firstOrNull { it !in batchReserved }
        checkNotNull(candidate) {
            "No proven-safe free model id remains in $provenSafeFreeStart..$provenSafeFreeEnd for " +
                "'$key' (${batchReserved.size} id(s) already reserved this batch). This is a genuine " +
                "capacity blocker - re-run ModelNamespaceCensusTool to confirm current capacity before " +
                "considering a namespace-scaling decision; do not fall back to an unverified hole below " +
                "the census ceiling."
        }
        return candidate
    }

    /**
     * Strategy B (hole reuse): the same existing-mapping-first contract as [resolve], but the candidate
     * pool is an explicit, ascending list of ids that a **fresh** [ModelNamespaceCensusTool] run proved
     * free - `Report.provenFreeHoles` (absent in both caches and referenced by no traced definition
     * type, graphics defaults included) plus `Report.safeFreeRange`. FeroxImportTool already allocates
     * loc meshes from that same census list. Callers choose the order (e.g. item meshes above 32767 so
     * the low holes stay available to spotanims, whose model field is a signed short).
     */
    fun resolveFromCandidates(
        sourceIdentity: String,
        role: String,
        existingMapping: Map<String, Int>,
        provenFreeCandidates: List<Int>,
        batchReserved: Set<Int> = emptySet(),
    ): Int {
        existingMapping[mappingKey(sourceIdentity, role)]?.let { return it }
        val candidate = provenFreeCandidates.firstOrNull { it in MIN_MODEL_ID..MAX_MODEL_ID && it !in batchReserved }
        return checkNotNull(candidate) {
            "No census-proven free model id remains for '${mappingKey(sourceIdentity, role)}' " +
                "(${provenFreeCandidates.size} candidates, ${batchReserved.size} reserved this batch)."
        }
    }

    fun mappingKey(
        sourceIdentity: String,
        role: String,
    ) = "$sourceIdentity|$role"

    const val MIN_MODEL_ID = ModelNamespaceCensusTool.MIN_MODEL_ID
    const val MAX_MODEL_ID = ModelNamespaceCensusTool.MAX_MODEL_ID
}
