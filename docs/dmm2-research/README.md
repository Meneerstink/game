# DMM2 research handoff

Entry point: [REPORT.md](REPORT.md). This is phase 1 research and a proposed plan, not an implementation or runtime acceptance.
The full report is also appended to `C:\RSPS\team\dossiers\DMM-PAKKET-2.txt` under a dated ChatGPT-A entry.

Evidence:

- `wiki-revisions.md`, `wiki-manifest.json`, `wiki/`: page revisions, UTC timestamps, permanent links and captured wikitext/API responses.
- `owner-decisions-current-literal.md`: relevant literal decisions, source line numbers and hashes. The earlier broader snapshot is preserved as `owner-decisions-literal.txt`.
- `code-inventory.*`, `inventory-supplement.*`: source locations and hashes; lexical hits include comments and are not a call graph.
- `cache2686-items.json`, `cache2686-npcs.json`, `cache-item-matrix.md`: decoded pinned source definitions and local name comparisons. Source IDs are not local runtime IDs.
- `source-assets-presence.json`: hashes and presence of selected resource candidates. Presence does not prove semantic animation, sound, icon or UI binding.
- `validation.json`: integrity checks plus explicitly unresolved gaps. Missing source item candidate 34607 is recorded separately; zero integrity failures does not mean all requested dependencies have been identified.

Only research outputs are written by the Python helpers. Run them from this worktree root:

```powershell
python docs/dmm2-research/probe_dependencies.py
python docs/dmm2-research/finish_research.py
python docs/dmm2-research/assemble_report.py
```

`--wiki` on `finish_research.py` refreshes supplemental public sources and their revision records. It deliberately does not rebuild the earlier complete wiki manifest from scratch. `probe_cache.py` and `probe_ui.py` are preserved from the interrupted research; they perform broader scans, and rerunning the former should be followed by `probe_dependencies.py` to restore supplemental dependency coverage.

No Gradle, cache import/apply, player save inspection, client operation or deployment was performed. Approval of the proposed plan and decisions must be recorded in the dossier before phase 2.
