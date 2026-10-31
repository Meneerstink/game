# N11 bundle 6 - remaining item options

Branch: `gpt-b/n11b6`. Only the supplied V/G register, after bundles 1-5 and Claude's blocks. **19 G ids connected to existing routes**; 9508 has only Empty connected. G still means a server-side GE candidate, not client-proven acquisition. Claude must establish acquisition and perform real clicks. No cache/client/save/service changes, push or merge.

## Built, source per item

| Id | Option and existing shared route | Item source |
| --- | --- | --- |
| 11284 | Inspect -> DragonfireShield family + existing item dialogue. Both native ids retain the existing id-stable charge lifecycle and zero default; ward/wyvern defaults remain 50. | https://oldschool.runescape.wiki/w/Dragonfire_shield |
| 11256 | Loot -> ImplingData + Impling.openJar, shared with every existing jar. | https://oldschool.runescape.wiki/w/Dragon_impling_jar and https://oldschool.runescape.wiki/w/Dragon_impling |
| 6215 | Remove-charge -> ItemCharges, 6235, through ItemActionGuard confirmation. | https://oldschool.runescape.wiki/w/Broodoo_shield_(poison) |
| 6237 | Same, 6257. | https://oldschool.runescape.wiki/w/Broodoo_shield_(disease) |
| 6259 | Same, 6279. | https://oldschool.runescape.wiki/w/Broodoo_shield_(combat) |
| 8901 | Same, 8921. No imbue refund or recharging. | https://oldschool.runescape.wiki/w/Black_mask |
| 22312 | Scatter -> AshData/scattering, 110 base Prayer XP. Native infernal ashes retain 62.5. | https://oldschool.runescape.wiki/w/Infernal_ashes |
| 10016 | Release -> HunterRelease, empty jar10012, heal15. | https://oldschool.runescape.wiki/w/Snowy_knight_(item) and https://oldschool.runescape.wiki/w/Snowy_knight |
| 10018 | Release -> same, Defence +4+floor(15% base). | https://oldschool.runescape.wiki/w/Sapphire_glacialis_(item) |
| 10020 | Release -> same, Attack +4+floor(15% base). | https://oldschool.runescape.wiki/w/Ruby_harvest_(item) |
| 10033 | Release -> existing chinchompa single/stack confirmation, whole selected stack. | https://oldschool.runescape.wiki/w/Chinchompa and https://oldschool.runescape.wiki/w/Talk:Red_chinchompa |
| 10146 | Release -> HunterRelease, remove selected live creature. | https://oldschool.runescape.wiki/w/Orange_salamander |
| 10147 | Same. | https://oldschool.runescape.wiki/w/Red_salamander |
| 10148 | Same. | https://oldschool.runescape.wiki/w/Black_salamander |
| 10149 | Same. | https://oldschool.runescape.wiki/w/Swamp_lizard |
| 23936 | Dismantle -> existing serpentine helm dismantling route, 20,000 scales, shared confirmation. No level or XP for dismantling. | https://oldschool.runescape.wiki/w/Serpentine_visage |
| 12651 | Empty -> existing late empty-container TSV route, bucket1925. This is the2008 Easter-event water copy, not an inferred ordinary item by name. | https://runescape.wiki/w/Bucket_of_water_(2008_Easter_event) |
| 10719 | Rub -> existing EMPTY_GLORY refusal; no teleport or consumption. Donor explicitly identifies the zero-charge trimmed copy. | https://oldschool.runescape.wiki/w/Amulet_of_glory_(t) |
| 9508 | Empty only -> existing late empty-container TSV route, cocktail glass2026. | https://runescape.wiki/w/Wizard_blizzard and https://oldschool.runescape.wiki/w/Wizard_blizzard |

Sources actually retrieved: OSRS Wiki dragon impling/jar (complete main table, jar return and tertiary), Black mask, Infernal ashes, Serpentine visage including transcript; indexed Wiki Broodoo combat page/update and Module:GEIDs/data.json (all three charged/empty id pairs); Snowy knight, Attack/Temporary skill boost, Hunter's mix, Salamander, Orange salamander and Red chinchompa talk page. Some individual item pages return robots/cache-miss: their URLs above are canonical references, not a claim that each request succeeded. Defence/Attack formula also confirmed by the retrieved Wiki mix/boost tables and Void Butterfly.kt. DFS uses the explicitly requested existing text and the owner's established item-dialogue route.

Donors read, no wholesale port:
- `C:/RSPS/Donors/void/game/src/main/kotlin/content/skill/hunter/Impling.kt`: two-free-space refusal and jar-break message; always grant drops even when the jar breaks.
- Same directory `Butterfly.kt`, `Salamander.kt`: Release messages, stat/HP effects and visuals; `data/skill/hunter/hunting.gfx.toml`:915/916/917 for snowy/sapphire/ruby; existing5213 jar-open animation. No sourced Release sound found: none invented. These are self-Release actions; no new player-on-player Use/share route.
- `Void/data/activity/event/easter/easter.items.toml`:12651 clones12650; explicit `empty="bucket"`.
- `Void/data/entity/player/unobtainable.items.toml`:10719=`amulet_of_glory_t_0`, not a charged glory.
- `Void/data/minigame/gnome_resturant/gnome_resturant.items.toml`:9508 clones9487; explicit `empty="cocktail_glass"`, but no healing metadata. Drink deliberately unresolved.
- Darkan/Novite searched for Remove-charge handlers: none found. Wiki Black mask+Broodoo pages/GE ids establish exhausted forms. RS2010 update confirms the native mask option spelling: https://runescape.wiki/w/Update:Dungeoneering_%E2%80%93_The_Warped_Floors (reference only; no Dungeoneering restored).

## Loot and shared safeguards

Dragon jar has18 equal ungated main slots: two dragonstone-tip slots; mystic bottom; noted glory/dragonstone amulet; arrows/bolts/longsword/noted p++ daggers/darts/noted dragonstone/dart tips/arrowtips; noted baby/dragon bones; magic/snapdragon seeds; noted summer pies. Quantities follow the fetched table. The19th javelin-head row requires Monkey Madness II. Use the existing QuestStatus through MonkeyMadnessII.isFinished:19 equal rows after completion,18 before completion, as the Wiki's Mod Ash2020-05-20 reference specifies. The imported javelin tips are local22585 mapped to OSRS19582, not the unrelated native19582 item. No quest added or completion assumed. The existing elite clue id19043 is an independent1/50 reward, suppressed when already in inventory or bank.

Shared jar payout now returns an empty jar90% of the time and still awards main loot when the jar breaks10%. The former route discarded all loot on a20% break and never returned an empty jar. Existing tiers retain their common/rare tables; no unrelated reward rebalance or new NPC spawns/capture tier. Jar handlers moved from hunter.plugin.kts into impling_jars.plugin.kts, so there is exactly one shared owner of Loot. Staged insertion is atomic, requires the donor's two free spaces, and preserves unrelated item object identities. A full inventory refuses before consuming or rolling.

Scatter captures the actual slot and item before its two-tick queue; changing the interaction attribute, moving the item or replacing it cannot consume another copy or grant XP. Fast repeats consume once. No new ash sound invented; existing animation445/gfx40 and message retained. Imported22312 is not native20268.

Remove-charge is the native spelling of Uncharge, so the existing global ItemActionGuard now recognises it. Owner confirmation GUI94 and owner-adapted charge_gone sound1650 are reused. The general confirmation recheck now requires the same item object, including worn actions: a new identical item cannot inherit an old Yes. The four handlers only replace their inventory form. No charge attribute carried to exhausted items. Generic confirmation/status wording remains the existing owner convention; it is not presented as an exact retail transcript.

All new item bindings check existing ownership. Empty uses the existing late `hasItemOption` rule. Butterfly releases share a three-tick cooldown without extending attack delay; boosts cap and do not lower stronger boosts. Existing black-warlock/red-chin behaviour is retained, apart from applying the shared butterfly click cooldown to prevent spam. Visage shares the existing rollback on inventory refusal.

## Remaining G decisions - explicit skips/questions

Only the following30 untouched G ids remain from the bundle5 rest list, plus9508 Drink. This table records a question instead of creating a lifecycle or guessing a same-name item's behaviour. Already-done4436 and balm4842/4846/4848 remain unchanged.1883 is explicitly outside this card and remains the bundle5 source question.

| Ids | Wiki reference per item/family | Why skipped / question for Claude/owner |
| --- | --- | --- |
| 751 | https://oldschool.runescape.wiki/w/Gnomeball | Hold belongs to gnomeball possession/passing; no existing local ball-state route. Separate complete gnomeball task? |
| 7759,7761 | https://oldschool.runescape.wiki/w/Toy_soldier | Wind/wound Release needs mobile toy, ownership and return lifecycle, absent locally. Authorize that system separately? |
| 7763,7765 | https://oldschool.runescape.wiki/w/Toy_doll | Same question for doll. |
| 7767,7769 | https://oldschool.runescape.wiki/w/Toy_mouse | Same question for mouse and its release/catch lifecycle. |
| 7928,7929,7930,7931,7932,7933 | https://runescape.wiki/w/Easter_egg_(2006_Easter_event) | Void identifies the six2006 event eggs, not the ordinary1961 food. Exact per-colour Eat effect/transcript not recovered; no event/transformation route exists locally. Supply exact effects or authorize the event lifecycle separately? |
| 4561 | https://oldschool.runescape.wiki/w/Unobtainable_items and https://runescape.wiki/w/Purple_sweets_(2005_Halloween_event) | Retrieved Wiki explicitly lists4561 as2005 Halloween candy. It is not stackable treasure-trail10476. Exact event-candy heal/effect required; do not copy10476's1-3HP/10energy by name. |
| 4608 | https://oldschool.runescape.wiki/w/Super_kebab | Existing normal-kebab random model is not proof of the super-kebab probabilities. Need the exact super-kebab distribution/transcript. |
| 11205 | https://oldschool.runescape.wiki/w/Shrunk_ogleroot | Eat requires shrinking/growing in Grim Tales; no shared local size/quest-state lifecycle. Separate quest task? |
| 8016 | https://oldschool.runescape.wiki/w/Enchant_sapphire_or_opal | Existing magic_tablets Use-on-jewellery works. Current OSRS removed Break in2018, uses Info;667 still advertises Break. Claude should choose cache-menu correction to modern Info/Use or a sourced legacy target-selection UI, rather than invent an enchant target. |
| 8017 | https://oldschool.runescape.wiki/w/Enchant_emerald_or_jade | Same Break/Use/Info menu question. |
| 8018 | https://oldschool.runescape.wiki/w/Enchant_ruby_or_topaz | Same. |
| 8019 | https://oldschool.runescape.wiki/w/Enchant_diamond | Same. |
| 8020 | https://oldschool.runescape.wiki/w/Enchant_dragonstone | Same. |
| 8021 | https://oldschool.runescape.wiki/w/Enchant_onyx | Same. No cache altered by B. |
| 9469 | https://oldschool.runescape.wiki/w/Grand_seed_pod | Launch requires farmer arrival/ladder/teleport lifecycle. A generic teleport does not implement this. Separate launch lifecycle task? |
| 9508 Drink | https://runescape.wiki/w/Wizard_blizzard | Empty built. Donor identifies an unfinished/strange-mix copy without heal metadata, unlike finished2054. Exact Drink refusal/effect needed; not given finished-cocktail stats by name. |
| 20127 | https://runescape.wiki/w/Ancient_ceremonial_top | Worn Activate belongs to ceremonial equipment/set action. No local shared activation route/source-exact audiovisual specification. Supply full action specification? |
| 20470 | https://runescape.wiki/w/Cannon_stand | Study is part of the newer cannon upgrade/lifecycle; no local lifecycle. Keep with cannon task? |
| 20473 | https://runescape.wiki/w/Cannon_base | Same Study question. |
| 20484 | https://runescape.wiki/w/Cannon_furnace | Rebuild/Study require cannon variant/state lifecycle. Same cannon task? |
| 20485 | https://runescape.wiki/w/Cannon_furnace | Same Study question. |
| 22953 | https://oldschool.runescape.wiki/w/Ancient_wyvern_shield | Existing DragonfireShield explicitly leaves fossil/numulite Recharge as a source gap. No shared recharge action to connect; authorize a complete sourced charging action separately? |
| 23388 | https://oldschool.runescape.wiki/w/Haemostatic_poultice | Exact Empty remainder/amount/transcript not verified. Do not guess a vial from dressing/poultice names; specify the actual container rule. |

**Explicit card question:** cannon base6 Set-up: authorize a separate complete multicannon lifecycle task, or keep parked? No cannon option was bound here.

Acquisition caveat: several G entries are explicitly nontradeable in donor data (event copies,10719). The original G register used our server metadata/prices, not client opcode65. Claude must not interpret this bundle as proof that those legacy copies are searchable/purchasable in the real client. No GE metadata/source policy changed here.

## Files

Production: `data/cfg/item_empty.tsv`; `items/ItemActionGuard.kt`; `items/charges/ItemCharges.kt`, new `remove_charges.plugin.kts`; `items/jewellery/amulet_of_glory.plugin.kts`; `items/osrs/DragonfireShield.kt`, `dragonfire_shield.plugin.kts`, `serpentine_helm.plugin.kts`; `skills/hunter/HunterRelease.kt`, `hunter_release.plugin.kts`, `Impling.kt`, `hunter.plugin.kts`, new `impling_jars.plugin.kts`; `skills/prayer/scattering/AshData.kt`, `ash_scattering.plugin.kts`. Existing Items.kt constants suffice; unchanged.

Tests: new N11InspectTests, N11ScatterTests, N11RemoveChargeTests, N11LootTests, N11DismantleTests, N11RemainingEmptyTests, N11EmptyGloryTests; extended N11HunterReleaseTests; census fixture menus. No new systems, quests or minigames.

## Claude's real client check

Boot all plugins without duplicate binding. Establish real acquisition for each built G id, then one actual click+avtrace per id:
-11284 Inspect in inventory,0 and stored charges; native11283, ward and wyvern neighbour checks. The same item picture/text; noted/stale/moved/replaced-before-queue refusal. Inspect must never mutate charges.
-11256 Loot: main loot, empty jar or broken jar, retained main loot on break, elite clue eligibility; MMII unfinished/completed19th-row eligibility. Full inventory and only one free slot refuse; two free slots suffice even with clue. Rapid clicks/stale slot/noted11257 do not duplicate. One existing jar also returns loot+jar through the same route.
-6215/6237/6259/8901 Remove-charge: Yes/No GUI94 (including667 pause-button dispatch), correct exhausted6235/6257/6279/8921, sound1650 once on Yes. Full inventory, two rapid clicks, remove/move/replace identical item while GUI open, and equipped copy must not be changed through an inventory click.
-22312 Scatter:110 baseXP once, animation445/gfx40/message; native infernal62.5 unaffected. Full inventory, combat, rapid repeat, notes, remove/move during the delay.
-10016/10018/10020 Release: jar10012 in full inventory,15HP / capped Defence / capped Attack, visuals5213 with915/916/917; no attack delay, three-tick click cooldown.10033 single, stack cancel/confirm.10146-10149 Release each selected creature exactly once; worn copy untouched.
-23936 Dismantle: Yes/No,20,000 scales, owner dismantle sound2632; inventory full, already almost-max scales, moved/replaced item, rapid clicks. Existing uncharged helm neighbour.
-12651 Empty ->1925;9508 Empty ->2026, no Drink claim. Full inventory and late-binding coexistence with existing Empty routes.
-10719 Rub only displays the existing exhausted-glory refusal. No teleport, charge mutation or consumption.

No server/testclient was started by B. Exact audiovisual absence/presence beyond the sourced donor actions still requires Claude's avtrace; no invented sound id is claimed retail-exact.

## Verification

Targeted Gradle command (only N11 and directly touched shared-route tests), read-only reference cache:

```powershell
$env:RSPS_TEST_CACHE = 'C:\RSPS\reference\openrs2_667\cache'
.\gradlew.bat :game:plugins:compileKotlin :game:plugins:test --tests 'gg.rsmod.plugins.content.n11.*' --tests '*DragonfireShieldTests*' --tests '*ItemActionGuardPagesTests*' --tests '*HunterDelayedInteractionTests*' --tests '*ConsumableTablesTests*' --priority low
```

Intermediate run:79 tests,0 failures/errors after fixing two test-verification mistakes. Final result recorded below after completion. Final source audit corrected an initial mistaken MMII-absence assumption before commit; new test exercises stages0/9/10 and both18/19-row tables. Doctor.ps1 hardcodes the primary checkout/service inspection, so it is not run under this card. Lint is evaluated read-only with its root redirected to this worktree.
