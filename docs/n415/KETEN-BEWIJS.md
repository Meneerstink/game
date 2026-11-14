# N415 evidence gates

Scope: MA1, all three regular MA2 guardians and healer, normal/imbued/ancient/max cape variants, quest items, area and all routes. Research: RESEARCH.md, source-digests.json, ancient-source2762.json, ancient-model-comparison.json. Current implementation is partial; no point below certifies runtime or live parity. Tests are recorded separately after execution.

## General

| Gate | State | Evidence / remaining work |
|---|---|---|
| A1 | DEELS | Wiki/transcripts,2686/2762 and gameval read; unresolved timing/heat/legacy decisions listed. |
| A2 | DEELS | Existing5forms,3Breach actors,12source actors,4quest items,7ancient imports inventoried; full item consumers/mappings pending. |
| A3 | DEELS | MA1 routes plus MA2 start/replacement/hand-ins/imbue/journals, ancient/max recipes and both Destroy routes implemented. Tracking/guardian encounters/death exceptions remain open. |
| A4 | DEELS | Shared magic launch, bank, QuestStatus, max/perk/emote, Perdu/Trouver and store consumers reviewed/integrated; drop/log/tracking and D4 decision pending. |
| A5 | OPEN | Targeted MageArena*, GodSpellTraining, AncientCape, QuestStatus/QuestTab, Perdu, Store tests started; final per-class result must be attached. No full suite. |
| A6 | OPEN | Claude testclient; ChatGPT task-card prohibition. |
| A7 | DEELS | Source stats/recipes compared; no OSRS side-by-side client proof. |
| A8 | DEELS | Staff/full/duplicate and duel ownership/death replay automated tests prepared; actual5runs/death/logout/restart pending. |
| A9 | OPEN | Claude release and ordinary-client verification. |
| A10 | DEELS | Dossier progress and handoff maintained; no AF or list edits by ChatGPT. |

## Boss chain: each row covers Kolodion AND separately Zachariah, Derwen, Porazdir/healer

| Gate | State | OSRS / local difference / evidence |
|---|---|---|
| B1 | DEELS | Kolodion uses original3105,3933 landing; MA2 Wiki candidate areas need collision validation and personal rotation. |
| B2 | DEELS | MA1 explicitly targets owner; regular MA2 source aggression differs by actor, no runtime script yet. |
| B3 | OPEN | MA1 keeps existing three-spell attack table; MA2 special attacks/timing not built. |
| B4 | DEELS | MA1 stats applied. MageArenaIIGuardians prepares source320HP/6ticks definitions; no complete MA2 encounter activation. Breach unchanged. |
| B5 | DEELS | Removed false human-form bones table; regular MA2 remains/drop ownership still absent. |
| B6 | OPEN | Source pages show no pet; full pet/drop-log absence must be verified before NVT. |
| B7 | OPEN | Quest drops/DV/CL/kill-count policy still pending; no invented reward slots. |
| B8 | OPEN | Correct quest/location/minimap flags and roaming visibility; test/live proof pending. |
| B9 | DEELS | Existing world routes retained, normal death applies; MA2 spawn/loot/escape restrictions pending. |
| B10 | OPEN | Deadman tasks/emblem/logging/drop sounds require explicit regular-boss audit. |
| B11 | OPEN | Existing MA1 data-driven audio preserved; source sequences/synths and two-account avtrace/hearing pending. |
| B12 | OPEN | MA1 forms3/4 still667 troll/dark beast. Imports prepared, actor/rig mapping not applied. |
| B13 | OPEN | Attack delays sourced; exact MA2 special/cast/impact probabilities/timings missing. |
| B14 | DEELS | MA1 owner-specific persistent five-form transitions; MA2 spawn/combat/reset states absent. |
| B15 | OPEN | Derwen healer7514 imported by prepared batch only; count/spawn/heal/expiry needs source. |
| B16 | DEELS | MA1 captured-style gate. MA2 launch gate uses explicit spell id; impact helper uses captured metadata. Poison/venom definitions prepared. Recoil, prayer factors and actual encounter binding remain open. |
| B17 | DEELS | MA1 stats applied; MA2 definition factory preserves Derwen80Magic/+180magic attack and avoids double-applying20/55% XP. Runtime activation pending. |
| B18 | OPEN | Original MA1 spawn/cleanup uses normal-view distance, not an asserted OSRS leash; all map collision/size/path/pull proofs pending. |
| B19 | DEELS | MA1 owner/reset logic; real multiplayer/logout/death/teleport and MA2 tolerance not verified. |
| B20 | DEELS | MA1 unowned/stale death cannot award completion; two-player actual encounters and regularMA2 loot pending. |
| B21 | DEELS | MA1 refusal/staff and MA2 requirements/advice/hand-ins/imbues use Wiki transcripts. Early individual imbue evidence conflicts with first-all-three interpretation; source decision open. |
| B22 | DEELS | Only final MA1 form marks completed encounter; source death-animation duration/art and all MA2 expiry/drop cleanup pending. |
| B23 | OPEN | No safe-death shortcut; D1-D20/N522 actual PvM/PvP corpse/killerloot/teleblock tests pending. |
| B24 | OPEN | Pool/lever/web/Mage Bank safety retained; no OSRS parity claim without client/area checks. |
| B25 | DEELS | Final Kolodion demon107SlayerXP registered/tested by A; Porazdir black-demon376XP prepared. Derwen has no Ent assignment. Runtime proof pending. |
| B26 | OPEN | Correct source names/levels/options known; current667forms/Breach levels are not regularMA2 assets. |
| B27 | OPEN | Deadman-specific death/risk/emblem/task policies untouched; regularMA2 integration pending. |
| B28 | DEELS | Automated real owned death chain/replay test prepared; actual5kills/death/logout/restart still open. |
| B29 | DEELS | Source URLs/hashes/npc/sequence/item source ids recorded; source gaps explicit. |
| B30 | OPEN | This matrix is a gap inventory. No boss is AF while these OPEN/DEELS rows remain. |

## Item, area and full-miniquest integration

| Gate | State | Evidence / remaining work |
|---|---|---|
| I1 | DEELS | Exact source names/options/models decoded; local allocation, examines/icons and all visual variants pending. |
| I2 | DEELS | Symbol/remains Destroy and ancient fabric/normal/max/hood recipes implemented against mapped ids. Symbol Activate/tracking and mapped option verification remain open. |
| I3 | DEELS | Ancient import specs translate normal cape75Magic; stats/slot sourced. Final cache allocation/equip/runtime verification pending. |
| I4 | OPEN | Both body types/gear combinations/icons/ground models require imported art and client. |
| I5 | DEELS | Normal/broken/locked/max/hood source variants identified; no local mapping yet. |
| I6 | DEELS | Real Bank.deposit rejects remains; Destroy has original-slot check. Cache untradeability/GE and death loss versus D4 still require proof/decision. |
| I7 | DEELS | Mapped ancient96k/99k breakables and normal(l) factory implemented. Named bindings for visible(l)broken/mangled clones required; exporter refuses incomplete clones and lockable factory refuses a half-pair. Claude cache transaction and actual D1-D20 proof pending. |
| I8 | DEELS | Atomic first staff and MA2 first/repeat imbues implemented. Earned MA2 grants repeat free ancient fabric as requested alternate acquisition. Full route still depends on tracking/bosses/cache. |
| I9 | OPEN | New equip/attach/detach/destroy audio needs source and client verification. |
| I10 | DEELS | First-staff entitlement/duplicate tests prepared; full inventory+bank+grave+Perdu recipe/death accounting pending. |
| G1 | OPEN | Existing arena map retained; exact OSRS comparison/import proof outstanding. |
| G2 | OPEN | Normal landing resolver retained; MA2 candidate positions/pulls need collision proof. |
| G3 | OPEN | Pool/lever/web routes require actual testing; MA2 wilderness restrictions pending. |
| G4 | OPEN | Teleports/obelisks/jewellery/escape/teleblock regressions pending. |
| G5 | OPEN | Quest/map/minimap cache work and liveA9 pending. |
| G6 | DEELS | Shared Kolodion spawn replaced by owner duel. PvP/single/multi/safety HUD unchanged, not certified. |
| G7 | OPEN | Existing clue/landings/nearby NPC/breach coexistence requires Claude verification. |
| G8 | OPEN | Existing objects retained; full names/options/examines/render/animation proof pending. |
| QUEST | DEELS | Earned state, base75+3trained spells, journal/custom quest rows, hand-ins/first/repeat rewards and placeholder-reset display implemented. Pure cache progress patch ready for Claude. Tracking/bosses/early-imbue conflict and complete runtime route remain open. Current sect9 has no separately numbered K-QUEST list. |

Additional applicable chains remain open: deathD1-D6, soundS1-S3, shopW1-W5, spellP1-P4 and existing-content fixF1-F8. Detailed player checks and source/owner questions are in CLAUDE-HANDOFF.md. No source gap is signed off as NVT.
