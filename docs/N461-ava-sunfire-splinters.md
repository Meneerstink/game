# N461 - Ava and Sunfire splinters

Owner instruction (N461 dossier, 2026-10-30): Ava on Draynor Manor's second floor; splinters for coins around the OSRS market price, no requirements, unlimited stock. Dizana's quiver stays in the Loyalty Store.

## Sources and choices

- https://oldschool.runescape.wiki/w/Ava and https://oldschool.runescape.wiki/w/Animal_Magnetism (consulted 2026-10-30, indexed Wiki text): OSRS Ava lives downstairs behind the bookcase. The upper-floor relocation is the explicit owner customization, not an OSRS Ava spawn.
- Void `data/area/misthalin/draynor/draynor.npc-spawns.toml`: Ava `(3093,3357,0)`; Oddenstein `(3110,3367,2)`. Darkan `world-server-dev/data/npcs/spawns/real_rs_spawns_unorganized.json`: same coordinates. 2009scape `Server/data/configs/npc_spawns.json`: Oddenstein NPC 286 `(3111,3367,2)`; no Ava spawn found by NPC 5198. Donors are local read-only copies under `C:/RSPS/Donors` and `C:/RSPS/import-source/donors`.
- Ava's selected tile `(3109,3367,2)` is our custom placement next to the donor lab coordinates, validated against the actual 667 map/collision. No wandering, to keep her off lab scenery and ladder landings.
- 667 landscape `l48_52` (group 621) and terrain `m48_52` (group 620): staircase 47364 `(3107,3362,0)` / 47657 `(3108,3364,1)`; ladder 47574/47575 `(3105,3363,1/2)`. Existing `draynor_manor.plugin.kts` handles both directions explicitly; `data/cfg/stairs/stairs.json` needs no new entry. Lab door 47512 is at `(3108,3364,2)`.
- https://oldschool.runescape.wiki/w/Sunfire_splinters (consulted 2026-10-30): indexed GE guide price **379 coins**, stackable, OSRS ID 28924. This is the retrieved guide snapshot, not a claimed transaction price for 2026-10-30. Existing local imported ID: `Items.SUNFIRE_SPLINTERS = 22669`.
- Shop behavior reuses `KotlinPlugin.create_shop`, `CoinCurrency` / `ItemCurrency` and the existing `Int.MAX_VALUE` unlimited-stock convention. Separate Talk-to option and coin shop avoid Ava's device-shop skill gate. No donor implementation of this custom shop found; shop binding built here using existing server infrastructure. No cache edits.

## Checks and handoff

Target classes: `AvaLocationTests`, `AvaShardRuntimeTests`, `AvaShardShopTests`, `LocatorOrbTests`. Cache-backed Ava tests accept `RSPS_TEST_CACHE` for a read-only existing cache in a worktree without copying/modifying cache data.

`AvaLocationTests` loads the real 667 region and collision, checks all four stair/ladder objects and existing handler landings, and flood-fills the height-1 and height-2 routes. It models the lab door as open by removing that door in the disposable in-memory test world. The real door interaction still needs the testclient.

Claude: walk into the Manor, take the main staircase then the ladder, open the lab door, find Ava at `(3109,3367,2)`, check the old ground-floor spawn is gone. With low skills, Talk-to -> buy sunfire splinters; verify 379 coins each, purchases with full inventory with/without an existing splinter stack, unchanged stock, and no sell-back. Buy 150,000, charge up to 20,000 and bless the quiver at Ralos (total 150,000 contribution). Recheck locator-orb activation/replacement and the existing device requirements.

Test results (2026-10-30): **26 tests, zero failures/errors/skips**, Gradle exit 0. AvaLocationTests 2; AvaShardRuntimeTests 7; AvaShardShopTests 7; LocatorOrbTests 10. Command:

```powershell
$env:RSPS_TEST_CACHE='C:\RSPS\game\game\data\cache'
.\gradlew.bat :game:plugins:test --tests '*AvaLocationTests' --tests '*AvaShardRuntimeTests' --tests '*AvaShardShopTests' --tests '*LocatorOrbTests' --priority low --console=plain
```

`git diff --check` passes. No server, testclient or live verification performed by ChatGPT-A.
