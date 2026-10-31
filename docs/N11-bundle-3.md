# N11 bundle 3 - Toggle and Hunter Release

Scope: gpt-b/n11b3, 2026-10-31. No services, client, cache or player saves changed. Clean/Empty Bowl/Dish/Extinguish are Claude's separate work and untouched. All item constants already existed; Items.kt unchanged. Ava NPC, shop and Perdu/death rules untouched (N224/N461 owner correction).

## Behavior and sources

| Source key/item | Reference | Result |
| --- | --- | --- |
| S10498 Ava's attractor | https://oldschool.runescape.wiki/w/Ava%27s_attractor | Inventory Toggle op4 and worn Toggle op1 control random metal gathering. Iron table, 350 ticks and movement requirement reuse the existing assembler timer. |
| S10499 Ava's accumulator | https://oldschool.runescape.wiki/w/Ava%27s_accumulator | Same shared preference and menus; steel table. Account preference survives relog using existing `avas_assembler_gathering_stopped` key. Toggle leaves ammunition recovery active. |
| S10014 Black warlock | https://oldschool.runescape.wiki/w/Black_warlock_(item) and https://oldschool.runescape.wiki/w/Black_warlock | Release op4 replaces the filled jar in its own slot with butterfly jar10012. Strength boost4+floor(15% base), capped by the shared skill API; no Hunter level requirement to release. |
| S10034 Red chinchompa | https://oldschool.runescape.wiki/w/Red_chinchompa and https://oldschool.runescape.wiki/w/Talk:Red_chinchompa | Release op5 consumes one single chin immediately; a stack asks Yes/No before consuming the whole selected stack. Object identity, slot and amount are rechecked after dialogue. No ground item/XP. |
| S10031 Rabbit snare | https://oldschool.runescape.wiki/w/Rabbit_snare and https://oldschool.runescape.wiki/w/Hunter#Rabbit_snaring | SKIPPED as authorized: actual rabbit holes, ferret flushing and rabbit-snare states do not exist in this Hunter implementation. Generic timed bird/box traps are not this lifecycle. |

Read 2026-10-31 through indexed Wiki results; direct Wiki requests were robots-blocked. Black-warlock item contents also retrieved from the Wiki mirror https://osrsindex.com/wiki/black-warlock-item?site=osrs_wiki. Red stack confirmation and singular release message are explicitly recorded on the Wiki talk page (2024 observation); not invented from the current Drop label. Native667 retains Toggle/Release names and indices; no cache menu edits.

Local reuse: `items/osrs/AvasAssembler.kt` and `avas_assembler.plugin.kts` already own saved junk preference, timer, movement/torso checks and assembler Commune dialogue. New native Toggle changes the same preference directly. `combat/strategy/ranged/AvasDevices.kt` keeps its existing combat recovery rules. Attraction table weights follow the per-item Wiki tables (not donor random distributions). Existing assembler overflow/Commune behavior retained. Native device overflow goes to an owned ground item. Wearable native rewards use the shared EquipAction into compatible free equipment slots, then inventory, then ground.

Donor: `C:/RSPS/Donors/void/game/src/main/kotlin/content/entity/player/effect/AvasDevices.kt` confirms direct Toggle of junk collection and ground overflow. Its90-second timer and unweighted tables were NOT ported; existing OSRS timer retained. `.../content/skill/hunter/Butterfly.kt` confirms jar replacement and release visuals/message; `data/skill/hunter/hunter.anims.toml` open_butterfly_jar5213, `hunting.gfx.toml` release_black_warlock914. Donor bonus dispatch has inconsistent black-warlock name; used sourced numeric id10014 and Wiki boost, not that dispatch. Existing Hunter catch/Impling system unchanged; release is a separate shared route in the same package. Other butterflies/chins not expanded beyond this card. Important local identity: target10034 is `Items.RED_CHINCHOMPA_10034`; `Items.RED_CHINCHOMPA` is9977 and was not substituted. Acquisition evidence remains S10034 (Carnivorous chinchompa5080 drop + spawn), not a claim that the existing simplified trap output has that id.

## Files

Production under `game/plugins/src/main/kotlin/gg/rsmod/plugins/content/`:
- `items/osrs/AvasAssembler.kt`: native attraction tables/device set, existing saved key reused.
- `items/osrs/avas_assembler.plugin.kts`: guarded inventory/worn Toggle bindings, shared timer extension.
- `skills/hunter/HunterRelease.kt`, `hunter_release.plugin.kts`: shared guarded release and stack confirmation.

Tests under `game/plugins/src/test/kotlin/gg/rsmod/plugins/content/`:
- `n11/N11ToggleTests.kt`, `n11/N11HunterReleaseTests.kt`: compiled scripts, actual timer, slot/amount/stale guards, noted refusal, full inventory, rapid Toggle, existing bindings.
- `items/osrs/AvasCommuneTests.kt`: accepts RSPS_TEST_CACHE read-only reference path like existing route tests.

## Validation

Final run GREEN:42 tests,0 failures/errors/skips. Core/plugins/scripts compileKotlin successful. Changed-file lint4 production files/0 findings; git diff --check clean. First build caught a worn binding using Int instead of the required option-name String (fixed); first test run41/42 passed, remaining fixture used the wrong No component (fixed to228:3; Yes228:2). Final identical selection passed. Exact command:

```powershell
$env:RSPS_TEST_CACHE = 'C:\RSPS\game\game\data\cache'
.\gradlew.bat :game:compileKotlin :game:plugins:test --tests 'gg.rsmod.plugins.content.n11.*' --tests 'gg.rsmod.plugins.content.items.osrs.AvasCommuneTests' --tests 'gg.rsmod.plugins.content.skills.hunter.HunterDelayedInteractionTests' --priority low
```

## Claude testclient

Boot server and verify no duplicate bindings. Each class one real click plus avtrace:
1. Inventory10498/10499 op4 Toggle, wear and op1 Toggle; two rapid clicks return to initial state. Relog confirms preference. Wear non-metal torso, move3+tiles between350-tick collection intervals; on gives iron/steel, off gives none. Combat recovery remains active in either state. Metal torso still blocks collection. Full inventory with incompatible ammo receives an owned ground reward; compatible ammo can increase without an inventory slot. Existing assembler Commune remains functional.
2. Release10014 op4 with full inventory: jar10012 replaces it, Strength rises by4+15% base (99->117, without stacking beyond cap); avtrace anim5213/gfx914. Repeated stale click cannot release another item. Noted10015 refuses/no conversion. Catching still uses the preexisting Hunter route.
3. Release10034 op5: one disappears immediately; stack asks Yes/No, No retains all, Yes releases the selected whole stack. While prompt is open, replacing/moving/changing the selected stack must leave the current inventory untouched. No ground chin/XP created.

Open question: authorize a separate full rabbit-snaring task (holes, ferret flushing, trap ownership/states/recovery/level27), or keep Lay10031 parked? No simulated rabbit catch was added. Release sound has no verified donor id and is not invented; Claude should check visuals in667 with avtrace.
