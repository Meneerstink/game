# Deadman breach status (branch `breach-fix`, started 2026-09-27)

Evidence labels (AGENTS.md 4): SOURCE / FUNCTION (tests) / CACHE / RUNTIME_BOOT / LIVE-AV (owner in the client only).

## Test limits of this run (read first)

The agent cannot log in to the client: there is no auto-login, and AGENTS.md 5 plus the owner's standing rule forbid GUI
automation of the client. So no screenshot, recording or second-client test was made by the agent. Everything marked
LIVE-AV below still needs the owner in the client; everything else is proven by cache reads, tests or server logs.

## Issues (status: OPEN / FIXED / VERIFIED / NOT A BUG / BLOCKED)

| id | issue | status | evidence |
|----|-------|--------|----------|
| A1 | frame-sound radius dropped on import (radius 0 = only the animating player hears it) | FIXED | CACHE tx-20260927-032334 (25 seqs, both caches); importer keeps `location` |
| A2 | npc attack sounds never sent (data attack marks the cycle, dealHit then skips `NpcCombatAudio.onAttack`) | FIXED | SOURCE `NpcAttacks.playedSectionSoundsThisCycle` / `PawnExt.dealHit`; FUNCTION NpcCombatAudioTests 7/7; LIVE-AV open |
| A3 | double sounds (row sound + frame sound of the OSRS animation) | FIXED | one source per sound: attack/defend/death row cue skipped when that animation has AUDIBLE frame sounds (`AnimDef.hasAudibleFrameSounds`, radius > 0), for every npc; FUNCTION BreachAuditTests A1 |
| A4 | ADAPTED sound families (Porazdir, Derwen, Jal-ImKot, smoke devil, Zemouregal) | SOURCE_BLOCKED | their OSRS sequences carry no frame sounds (SeqSoundRadiusRepairTool: none of their seqs among the 27 with sounds); OSRS Wiki pages have no sound section; "List of sound IDs" search found no name for them. The owner-chosen ADAPTED families stay (now audible to everyone, A5) |
| A5 | section sounds with radius 0 do nothing on an npc | FIXED | breach monsters: radius-0 section / row / defend / death sounds become area sounds at the monster, radius 15 (ADAPTED: most common OSRS npc frame-sound radius in the imported data, 20 of 44). Other npcs keep the 667 Void rule. LIVE-AV (second player hears it) open |
| B1 | range deadlock (pathfinder "arrived" vs withinRange "too far") | FIXED | `combat.plugin.kts`: for every data-attack npc `NpcAttacks.withinRange` is the only "arrived" rule; not in reach = one melee-approach step per tick (no touch-radius box). Applies to all data npcs (GWD, Jad ...) - REGRESSION LIVE check open |
| B2 | wait deadlock in `PawnPathAction.walkTo` (route cut short) | FIXED | stall exit after `STALL_TICKS` (2) ticks without a step and nothing queued (4 with steps queued); npc combat does not wait at all any more (`waitForArrival = false`) |
| B3 | breach npcs without smartPathfinding | FIXED | `spawnMonster` sets `smartPathfinding` (BFS supports sizes up to 5); landing also needs `canLeave` (footprint can step at least one way). Note: OSRS npcs use "dumb" pathing; done on the owner's mission instruction |
| B4 | aggro: chase 8 instead of the leash, refused target blocks the monster, single-way pile-up | FIXED | `npc_aggro.plugin.kts`: a breach monster keeps a live target while `NpcLeash.mayPursue` holds; the search collects every candidate that passes the real checks (aggro rule, can_attack plugins incl. PvP protection, `Combat.canEngage`, leash, single-way "already fought") over the whole footprint and picks one at random. Applies to every aggressive npc |
| B5 | leash failure leaves the npc standing at the edge | FIXED | `NpcLeash.returnHome`: the npc walks back to its spawn tile when outside its wander radius (all non-familiar npcs) |
| B6 | PvP hit on a player releases the monster fighting him | FIXED (corrected after the in-engine run) | `combat.plugin.kts`: the single-way "already under attack" checks never release a breach monster (like the Deadman guards). `BossNpcs` NOT changed: making breach monsters bosses would also turn their regions into teleport-safe boss areas (owner decision) |
| B7 | attack styles/ranges vs OSRS Wiki | PARTLY | Graardor range / Zilyana magic / K'ril magic: range 1 -> 8 (their Void range-1 sections are GWD room scripts; ADAPTED 8). Checked and left: Pyrelord and Warped Jelly (OSRS pyrefiends/jellies use magic at melee distance), Cave abomination, Jal-ImKot, Cerberus, Jaguar, Splatter (melee per wiki text quoted in BreachMonsters). Big Evil Chicken magic range 1: not verified on the wiki - OPEN. `Custom.range` is only used for the row's attack_range (sections decide); Void `approach` unused - reported |
| C1-C3 | classic labels vs frame bases | NOT A BUG (data) | CACHE: `AnimCoverage` over all 40 roster npcs (BAS + attack/block/death + section anims) in BOTH caches: every frame present, parses on its base, and moves labelled vertices. No breach npc model is skeletal-only. |
| C4 | BAS idle-turn (38/39) missing | FIXED (encoder) / NOT NEEDED (cache) | encoder writes 38/39; `BasIdleTurnRepairTool` dry run: 0 BAS to patch - the OSRS breach npcs define only stand + walk (probe 12439, 12444) |
| C5 | npc import can overwrite player framesets | FIXED | `OsrsNpcImportTool.local`: a frameset/base/seq the fx tool owns gets an npc-only copy (`npcframeset` ...). Current caches: breach 390/424/836 are npc-owned and the player copies use `playerframeset` (fx tool guard) - no overwrite present |
| C6 | frame scaling on real breach npcs | OPEN (LIVE-AV) | |
| C7 | OSRS attack blending with a 667 walk | NOT PRESENT | every monster whose attack is an imported OSRS sequence also stands/walks on imported OSRS sequences of the same rig; the 667-rig humanoids (Durial321, Magic Mark, Ranging Ro, I DSCIM YOU) use 667 attack sequences (AnimCoverage) |
| C8 | HD rotateNormals (op 14) | NOT CHANGED | CACHE: only 186 of 13 937 original 667 sequences carry op 14, so 667 itself does not use it for ordinary npc animation; screenshots before/after need the owner (LIVE-AV) |
| D1 | gfx 3050 visible/animating, long distance | FIXED (distance) / LIVE-AV open | CACHE: spotanim 3050 model 3959 + seq 15527, 8 frames, 59/59 groups reach vertices. A 667 projectile only reaches players whose loaded map holds its START tile: regional projectiles now start at most 30 tiles before the landing on the spawner line (`projectileStart`, FUNCTION BreachAuditTests D1) |
| D2 | per-monster attack/projectile/impact gfx | CACHE OK / LIVE-AV open | every section gfx and projectile exists (DeadmanBreachTests), breach spotanims 3050-3065 all animate their model (AnimCoverage). 667 spotanims 157/369/377/2731/2739 are original 667 graphics (old model format the checker does not decode) |
| E | skeletal (animaya) support | DONE for the breach itself / not for the 7 bosses | see "Skeletal" below: Breach spiral, Boss Spawn and breach spawn/despawn graphics baked into classic frames |
| F1 | close() vs old run task | FIXED | objects list emptied in close; run task returns when its sites are closed |
| F2 | breachclose leaves survivors, in-flight projectiles, summons | FIXED | close() clears open + lingering sites, Boss Spawns, every `live` breach npc (summons, Jad-Rek, test monsters); landings check `site.closed` |
| F3 | breachtele number = list index | FIXED | `breachtele site <1/2>` for the open breach, `breachtele <n>` = breachlist number |
| F4 | breachtest monsters give rewards | FIXED | `test` flag -> `dropLoot` pays nothing (FUNCTION BreachAuditTests F4) |
| F5 | broadcast / points via filterableMessage | FIXED | plain `message` |
| F6 | regional landing on the spawner, isFree only active | FIXED | footprint never overlaps any spawner; pending of lingering sites counted |
| F7 | exceptions leave `active` set / pending / Boss Spawn | FIXED | try/catch per tick and in open(); finally releases footprint and Boss Spawn |
| F8 | dropped locations not logged | FIXED | boot log names every dropped location |
| F9 | usable() filestore per tile; dropLoot name lookups | FIXED | map-square cache; one player map per kill |
| F10 | client crash risks | FIXED | client commit 233dee4f: AudioRenderer null stream, Animator missing sequence, FrameSequenceManager missing frame |
| F11 | duplicate 14462 key in test | FIXED | list of pairs + count assert |
| F12 | home board uses old WildernessBreach | FIXED | board already used `DeadmanBreach.statusLine`; stale import/doc removed |

## Regression baseline (before fixes)

Recorded as code/cache facts (no live baseline possible, see "Test limits"):
- `AnimCoverage` on both caches: all breach npc sequences OK; player seq 390 is an original 667 sequence (not re-encoded).
- 25 imported sequences with frame sounds had radius 0: GWD generals (Graardor 15614/15616/15617, Zilyana 15620/15621,
  K'ril 15622-15625), KBD fire 15610, bees 15664/15665, Jaguar 15669-15671, Zemouregal summon 15678, player weapon
  sequences 15809/15810/15818 (glaive) and the rest in the transaction journal.

## Test results (this run)

- FUNCTION: `:game:test` BreachAuditImportTests 4/4, OsrsFxConversionTests 2/2, OsrsPlayerSeqImportTests 6/6.
- FUNCTION: `:game:plugins:test` BreachAuditTests 5/5, DeadmanBreachTests 14/14, BreachRewardTests 11/11, BreachPerformanceTests 2/2,
  NpcCombatAudioTests 7/7, NpcAttacksTests 13/13, NpcLeashTests 4/4, DeadmanTimerGateTests 12/12, CityGuardsTests 21/21,
  GodWarsInteractionTests 7/7 (96 tests, all green after one fix for mocked definitions).
- RUNTIME_BOOT: `installDist` 05:43, game server booted headless 05:43:26: 0 ERROR / Exception lines, "Breach locations: 57 localised
  and 7 regional usable; 17 dropped (...)", "Deadman breaches: 40 monsters"; stopped through the command port (graceful).
- Not covered by an automated test (no world simulation in the test suite): the combat-cycle step rule (B1), the stall exit (B2),
  the aggro candidate choice (B4) and the leash return (B5). They need the owner's live check below.

## Regression (after)

Code-level: the B1/B2/B4/B5 changes apply to every data-attack npc (GWD, Jad, Fight Caves, guards use their own rules first) -
the GWD, guard, leash and npc-attack test classes are green. Player animations: C5 guard in the npc importer; no player sequence
was re-encoded (the radius repair touched only op-13 radius bits of 25 sequences, incl. 3 glaive player sequences which other
players can now hear). LIVE regression (owner): GWD generals + minions, Deadman guards, a few slayer monsters, Jad, one
ranged and one magic npc; player weapons 390/424/836, specials, ranged/magic sounds.

## In-engine test run (BreachSim) - added after the first report

The agent cannot log in to the client, so a headless test player was built into the server: start the server with
`-Drsps.breachSim=true` (never in the normal start) and it logs in a player without a network client, opens a real breach,
fires its spawner and puts every spawnable monster next to that player, measuring from the real game loop: aggro tick,
first attack tick, attack distance per swing (gap between footprints), animations used, standing idle while in reach,
keeps fighting while hit, follows 10 tiles, lets go and walks home past the leash, frozen (does not step) and resumes,
death animation + removal; plus a blocked-path, a single-way and a reward scenario. Results: `BREACHSIM` lines in
`launcher-logs\game-server-breachsim-*.out.log`.

Bugs the run found and that are fixed (all npcs, not only breaches):
- BFS npc routes ended on tiles for a 1x1 walker: 4x4 and 5x5 monsters (Graardor, Jal-ImKot, Night beast, smoke devil,
  Cerberus ...) never reached melee distance. `BFSPathFindingStrategy.getEndTiles` is now size-aware (FUNCTION BreachPathingTests).
- Smart npc routes went straight through standing npcs/players; the mover refused the step every tick and the monster stood
  behind the npc forever. The route search now avoids other bodies (`Pawn.occupiedTiles`), sim: goblin walks around a man.
- My own B6 fix had switched the single-way rule off for breach monsters: two monsters shared one player. Restored: in
  single-way a player fights one npc; the aggro search also skips a player another npc picked this tick. Sim: 3 goblins,
  2 players -> 1 each, the third waits.
- Greater abyssal demon teleported onto its target (3x3 footprint over the player): destination must now fit and not overlap.

### Final run per monster (2026-09-27 08:35, final build, 0 ERROR lines in the server log)

Site East of Shilo Village (single + multi), test player unprotected. Gaps are the distance between footprints when a swing started (1 = adjacent). Timing columns in game ticks.

| npc | monster | verdict | aggro tick | 1st attack tick | attack gaps | anims | idle in reach (max ticks) / speed | hit->keeps fighting | follows 10 | leash release / home gap | frozen stays / resumes | death anim, removed |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 14438 | Dagannoth Rex | OK | 3 | 9 | [1] | [15606] | 4 / 5 | true | true | true / 6 | true / true | [15609] true |
| 14439 | King Black Dragon | OK | 3 | 3 | [1, 2, 6] | [15610, 15611] | 3 / 4 | true | true | true / 0 | true / true | [15612] true |
| 14440 | Dagannoth Supreme | OK | 3 | 3 | [1, 4, 6] | [15608] | 3 / 4 | true | true | true / 0 | true / true | [15609] true |
| 14441 | Dagannoth Prime | OK | 3 | 3 | [1, 3, 6] | [15607] | 3 / 4 | true | true | true / 0 | true / true | [15609] true |
| 14442 | General Graardor | OK | 3 | 3 | [1, 4, 6] | [15614, 15617] | 5 / 6 | true | true | true / 0 | true / true | [15616] true |
| 14443 | Commander Zilyana | OK | 3 | 3 | [1, 3, 5] | [15618, 15621] | 1 / 2 | true | true | true / 0 | true / true | [15619] true |
| 14444 | K'ril Tsutsaroth | OK | 3 | 3 | [1, 4, 6] | [15623, 15625] | 5 / 6 | true | true | true / 0 | true / true | [15624] true |
| 14445 | Dharok the Wretched | OK | 3 | 7 | [1] | [15703] | 6 / 7 | true | true | true / 7 | true / true | [15707] true |
| 14446 | Porazdir | OK | 3 | 3 | [1, 4, 6] | [15627, 15628] | 5 / 6 | true | true | true / 0 | true / true | [15629] true |
| 14447 | Justiciar Zachariah | OK | 3 | 3 | [1, 4, 6] | [15630, 15632] | 5 / 6 | true | true | true / 0 | true / true | [15631] true |
| 14448 | Derwen | OK | 3 | 3 | [1, 4, 6] | [15636] | 5 / 6 | true | true | true / 0 | true / true | [15637] true |
| 14449 | Greater abyssal demon | OK | 3 | 9 | [1] | [15638] | 3 / 4 | true | true | true / 8 | true / true | [15639] true |
| 14450 | Giant goblin | OK | 3 | 7 | [1] | [15643] | 4 / 5 | true | true | true / 7 | true / true | [15641] true |
| 14451 | Flaming pyrelord | OK | 3 | 9 | [1] | [15646] | 3 / 4 | true | true | true / 7 | true / true | [15644] true |
| 14452 | Cave abomination | OK | 3 | 9 | [1] | [15649] | 4 / 5 | true | true | true / 6 | true / true | [15648] true |
| 14453 | Jal-ImKot | OK | 3 | 10 | [1] | [15650] | 4 / 5 | true | true | true / 6 | true / true | [15652] true |
| 14454 | Malevolent Mage | OK | 3 | 3 | [1, 5] | [15704] | 3 / 4 | true | true | true / 1 | true / true | [15707] true |
| 14455 | Vitreous warped Jelly | OK | 3 | 9 | [1] | [15654] | 3 / 4 | true | true | true / 6 | true / true | [15655] true |
| 14456 | Sulphur Lizard | OK | 3 | 7 | [1] | [15656] | 1 / 5 | true | true | true / 7 | true / true | [15657] true |
| 14457 | Night beast | OK | 3 | 3 | [1, 2, 6] | [15658] | 3 / 4 | true | true | true / 0 | true / true | [15660] true |
| 14458 | Cerberus | OK | 3 | 10 | [1] | [15662] | 2 / 3 | true | true | true / 7 | true / true | [15663] true |
| 14459 | Bee Swarm | OK | 3 | 9 | [1] | [15572] | 0 / 1 | true | true | true / 6 | true / true | [15665] true |
| 14460 | Thermonuclear smoke devil | OK | 3 | 3 | [1, 2, 4, 6] | [15666] | 1 / 2 | true | true | true / 0 | true / true | [15668] true |
| 14461 | Jaguar warrior | OK | 3 | 7 | [1] | [15669] | 5 / 6 | true | true | true / 7 | true / true | [15671] true |
| 14462 | TzTok-Jad | OK | 3 | 3 | [1, 4, 6] | [15672, 15676] | 7 / 8 | true | true | true / 0 | true / true | [15674] true |
| 14463 | Durial321 | OK | 3 | 3 | [5] | [1979] | 3 / 4 | true | true | true / 2 | true / true | [836] true |
| 14464 | Magic Mark | OK | 3 | 3 | [1, 4, 5] | [1979] | 4 / 5 | true | true | true / 1 | true / true | [836] true |
| 14465 | Ranging Ro | OK | 3 | 3 | [1, 5] | [2779] | 3 / 4 | true | true | true / 1 | true / true | [836] true |
| 14466 | Zemouregal | OK | 3 | 3 | [1, 2, 5] | [15677] | 6 / 7 | true | true | true / 0 | true / true | [15679] true |
| 14467 | Big Evil Chicken | OK | 3 | 9 | [1] | [15683] | 2 / 3 | true | true | true / 6 | true / true | [15682] true |
| 14468 | Splatter | OK | 3 | 9 | [1] | [15686] | 3 / 4 | true | true | true / 6 | true / true | [15684] true |
| 14469 | I DSCIM YOU | OK | 3 | 5 | [1] | [390, 12031] | 2 / 3 | true | true | true / 7 | true / true | [836] true |
| 14470 | Pestilent Bloat | NO-DIRECT-ATTACK(bloat) | -1 | -1 | [] | [] | 0 / 5 | false | false | true / 0 | true / false | [15687] true |

Scenarios: blocked path (goblin, man in between) first attack tick 9 OK; single-way (3 goblins, 2 players) 1 + 1, third waits OK; rewards (real Malevolent Mage, 300 damage) 3 drops for the player, tier-5 emblem, +300 breach points OK; breach open/fire/land/close: 3 projectiles, Boss Spawn seen and removed, 3 monsters landed, close removed all, no objects left.

## Skeletal (phase 4)

Source for the OSRS "animaya" format: MRobertEvers/oldschool-clientc `docs/skeletal/` (a port of the OSRS client: curves,
bones, bind pose, linear blend skinning). `SkeletalBakeTool` evaluates the skeletal clip like the OSRS client and bakes it
into classic 667 frames: vertices are grouped by their skin (max 63 groups), and per group and frame the blended bone
matrix is decomposed into the classic origin / scale / rotate / translate transforms of the 667 client (`JavaModel.I`
rotation order Z, X, Y; 16384-step trig; scale /128). The tool replays those transforms exactly like the client and
compares them with the skinning: max error 1.7 model units (spiral), 1.0 (Boss Spawn). No client change, classic
animations untouched.

Applied (both caches): tx-20260927-062328 (Breach loc 62747 now animates: OSRS 10418 -> seq 15828; Boss Spawn 62748:
10423 -> 15829; their models relabelled in place) and tx-20260927-062541 (VFX_DM_BREACH_SPAWN 2519 -> spotanim 3173,
VFX_DM_BREACH_DESPAWN 2553 -> 3174). `DeadmanBreach` plays 3173 when a spawner opens and 3174 when it closes; the old
stand-in pulse of the projectile graphic is gone. CACHE: AnimCoverage OK in both caches; FUNCTION: BreachAuditImportTests
(bake error < 3, both caches). LIVE-AV open (owner: does the spiral look like OSRS?).

Not done: the Boss effects VFX_DM_BOSS_* 2554-2558 (which id is which effect is not sourced) and the seven skeletal
monsters - the owner decided they stay off the server (Vardorvis, Scurrius, Phantom Muspah, both Wardens, Sol Heredit,
Yama). The same tool can bake them when the owner wants them (limit: 63 skin groups per model).
## Owner decisions / contradictions (reported, not changed)

- Stats version: `BreachMonsters` header says "Deadman: Annihilation"; the TzTok-Jad entry comment and the old test comment say
  "Permanent" max hits (78 / 108). Values untouched.
- Archaic emblem (tier 5) tradeable and worth 1.5M breach points: unchanged, please confirm.
- B3 smart pathfinding makes breach monsters un-safespottable (OSRS npcs use simple pathing) - done because the mission asked.
- Breach monsters are NOT in `BossNpcs` (would make their regions teleport-safe boss areas).
- ADAPTED values added: sound radius 15, projectile start 30 tiles before a far landing, God Wars clone ranged/magic reach 8.

## Branch / how to test

Server `game\game` branch `breach-fix` (commit 100eb50c3 on audit-fixes 638b9988d), client branch `breach-fix` (233dee4f).
Pushing was refused by the agent's permission check - push both `breach-fix` branches yourself. Not merged into audit-fixes:
the LIVE acceptance criteria are not proven yet.
Caches: tx-20260927-032334 (both caches, frame-sound radius of 25 sequences).
Owner test (commands without prefix): `breach local`, `breachtele site 1`, `breachtele site 2`, `breach region`, `breachtest`,
`breachtest 14462`, `breachinfo`, `breachclose`, with `C:\RSPS\avtrace.on` present and a second account in a second client
(`Start-RSPS-SecondClient.ps1`).