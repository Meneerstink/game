# Tested staged adapters awaiting the frozen map integration

B16-Nov01:27. These .txt snapshots are NOT active source files and MUST NOT be
counted as completed gameplay. They protect this work across a stopped session.
SHA256s pin exact tested files; source counterparts remain build/clue/next.

1. Wait for integration-20261115-235508 summary.json accepted=true (4core/14plugin,
   4519immutable inputs). No Kotlin/cfg/Gradle edits during it.
2. Uri: promote TreasureTrailUri/Emotes libraries and two tests, copy
   cache/uri-source.json to data/cfg/clue/uri.json. Add the emotes script. Merge
   ONLY the new perform call into ClueScrollManager.tryEmote (the full snapshot
   is a reference, not permission to overwrite later manager changes).
3. Puzzle UI: promote CluePuzzleInterfaceBake/test and merge the small staged
   ClueInterfaceImportTool delta (--puzzles, extra source models, Bake delegation,
   @JvmOverloads preserving Java callers). Source input cache/puzzle-interface.json.
4. Extend game test fixture inputs, standalone verifier production sources/tests,
   and the targeted integration to include new classes. First compile actual
   promoted sources; then ONE new narrow Gradle/scripts run on frozen inputs.

Proof: uri-staged/result.txt nine tests green with both libraries AND the actual
modified manager compiled. Wrong gear/emote/plane, second-emote order, no duplicate
Uris, wrong account/NPC, replacement/bank/death/replay, transaction and spawn failure.
First six-test attempt had one MockK nested getter verification failure; moved
that getter outside verify, then added source tests/spawn check and nine passed.
Two source tests verify all25current phrases/SHA and existing actor head/BAS.

Puzzle UI: two fresh tests green including actual staged importer compilation;
actual native client Component decoder199/199 green,168real models+25Move cells.
PREFLIGHT tx-20261116-002621 (see exact header in PUZZLE-UI-PREFLIGHT.tsv) is only
a proposal, with both caches/ledger/interfaces mapping unchanged. Never apply a
preflight TSV as mapping. 170models+199components+2sprites; no168tile-item imports.
Rerun with shared interfaces.tsv AFTER reward/text and maps; proposals overlap.

Uri lifetime/appearance animation still lack pinned numeric/sequence source;
no invented timer/teleport. Sessions clear when held-clue contract changes and
on logout/death. Only12URI steps (easy/medium/elite) enter this adapter. Five
hard/78 double-agent steps remain excluded until the guardian encounter exists;
no first-step or defeat-rule bypass. Pure source-point matching, no invented radius.

Puzzle draw/import is NOT gameplay activation: NPC handover, held-clue puzzle
state/close/solve/hand-in/death/duplicate-box ownership still required. Source
light-box wiring and sliding/light split probability remain unobserved; do not
invent either. No server/client start, cache apply, merge, push or release here.
