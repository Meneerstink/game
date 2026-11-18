# DMM2 fase 7 - ChatGPT-A

Hervat op 18 november met de niet-gecommitte voorgangersdiff; die is behouden en afgemaakt.
Werkbranch `gpt-a/dmm-pakket-2`. Dit verslag beoordeelt servercode en bronbewijs. DMM2 is nog niet
volledig live-klaar: Chronicle en de volledige advanced-opening missen een bewezen presentatiecontract.

## Stand per kaartpunt

|Punt|Status|Resultaat en bewijs|
|---|---|---|
|7.0|OK|Synchronisatie in f6cb45131; a0152e13f/N628/staff aanwezig. Later door Claude gepubliceerde Wisp-mapping behouden:170regels, bytegelijk aan integratiebranch.|
|7.1|OK met bronvoorbehoud|61b9042f9: smalle extra-seqroute, bestaande rig/skin/base/modellen hergebruikt. Claude keurde de2index-appends goed en paste tx-20261117-205101 toe. Dossier22:52/22:5x bewijst4tickattack + correct beeld. Dmm2BootTests dekken mapping aanwezig/afwezig. MAGIC01 is een gelabeld advies, geen bewezen OSRS-DMM-variant.|
|7.2|ONZEKER|da1e784ec: nul-charge/Wilderness weigeren voor AV-gate, zonderchargeverlies. Vier OSRS-video's ondersteunen standaardteleport als inferentie, maar montage/versnelling verhinderen exacte tickmeting. chronicleAppearance blijft null.|
|7.3|ONZEKER voor runtime|Cache2686 bewijst loc49564/49565 + objectseq10426. Benoemde Fx/Loc-batches en dependencyguard gebouwd; geblokkeerd veld geeft bronmelding voor AV-gate. Spelerpose/plaatsing/modelwissel/serverduur ontbreken; advancedAppearance blijft null. PLAN en contracttests staan in PHASE7-ADVANCED-PLAN.md.|
|7.4|Zie uitslag hieronder|D5-correctie voor BloodMoney; migratiecrashproef en echte armour/Wisp-boottests toegevoegd. De volledige servermatrix en haar bronbeperkingen staan in checklist15.|
|7.5|Uitslag wordt na de lopende run ingevuld|Normale installDist +3core/23plugin-klassen, via rgradle met expliciete execution/discoveryfilters en alle bindingscripts.|
|7.6|Dit verslag|Commits, bronkeuzes, resterende testclientronde en PREFLIGHT hieronder; ook dossierregels per afgerond punt.|

## 7.4 - servermatrix

|Onderdeel|Bron / besluit|Bewijs en grens|
|---|---|---|
|a Valuta/migratie|V1/KOERS:200BP ->1DP naarboven; V7:1bloodmoney per VALID PK; D5/D6/D10/D11.|Dmm2CurrencyTests en Dmm2BreakTests: alle ongeldige verdicts/self/release-off, ownedmunt op slachtoffertegel, D5-timers100/500 of100/6100; pending/overflow, retry/relog. Nieuwe crashproef gebruikt echte writeAtomically met synthetische JSON, tijdenswrite en directnaatomiccommit;5reloads eindigen op52DP uit50+ceil(201/200), volledige audit, geen dubbelecredit. De timer bepaalt geen publieke toegankelijkheid van een onruilbaar item.|
|b Nigel/rewards|Deadman armour1bloodmoney per permanentunlock, gratisreclaim; V3 zeven rewards in DP, gelijke sellback.|Dmm2Currency/Break/Boot/Window/NigelRewardStoreTests: alle3echtearmourIDs, geenmunt/vol/dubbelunlock/reclaim; D2/D4/unlock blijft. Alle7prijsloops/refunds, notes/0/MAX/stale/close/relog; totaalwaarde blijft gelijk.|
|c Corrupted8|Wiki/gameval/sourcepins fase1/5; basiscallbacks en presentatie hergebruikt.|Dmm2WeaponPresentationTests:5echte specialaliases,50/50/50/55/55energie en teweinigenergie; tbow/scythe/shadow geen fictievespecial. ItemServices/Cosmetics/Boot dekken charges/depletion/PvP en16skin-stateparen. Claude bewees alle5specials via echte884:4-balk op17nov13:1x; bot+::spec blijft ongeschikt. Werkelijke audio/modelroutes: checklist13/15.|
|d Trinkets|Fortuity10M/+1alleenbossen; D14 voor burden/kits; V6 voor OSRS-families.|ItemServices/SourcePolicy/Break:10M heen/terug/MAX, Avarice notes/bones/threshold/clamp/vol, vengeance gedeeldecooldown, carriedvsbox, alle6trinkets door echte PvM-deathexecutor met12totaal en5replays. FairyRingTravelLog/SpiritTreeMenu/N503PetDeath zijn geselecteerde bestaande regressies. Avarice GE-stackwaardering blijft lokaal advies; advanced AV onvolledig.|
|e Starter/Chronicle|V2, D9 blijft apart; wiki/Transcript:Chronicle.|DeadmanStarterPack/StarterPackInteractions/StarterWeaponCombat/Break: migratie/grant/reclaim zonderdubbelecash; PvM-only; bow2000/staff500; accountchargesmax1000, card13658 deelt teller, verlies/destroygeenreset, identity/spend/Wilderness/0charges. Chronicle-AV is nog dicht.11slots-vs21inhoud is het expliciete bronconflict in dossier18:5x; veilig weigeren blijft gekozen advies.|
|f Fire/POH/Home|Fire21RC/3ticks/125XP/recept; rug28Construction/5ticks/283XP; eigen Wilderness-housebesluiten.|ItemServices/Break/NigelHouseRewards/NigelRewardStore testen voorwaarden/completion/interrupt/full/relog/laatsteportal/unlocks en thuistijl. HomeTeleportDeadmanGateTests zijn opgenomen; ontbrekende-cacheomgeving apart vermeld. Claude's bestaande native721/116-Firecorrectie is behouden. Letterlijke Fire-refusal en audiohoogte blijven als bronbeperking beschreven.|

## Regressie en native PLAN

De actuele XML-totalen, timestamps en source-snapshotvergelijking komen in `PHASE7-VALIDATION.json`.
Geen lege/skipped suites als groen tellen. Bekende ontbrekende-cachefouten blijven echte failures in
die totalen en worden afzonderlijk benoemd. De tussentijdse oude150tests/7failures onder
`regression-first` zijn geen bewijs voor deze laatste crash-/dependencywijzigingen.

Native PLAN: zie `PHASE7-ADVANCED-PLAN.md` en `phase7-output/advanced-final/`.
Beide targets zijn fysieke losse kopieen, geen symlinks/hardlinks naar originele cachebestanden.
Eerste onvolledige locproef met anim=-1 is als historie bewaard; definitief PLAN vereist de voorafgaande
voorlopige seqmapping in uitsluitend de aparte PLAN-ledger. Geen apply door GPT-A.

## Bronnen en gekozen adviezen

OSRS Wiki API met revisies/datum, cache2686 en RuneLite gameval; geen donorcode toegevoegd.
Alle pogingen/URLs/tijdstempels en ruwe bronresponses staan in `PHASE7-SOURCES.md` en `phase7-sources/`.
Belangrijke pins: Chronicle15366319 (2026-10-05), Transcript14828423 (2024-12-28), advanced15355050
(2026-09-24), File15123705 (2026-02-08), visuele hotfix15121071 (2026-02-05), undead15298165
(2026-08-13), Whisperer15403864 (2026-11-16).

- Chronicle: advies geen onbewezen AV/timing activeren; vier bewerkte video's leveren geen exacte ticks.
- Advanced: bewezen scenery voorbereiden, playerseq niet met objectseq verwisselen; volledige opening nog uit.
- Wisp: MAGIC01 blijft het gekozen gameval/cacheadvies; Claude heeft ons renderpad bewezen.
- Undead-refusals: transcript ontbreekt; alle9784client-scriptgroepen zonderleesfouten doorzocht, geen meldingbron.
  Advies bestaande stilleweigering behouden. Geen geleende spreukentekst of verzonnen melding.
- Eerdere beleidskeuzes blijven:200:1DP,10MFortuity, AvariceGE-stackwaardering, conservatieve starter-slotweigering.
  Dit zijn waar gemarkeerd lokale besluiten/adviezen, geen nieuwe OSRS-feiten.

## PREFLIGHT voor Claude

1. Wisp10241 is al toegepast, niet opnieuw importeren. Historie/goedkeuring in PHASE7-WISP.md.
2. Advanced: bestaande OsrsFxImportTool `dmm2-advanced-loot` (seq10426), daarna bestaande
   OsrsLocImportTool `dmm2-advanced-loot` (loc49564/49565). Exacte uitvoerbare opdracht voor kopie-PLAN
   en native argv staan in PHASE7-ADVANCED-PLAN.md/advanced-final. Echte import/mappings blijven Claude.
3. Chronicle: geen nieuwe batch; eerst exact AV/timingbewijs. Geen voorlopig lokaal id in ids.tsv.

## VOOR CLAUDE TESTCLIENT

Volg TESTCLIENT-CHECKLIST.md blok15 en de bijbehorende blokken1-14; voorkom herhaling van al bewezen
Wisp/Armageddon-beeld en5specials als het betreffende pad niet verandert.

- Na mergebundel/releasevlag: legacy-loginmigratie eenmaal, relog, valide PK1ownedbloodmoney,
  alt/same-IP/practice/self0; D5-vervaltijden en geen verlies/dupe. Geen saves handmatig aanpassen.
- Alle3armourunlocks, gratisreclaim na dood, volleinventory, safecontents buiten dood/effecten;
  Reward Store0/MAX/stale/X/close/relog en Fixed/Resizable/Stretched.
- Chronicle0charges geeft transcripttekst en Wilderness weigert zonderchargeverlies. Geladen teleport
  en vrije advanced-opening blijven niet spelerklaar; verwacht daar geen PASS zolang broncontract ontbreekt.
  Advanced bij blokkade geeft wel de exacte obstructiemelding en behoudt het item.
- Avarice echte Value-optie2; fairies reizen, vengeance,10MFortuity; alle8wapens/charges/audiofamilies
  vergelijken waar nog geen beeld-/geluidsbewijs bestaat. Fire-effect en POH-rug/portal/theme/relog.

## Werkplek en overdracht

Alleen eigen worktree gewijzigd; tools/clue/ onaangeroerd. Geen cacheapply, server-/clientbediening,
live/VPS/push, game.yml/geheimen, spelersaves of data/ge. Release-/chestvlaggen niet aangezet.
Doctor.ps1 en Rsps-Lint.ps1 ontbreken in deze worktree; beschikbare gerichte checks staan in de uitslag.
Claude reviewt/merget en beheert testclient/live. Geen AF/runtime/live-PASS voor de hele klus.

Commitlijst en eindtotalen worden na de laatste controles ingevuld.
