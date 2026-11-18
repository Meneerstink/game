# Import preflight side effect and restoration

On 15 November at 00:10, `OsrsItemImportTool clue` was invoked without `--apply`.
The existing `ImportBatchOrchestrator.orchestrate` finalized recovery records
**before** checking its `apply` argument. It appended five already applied N423
trinket records to the shared `C:/RSPS/OSRS_IMPORT_MASTER.yml`.

No cache transaction was applied (`applied=false`, `finalized=0`). The five
records were duplicate local IDs 24232–24236, source identities
`n423_trinket:6/9/12/18/30`. The reader previously ignored their textual identity,
so each invocation could recover and append them again.

B immediately reported the side effect and removed **only this invocation's
exact 670-byte suffix**, after checking the unchanged 2,788,484-byte size, exact
suffix and identical preceding five records. Every earlier byte was preserved.
Restored length: 2,787,814 bytes. Restored SHA-256:
`17c749ea622009a33e23efa7d0aa2002f979479783f6d0163c4d1f628778ece2`.
Machine evidence: `build/clue/dry-run-ledger-restoration.json`.

The proposed fix guards recovery finalization with `apply` and recognizes
textual source identities. `ClueImportDryRunTests` uses throwaway caches and a
pending-recovery fixture to verify that planning leaves cache bytes, the ledger
and journal directories unchanged. Its targeted test result is recorded in the
handoff; the fix is not considered tested merely because this document exists.
