# N410 bundel 1 - ChatGPT-B

Scope: 24 skillcapes (plain/trim/hood), all wearable max capes/hoods including locked forms and the underlying item used by MaxCapeLooks. No Dungeoneering. No saves, cache bytes, client, live, death or store changes.

## Changes

- `data/cfg/items.yml`: 72 skill items require their own base level 99; 28 wearable max items require all skills 0..23 at 99. This includes the additional wearable Max hood 20751. Max cape 20747 has no equipment metadata and remains unwearable.
- Existing `ItemMetadataService` and `EquipAction` enforce requirements only when equipping. Temporary drains never remove equipment. Current boosts cannot bypass a base level requirement.
- `EquipAction` now has a Summoning skill name, preventing an array index failure on a refused Summoning cape/hood or max item. Constitution uses the local 667 name.
- `SkillcapeBoost`: only 99 -> 100; no drain restoration. Runtime-only activation ownership permits removal of the cape's own +1 on unequip or replacement. Drains, independent boosts already present at equip, and stronger boosts survive removal. Constitution/Prayer retain their 100-tick cooldown through removal.
- Hoods never activate/remove boosts. Plain max keeps all perks; combined/locked max variants keep the existing VariantPerks. MaxCapeLooks only changes appearance, so the plain item's requirements and perks still apply.

## Construction: cache handoff to Claude (required before this menu is complete)

OSRS Construction has a worn Boost operation. The current 667 items 9789 and 9790 have only Teleport and Tele to POH. A server-only menu-array edit does not update the client's cached options, so it cannot make the missing operation visible.

Claude: using CacheTransaction on both real cache targets, change only item index 19, group 38, files 61 (9789) and 62 (9790). Preserve the inventory options, models, colours, stats and all other fields. Preserve the current worn strings at params 528 and 529 (Teleport and Tele to POH); add string param 530 = `Boost` (third worn operation, reachable by the 667 equipment script). Do not move existing operations. Check the existing params before applying; if 530 is occupied, reconcile explicitly rather than overwrite it. Restart/reload caches and the testclient through Claude's usual workflow. `skillcape_boost.plugin.kts` already binds Boost by name whenever it exists and binds equip/removal on both cape ids. No additional server code is needed after the cache patch.

This agent did not run a cache tool or change either cache. The menu is a remaining integration step, not a tested runtime success.

## Sources and limits

- https://oldschool.runescape.wiki/w/Cape_of_Accomplishment#Skill_boost : equip/Boost, 99/100, ordinary decay and one-minute Constitution/Prayer cooldown; also notes the base-level wearing requirement after experience loss.
- https://oldschool.runescape.wiki/w/Magic_cape : explicitly 99 -> 100, cannot restore drain, removal clears boost.
- https://oldschool.runescape.wiki/w/Farming_cape : removal clears boost.
- https://oldschool.runescape.wiki/w/Temporary_skill_boost#Construction : Construction cape has an activated +1 effect. The Construction item page was blocked by robots in this run; generic Cape of Accomplishment Boost rules apply to it. Search index also exposed a mirror of the item infobox listing worn Boost/Teleport/Tele to POH; this mirror is supporting evidence, not a donor implementation.

No donor code copied. Preserve existing normal restoration timing. Runtime activation attribution records the cape's own use, not every later potion/drain source; a later independent effect that lands on exactly 100 after a cape activation cannot be distinguished by the current skill system. Stronger values are explicitly preserved. Existing invalid worn capes are not scanned or removed, and player saves are never edited.

## Verification and Claude checks

Targeted command: `gradlew.bat :game:plugins:test --tests '*Skillcape*' --tests '*MaxCape*' --tests '*Equip*' --priority low`.

Result 2026-11-01: **PASS, 53 tests in 11 classes, 0 failures/errors/skips**. SkillcapeEquipTests: 10; SkillcapeBoostTests: 4. One heavy compilation (14m36; Kotlin daemon disconnected and Gradle completed source/script compilation through its fallback). The initial test compile exposed an inaccessible internal Player.writeMessage assertion in the new test; corrected that test, then repeated the same filters. Final run 3m08, 17 tasks up-to-date, only test compilation and tests executed. Changed-source RSPS lint: 3 files, 0 findings. `git diff --check`: PASS.

New `SkillcapeEquipTests` loads real YAML through ItemMetadataService and runs EquipAction for every id, including each missing max skill individually, boosted 98 rejection, drained 99 acceptance, hood order, swap/removal and perk preservation. `SkillcapeBoostTests` adds drain, hood mapping and Constitution/Prayer point-storage/cooldown regressions.

Claude should run the touched packages and inspect in the testclient: plain/trim/hood rejected with base 98 even if current 99/100; accepted with base 99; drain to 97 while worn retains item and Boost does nothing; 99 boosts to 100; removing/replacing cape clears that boost; removing only hood leaves it intact. Check Constitution/Prayer displays and cooldown after remove/re-equip. Check each max variant/locked form/hood with one base skill 98, including Summoning, and all 99 with Dungeoneering 1. Check a selected MaxCapeLooks appearance, guild access/pouch perks and plain max perks. After Claude's cache patch, Construction worn Boost must be visible and work without disturbing either teleport option.

No testserver or testclient was started. Doctor is not used for this task because it hardcodes the separate main worktree and operations environment; changed-source lint is run with its root redirected in memory to this worktree.
