# A review checkpoint: 15 November 07:38

Inherited runtime snapshot: `05286a72c` (B WIP). Source-data fixes `2ae33920e` and `2d0f7b3e2` do not activate handlers.

The Gradle run completed successfully in 31m04s: **68 tests in 19 selected classes**, zero failures/errors. The earlier dossier total 81 was an arithmetic error, corrected in the next row. Core/plugin libraries and scripts compiled from the inherited snapshot. Later StepCatalogue / 101-slide changes are outside this baseline.

Invocation:

```powershell
.\gradlew.bat -I tools/clue/test-init.gradle :game:plugins:test --tests "*Clue*" --tests "*TreasureTrail*" --tests "*Casket*" --tests "*Mimic*" --tests "*WatsonCluesTests*" --priority low
```

| Test class | Tests | Failures/errors |
|---|---:|---:|
| gg.rsmod.plugins.content.areas.watson.MimicChallengeTests | 7 | 0/0 |
| gg.rsmod.plugins.content.areas.watson.MimicRewardsTests | 3 | 0/0 |
| gg.rsmod.plugins.content.areas.watson.OsrsMimicImportTests | 2 | 0/0 |
| gg.rsmod.plugins.content.areas.watson.StrangeCasketTests | 1 | 0/0 |
| gg.rsmod.plugins.content.areas.watson.TheMimicCombatDefTests | 1 | 0/0 |
| gg.rsmod.plugins.content.areas.watson.WatsonClueTests | 7 | 0/0 |
| gg.rsmod.plugins.content.items.osrs.OsrsCasketOrnamentsImportTests | 2 | 0/0 |
| gg.rsmod.plugins.content.items.osrs.OsrsCasketTeleportScrollsImportTests | 2 | 0/0 |
| gg.rsmod.plugins.content.items.osrs.OsrsCasketWeaponsImportTests | 3 | 0/0 |
| gg.rsmod.plugins.content.items.OsrsEliteCasketTests | 1 | 0/0 |
| gg.rsmod.plugins.content.scrolls.ClueCasketTransactionTests | 13 | 0/0 |
| gg.rsmod.plugins.content.scrolls.ClueDropIndexTests | 1 | 0/0 |
| gg.rsmod.plugins.content.scrolls.ClueDropOwnershipTests | 4 | 0/0 |
| gg.rsmod.plugins.content.scrolls.ClueInterfacesTests | 3 | 0/0 |
| gg.rsmod.plugins.content.scrolls.ClueLogCatalogueTests | 1 | 0/0 |
| gg.rsmod.plugins.content.scrolls.ClueNpcSourcesTests | 4 | 0/0 |
| gg.rsmod.plugins.content.scrolls.ClueRewardCatalogueTests | 7 | 0/0 |
| gg.rsmod.plugins.content.scrolls.ClueScrollLifecycleTests | 3 | 0/0 |
| gg.rsmod.plugins.content.scrolls.TreasureTrailChallengeTests | 3 | 0/0 |


## Mandatory inherited review points

* ClueNpcSources: unchanged ordinary slots/rolls without source; bulk registration still happens; no duplicate legacy/source clue; full versus empty account viewer chances. All four regressions passed.
* Per-account viewer item indexes passed (ClueDropIndexTests).
* Thirteen casket transaction regressions passed: stale identity, full inventory/overflow, missing definitions, counters, Entrana and cases.
* Mimic conversion/IRS uses existing addPreservingAttr. General ItemContainer.add was not changed by A.
* content/items/casket.plugin.kts binds only fishing casket 405, with 128 weights summing to 128. The old clue total500/502 route is gone; five clue caskets use the source catalogue.
* ::dvaudit, N614 runtime warning comparison, renderer/style and heard sound remain Claude-only server/client checks.

## Still required

No release acceptance. Item/UI/arena cache imports are not applied and source combat timings remain gated. The donor 41-all-tiers registration remains to be replaced with the limited source pool. Step adapters, STASH, guards, puzzles, Falo/Sherlock/hot-cold and a source-backed master pool remain required. PLAN.md and the latest dossier rows retain these gates.

## 15 November 08:50 - source and integration checkpoint

`db9f88a6f` pins fourteen guardian/minion stat variants, including XP bonuses and signed defence values. Source probe: 23 NPCs, 21 selected scenes, read-only. No combat activation.

Torn parts: the pinned Wiki explicitly says that combining parts after swapping to a master clue requiring only one action discards the parts. The source wording is in `sources/Torn_clue_scroll.wiki`, paragraph beginning `In ... clue juggling`. The tested atomic backend follows this rule; it does not silently grant the unrelated step.

Gradle session 88209 is NOT acceptance. A added the NPC-override regression while the core compilation was already running; test compilation then saw the newer test against the older core API and failed. The run's core/plugin snapshot is quarantined. A stable-source rerun is required, including the new `ClueNpcOptionOverrideTests`, `TheMimicCombatDefTests`, and the previous selected UI/step classes. Do not count partial reports from that failed run as green.

Primary transcript research added `Transcript_Abbot_Langley.wiki` revision 15324395 and `Transcript_Robin.wiki` revision 15173500, with hashes in the manifest. Robin's transcript heading says an equipped blowpipe fails, while the cryptic guide says only its bonus does not count. The current inactive backend follows the guide; this discrepancy requires OSRS verification before activation. Abbot's transcript provides the actual master part/full-inventory lines despite its general incomplete banner.
