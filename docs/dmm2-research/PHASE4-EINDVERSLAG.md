# DMM2 fase 4 — overdracht ChatGPT-A

Fase4(a)-(d) is voorbereid/gevalideerd in de eigen branch `gpt-a/dmm-pakket-2`. Dit is een server/tool-WIP voor Claude,
geen testclient-/live-/spelerklaarclaim. GPT-A heeft geen cache-apply uitgevoerd en geen verboden bestanden gewijzigd.

|Deel|Resultaat en bestanden|
|---|---|
|a|Benoemde batches in de bestaande OsrsItem/Npc/Fx/Loc/Pet-importers; geïsoleerde OsrsInterfacePortTool-route voor uitsluitend UI232. Zie `PHASE4-BATCHES.md`. Commit `dbc277f04`.|
|b|Acht native PLANs op twee cachekopieën: alle allocaties gelijk aan fase3,78 bestanden bytegelijk, journal leeg. Exacte uitvoer/Javaargs/census in `phase4-output`; runner en vergelijkingsscript aanwezig. Commit `d67eb34f2`.|
|c|`DMM2-APPLY.sh`, `phase4-apply.gradle` en `dmm2_ids_from_apply.py`: sequentiële bestaande transacties in één bouwslot, stop bij fouten, werkelijke Wisp-owner/seqs en atomaire runtime-idpublicatie. Script niet uitgevoerd door GPT-A. Commit `0ddc41f34`.|
|d|Vijf losse voorstelpatches in `voorstel/`, geen daarvan toegepast. Drie met voorlopigeIDs heten expliciet PLAN-ONLY. Generator voor echte apply-IDs/native metadata, aparte logic-generator, README met bron-/risicokeuzes. Deze overdrachtscommit bevat ook de gecorrigeerde eigen testfixture/checklist.|

Validatie in deze ronde: drie eigen JUnit-batchcontracttests,17 eigen Dmm2BreakTests en zes eigen Python-handofftests groen
(26 gericht). De actuele zes importerbronnen zijn gecompileerd; beide gereserveerde logic-voorstellen zijn uitsluitend als
kopie onder `build/dmm2/proposal-sources` gecompileerd. De geïsoleerde compiler kreeg de ongewijzigde DropEntry/DropTable/
DropTableFactory-bronnen mee voor Kotlin-modulegrenzen; daarvoor is geen productieworkaround toegevoegd.
Alle vijf patches slagen voor `git apply --check`; Pythoncompile en `bash -n` groen. Alle builds waren één tegelijk onder
het centrale slot met `--priority low`, uitsluitend eigen testklassen. Zie `PHASE4-VALIDATION.json` voor bewijs/hashes.

Belangrijkste reviewbevindingen:

- De oude cachePLAN-baseline en de tekstmetadata zijn niet dezelfde generatie:67 voorlopige itemIDs botsen met ToA-entries.
  Daarom de PLAN-ONLY-patches niet toepassen. Genereer na Claude's echte apply opnieuw met de actuele ids.tsv en
  `items.generated.yml`. Echte modus weigert naamconflicten. ToA/clues zijn niet gewijzigd/hernummerd.
- Native cache2686-bonussen verschillen bij diverse corrupted weapons van de gepinde wiki. De metadata-voorstellen lezen
  alle14 bonusvelden per staat exact uit de bron;28 reviewregels (inclusief twee expliciete scythe-audiogaten) staan in
  `voorstel/metadata-review.json`. Geen algemene80%-formule en geen zelfbedachte AV.
- Broncorrectie van de eerdere fase3-fixtureclaim: Blood money is onruilbaar (wiki15380671,2026-10-23T22:06:09Z +cache2686).
  De geladen corrupted scythe/shadow ook. De bestaande safebox-service weigerde al correct; de fixture en checklist waren
  fout en zijn hersteld. Elfmuntenproef gebruikt nu995coins; geladen scythe weigert zonder chargeverlies, lege vorm mag erin;
  vijf herhaalde Blood-money-relogpogingen geven nul transfers. Geen productiebeleid voor trade/death gewijzigd.

Bronnen/donors: gepinde OSRS Wiki-revisies in `wiki-manifest.json` en `phase3-sources/manifest.json`, RuneLite-gameval en
OpenRS2-cache2686. Nigel's echte GE-mapveld3152,3512 is wiki15401296,2026-11-12T18:13:26Z. Wizard/Seer in de GE is de expliciete
eigenaarsverplaatsing; hun voorgestelde buurtegels/rotatie zijn als lokaal advies gelabeld. Geen donorcode gebruikt.

Claude doet bij integratie: actuele cache/ledgercensus en apply; echte metadata/IDs opnieuw genereren; NPC-spawns/collision,
Nigel-worldmap/minimapicoon, echte Safe/RewardUI inclusief callbacks en font, gedragen modellen/beide geslachten/AV,
Wisp-sidecarafspelen, POH/tutor/Chronicle/well-bindingen, DropSources/CollectionLog-voorstellen reviewen en bestaande relevante
tests draaien. Vervolgens de volledige `TESTCLIENT-CHECKLIST.md`, inclusief skull/relog/dood/I10, charged/untradeable-weigering,
allewapen-/skin-/chargestates, Nigel-DP/bloodmoney en migratie-idempotentie. Live/releasevlaggen uitsluitend door Claude.

Open bron-/assetpunten blijven expliciet: UI230/font645 (UI232 is alleen de inventory-zijbalk), Nigel-iconsemantiek,
ancientDeadmancape34607 uit een nieuwere gepinde cache, exact khopeshinitialoffset/AV, scytheextra-targetprioriteit,
starter11slots-versus-inhoud, Wizard-unnoting/Seer-bankuitbreiding en Salarin-baseline. De bestaande gekozen adviezen staan in
`PHASE3-SOURCES.md`; deze ronde heeft daarvoor geen OSRS-feiten of clientassets verzonnen.

Geen gitpush/merge/live/server/client/VPS-acties door GPT-A. Gereserveerde Items/items.yml/DropSources/CollectionLog/world_edits
zijn byte-ongewijzigd; alleen losse tekstpatches. Het bestaande ongevolgde `tools/clue/` is onaangeroerd. Eigen builds/tests
afgerond, geen eigen monitor/achtergrondshell nodig; idle Gradle-daemons en andermans processen blijven volgens de regels staan.
