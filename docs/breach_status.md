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
| A4 | ADAPTED sound families (Porazdir, Derwen, Jal-ImKot, smoke devil, Zemouregal) | OPEN | |
| A5 | section sounds with radius 0 do nothing on an npc | FIXED | breach monsters: radius-0 section / row / defend / death sounds become area sounds at the monster, radius 15 (ADAPTED: most common OSRS npc frame-sound radius in the imported data, 20 of 44). Other npcs keep the 667 Void rule. LIVE-AV (second player hears it) open |
| B1 | range deadlock (pathfinder "arrived" vs withinRange "too far") | FIXED | `combat.plugin.kts`: for every data-attack npc `NpcAttacks.withinRange` is the only "arrived" rule; not in reach = one melee-approach step per tick (no touch-radius box). Applies to all data npcs (GWD, Jad ...) - REGRESSION LIVE check open |
| B2 | wait deadlock in `PawnPathAction.walkTo` (route cut short) | FIXED | stall exit after `STALL_TICKS` (2) ticks without a step and nothing queued (4 with steps queued); npc combat does not wait at all any more (`waitForArrival = false`) |
| B3 | breach npcs without smartPathfinding | FIXED | `spawnMonster` sets `smartPathfinding` (BFS supports sizes up to 5); landing also needs `canLeave` (footprint can step at least one way). Note: OSRS npcs use "dumb" pathing; done on the owner's mission instruction |
| B4 | aggro: chase 8 instead of the leash, refused target blocks the monster, single-way pile-up | FIXED | `npc_aggro.plugin.kts`: a breach monster keeps a live target while `NpcLeash.mayPursue` holds; the search collects every candidate that passes the real checks (aggro rule, can_attack plugins incl. PvP protection, `Combat.canEngage`, leash, single-way "already fought") over the whole footprint and picks one at random. Applies to every aggressive npc |
| B5 | leash failure leaves the npc standing at the edge | FIXED | `NpcLeash.returnHome`: the npc walks back to its spawn tile when outside its wander radius (all non-familiar npcs) |
| B6 | PvP hit on a player releases the monster fighting him | FIXED | `combat.plugin.kts`: the single-way "already under attack" checks never release a breach monster (like the Deadman guards). `BossNpcs` NOT changed: making breach monsters bosses would also turn their regions into teleport-safe boss areas (owner decision) |
| B7 | attack styles/ranges vs OSRS Wiki | PARTLY | Graardor range / Zilyana magic / K'ril magic: range 1 -> 8 (their Void range-1 sections are GWD room scripts; ADAPTED 8). Checked and left: Pyrelord and Warped Jelly (OSRS pyrefiends/jellies use magic at melee distance), Cave abomination, Jal-ImKot, Cerberus, Jaguar, Splatter (melee per wiki text quoted in BreachMonsters). Big Evil Chicken magic range 1: not verified on the wiki - OPEN. `Custom.range` is only used for the row's attack_range (sections decide); Void `approach` unused - reported |
| C1-C3 | classic labels vs frame bases | NOT A BUG (data) | CACHE: `AnimCoverage` over all 40 roster npcs (BAS + attack/block/death + section anims) in BOTH caches: every frame present, parses on its base, and moves labelled vertices. No breach npc model is skeletal-only. |
| C4 | BAS idle-turn (38/39) missing | FIXED (encoder) / NOT NEEDED (cache) | encoder writes 38/39; `BasIdleTurnRepairTool` dry run: 0 BAS to patch - the OSRS breach npcs define only stand + walk (probe 12439, 12444) |
| C5 | npc import can overwrite player framesets | FIXED | `OsrsNpcImportTool.local`: a frameset/base/seq the fx tool owns gets an npc-only copy (`npcframeset` ...). Current caches: breach 390/424/836 are npc-owned and the player copies use `playerframeset` (fx tool guard) - no overwrite present |
| C6 | frame scaling on real breach npcs | OPEN (LIVE-AV) | |
| C7 | OSRS attack blending with a 667 walk | OPEN | |
| C8 | HD rotateNormals (op 14) | NOT CHANGED | CACHE: only 186 of 13 937 original 667 sequences carry op 14, so 667 itself does not use it for ordinary npc animation; screenshots before/after need the owner (LIVE-AV) |
| D1 | gfx 3050 visible/animating, long distance | FIXED (distance) / LIVE-AV open | CACHE: spotanim 3050 model 3959 + seq 15527, 8 frames, 59/59 groups reach vertices. A 667 projectile only reaches players whose loaded map holds its START tile: regional projectiles now start at most 30 tiles before the landing on the spawner line (`projectileStart`, FUNCTION BreachAuditTests D1) |
| D2 | per-monster attack/projectile/impact gfx | OPEN | |
| E | skeletal (animaya) support | OPEN | |
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
