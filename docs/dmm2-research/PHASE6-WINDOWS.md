# DMM2 fase 6: servervensters

Hervatting van het ongecommitte werk, 17 november 2026. Alleen de eigen worktree/branch. Geen cache-apply,
server/client-bediening, live, push of merge door GPT-A. Claude doet de cache- en visuele controle.

## Broncontract

Cache OpenRS2 2686, strikt gedecodeerde scripts met SHA256:
`phase5-sources/ui-scripts.json`, `phase5-sources/omnishop-scripts.json`,
`phase6-sources/scripts.json`, `phase6-sources/structs.json`.

* Safe Deposit Box: 1151/1155/1156, tien losse widgets, 5x2 op 39x38; 1158/1160/1161 inventory,
  4x7 op 42x36 met offset16/4. Stoneframe703/711, desktopvariant; bulkopties230:9 en230:11
  betekenen inventory en worn items. Opties Withdraw/Deposit-1/5/10/X/All en Examine komen uit de scripts.
* Reward Store: OMNISHOP819/806, scripts7142/7144/7147/7245/7248/7249/7254/7255/7256.
  Zeven producten uit DBrow929, table39. Productrows1355/3609/3610/3611/9572/9573/9574.
  De twaalf componenten per product reconstrueren5288 + icon/name uit7144; struct3712 geeft de
  border-sprites3524..3531 en inset4. Desktopknoppen7139/486 gebruiken913..920.
* **Valutacorrectie:** bronrow1206 zegt `Deadman Point` / `Deadman Points`, niet Blood money.
  Zie de bytes in `phase5-sources/shop-reward-rows.json`. Daarom blijft het goedgekeurde V3 gelden:
  Reward Store in bestaande DP, blood money alleen voor de drie armour-unlocks. Ook `table3` uit de
  fase6-kaart is een verschrijving: de bewezen source table is39.
* Wiki-pins blijven [Deadman Reward Store rev15394269](https://oldschool.runescape.wiki/w/Special:PermanentLink/15394269),
  [Financial Wizard rev15364116](https://oldschool.runescape.wiki/w/Special:PermanentLink/15364116),
  [Financial Seer rev15390113](https://oldschool.runescape.wiki/w/Special:PermanentLink/15390113),
  [Wizard-transcript rev14868949](https://oldschool.runescape.wiki/w/Special:PermanentLink/14868949).
  De bronafbeelding is `phase5-sources/reward-store.png`. Geen donor toegevoegd.

## Implementatie

`Dmm2SafeDepositUi` en `Dmm2RewardUi` installeren alleen wanneer beide eigen roots in `Dmm2Ids` staan.
De lokale interface-id komt altijd uit `Dmm2Ids.interfaceId(source)`; componentnummers zijn het expliciete
append-only importcontract. `Dmm2Boot` installeert beide adapters. `dmm2.plugin.kts` bindt de echte roots,
sluitcallbacks, alle opslag/product/inventoryslots en de koop/sellbackknoppen. Beide Nigel-openroutes
(Trade en de bestaande Reward Store-dialoog) gebruiken dezelfde adapter.

De SafeBox hergebruikt de bestaande tien-slotscontainer. X bewaart view-identiteit, refreshrevisie en de
exacte inventory-itemreferentie; close/reopen of vervangen van de stack maakt een oud antwoord onbruikbaar.
1/5/10/All loopt over bestaande fysieke exemplaren; iedere verplaatsing behoudt eigen charges.
Bulk worn-items roept ook de bestaande unequip-item/slot-hooks aan. Geen bankfallback of ontradeable bypass.

De Reward Store laat zeven producten zien, vult de twee saldovelden, geselecteerde prijs en onbeperkte voorraad,
en biedt 1/5/10/50 via kaartmenu en koopknoppen. Inventory Select opent de806-info met Value/Owned,
sellbackknoppen en Close Info. Notes gebruiken hun echte basisreward; gewijzigde items krijgen geen refund.
Refund is de volledige bronprijs van een nog fysiek aanwezige reward. Geconsumeerde permanente unlocks
zijn geen verkoopbare items. Alle mutaties blijven in `NigelRewardStore` achter de NPC/afstand/zichtbaarheid/
dood/sessionchecks van `Dmm2Interactions`; geen tweede eigen wallet of transactielogica.

De adapters bewaren views uitsluitend in niet-persistente, reset-on-death attributes. Close verwijdert de view
voordat de andere root wordt gesloten, zodat kruisende closecallbacks niet recursief de inventory heropenen.
Logout sluit beide services. Store- en SafeBoxgegevens blijven de bestaande persistente attributen/containers.

## Echte mappings en cacheoverdracht

Claude's actual UI/AV-ronde is bytegelijk overgenomen uit zijn expliciet aangewezen `ids.tsv`:
SHA256 `aede3e0617d4a9068038d30b636d63a25e3f2f1ff862e3207cc2ab59564462fb`.
**151 mappings, 163 bestandsregels.** De eerdere143 mappings zijn ongewijzigd; toegevoegd:
230→1204,819→1205,806→1206,721→17752,8056→17753,8057→17754,679→17755,spot116→3470.
232→1203 was al aanwezig. Dit vervangt de oude143-regels-pin als actuele bootfixture; historische fase5-bewijzen
zijn niet achteraf aangepast.

De reeds toegepaste ronde had uitsluitend17/3/55/14 componenten. **Na deze merge moet Claude de bestaande
OsrsInterfacePortTool opnieuw uitvoeren met `dmm2-ui --reuse`**, met zijn echte `cfg/dmm2/interfaces.tsv`.
De nieuwe append-contracten zijn:

|OSRS-root|Componenten|Servercontract|
|---|---:|---|
|230|41|title18, close30, stored31..40, bulk9/11|
|232|31|inventory3..30|
|819|193|title56, close66, products67..150 stride12, info151..155, buttons26..29|
|806|83|inventory14..41, info42..46, Close Info3, buttons8..11|

Exacte source-spritegroepen worden door de bestaande tool verzameld; geen handmatig lokale sprite-ids kiezen.
`--reuse` behoudt alle vier bestaande root-id's en oude sprites, en alloceert alleen ontbrekende sprites.
De nieuwe UI's verwachten deze extra widgets: de oude statische import is onvoldoende.

`PREFLIGHT-PHASE6.sh` doet uitsluitend PLAN op de twee bewaarde fysieke fase4-kopieën. De batchcode is dezelfde
als Claude gebruikt. Java-argv, output, exitcodes en voor/na-hashes staan onder `phase6-ui-output/`.
De oude kopieën hebben een andere allocatorstand dan Claude's actuele testcache; hun PLAN-ids mogen niet naar
`ids.tsv` worden gekopieerd. Het productiehergebruik moet Claude op zijn actuele cache nog plannen.

## Grenzen van dit bewijs

Dit is de desktop normal-view van OMNISHOP. De optionele expanded/mobile branches en hun hover/pressed
scripts zijn niet naar667 vertaald; de ongebonden view/help-controls11/12 worden verborgen. De zeven producten,
selectie-informatie, scrollbars, koop/refund en sluitroutes blijven bereikbaar. Claude vergelijkt de concrete layout,
fonts, klikken op icon/naam/achtergrond, scrollbars, Fixed/Resizable/Stretched en sluiten via X/Esc met OSRS.
Een groene native PLAN/unitrun is geen zichtbare UI- of live-PASS.

Geen onbewezen OSRS-refusalzin toegevoegd voor volle box, ontradeable, onvoldoende saldo of overflow:
de transactie weigert zonder mutatie en het venster ververst. Skulltekst is de bestaande bronquote.
De bron-UI's spelen2266 bij bulkdeposit1152, productselectie9771, koopknoppen9776 en inventoryops7251.
De volledige97-byte synthpayload is in bron2686 en de eigen667-cache identiek (SHA256
`50d12307c9213b6bce7998b29251792e258e865b5501114f724912256f8967d6`): daarom hergebruik van de bestaande
`Sfx.INTERFACE_SELECT1`, zonder cache-import of gegokt geluid. Bewijs: `phase6-sources/button-sound.json`.
Hover/pressed scripts blijven een visuele Claude-controle bij UI-pariteit.

Validatie en uiteindelijke commit-/testnummers staan in `PHASE6-VALIDATION.json` en de dossierregels.
