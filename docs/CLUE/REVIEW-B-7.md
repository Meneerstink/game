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


## Frozen inherited integration accepted, B15-Nov20:29

build/clue/integration-20261115-183642/summary.json: accepted=true, exit0,
unchangedInputs=true (4469), changed=[], missing=[], tests149, skipped0;
39 explicit classes all failures/errors0. Actual Gradle BUILD SUCCESSFUL51m33s,
including buildslot/RAM waiting. Core/plugins and all plugin scripts compiled.
This accepts the inherited Falo/Sherlock implementation at0f1466238 and the
other selected CLUE regressions. It does not accept the later staged changes or
client/live behaviour. No runtime/cfg/Gradle file changed during that run.

Staged new numeric backend: check_compile.py HEAD basic-staged-tests eventually
passed10 fresh tests, including full inventory, wrong/conditional answers,
exact-item and account guard, bank/drop/serialization/death/slot movement,
last-step casket and Watson hand-in. Earlier rejected attempts: the new staged
mode initially misclassified its source paths, then one test called ItemContainer
isEmpty as a function. Both corrected before the green run. The tested five
libraries remain in build/clue/next until promoted. Source dialogue strings use
the pinned NPC transcripts; Baraek's mislabeled Dunstan prompt is omitted.


## UI camera source promoted, B15-Nov20:34

Five staged codec tests and real unchanged667 Component decoder passed. Actual
ClueInterfaceImportTool + ClueInterfaceImportTests are normalized-byte identical
to those tested files (interface-port/promoted-proof.json). Expected camera now
comes from preserved raw OSRS bytes, not only a hardcoded converter expectation.
The normal codec verifier now reads actual sources again; temporary staged mode
removed. Corrected UI PREFLIGHT tx-20261115-193224 passed27 mutations and all
cache/ledger/runtime mapping fingerprints stayed identical. Exact current IDs
in UI-PREFLIGHT.tsv and latest CLAUDE-HANDOFF section. No apply/client process.
Whole-branch Gradle acceptance for the subsequent gameplay changes is still due.


## Basic source routes and paper challenges, B15-Nov20:55

Actual source libraries compile: two core files and45 scroll/Watson libraries.
The focused14-class cohort has82 passing tests after fixture correction:
76 passed in its first run;6 failed only because MockK nested property getters
were recorded in stubs and a core fixture used a module-relative path from the
standalone root. Both corrected. The2 affected classes then passed all7 tests;
no gameplay library changed between those runs. Final Gradle/script validation
is still required. Temporary staged helper modes have been retired; use actual
source command python tools/clue/check_compile.py HEAD basic-tests.

The former41 donor steps are no longer registered in every tier. The registry
now admits implemented source types with their real menus/assets: plain cryptic
and guard-free coordinate digs; sourced kill; three exact Search crates and the
jail bucket only after its actual Search menu exists; plain sourced NPC hand-ins;
nine numeric challenges when the corresponding NPC/paper import is available.
Falo/Sherlock keep their dedicated registrations. The unused old donor data
classes remain for now; their removal waits for the new emote/map adapters.
No guarded/puzzle/map/emote/hot-cold row becomes an unguarded free completion.

Numeric papers2842/7269/12139 are tier aliases in ownership, bank/grave/Death,
box capacity, Read/Check steps and Watson. Same-slot handover preserves a full
inventory and all progress. The exact held-item/account/step/progress contract
rejects stale answers, a replacement item and replay. Wrong answers spend
nothing; ordinary Father Aereck accepts19, not the unimplemented Jarvis event20.
Read shows the actual question.11 tests also pin every emitted line to the
original conditional transcript+SHA, preserving the PLAYER interjection and
omitting Baraek's misattributed Dunstan prompt. Missing dialogue remains absent.

Two searches replaced unsupported routes without adding scenery: swamp hut
bookcase and ambiguous closed Duke chest -> Rommik9533 at2946,3207,0 and
Varrock Castle source5113/native46266 at3224,3492,0. Both actual scenes, exact
menus and hashes are preserved; excluded pair evidence is retained separately.
The Lumber Yard crate2620 and jail bucket9568 remain. Bucket cache-menu patch
only appends the sourced Search option and preserves all existing model/other
bytes. Its real native decoder test passed; PLAN/PREFLIGHT still to run.

Master category selection now follows pinned Full guide/Master15384814 / cited
Mod Ash16-May2024: choose a category equally, then a step within it. Tests cover
all7 categories and differently sized groups; other tiers do not inherit an
unproven equal-category rule. The active subset still has explicit missing
subtypes; this is not whole-card or loaded-client acceptance.


## Resumed maps, B16-Nov00:51

Resumed at0258ffc25 plus the uncommitted map probes/source exports. The previous
integration200204 was killed while waiting; no summary, no acceptance. Both
native/server caches remain Claude-owned and were only opened read-only here.

Nine selected source illustrations compared manually against pinned Wiki images
and decoded cache2686 model geometry/cameras; sources/map-images/manifest.json
pins URLs and downloaded SHA256s. Reproducible build_map_sources.py ties all nine
selected map keys to those illustrations, source DB rows/hashes and real Close
components. Geometry preview is a navigation aid, not a client rendering proof.

ClueInterfaceImportTool supports all675 actual source map components with exact
child counts/parents/cameras/model imports. Both UI batch modes now preserve the
other batch's verified mapping on reruns. Missing interior or final children are
rejected. Missing map imports cannot disclose the answer as plain-text hints.
Reading enables the actual source Close; dig/search use the held-clue contract.
No monsters are attached to map digs, per Wiki Maps. No beginner content added.

Eight fresh source-code importer tests passed. The independent native Component
decoder passed all675 encoded map components, including wide model IDs, parent
hierarchy, dimensions, raw camera order and Close hover hooks. Initial fixture
failure: did not set Component.slot to its group before decode (normal loader
does); corrected without changing production bytes. Actual source libraries
compiled with84 focused tests green. Added Read/Close regression follows in its
own narrow test rerun and final Gradle/script acceptance.

PREFLIGHT tx-20261115-234848:693 logical mutations, no apply. Exact proposal in
MAP-UI-PREFLIGHT.tsv:16models66715..66730,2sprites8955/8956,9interfaces1201..1209.
The8954 CoX sprite was respected. This overlaps UNAPPLIED reward/text proposals;
apply reward/text first, rerun maps against the same interfaces.tsv, then apply.
Both caches/ledger/runtime mappings unchanged: maps-preflight/read-only-proof.json.
Fullcard remains open (Uri/emotes/guards/puzzles/hotcold/Mimic-Q1/client checks).

16-Nov00:54: Read/Close test rerun passed3/3 on freshly compiled production classes. Initial verification failure included static extension calls from the prior successful opens; used a separate missing-import account to isolate that assertion. No production change for this test correction. Unique standalone cohort now85 tests. Final Gradle/script integration starts on frozen inputs next.


## Resumed Uri and puzzle atlas promotion, B16-Nov16:44

Previous integration235508 has no summary or final script/test output and is not
accepted. No active own build was found. Verified and promoted all nine staged
adapters; normalized-byte equality in NEXT-INTEGRATION/PROMOTED-SHA256.json.
Only the perform call was merged into the actual manager.

Actual-source standalone `python tools/clue/check_compile.py HEAD basic-tests`:
2 core +48 plugin libraries compiled,94 tests green (the prior85 +9 Uri).
`python tools/clue/verify_interface_port.py`:10 focused codec tests green;
actual native Component decoder: wide/legacy/absent/cameras/alignment/cache keys,
675 map components and199 puzzle components all green. Canonical client untouched.
Production fixture inputs now include both new source files and puzzle pictures.

`python tools/clue/preflight_puzzles.py`: tx-20261116-154016,371 mutations,
applied=false, both caches/ledger/runtime mappings unchanged. Exact current
proposal PUZZLE-UI-PREFLIGHT.tsv: interface306->1203,170 models66725..66894,
sprites539/540->8955/8956. These are not reservations: rerun after reward/maps.
New narrow Gradle/scripts acceptance is still required. Twelve Uri steps can
register only with verified native actors/equipment. No hard/master agent bypass.
Puzzle atlas import alone does not activate puzzle gameplay. Runtime puzzle
handover work is separate, not included in the94-test acceptance above.
