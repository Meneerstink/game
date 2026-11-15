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

## Frozen branch acceptance, 15 November 15:10

`build/clue/integration-20261115-134729/summary.json`: **accepted=true**,
**124 tests / 35 explicit classes**, zero failures/errors/skips/missing reports.
All **4,453** Kotlin/Gradle/runtime-catalogue/used-fixture hashes stayed identical.
Core + plugin libraries + all plugin scripts compiled in the actual task graph;
**BUILD SUCCESSFUL / 22m12s**. This includes the new Settings script and ordinary
STASH worktree paths, the inherited Watson variants/noticeboard, shared multiloc
fallback/dispatch regressions, reward/log/viewer/lifecycle and Mimic tests.
No live/testserver/client operation or cache write occurred.

Requested Mimic definition XML:
`game/plugins/build/test-results/test/TEST-gg.rsmod.plugins.content.areas.watson.TheMimicCombatDefTests.xml`
� **tests=1, failures=0, errors=0, skipped=0**, fresh this run. This supersedes the
missing-XML discussion. Encounter acceptance still requires the documented Q1
source timing and full mechanics/client checks.

The Father Aereck correction is also tested: ordinary state accepts **19**;
source answer20 remains recorded with its exact Jarvis/Halloween2017 condition.
Both source curation and runtime generation reproduce the correction and verify
the pinned source SHA. The new explicit catalogue regression passed. No event
state or unconditional alternate answer was invented.

This is a tested source/backend checkpoint, not full CLUE completion: donor pool
replacement, subtype handlers, challenge UI/physical items, dig combat, unresolved
source/area/variant cases, and Claude's client/cache/live gates remain open.

## Delayed clue interaction guard, 15 November 15:13

`ClueStepContract` captures the actual account, held Item reference, step key,
registered step object and tier progress before a dialogue/puzzle suspends. A
later answer must still match them all before the existing atomic advance/consume
transaction runs. Moving the same inventory item is allowed; a copied/replaced,
banked or different-account clue, changed progress/key and replay are rejected.
No account-global pending answer is introduced.

`python tools/clue/check_compile.py HEAD contract-tests`: **four fresh standalone
tests passed**. This is the shared guard for the forthcoming subtype handlers;
no donor step registration has been replaced by this helper alone. A focused
Gradle run is still required when the actual consumers are integrated.
