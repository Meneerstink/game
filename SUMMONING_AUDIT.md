## 2026-09-01 — special-energy recovery (automated)

- Summoning special energy now regenerates by 15 every 30 online seconds, capped at 60, while a familiar is active. The partial 30-second accumulator is persisted, so logout cannot reset or skip the recharge interval.
- `FamiliarPointsTests` covers the exact restoration interval and amount.
## 2026-09-01 — Beast of Burden completion batch (automated)

- Added all nine target-period Beast-of-Burden familiars: Thorny snail (3), Spirit kalphite (6), Bull ant (9), Spirit terrorbird (12), Abyssal parasite (7 essence), Abyssal lurker (7 essence), War tortoise (18), Abyssal titan (7 essence) and Pack yak (30).
- The three Abyssal familiars accept only unnoted rune/pure essence; all other BoB familiars reject essence. Dismiss, expiry, death and replacement now drop held items at the familiar rather than leaving hidden persistent storage. Logout preserves the active familiar and its storage for relog.
- Focused tests cover the registry, capacities, deposits, withdrawals and essence rule. Graphical interface 671 remains a separate client-contract task.
## 2026-09-01 — lifecycle data correction (automated)

- Replaced the invented gradual Summoning-point drain with the target-period one-time pouch cost.
- Added a production 78-row ledger for pouch cost and native familiar duration, sourced from the 2011 familiar roster and cross-checked with the preserved revision-634 data where the target cache does not expose the values.
- Renew is now gated below 2:50 remaining, consumes one matching pouch, and restores the native duration without a second point cost.
- Summoning points reaching zero no longer dismisses a familiar; lifetime expiry alone does. `FamiliarDefinitionTests` and `FamiliarPointsTests` cover the ledger, costs, expiry and renewal rules.
# Summoning familiar audit — Section A deliverable

Source data (both read in full, verbatim, this session — not summarized/guessed):
`game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/SummoningPouchData.kt`
(78 pouches, exact `level`/`npc` fields extracted via grep against the raw file, not memory) and
`SummoningScrollData.kt` (67 scroll entries; some scrolls are shared by a family of 2–7
npcs, giving 78 npc↔scroll pairings total — every one of the 78 pouches has exactly one
connected scroll). Per-scroll `specialPoints` cost is real data already in
`SummoningScrollData.kt` and is deliberately not duplicated/retyped here (risk of a transcription
error inventing a wrong number) — read that file directly for the exact cost of a given special.

**Rule applied throughout this table, per the work order:** a familiar is marked "yes" in a
`Today` column only if working code actually exercises that behaviour right now. Having a scroll
(all 78 do) is real data about the familiar's role, not evidence that this codebase supports its
special move — those are two different columns and must never be conflated.

## What is actually implemented today, cache-wide (not per-familiar)

| Capability | Status | Evidence |
|---|---|---|
| Summon (consume pouch, spawn npc, timer, XP) | **78/78**, subject to a real per-entry cache-option check | `familiar.plugin.kts` binds `on_item_option("summon")` only for pouches whose real `ItemDef.inventoryMenu` actually contains "summon" (checked at boot, logged as `boundSummon`/`skippedSummon` — not assumed) |
| Follow (collision-respecting `MovementQueue` step-toward-owner) | 78/78 (generic, npc-id-agnostic) | `Familiar.tick()` |
| Recover across teleport/plane change ("never permanently blocks/loses the owner") | 78/78 (generic) | `Familiar.tick()` — if the familiar's plane differs from the owner's, or Chebyshev distance exceeds `Player.NORMAL_VIEW_DISTANCE` (15, the real existing constant, not a new invented threshold), it is instantly repositioned next to the owner via `Pawn.teleportNpc` instead of trying to walk an unreachable route |
| "Call familiar" (explicit recall distinct from passive following) | 78/78 (generic) | `Familiar.call()`, bound to interface 662 component 49 |
| Renew | 78/78 (generic) | `Familiar.renew()` |
| Dismiss (manual, logout, owner death) | 78/78 (generic) | `Familiar.dismiss()`, called from the interact menu, `on_logout`, and now `on_player_pre_death` |
| Expiry (flat 20 min timer; per-familiar real duration table not sourced, still flat `LIFETIME_CYCLES`) | 78/78 (generic) | `Familiar.tick()` |
| Summoning-points economy (real max=level pool, immediate ~level/10 deduction, gradual drain totaling exactly `level` per life, early despawn on 0, persisted, HUD-wired to interface 662 components 41/44) | 78/78 (generic, R07.6) | `Familiar.maxPoints/currentPoints/setPoints/immediateCost`, `Attributes.SUMMONING_POINTS_ATTR`; formula sourced from 2011.rs's bunyip worked example (7 immediate + 61 gradual = level 68 total), 6/6 `FamiliarPointsTests.kt` passing. Previously this row said "no real upkeep-point economy exists" — no longer true. |
| No-replacement-exploit (summoning a 2nd familiar cleanly dismisses the 1st, no orphaned npc) | 78/78 (generic) | `Familiar.summon()` calls `dismiss()` before spawning |
| Beast of Burden storage (deposit/deposit-all/withdraw/withdraw-all, real `ItemContainer`) | **3/78**: Pack Yak (30), War Tortoise (18), Spirit Terrorbird (12) | `BeastOfBurden.kt` — capacities are the exact figures given in the work order itself |
| Special-move *effect* execution (any of the 67 real scrolls actually doing something) | **0/78** | no file in this codebase or in the upstream `2011Scape/game` reference repo implements any scroll's effect — confirmed by local grep across `content/npcs/**` and a GitHub Contents API listing of upstream's `summoning/` folder (7 files, none of them an effect handler) |
| Combat-assist damage (familiar fighting alongside its owner) | **0/78** | no Summoning familiar npc, here or upstream, has a registered `NpcCombatDef` (`set_combat_def`) — no attack speed/animation/stats/bonuses exist for any of them to fight with. This is a genuine content-authoring gap in the source project, not a local bug or a search miss (see `OWNER_TASK_STATUS.md` R07.3b for the full evidence trail) |

## Full 78-familiar roster

Format: `#. Name (level, Npcs.<const>) — scroll: SCROLL_NAME | BoB | notes`

1. Spirit Wolf (1, `SPIRIT_WOLF`) — scroll: HOWL_SCROLL
2. Dreadfowl (4, `DREADFOWL`) — scroll: DREADFOWL_STRIKE_SCROLL
3. Spirit Spider (10, `SPIRIT_SPIDER`) — scroll: EGG_SPAWN_SCROLL
4. Thorny Snail (13, `THORNY_SNAIL`) — scroll: SLIME_SPRAY_SCROLL
5. Granite Crab (16, `GRANITE_CRAB`) — scroll: STONY_SHELL_SCROLL
6. Spirit Mosquito (17, `SPIRIT_MOSQUITO`) — scroll: PESTER_SCROLL
7. Desert Wyrm (18, `DESERT_WYRM`) — scroll: ELECTRIC_LASH_SCROLL
8. Spirit Scorpion (19, `SPIRIT_SCORPION`) — scroll: VENOM_SHOT_SCROLL
9. Spirit Tz-Kih (22, `SPIRIT_TZKIH_7362`) — scroll: FIREBALL_ASSAULT_SCROLL
10. Albino Rat (23, `ALBINO_RAT`) — scroll: CHEESE_FEAST_SCROLL
11. Spirit Kalphite (25, `SPIRIT_KALPHITE`) — scroll: SANDSTORM_SCROLL
12. Compost Mound (28, `COMPOST_MOUND`) — scroll: GENERATE_COMPOST_SCROLL
13. Giant Chinchompa (29, `GIANT_CHINCHOMPA`) — scroll: EXPLODE_SCROLL
14. Vampyre Bat (31, `VAMPYRE_BAT`) — scroll: VAMPIRE_TOUCH_SCROLL
15. Honey Badger (32, `HONEY_BADGER`) — scroll: INSANE_FEROCITY_SCROLL
16. Beaver (33, `BEAVER`) — scroll: MULTICHOP_SCROLL
17. Void Ravager (34, `VOID_RAVAGER`) — scroll: CALL_TO_ARMS_SCROLL (shared, 4 Void familiars)
18. Void Spinner (34, `VOID_SPINNER`) — scroll: CALL_TO_ARMS_SCROLL (shared)
19. Void Shifter (34, `VOID_SHIFTER`) — scroll: CALL_TO_ARMS_SCROLL (shared)
20. Void Torcher (34, `VOID_TORCHER`) — scroll: CALL_TO_ARMS_SCROLL (shared)
21. Bronze Minotaur (36, `BRONZE_MINOTAUR`) — scroll: BRONZE_BULL_RUSH_SCROLL
22. Bull Ant (40, `BULL_ANT`) — scroll: UNBURDEN_SCROLL
23. Macaw (41, `MACAW`) — scroll: HERBCALL_SCROLL
24. Evil Turnip (42, `EVIL_TURNIP`) — scroll: EVIL_FLAMES_SCROLL
25. Spirit Cockatrice (43, `SPIRIT_COCKATRICE`) — scroll: PETRIFYING_GAZE_SCROLL (shared, 7 -trice familiars)
26. Spirit Guthatrice (43, `SPIRIT_GUTHATRICE`) — scroll: PETRIFYING_GAZE_SCROLL (shared)
27. Spirit Saratrice (43, `SPIRIT_SARATRICE`) — scroll: PETRIFYING_GAZE_SCROLL (shared)
28. Spirit Zamatrice (43, `SPIRIT_ZAMATRICE`) — scroll: PETRIFYING_GAZE_SCROLL (shared)
29. Spirit Pengatrice (43, `SPIRIT_PENGATRICE`) — scroll: PETRIFYING_GAZE_SCROLL (shared)
30. Spirit Coraxatrice (43, `SPIRIT_CORAXATRICE`) — scroll: PETRIFYING_GAZE_SCROLL (shared)
31. Spirit Vulatrice (43, `SPIRIT_VULATRICE`) — scroll: PETRIFYING_GAZE_SCROLL (shared)
32. Iron Minotaur (46, `IRON_MINOTAUR`) — scroll: IRON_BULL_RUSH_SCROLL
33. Pyrelord (46, `PYRELORD`) — scroll: IMMENSE_HEAT_SCROLL
34. Magpie (47, `MAGPIE`) — scroll: THIEVING_FINGERS_SCROLL
35. Bloated Leech (49, `BLOATED_LEECH`) — scroll: BLOOD_DRAIN_SCROLL
36. **Spirit Terrorbird** (52, `SPIRIT_TERRORBIRD`) — scroll: TIRELESS_RUN_SCROLL | **BoB (12 slots)**
37. Abyssal Parasite (54, `ABYSSAL_PARASITE`) — scroll: ABYSSAL_DRAIN_SCROLL
38. Spirit Jelly (55, `SPIRIT_JELLY`) — scroll: DISSOLVE_SCROLL
39. Ibis (56, `IBIS`) — scroll: FISH_RAIN_SCROLL
40. Steel Minotaur (56, `STEEL_MINOTAUR`) — scroll: STEEL_BULL_RUSH_SCROLL
41. Spirit Graahk (57, `SPIRIT_GRAAHK`) — scroll: GOAD_SCROLL
42. Spirit Kyatt (57, `SPIRIT_KYATT`) — scroll: AMBUSH_SCROLL
43. Spirit Larupia (57, `SPIRIT_LARUPIA`) — scroll: RENDING_SCROLL
44. Karamthulhu Overlord (58, `KARAMTHULHU_OVERLORD`) — scroll: DOOMSPHERE_SCROLL
45. Smoke Devil (61, `SMOKE_DEVIL`) — scroll: DUST_CLOUD_SCROLL
46. Abyssal Lurker (62, `ABYSSAL_LURKER`) — scroll: ABYSSAL_STEALTH_SCROLL
47. Spirit Cobra (63, `SPIRIT_COBRA`) — scroll: OPHIDIAN_INCUBATION_SCROLL
48. Stranger Plant (64, `STRANGER_PLANT`) — scroll: POISONOUS_BLAST_SCROLL
49. Barker Toad (66, `BARKER_TOAD`) — scroll: TOAD_BARK_SCROLL
50. Mithril Minotaur (66, `MITHRIL_MINOTAUR`) — scroll: MITHRIL_BULL_RUSH_SCROLL
51. **War Tortoise** (67, `WAR_TORTOISE`) — scroll: TESTUDO_SCROLL | **BoB (18 slots)**
52. Bunyip (68, `BUNYIP`) — scroll: SWALLOW_WHOLE_SCROLL
53. Fruit Bat (69, `FRUIT_BAT`) — scroll: FRUITFALL_SCROLL
54. Ravenous Locust (70, `RAVENOUS_LOCUST`) — scroll: FAMINE_SCROLL
55. Arctic Bear (71, `ARCTIC_BEAR`) — scroll: ARCTIC_BLAST_SCROLL
56. Phoenix (72, `PHOENIX`) — scroll: RISE_FROM_THE_ASHES_SCROLL
57. Obsidian Golem (73, `OBSIDIAN_GOLEM`) — scroll: VOLCANIC_STRENGTH_SCROLL
58. Granite Lobster (74, `GRANITE_LOBSTER`) — scroll: CRUSHING_CLAW_SCROLL
59. Praying Mantis (75, `PRAYING_MANTIS`) — scroll: MANTIS_STRIKE_SCROLL
60. Forge Regent (76, `FORGE_REGENT`) — scroll: INFERNO_SCROLL
61. Adamant Minotaur (76, `ADAMANT_MINOTAUR`) — scroll: ADAMANT_BULL_RUSH_SCROLL
62. Talon Beast (77, `TALON_BEAST`) — scroll: DEADLY_CLAW_SCROLL
63. Giant Ent (78, `GIANT_ENT`) — scroll: ACORN_MISSILE_SCROLL
64. Fire Titan (79, `FIRE_TITAN`) — scroll: TITANS_CONSTITUTION_SCROLL (shared, 3 elemental Titans)
65. Ice Titan (79, `ICE_TITAN`) — scroll: TITANS_CONSTITUTION_SCROLL (shared)
66. Moss Titan (79, `MOSS_TITAN`) — scroll: TITANS_CONSTITUTION_SCROLL (shared)
67. Hydra (80, `HYDRA`) — scroll: REGROWTH_SCROLL
68. Spirit Dagannoth (83, `SPIRIT_DAGANNOTH`) — scroll: SPIKE_SHOT_SCROLL
69. Lava Titan (83, `LAVA_TITAN`) — scroll: EBON_THUNDER_SCROLL
70. Swamp Titan (85, `SWAMP_TITAN`) — scroll: SWAMP_PLAGUE_SCROLL
71. Rune Minotaur (86, `RUNE_MINOTAUR`) — scroll: RUNE_BULL_RUSH_SCROLL
72. Unicorn Stallion (88, `UNICORN_STALLION`) — scroll: HEALING_AURA_SCROLL
73. Geyser Titan (89, `GEYSER_TITAN`) — scroll: BOIL_SCROLL
74. Wolpertinger (92, `WOLPERTINGER`) — scroll: MAGIC_FOCUS_SCROLL
75. Abyssal Titan (93, `ABYSSAL_TITAN`) — scroll: ESSENCE_SHIPMENT_SCROLL
76. Iron Titan (95, `IRON_TITAN`) — scroll: IRON_WITHIN_SCROLL
77. **Pack Yak** (96, `PACK_YAK`) — scroll: WINTER_STORAGE_SCROLL | **BoB (30 slots)**
78. Steel Titan (99, `STEEL_TITAN`) — scroll: STEEL_OF_LEGENDS_SCROLL

## Honest conclusion

Every one of the 78 familiars has real lifecycle support (summon/follow/recall/renew/dismiss/
expiry) and 3 of them have real Beast of Burden storage. **None of the 78 have a working special
move, and none can currently fight alongside their owner** — both are genuine, evidence-backed
gaps in the underlying data (no scroll-effect code exists anywhere, and no familiar npc has combat
stats anywhere in this codebase or its upstream source), not something this session invented
numbers to paper over. Steel Titan specifically: summon/follow/recall/renew/dismiss work exactly
like every other familiar; "Steel of Legends" and basic melee assist do not exist and cannot be
built without either sourcing real 2011 combat data for `Npcs.STEEL_TITAN` or an owner decision to
accept invented placeholder values (explicitly against the work order's own rules).

## Phase 8 — named representative verification (R07.9, this session)

Checked each of the 5 mandatory representatives' checklist items against real, running code
(not re-derived from scratch — builds on the Phase 1–5 work above):

- **Dreadfowl**: pouch creation/summon requirements, pouch/point consumption, interface/name/
  timer, and follow/call/renew/dismiss/expiry all real and working (generic `Familiar` code,
  exercised by `FamiliarPointsTests`). "Actual special behavior" (a strike attack via
  `DREADFOWL_STRIKE_SCROLL`) is the same genuine Phase 7 blocker as every other familiar — no
  scroll-effect code exists anywhere to drive it.
- **Spirit Terrorbird**: 12-slot BoB confirmed real (`BeastOfBurden.SPIRIT_TERRORBIRD_KEY`,
  exact capacity from the work order). Interface 671 partially wired (Phase 5, R07.8) — Close/
  Take BoB buttons work once open, but no real open-trigger was sourced. Store/take/lifecycle
  verified working via the generic `ItemContainer`; "full container" behaviour is the
  container's own real `add(..., assureFullInsertion = false)` best-effort transaction (already
  exercised by `deposit`/`depositAll`, returns a real partial-completion count rather than
  silently dropping items). Movement real (Phase 3). Special behavior (`TIRELESS_RUN_SCROLL`)
  blocked on Phase 7.
- **War Tortoise**: 18-slot BoB confirmed real, same container/lifecycle matrix as above.
  "Verified combat/special behavior" (`TESTUDO_SCROLL`) blocked — War Tortoise has no
  `NpcCombatDef` either (Phase 6 blocker applies to it exactly as it does to every other
  familiar; it is not exempt just because its real-RS special is defensive rather than
  offensive).
- **Pack Yak**: 30-slot BoB confirmed real. "Graphical storage" is the same Phase 5 interface-671
  open-trigger blocker as Terrorbird/Tortoise. "Bank interactions" (some later-era clients let a
  Pack Yak act as extra bank space near a bank booth) was not found as a real, sourced 2011-era
  mechanic this session and is not implemented — flagging rather than guessing. "Winter Storage
  item-target flow" (`WINTER_STORAGE_SCROLL`) blocked on Phase 7. **"No loss or duplication
  across every termination path" — verified real, not just asserted**: `BeastOfBurden`'s
  container is keyed by `player.containers`/`ContainerKey` (registered via
  `register_container_key`, the same generic mechanism `JsonPlayerSerializer` already persists
  any registered container through), entirely independent of the familiar npc's own lifecycle.
  Reading `Familiar.dismiss`/`expire`/`disconnect`/`restoreOnLogin` confirms none of them ever
  touch `player.containers` — a Pack Yak's stored items survive dismiss, expiry, owner death,
  logout, and login untouched, because nothing in the npc-lifecycle code path can reach them.
- **Steel Titan**: unchanged from the "Honest conclusion" above — everything except combat/
  special works; combat/special remain blocked on real Phase 6/7 data that doesn't exist
  anywhere in this codebase or its upstream source.

## Phase 9 — boot/runtime verification (R07.9, this session)

Booted the real game server (`./gradlew :game:run`, working dir `game/game`, real `data/cache`)
twice this session — the first attempt hit a pre-existing orphaned java process (PID from
earlier in this same session, unrelated to Summoning) already holding the game port; killed it
and re-ran clean. The clean boot: `RS Mod Server [Tek5] loaded up in 25295ms`, real port
`50015` confirmed listening (`netstat`), zero `ERROR`/`Exception` lines anywhere in the full
boot log, and the plugin's own diagnostic line confirms real registration at runtime (not just
compile-time): `R07.1 familiar: bound Summon on 78/78 pouches, Interact on 77/78 familiar npcs`.
This is real evidence the Phase 4/5 changes (login/logout persistence, dismiss confirmation, the
new 747/671 button bindings) load and register without exception against the live cache/plugin
system — status **booted**, one tier past "compiled" on the evidence-hierarchy scale.

**Genuine tooling blocker for full "live-verified" status**: this session runs in a headless CLI
environment with no attached `2011scape-client` GUI and no packet-level login-simulation harness
— there is no way to actually log in, see the client's rendered interface 662/747/671, or
visually confirm chathead/model/interface layout/mode-switch behaviour from here. The server-side
boot/registration evidence above is real and as far as this environment can verify; an actual
client login pass is an owner action, not something this session can fake or skip past silently.
(The server process used for this boot check was stopped afterward — nothing was left running.)
[Codex continuation — 2026-09-01]

- `f32d4ec0`: corrected four revision-667 pouch NPC mappings: Spirit Tz-Kih now uses `Npcs.SPIRIT_TZKIH`, Void Shifter/Spinner are no longer swapped, and Phoenix uses the familiar definition `Npcs.PHOENIX_8575`. Added deterministic roster regression coverage.
- `b0ad2644`: added the separate persisted special-move pool (`0..60`), capped point/special restoration helpers, and all target-cache Summoning potion dose bindings (legacy 12140-series and 5-dose 14277-series). A dose restores `maxSummoning/4 + 7` Summoning points and 15 special points.
- `1cf15653`: added guarded `Renew-Points` handling for every known revision-667 Summoning obelisk definition; it restores both pools without changing the familiar timer.
- Verification: `:game:plugins:test` with `FamiliarDefinitionTests` and `FamiliarPointsTests` passes; plugin compilation passes. Full suite remains blocked by pre-existing test-JVM OOM plus unrelated failures when all cache-heavy tests run together. No live-client verification is available in this headless session.

## 2026-09-01 — authoritative familiar-combat ledger (Codex continuation)

- Added a machine-readable 78-row `SummoningCombatDefinitions` production ledger. It links every target revision-667 pouch/base familiar to its combat variant candidate, stats, combat style, assist policy, range, speed, max hit, animations and projectile/graphic fields.
- The official defensive-only exceptions are explicit: Void Spinner, Bunyip, Unicorn Stallion and Pack Yak. The five non-combat foragers are explicit: Beaver, Macaw, Magpie, Ibis and Fruit Bat.
- 73 familiars are classified as fighting familiars. 72 have a complete sourced executable combat row. Albino Rat remains explicitly blocked because the preserved combat data does not contain its attack/death animation mapping; no animation ID was guessed.
- `SummoningCombatDefinitionTests` verifies all 78 rows, the 73/5 split, the four defensive-only exceptions, the single blocked row and named Dreadfowl/Steel Titan data. Targeted test result: `BUILD SUCCESSFUL`.
- This batch creates the validated data foundation. Native combat queue integration, owner damage/XP attribution and live gameplay remain the next batch; ledger completion alone is not claimed as working familiar combat.


## 2026-09-01 — native familiar combat runtime (Codex continuation)

- Added `FamiliarCombat`: 72 fully sourced familiar rows now use the normal combat queue, pathing, attack delay, line-of-sight/range checks, hit queue, animations, graphics/projectiles and combat formulas. Combat is restricted to multi-combat and all owner attack blockers are checked before a familiar receives a target.
- Owner-led assist runs from the existing familiar tick. Void Spinner, Bunyip, Unicorn Stallion and Pack Yak only assist defensively; the other executable combat familiars join a deliberate owner fight. Interface-target packets are bound for follower details 662 and fixed/resizable Summoning orb 747.
- Familiar damage now credits the owner through a general non-persistent `DAMAGE_CREDIT_ATTR`, so NPC killer/drop attribution uses the player. Familiar combat awards the matching combat XP plus Constitution XP. Delayed familiar hits re-check player safe-zone PvP eligibility before landing.
- Registered real NPC combat definitions for every executable base/combat familiar ID. The real server booted cleanly against the revision-667 cache, loaded 17,737 plugins, registered 78/78 Summon and 78/78 Interact entries, and listened on port 50015. The temporary boot process was stopped afterward.
- All focused Summoning tests passed before the final delayed-PvP guard; plugin compilation passed again after it. Live client combat still requires owner verification. Albino Rat remains the sole combat-animation data blocker and is not silently given a guessed animation.

## 2026-09-01 — typed special-move dispatcher, first verified effects

- Added revision-667 component-safe dispatch from both follower details (662) and summoning orb (747).
- Implemented Dreadfowl Strike, Tireless Run, Testudo, Winter Storage and Steel of Legends.
- Matching familiar, matching scroll and sufficient special energy are rechecked server-side; failed and stale targets consume nothing.
- Winter Storage preflights bank capacity and moves exactly one selected inventory item without loss/duplication.
- Focused special-move plus existing lifecycle, points, BoB and combat-definition suites pass (23 tests).
- Remaining 62 scroll effects and live client interaction proof remain open; this entry does not claim Summoning complete.

## 2026-09-01 — healing familiar passives

- Void Spinner now restores 100 internal life points and Bunyip 20 every 15 online seconds while active.
- Healing stops immediately on dismiss/expiry and never exceeds the player's normal maximum.
- Bunyip uses the sourced revision graphic 1507 only when healing actually restores life points.

## 2026-09-01 — boost, restoration and heal scroll batch

- Added Stony Shell, Thieving Fingers, Unburden, Abyssal Stealth, Volcanic Strength, Magic Focus, Healing Aura, Titan's Constitution and Insane Ferocity.
- All fourteen implemented special moves now share the same matching-familiar, scroll, energy and stale-click gate on both 662 and 747.
- Used source-backed revision-667 component, animation and graphic mappings; no new visual ids were guessed.

## 2026-09-01 — direct combat scroll batch

- Added Slime Spray, Electric Lash, Evil Flames, Dissolve, Rending, Doomsphere, Arctic Blast, Crushing Claw, Mantis Strike, Inferno, Spike Shot and Ebon Thunder.
- Projectile, source/target graphic, animation and damage data come from the preserved Summoning source tables.
- Added sourced secondary effects: stuns/binds and Attack, Strength, Defence or Magic drains where applicable.
- Dispatcher now covers 26 working special moves; the remaining effects are still open and are not counted as complete.

## 2026-09-01 — expanded combat-special batch

- Added source-backed Poisonous Blast, Swamp Plague, Boil, Deadly Claw, Acorn Missile, Iron Within, Fireball Assault, Sandstorm and the full six-tier Bull Rush family.
- Bull Rush now uses one registered revision-667 interface component and resolves the exact scroll from the active minotaur, preventing duplicate component handlers.
- Added multi-zone and canAttack filtering for AoE/splash targets; invalid or empty casts consume neither scroll nor special energy.
- Dispatcher now covers 35 registered component bindings and 40 executable scroll variants (Bull Rush contributes six variants through one binding).
- Focused SummoningSpecialMoveTests and plugin compilation: BUILD SUCCESSFUL. Live animation, projectile timing and target-selection still require owner client verification.
