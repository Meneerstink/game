# DMM2 fase 4(b): benoemde PLANs bewezen

De benoemde native batches zijn daadwerkelijk uitgevoerd met de actuele zes Kotlin-toolbronnen, zonder `--apply`, op
twee fysieke kopieën onder `build/dmm2/phase4-plan`. Er is geen in-memory batchinjectie gebruikt. De broncache is OpenRS2 2686.
De doelkopieën en de geïsoleerde ledger komen uit de eerder gehashte fase3-snapshot; ze vertegenwoordigen die baseline,
geen verse live-/distributiecache. De eigen journal bleef leeg.

|Native batch|Resultaat|Vergelijking met fase3-bundel|
|---|---|---|
|`dmm2-items`|82 itemrecords uit 51 ouders; notes/counts automatisch|Alle `PLAN item`-regels en CENSUS exact gelijk|
|`dmm2-npcs`|6 klassieke NPCs|Alle `PLAN npc`-regels exact gelijk aan `bundle-npc-classic`|
|`dmm2-seqs`|22 expliciete sequences|Alle `PLAN seq`-regels exact gelijk|
|`dmm2-spots`|12 spotanims plus dependencies|Alle `PLAN spotanim`-regels exact gelijk|
|`dmm2-locs`|3 rugdelen|Alle `PLAN loc`-regels exact gelijk|
|Interface `dmm2`|UI232 naar voorlopig1185, 3 componenten|Zelfde interface als het aparte fase3-plan|
|`skeletal-npc dmm2-wisp`|Wisp15571, owner voorlopig15081, seq10230/10233|Zelfde skeletal-plan, inclusief model/seq-allocaties|
|Wisp-sidecar `cache plan`|Echte rig2138/clips212:0 en210:0; 5 mutations|Zelfde brondata; owner/seqs uit déze Wisp-uitvoer afgeleid|

Bewijs staat in `phase4-output/*.txt`, exacte Java-aanroepen met exitcodes in `*-command.json`, vergelijking in
`comparison.json`. `validation.json`: acht geslaagde native plannen, alle allocaties gelijk, 78 cachebestanden
bytegelijk vóór/na, nul journalentries. De drie eigen JUnit-batchtests zijn ook groen (`batch-tests.txt`).

Herhaling: `powershell -File docs/dmm2-research/RUN-PHASE4-PLANS.ps1` maakt een nieuwe fase4-kopie uit de intacte fase3-snapshot.
Bestaat deze fase4-kopie al, gebruik `-Resume`: eerst snapshotcontrole, alleen nog ontbrekende/mislukte stappen.
Beide routes gaan via het centrale Gradle-bouwslot en `--priority low`. Alle tools krijgen `-Drsps.root` en `-Drsps.data`
voor de sandbox. Er is geen apply-route in dit plandraaiboek.

De PLANs per tool zien dezelfde ongewijzigde snapshot. Hun lokale namespaces mogen daarom onderling overlappen:
Wisp15081 is bijvoorbeeld ook Wizard15081 in het aparte klassieke NPC-plan; seq11810 is16222 in de expliciete seq-batch
en16221 in het afzonderlijke spotplan. Dit zijn **geen gecombineerde, gereserveerde runtime-IDs**. Een echte opeenvolgende
apply gebruikt steeds de dan actuele cache/ledger, hergebruikt dependencies en wijkt daardoor legitiem af. De apply-generator
weigert zulke tegenstrijdige mappings en accepteert geen PLAN-uitvoer.

De native tools melden hun bestaande conversieverliezen (`DROPPED`): onder meer lighting, onbekende opcodes, stretches en
fallback-bakefout bij Wisp. Groen PLAN bewijst geen gedragen model/animatie/geluid. Claude vergelijkt die in de testclient.
UI230/font645, Nigel-iconkandidaten1524/1681 en ancient cape34607 zijn expliciet buiten deze geslaagde ronde gebleven.
