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

## Falo source/runtime checkpoint, 15 November 15:30

New source action, catalogue and dialogue: `TreasureTrailSourceStep`,
`TreasureTrailTaskCatalogue`, `TreasureTrailFalo`, `TreasureTrailFaloDialogue` and
`treasure_trail_falo.plugin.kts`. Seven pinned lyrics; actual requested canonical
and ornamented variants, unnoted only; shown object is retained. The chosen lyric
is an appended per-item attribute, survives copies/drop/bank/relog and is not
rerolled by repeat dialogue. Successful skill state has a separate appended field
for the next Sherlock handlers. Existing enum ordinals remain unchanged.

The shared lifecycle clears these two task fields only on a genuinely new step
or removed-step migration, preserving them on a valid reread/re-pick. The exact
pending interaction guard also rejects death before inventory recovery runs.
Falo dialogues revalidate the exact clue and shown object after suspension.
The source uses a Fremennik name; like existing Olaf, this world uses the account
name because it has no Fremennik-name provider. Ordinary quests already count as
completed through QuestStatus; no new quest or unlock bypass is added.

`python tools/clue/check_compile.py HEAD falo-tests`: **18 fresh standalone tests
passed**, 6 Falo / 2 NPC asset guards / 5 interaction contracts / 5 real lifecycle.
Includes actual full-inventory Falo advancement, retaining charged gear, wrong and
noted items, unknown saved-task refusal, serialization, two accounts, stale clicks,
death, removed donor migration and task reset. The seven production libraries,
modified manager and appended core enum compile. New script still needs the next
actual Gradle/plugin-script acceptance; no client or release acceptance yet.

`build_challenge_runtime.py` derives 7 Falo and 13 Sherlock entries from pinned
source hashes. `build_npc_mapping.py` audits both native caches and the actual
ledger, verifies source/native models, portraits, menu/size/level and movement set,
and publishes no runtime mapping while the 9-source `clue-steps` batch is missing.
Audit now reports **0 verified / 9 awaiting imports**. Runtime `ClueNpcs` accepts
only a complete applied mapping and rechecks loaded definitions; sourced Wiki
examines are installed after validation. No proposed source ID becomes a local ID.
After the batch, Falo is placed at the source 2689,3549 pin/radius4 with Talk-to and
item-on-NPC handlers; one Falo source step registers. The donor pool is still
present elsewhere: this checkpoint does not claim all 69 steps are active.


## Sherlock checkpoint, B 15 November 16:08 - commit 0f1466238

Thirteen selected source tasks connect to actual successful skill actions and a
Nechryael kill in the sourced Tower top-floor/basement. Correct product/location
and a physically held issued task are required; having the finished product does
not count. Current visible boosts satisfy the source level requirements. Actions
mark once; return to Sherlock is required before advancing.

Elite handover replaces the physical clue with imported source12128 in the same
slot, preserving attributes and supporting a full inventory. Successful return
atomically restores the canonical clue or gives a casket. Inventory/bank/grave/
Death/Mimic-recovery ownership, box counts, Read and Watson recognize this alias.
Completed tasks take priority over incomplete other-tier assignments (pinned Wiki
Sherlock8-Dec-2016). Master retains the owner's78 branding. Missing imports retain
the original clue and progress. New scripts require the applied9-NPC mapping.

python tools/clue/check_compile.py HEAD sherlock-tests: 40 fresh standalone tests
passed:12 Sherlock,6 Falo,2 NPC guards,5 contracts,5 lifecycle,4 drop-ownership,
6 Watson. Compiled the appended core enum and51 libraries, including12 actual
skill hook files. First compile's missing Items import was fixed before the green
run. New script/Gradle acceptance is pending in the frozen39-class integration.
Exact source/cache/client instructions: CLAUDE-HANDOFF.md Falo/Sherlock section.
The old donor registration and remaining subtype/guard/puzzle work remain open.


## Resumed source probes, B 15 November 19:43

Reviewed the three interrupted Java probes. ClueDbRowProbe decoded all16,844
config2/38 rows in cache2686. Framing/types checked against primary RuneLite
DBRowLoader/DBTableLoader/ScriptVarType; code guidance, not gameplay evidence.
Unknown field types now fail. cache/selected-step-db.json keeps60 selected
clue identities,149 referenced rows and4 explicit unresolved Wiki IDs; no guess.

ClueSearchSceneProbe rechecked all4 selected searches in both actual scenes,
including menus/planes/bridge flags/SHA256s. Keys are never printed. Evidence:
cache/search-scenes.json. Swamp bookcase absent; Duke closed chest37009 at
3209,3218,1 differs from nearby quest chest6910; jail bucket9568 lacks Search;
Lumber Yard crate2620 matches. First invocation used the wrong input schema;
fixed before export. Probe now filters SEARCH from the runtime steps file.

ClueMapMatchProbe compares rendered native geometry units. No complete
field-for-field mesh match found; no native map artwork assumed equivalent.
Map imports remain required. These probes call no cache writer.

Reproduce via Java11 source mode, classpath game/build/classes/kotlin/main;
game/build/install/game/lib/* (semicolon joins paths). DB arguments:
<source-cache> build/clue/db-rows.json all. Search arguments: <source-cache>
<native-cache> <native-data>/xteas data/cfg/clue/steps.json
build/clue/next/search-scenes.json. Then python tools/clue/build_step_evidence.py.
Generator/py_compile/diffcheck passed. Full39-class integration started19:36,
4469 frozen inputs; waits for Claude/buildslot/4GB. No acceptance result yet.
