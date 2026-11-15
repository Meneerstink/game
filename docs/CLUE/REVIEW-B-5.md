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
