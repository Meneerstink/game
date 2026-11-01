# N521 equipment conformance

Input: `C:\RSPS\bugs\conformance\equipment-20261101-125448\report.txt`,
125 items completed, 84 FAILs. The run stopped during Maple shortbow, so later items are not covered by that report.

## Findings and changes

| FAIL class | Count | Cause and fix |
|---|---:|---|
| Equip retired tools / Picture | 21 | The roster used `equipSlot` as proof of wearability. Pinned OSRS broken tools retain a weapon position but have no Wield option. Exclude all 15 unnoted handles/broken tools (466â€“478 and 492â€“506, step 2). Exclude the entire model-less 667 Picture placeholder class; their inherited Wear option does not establish an equipment identity. Log each exclusion in the report notes. |
| Robe top slot | 3 | 540/544/546 have the same 667 display name as their bottoms. Resolve the explicit top pages, keep slots and 667 models. The corrected identity exposes real prayer errors: 544 changes 5â†’6, 546 changes 4â†’5. Audit identity matching now uses these proven same-id definitions. |
| Blue hat magic attack/defence | 2 | 740 is the Legends' Quest hat (+2), not ordinary Blue hat 660 (+3). Resolve its exact page, retain its existing stats. Add the same identity to the stats audit. |
| Javelin slot/ranged attack/strength | 18 | Owner decision (g), 2026-09-14, explicitly retains 667 thrown javelins 825â€“830. Copy the wiki row and replace those three expectations with the frozen 667 values; label these checks `owner (g): 667 thrown javelins`. No ballista ammo or item data change. |
| Holy water / thrownaxe reach | 7 | Real combat range errors: Holy water 732 and every metal thrownaxe 800â€“805 fell back to range 7. Set range 4, confirmed by OSRS build-240 param 13 and wiki. |
| Bow/crossbow styles and reach | 33 | 25 swing failures and 8 reach failures. The suite handed runite bolts to bronze-only crossbows and rune arrows to low-tier bows. Select legal ammo from the registered BowType/CrossbowType arrays, preserving dedicated loadouts (Karil, ballista, etc.). Two additional real combat errors: Phoenix crossbow 767 needs range 5 and plain Crossbow 837 needs range 10; both previously defaulted to 7. |

All 84 FAIL rows were read and classified, including Broken hatchet 506 and the partial Maple shortbow row.
No existing UNSOURCED issue was resolved: 130 rows comprise 40 missing references, 20 equipment slots,
57 attack speeds (19 accurate, 18 rapid, 18 longrange, 1 aggressive, 1 defensive), and 13 two-handed checks.

## Sources and additional class checks

- Read-only `C:\RSPS\tools\cache667\items.py` and `osrs_items.py`, using the existing 667 cache and pinned
  `C:\RSPS\import-source\openrs2-2686\cache` (build 240). No cache tools were changed or applied.
- Existing wiki Bucket snapshot: `C:\RSPS\reference\weapons\osrs_weapons.json`.
  Exact top rows exist with OSRS ids 540/544/546; the quest hat row identifies 740.
- Existing `OsrsEquipmentStatsAuditTests`, `SAME_ITEM_BY_ID`, `ALL_FIELDS_EXCLUDED`, and owner stats decisions in
  `C:\RSPS\OSRS_IMPORT_MASTER.yml`, especially the 2026-09-14 (d)/(f)/(g) table near line 8425.
  Dossiers were searched for prior stats decisions; none overrides these identities.
- [Phoenix crossbow](https://oldschool.runescape.wiki/w/Phoenix_crossbow): bronze bolts, range 5/7.
  [Crossbow](https://oldschool.runescape.wiki/w/Crossbow): bronze bolts, range 10.
  [Holy water](https://oldschool.runescape.wiki/w/Holy_water): range 4/6.
  Cache param 13 independently confirms all nine corrected ranges.
- Retired class additional checks: Broken pickaxe 478 and Broken hatchet 506 have no OSRS Wield option,
  despite wearPos 3; Picture 547/549 also lack 667 worn models. Bronze pickaxe/hatchet remain eligible.
- Robe class additional checks: bottoms 538/542/548 stay in slot 7 with prayer 4/5/4;
  the 667 druid/shade tops share worn models 170/348, bottoms share 265/428. Monk 544 uses
  worn models 16253/33959 versus bottom 542's 16252/33958. OSRS slots agree; retain 667 appearance.
- Hat class additional checks: regular Blue hat 660 stays +3, Blue wizard hat 579 stays +2.
  Quest 740 has the same icon/worn model family but a separate +2 definition in both sources.
- Javelin class additional checks: Iron 826 remains attack/strength 8/10, Rune 830 remains 38/42;
  the tests cover all six. Poisoned 831â€“836 are separate names with no corresponding modern ballista row;
  they keep their existing data/reference status.
- Range class additional checks: Iron thrownaxe 801 and Rune thrownaxe 805 both have source range 4.
- Ammo class additional checks: Oak shortbow 843 and Maple longbow 851 accept bronze arrows;
  every registered bow/crossbow is covered by a compatibility test.
- No donor implementation was ported; existing server ammo registrations and pinned cache definitions supply the evidence.

## Verification and Claude handoff

One heavy Gradle invocation (9m15s):

```text
gradlew.bat :game:test --tests '*OsrsEquipmentStats*' :game:plugins:test --tests '*Equip*' --tests '*RangedAttackRangeTests*' --priority low
```

Results: core `OsrsEquipmentStatsAuditTests` 6/6 passed. Plugin selection ran 45 tests:
`EquipActionTests` 7, `EquipmentReferencesTests` 5, `RangedAttackRangeTests` 1,
`EquipmentStatsTextLayoutTests` 1, `EquipmentDegradationTests` 5, `CrystalEquipmentTests` 8,
`OsrsEquipment2ImportTests` 4, `EquipSoundsTests` 4, `SkillcapeEquipTests` 10.
The initial new ammo test incorrectly required external ammo for `CRYSTAL_BOW_110` (empty ammo array);
corrected the test to expect null for self-ammo bows. Every other selected test passed in that run.
Only the corrected class was repeated:

```text
gradlew.bat :game:plugins:test --tests '*EquipmentReferencesTests*' --priority low
```

That incremental recovery passed 5/5 in 31s (all production compilation tasks UP-TO-DATE).
All 51 distinct selected tests are green across these runs. No full suite/package tests were run.

New tests cover retired-source wearability, all six robe identities and stats, quest/ordinary hats,
all six frozen javelin expectations (including source non-mutation), and every registered ammo loadout.
The existing ranged-range regression covers all nine corrected weapons.
`git diff --check` and the shared lint script redirected in memory to this worktree are also checked.
Doctor/package runs and the actual equipment conformance suite belong to Claude's server verification.

Claude: rerun package checks and conformance equipment after rebuilding. Check robe tops/bottoms visually and their
prayer totals; quest hat +2 versus regular hat +3; no scenarios for retired tools/model-less Picture;
six thrown javelins still wield in the weapon slot with owner-labelled checks; low bows/crossbows actually fire;
Holy water/metal thrownaxes stop at 4 tiles, Phoenix at 5, plain Crossbow at 10 (longrange adds 2, capped at 10).
Check one tile beyond each range with both actors frozen. No saves, clients, caches, live or death/store code changed.
No owner choice is needed for these fixes. Runtime conformance results remain pending Claude's rerun.
