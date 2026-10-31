# N11 bundle 2: Read and Drink

Card: ChatGPT-B, branch `gpt-b/n11b2`. Acquisition evidence remains in `C:/RSPS/team/dossiers/N11.txt`, S<id>; Wiki pages specify behaviour, not local acquisition. Resumed existing uncommitted implementation; no cache, client, saves or running services changed.

## Implementation

19 of the 24 specified Read items use one immutable document registry with attributed Wiki text and interface 960, the existing Wilderness Manual / Barbarian Skills book interface. The old implementation removed CURR_BOOK immediately after setting it and never registered its page buttons. The route now registers close/previous/next once, stores page position per player, fills both 15-line leaves, wraps text conservatively and retains the final partial page. Existing books still use the same API and have a script regression test. Read refuses combat, another modal, locked interaction, noted definitions and stale inventory slots. Pages do not consume or modify items, change quest progress, grant rewards or summon champions.

Relicym's balm 4842/4844/4846/4848 uses the existing Potion enum, PotionType, Potions.drinkAt and potionDrinking script. Chain: 4 -> 3 -> 2 -> 1 -> empty vial 229. There is no player disease state (only farming disease, hitsplat labels and descriptive summoning text), so the card-authorized fallback consumes a dose with the existing potion drink/dose/finished messages, animation, sound and cooldown. It does not heal, restore skills, cure poison/venom or grant immunity. Empty remains the existing decant route. Empty vial and noted doses acquire no Drink binding. RemovedPotions stays excluded. A stale-slot guard in the shared route prevents ItemContainer.remove from searching forward and consuming a second same-id item.

Blamish oil 1582 refuses through `consumables.plugin.kts`, with the RS Wiki player dialogue: "You know, I'd really rather not." Oil stays intact; no dose, vial, health effect, animation or potion delay.

## Per-item sources

Canonical Wiki transcript/item URLs below are also source comments beside every registered document. Mirrors explicitly identified in code were used when canonical pages returned 403/robots/cache-miss. The Knight's notes and full Gianne's cook book transcripts were additionally recovered on resumption; OSRS wording uses 'aviansie' (the Wiki records an older 'aviantese' spelling). Wiki game transcripts are Jagex text, transcribed by the Wiki contributors; no replacement prose was invented.

| S key / id | Registered text | Canonical source |
| --- | --- | --- |
| S5 | Instruction manual | [Instruction manual](https://oldschool.runescape.wiki/w/Instruction_manual) |
| S600 | Astronomy book | [Astronomy book](https://oldschool.runescape.wiki/w/Astronomy_book) |
| S1856 | Guide book | [Guide book](https://oldschool.runescape.wiki/w/Guide_book) |
| S2023 | Cocktail guide | [Cocktail guide](https://oldschool.runescape.wiki/w/Cocktail_guide) |
| S4686 | Embalming manual | [Embalming manual](https://oldschool.runescape.wiki/w/Embalming_manual) |
| S4707 | Crumbling tome | [Crumbling tome](https://oldschool.runescape.wiki/w/Crumbling_tome) |
| S5520 | Abyssal book | [Abyssal book](https://oldschool.runescape.wiki/w/Abyssal_book) |
| S6071 | Mourner letter | [Mourner letter](https://oldschool.runescape.wiki/w/Mourner_letter) |
| S6800 | Champion's scroll | [Champion's scroll](https://oldschool.runescape.wiki/w/Giant_champion_scroll) |
| S7162 | Pie recipe book | [Pie recipe book](https://oldschool.runescape.wiki/w/Pie_recipe_book) |
| S7922 | Al kharid flyer | [Al kharid flyer](https://oldschool.runescape.wiki/w/Al_kharid_flyer) |
| S9003 | Security book | [Security book](https://oldschool.runescape.wiki/w/Security_book) |
| S9004 | Stronghold notes | [Stronghold notes](https://oldschool.runescape.wiki/w/Stronghold_notes) |
| S9078 | Moonclan manual | [Moonclan manual](https://oldschool.runescape.wiki/w/Moonclan_manual) |
| S10999 | Goblin book | [Goblin book](https://oldschool.runescape.wiki/w/Goblin_book) |
| S11169 | Newspaper | [Newspaper](https://runescape.wiki/w/Newspaper) |
| S11656 | Glassblowing book | [Glassblowing book](https://oldschool.runescape.wiki/w/Glassblowing_book) |
| S2167 | Gianne's cook book | [Gianne's cook book](https://oldschool.runescape.wiki/w/Gianne%27s_cook_book) |
| S11734 | Knight's notes | [Knight's notes](https://oldschool.runescape.wiki/w/Knight%27s_notes) |

Retrieval notes: Astronomy book, Guide book, Embalming manual, Giant champion scroll, Al kharid flyer, Gianne's cook book and Knight's notes were retrieved from the corresponding `https://osrsindex.com/wiki/<hyphenated-title>?site=osrs_wiki` mirror. Newspaper and Glassblowing book were retrieved from `https://runescape.fandom.com/wiki/Newspaper` and `https://runescape.fandom.com/wiki/Glassblowing_book`. Other document text was recovered from the canonical Wiki item pages in the interrupted work. The full transcript is stored in the registry, not a summary. Current OSRS Security book mentions Jagex Accounts, and current Pie recipe book includes botanical/mushroom/dragonfruit pies: these are retail source text, not a claim those services/recipes are implemented here.

| Drink / id | Behaviour source |
| --- | --- |
| S4844; family 4842/4844/4846/4848, vial 229 | [Relicym's balm](https://oldschool.runescape.wiki/w/Relicym%27s_balm). Each dose weakens disease; no temporary immunity. Disease-free fallback explicitly authorized by task card. Exact balm-specific drink transcript was not present on the retrieved item page; existing potion-route messages are used, rather than inventing a disease-cured message. |
| S1582 | [Blamish oil](https://runescape.wiki/w/Blamish_oil), retrieved via indexed Wiki text, explicitly quotes the RS refusal. OSRS punctuation differs (ellipsis); this 667 item follows the RS wording. |

Donors: no external donor implementation ported. Reused this repository's book interface and potion/consumable systems. No donor-first requirement. Items.kt constants already exist and need no modification.

## Five skipped items: no guessed content or device state

| S key | Source attempted | Reason / follow-up |
| --- | --- | --- |
| S11680 Address form | [Wiki](https://oldschool.runescape.wiki/w/Address_form) | Item/acquisition description retrieved, no actual Read text. Do not turn its encyclopaedia description into an in-game document. Obtain Read transcript. |
| S15056 Ticker (Read op2) | [Wiki](https://runescape.wiki/w/Ticker), [legacy Wiki](https://runescape.fandom.com/wiki/Ticker) | Counter tool, not a book. Exact read display and current counter state not verified; no working Tick lifecycle here. Omitted rather than return a fabricated zero. |
| S15061 Timepiece (stopped), Read op2 | [Wiki](https://runescape.wiki/w/Timepiece), [legacy Wiki](https://runescape.fandom.com/wiki/Timepiece) | Wiki describes elapsed days/hours/minutes/seconds and reset before bank, but no exact 667 Read transcript/display obtained. Start/pause/reset lifecycle is absent. Omitted as allowed by card. |
| S15077 Faruq's toolonomicon | [Wiki](https://runescape.wiki/w/Faruq%27s_toolonomicon), [legacy Wiki](https://runescape.fandom.com/wiki/Faruq%27s_toolonomicon) | Purpose found; complete book transcript not recovered. Do not substitute a guide-site description. |
| S18757 Court summons | [Wiki](https://runescape.wiki/w/Court_summons), [Court Cases transcript](https://runescape.fandom.com/wiki/Transcript%3ACourt_Cases) | No complete actual summons text obtained; case-dependent content and unavailable Court Cases state cannot be guessed. No Court Cases/King's Ransom added. |

These five Read options remain unresolved. No owner choice is made about missing quests or game-tool mechanics. Source retrieval, then scope assessment, is needed before implementing them.

## Files and validation

Production: `items/books/ReadableDocuments.kt`, `documents.plugin.kts`, `book_interface.plugin.kt`; `items/potion/Potion.kt`, `PotionType.kt`, `Potions.kt`; `items/food/consumables.plugin.kts` (all beneath `game/plugins/src/main/kotlin/gg/rsmod/plugins/content/`). Tests: `n11/N11ReadTests.kt`, `n11/N11DrinkTests.kt`, menu fixture `src/test/resources/n11-item-menus.tsv`, and existing `items/potion/PotionRosterTests.kt` (remove obsolete balm block; allow RSPS_TEST_CACHE for read-only reference cache). No new interface/cache definition required.

Tests drive compiled production scripts for actual Read/Drink dispatch and pagination. They check every document's nonempty text, source comment and real census Read option; preserve every non-whitespace character across all pages; test close cleanup, separate readers, stale buttons, combat/other-modal/noted/stale-slot rejection; exercise both existing books; test every balm dose, final vial, message, cooldown, rapid clicks, stale slot, noted dose and RemovedPotions exclusions; test oil refusal and unchanged inventory/effects.

Doctor hardcodes the primary worktree and inspects services, so is outside the card. Lint is run with its root redirected in memory to this worktree.

## Claude: real testclient acceptance

1. Obtain three books through ordinary sources: Pie recipe book 7162 (Cooking Guild shop), Astronomy book 600 (ground spawn 2438,3187; S600), and Crumbling tome 4707 (ground spawn 3571,3312; S4707). Read them; screenshot opening, middle and final spreads. Confirm every line is visible, title, page buttons and closing. Read the full long Crumbling tome to its last sentence. Check fixed/resizable if interface layout differs.
2. Try Read during combat, with another modal open and on a noted copy; no book should open or replace the other modal. Close/reopen starts at page 1. Check the existing Wilderness Manual and Barbarian Skills books still open and turn pages.
3. Buy 4844 from Uglug's ordinary shop. Drink 3 -> 2 -> 1 -> vial 229; capture messages, animation and sound. Try a rapid second click and another same-id inventory slot; only one dose per cooldown. Try 4842 -> 4844 too. No poison cure or stat restoration is expected, as disease does not exist here.
4. Click Drink on blamish oil 1582: player dialogue, oil retained, no drink animation or vial. Record a screenshot.

Open questions: five missing transcripts/device-state specifications above; exact balm-specific Wiki drink message not recovered (standard route text used). Visual verification of interface-960 line widths remains Claude's task; no client/server was started by B.

## Final verification (2026-10-31)

- Core/plugins/scripts compileKotlin: pass. A final compile after whitespace cleanup also passed.
- Targeted final run: 38 tests, 0 failures, 0 errors. N11ReadTests 5 + N11DrinkTests 3; existing N11 classes 20; PotionRosterTests 8; ConsumableTablesTests 2.
- Changed-file lint redirected to this worktree: 7 production files, 0 findings. git diff --check: pass.
- Initial run: 28 passing tests, two existing cache-backed class setup failures from the empty worktree cache. Corrected by the read-only reference-cache environment variable; no cache copied or changed.
- Exact package filter is intentional: the card's broad `*N11*` also matches unrelated method names (documented in bundle 1). Only the N11 package and the two shared-route classes were run.

```powershell
$env:RSPS_TEST_CACHE = 'C:\RSPS\game\game\data\cache'
.\gradlew.bat :game:compileKotlin :game:plugins:compileKotlin :game:plugins:test --tests 'gg.rsmod.plugins.content.n11.*' --tests '*PotionRosterTests*' --tests '*ConsumableTablesTests*' --priority low
.\gradlew.bat :game:plugins:compileKotlin :game:plugins:scripts:compileKotlin --priority low
```
