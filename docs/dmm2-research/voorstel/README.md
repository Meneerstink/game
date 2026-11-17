# VOORSTEL voor Claude — niet toegepast

Deze map bevat vijf losse patches. De echte `items.yml`, `Items.kt`, `world_edits.json`, `DropSources.kt` en
`CollectionLogPages.kt` zijn door GPT-A **niet gewijzigd**. `git apply --check` controleert alleen de toepasbaarheid van de
tekst op deze worktree, en is geen import/runtimegoedkeuring.

|Patch|Inhoud|Gebruik|
|---|---|---|
|`01-items.yml.PLAN-ONLY.patch`|73 native metadatarecords (51 ouders +22 notes), source-bonussen/eisen/examines, equipment; exacte wiki-override voor tien corrupted states|Uitsluitend review; daarna opnieuw genereren met de echte apply-IDs en native appearance-ranks|
|`02-Items.kt.PLAN-ONLY.patch`|Benoemde DMM2-constanten met bron-id in de naam; geen count-visuals als gameplay-items|Uitsluitend review; geen tijdelijke PLAN-IDs in de server overnemen|
|`03-world_edits.json.PLAN-ONLY.patch`|Nigel/Wizard/Seer en verwijderen oude Breach Trader bij de release|Opnieuw genereren met echte NPC-IDs; GE-collision/rotatie nog in de testclient controleren|
|`04-DropSources.kt.patch`|Per-bron Fortuity-rate, aparte indexkeys voor bracket/bonus, snapshot bij warm-up, geen nieuwe retired Archaic-emblembron na release|Claude reviewt/samenvoegt en draait zijn bestaande relevante tests; vieweropmaak blijft buiten deze patch|
|`05-CollectionLogPages.kt.patch`|Fire-ruby-page uit echte Infernal-Mage-bronsleutels; retired emblem uit de actuele breach-beloningen bij release|Bestaande historische obtained-data niet wissen; registratie gebruikt de bestaande generieke NPC-droproute|

De huidige PLAN-baseline bevat item24634..24715. De actuele tekstmetadata in deze worktree bevat inmiddels ToA-items op
veel van die plekken: **67 botsende metadata-IDs** staan precies in `metadata-review.json`. De `.PLAN-ONLY.patch`-bestanden
zijn daarom bewust geen apply-instructie. Die tegenstrijdigheid is niet opgelost door bestaande ToA-items te hernummeren.
De native importer moet op Claude's actuele gecombineerde cache/ledger opnieuw alloceren; ontbrekende cache/metadata-
overeenstemming eerst herstellen. De generator in echte modus weigert ieder naamconflict op een bestaande metadata-ID.

Na de echte cache-apply, vanaf de geïntegreerde worktree:

```bash
python docs/dmm2-research/make_proposals.py \
  --ids data/cfg/dmm2/ids.tsv \
  --generated-yml build/dmm2-apply-YYYYMMDD-HHMMSS/items.generated.yml \
  --out docs/dmm2-research/voorstel-actual
python docs/dmm2-research/make_logic_proposals.py
```

Dit schrijft alleen nieuwe tekstpatches; het past niets toe. De eerste opdracht gebruikt de door de native tool berekende
werkelijke appearance-ranks. De huidige PLAN-patch corrigeert de `--yml-only`-rank met het aantal eerder geplande wearable
items: die staan tijdens een plan immers nog niet in de cache. Dat voorlopige rankbewijs is geen model-PASS.

`metadata-review.json` bewaart alle cache→wiki-statverschillen en SHA256 van de gereserveerde inputbestanden. Voorbeeld:
corrupted voidwaker cache2686 stab70/slash80/strength68 versus gepinde wiki56/64/64. Alle veertien bonusvelden worden exact
per staat gelezen, geen algemene80%-formule. Vereisten/snelheid komen uit de echte bronparams; darkbow7ticks volgt het eerder
bewezen cacheadvies. Wapentypes/audio volgen de bestaande bewezen base-metadata, scythe type22 volgt `WeaponType.SCYTHE`.
Scythe-audio zonder bewijs blijft native-1 en expliciet een Claude-AV-punt. Native clientparams/pose/combatstijl moeten bij de
cache/clientintegratie eveneens gecontroleerd worden; deze metadatareview maakt geen extra clientassets.

Plaatsing: Nigel3152,3512 is gepind op wiki Nigel rev15401296,2026-11-12T18:13:26Z (mapveld); dit vervangt geen bestaand NPC-schema. Wizard en
Seer staan in OSRS niet samen in de GE. Hun voorgestelde GE-tegels3150,3512 en3150,3514 volgen de expliciete eigenaarsverplaatsing,
met zuidrotatie als lokaal voorstel; niet als OSRS-spawn gepresenteerd. De patch behoudt hun cacheopties. De oude Trader staat in
deze editgeschiedenis op3168,3497; verwijderen alleen tegelijk met het definitieve migratie-/winkelreleasebesluit.

Bronnen: gepinde `wiki-manifest.json`, fase3-bronmanifest en cache2686-decodes; geen donorcode. De generieke Fire-ruby-logkeuze
is een lokale invulling van V11, geen beweerde OSRS-Collection-Log-categorie. Normale vengeance krijgt hier geen verzonnen drop.

Aanvullende controle vond een fout in de eerdere testfixture/checklist: Blood money13307 is **onruilbaar** (wiki15380671 en
native cache), net als de geladen corrupted scythe/shadow. De safebox-service weigerde al onruilbare items; de fixture en
`TESTCLIENT-CHECKLIST.md` zijn gecorrigeerd. De elfmuntenproef gebruikt nu echte995coins. De nieuwe Blood-money-relogproef
moet vijfmaal nul transfers laten zien. Er is geen productiewijziging aan tradeability/dood verzonnen.

Nog door Claude te bewijzen: UI230/font645 en echte callbacks, Nigel-minimap/worldmapicoon, ancient34607 uit een nieuwere
gepinde cache, volledige gedragen modellen/AV/Wisp-sidecar/POH en de bestaande bronvragen in `PHASE3-SOURCES.md`.
