# Overdracht ChatGPT-B: N519, N235, N501

Branch server: `gpt-b/backlog-bundel-3`. Geen release, cachewijziging, savewijziging of draaiende testserver/client.

## N519 — quick-prayer-iconen hebben geen menuactie

De echte hit-test (`InterfaceManager.logicComponentList`) is headless gereproduceerd. Script 1388 bouwt **15×15** vinkvakjes op **271:42**, vanaf `(6,28)`, kolomafstand 37 en rijafstand 35. Die hebben `Select`/`Deselect`, `onOp 2290` en server-OP1. Script 1237 bouwt daarnaast **30×30** gebedsiconen op **271:8**, vanaf `(8,30)` binnen 271:5 (y=2). In quick-modus slaat dat script juist het toevoegen van `Activate` en de normale hoveractie over. Het icoonmidden valt buiten het bijbehorende vinkvakje. Dat punt levert met de oude clientcode alleen Cancel op.

Er is geen afdekkend component met `noClickThrough` gevonden. 271:42 is een kind van 271:4, naast 271:5; de dynamische vinkvakjes worden na de statische kinderen van 271:42 verwerkt. `serverActiveProperties` gebruikt de juiste sleutel `(hash << 32) + child`. `getOp` accepteert OP1 of `onOp`, en `ifButtonXSend` voert de hook uit en controleert daarna server-OP1. De server schakelt die events in `Prayers.setQuickPrayerSelectMode/unlockPrayerBookButtons` correct om. `RspsIconDrag.editable()` weigert quick-modus; de filter-menuacties onderscheppen de gewone IF_BUTTON-actie niet.

Vergelijking `3e654dd6^..dde836ef`: InterfaceManager kreeg alleen de PrayerFilters-menuhook en de gedeelde drag-observatie. De filterlayout laat in quick-modus de oorspronkelijke volgorde terugkomen; het onvoorwaardelijke `setStaticHidden(GRID,false)` verklaart hier geen verdwenen opties. De ontbrekende icoonactie volgt uit de cache-scripts, niet uit een bewezen N482/N494/N492-overlayregressie. De oude jar is niet gestart, conform de taakkaart.

Fix: `RspsPrayerFilters.quickMenuComponent` verwijst een quick-icoon naar de originele 271:42-child met hetzelfde slot. `InterfaceManager.addMiniMenuOptions` gebruikt die child en voorkomt dubbele entries waar icoon en vinkvakje overlappen. De bestaande tekst, onOp en server-events blijven de afhandeling bepalen. Normale iconen, Confirm, orb en servercode zijn niet gewijzigd.

Clientbestanden: `runescape/src/main/java/{InterfaceManager,RspsPrayerFilters}.java`, `runescape/src/test/java/RspsPrayerFiltersRegressionTest.java`. De clientcheckout staat **binnen** deze worktree: `build/n519-client`, branch `gpt-b/backlog-bundel-3`, basis `dde836ef`. De clientcommit wordt ook als `docs/client-patches/N519-quick-prayer-icon-clicks.patch` meegeleverd zodat Claude hem in de centrale clientrepository kan toepassen. De centrale clientmap is niet gewijzigd.

Regressie: de echte component-hit-test, iconen en native vinkvakjes, Select/Deselect zonder dubbele entries, alle 30/20/24 slots, 0/99 punten, filtering uit/aan met verborgen/herordende prayers, fixed/resizable, verborgen quick-grid en terugschakelen naar normale menuafhandeling. De fixture gebruikt de geometrie en hooks uit de cache; er wordt geen renderer, netwerk of echte cache-scriptuitvoering gestart. Met de oude menucode faalt het icoonmidden; met de fix slaagt het.

Claude: controleer **ook apart het kleine vinkvakje** en het icoonmidden. Mocht het kleine vinkvakje op de echte client nog alleen Cancel geven, dan verklaart deze bewezen icoonbug niet de gehele runtimefout en moet de actuele 271:42-child-/eventstaat worden gelogd. Controleer alle drie boeken, 0/99 punten, filtering uit/aan, fixed/resizable, Confirm, selectievarbits en daarna de orb. N483 (de teruggedraaide 2295-puntencheckpatch) is niet aangepast.

## N235 — alleen het swinggeluid

Uit `combat/specialattack/weapons/ancient_godsword.plugin.kts` zijn uitsluitend de twee extra `NEX2021_SACRIFICE_ATTACK_IMPACT`-aanroepen verwijderd: aanvaller en spelerdoelwit bij de 25-hit na acht ticks. Het swinggeluid `OsrsSfx.GODWARS_GODSWORD_SPECIAL_ATTACK` blijft: lokaal **10271**, OSRS **3869**. Ook op een mis-hit blijft dat geluid spelen. Schade, healing, afstand, offlinecontroles en killcredit zijn gelijk gebleven.

Klasseonderzoek: Morrigan's javelin (bleed), Ursine chainmace (bleed), Abyssal tentacle/vine whip (uitgestelde poison), Enhanced Excalibur (healing per vier ticks) voegen geen gelijksoortige tweede effectcue toe. Dragon crossbow en Dark bow hebben wel een afzonderlijke projectile-impactcue; hun broncommentaar beschrijft die expliciet. Ook de Fang heeft getimede specialdelen. Geen daarvan gebruikt de AGS-Nex-impactcue; alleen AGS gewijzigd.

Test: `AncientGodswordTests`, inclusief één swingcue vóór de miss-return en geen geluidsaanroep na MARK_TICKS. Claude: NPC, speler, mis-hit en `[avtrace]`: 10271 bij de special, geen 10345 bij de vertraagde hit.

## N501 — volledige route en ontbrekende verkrijgroute

Dit is de bestaande korte questvariant van de eigenaar (26 september), met varp **1912**, niet de volledige OSRS-quest. Combat 100+ is een advies, geen startblokkade. Er zijn geen questitems nodig of tussenstappen die items uitgeven.

1. **Stage 0 → 1:** spreek Archaeologist **1918**, GE-hal eerste verdieping `(3156,3490,1)`; kies Desert Treasure II en `I'll investigate the vault.` De quest biedt vervoer naar Nardah. Weigeren laat de quest actief; later opnieuw aanspreken geeft de herinnering/vervoeroptie.
2. **Stage 1 → 2:** spreek Ali the Wise **7447**, post `(3420,2938,0)`, reisdoel `(3419,2936,0)`. Hij beschrijft de Forgotten Four en de inscriptie.
3. **Stage 2 → 3:** spreek Ali opnieuw. Lees de messagebox en kies achtereenvolgens **Vardorvis (blade), Duke Sucellus (ice), Leviathan (deep), Whisperer (whisper)**. De opties worden na elke keuze opnieuw alfabetisch gesorteerd: volg de namen, niet vaste menunummers. Een fout behoudt stage 2; opnieuw aanspreken begint de puzzel opnieuw.
4. **Stage 3 → 4:** versla de persoonlijke **mummy 1961, level 103**, bij Nardah. De helper-timer start het gevecht op aankomst binnen 15 tiles, ook als de helperweergave uitstaat; Ali aanspreken kan het ook starten. Alleen de eigenaar-kill telt (`on_npc_killed → QuestFights.onKilled → completeStep`). Vluchten, doodgaan of uitloggen despawnt de foe; de stage blijft behouden en terugkomen laat hem opnieuw verschijnen. De kill biedt vervoer terug naar de GE-hal.
5. **Stage 4 → 5:** spreek Azzanadra **1971**, GE-hal eerste verdieping `(3170,3488,1)`. De dialoog en messagebox handelen het opnieuw verzegelen van de vier af; hier zijn geen vier bossgevechten of echte medaillons ingebouwd.
6. **Stage 5 → 10:** spreek Archaeologist opnieuw. `FoundationRewards.complete` geeft **één Ring of shadows (uncharged), lokaal 23848**, drie echte 100.000-XP-combatlamps en vijf questpunten; het schakelt de bestaande ring/sceptre/Scar/Forgotten Four/Brutus-unlocks in. Inventaris vol: beloning naar bank; ook bank vol: aan de voeten. Opnieuw klikken betaalt niet opnieuw. De Magic-keuze bij de Quest Guide gebruikt dezelfde completionfunctie en geeft dezelfde ring.

Archaeologist/Azzanadra zijn GE-halposts (`GeHomeHall.HALL_NPCS`, plaatsing via world_edits); hun Talk-to-handlers proberen eerst `FoundationQuests.talk`. Ali wordt door `FoundationQuests.NPC_POSTS` gespawnd en met Talk-to gebonden. Alle vijf onafgemaakte stages staan in de opgeslagen varp en blijven na relog beschikbaar. De echte spawn, bereikbaarheid, combat en relog tijdens de mummy moet Claude in de testclient controleren.

**Werkelijk ontbrekend:** `QuestChoices.applyLegacy` zet voor oude accounts de quest op voltooid én de rewardflag op betaald, maar geeft bewust geen items/XP. Zulke accounts krijgen dus geen ring en kunnen de quest niet meer spelen of de reward opnieuw claimen. Ook na verlies/Destroy is geen vervangingsdialoog, shop of drop voor de ring gevonden. Het itemscript kan alleen een bestaande ring laden/ontladen/controleren. Dit verklaart een verkrijgbaarheidsprobleem, terwijl de route voor nieuwe accounts wel bestaat. Geen nieuwe beloningsregel of vervangingsshop gebouwd: Claude/eigenaar moet bepalen hoe oude accounts en verloren ringen worden behandeld.

De vier volledige OSRS-bossroutes/gebieden zijn niet aanwezig in deze korte quest. Ringteleports melden expliciet dat die gebieden niet in deze wereld bestaan (bestaand besluit 19 september). N490: de DT2-dialoog/rewardtekst noemt Ruinous Powers nog niet en de geplande boekunlock ontbreekt hier nog; niet gewijzigd, conform taakkaart.

Testklasse `DesertTreasureIIRouteTests` voert de echte start-, Ali/puzzel-, Azzanadra- en einddialogen uit met nagebootste antwoorden, inclusief fout antwoord en herkansing. De mummy-kill wordt via de productie-completionfunctie vertegenwoordigd; combat wordt niet gesimuleerd. Verder: varp-reload op alle vijf stages, reward/duplicate, volle inventaris/bankfallback, Quest Guide-keuze en bewijs van de legacy-beloningkloof.

## Bronnen en besluiten

Lokale broncode en read-only cachedecompilaties 1717/1388/1237/2290; clientcommits `3e654dd6`, `750ef36e`, `dde836ef`; dossiers N519/N235/N501/N490 en de expliciete eigenaarbesluiten in de taakkaart. Geen donorcode overgenomen of nieuwe OSRS-mechanics bedacht. Tests en commits staan hieronder zodra afgerond.

## Afgeronde verificatie (2026-11-01)

- Clientcommit **0b2593b5891573f06c9705f5ade16a96ccd6e7b4**, basis dde836ef. `gradlew.bat :runescape:prayerFiltersRegression --priority low --max-workers=1 --offline`: **4192 assertions groen**. De eerdere regressie zonder de menuverwijzing faalde op het eerste icoonmidden. De exportpatch is met `git apply --reverse --check` op de gewijzigde checkout gecontroleerd.
- `gradlew.bat :game:plugins:compileKotlin :game:plugins:test --tests '*AncientGodswordTests' --tests '*DesertTreasureIIRouteTests' --priority low --max-workers=2 --offline`: Kotlin- en scriptcompilatie groen; **AncientGodswordTests 5/5 groen**. N501 stopte toen in setup doordat deze worktree geen cache heeft.
- De DT2-fixture is vervolgens cachevrij gemaakt met beperkte definities voor beloningsitems, NPC-namen en onafhankelijke varbits. Een fout in het uitlezen van de opties in de testharness is hersteld. `gradlew.bat :game:plugins:test --tests '*DesertTreasureIIRouteTests' --priority low --max-workers=1 --offline`: **4/4 groen**, inclusief echte dialogen en puzzelherkansing. Geen productiefix aan DT2 nodig gevonden; echte cachebindingen, combat en beweging blijven de clientcontrole.
- Rsps-Lint (kopie met root naar deze worktree omgezet), gewijzigde productiecode: **0 bevindingen**. Bron-diffs zonder whitespacefouten. Doctor uitgevoerd: 15 OK, 1 WARN (bestaande crashregels); de cache-hashcontrole had leesfouten op gelockte centrale cachebestanden en geldt dus niet als bewijs van cachegelijkheid.
- Geen volledige suite, clientrelease, testserver, testclient, cache- of savewijzigingen. De dossiers N519/N235/N501 zijn onderaan bijgewerkt met voortgang en testuitslagen.
