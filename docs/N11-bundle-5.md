# N11 bundle 5 — GE food, drink and bones

Branch: `gpt-b/n11b5`. Scope is the G-register in `C:/RSPS/team/dossiers/N11.txt`, plus the three remaining sip states of each G keg. No cache, client, save, service, live or test-server changes.

Implemented: 48 G ids: 20 full kegs, nine teas, poison chalice, twelve gnome meals, roasted chompy, gout tuber, four bones. The kegs also require 60 partially drunk ids. **1883 remains unresolved**, as explained below; this bundle is not a claim that every requested item has exact retail behaviour.

## Routes and files

- `data/cfg/consumables.tsv`: 90 added drink rows, containing x10 healing and the next serving/empty container. No additional direct item binding.
- `items/food/ConsumableTable.kt`: existing TSV parser and row type extracted from the script so tests use the production parser.
- `items/food/consumables.plugin.kts`: unchanged late `hasItemOption` ownership rule; explicit noted refusal; dispatch added drink effects by id. Existing ordinary drinks retain their existing effects.
- `items/food/GeDrinks.kt`: explicit keg family identity, OSRS percentage drains from current levels, capped boosts, Construction tea, nettle energy and poison-chalice distribution. No extra drunkenness timer: alcohol is represented by its stacking skill drains.
- `items/food/Food.kt`, `Foods.kt`: fourteen G-food entries. Separate attack/eat delay fields preserve the OSRS gnome combo rule (2 attack, 3 eat ticks). All other food retains its old attack delay. Gout tuber restores 50 energy, capped at 100.
- `skills/prayer/burying/BoneData.kt`: four G aliases, using the ordinary bury route/animation/sound/XP.
- Tests: `n11/N11DrinkTests.kt`, `N11FoodTests.kt`, `N11BuryTests.kt`, fixture and census menus; existing `FoodAttackDelayTests.kt` expects the separate attack delay.

## Drink specification and sources

`C` below means that skill's current level; `B` its base level. Percentage terms round down. Boosts cap at base plus boost and do not lower a stronger existing boost. Drains stack and floor at one. The shared drink clock is two ticks and does not extend the attack clock.

Source retrieved directly: [Brewing](https://oldschool.runescape.wiki/w/Brewing), including its complete regular/mature formula table; [Mature ale](https://oldschool.runescape.wiki/w/Mature_ale). Individual URLs are provided below. Many individual page requests returned robots/403/cache-miss; the retrieved brewing table supplies their formulas. No claim of having fetched each blocked page.

Each keg has four sips. Ids in the table are the G full forms. Lower serving ids decrement by two, finally yielding empty calquat keg **5769**, never a vial/beer glass. Example: `5777 -> 5775 -> 5773 -> 5771 -> 5769`. Even ids in these ranges are notes and have no binding. Keg/glass equivalence is explicit on [Dwarven stout (keg)](https://oldschool.runescape.wiki/w/Dwarven_stout_(keg)) and [Mind bomb (keg)](https://oldschool.runescape.wiki/w/Mind_bomb_(keg)); [calquat keg](https://oldschool.runescape.wiki/w/Calquat_keg) identifies the container. Void `data/skill/cooking/brewing.items.toml` confirms the 667 ids and note interleaving.

| G regular / mature | Individual Wiki sources | HP regular / mature | Boost regular / mature | Drain regular / mature |
| --- | --- | --- | --- | --- |
| 5777 / 5857 | [Dwarven stout](https://oldschool.runescape.wiki/w/Dwarven_stout), [mature](https://oldschool.runescape.wiki/w/Dwarven_stout(m)) | 1 / 2 | Mining, Smithing +1 / +2 | Attack, Strength, Defence: 2+4%C / 3+4%C |
| 5785 / 5865 | [Asgarnian ale](https://oldschool.runescape.wiki/w/Asgarnian_ale), [mature](https://oldschool.runescape.wiki/w/Asgarnian_ale(m)) | 1 / 2 | Strength +2 / +3 | Attack: 2+5%C / 3+5%C |
| 5793 / 5873 | [Greenman's ale](https://oldschool.runescape.wiki/w/Greenman%27s_ale), [mature](https://oldschool.runescape.wiki/w/Greenman%27s_ale(m)) | 1 / 2 | Herblore +1 / +2 | Attack, Strength, Defence: 2+4%C / 3+4%C |
| 5801 / 5881 | [Wizard's mind bomb](https://oldschool.runescape.wiki/w/Wizard%27s_mind_bomb), [Mature wmb](https://oldschool.runescape.wiki/w/Mature_wmb) | 1 / 2 | Magic 2+2%B / 3+2%B | Attack, Strength, Defence: 1+5%C / 2+5%C |
| 5809 / 5889 | [Dragon bitter](https://oldschool.runescape.wiki/w/Dragon_bitter), [mature](https://oldschool.runescape.wiki/w/Dragon_bitter(m)) | 1 / 2 | Strength +2 / +3 | Attack: 2+5%C / 2+5%C |
| 5817 / 5897 | [Moonlight mead](https://oldschool.runescape.wiki/w/Moonlight_mead), [mature](https://oldschool.runescape.wiki/w/Moonlight_mead(m)) | 4 / 6 | None | None |
| 5825 / 5905 | [Axeman's folly](https://oldschool.runescape.wiki/w/Axeman%27s_folly), [mature](https://oldschool.runescape.wiki/w/Axeman%27s_folly(m)) | 1 / 2 | Woodcutting +1 / +2 | Attack, Strength: 2+2%C / 3+2%C |
| 5833 / 5913 | [Chef's delight](https://oldschool.runescape.wiki/w/Chef%27s_delight), [mature](https://oldschool.runescape.wiki/w/Chef%27s_delight(m)) | 1 / 2 | Cooking 1+5%B / 2+5%B | Attack, Strength: 2+5%C / 3+5%C |
| 5841 / 5921 | [Slayer's respite](https://oldschool.runescape.wiki/w/Slayer%27s_respite), [mature](https://oldschool.runescape.wiki/w/Slayer%27s_respite(m)) | 1 / 2 | Slayer +2 / +4 | Attack, Strength: 2+2%C / 3+2%C |
| 5849 / 5929 | [Cider](https://oldschool.runescape.wiki/w/Cider), [Mature cider](https://oldschool.runescape.wiki/w/Mature_cider) | 1 / 2 | Farming +1 / +2 | Attack, Strength: 2+2%C / 3+2%C |

Source conflict: the Ale overview includes older fixed drains/heals that differ from Brewing and Stat drain. The implementation follows the explicit percentage table from Brewing and [Stat drain](https://oldschool.runescape.wiki/w/Stat_drain), and the item pages retrieved for stout/mind bomb. Stat drain also lists anomalous dragon-bitter keg entries; the explicit keg-page statement that effects match the glass is used. This is a documented source interpretation, not an owner override.

| G ids | Source | Effect | Empty form |
| --- | --- | --- | --- |
| 4243 | [Cup of tea (nettle)](https://oldschool.runescape.wiki/w/Cup_of_tea_(nettle)), [milky](https://oldschool.runescape.wiki/w/Cup_of_tea_(milky_nettle)) | 3 HP; 5 energy only when injured before the sip; no Attack boost | 1980 |
| 4245, 4246 | [Cup of tea (Ghosts Ahoy)](https://oldschool.runescape.wiki/w/Cup_of_tea_(Ghosts_Ahoy)) | Same nettle-tea effect; see inference below | 4244 |
| 7730, 7731 | [Cup of tea (clay)](https://oldschool.runescape.wiki/w/Cup_of_tea_(clay)) | Construction +1; no healing/Attack | 7728 |
| 7733, 7734 | [Cup of tea (porcelain)](https://oldschool.runescape.wiki/w/Cup_of_tea_(porcelain)) | Construction +2; no healing/Attack | 7732 |
| 7736, 7737 | [Cup of tea (trimmed)](https://oldschool.runescape.wiki/w/Cup_of_tea_(trimmed)) | Construction +3; no healing/Attack | 7735 |
| 197 | [Poison chalice](https://oldschool.runescape.wiki/w/Poison_chalice) | Random outcome below | Cocktail glass 2026 |

Retrieval: Construction tea pages through OSRS Index ([clay](https://osrsindex.com/wiki/cup-of-tea-clay?site=osrs_wiki), [porcelain](https://osrsindex.com/wiki/cup-of-tea-porcelain?site=osrs_wiki), [trimmed](https://osrsindex.com/wiki/cup-of-tea-trimmed?site=osrs_wiki)); nettle text through [Wiki revision mirror](https://danielpgleason.com/osrs/reference/cup-of-tea-nettle-461c527f/) and the directly retrieved [Cooking](https://oldschool.runescape.wiki/w/Cooking) tea table. Chalice through the [OSRS Index copy](https://osrsindex.com/wiki/poison-chalice?site=osrs_wiki), which reproduces Wiki's Mod Ash reference.

**Inference for 4245/4246:** the quest changes the drinking vessel, not the nettle tea ingredients. 2009scape `Consumables.java:300-302` assigns identical healing/energy to all three; OSRS's current 5 energy and injured-only condition replace the donor's older 10. Void identifies cup leftovers, but its 4243/4246 zero-heal metadata conflicts with OSRS's explicit milky-tea equivalence. This interpretation needs Claude's review; an exact separate OSRS quest-cup effect transcript was not retrieved. No Ghosts Ahoy quest state has been added.

Poison chalice, uniform 0..31 roll: 9 outcomes heal 15% base HP and Thieving +1; 8 outcomes melee -1 and Crafting +1; 7 outcomes no effect; 4 heal 5%; 2 damage 5%; 1 damages 50% and drains each melee skill 1–3; 1 heals 30% and boosts melee +4. Damage floors HP at four, recomputed when queued damage resolves. No poison status is applied. Tests enumerate all 32 rolls. Its outcome messages follow the retrieved Wiki table.

Messages for other new drinks use the existing shared `You drink the <name>.` template. Exact per-drink retail text/forced speech was **not verified**, so these are not claimed exact Wiki transcripts. Existing animation 829 and drink sound remain the shared route; Claude checks avtrace. Glass-form beers already handled before this card are unchanged.

## Food and bones — source per G item

All food rows use x10 ledger units internally; the following healing numbers are real HP. No bowls/tins remain from prepared gnome dishes. All twelve G gnome meals combo-eat, add two attack ticks during combat and have a three-tick eat cooldown. [Fast foods](https://oldschool.runescape.wiki/w/Fast_foods) explicitly documents both the combo capability and independent eat/attack timers. The current [Cooking](https://oldschool.runescape.wiki/w/Cooking) table confirms the complete healing roster when individual requests were blocked.

| G id | Wiki source | HP |
| --- | --- | --- |
| 9527 | [Fruit batta](https://oldschool.runescape.wiki/w/Fruit_batta) | 11 |
| 9529 | [Toad batta](https://oldschool.runescape.wiki/w/Toad_batta) | 11 |
| 9531 | [Worm batta](https://oldschool.runescape.wiki/w/Worm_batta) | 11 |
| 9533 | [Vegetable batta](https://oldschool.runescape.wiki/w/Vegetable_batta) | 11 |
| 9535 | [Cheese+tom batta](https://oldschool.runescape.wiki/w/Cheese%2Btom_batta) | 11 |
| 9538 | [Toad crunchies](https://oldschool.runescape.wiki/w/Toad_crunchies) | 8 |
| 9540 | [Spicy crunchies](https://oldschool.runescape.wiki/w/Spicy_crunchies) | 7 |
| 9542 | [Worm crunchies](https://oldschool.runescape.wiki/w/Worm_crunchies) | 8 |
| 9544 | [Chocchip crunchies](https://oldschool.runescape.wiki/w/Chocchip_crunchies) | 7 |
| 9547 | [Worm hole](https://oldschool.runescape.wiki/w/Worm_hole) | 12 |
| 9549 | [Veg ball](https://oldschool.runescape.wiki/w/Veg_ball) | 12 |
| 9553 | [Chocolate bomb](https://oldschool.runescape.wiki/w/Chocolate_bomb) | 15 |
| 7228 | [Cooked chompy (roasted)](https://oldschool.runescape.wiki/w/Cooked_chompy_(roasted)) | 10 |
| 6311 | [Gout tuber](https://oldschool.runescape.wiki/w/Gout_tuber) | 12, plus 50 run energy |

Gout tuber uses its sourced eating message. Other food uses the existing food-route messages. The 9538 alias gets eight HP (the old ordinary TOAD_CRUNCHIES enum row has a separate pre-existing wrong value; this card changes G ids only).

| G bone | Source | Base Prayer XP |
| --- | --- | --- |
| 2530 | [Bones](https://oldschool.runescape.wiki/w/Bones) | 4.5 |
| 3187 | [Bones](https://oldschool.runescape.wiki/w/Bones) | 4.5 |
| 14693 | [Bones (RuneScape)](https://runescape.wiki/w/Bones) | 4.5 |
| 14793 | [Ourg bones (RuneScape)](https://runescape.wiki/w/Ourg_bones), [OSRS ordinary form](https://oldschool.runescape.wiki/w/Ourg_bones) | 140 |

The card explicitly requires these legacy ids to match their ordinary forms. The ordinary Ourg Wiki page was retrieved and confirms 140 XP; same-form alias mapping is card-authorized, not inferred from id equality with modern OSRS. Existing bury slot/object identity check and two-tick queue prevent double XP. No altar/quest-hand-in changes.

## Unresolved requested item and limits

**1883 Ugthanki kebab:** [ordinary Wiki page](https://oldschool.runescape.wiki/w/Ugthanki_kebab) identifies **1885** as the 19-HP fresh form and links [smelling](https://oldschool.runescape.wiki/w/Ugthanki_kebab_(smelling)) / [bad](https://oldschool.runescape.wiki/w/Ugthanki_kebab_(bad)). Wiki Food/All food says the bad form usually heals zero and can heal nine, without giving an exact chance. Direct bad/smelling pages, raw/API requests and OSRS Index retrieval failed. Void's cooking metadata identifies 1883 as smelling and gives zero; Darkan Foods.java:442 gives it 19. 2009scape `SmellingUgthankiKebabEffect.java` explicitly says its **10% is invented because the chance is unknown**, while its enum also swaps 1883/1885. Therefore none supplies the exact OSRS rule requested. **1883 has not been bound/consumed with guessed behaviour.** Owner/Claude needs an authoritative roll specification or an explicit choice of a donor approximation. No save or acquired item was converted to 1885.

Other limits: exact general drink transcripts remain unverified; special-cup nettle equivalence is an inference. Tests cover handler registration, slot/notes, state, timers, healing and XP; they do not substitute for a full server boot or client audiovisual review. No new external donor implementation was blindly ported.

## Rest of G-register — outside these three selected families

These remain untouched by this bundle, including menu options belonging to other systems. Already addressed elsewhere does not mean this bundle retests them.

- Hold/Release and clockwork toys: 751; 7759, 7761, 7763, 7765, 7767, 7769; butterflies 10016, 10018, 10020; chinchompa 10033; salamanders/lizard 10146–10149.
- Easter Eat: 7928–7933, explicitly excluded by card.
- Other special Eat: purple sweets 4561, super kebab 4608, shrunk ogleroot 11205. Their random effects/minigame handling are not one of the selected gnome/cooked-food families.
- Other Drink: 9508 Wizard blizzard (legacy intermediate/copy identity needs its own specification); Relicym's balm 4842/4846/4848 belongs to bundle 2.
- Open: airtight pot 4436, previously addressed in bundle 1.
- Remove-charge: Broodoo shields 6215/6237/6259, black mask 8901.
- Break/Enchant: tablets 8016–8021.
- Launch/Rub/Inspect/Loot: seed pod 9469, glory(t) 10719, DFS 11284, dragon impling jar 11256.
- Empty: bucket 12651, poultice 23388 (9508 also has Empty).
- Equipment Activate/Recharge: ceremonial top 20127, wyvern shield 22953.
- Cannon Study/Rebuild: 20470, 20473, 20484, 20485.
- Scatter: infernal ashes 22312 (not Bury).
- Dismantle: serpentine visage 23936.

## Claude's client check

Boot with the complete plugin set first: no duplicate item binding, especially the late consumables/Empty routes. Then actually buy/click and record avtrace:

1. Dwarven stout(4) **5777**: four sips to **5769**, +1 Mining/Smithing capped, melee drains stack, 1 HP per sip. Repeat with mature **5857** or mind bomb **5881**, verify mature boost and two HP.
2. Tea **7736**: +3 Construction, own trimmed cup **7735**, no Attack boost. Also inspect **4243** injured/full HP (energy only while injured) and **4245/4246** inference if available.
3. Toad batta **9529** and chocolate bomb **9553**: 11/15 HP, disappearance, two ticks added to an existing attack delay, shark → gnome combo works, gnome → shark/another gnome immediately refuses.
4. Bones **2530** or **3187**: ordinary 4.5 base XP, reach-down animation/sound; Ourg alias **14793** gives 140 base XP.
5. Noted copy/stale slot, double click, full inventory (leftover reuses consumed slot), last sip. Chalice **197** leaves **2026** and damage cannot drop HP below four.

1883 is deliberately still open; do not report its click as fixed. No client/server was started by ChatGPT-B under this card.

## Verification

Final run: **41 tests, zero failures/errors**. Core, plugins and scripts `compileKotlin` pass. `ConsumableTablesTests` validates the actual cache's options, note status and leftover ids using the read-only reference cache; nothing was copied or changed. Changed-file lint: six production files, zero findings. Staged diff check passes.

```powershell
$env:RSPS_TEST_CACHE = 'C:\RSPS\reference\openrs2_667\cache'
.\gradlew.bat :game:plugins:compileKotlin :game:plugins:test --tests 'gg.rsmod.plugins.content.n11.*' --tests '*ConsumableTablesTests*' --tests '*FoodAttackDelayTests*' --tests '*FoodAndDrinkLifepointUnitTests*' --priority low
```

The first run passed 37 tests. Adding the full formula matrix and quest-cup tests exposed two missing census menu rows in the fixture; after adding the real census menus, the final 41-test run is green. No production error was suppressed. `Doctor.ps1` hard-codes the primary checkout and service checks, so it was not run under this card. Changed-file lint was evaluated read-only with its root redirected to this worktree.
