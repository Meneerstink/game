# B continuation — 15 November 14:32

Base `bfbc07b8e`, branch `gpt-b/clue-scrolls-7`; full card assigned by dossier 14:3x.
No cache apply, server/client operation, push, merge or live acceptance.

## Worktree data regression

Ordinary `:game:test` and `:game:plugins:test` tasks did not set `rsps.data`.
The default in `RspsPaths` deliberately points at the canonical laptop checkout;
therefore source-backed tests loaded a different branch's catalogues. The earlier
custom init script masked the problem in B's runs. Both actual test tasks now set
the absolute data directory of their root project and declare clue catalogues as
inputs. The core chart tests use the same module-relative source fixtures as the
existing clue-interface tests, also declared as inputs. The standalone chart
runner now uses that module's working directory. Shared production `RspsPaths`
defaults are unchanged; Gradle's local `:game:run` explicitly passes its project
data path, preserving an explicit `-Drsps.data` override. Deployed launchers keep
their current configuration.

Ordinary targeted run, **BUILD SUCCESSFUL / 2m58s**, no init script:

```powershell
.\gradlew.bat :game:test --tests '*ClueStashChartImportTests' :game:plugins:test --tests '*TreasureTrailStashTests' --tests '*TreasureTrailStashChartTests' --priority low --console=plain
```

Fresh XML evidence, all 15-Nov 14:37–14:38 local time; **14 tests, zero failures,
errors or skips**:

* `game/build/test-results/test/TEST-gg.rsmod.game.tools.importer.ClueStashChartImportTests.xml`: 2.
* `game/plugins/build/test-results/test/TEST-gg.rsmod.plugins.content.scrolls.TreasureTrailStashTests.xml`: 10.
* `game/plugins/build/test-results/test/TEST-gg.rsmod.plugins.content.scrolls.TreasureTrailStashChartTests.xml`: 2.

Core, plugin libraries and plugin-script compilation passed in the actual Gradle
task graph (unchanged compiler outputs legitimately reused from Gradle's cache).
This accepts the path correction, not the still unfinished Treasure Trails card.

## Next work

Complete the equipped-item setting using the pinned source struct 6245 label/help
and the existing 667 Settings UI. Source-backed step registration/handlers,
challenge interfaces, guardians, acquisition gaps and the Mimic Q1 source gate
remain open. Claude alone performs the previously documented client-first cache
application, multiloc traversal/door/farming checks, Watson bootcheck and loaded
client/live proof. POH Study/chart/blueprint sales remain excluded by Q5.

## Equipped STASH setting, 14:46

`ClueStashSettingCacheTool` + `TreasureTrailStashSetting` and its script implement
the pinned source setting in the existing Settings tab, using the same persistent
flag as the tested storage transaction. Five focused standalone tests passed;
the real native Component decoder accepted all three server fixtures. Read-only
preflight tx-20261115-134520 passed three logical mutations, both caches/ledger/
runtime mappings unchanged. Exact IDs, command, source, batch ordering and client
checks are in CLAUDE-HANDOFF.md. No cache apply or client window. Full Gradle/script
integration remains required after this checkpoint.
