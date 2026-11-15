# A continuation: 15 November 12:05

Base `639a0105f`, branch `gpt-a/clue-scrolls-6`. Latest dossier handoff reassigns the complete CLUE card to A. No cache apply, client/server operation, push or live acceptance.

## Q5 applied

Dossier 11:1x explicitly rejects restoring the POH Study/chart and selling or dropping its blueprint. Watson now retains only the pinned noticeboard explanation. The purchase backend and its obsolete test are removed. Noticeboard Read and chart functionality are retained.

## Fresh Mimic combat definition check

`python tools/clue/check_compile.py HEAD mimic-tests`: fresh compile of MimicCombat.kt and TheMimicCombatDefTests.kt, JUnit 4.13.2, **1 test, zero failures**, 0.117 seconds test time. This checks the actual definition: 230 HP, stats/bonus indices, 3-tick attack, animations, immunities and XP multiplier 1.25. Fresh standalone output lives at `build/clue/compile/mimic-tests/{plugins,tests}`. This standalone runner does not create XML; a later frozen Gradle result must provide the requested XML path. This is not encounter or client acceptance.

B's `integration-20261115-101935` has no summary.json and stops in compilation; it is not accepted. The other B 111-test result remains historical evidence, not acceptance of later script changes.
