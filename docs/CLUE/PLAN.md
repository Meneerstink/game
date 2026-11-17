# CLUE - OSRS Treasure Trails

ChatGPT-B, branch `gpt-b/clue-scrolls-7`, resumed 17 November 2026 after the interrupted guardian-PvP continuation.
The latest dossier handoff assigns the entire card to B.
This is an implementation branch, not evidence of client or live acceptance.
Claude owns cache application, testclient verification, merge and release.

## Original starting point (14 November; historical, not current runtime)

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

1. Five tiers (owner excludes beginner), correct trail lengths, item-specific steps and persistent tier
   progress; same-tier ownership across inventory/bank/grave/Death; dropped clue
   identity, relog, full-inventory replacement and duplicate-click safety.
2. Exact five reward catalogues with the owner's shop-only kit rerolls, grouped outcomes, per-casket roll counts, counters,
   master clue rolls, milestone rewards, bloodhound, Entrana rules, complete log
   and Drop Viewer data in the approved layout. All missing assets must resolve
   before a reward is consumed; never silently remove/reweight missing items.
3. Preserve source-correct originals alongside a limited source-classified selection (about 70 NEW steps): emotes/Uri/double
   agents/STASH, cryptic/search/talk, anagrams/challenges/ciphers, coordinates and
   wizards, maps, puzzles, light boxes, hot/cold, Falo, Charlie and Sherlock.
   Record each excluded step and the proven missing map/area in the dossier.
   The donor's every-tier registration, incorrect gear and automatic completion
   cannot be reinstated. ORIGINAL-POOL-AUDIT.json reconciles all41 old locations:
   38 now have catalogue records,3 still require source/asset/handler review.
   The initial69 selection overlaps15 originals, leaving54 unique additions.
   Six other original emotes and17original maps have now been restored, with
   23total STASH units. Fifteen further sourced steps remove the overlap deficit:
   current catalogue107 (29/29/25/12/12),38originals+69unique additions. This is
   NOT an active-pool count; three torn parts form one choice. Three historical
   originals still have explicit source/modern-area gaps. Guardian combat, hot/cold
   and master puzzle handlers/assets remain acceptance points.
4. Finish and independently review the inherited Watson hand-in/check/NPC Contact,
   Mimic arena/combat/death/rewards. B owns all of these after the 04:2x handoff.
5. Source-by-source acquisition: NPC drop tables, nests/geodes/bottles, implings,
   activities, clue/scroll boxes. Preserve the approved drop-rate modifiers only
   where a current owner decision requires them, and show the actual roll rate.
6. ITEM+NPC+UI+K-FIX and A1-A10, plus BOSS/AREA/DEATH/SOUND where applicable:
   options/examines, GE/trade, death D1-D20, map, audio, model and client proof.

## Open source/activation points

* Beginner is excluded by the owner; the source archive retains research but
  runtime data, entry points, sources and log pages must not expose that tier.
* The current Wiki contains scroll boxes/cases (July 2025), unlike the donor.
  They are in pinned cache 2686 and therefore belong to the parity work. Quest
  Owner 15 November 00:2x explicitly grants the X Marks the Spot box unlock to
  everyone; no beginner quest or beginner tier is introduced.
* Bloodhound's Dig option was added after the pinned cache (8 September 2026):
  runtime option/ID needs a source-compatible import plan, not a guessed slot.
* Owner 14-11 22:1x keeps kits shop-only. Kit outcomes reroll over the remaining
  weighted table. Source probabilities stay in the evidence; the actual roll and
  viewer use the conditional distribution, which slightly increases other rates.
  No kit slots become nothing and no shop offer is removed.
* No client layout, runtime sound, arena collision or death recovery claim is
  accepted from source tests alone. Those checks remain Claude's assigned gate.

## Current acquisition implementation (17 November)

NPC-RESOLUTION-AUDIT.md records625combat tier routes for587current native NPC
identities (easy240/medium130/hard191/elite64), with46source skill base rates.
Farmer/Vyrewatch infobox levels and11byte-pinned native Jelly variants resolve
all earlier conflicts; NPC-SOURCE-CONFLICTS.json is empty. NPC-SOURCE-GAPS.json
retains absent or incompatible variants, including differently levelled Deadman
breach bosses. Kill/thieving/reward icons remain separate source methods.
The same per-player provider feeds rolls and Drop Viewer without format changes.

## Current client repair pass (17 November)

Claude reported20emote editorial prefixes, unavailable-Mimic referral blocking
78caskets, an unbound loose2795castle puzzle, and four invisible resizable
window routes. Commits9c15f3b2c/35d37c1ca/aea40c2b5 address those paths. All
four modal routes use548:9/746:12; loose boxes open a personal playable view
without a clue-completion token. While Mimic is unavailable, the ordinary table
and odds remain active without consuming opt-in/RECEIVED/attempt state.
Fresh13-class/scripts integration20261117-193256 is pending. See the current
CLAUDE-HANDOFF.md section for exact fixed/resizable and transaction rechecks.
No cache writes are required for these repairs. Owner Q1/Q5 and source Q8 remain
explicit exclusions; no primary values are fabricated to remove their gates.

## Historical scope decisions (current ownership: B, dossier 15-Nov 04:2x)

### OWNER DECISION 2026-11-14 21:5x (binding, from Claude)

Do NOT build all 761 OSRS steps. Order: (1) exact OSRS casket rewards + Collection Log pages + Drop Viewer caskets, (2) Watson + Mimic, (3) beginner and master tiers, (4) only then a LIMITED set of new steps: a handful per OSRS step type per tier, only in areas we have. See team/dossiers/CLUE.txt 21:5x.

Counts (Claude advice 21:5x, until the owner says otherwise): about 80 new steps: beginner 10, easy +15, medium +15, hard +15, elite +12, master 12, spread over each tier's OSRS step types, only in areas we have.

OWNER 2026-11-14 22:0x (binding): SKIP the BEGINNER tier completely (no steps, casket, sources or Collection Log page). Master stays. About 70 new steps: easy/medium/hard +15, elite +12, master 12.

OWNER 2026-11-14 22:1x (binding): (1) ornament kits stay SHOP-ONLY: no kits from clue caskets (a kit outcome becomes a normal roll on the rest of the table); (2) 3rd Age and gilded ALSO from caskets as OSRS (Donator store offer stays); (3) include scroll boxes/cases; (4) OSRS clue drop rates; (5) death with a clue/casket as OSRS; (6) Mimic as OSRS (opt-in).

OWNER 2026-11-14 22:2x: include the monsters that appear when digging / performing a step, as OSRS: coordinate-dig wizards (Zamorak/Saradomin/Guthix/Armadyl/Bandos wizard, Ancient wizard, Brassican mage) and double agents on elite/master emote steps, with OSRS stats/attacks/chances and the must-defeat-first rule, for every step type you build.

OWNER 2026-11-14 22:2x: GPT-B builds WATSON + MIMIC (own worktree, from 23:27). GPT-A: do NOT start Watson/Mimic; your part = OSRS casket rewards, Collection Log, Drop Viewer, scroll boxes, master tier, the ~70 steps and the dig monsters.

OWNER 2026-11-14 22:4x (binding, owner-requested custom): the master tier is named "78 clue scroll" and its casket "Reward casket (78)"; colours Gold & onyx (scroll) / gold casket with onyx bands+lock (screens/N415-78clue). Mechanics and reward table stay OSRS master. Extra: a GOLD world broadcast on a rare 78-casket reward, and an opening effect (existing OSRS graphic+sound, note the source). Claude does the cache rename/recolour - list the item ids in your handoff. Exclusive cosmetic = later.

## B source selection checkpoint: 15 November 05:36

`data/cfg/clue/steps.json` resolves the 69 reviewed candidates into explicit
DIG/TALK/SEARCH/EMOTE/KILL/FALO/SHERLOCK/HOT_COLD actions, preserving source
hashes, challenge requirements and source equipment alternatives. This is data,
not an activated step pool. Five NPC routes and optional equipment alternatives
remain in `STEP-ASSET-GAPS.json`; missing identities never become empty gear.

The medium Captain Harlan cipher was replaced with the sourced Drezel cipher
GUHCHO. Captain Harlan's current ID 16821 is absent from pinned cache 2686 (NPC
maximum 16358); source 3216 still names the old Melee combat tutor. Neither a
made-up appearance nor an unproven renamed NPC is substituted. Drezel's source
point 3440,9895,0, question and answer 7 are present in our supported area.
`cache/step-source-npcs.json` pins the read-only source NPC probe output.

Master cryptic components must generate the three physical torn clue parts
19837/19838/19839 and require Combine; selecting one component cannot complete
a whole master step. The source permits parts to be reused while juggling, so
parts must not be bound to one clue identity. Abbot's negative prayer bonus and
Robin's 180 ranged bonus excluding blowpipe are explicit requirements. They
remain inactive pending their actual handlers and challenge UI/guards.
