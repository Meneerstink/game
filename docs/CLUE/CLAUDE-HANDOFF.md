# CLUE — Claude cache/client handoff (in progress)

This card is not ready to merge/release. A resumed the entire card on
`gpt-a/clue-scrolls-4` from B's WIP `05286a72c` (dossier 15 November 07:02).
Do not interpret a successful cache preflight as a completed gameplay chain.
All cache application, testserver/client operation, merge and release belong to Claude.

## Exact item batch

* Source: `C:/RSPS/import-source/openrs2-2686/cache`.
* Batch: `OsrsItemImportTool clue --clue-roster=<this worktree>/data/cfg/clue/import-items.tsv`.
* The roster is the exact 245-source-item list (including notes where marked).
  The 15 November 08:04 preflight generated **436 definitions**, proposed local IDs
  **24755–25190**. Every source/local/model row is in `ITEM-PREFLIGHT.tsv`.
  These local IDs are proposals, not reservations: rerun against the current
  canonical caches immediately before applying, then regenerate `items.tsv`.
* Use the reviewed importer/forearm fix from this branch. The old importer
  stopped on Ancient/Armadyl/Bandos platebodies (12460/12470/12480).
* Plan report: `build/clue/item-preflight-245.txt`. The reported transaction was
  `tx-20261115-070458`, `applied=false`, `finalized=0`, `recovered=0`. See `DRY-RUN-INCIDENT.md` for the
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
   relog, Astral Contact, blueprint payment and menu/head sizing.
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

```
.\gradlew.bat :game:runOsrsNpcImportTool --priority low --args="clue-steps"
```

Source cache: `import-source/openrs2-2686/cache`. A ran the compiled tool's
PREFLIGHT only. Transaction `tx-20261115-064813`: 161 logical mutations, 63 source
asset records. Complete output: `NPC-PREFLIGHT.txt`. The ledger hash and all cache
file sizes/mtimes were unchanged before/after.

| Source | Role | Proposed local NPC |
|---:|---|---:|
| 6586 | Sherlock | 15234 |
| 7306 | Falo the Bard | 15235 |
| 7312 | Master double agent | 15236 |
| 6587 | Armadylean guard | 15237 |
| 6588 | Bandosian guard | 15238 |
| 7307 | Ancient Wizard (mage) | 15239 |
| 7308 | Ancient Wizard (ranger) | 15240 |
| 7309 | Ancient Wizard (melee) | 15241 |
| 7310 | Brassican Mage | 15242 |

Existing Uri 5141?5143, hard agents 5144/5145, Saradomin wizard 1264 and Zamorak
wizard 1007 are not duplicated. Wizard Traiborn / Lumbridge Guide identity/name
adapters still need review (source names versus existing Traiborn / Lumbridge
Sage). Abbot Langley and Robin are already spawned in the current world.

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
puzzle choices. The old 41-donor-step registration still requires replacement;
no complete-step-pool claim is made by these source/backend commits.

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
