# N490 overdracht aan Claude

Branch: `gpt-b/backlog-bundel-4`. Besluitbron: `C:\RSPS\team\dossiers\N490.txt`, definitief plan, correctie DT2 en V1–V9.

## Gedrag en bestanden

- `mechanics/prayer/RuinousUnlocks.kt` en `ruinous_unlocks.plugin.kts`: DT2-completion plus een gelezen boek 24624; opgeslagen boolean `ruinous_unlocked`. Eén boek wordt atomair verbruikt, ook bij twee exemplaren/herhaalde clicks. Zonder DT2 of bij een bestaande unlock blijft het boek. Unlockgeluid `Sfx.TRAILBLAZER_UNLOCK_POWER`, messageBox en rode chatregel. `data/cfg/items.yml` voegt alleen de afgesproken Destroy-tekst toe.
- `AncientCurses.kt`: centrale guard vóór iedere Ruinous-wissel. Gevonden routes: POH-altaar, Ferox/prayer altar, Azzanadra, `::curse book ruinous`, `::prayerbook ruinous`, BugReplay. Alle gebruiken `switchBook`.
- `quests/quest_tab.plugin.kts`: na `QuestStatus.login` wordt een onterecht opgeslagen Ruinous-boek naar Normal omgezet, met uitleg; geen combat-timer kan deze migratie tegenhouden. Eerst de legacy-quests herstellen voorkomt een onterechte weigering van een geldig oud DT2-account. Geen spelersavebestanden aangeraakt.
- `FoundationQuests.kt`, `QuestChoices.kt`, `QuestGuideUi.kt`: DT2-beloning/Quest Guide leggen de boekvoorwaarde uit. De laatste DT2-stap is nu bij Azzanadra in dezelfde GE-hal; de bestaande stages blijven gelijk. Hij legt bij afronding uit dat het boek een drop is. De quest geeft geen boek of definitieve boekunlock. Temple at Senntisten blijft alleen Curses.
- `RuinousBookDrops.kt`, `DeadmanChest.kt`, `DeadmanBreach.kt`: onafhankelijke rollen zonder bracketboost: chest 1/25, breach monster 1/300, baas 1/400, Nex 1/150, Revenant 1/1000. De bestaande boss-classificatie van `BurdenTrinketDrops.sourceOf` wordt gebruikt, inclusief breach-bazen en Zemouregal; Nex heeft voorrang. Summons rollen niet. Breach-loot loopt één keer per bestaande lootgerechtigde contributor; de algemene kill-hook slaat breach-NPCs over. Het boek gaat bij een chest in de inventory (vol = persoonlijke grondloot), bij NPCs als persoonlijke grondloot. Paarse wereldmelding gebruikt dezelfde `DeadmanChestWorld.BROADCAST_COLOUR` als andere zeldzame drops.
- `DropSources.kt`: alleen een nieuwe dropregel, bestaande Drop Viewer-layout blijft intact. Your chance wordt 0 bij unlock/bezit; Normal blijft de afgesproken kans. De persoonlijke controle komt buiten de gedeelde view-cache.
- `CollectionLogPages.kt`: boek op chest-/breachpagina's en toepasselijke boss-pagina's; gedeelde Other-pagina Ruinous Powers dekt ook Revenants en de chest guardian. Awards registreren de bestaande source-key en killcount.

`Possessions.owns` is de enige bezitscontrole: alle geregistreerde containers, onder meer bank, gravestone en Death's Office. Een permanente unlock blokkeert drops ook zonder boek in bezit. De gewone lootrol wordt niet vervangen: een geblokkeerde extra boekrol valt weg.

## Testaccounts en dood

TeamAccountPresets/serializer nagekeken: teamtest1/4 krijgen Normal, teamtest2/3 Curses; geen preset geeft Ruinous. Daarom geen bypass of automatische Ruinous-unlock toegevoegd. Een teamtest-account kan DT2 afronden/kiezen en het boek lezen zoals een gewone speler. Legacy voltooit DT2 volgens de bestaande regel maar krijgt de boekunlock niet cadeau.

`mechanics/death` en `skills/summoning` zijn alleen gelezen, niet gewijzigd. Het boek is onruilbaar, niet draagbaar en niet lockbaar. In de bestaande PvP-code is het `LOST_FOR_COINS`, zowel onder als boven Wilderness 20: het verdwijnt, killer krijgt de bestaande high-alch coinwaarde en Perdu registreert terugkoop tegen itemwaarde (minstens 1 coin). Bij kostprijs 1 betekent dit 0 killer-coins en 1 coin terugkoop. Bij monsterdood geldt de bestaande verdeling: ongeskulled drie waardevolste items behouden; geskulled nul; verloren boek naar gravestone/Death's Office. Een veilige dood houdt de normale veilige regels. De permanente prayer-unlock gaat niet verloren.

## Validatie

Eindrun geslaagd: **165 tests, 0 failures, 0 errors, 0 skips**. Serverkern en scripts compileren. Nieuwe tests: `RuinousUnlocksTests` (12); bijgewerkt: `RuinousPowersTests`, `DesertTreasureIIRouteTests`. De echte-mapcheck van de quest-NPCs en landingen is ook groen na het aanvullen van de ontbrekende, genegeerde lokale xteas-testfixture.

```powershell
.\gradlew.bat :game:plugins:compileKotlin :game:plugins:test --tests '*RuinousUnlocksTests' --tests '*RuinousPowersTests' --tests '*AncientCurses*Tests' --tests '*PrayerScrolls*Tests' --tests '*DropViewerTests' --tests '*DesertTreasureIIRouteTests' --tests '*TeamAccountPresetsTests' --tests '*DeadmanChestTests' --tests '*BreachRewardTests' --tests '*NewPlayerFoundationTests.every quest npc post*' --priority low --max-workers=1
```

Een eerdere ruimere run van 190 tests had daarnaast twee bestaande foundationproblemen, buiten N490:

- `NewPlayerFoundationTests.the quest list has the ten OSRS quests and puts unfinished quests on top`: het ongewijzigde `QuestListCacheTool.plan` meldt `struct 2748 exists with other content` in de bestaande cache. Geen cacheherstel uitgevoerd.
- `NewPlayerFoundationTests.no main source outside the foundation writes a choice quest or its unlock`: de bestaande `::prayerbook ancient`-testhelper in `commands.plugin.kts` schrijft `AncientCurses.UNLOCKED_ATTR = true`. Die regel staat ook in de oorspronkelijke HEAD en is niet door N490 gewijzigd; de Ruinous-route gebruikt de nieuwe guard. Geen wijziging aan deze bestaande Curses-testhelper.

Deze twee checks zijn geen onderdeel van de gerichte groene eindrun. De overige 23 foundationtests waren groen in de ruimere run; de aanvankelijk ontbrekende mapfixture is vervolgens aangevuld en de mapcheck is opnieuw groen uitgevoerd. De samenvatting van de ruimere run staat lokaal in `build/N490-first-test-summary.txt`.

`git diff --check` en Rsps-Lint op deze worktree: geen bevindingen. Doctor gaf waarschuwingen over vrij RAM en bestaande serverlogs; de standaard cache-hashcontrole kon door bestandslocks niet betrouwbaar controleren. Een aparte read-only SHA-256-vergelijking met gedeelde bestandstoegang bevestigde daarna dat alle 39 lokale cachebestanden bytegelijk aan de broncache zijn.

Bronnen/patronen: eigenaarplan V1–V9; bestaande `PrayerScrolls`, Ancient Hymnal-read, `Possessions`, burden-trinket drops, DeathRules/UntradeableDeathProtection/PerduReclaim. Geen nieuwe donorport of cache-import nodig. Cache-afhankelijke tests lezen een ongewijzigde, genegeerde lokale kopie van de bestaande cache; geen broncache aangepast.

## Claude in de testclient

1. DT2 spelen: afronden bij Azzanadra, boekvoorwaarde in dialoog, beloningsscherm en Quest Guide controleren.
2. Boek zonder DT2 lezen: weigering en behoud. Met DT2 lezen: geluid, messageBox, rode exacte unlockregel en één boek weg. Tweede boek/herhaalde click behoudt het overige boek.
3. Zonder iedere voorwaarde via Ferox, POH, Azzanadra en beide commando's wisselen: weigering; na unlock wisselen en paars tabicoon controleren.
4. Relog na unlock behoudt Ruinous; oud Ruinous-account zonder unlock wordt Normal met uitleg. Ook met nog lopende combat-timer.
5. Drops forceren uit chest, breach-monster/-baas, Zemouregal, gewone baas, Nex en Revenant. Paarse wereldmelding, persoonlijke grondloot, volle inventory en Collection Log controleren. Met boek in bank/graf of al gelezen mag geen extra boek vallen.
6. Drop Viewer-regel: juiste bron/kans en dezelfde stijl; bezit/unlock geeft Your 0 en onveranderde Normal-kans.
7. Dood met boek: monsterdood keep/grave, PvP/Perdu zoals hierboven; na lezen blijft de unlock bestaan. Destroy-dialogue noemt: `You will have to find another one.`

Geen testserver/testclient gestart en niets live gezet. Geen open eigenaarsbesluiten toegevoegd; de bestaande boss-classificatie is hierboven expliciet vastgelegd voor review. De twee bestaande foundationchecks hierboven blijven als technische vervolgpunten voor Claude staan.
