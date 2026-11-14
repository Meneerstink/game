# CLUE - OSRS Treasure Trails

ChatGPT-A, branch `gpt-a/clue-scrolls`, 14 November 2026. This is an implementation
branch, not evidence that any content has passed the loaded testclient or live gate.
Claude owns cache application, testclient verification, merge and release.

## Verified starting point

* Four tiers, 24 map locations and 17 emotes. Every tier currently receives every
  step, taken from Novite. Several coordinates and equipment requirements differ
  from OSRS. The donor's 50/40/35/20 percent completion rules are not OSRS rules.
* Easy, medium and hard caskets give a single roll from donor tables and a shared
  junk pool. Elite has a sourced slot tree and 4-6 rolls, but omits master clues,
  Mimics, scroll cases, some scrolls/books and owner-excluded kits.
* Watson house square 6455, Strange casket 62743, Watson 14406, Mimic item 23729,
  Mimic NPCs 14401-14405 and its animations/effects already exist. The initial
  dossier's claim that the Mimic item is missing is stale. The encounter and
  Watson service are missing; opt-in dialogue and a combat definition exist.
* Most existing clue drops bypass ownership checks. Dragon impling jars alone
  have an inventory/bank check. No checks cover graves or Death's Office.
* Collection Log contains only a rarity-filtered subset of elite loot, without
  completion counts. Drop Viewer currently has no clue casket sources.

## Sources and generated data

`sources/manifest.json` records Wiki revisions, timestamps, URLs and SHA-256s.
`tools/clue/ClueCacheInventory.java` reads pinned OSRS cache 2686, config 2/10 and
2/8, without a target-cache writer. `tools/clue/build_catalogue.py` derives exact
rational reward probabilities, source item identities, local mappings and imports.
RuneLite clue classes are navigation/code aids only; Wiki and cache remain the
gameplay/ID authorities. Novite behaviour is not retained as proof of OSRS parity.

The hard mega-rare combat-potion result is ONE event with three items. Its marginal
item rates sum above one if treated as three independent events; the Wiki's
`dual-drop` footnote explicitly resolves this. No rates are renormalized.

OSRS collection-log enums: beginner 2322 (16), easy 2141 (131), medium 2142 (115),
hard 2143 (134), elite 2144 (59), master 2145 (49), shared 2146 (49). These replace
the former rarity heuristic and provide all items in source order.

## Implementation order and remaining gates

1. Six tiers, correct trail lengths, item-specific steps and persistent tier
   progress; same-tier ownership across inventory/bank/grave/Death; dropped clue
   identity, relog, full-inventory replacement and duplicate-click safety.
2. Exact six reward catalogues, grouped outcomes, per-casket roll counts, counters,
   master clue rolls, milestone rewards, bloodhound, Entrana rules, complete log
   and Drop Viewer data in the approved layout. All missing assets must resolve
   before a reward is consumed; never silently remove/reweight missing items.
3. Replace the donor step pool with source-classified steps: emotes/Uri/double
   agents/STASH, cryptic/search/talk, anagrams/challenges/ciphers, coordinates and
   wizards, maps, puzzles, light boxes, hot/cold, Falo, Charlie and Sherlock.
   Record each excluded step and the proven missing map/area in the dossier.
4. Watson hand-in/check/NPC Contact and persistence; Mimic entry/instance, attempts,
   combat/minions, exit/death/logout, reward/bonus and no-banking rule.
5. Source-by-source acquisition: NPC drop tables, nests/geodes/bottles, implings,
   activities, clue/scroll boxes. Preserve the approved drop-rate modifiers only
   where a current owner decision requires them, and show the actual roll rate.
6. ITEM+NPC+UI+K-FIX and A1-A10, plus BOSS/AREA/DEATH/SOUND where applicable:
   options/examines, GE/trade, death D1-D20, map, audio, model and client proof.

## Open source/activation points

* Beginner rewards prohibit uniques before ten hours played. Obtain the exact
  fallback roll rule; do not invent a replacement distribution.
* The current Wiki contains scroll boxes/cases (July 2025), unlike the donor.
  They are in pinned cache 2686 and therefore belong to the parity work. Quest
  condition X Marks the Spot must be checked, not assumed complete for everyone.
* Bloodhound's Dig option was added after the pinned cache (8 September 2026):
  runtime option/ID needs a source-compatible import plan, not a guessed slot.
* The prior kits-from-stores-only exclusions conflict with this task's newer
  explicit instruction for all OSRS casket rewards. This implementation follows
  the newer task; record the restored kit sources for Claude's release review.
* No client layout, runtime sound, arena collision or death recovery claim is
  accepted from source tests alone. Those checks remain Claude's assigned gate.

Every completed step is appended to `C:\RSPS\team\dossiers\CLUE.txt`. Do not mark
CLUE complete while this plan or the handoff still contains unimplemented work.
