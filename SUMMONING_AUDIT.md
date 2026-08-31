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
