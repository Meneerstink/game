# CLUE — Claude cache/client handoff (in progress)

This card is not ready to merge/release. B resumed the entire card on
`gpt-b/clue-scrolls-7` from `bfbc07b8e` (dossier 15 November 14:3x).
Do not interpret a successful cache preflight as a completed gameplay chain.
All cache application, testserver/client operation, merge and release belong to Claude.

Current continuation/results: `COMBAT-SOURCE-AUDIT.md`, `DIG-GUARDIANS-RUNTIME.md`, `NPC-RESOLUTION-AUDIT.md`; earlier results: `LIGHTBOX-SOURCE-AUDIT.md` / `GUARDIAN-SOURCE-AUDIT.md`.
Cache roster ready as of9e7b09eda:248items/10NPCs/23STASHunits/26maps.
Claude may apply these prepared batches in order now; rerun each allocator after
its predecessors. The unfinished guardian/Mimic runtime does not require waiting
to import these identities. Full gameplay/client/release acceptance remains open.

Watson statistics/pinboard papers are prepared too: WATSON-BOARDS.md,
BOARD-PREFLIGHT.tsv and validation/watson-boards-{actual-source,preflight}.json.
Source221/625 -> proposed1207/1208;31mutations,tx-20261117-124937,applied=false.
Run verify_boards.py then preflight_boards.py on current production source.
Uses existing scenery62628/62629 and the SAME interfaces.tsv; rerun after
preceding UI batches. Wide-IF3 client patch/release precedes UI cache application.
The pinboard text/scroll height is1396 rather than source1200: actual q8 font
layout proves this is needed to keep all87names reachable. Seven fresh tests,
22actualComponent decodes and87nativeFont paragraph rows pass; exact sources,
font metrics and read-only proofs are in WATSON-BOARDS.md/validation.

The older checkpoints below are
historical evidence, with remaining gates explicitly superseded by later entries.

## Continuation 16-Nov21:59: Mimic readiness and preserved originals

The Mimic is deliberately unavailable until its remaining combat exists.
`MimicCombat.ENCOUNTER_IMPLEMENTED=false` is a code gate: setting arenaImported
or filling timing JSON cannot enable a melee-only encounter. Both entry and
casket conversion require the complete readiness state. A selected unavailable
Mimic retains its ordinary casket and first-encounter eligibility; no attempt,
conversion or completion is spent. The existing opt-in probabilities are intact.
Claude17:1x video measurement pins candy impact to approximately4ticks; the
JSON retains its video/frame-sheet hash and unknown candy/forward-step chances.
A fixed candyInterval is no longer a claimed model. Seventeen freshly compiled
Mimic lifecycle/conversion/combat-definition tests passed. Gradle/scripts follow.

`ORIGINAL-POOL-AUDIT.json` enumerates every original41 location and pins its
candidate Wiki identity; it is NOT an active-step list. The initial69 overlaps
15 originals, so contains54 unique additions. Six other original emotes are now
restored through existing Uri/gear/second-emote handlers. Source catalogue75:
19easy/17medium/15hard/12elite/12master;21originals represented,20deferred
(17map assets, modern Emir office scene,2unmapped historical emotes). Three torn
parts still form one trail choice. Count requirement remains OPEN.

The six restored steps require no additional item/NPC identities. Item roster247
and clue-steps NPC roster10 remain unchanged. STASH grows17->23; rerun its batch
and the Watson chart with the current source data, not an older17-unit/130-node
artifact. Chart is now150nodes/46personal marks, with source/native-font rows
fitting the fixed viewport (largest content height141 versus226 available).
Latest exact STASH plan: STASH-PREFLIGHT.tsv, tx-20261116-205658,
23units/85mutations/84asset records; proposed control2408, varbits9979..10001.
Watson chart: STASH-CHART-PREFLIGHT.tsv, tx-20261116-210151,
174mutations/150nodes, proposed group1203/script5425/control2408,
22sprites8955..8976. Both proofs are preserved under validation/restored-*.
These separate plans share unreserved control2408: apply STASH first, rerun the
chart allocator afterwards. Sprite8954 (CoX) is avoided. No cache was applied.
Four chart/import/runtime tests and all150 real Component decoder fixtures pass.

New source STASH bases:28977(Fishing Guild entrance),28958(Lumbridge Swamp),
28972(Party Room),28964(Port Sarim monks),28992(Barbarian Agility),28994(Yanille).
Source28964 uses physical plane1/effective plane0, just like the earlier
Wizards' Tower bridge case. Claude must inspect placement/collision and both
accounts' personal built/filled states, fill/withdraw/full-inventory/relog.
23base+26shared-form definitions now replace the historical17base+22forms.
Historical checkpoint: later entries supersede this count. The current item/NPC
rosters below are ready for Claude to preflight/apply independently; gameplay
readiness checks still protect unfinished encounters. Cache preparation is not
whole-card merge/release acceptance.

## Current map extension, B16-Nov22:42

Supersedes the earlier75-record/9-map checkpoint:26drawings/1998components,
including17restored original maps and4new searches at exact existing scenery.
Details/source conflicts: ORIGINAL-MAPS/README.md.12runtime+10codec tests and
1998real decoder fixtures pass. Whole Gradle/scripts pending below.

PREFLIGHT tx-20261116-212134 passed2025mutations; exact rows MAP-UI-PREFLIGHT.tsv.
Proposals:interfaces1203..1228,models66727..66751,sprites8955/8956. Both caches,
ledger and mappings unchanged:validation/original-maps-preflight.json. Rerun
all allocators after previous batches. Item247/NPC10/STASH23 rosters unchanged.
Current catalogue92(22/26/20/12/12),38originals represented,54unique additions;
three torn parts form one choice. It is not an active-pool or count-acceptance claim.

Queued integration210040 was deliberately cancelled BEFORE compilation to fold
this tested extension into one combined run. Its stale XML counts are not new
evidence; accepted=false. Replacement213117 freezes4543inputs,5core/27plugin
classes. Source/cfg/Gradle inputs stay frozen until result. To resolve its memory
gate under the existing below2GB rule, Gradle--status confirmed11760IDLE7.6;
only that idle4.6GB daemon was stopped. Active4644, servers and client untouched.
The new run is compiling core. No cache/server/client/live operation by B.

## Applying the prepared batches (Claude)

Rerun each batch against the caches produced by preceding batches; commit its
regenerated runtime mapping before continuing. This refreshes every allocator,
including sprites after the CoX8954 allocation.

0. Install/release the reviewed `CLIENT-WIDE-INTERFACE.patch` and
   `CLIENT-WIDE-STASH.patch` **before applying any new wide-model UI or opcode242
   object definitions**. Old clients cannot decode those definitions. The later
   loaded-client checks verify the resulting cache and client together.
1. Combined248-item roster and generated YAML metadata (17-Nov guardian seed); refresh items.tsv and
   reward/log mappings. Then rerun the core-item patch, including source19835/19836
   and the owner's 78 names/Gold-onyx presentation described below.
2. Current10-NPC clue-steps batch; regenerate the complete verified npcs.tsv.
3. Watson/STASH objects, noticeboard, setting and search-menu batches with their
   per-batch preflights and mappings. Keep the owner's POH Study exclusion.
4. Reward/text UI first, maps second, puzzle UI third, Watson papers fourth, sharing interfaces.tsv.
   Rerun the allocator before every application; the older ID ranges overlap.
5. Lightbox, STASH chart/setting and supplemental combat batches use the exact
   sections below. Follow the loaded-client checks with the codecs from step0.
   Keep the Mimic encounter marked unavailable until its full combat/source work
   is complete; arena import/collision checking is only part of that chain.

The item/NPC snapshots under validation/ preserve this session's read-only
proofs. NPC-PREFLIGHT.tsv and ITEM-PREFLIGHT.tsv contain the current proposals.

## Exact item batch

* Source: `C:/RSPS/import-source/openrs2-2686/cache`.
* Batch: `OsrsItemImportTool clue --clue-roster=<this worktree>/data/cfg/clue/import-items.tsv`.
* The current roster has **248 source items** (including notes where marked):
  the reviewed247 plus source22879 (snape grass seed, required by guardian loot).
  The latest **tx-20261117-070628** preflight generated **443 definitions**;
  every source/local/model row is in `ITEM-PREFLIGHT.tsv`. The older247/438
  reports below are historical and must not be used for application.
  These local IDs are proposals, not reservations: rerun against the current
  canonical caches immediately before applying, then regenerate `items.tsv`.
* Use the reviewed importer/forearm fix from this branch. The old importer
  stopped on Ancient/Armadyl/Bandos platebodies (12460/12470/12480).
* Current roster corrections: **remove25612 (unused STASH blueprint; ownerQ5),
  add12139 (numeric elite challenge),19887 and23173 (two exact tree puzzle boxes)**.
  Sherlock's distinct skill challenge12128 remains included. The two-box-only
  dry-run is in PUZZLE-ITEM-PREFLIGHT.tsv; it is not the combined247 allocation.
  Do not apply an older roster or reuse its shifted ID rows.
* Repeat read-only with `python tools/clue/preflight_items.py` after core compile.
  Plan report: `build/clue/item-preflight-current/result.txt`; proof records both
  cache file fingerprints, ledger SHA, roster and mapping SHA before/after.
  Latest combined transaction **`tx-20261117-070628`**, `applied=false`.
  See `DRY-RUN-INCIDENT.md` for the
  corrected ledger-recovery side effect; no cache bytes were applied.
* Obtain/merge the importer's `--yml-out=<review file>` metadata on Claude's
  application run. Replace entries by ID; never blindly append duplicate IDs.
* After application: read-only `ClueCacheInventory` export, then
  `python tools/clue/build_catalogue.py --refresh-local`,
  `python tools/clue/build_mimic_catalogue.py`,
  `python tools/clue/build_entrana_catalogue.py`. Commit the generated mappings.
  No unmapped log/reward/container item may remain.

## Existing core item identities

`ClueCoreCacheTool` defaults to PLAN/PREFLIGHT. Use its source from this branch,
with `--mapping=<regenerated items.tsv>` and `--yml-out=<review fragment>`.
Its eight-item preflight passed with 16 guarded replacements, no cache writes.

| OSRS source | Existing local | Required result |
|---|---|---|
| 2677 | 2677 | Easy clue: Read / Check steps |
| 2801 | 2801 | Medium clue: Read / Check steps |
| 2722 | 2722 | Hard clue: Read / Check steps |
| 12073 | 19043 | Elite clue: Read / Check steps, remove obsolete Scan |
| 20546 | 2714 | Reward casket (easy), stackable |
| 20545 | 2802 | Reward casket (medium), stackable |
| 20544 | 2724 | Reward casket (hard), stackable |
| 20543 | 19039 | Reward casket (elite), stackable |
| 19835 | allocator mapping | **78 clue scroll** |
| 19836 | allocator mapping | **Reward casket (78)**, stackable |

The master source IDs are 19835/19836, never 19837. Use the newly allocated
local IDs from the mapping. Claude applies the owner's Gold & onyx scroll and
gold/onyx-banded casket A recolours (`screens/N415-78clue/`). Source master
mechanics/rewards remain unchanged. No exclusive cosmetic reward is introduced.

For the owner's78presentation use source**19835/19836**, resolved through the
latest`ITEM-PREFLIGHT.tsv` and then the final applied`items.tsv`. All old numeric
proposals (including25192/25193) are superseded; do not hard-code them.

The opening effect is the existing OSRS Treasure Trails jingle, index 11/group
193; the source and local payloads are byte-identical. Test with sound enabled.
The golden world message currently covers the catalogue's mega-rare outcomes.

## UI and arena source assets

Read-only cache evidence establishes:

* OSRS 73 = reward screen, 203 = clue text, 306 = sliding puzzle,
  322 = light box. Raw dumps are under `build/clue/`; final import/runtime
  integration is still pending. Models 3395/1226/3438/4695 and sprites 539/540
  are **not** equivalent to the same numeric local IDs (`ui-asset-evidence.json`).
* Mimic arena is source **10819** (`m42_67`), confirmed by cache 2686 locs
  34675–34732. The room's decorative objects occupy planes 0/1; do not put
  players onto a guessed donor floor. The current shared `ModernRegion.load`
  understands only named archives. Cache 2686 instead uses group=region,
  file 0=terrain, file 1=locations (unnamed, unencrypted). `ClueSceneProbe`
  proves this using the actual source payloads. Final map import is pending.
* Existing Watson 14406, Strange casket 62743 at 1645,3569,1, Mimic 23729,
  combat/noncombat NPCs 14401–14405 exist already. Do not allocate duplicates.

## Claude's loaded-client checks

After A records tested commits and all asset gates pass:

1. Open every tier with room and with a full inventory; verify all rewards,
   private overflow, one counter increment, log pages and golden 78 message.
2. Check all seven clue-log pages, all items/counters, and casket drops in the
   existing viewer style (including player-specific master/case eligibility).
3. Entrana: ordinary alternate tables; 78 casket refused; dungeon uses normal
   tables. Check upper-floor behaviour as well.
4. Watson: first/repeat dialogue, partial/all hand-ins, duplicates, stored master
   collection, bank/grave/Death ownership, full inventory, another account,
   relog, Astral Contact and menu/head sizing. No blueprint sale or POH Study/chart
   (owner10October decision, confirmed Q5); use the Watson noticeboard.
5. Mimic encounter/death/retrieval tests will be detailed when its runtime is
   finished. No fight-ready claim is made by the present prototype/backend.
6. Dropped clues/boxes/caskets, wrong gear/emotes, STASH, dig monsters and the
   limited sourced step set remain explicit gameplay gates.

Open source/owner questions are in `C:/RSPS/team/dossiers/CLUE.txt`; A continues
independent work while Claude obtains missing source evidence.

## A's acquisition and regression checkpoint (15 November)

Source data commit: `0ef8878ea`. No additional cache IDs are allocated by the new
NPC/skilling/impling routes; they use the five scroll boxes in the existing item
roster. Runtime changes are still pending the targeted build, not release-ready.

* `ClueNpcSources`: 564 exact tier routes for 526 NPC identities; old replaced
  clue entries become nothing without changing other slot weights. Normal/Your
  chances share this source and only the appropriate Wilderness RoW modifier.
  This clue roll is outside the Annihilation bracket multiplier. Account-specific
  eligibility must bypass the viewer cache. Preserve the approved renderer.
* `ClueSkilling`: 46 activities from the pinned nest/geode/bottle formulas.
  Visible levels only; independent easy/medium/hard/elite rolls; no beginner,
  78/master skilling roll, 200m modifier, familiar boost or woodcutting cape boost.
  No Combat Achievements system exists here; its rewards are not invented.
* `ClueImplings`: baby/young/gourmet -> easy (100/50/25); earth/essence/eclectic ->
  medium (100/50/25); nature/magpie/ninja -> hard (100/50/25); dragon -> elite (50).
  Check both jars and existing barehanded catch routes, full inventory and cap.
* `ClueOwnership`/`ClueContainers`: include the Mimic IRS in clue ownership and box
  capacity; opening a case does not log a second copy of the already awarded case.
* `ClueGroundItems`: player-dropped clues/Mimics 6000 ticks; boxes 300 ticks at
  award/drop routes; explicit D1-D20 death timings win. `OpHeld5Handler` gets one
  content timing callback after confirmation and requires the exact held Item.
* `MimicCombat`: 230 real HP (DSL input 2300, not 230), poison/venom immunity.
  Its regression builds the actual definition, rather than checking strings.
* Casket/Mimic viewer: conversion replaces ordinary and tertiary contents together;
  the post-fight source includes the ordinary casket extras and fixed remaining
  reward-roll count. First-casket prompt directs to Strange casket; it cannot
  enable Mimic challenges by itself.

Reproduce source generation without refetching or touching a cache writer:

```
python tools/clue/fetch_acquisition_sources.py --from-snapshot
python tools/clue/build_npc_catalogue.py
python tools/clue/build_skilling_catalogue.py
```

The census is read-only against the canonical cache. Source SHA-256 validation
passed for 106 main Wiki pages and five acquisition HTML snapshots at A's first
source commit; the step archive has its own separate manifest. Four core import
tests passed at 02:27 (two forearm, two dry-run). The ongoing run's plugin result
and runtime commits must be recorded before review/merge; no client/live proof yet.


## B checkpoint 15 November 05:15: wide UI models (preflight only)

The normal model archive is full. A fresh census found no free model id that fits
an IF3 u16 model field. The exact source paper/chest therefore use archive 37;
no existing mesh is overwritten, and no synthetic gameplay items are created.

**Install/review `CLIENT-WIDE-INTERFACE.patch` before applying this UI batch.**
It changes only Component's static MODEL decoder and its model cache key:
IF3 type flag 0x40 selects a g4 model id, and model bit 16 gets a separate cache
bit. All legacy component bytes and legacy cache keys remain unchanged. The
existing client Mesh/JS5 archive-37 route is reused. The patch is prepared in
this worktree; B has not edited or built the canonical client.

Reproducible independent codec verification:

```
python tools/clue/verify_interface_port.py
```

This compiled only the three changed server codec files plus the staged actual
client Component against existing outputs: 3 targeted JUnit tests passed. The
client decoded the real server-generated legacy/wide/absent fixtures, retained
104/0/761/914 camera values, consumed every byte, preserved legacy model-cache
keys and separated low model 548 from wide model 66084. It opens no client/window
and changes neither canonical client nor cache. OpenGL rendering still belongs
to Claude's loaded-client check.

Exact preflight invocation (no apply):

```
.\gradlew.bat :game:runClueInterfaceImportTool --priority low --args="C:/RSPS/work/gpt-a/docs/CLUE/cache/interfaces.json C:/RSPS/work/gpt-a/data/cfg/clue/interfaces.tsv C:/RSPS/work/gpt-a/build/clue/interface-preflight.tsv"
```

For Claude's application run, the second argument must be the canonical runtime
`data/cfg/clue/interfaces.tsv`. The tool writes that mapping only after successful
cache verification. Rerun after the 245-item batch and the clue-step NPC batch and any other cache changes.
`INTERFACE-PREFLIGHT.tsv` records transaction `tx-20261115-041434`, applied=false:

* OSRS 73 -> proposed local interface 1196 (17 components; slots 5..16).
* OSRS 203 -> proposed local interface 1197 (3 components; clue text component 2).
* Source models 3395/1226 -> proposed 66084/66085 (archive 37 groups 548/549).
* Source static chest texture 22 / sprite 468 -> texture 1423 / sprite 8922.
  Source texture pixels survive a decode/encode/decode equality check; scrolling
  is zero. Source close sprite 539 -> proposed sprite 8923.
* 26 logical mutations across both caches; all preflight checks passed.

These are proposals, not reservations. Runtime integration of the receipt/text
window and source close-button hover are still to be completed after the current
NPC/Mimic regression run. No UI-ready or release-ready claim is made here.


## A checkpoint 15 November 08:12: current preflights and source steps

Inherited runtime baseline is recorded in `REVIEW-A.md`: **68 tests / 19 classes**,
zero failures/errors, scripts compiled (31m04s). Earlier dossier total 81 was an
arithmetic error; 68 is the XML-derived total. The newer typed step catalogue and
101-slide puzzle generator passed **7 additional tests / 2 explicit classes**
with `python tools/clue/check_compile.py dec080e2e step-tests`.
The later atomic advance / removed-donor-key migration / torn-parts backend is
still awaiting its targeted Gradle run. It is not yet registered as gameplay.

### Item and UI plans

* Current item transaction `tx-20261115-070458`: 245 source roster entries,
  436 definitions including linked notes, proposed local **24755?25190**.
  `ITEM-PREFLIGHT.tsv` is regenerated from this run. The additional 18 source
  step objects are challenge scrolls (2842/7269/12128), puzzle pictures
  (2795/3565/3569/12161/20280/20281/20282/23417), light box and its lamp display
  items (20355/20356/20357), torn parts (19837/19838/19839), and master strange
  device (19939). No beginner objects are added.
* UI transaction `tx-20261115-070939`: **27** logical mutations, not the older
  26-mutation plan. Added source hover sprite 540 -> proposed **8924**; source
  close sprite 539 -> 8923. Interfaces remain proposed 1196/1197; models
  66084/66085; texture 1423; texture sprite 8922. Actual rows are in
  `INTERFACE-PREFLIGHT.tsv`.
* Both runs compared the import-ledger SHA-256 and every cache data/index file's
  size and mtime before/after: unchanged. No application, finalization, runtime
  mapping publication, testserver/client operation or canonical-client edit.
* All allocator IDs are **proposals**, not reservations. Item, NPC and UI plans
  independently used the same free model namespace. Apply in a reviewed order
  and rerun each later batch against the result of the earlier batch. Never
  paste model numbers from these separate dry runs into a combined transaction.

### Exact NPC batch

Current batch: **10 source NPCs**, including Lumbridge Guide306. The table below
is the 16November18:07 proposal. Rerun clue-steps against the current caches and
regenerate the complete mapping after application. No ID is reserved.

```
.\gradlew.bat :game:runOsrsNpcImportTool --priority low --args="clue-steps"
```

Source cache: `import-source/openrs2-2686/cache`. Repeat read-only with
`python tools/clue/preflight_npcs.py`. B ran the compiled tool's PREFLIGHT only:
transaction `tx-20261116-170726`, 168 logical mutations, 70 source asset records.
Complete output: `NPC-PREFLIGHT.txt`; every source/native/asset row:
`NPC-PREFLIGHT.tsv`. The ledger hash, both cache file fingerprints, runtime NPC
mapping and profile hash were unchanged: npc-preflight-current/read-only-proof.json.

| Source | Role | Proposed local NPC |
|---:|---|---:|
| 306 | Lumbridge Guide | 15314 |
| 6586 | Sherlock | 15315 |
| 7306 | Falo the Bard | 15316 |
| 7312 | Master double agent | 15317 |
| 6587 | Armadylean guard | 15318 |
| 6588 | Bandosian guard | 15319 |
| 7307 | Ancient Wizard (mage) | 15320 |
| 7308 | Ancient Wizard (ranger) | 15321 |
| 7309 | Ancient Wizard (melee) | 15322 |
| 7310 | Brassican Mage | 15323 |

Existing Uri5141..5143, hard agents5144/5145, Saradomin wizard1264 and Zamorak
wizard1007 are not duplicated. Lumbridge Guide uses its exact source306 import,
not the unrelated local Lumbridge Sage. Abbot Langley and Robin already exist.
The Guide's ordinary quest-advice dialogue still has the source gap described
in the latest puzzle handover section below.

`data/cfg/clue/guardians.json` contains 14 Wiki/cache-checked guardian/minion variants,
including 76 HP Zamorak wizard, 160 HP master agent, the 80 HP ancient trio and
Brassican's typeless magic. This is source data, **not an activated combat script**.
The NPC importer drops stats because they belong in server combat definitions.
Its report also notes unrepresentable face z-offsets on Brassican's two meshes:
Claude must inspect these in the loaded client before accepting the asset chain.

`STEP-ASSET-EVIDENCE.md` records all 17 selected STASH placements and the actual
unsupported swamp-hut bookcase. The hard Emir's Arena coordinate has no fight;
the two selected master coordinates require Brassican, not a random trio.
The source generator preserves those exceptions and the two master anagram
puzzle choices. The old41-donor-step registration was replaced in2f21cdd90;
the source pool still excludes subtype handlers/assets that are not ready.
No complete-step-pool claim is made by these source/backend commits.

## STASH batch blueprint (source identities; implementation below)

`data/cfg/clue/stashes.json` is the exact source/cache blueprint for the seventeen selected units, including each placement/type/rotation/physical and effective planes, source varbit/varp/bit position, construction recipe, required equipment and asset hashes. The dedicated ClueStashCacheTool below now implements the batch; OsrsLocImportTool is unchanged.

Proposed batch: `clue-stash`. Import/remap the following 39 source object definitions (17 base multilocs + 22 shared forms), rather than using a coincidentally matching 667 numeric ID:

* Bases: 28968,28965,28973,28959,28974,29005,29001,28991,28989,29014,29010,29013,29029,29028,29038,29059,29056.
* Forms: 6657,15943,16014,16015,16016,16017,16018,16019,16024,28933,28936,28937,28940,28941,28944,28945,28948,28949,28950,28951,28952,28953.

The selectors in the blueprint are SOURCE identities only. Allocate collision-free local selectors or implement a proven equivalent per-player visual route, preserving Build/Search options and ordinary terrain/object collision. Do not just copy the source varbit IDs. Every static placement and personal built/filled state must be scoped to the exact unit/tile, not just its shared visual form ID. Cache work stays PLAN/PREFLIGHT for GPT; Claude alone applies after reviewing the complete batch.

Storage rules from pinned STASH revision: full set only; seven legal nail metals (no dragon); ten nails of one metal; no bending; withdrawal all at once; inventory before equipment; equipped items only with the setting. Source catalogue includes boostable level/XP/three ticks, hammer+saw and master Gold leaf 8784. Nothing in this paragraph claims a storage handler or Watson chart exists.

## B checkpoint 15 November 10:03: STASH preflight and mandatory client decoder

`ClueStashCacheTool` PREFLIGHT **tx-20261115-090151** passed: 17 units, **69 logical mutations**, 68 allocated identities. Exact source/local rows: `STASH-PREFLIGHT.tsv`. Proposals: 39 locs **66313..66351**, 17 selectors **9979..9995**, private control varp **2408** (bits 0..16), nine models **66084..66092**, source texture 8 -> **1423**, its sprite -> **8922**. The new control is appended beyond every physical local varp definition/selector reference. Source selectors are never copied into the old client namespace.

**Apply/release `CLIENT-WIDE-STASH.patch` BEFORE using this cache batch**, in the same new client as the existing `CLIENT-WIDE-INTERFACE.patch`. Existing locs already exceed 65535; their multiloc destinations therefore need the staged opcode 242 (u16 selector IDs, signed g4 destinations and fallback). Old loc opcodes 77/92 keep their byte layout. Server ObjectDef previously dropped the legacy fallback: now it retains it like the real 667 LocType decoder. This is shared core code and requires Claude's review. The live/client trees were not edited.

Exact command (first PLAN/PREFLIGHT, Claude alone adds application flag after client review):

```powershell
.\gradlew.bat :game:runClueStashCacheTool --priority low --args="data/cfg/clue/stashes.json data/cfg/clue/stash-mapping.tsv docs/CLUE/STASH-PREFLIGHT.tsv"
```

The tool publishes `stash-mapping.tsv` and appends its identity records to the existing import ledger ONLY after both caches verify. Runtime refuses a plan-only mapping. Do not copy/rename the preflight TSV into runtime. Cache/ledger/mapping proof: `build/clue/stash-preflight/read-only-proof.json` (all cache dat/index file sizes/mtimes plus ledger SHA identical). No apply occurred. Item/UI/NPC/STASH plans currently share unreserved model/sprite/texture IDs; rerun each later batch in the actual chosen application order.

Validation: 10 STASH storage/build regressions, four import/codec regressions; `python tools/clue/verify_stash_client.py` compiles only staged real LocType and checks legacy/wide/default/invisible destinations, byte alignment and actual source texture pixels through the real client IndexedImage. The binary cutout of source texture 8 is preserved (material alpha mode 1), rather than flattened. Source geometry and sprite pixels also pass decode/encode/decode equality. No client window started.

Runtime posts only verified mapped units on their exact effective tiles and routes Build/Search by base identity AND tile/type, so shared forms do not share inventory or state. Build checks resources immediately and again after the source three ticks; materials/XP are spent once. Login restores private built flags; Search stores/returns complete sets with item attributes. Existing scenery at a placement is retained and reported as PROBLEM, never overwritten.

Still open: Construction animation/audio identity verification, the Settings entry for the equipped-item toggle, source chart 493/Watson noticeboard integration, and Claude's loaded-client/collision checks (notably source physical plane 1/effective plane 0 at Wizards' Tower). The remaining clue-step pool/guards/Mimic source gates are unchanged. **This is not full CLUE acceptance.**

## Watson personal pre/post-introduction variants — B, 15 November 11:04

Pinned cache 2686 NPC 7303 has only Talk-to; 7304 adds Check in slot 3. All other decoded fields match. Existing installed 14406 is byte-identical to the pinned pre-introduction conversion, using its ledger-owned models/chathead and existing BAS. `ClueWatsonCacheTool` refuses a mismatch rather than adding an invented option to 7303.

Exact read-only proof: `build/clue/watson-import/read-only-proof.json`, transaction `tx-20261115-100537`. **Four** logical mutations; both caches, ledger SHA and runtime mappings unchanged. Proposed mappings in `WATSON-PREFLIGHT.tsv`:

| Role | Source | Proposed native identity |
|---|---:|---:|
| Pre-introduction variant | NPC 7303 | NPC 15234 |
| Post-introduction variant | NPC 7304 | NPC 15235 |
| Existing physical Watson/root | NPC 7303 | NPC 14406 (owned definition update) |
| Personal introduction selector | local control | varp 2408 |

**These are not reservations.** They overlap the current clue-steps/STASH proposals because none of these batches is applied. Apply this Watson batch after the earlier NPC/STASH batches, rerunning its PLAN/PREFLIGHT then; retain the newly generated exact IDs. No new models, animation or client opcode is needed. NPC opcode 118 is already understood by the actual unmodified 667 client; `python tools/clue/verify_watson_client.py` passed the real decoder on the tested server fixture.

Claude command (first run WITHOUT apply):

```powershell
.\gradlew.bat :game:runClueWatsonCacheTool --args="data/cfg/clue/watson-mapping.tsv docs/CLUE/WATSON-PREFLIGHT.tsv" --priority low
```

Only Claude may add `--apply` after reviewing the fresh preflight. The tool publishes mapping/ledger records only after both caches verify; verification failure rolls back. Preserve all four mapping rows when copying runtime cfg. The root stays 14406 and there is still exactly one spawned Watson.

Runtime: `WatsonAppearance` validates the applied mappings and source menu slots, reconstructs the private varp from the persistent `watson_introduced` attribute on login and updates it after the introduction. Talk-to and item hand-ins route through the real physical/transformed IDs. The previous unconditional missing Check binding is removed; without the batch the pre-introduction Talk-to remains and a PROBLEM identifies the missing post-introduction assets.

Client checks: two accounts with different introduction states must see their own Talk-to/Check menus; first full introduction must unlock Check without relog; check partial/all/duplicate hand-ins and Astral Contact; relog restores the correct menu; one physical NPC, no other NPC menu changes. The server's existing generic NPC fallback behavior was not changed. New Watson scripts still need the next frozen Gradle/plugin-script acceptance; standalone checks are separately recorded in REVIEW-B-5.md.

## STASH chart/Watson noticeboard — B, 15 November 11:18

Source 493 chart, exact 703/711/98 frame, 1478 rows, 1482 tabs and 1483 hover. The baked source and runtime control metadata are generated by `python tools/clue/bake_stash_chart.py`. Source enum order/labels and actual native p12 measurements are preserved for the selected 17 units. Five tabs, no beginner entry, master label 78. Native script 44's exact decoded IF_SETGRAPHIC instructions are expanded to three static tab graphics; the private selector prevents selected-tab hover changes, equivalent to the source's removed selected-tab callbacks. Source colours and selected/unselected sprites are retained.

`STASH-CHART-PREFLIGHT.tsv` is the **non-reserving** exact proposal: transaction `tx-20261115-101610`, **154** logical mutations = 130 components + 22 sprite groups + 1 native callback + 1 local control definition. Interface 493 -> **1196**, script 1483 -> **5425**, control -> **2408**, sprites -> **8922..8943** (each source ID is listed in the TSV). Both caches, ledger SHA and runtime mappings stayed identical in `build/clue/stash-chart-preflight/read-only-proof.json`.

**Run this last after the prior clue UI, STASH scenery, NPC and Watson batches, and rerun PLAN/PREFLIGHT then.** Current proposals overlap those unapplied batches; never reuse the old numbers after applying an earlier batch. Runtime reads only the tool's applied/verified `data/cfg/clue/stash-chart-mapping.tsv` plus `stash-chart.json`. No new client opcode or model is required for this interface. The overall bundle's wide-model/loc client patches still precede their respective cache transactions.

```powershell
.\gradlew.bat :game:runClueStashChartCacheTool --args="docs/CLUE/cache/stash-chart-baked.json data/cfg/clue/stash-chart-mapping.tsv docs/CLUE/STASH-CHART-PREFLIGHT.tsv" --priority low
```

Only Claude may add `--apply` after reviewing the fresh plan. Tool refuses occupied unowned destinations and publishes mapping/ledger only after verified writes to both caches; failed verification rolls back.

Existing noticeboard **62735** (source 29718) is already correctly imported: native/source scene both **1644,3575,0 / type 4 / rotation 1**, option **Read** in slot 1. Read-only identity/scene/hash proof: `cache/watson-noticeboard.json`; its source examine is added to objs.yml. No board duplication or scene write. The handler opens the personal chart only for this actual board. Built and filled marks read the same persistent item storage as STASH Search; corrupt storage is not marked filled and is retained.

Validation: four new targeted tests, no failures; `verify_stash_chart_client.py` compiled the actual unmodified Component decoder and decoded all 130 server-generated components, including three-sprite hover hooks, close hover, row fills, 78 label and complete byte alignment. Frozen Gradle/script integration is the next gate. Client checks: Read/Close; all five tabs; hover selected/unselected; no beginner; long two/three-line labels at fixed and stretched viewports; all 17 built/filled marks before/after store/withdraw; relog; second account; no cropped content or dead clicks. The largest selected source row set is short, but actual viewport/scrollbar correctness remains a client check.

**Q5 resolved, dossier 15 November 11:1x:** retain the owner's 10 October POH decision. No Study, POH chart or blueprint sale/drop. Watson noticeboard provides the STASH overview; its dialogue uses only the pinned source explanation. The unreachable blueprint purchase backend and its obsolete test have been removed. Equipped-item Settings entry and sourced build animation/audio remain open.

## B checkpoint 15 November 14:46: equipped STASH setting

Pinned OSRS struct **6245**, label parameter1086 and help parameter1096, SHA256
`c160459064efde53b1fb24881c3a17c85d8e12d8999e5ed327b63a177f6b1bae`.
The tool reads that actual source struct and refuses a changed hash. It never
copies parameter1077 into a native varp. The existing persistent
`clue_stash_take_equipped` flag controls the setting and actual storage together.
Default off; inventory still takes priority; login and Settings remount restore
the checkbox; closed-pane clicks cannot enable equipment removal.

PREFLIGHT **tx-20261115-134520**, **three** logical mutations. Exact unapplied
proposal: native Settings **261:26/27/28** = toggle layer/checkbox/label, at the
verified free band **176..202**, between the existing button rows. Source checkbox
sprites **8378/8379** are already installed as **8515/8516** by Keybinding; both
native groups were verified byte-identical to cache2686, so no new sprite/model,
client opcode or selector is added. Existing components 0..25 are untouched.
The full source label wraps into the existing sidebar's available width; this is
a 667 Settings presentation, not a claim that its layout is the modern OSRS menu.

```powershell
.\gradlew.bat :game:runClueStashSettingCacheTool --args="data/cfg/clue/stash-setting-mapping.tsv docs/CLUE/STASH-SETTING-PREFLIGHT.tsv" --priority low
```

Run PLAN/PREFLIGHT again after other Settings changes; only Claude adds `--apply`.
Mapping is published only after both caches verify; failure rolls back. The tool
refuses occupied unowned components or growth outside the reviewed layout.
The existing Keybinding tool currently refuses any later Settings additions;
do not rerun it blindly after this batch (it will safely refuse the expanded tab).

`python tools/clue/preflight_stash_setting.py` proved both caches, ledger SHA and
runtime mappings unchanged: `build/clue/stash-setting-preflight/read-only-proof.json`.
`STASH-SETTING-PREFLIGHT.tsv` is a non-reserving proposal, never a runtime mapping.
Five new standalone tests passed (2 importer + 3 setting/storage-flag checks).
`python tools/clue/verify_stash_setting_client.py` compiled the actual unmodified
Component decoder and decoded all three actual server fixtures with full byte
alignment, exact source label, Toggle and checkbox. No window/canonical-client
edit. Actual script/Gradle integration is the next acceptance gate.

Claude checks: Settings fixed/resizable/stretched, full two-line label without
clipping, click and checkbox, close/remount, relog, second account, inventory
priority, equipped-item removal and unequip restrictions. Recheck every old
Settings/Keybinding button. This completes the setting implementation, not the
remaining STASH construction animation/audio or the whole CLUE card.


## Falo and Sherlock services, B 15 November (source implementation)

Use the existing **OsrsNpcImportTool clue-steps** batch, exact source IDs:
**6586,7306,7312,6587,6588,7307,7308,7309,7310**. Do not allocate duplicates or
copy these numeric source IDs into runtime. `NPC-PREFLIGHT.tsv` remains a proposal;
rerun after other imports because all proposals share the same free namespaces.

After Claude actually applies/verifies both caches and the import ledger:

```powershell
python tools/clue/build_npc_mapping.py --write
```

This command only reads caches/ledger and writes the worktree's runtime TSV/profile;
it never applies a cache transaction. Without `--write` it is audit only. Current
audit is **0 verified / 9 waiting** and publishes no runtime NPC mapping. It checks
both caches' actual name, size, menu, combat level, body/head model ledger references
and movement set. Ship the generated `data/cfg/clue/npcs.tsv` with
`npc-assets.json`; runtime rechecks loaded definitions before installing source
Wiki examines. A missing or partial mapping disables these services and prints
PROBLEM rather than guessing a local ID. Source pins: Falo **2689,3549,0/r4**;
Sherlock **2733,3413,0/r5**. Recheck pathing/head models after cache application.

Falo's seven selected source lyrics and Sherlock's thirteen selected tasks are
reproducibly generated from pinned Wiki hashes by `build_challenge_runtime.py`.
Only these supported tasks are selected; no inaccessible full-OSRS task pool is
silently enabled. Appended ItemAttribute fields CLUE_TASK_KEY / CLUE_TASK_COMPLETE
preserve existing ordinals. Per-scroll task survives ordinary item serialization,
drop/re-pick and bank. Exact-account/Item/progress guards protect suspended dialogue.

Sherlock elite replaces its actual clue with source **12128**, preserving all
attributes in the same inventory slot. It blocks another elite in inventory,
bank, grave, Death's Office and Mimic recovery, and Watson accepts it as sourced.
After actual successful skill action, return to Sherlock is required before the
atomic step advance restores the canonical clue or gives the casket. Source
current-level requirements allow temporary boosts. The source's completed-task
priority when another tier is unfinished is preserved. Master stays the owner's
78 clue scroll. Unnoted Falo shown gear is retained, including item attributes.

Claude client checks: all seven lyrics (canonical and accepted ornamented gear),
wrong/noted item, full inventory, relog, bank/drop, death, second account, pending
click after clue replacement. Sherlock: all thirteen real actions with the issued
paper held; wrong product/location, banked paper, temporary boost/drain; return
before advance; both elite/78 assignments; Check completion / Check steps;
Watson in full inventory; no duplicate tier via boxes or loot.

UI adaptation choice (owner 15 November 14:4x allows following own advice): the
source catalogue proves the Check steps option and stored numeric count but not
its exact server sentence; native chatbox currently renders that label plus the
actual count. Check completion before success shows the actual pinned task text.
These are native presentations, not fabricated OSRS dialogue. Falo uses the
account name in the existing world's missing Fremennik-name provider, as Olaf does.

This is not the entire sourced step-pool replacement. The old donor registration
and remaining subtype/puzzle/guard work are still called out in REVIEW-B-7.md.
Script/Gradle acceptance must be recorded separately from standalone tests.


## Latest UI camera correction, B15-Nov20:34 (supersedes earlier UI plans)

Use the corrected ClueInterfaceImportTool from this branch: RuneLite's source
angle labels are X/Z/Y in raw order, and native Component reads X/Y/Z. The
reward chest must receive104,761,0,914. Earlier regression104,0,761,914 was wrong.
Source raw bytes/hashes: cache/ui-camera-order.json; explanation UI-CAMERA-ORDER.md.
Five focused codec tests + actual unchanged667 Component decode/hover/wide-model
cache-key tests passed on the exact promoted sources. No canonical client edits.

Read-only corrected PREFLIGHT tx-20261115-193224:27 logical mutations, both
caches/ledger/runtime mapping unchanged. UI-PREFLIGHT.tsv is the exact proposal:
interface73->1201,203->1202; model3395->66637,1226->66638; source sprite468->8954
(texture),539->8955,540->8956; texture22->1423. No IDs are reserved. Rerun AFTER
preceding batches. Command: python tools/clue/preflight_ui.py, after compiling
current UI codec classes with python tools/clue/verify_interface_port.py.
Read-only proof: build/clue/ui-preflight-current/read-only-proof.json.

Claude: retain the previously staged wide-IF3 client patch/release ordering;
load the corrected reward screen in OpenGL and compare the chest orientation
against the source, check text paper, Close/hover/fixed/resizable/Stretched.
New final-branch Gradle acceptance follows separately. This is not whole CLUE
release acceptance; map/puzzle/guardian/source gates remain open.


## Torn master step activation, B15-Nov21:06

19837/19838/19839 must be imported through the existing 245-item batch.
Only then registers the single three-part master step and Combine handlers.
Abbot/Robin use pinned transcripts and actual equipment checks; Robin rejects
any equipped toxic blowpipe even if other equipment reaches180. Pieces do not
advance the trail individually; Combine removes all3 atomically and advances1.
Four focused TornParts tests passed with freshly compiled libraries. Final
Gradle/script acceptance follows. Claude: wrong gear, Robin180 with/without
blowpipe, full inventory, part juggling/bank, Combine wrong clue and replay.
No cache application, server/client start or release performed.


## Core master identity preflight fix, B15-Nov21:08

ClueCoreCacheTool now accepts actual source19835 opcode160 through a narrowly
scoped, Wiki-proven nonstackable master override. Previously it crashed after
the master item import. No general opcode160 meaning was invented. Ten pinned
source item records+SHA in cache/core-source-items.json. Three fresh own tests
passed: all10 options/names/stacking, original model/value retained, idempotence,
real19835 and unknown flags rejected. Claude must rerun core PLAN/PREFLIGHT
AFTER the item batch so imported19835/19836 can be renamed; source IDs remain
19835=78 clue scroll and19836=Reward casket(78). Never map these to legacy items.
The gold/onyx recolours remain Claude cache work from the existing handoff.


## Source bucket menu, B15-Nov21:15

PREFLIGHT tx-20261115-200231: exactly1 mutation, native object9568
index16/group37/file96; append opcode30 Search before terminator. Existing
geometry and all prior bytes retained; no IDs allocated. Actual paired raw
bytes+SHA: cache/search-bucket.json. Two focused tests passed. Read-only proof
build/clue/search-menu-preflight/read-only-proof.json confirms both caches,
ledger and stash mapping unchanged. Rerun python tools/clue/preflight_search_menu.py
after compiling current tool (check_compile.py HEAD basic-tests). Claude applies
ClueSearchMenuCacheTool through the normal cache workflow; no --apply used here.
Test jail bucket9568 at3013,3179,0: Search completes only its active source clue;
other steps/accounts/objects must retain ordinary behaviour.

Final new-component Gradle run integration-20261115-200204:3core+13plugin
classes requested,4513 source/fixture inputs frozen; waiting on Claude buildslot.
No result accepted until summary.json reports accepted=true. Map drafts remain
under build/clue/next and are excluded from this frozen run.


## Nine map clues, B16-Nov00:51

Use the current ClueInterfaceImportTool, with --maps and
`docs/CLUE/cache/map-interfaces.json`, SAME verified mapping
`data/cfg/clue/interfaces.tsv`, plan `docs/CLUE/MAP-UI-PREFLIGHT.tsv`.
Read-only command: `python tools/clue/preflight_maps.py`, after compiling the
current codec via `python tools/clue/verify_interface_port.py`.

PREFLIGHT tx-20261115-234848:693 mutations=675components+16models+2sprites.
Current non-reserving proposal: source groups86,348,350,351,353,354,356,360,362
->local1201..1209;source539/540->8955/8956;16models66715..66730 (exact TSV).
CoX8954 is occupied and was not used. Reward/text and map proposals currently
OVERLAP because neither is applied: APPLY reward/text first, rerun map preflight
with its now-verified interfaces.tsv, THEN apply maps. Both tool modes preserve
all other verified entries; common paper/model/sprites are reused. Never publish
a preflight TSV as the runtime mapping. Wide IF3 client patch/release first.
No map asset or model ID is hardcoded into runtime; without imports, maps are
excluded from the pool. Source group counts include their real final Close.

Eight importer tests and675-component native decoder proof passed;84 actual
library tests green. New Read/Close test and final Gradle/scripts acceptance
recorded separately. Source illustrations+URL/hash manifest in sources/map-images.

Claude OpenGL checks: all9drawings beside their Wiki images, camera orientation,
parent clipping, zoom/paper/frame, Close+hover+Escape, fixed/resizable/Stretched.
Dig every8dig point on the wrong tile/plane then the exact point; search the
Lumber Yard crate2620 at3309,3503,0 instead of digging it. Full inventory must
replace clue with next clue/casket once. Bank/drop/re-pick/relog/second account;
no map should give a free step from Read or another player's interaction. No
monsters spawn on map digs. Those client/live gates remain Claude's responsibility.

16-Nov00:54: Read/Close test rerun passed3/3 on freshly compiled production classes. Initial verification failure included static extension calls from the prior successful opens; used a separate missing-import account to isolate that assertion. No production change for this test correction. Unique standalone cohort now85 tests. Final Gradle/script integration starts on frozen inputs next.


## Latest Uri and puzzle atlas, B16-Nov16:44

Active adapters are now promoted from NEXT-INTEGRATION with SHA proof.94 focused
actual-source tests and10 codec tests passed; actual native client decoder passed
all675 map +199 puzzle components. Whole-branch Gradle/scripts must still pass.
Uri uses existing5141/5142/5143,25 current pinned transcript phrases, refusal,
gear recheck on Talk-to, medium second-emote ordering and exact held-clue contract.
No guessed lifetime or spawn/vanish animation: unresolved source points remain
as documented in uri-source.json. Twelve easy/medium/elite steps; guards separate.

Current puzzle UI command: python tools/clue/verify_interface_port.py then
python tools/clue/preflight_puzzles.py.371 mutations,tx-20261116-154016,no apply;
exact IDs in PUZZLE-UI-PREFLIGHT.tsv. Proposed306->1203,models66725..66894,
sprites8955/8956. Apply reward/text, then rerun maps, then rerun puzzles using the
SAME VERIFIED interfaces.tsv; allocations overlap until actually applied.
Never copy proposals into runtime mapping. Client wide-IF3 patch/release first.

Claude: OpenGL compare all seven puzzle pictures and movement cells, Close/hover,
fixed/resizable/Stretched, both sounds1859/2304. Uri wrong gear/emote/plane,
medium second emote before/after first, repeat emote, gear switch before Talk,
other account, clue banking/replacement/progress/death/logout and full inventory.
Puzzle handover/solve/death/ownership implementation and guard/hotcold work remain
open; neither the whole card nor client/live acceptance is claimed here.


## Exact hard puzzle boxes and Guide import, B16-Nov16:55

Main clue item roster is now247: previous245 plus source19887/23173 (tree boxes).
Extra-only dry-run: python tools/clue/preflight_items.py --puzzles;
PREFLIGHT tx-20261116-154712,applied=false finalized=0 recovered=0;
proposed24940/24941, model4204->66725. No reservation; rerun main247-item roster
with preflight_items.py after preceding batches. Both caches/ledger/mapping
fingerprints stayed equal. PUZZLE-ITEM-PREFLIGHT.tsv lists exact extra definitions.
Do not reuse generic2795/3565/3569 for these two source NPCs.

clue-steps NPC batch now includes source306 (Lumbridge Guide),10NPCs total.
After applying the batch rerun python tools/clue/build_npc_mapping.py --write
for the COMPLETE10-profile mapping; an older9-row mapping is incomplete.
The static profiles+audit were regenerated read-only (0 applied/10 pending).
Guide spawn3238,3220,0 radius3 is source-pinned. Gunnjorn607 already exists.
Source identity/picture proof: cache/puzzle-routes.json and puzzle-source-items.json.
The ordinary Guide transcript contains unresolved quest-advice/goal-condition
source gaps; this part implements its Treasure Trails branch, not those ordinary
branches. Keep that N2 review point open before deploying a new Guide service.

Claude checks after UI306 import: both exact tree boxes, Open/Move/Close5/Escape,
wrong/nonadjacent cells and both sounds1859/2304; solve then NPC hand-in, no
advance merely on Solve/Close, bank/drop/relog resets unsolved board, solved box
attributes retained, missing clue/box, wrong NPC, duplicate boxes, full inventory,
final casket, other account, death value100 vs clue20 and normal box floor timer.
Boxes are additional inventory items, not replacements for their hard clue.
Backend/identity tests13green; whole-branch Gradle/scripts acceptance follows.

## Accepted restoration gate, B16-Nov23:20

integration-20261116-213117:171tests/32fresh classes (5core/27plugins), zero
failures/errors/skips/missing,4543unchanged source inputs; core/plugins/ALL
scripts compile,48m7s including queue. Durable proof:
validation/integration-20261116-213117.json. Validates source d1381c985 and
predecessors: guardian state, Mimic readiness,23STASH units/chart150nodes,
26maps/1998components. Supersedes the unaccepted canceled210040 checkpoint.
Client/cache/live checks are still open. Item247/NPC10/STASH23 rosters unchanged;
fifteen new unique NPC/coordinate steps are staged and need their own acceptance.
No cache apply, server/client lifecycle, live or push action performed.

## Fifteen additional distinct source steps, resumed 16-Nov

107 source records now preserve 38/41 original locations and 69 distinct additions
(the original 69 selection overlaps 15 originals, corrected by 7 easy/3 medium/5 hard).
Catalogue tiers:29/29/25/12/12. Three torn parts still form one trail choice.
These are catalogue counts; actual asset/handler availability remains separate.
Seven easy NPC handovers use pinned conditional transcripts, including silent
Hans/Ellis/Cassie handovers. Five extra number challenges retain exact wording,
answers and real actor spawn identity; other Ned/Wise Old Man forms cannot claim them.
No item247/NPC10/STASH23/map26 import roster changes.

Fresh command python tools/clue/test_unique_additions.py:25 tests across4 affected
classes passed against compiled actual production delta in one JVM; source/data
SHA256 unchanged. audit_unique_additions.py and audit_original_pool.py passed;
git diff --check passed. This is not Gradle/script/client acceptance.
Claude: each new NPC clue and numeric paper, wrong answer, trade vs Talk-to,
full inventory final/nonfinal handover, wrong spawn/account, bank/drop/relog.
Next: guardian combat adapter, hot/cold/lightbox and full Mimic mechanics.

## 17-Nov: double-agent runtime

No item/NPC/cache roster expansion for this component: all three identities
were already in the native cache or the ten-NPC batch. Existing three hard
emotes and two78emotes now have the actor/combat/Uri registration path.
31 fresh actual-source tests pass; Gradle/script verification follows.
Client: first-step exemption, later correct/wrong gear+emote, repeat emote
during a fight, change to combat gear, kill in clue gear (also move two tiles),
kill without clue gear then re-equip/emote, bank/drop/relog/death mid-fight,
two accounts/familiar, bones and unchanged Drop Viewer layout. In Wilderness
singles use three accounts: guardian -> PKer -> second PKer, including queued
projectile and familiar. Verify native human block/death animation and sounds.
Master actor source7312 uses regenerated npcs.tsv after the normal NPC batch;
missing mapping must never bind the actor to a proposed local ID.

## 17-Nov: master puzzles and source light-box UI

Source/code proof: LIGHTBOX-SOURCE-AUDIT.md.12 fresh actual-source tests pass,
including public Bank.deposit/all and full item network preservation. Native
Component/ClientScript checks:44components,10scripts,52hooks,150 item-icon
states across two sizes/three light patterns. All30old ItemAttribute ordinals
are unchanged; nine light-box fields appended. Full Gradle/scripts follow.
The sole shared-bank change rejects source20282 (Gnome-child master puzzle),
as the pinned Wiki says it cannot be banked; other picture/light boxes retain
normal banking. Review that4-line boundary separately when merging.

No item/NPC roster change: all four master pictures20280/20281/20282/23417,
light box20355 and internal lamps20356/20357 are already in247items. Master
Mandrith/Radimus now have the supplemental box/solve/return-to-NPC handler.
See source question Q7 in LIGHTBOX-SOURCE-AUDIT.md: public sources do not
publish the random matrix generator or box-type weights. The compatible
uniform-type/picture and solvable-network choice is not proof of their hidden
OSRS distribution; no full parity/CLUE acceptance is claimed.

Cache batch: ClueLightBoxCacheTool, source group322,44static components plus
source1339 item-icon draw and native ports of134/135/208/98/6388/1335/1336/
1337/1339/1338. This uses existing native client opcodes/hooks; no additional
client patch is needed specifically for light boxes. Other clue interfaces
still need the earlier wide-model patch before the combined cache/client release.

Exact latest PLAN/PREFLIGHT: LIGHTBOX-PREFLIGHT.tsv, tx-20261117-032937,
92mutations, proposed interface1203, private control2408, scripts5425..5434,
37sprites8955..8991. Existing source/native sounds576/580 are byte-identical
in both caches, so no sound replacement. Lamp item proposals25285/25286 belong
to the combined247-item plan, not a second import. Both caches/ledger/item
roster/all runtime mappings stayed unchanged (validation/lightbox-preflight.json).

Execute only AFTER the combined item batch and regenerated items.tsv, and
rerun allocation after STASH/chart or any intervening cache batch. Proposed
interface1203/control2408/script5425 are not reserved and overlap separate
historical preflights. The apply path refuses unapplied lamp item proposals
by checking both source item identities against the durable item ledger.
Use the compiled class from this branch (main below defaults to PREFLIGHT):

    java -Xmx512m -cp "<checkout>/game/build/classes/kotlin/main;<checkout>/game/build/install/game/lib/*" gg.rsmod.game.tools.importer.ClueLightBoxCacheTool docs/CLUE/cache/lightbox-interface.json docs/CLUE/cache/lightbox-scripts.json data/cfg/clue/items.tsv data/cfg/clue/lightbox.tsv docs/CLUE/LIGHTBOX-PREFLIGHT.tsv

Claude adds the apply flag only in his own cache run. The runtime mapping is
SEPARATE data/cfg/clue/lightbox.tsv; do not paste this preflight into it or
replace the other interfaces.tsv. Source322/control1356/script/sprite/item
rows are all emitted only after transaction verification when applying.
Read-only reproduction here: python tools/clue/preflight_lightbox.py after
python tools/clue/verify_lightbox.py (fresh libraries/tests/native decoders).

Client checklist is at the end of LIGHTBOX-SOURCE-AUDIT.md. Include normal/
hover/15client-cycle pressed buttons, p12 labels, icon outlines/spacing,
resizable/fixed, sounds576/580, all four master pictures, both NPCs, full
inventory, duplicate/stored boxes, close/relog/death and second account.
Coordinate guard combat, hot/cold and full Mimic remain separate open work.

## 17-Nov: guardian loot continuation

The combined item roster is now248 (247plus Snape grass seed22879). Re-run the
whole roster, never just the seed. Latest ITEM-PREFLIGHT.tsv:
tx-20261117-070628,443definitions, applied=false. Both caches, ledger, runtime
mapping and input roster stayed identical; build/clue/item-preflight-current/read-only-proof.json.
This supersedes all earlier247-item proposed IDs. Other NPC10/STASH23/map26
rosters are unchanged. Allocation is a proposal: rerun after any intervening import.

ClueGuardianDropCatalogue supplies all11 guardian variants' exact slot trees
from seven pinned NPC pages plus shared herb/allotment seed/gem tables. Owner
excludes Looting bag. God items are one1/1000000elite pre-roll (two equal items),
which replaces main loot, preserving bones. Ring of wealth removes only shared
empty gem/mega slots; Legends quest and source talisman geography are personal.
Drop Viewer bypasses shared cached table data for registered guardians, using
the unchanged approved row rendering. Clue loot is personal, not LootShare.
Native double agents use this route already; coordinate encounter adapter follows.

Six fresh actual-source tests passed. Failed preliminary fixture expected the
bottom robe to include the top and used slot chances for guaranteed bones; the
source data/loot was correct. Both assertions corrected to the ordinary factory
semantics. Durable proof validation/guardian-loot-actual-source.json. Whole-branch
Gradle/scripts acceptance is the next gate; this is not full-card acceptance.

Claude: inspect elite god-items/main replacement, bones, range quantities,
nested herbs/seeds/gems, RoW on/off, Legends completed/incomplete and2accounts;
no cached ring/quest context should leak. No viewer style/layout change.

## 17-Nov: hard wizards, master dig controller and Mimic known mechanics

Commits73997fbd8 (hard wizard/device runtime),5bddd2b61 (master dig controller,
combat profiles/rules and supplemental assets),01353ee76 (Mimic candy/minions/
forward/multi lifecycle),414b8d061 (NPC source reconciliation).
Production-source checks:32guardian tests,33Mimic tests,5acquisition tests;
source hashes and output in validation/dig-guard-combat-actual-source.json,
validation/mimic-candy-attack-actual-source.json and
validation/npc-resolution-actual-source.json. The prior070825run was interrupted
without a summary and is NOT accepted. New integration083438 freezes4601inputs,
1core/30pluginclasses, including all script compilation; result pending.

The original item248/NPC10/STASH23/map26rosters remain fixed and may be applied
in the already documented order. B did NOT apply any cache or publish mappings.
Additional SEQUENCE-ONLY batch, independent of those identities:

```
OsrsFxImportTool clue-guard-combat
```

Source2686sequences:
376,378,390,424,426,427,727,836,1162,1167,4320,4321,4322,6957,6958,6959.
B's read-only preflight tx-20261117-082149 succeeded294logical mutations,
CREATE276/NO_OP312; exact report COMBAT-PREFLIGHT.txt and before/after fingerprints
validation/combat-preflight-read-only.json. Proposals (not reserved):
376->17752,378->17753,390->15785,424->16085,426->15799,427->17754,727->15755,
836->17755,1162->15774,1167->15756,4320->16157,4321->16159,4322->16158,
6957->17756,6958->17757,6959->17758. Rerun allocation after predecessors.
After Claude's apply, `python tools/clue/build_combat_mapping.py --write`
validates all16ledger-owned identities against both caches before publishing
this worktree's cfg/clue/combat-assets.tsv. No provisional ID enters gameplay.
Source vertical-offset/stretch omissions are printed in the exact report;
loaded-client frame/audio checks remain mandatory.

Q8 explicit source gates: generic rig roles do not prove per-guardian attack
animation/projectile/timing. cfg/clue/combat-visuals.json is intentionally absent;
ClueGuardianCombat.visuals requires a verified source row and applied sequence
map before installing these combat types. See COMBAT-SOURCE-AUDIT.md for the
row contract and observed two-blade Armadylean image/candidate6957 inference.
An unspecified elite alternative-choice probability is NOT silently50/50.
Therefore master/elite coordinate/hotcold selection still has source gates,
although the held-scroll/group/target/kill/second-dig implementation is present.
This does not block applying the other prepared cache batches or further work.

Q1 remains as the owner's explicit exception: Mimic ENCOUNTER_IMPLEMENTED=false;
candyAttackChance/forwardStepChance remain null. Four-tick candy impact comes
from Claude's pinned video measurement. New explicit null candyLandingOffsets
and candyFlight require the real six landing offsets and launch height/end
height/slope/steepness/releaseDelay. No donor default is inserted. The complete
plumbing consumes that measured plan; ready(world) additionally checks minion
visuals and the existing Mimic candy assets. An unavailable selected Mimic keeps
the ordinary casket and dry-protection eligibility. No attempt/loot is spent.

Claude client checks after the source gates/assets are actually ready:
* Hard wizard: first dig spawns once, exact kill then second correct dig; prayer
  switching/poison, wrong tile, bank/drop/logout/death, two accounts/familiar.
* Master single/trio: three exact deaths required; saved hot/cold target remains
  through fight/serialization; Feel3..8damage; wrong plane/outside9x9cannot dig.
* Elite guard: source selection probability, flying/halberd rule, accurate poison
  through prayer, source visual/audio; personal loot/viewer unchanged style.
* Mimic: all six candy colours/projectiles and4tick impacts, dodge, replacement
  only after next volley, freeze/body collision, minion styles/stats/no loot,
  all NPCs simultaneous in the private multi instance; exit/plane change/death/
  logout/re-entry/simultaneous boss-player death settle once,90kdeathbank route.
* Acquisition:625source routes/587NPCs. Check regular Jelly1/128, Farmer7,
  Vyrewatch110/120, full-box account versus second account. Native130/140Vyres
  and absent modern cave Jelly actors were NOT guessed into an OSRS variant.

## 17-Nov: puzzle-box bank refusal and application order

`Bank.deposit` now gives the standard OSRS refusal for the source20282 Gnome
child puzzle box, including deposit-all and equipment. This is the only changed
banking behaviour. Puzzle_box revision15185934 (see the source manifest) proves
`bankable7=No`; `BANK-SOURCE.json` pins Bank revision15388948 and its standard
storage-limits message: "A magical force prevents you from banking this item!".
The existing Mimic refusal uses the same message.

Twelve freshly compiled lightbox/master-puzzle tests pass, including exact
per-attempt messages and unchanged containers. Native lightbox decoder/CS2 checks
also pass (44components,10programs,52hooks,150item icons). Durable source hashes
and output: validation/bank-refusal-actual-source.json. The first run's static
MockK call-history assertion failed and was corrected; that run is not accepted.
The combined31-class integration115813 is separate and still pending.

The application list above now explicitly puts both reviewed client codec
patches/releases BEFORE any wide UI models/opcode242 definitions, as required by
Claude's15November review. Current combined roster248 and tx070628 supersede
old247/438-definition proposals. No blueprint sale/POH Study is included (Q5).
