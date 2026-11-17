# Fase 5 ? echte DMM2-data en serverintegratie

De cachetransacties zijn door Claude uitgevoerd. GPT-A past uitsluitend de tekstdata/servercode toe; geen cache, server, client of live wordt bediend.

## Data

`data/cfg/dmm2/ids.tsv` is bytegelijk aan Claude's gepubliceerde bestand (143 mappings; SHA256 `e7efbb117ec161980fe20e20d232da8e8c92ecd8c34fdd71d71795866084c7d4`). De native `items.generated.yml` is als bronkopie opgenomen onder `phase5-output/`, SHA256 `4f6fe3f285456477963acce10a289e109f31766253de526e73f102f0520182c9`.

De opnieuw gegenereerde voorstellen in `voorstel-actual/` zijn toegepast op items.yml, Items.kt en world_edits.json: 73 native metadataregels, 77 benoemde itemconstanten, Nigel 15316, Wizard 15314 en Seer 15315. Tien corrupted states gebruiken exact de eerder gepinde wiki-bonussen; geen algemene schaalfactor. Native appearance-ranks, tradeability, eisen en examines blijven behouden. De vier bestaande trinkets worden hergebruikt en de negen blood-money-countvisuals zijn geen gameplayconstanten.

`phase5-output/data-validation.json` bewijst geen dubbele metadata-ids, 73/73 aanwezige records, 10/10 exacte corrupted-bonussets, behoud van native appearance/tradeability en precies ??n spawn per NPC. Nigel 3152/3512 volgt wiki rev15401296; Wizard 3150/3512 en Seer3150/3514 zijn de gevraagde lokale GE-verplaatsing, rotatie4 is lokaal advies. Collision en vormgeving controleert Claude. De oude Breach Trader wordt in de world-editgeschiedenis verwijderd; neem die verwijdering pas in de definitieve DMM2-release op, tegelijk met `rsps.dmm2.release=true` en het migratie-/assortimentsbesluit.

## Boot en regressie

In uitvoering: zestien exacte cosmetic-stateparen in skins.tsv, expliciet laden van ids.tsv v??r bootregistratie, unitproeven met echte data, bestaande gerichte combat/drops/quest-tab-tests. Nog geen test-PASS.

## Grenzen

UI230/font645, ancient cape34607 en Nigel's kaarticoon hebben geen mapping in deze ronde; er is geen id voor verzonnen. Asset-/callbackcontracten voor UI, AV, thralls, Home/POH, Chronicle en tutorreclaim blijven de eerder beschreven Claude-integratie. Khopesh-timing, Salarin en overige gekozen bronadviezen blijven in PHASE3-SOURCES.md. Dit document is geen runtime/live/AF-bewijs.
