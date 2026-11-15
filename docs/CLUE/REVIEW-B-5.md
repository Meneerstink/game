# B continuation: 15 November 09:29

Base: `2c6cf2453`, branch `gpt-b/clue-scrolls-5`. A runs 88209 and 71870 are not counted as integration acceptance.

## STASH transaction backend

`TreasureTrailStashes.kt` reads all 17 selected source units and their five source recipes. A per-source-object save string contains the complete item set, including ItemAttribute values; the same account save holds inventory, equipment and storage together. Storage always prefers inventory, checks the equipped-item setting and existing unequip guards, and calls the existing unequip hooks after committing. Partial sets, partial withdrawals, corrupt storage, missing tools/materials and duplicate construction cannot spend items.

Tool sources now include Hammer/Saw, main-hand and off-hand Imcando/Amy variants, and Crystal saw with revisions/hashes in sources/manifest.json. Crystal saw grants +3 on top of visible boosts and loses no charges when building STASH. Broken hammer and crystal seed are excluded. Optional unmapped modern tools cannot become an automatic pass; ordinary hammer/saw and the verified 667 crystal saw remain available.

Validation: `python tools/clue/build_stash_catalogue.py` and `python tools/clue/check_compile.py HEAD stash-tests`: **9 tests, zero failures**, fresh isolated output, 512m compiler/256m JUnit, below-normal priority. Includes mixed nail metals, missing final gear item, full/partly-full inventory, equipped-item denial, inventory priority, charged-item save roundtrip, second account and corrupt storage. This is backend acceptance only.

Still required for this part: scenery/selector imports and preflight; world placement; contextual Build/Search handlers with the three-tick build; equipped-item setting UI; chart/noticeboard UI; client/collision proof. No STASH runtime registration or cache write occurred.

The lockpick in the raw magic-axe-hut source template is already correctly excluded from the generated equipment array; no nonexistent gameplay bug was patched.

## 10:03 scene/codec checkpoint

Source hash/mapping proof and the exact 69-mutation transaction are in CLAUDE-HANDOFF.md. Shared ObjectDef fallback correction and new opcode 242 need core review. Ordinary loc bytes are unchanged. A separate staged real-client LocType decoder passed the same four server-produced fixtures (77/92/242 with visible/invisible defaults), and real IndexedImage preserved the source binary texture cutout. All ten current STASH tests and four current importer tests passed standalone with fresh outputs. New world/script integration is not accepted until the frozen targeted Gradle run completes.

No client/server operation, canonical client edit, cache application or import-ledger mutation occurred. Remaining STASH gates: animation/audio, setting UI, chart/Watson board, actual placement/collision/client verification.

## Frozen integration and further source work, 10:54

`build/clue/integration-20261115-090652`: all 4,433 Kotlin/Gradle/runtime catalogue input hashes stayed identical. Core, plugin libraries and every plugin script compiled. The 29 explicitly selected classes produced 111 tests, zero skipped, with two failures in `OsrsMimicImportTests` caused by its old relative `../../data/cache` path. The other 109 tests passed. This run is **not green**. Only that test class is being rerun after changing its read-only cache path; runtime catalogues still come from this worktree through test-init.gradle.

`CHALLENGE-CANDIDATES.json` records seven individually sourced Falo lyrics and thirteen Sherlock tasks, with full source rows/requirements and source revisions/hashes. It is research only. Barrows gloves 7462 are the existing native item named `Gloves`, independently confirmed by their source page, the native Barrows-themed examine and matching inventory/worn model references. This does not authorize a generic Gloves alias. Numeric challenge reading is optional; wrong answers have no penalty. Sherlock requires the actual successful action while the challenge is held, not possession of the result. Jorral's transcript is now pinned. Challenge-item replacement/ownership and all action adapters still need implementation.

The source STASH chart 493 is decoded with its actual frame/row/tab scripts. `stash-chart-baked.json` contains 130 review components and 34 personal built/filled checkmarks for the selected 17 units. Source enum order, two-column row geometry, real native p12 font measurements and responsive frame alignments are retained. Beginner is hidden and Master is labelled 78. Tab hover, small-viewport behavior, cache import and runtime bindings remain open; this JSON is not an applied interface.

`cache/watson-source-npcs.json` proves the installed source 7303 has Talk-to only, while post-introduction source 7304 adds Check in slot 3; every other decoded field is identical. The existing 14406 installation represents 7303. Its unconditional Check script binding is therefore an activation/boot gate until proper source-backed personal pre/post-introduction representation is provided. Do not merely assume that the existing handler makes the client option available.

### 11:00 cache-test correction accepted

The isolated `:game:plugins:test --tests '*OsrsMimicImportTests*' --priority low` rerun completed successfully in 8m30s: two tests, zero failures/errors/skips (XML timestamp 09:57:40 UTC). Together with the 109 unaffected passing tests on the frozen snapshot, all 111 selected checks are now green. `rsps.clue.test.cache` can explicitly override the read-only installed cache, whose default is `RspsPaths.root("game/game/data/cache")`; rsps.data remains the reviewed worktree's catalogues. No cache copy or change was needed. New Watson variants/chart work after this snapshot is not covered by that acceptance.
