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
Bulk worn-items controleert de bestaande `canUnequipSlot`-guard voordat iets verplaatst wordt en roept bij
volledige verwijdering ook de bestaande unequip-item/slot-hooks aan. Een geblokkeerd gedragen item behoudt
zijn plaats, charges en totaal; gedeeltelijk opgeslagen ammo blijft uitgerust. Geen bankfallback of untradeable bypass.

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

Claude heeft de nieuwe append-contracten op 17 november om 15:45 al toegepast op de testcache:
`tx-20261117-144453`, 622 writes, `VERIFY_OK` (dossier 15:4x, integratierapportcommit `d1d87fbcb`).
De mislukte ronde 14:00 is teruggedraaid; de component-id-correctie uit `6b5be3536` zit in deze geslaagde import.
De onderstaande aantallen komen uit die overdracht. Dit is Claude's cachebewijs; GPT-A heeft geen apply gedaan.
De hervatting na16:00 verandert geen importercode en vereist daarvoor geen nieuwe UI-import.
De append-contracten zijn:

|OSRS-root|Componenten|Servercontract|
|---|---:|---|
|230|41|title18, close30, stored31..40, bulk9/11|
|232|31|inventory3..30|
|819|211|title56, close66, products67..150 stride12, info151..155, buttons26..29|
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

## Hervatting na 16:00 en eigen regressie

Claude's twee fixturecorrecties uit `989df32ac` zijn op deze branch overgenomen: MockK-verificatie direct op
het pluginsobject en de echte scythe 8056-mapping. De productiecorrecties native Fire 721/116 en de
basiswapencue van Volatile uit `6b5be3536` blijven behouden.

`RUN-PHASE6-TESTS.ps1` gebruikt nu de verplichte `C:/RSPS/tools/remote/rgradle.cmd`-route en normale
modulecompilatie. `phase6-regression.gradle` beperkt alleen de scripts tot de negen gewijzigde DMM2-bindings
en isoleert testklassen; er worden geen compiledependencies uitgeschakeld en geen oude phase5-classfiles
voorgeschoven. De eigen gerichte klassen zijn UiPlan, Window, Interaction, SafeDepositBox, NigelRewardStore,
Boot, Break en WeaponPresentation. De verse aantallen, XML-hashes en uitvoerstatus volgen in
`PHASE6-VALIDATION.json`; eerdere Claude-resultaten worden daarin apart gelabeld.

De eerste productieaanvulling van deze hervatting is de bestaande unequip-slotbeveiliging op worn-bulkdeposit.
Bron voor die integratie is `EquipAction.unequipInner` / `PluginRepository.canUnequipSlot`; geen nieuwe
OSRS-mechaniek, getal, tekst of donor toegevoegd. Het nieuwe breekgeval controleert de weigering met een
charged item, geen unequipcallbacks bij weigering, daarna normale overdracht zodra de guard toestaat.

Claude controleert nog zichtbaar in de echte client: alle knoppen en item-/naam-/kaartklikken, skull,
X-invoer na sluiten/heropenen, gedeelde Wizard/Seer-inhoud, volle inventory, charges en geblokkeerde uitrusting,
Nigel Trade en dialoog, zeven koop/refundprijzen in DP, sluiten/X/Esc, scrollen en Fixed/Resizable/Stretched.
Het Reward Store-bronmenu is 1/5/10/50; het SafeBox-menu is 1/5/10/X/All. Die verschillen komen uit de scripts.
De testclientstappen staan in `TESTCLIENT-CHECKLIST.md` blok 14. Open visuele bronpariteit blijft bij Claude;
deze serveroverdracht claimt geen grafische of live-aftekening.

## Nieuwe echte-clientbevindingen van Claude om 16:1x

De negen venstercallbacks gebruikten de verkeerde waarde: `getInteractingOption()` is de positie
in `packets.yml`, geen OSRS-menunummer. Ze roepen nu `Dmm2Interactions.option(player)` aan, dat
het echte `INTERACTING_OPCODE_ATTR` leest via `getInteractingOpcode()` en de bestaande,
in de client bewezen `SlayerRewardsUi.opOf` gebruikt. IF_BUTTON1/opcode 61 is operatie 1,
ook al staat hij als derde in de packetlijst. De nieuwe regressie vult beide packetvelden,
loopt door Select/Buy-1/Sell en Deposit-1/5 en controleert saldo en itemtotalen. Een tweede
regressie bewaakt de verborgen expanded-view-header 14 en de normale footer met valuta en saldo.
Dit neemt Claude's twee clientfixes over; de verse herhaling moet deze nieuwste versie bevatten.

Het brongat voor View/Help/filter is opgelost in `PHASE6-CONTROLS.md` en
`phase6-sources/controls.json`. De grafische kinderen en volledige expanded-view blijven een
uitdrukkelijke cache/clientoverdracht aan Claude. Het opnieuw zichtbaar maken van lege controls
is geen oplossing; bronmaten en disabled dropdown-kleur/pijl zijn nu exact gedocumenteerd.

## Testdiscovery-les van deze hervatting

De eerste normale remote-run is afgebroken zonder groene claim. `--tests` filterde JUnit-uitvoering,
maar `forkEvery=1` kon nog lege workers starten voor alle ontdekte kandidaten (180 core- en
715 plugin-testbronnen). Claude bevestigde voortgang van executor 146 naar 147; het was geen
vastgelopen productietest. De gerichte init zet nu daarnaast expliciete `.class`-includes voor
exact UiPlan en de zeven eigen plugin-klassen, print die lijst en logt de testuitkomsten.
Core gebruikt een worker; alleen de zeven klassen met gedeelde installed/cache-statics worden
apart geisoleerd. De reproduceeropdracht gebruikt `--offline` omdat dependencies niet gewijzigd zijn.
Een wachtrijregel is geen actieve tweede zware build en wordt niet als testbewijs geteld.

## Laatste fase-6-opdracht: controls gebouwd

De eigenaar heeft via Claude fase 6 expliciet uitgebreid met View/Help/filter; daarna pauze, geen fase 7.
Commit `1b13f2d61` bouwt die server- en importeradapter. De bronbijlage is uitgebreid tot veertien scripts
plus enum 4988. Eerdere tekst over ongebouwde controls is daarmee historische context.

De existing `OsrsInterfacePortTool dmm2-ui --reuse` voegt nu toe:

- 819:193 View-graphic,194 Help-graphic,195/196 filterframe,197 filterpijl,198 filterlabel;
  backing192 wordt voor de correcte draw order gebruikt. Bron-sprites4735/4737/8386/8387/788.
- Expanded Help/Item Info steelframe199..210, title200 en Close210. **819 heeft nu211componenten**;
  de overige roots blijven230=41,232=31,806=83. Bestaande product- en transactienummers blijven gelijk.
- Een negen-int native667CS2-bridge met alleen IF_SETPOSITION2000/IF_SETSIZE2001 en RETURN.
  Dit is een serverlayoutvertaling van bron7155/7142, uitdrukkelijk geen byte-exacte OSRS-scriptimport.
  De bestaande tool alloceert het eerstvrije script-id, bewaakt replacementbytes via CacheTransaction
  en publiceert `script<TAB>7155<TAB><echt lokaal id>` in `cfg/dmm2/interfaces.tsv`.
  `--reuse` hergebruikt dat werkelijke id. Geen vast lokaal id en geen wijziging van de151basis-mappings.

Boot combineert alleen de echte script-/sprite-assets uit die optionele interface-map met `ids.tsv`;
conflicten worden geweigerd, identieke dubbele registraties voegen geen tweede mapping toe.
View gebruikt de bridge en de echte4735/4737-sprites. Zonder die assets weigert de knop de wissel
zonder geld- of itemmutatie. Het normale venster blijft beschikbaar. Claude importeert de aanvullingen
na de merge; de15:45-import van193componenten alleen bevat deze nieuwe controls nog niet.

View wisselt het bronraster: normaal231px/tweekolommen/111x74, expanded437px/achtkolommen/51x50,
3pxgaps en zonder namen; alle zeven producten blijven op dezelfde servertransacties aangesloten.
Help wist de selectie en toont het Help-paneel. In expanded view opent selectie Item Info met eigen
steelframe; Close210 keert naar het raster terug. De filterenum heeft geen entries, dus het label/pijl
zijn grijs en bieden geen filterkeuze. Er wordt geen verzonnen filterlijst aangeboden.

De bronmaten van7142/7144 worden op viewwissel toegepast, inclusief info-/footer-/buttonlayers;
geen layoutspam per tick. De scriptbridge heeft een byteformaat-/argumentvolgordeproef, de controls
hebben sprite/parent/color/disabled/geometrytests en de venstertests lopen door View -> product ->
Help -> Close -> View -> buy. Ontbrekende bridge en stale close zijn eveneens afgedekt.
Verwacht73proeven in dezelfde8klassen; alleen verse XML na de laatste snapshot is eindbewijs.

CLAUDE PREFLIGHT: dezelfde bestaande tool `dmm2-ui --reuse`, nieuwe211componenten en script7155-bridge,
plus de vijf bron-sprites. Neem de nieuwe echte `interfaces.tsv` mee naar de testdata zodat Boot het
script-/spritecontract leest. Geen nieuwe vaste waarden in `ids.tsv` invullen. GPT-A doet geen apply.
Hover/pressed gedrag en de volledige Fixed/Resizable/Stretched-pariteit blijven echte-clientcontrole;
unitcode of native PLAN is geen grafische of live-aftekening.

Laatste contractcorrectie: er zijn twee toegewezen native bridges, bronkey7155 voor positie/maat en
bronkey7153 voor op-caption/graphic. De caption is `Compact` in de portretstand en `Expand` in de
kleinekaartstand, exact uit7153. De tweede bridge heeft drie int-args plus een string-arg; beide
binair getoetst tegen de native667primitives/footer. Ze worden beide door de bestaande tool gepland
en bij Claude's apply in de echte interface-map gepubliceerd. De totale gerichte verwachting is nu
73tests (UiPlan7,Window18,Interaction2,Safe13,Rewards8,Boot4,Break17,Presentation4).

`PREFLIGHT-PHASE6.sh` gebruikt eveneens rgradle. De fysieke immutable kopieen bestaan alleen lokaal,
daarom kiest uitsluitend deze PLAN-opdracht expliciet de gedocumenteerde `RGRADLE_LOCAL=1`-route.
De scriptargumenten accepteren geen apply; voor/na worden alle78cachefiles gehasht en een lege
journal vereist. Geen cachekopie wordt stilzwijgend vervangen door een actuele originele cache.

Laatste bronreview: Help12 wordt in de portretstand verborgen en in de kleinekaartstand getoond,
exact7142:194..196/232..234. Dit voorkomt overlap met de disabled dropdown. De bestaande View-test
controleert beide vlaggen. De eindherhaling forceert alleen de acht gerichte Test-taken opnieuw
voor verse XML; ongewijzigde compilemodules behouden hun normale up-to-date-controle.

## Definitief bewijs en overdracht

Eindrun53515/snapshotbf887b231: **73/73 groen**,0failures/errors/skips in acht eigen klassen,
normale module/testcompilatie en negen bindingscripts groen via rgradle. Native PLAN daarna exit0,
zeven herhaalde contractproeven groen,78/78 cachekopiebestanden bytegelijk en journal leeg.
De native baseline alloceert1185..1188/5402..5403/8819..8867; dit zijn geen runtime-id's.
Werkelijke runtime-roots blijven1204/1203/1205/1206. De nieuwe scripts/sprites komen uitsluitend
uit Claude's volgende echte `interfaces.tsv`, geen overname van PLAN-waarden.

`PHASE6-VALIDATION.json` bewaart de verse XML's, snapshot/source-hashes en native bewijs-hashes.
Het volledige eindverslag staat in `PHASE6-EINDVERSLAG.md`, Claude's ronde in checklist14b.
GPT-A pauzeert na de bewijscommit zoals opgedragen en begint niet aan fase7.
