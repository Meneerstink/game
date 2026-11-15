# CLUE — Claude cache/client handoff (in progress)

This card is not ready to merge/release. A resumed the entire card on
`gpt-a/clue-scrolls-2` from B's WIP `be512be6a` (dossier 15 November 00:4x).
Do not interpret a successful cache preflight as a completed gameplay chain.
All cache application, testserver/client operation, merge and release belong to Claude.

## Exact item batch

* Source: `C:/RSPS/import-source/openrs2-2686/cache`.
* Batch: `OsrsItemImportTool clue --clue-roster=<this worktree>/data/cfg/clue/import-items.tsv`.
* The roster is the exact 227-source-item list (including notes where marked).
  The 15 November preflight generated **418 definitions**, proposed local IDs
  **24755–25172**. Every source/local/model row is in `ITEM-PREFLIGHT.tsv`.
  These local IDs are proposals, not reservations: rerun against the current
  canonical caches immediately before applying, then regenerate `items.tsv`.
* Use the reviewed importer/forearm fix from this branch. The old importer
  stopped on Ancient/Armadyl/Bandos platebodies (12460/12470/12480).
* Plan report: `build/clue/item-preflight.txt`. The reported transaction was
  `tx-20261114-231054`, `applied=false`. See `DRY-RUN-INCIDENT.md` for the
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
