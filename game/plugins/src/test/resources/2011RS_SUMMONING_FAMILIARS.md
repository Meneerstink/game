# RSPS Summoning — Hoofddocument

Status: `ACTIVE MASTER`  
Scope: revision 667 / late-2011 RSPS  
Laatste inventarisatie: 2026-09-15

Dit is het enige hoofddocument voor Summoning-tests en familiar-regressies. De
testimplementaties blijven in de servercheckout; dit document houdt de volledige
testinventaris, bronlocaties, bewijsdata en verificatiestatus bij.

## 1. Canonieke locaties

### Testcode

`C:\RSPS\game\game\game\plugins\src\test\kotlin\gg\rsmod\plugins\content\skills\summoning\`

Bevat momenteel 40 Summoning-testbestanden.

### Implementatie

`C:\RSPS\game\game\game\plugins\src\main\kotlin\gg\rsmod\plugins\content\skills\summoning\`

Belangrijke gedeelde routes:

- `Familiar.kt`, `FamiliarCombat.kt`, `FamiliarCapabilities.kt`
- `SummoningFamiliarDefinitions.kt`, `SummoningCombatDefinitions.kt`
- `SummoningSpecialMoves.kt`, `SummoningScrollData.kt`, `SummoningPouchData.kt`
- `SummoningSpawnDespawnAnimations.kt`, `SummoningChatheadAnimations.kt`
- `SummoningUi.kt`, `SummoningLeftClick.kt`, `SummoningLedger.kt`

### Evidence en referenties

- `C:\RSPS\summoning_refs\` — cacheprobes, familiar-definities, sequence-geluiden,
  spawn/despawn-data en live screenshots in `live`, `live2` en `live3`.
- `C:\RSPS\2011RS_SUMMONING_FAMILIARS.md` — dit hoofddocument met de 2011
  familiar-reference onderaan.
- `C:\RSPS\CLAUDE_SUMMONING_667_CANDIDATE_MATRIX.csv` — historische kandidaatmatrix.
- `C:\RSPS\all_scrolls.txt` en `C:\RSPS\impl_scrolls.txt` — historische scrollinventaris.
- `C:\RSPS\import-journal\resume-summoning-regression-20260912.log`
- `C:\RSPS\import-journal\resume-summoning-tests-20260912.log`
- `C:\RSPS\tools\live\Summon.ps1`, `SpawnPouch.ps1`, `SetupAndSummon.ps1` —
  live testhulpmiddelen.

## 2. Volledige testindex

Alle onderstaande bestanden staan in de canonieke testmap hierboven.

### Familiar lifecycle, interface en gedrag

- `EnchantedHeadgearTests.kt`
- `FamiliarAssistTests.kt`
- `FamiliarCapabilityTests.kt`
- `FamiliarChatheadTests.kt`
- `FamiliarDefinitionTests.kt`
- `FamiliarFollowRecoveryTests.kt`
- `FamiliarLifecycleTests.kt`
- `FamiliarPathingTests.kt`
- `FamiliarPointsTests.kt`
- `FamiliarSessionLifecycleTests.kt`
- `FamiliarSummoningProtectionTests.kt`
- `FamiliarTargetMaskTests.kt`
- `FollowerDetailsTabTests.kt`
- `SummoningInterfaceTests.kt`
- `SummoningLeftClickTests.kt`
- `SummoningPanelPlacementTests.kt`
- `SummoningLedgerTests.kt`

### Pouches, storage, obelisks en data

- `BeastOfBurdenRestrictionTests.kt`
- `BeastOfBurdenTests.kt`
- `ForagerStorageTests.kt`
- `ObeliskRenewalTests.kt`
- `SummoningCatalogueTests.kt`
- `SummoningCompletenessTests.kt`
- `SummoningCreationDataTests.kt`
- `SummoningScrollDataTests.kt`
- `SummoningTradeInDataTests.kt`
- `SummoningTestCache.kt`

### Combat, specials en animations

- `FamiliarCombatValueTests.kt`
- `FamiliarDamageUnitTests.kt`
- `FamineSpecialMoveTests.kt`
- `SummoningCombatDefinitionTests.kt`
- `SummoningCombatLevelTests.kt`
- `SummoningPassiveCoverageTests.kt`
- `SummoningSpawnDespawnAnimationTests.kt`
- `SummoningSpecialMoveCoverageTests.kt`
- `SummoningSpecialMoveTests.kt`
- `SummoningSpecialResourceTests.kt`

### Audio en cache-provenance

- `FamiliarAttackAudioTests.kt`
- `SummoningAudioTests.kt`
- `SummoningCacheAudioProvenanceTests.kt`

## 3. Prioriteit voor de huidige storing

De huidige live klacht is: familiar attack sounds en special attacks geven
onjuiste of ontbrekende resultaten. Begin daarom met deze keten:

1. `FamiliarAttackAudioTests.kt`
2. `SummoningAudioTests.kt`
3. `SummoningCacheAudioProvenanceTests.kt`
4. `FamiliarCombatValueTests.kt`
5. `SummoningCombatDefinitionTests.kt`
6. `SummoningSpecialMoveTests.kt`
7. `SummoningSpecialMoveCoverageTests.kt`
8. `SummoningSpecialResourceTests.kt`
9. `SummoningSpawnDespawnAnimationTests.kt`
10. `SummoningTestCache.kt`

Bronnen voor audio- en animatievergelijking:

- `C:\RSPS\summoning_refs\familiar_seq_sounds.txt`
- `C:\RSPS\summoning_refs\familiar_npcdefs.txt`
- `C:\RSPS\summoning_refs\familiar_bastypes.txt`
- `C:\RSPS\summoning_refs\darkan_spawn_despawn_anims.txt`
- `C:\RSPS\summoning_refs\live3\` — recente familiar/special-menu evidence.

## 4. Verificatiestatus

- `SOURCE`: alle 40 testbestanden zijn aanwezig in de canonieke testmap.
- `FUNCTION`: deze inventaris koppelt tests aan de relevante Summoning-routes.
- `RUN`: niet opnieuw uitgevoerd tijdens deze documentconsolidatie.
- `LIVE/AV`: attack sounds en special attacks blijven live te hertesten.
- Onbekende sound-ID's blijven `unknown/unresolved`; ze worden niet als bewuste
  stilte (`none`) geïnterpreteerd.
- Een groene unit-test bewijst niet automatisch client-audio of live gedrag.

Werkafspraak voor volgende agents: lees eerst dit document, daarna alleen de
relevante testgroep en de genoemde bron/evidencebestanden. Gebruik de oude
`C:\RSPS\game\game\SUMMONING_AUDIT.md` niet als aparte statusbron; die pointer
is retired.

## 5. 2011 Knowledge Base familiar reference

Regression-anchor transcription of the archived 2011 Knowledge Base article:
https://2011.rs/kb/summoning_familiars

The test parser intentionally follows the article's image slug followed by its
`Fights (Level N)` text. These 67 combat-level pairs are transcribed from the
article's familiar table; non-combat familiar rows are omitted because the
article does not state a combat level for them. The six alternate cockatrice
names are also absent from the article and are derived by the production code
from their combat-identical Spirit cockatrice record.

![Familiar](familiars/abyssal_lurker.gif)
Fights (Level 93)

![Familiar](familiars/abyssal_parasite.gif)
Fights (Level 86)

![Familiar](familiars/abyssal_titan.gif)
Fights (Level 215)

![Familiar](familiars/adamant_minotaur.gif)
Fights (Level 133)

![Familiar](familiars/albino_rat.gif)
Fights (Level 37)

![Familiar](familiars/arctic_bear.gif)
Fights (Level 122)

![Familiar](familiars/barker_toad.gif)
Fights (Level 112)

![Familiar](familiars/bloated_leech.gif)
Fights (Level 76)

![Familiar](familiars/bronze_minotaur.gif)
Fights (Level 50)

![Familiar](familiars/bull_ant.gif)
Fights (Level 58)

![Familiar](familiars/bunyip.gif)
Fights (Level 70)

![Familiar](familiars/compost_mound.gif)
Fights (Level 37)

![Familiar](familiars/desert_wyrm.gif)
Fights (Level 31)

![Familiar](familiars/dreadfowl.gif)
Fights (Level 26)

![Familiar](familiars/evil_turnip.gif)
Fights (Level 62)

![Familiar](familiars/fire_titan.gif)
Fights (Level 139)

![Familiar](familiars/forge_regent.gif)
Fights (Level 133)

![Familiar](familiars/geyser_titan.gif)
Fights (Level 200)

![Familiar](familiars/giant_chinchompa.gif)
Fights (Level 42)

![Familiar](familiars/giant_ent.gif)
Fights (Level 137)

![Familiar](familiars/granite_crab.gif)
Fights (Level 26)

![Familiar](familiars/granite_lobster.gif)
Fights (Level 129)

![Familiar](familiars/honey_badger.gif)
Fights (Level 45)

![Familiar](familiars/hydra.gif)
Fights (Level 141)

![Familiar](familiars/ice_titan.gif)
Fights (Level 139)

![Familiar](familiars/iron_minotaur.gif)
Fights (Level 70)

![Familiar](familiars/iron_titan.gif)
Fights (Level 220)

![Familiar](familiars/karamthulhu_overlord.gif)
Fights (Level 95)

![Familiar](familiars/lava_titan.gif)
Fights (Level 148)

![Familiar](familiars/mithril_minotaur.gif)
Fights (Level 112)

![Familiar](familiars/moss_titan.gif)
Fights (Level 139)

![Familiar](familiars/obsidian_golem.gif)
Fights (Level 126)

![Familiar](familiars/pack_yak.gif)
Fights (Level 175)

![Familiar](familiars/phoenix_essence.gif)
Fights (Level 124)

![Familiar](familiars/praying_mantis.gif)
Fights (Level 131)

![Familiar](familiars/pyrelord.gif)
Fights (Level 70)

![Familiar](familiars/ravenous_locust.gif)
Fights (Level 120)

![Familiar](familiars/rune_minotaur.gif)
Fights (Level 154)

![Familiar](familiars/smoke_devil.gif)
Fights (Level 101)

![Familiar](familiars/spirit_cobra.gif)
Fights (Level 105)

![Familiar](familiars/spirit_cockatrice.gif)
Fights (Level 64)

![Familiar](familiars/spirit_dagannoth.gif)
Fights (Level 148)

![Familiar](familiars/spirit_graahk.gif)
Fights (Level 93)

![Familiar](familiars/spirit_jelly.gif)
Fights (Level 88)

![Familiar](familiars/spirit_kalphite.gif)
Fights (Level 39)

![Familiar](familiars/spirit_kyatt.gif)
Fights (Level 93)

![Familiar](familiars/spirit_larupia.gif)
Fights (Level 93)

![Familiar](familiars/mosquito.gif)
Fights (Level 32)

![Familiar](familiars/spirit_scorpion.gif)
Fights (Level 51)

![Familiar](familiars/spirit_spider.gif)
Fights (Level 25 - Controlled)

![Familiar](familiars/spirit_terrorbird.gif)
Fights (Level 62)

![Familiar](familiars/spirit_tz_kih.gif)
Fights (Level 36)

![Familiar](familiars/spirit_wolf.gif)
Fights (Level 26)

![Familiar](familiars/steel_minotaur.gif)
Fights (Level 90)

![Familiar](familiars/steel_titan.gif)
Fights (Level 230)

![Familiar](familiars/stranger_plant.gif)
Fights (Level 107)

![Familiar](familiars/swamp_titan.gif)
Fights (Level 152)

![Familiar](familiars/talon_beast.gif)
Fights (Level 135)

![Familiar](familiars/thorny_snail.gif)
Fights (Level 26)

![Familiar](familiars/unicorn_stallion.gif)
Fights (Level 70)

![Familiar](familiars/vampire_bat.gif)
Fights (Level 44)

![Familiar](familiars/void_ravager.gif)
Fights (Level 46)

![Familiar](familiars/void_shifter.gif)
Fights (Level 46)

![Familiar](familiars/void_spinner.gif)
Fights (Level 40)

![Familiar](familiars/void_torcher.gif)
Fights (Level 46)

![Familiar](familiars/war_tortoise.gif)
Fights (Level 86)

![Familiar](familiars/wolpertinger.gif)
Fights (Level 210)
