# DMM2 fase 3 — overdracht ChatGPT-A aan Claude

17 november 2026, branch `gpt-a/dmm-pakket-2`, eigen worktree `C:\RSPS\work\gpt-a`.
Voortgezet vanaf het ongecommitte fase3-werk; de eerdere bouwcommits zijn behouden.
Dit is de afronding van GPT-A's fase3-kaart. De volledige DMM-content is nog geen spelerklare/live-release.

## Opgeleverd per kaartpunt

|Punt|Commit|Resultaat en bewijs|
|---|---|---|
|i — cachevoorbereiding|93763a44c|`PREFLIGHT-RONDE.sh`, native runner, exact uitvoerbare commando's en alle uitvoer in `preflight-output`.195 bronregels,105 uitgevoerde stappen,102 geslaagde plannen. Twee cachekopieën/78 bestanden SHA256 ongewijzigd. Geen apply.|
|ii — breektests|093e52b77|16 nieuwe `Dmm2BreakTests` met transacties, reconnect, skull/death, volle containers, overflow, replay en behoud van items/charges; inclusief echte PvM-deathexecutie naar graf met safebox apart. Eigen runharness en matrix in `PHASE3-BREAKTESTS.md`.|
|iii — bronpunten|d25347b5b|16 wiki-revisies met datum/inhoud/SHA256 en twee ontbrekende transcripts. Gekozen Avarice GE-stackwaardering, khopesh melee-delayadvies en bewezen ancient-cape-recept. Drie eigen sourcepolicy-tests; geen bronloos lokaal item-id.|
|iv — testclientdraaiboek|595bdcc02|`TESTCLIENT-CHECKLIST.md`, elf systeemblokken plus voorbereiding/aftekening. Echte `::`-commando's, gemapte itemgenerator, bronresultaten en A8/I10/D1–D20-proeven.|
|v — eindoverdracht|deze commit|Dit verslag, bewaarde groene JUnit-XML en `PHASE3-VALIDATION.json`/`validate_phase3.py`; volledige stand tevens onderaan het externe dossier.|

## Testuitslag

**101/101 tests groen, nul failures/errors/skipped,15 eigen klassen.** Daarvan zijn19 tests nieuw in fase3:16 breektests en3 bronpolicytests;
82 eigen tests uit de eerdere delen zijn in dezelfde gerichte validatie meegenomen. De actuele gewijzigde Kotlin is gecompileerd metKotlin1.8,
negen eigen bindingscripts compileerden en de tests zijn echte Gradle Test/JUnit-runs met `--priority low` binnen het machinebrede bouwslot.
De enige eerste rode test was een fixturefout: opnieuw definiëren van Blood money als stackable had de tradeable-flag teruggezet naarfalse.
Die flag is hersteld; uitsluitend de betreffende16-testklasse is herhaald. De overige85 groene resultaten zijn byte-exact bewaard.
Verse timestamps en bron-/XML-hashes staan in `PHASE3-VALIDATION.json`.

Geen volledige suite, normale serverboot, testclient- of live-PASS. `Doctor.ps1` en `tools/lint/Rsps-Lint.ps1` bestaan niet in deze worktree.
De productiediff is op whitespace en verboden bestandsnamen gecontroleerd; bron-/native uitvoer blijft byte-exact via `.gitattributes`.

## Cache-uitkomst en aanvullend id

Alle50 losse itemplannen zijn geslaagd. Er zijn coherente bundelplannen per bestaande tool, plus zes klassieke NPC's in één succesvol plan.
De klassieke Wisp-import faalt op animaya13893632; diezelfde beperking laat ook de volledige klassieke NPC-bundel falen.
De bestaande skeletal-npc-import én de echte sidecar-planning slagen wel: Wisp15571, modellen49222/49218/49221/49224,
source-sequences10230/10233, rig2138, clips212:0/210:0. De klassieke bake blijft slechts fallback; beeld/animatie nog door Claude controleren.

UI232 slaagt apart. UI230 faalt concreet omdat font645 geen mapping heeft; de porter ondersteunt494..497.
Geen blinde fontvervanging en geen bewezen Nigel-iconmapping. Deze drie foutuitkomsten blijven in de uitvoer staan als diagnose.
OUD→NIEUW en CENSUS staan per stap in `PREFLIGHT-RESULTAAT.md`; plan-ids zijn niet gereserveerd en nooit runtime-mappings.
Spritekandidaten, placeholders en note-links op hergebruikte ouders hebben expliciete afhandeling/beperkingen in de coverage.

**IDS-NODIG-VOOR-CLAUDE aanvullend:** item34607, `DEADMAN_MA2_ZAROS_CAPE`.
[Wiki-revisie15403804](https://oldschool.runescape.wiki/w/Special:PermanentLink/15403804),2026-11-16T15:04:15Z bewijst Ancient fabric + een
imbued Deadman god cape. De gewone ancient cape aan Nigel kan deze variant niet rechtstreeks maken. Recept en weigering zijn serverzijdig
gebouwd; de basealias gebruikt de bestaande ancient-cape death-/gameplayregels. Item34607 ontbreekt in2686 en vraagt een nieuwere gepinde cache.

## Bronnen en gekozen adviezen

OSRS Wiki API + OpenRS2-cache2686 + RuneLite gameval; **geen donorcode** in deze fase.
`PHASE3-SOURCES.md` beschrijft ieder opgelost/onopgelost bronpunt en de gekozen uitvoerregel.
Avarice gebruikt onze bestaande GE-stackwaarde inLong/clamp; dit is expliciet lokaal advies totdat de precieze OSRS-waarderingsbasis gemeten is.
Khopesh gebruikt voor de eerste PvP-hit de bestaande melee-delay1 met PID-correctie; de vaste bolts7/9/11/13 zijn wel rechtstreeks wiki-bewezen.
Het exacte eerste OSRS AV-offset blijft meetpunt; de appearance kan de waarde vervangen of metnull de special vóór kosten weigeren.

Starter houdt alle12 niet-stackbare Tuna en negen overige uitkomsten; bij het wiki-conflict11vrije slots versus echte plaatsruimte
weigert de bestaande service ondeelbaar als de gehele inhoud niet past. Geen gefingeerde Tuna-stack of verloren overflow.
Wizard-unnoting en Seer-bankuitbreiding zijn onvolledig beschreven: geen nieuwe ongecontroleerde bankdienst uit die gaten verzonnen.
Normale Vengeance is volgens de bron niet verkrijgbaar inAnnihilation; geen extra kans bedacht. Chestadvies8/8/1 blijft gelabeld en standaardUIT.

## Claude: integratie en testclient

1. Cache/import/cfg/equip-stats/requirements, nieuwe34607-bron, echte ids.tsv/skins.tsv, NPC-spawns en één Nigel-icon;
   UI230/font645, UI232 en Reward Store. Geen plan-id als lokaal item gebruiken.
2. AV-/pathingcontracten voor starter, advanced-opening, thralls, khopesh, Chronicle en Home Teleport; echte POH-hotspots/style/exitportal;
   tutor/Brugsen-reclaim en scythe-wellbinding. Gebruik `TESTCLIENT-CHECKLIST.md` per systeem, met screenshots en geluid.
3. Gereserveerde `DropSources` view/warmUp/itemIndex brongevoelig laten rekenen, Normal1x en cachekeys behouden;
   Fire ruby/overige relevante Log-/Viewer-entries en oude trader-spawn/icon/help bijrelease. Geen wijziging aan goedgekeurde Viewer-opmaak.
4. Salarin205 heeft nog geen bewezen strike-only/fixed-hitbaseline. Algemene fire+2 is geen bewijs voor die specifieke bossroute.
   Claude moet die gedeelde incoming-hitroute corrigeren/nalopen, inclusief recoil/venom/thrall-uitzonderingen.
   `PawnExt` is in fase3 niet gewijzigd: Claude meldde daar al een GPT-B-mergeconflict.
5. Alle D1–D20/I10-routes echt testen: safebox versus effecten/dood, charges, graf/Perdu/GE/keys, valid/alt/offlinekiller/guard,
   koop/sellback, migratie éénmaal en pending geld. Twee laptopaccounts op hetzelfdeIP horen geen geldige-PK-bloodmoney te geven.
6. Bestaande gedeelde combat-packagechecks en normale boot na merge, daarna A9 in een opnieuw gestarte gewone client.

## Stand en grenzen

Geen cache-apply, Items.kt/items.yml/CollectionLog/DropSources/world_edits/cluewijzigingen, server-/fileserver-/clientbediening,
VPS/live/push/merge, spelersaves, game.yml, data-ge of live-logs door GPT-A in deze fase.
Code-defaults `rsps.dmm2.release=false` en `rsps.dmm2.chestRewards=false` zijn niet door GPT-A geactiveerd.
Koers blijft het bevestigde200BP:1DP naar boven. Oude emblems behouden; geen automatische cash/halos-/Vengeancebron verzonnen.
Alle eigen builds zijn klaar; geen eigen test/compiler/monitor/shell blijft actief. Idle Gradle-daemon en andermans servers/client zijn ongemoeid.
Eigen werk is gecommit; het reeds bestaande ongevolgde `tools/clue/` is onaangeroerd.
