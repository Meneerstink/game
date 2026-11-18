# PREFLIGHT 7.3: advanced weaponry scenery

Bronnen: cache2686, RuneLite `ObjectID1.java` en `AnimationID.java`, Wiki
Trinket of advanced weaponry rev15355050 (2026-09-24), de openingsvideo op File-revisie15123705
(2026-02-08) en de zichtbaarheidshotfix rev15121071 (2026-02-05).
Decoderuitvoer met bronbytes/SHA256: `phase7-sources/advanced-loot-sources.json`.

|Bestaande tool / batch|Bron-IDs|Afhankelijkheid|
|---|---|---|
|OsrsFxImportTool / dmm2-advanced-loot|seq10426|classic objectanimatie, geen playerseq|
|OsrsLocImportTool / dmm2-advanced-loot|loc49564, loc49565|asset-map `seq:10426` verplicht|

De loc-tool weigert deze batch zonder de seqmapping. Een losse object-PLAN met `anim=-1`, zoals de
eerste onderbroken proef, is onvoldoende en geldt niet als definitieve geslaagde preflight.
Het voorafgaande seq-PLAN wijzigt geen cache en publiceert dus ook geen ledgerregel. Alleen in de
afzonderlijke PLAN-ledger wordt zijn voorlopige allocatie ingevoegd als
`status: PLAN_ONLY_DEPENDENCY_NOT_APPLIED`. Dit is een declaratieve afhankelijkheidsproef,
geen bewijs dat de seq al in die cache staat. De echte Claude-ronde moet de seq eerst toepassen
en dan de locs opnieuw plannen op de werkelijk ontstane mapping.

## Reproduceerbare PLAN-opdracht

De reeds voorbereide root `build/dmm2/phase7-advanced-plan` bevat twee fysieke kopieen van de
testcache van 17 november 22:12, een aparte openbare ledger, petsroster en lege journal.
`phase7-output/advanced-final/before.json` bevat SHA256 van alle 80 cachebestanden.
`inputs.json` bewaart de ledgerhash. Gebruik voor herhaling een verse kopie onder `build/dmm2`:
de runner weigert een ledger waarin de PLAN-afhankelijkheid al is toegevoegd.

```powershell
# Vanuit C:/RSPS/work/gpt-a; na afloop van een andere eigen Gradle-run.
$env:RGRADLE_LOCAL='1'
try {
  & C:/RSPS/tools/remote/rgradle.cmd dmm2Phase7WispPlan `
    -Pdmm2AdvancedOnly=true -Pdmm2PlanBox=build/dmm2/phase7-advanced-plan `
    -I docs/dmm2-research/phase7-wisp-plan.gradle --offline --priority low --no-parallel
} finally { Remove-Item Env:RGRADLE_LOCAL }
```

De bestaande harness compileert de actuele tools en voert vervolgens deze native opdrachten uit,
met `-Drsps.root=<kopieroot>` en `-Drsps.data=<kopieroot>/game/game/data`:

```text
org.junit.runner.JUnitCore gg.rsmod.game.tools.importer.Dmm2ImportBatchTests
gg.rsmod.game.tools.importer.OsrsFxImportTool dmm2-advanced-loot
gg.rsmod.game.tools.importer.OsrsLocImportTool dmm2-advanced-loot
```

Volledige argv/exitcodes en CENSUS/OUD->NIEUW-uitvoer worden bewaard in
`phase7-output/advanced-final/`. Definitieve resultaten en bytecontrole worden hier na de run toegevoegd.
De historische uitvoer direct onder `phase7-output/advanced-*` hoort bij de eerdere onvolledige proef.

## Runtimegrens en advies

De spelerpose, sceneryplaatsing, gekozen-wapenmodelwissel en serverduur zijn nog niet bewezen.
De clientduur van seq10426 (150 cycli) bewijst geen serververtraging.
`AdvancedAppearance` ondersteunt nu alleen playeranimation/ticks; objectanim10426 daarin zetten
zou een foutieve namespace gebruiken. Daarom blijft het volledige openingseffect ONZEKER en
uitgeschakeld. De bewezen obstructiemelding is bereikbaar zonder dat contract; het item blijft behouden.

PREFLIGHT voor Claude: bovenstaande twee benoemde batches, in deze volgorde. Na zijn echte import
de werkelijke ids publiceren via Dmm2Ids.seq(10426)/Dmm2Ids.loc(49564/49565), nooit de PLAN-allocaties.
Geen Chronicle-batch toevoegen zolang de precieze player-AV niet bewezen is. Wisp10241 is al door
Claude toegepast (tx-20261117-205101) en hoeft niet opnieuw geimporteerd te worden.
