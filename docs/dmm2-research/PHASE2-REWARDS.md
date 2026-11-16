# DMM2 deel 7: Nigel Reward Store, server-WIP

V3=Deadman Points; V9=Reward Store, XP, armour, cosmetics en blueprint-reclaim; geen reset/dagteleport. Geen breachmigratie, koers, blood-moneyverdiensten of armourbetalingen gebouwd: dat is deel2 en blijft geblokkeerd. Deel5 niet gestart.

Bronnen: bestaande wiki-manifest.json/cache2686-items.json; Deadman Reward Store rev15394269 (2026-11-05), Transcript:Nigel rev15394041 (2026-11-04), Armageddon weapon scroll rev15299526, cape fabric rev15401338, teleport rev15238901; Annihilation weapon scroll rev15301001, blueprints rev15288053, teleport rev15194299. Deadman rug rev15122909. Geen externe donorcode; lokale PointBalance, containerkopieën en persistNow hergebruikt.

## Serverdiensten

|Reward|Bron-id|DP|Serverkern|
|---|---:|---:|---|
|Armageddon weapon scroll|29625|12000|Permanente unlock + vrije heen/terugconversies|
|Armageddon cape fabric|29628|6000|Permanente cape-unlock + vrije conversies|
|Armageddon teleport scroll|29622|9000|Permanente unlock + persistente selectie + serverplaybackcontract|
|Armageddon rug|29619|3000|Fysiek item; 5-tick bouwtransactie, 4 cloth/tools/level28/283XP, refund bij meubel verwijderen|
|Annihilation weapon scroll|33012|12000|Permanente unlock + één exact charged/inactive-statepair per skin|
|Annihilation blueprints|33015|15000|Estate-agent-unlock vervangt blueprint door exit-blueprint33122; Nigel reclaim gratis|
|Annihilation teleport scroll|33018|10000|Permanente unlock + selectie + serverplaybackcontract|

`NigelRewardStore` gebruikt exact `StoreCatalogue.Currency.DEADMAN.attr`, nooit een nieuw saldo. Buy is onbeperkte stock, gevalideerde enum/hoeveelheid, Long-rekensommen en ondeelbare plaatsing. Sellback geeft dezelfde DP-prijs; sale-X ondersteunt meerdere identieke fysieke exemplaren en notes, nooit unlockflags of gemodificeerde items. Overflow weigert vóór itemverwijdering. Scroll/fabric consumeert één ongegenoteerd item na bevestiging; dubbele unlock consumeert niets. Er is geen tweede verkoopbare reward nadat die is opgebruikt.

`convert` werkt op een gevalideerd inventoryslot, verwachte id en geïnstalleerd exact statepair. Alle ItemAttributes worden diep gekopieerd; nooit oncharge/creationredirect of gratis charges. Geaccepteerde bronkleuren worden via acceptedBases naar dezelfde Deadman-variant omgezet; verwijderen herstelt altijd de canonieke standaardkleur, zoals de Annihilation-bron voorschrijft. Contractmaps/sets worden bij constructie gekopieerd, zodat een later veranderde loader-map geen bestaande winkelinstantie wijzigt. BaseItem geeft de normale versie terug voor de bestaande dood-/gameplay-adapters; de permanente unlock blijft bestaan. Geen automatische generieke ornamentkit gemaakt voor een geconsumeerde accountunlock.

`DeadmanHomeTeleport` bewaart door Claude bewezen lokale stages/ticks. De bestaande Home Teleport-queue leest de geselecteerde unlocked variant en behoudt combat-interrupts, 7s-gate, bestemming, cooldown en standaardstijl als geen AV-contract aanwezig is. Menu/clientbinding en AV-import zijn Claude. Geen timing voor nieuwe stijlen verzonnen.

`NigelHouseRewards` bevat persistent per geregistreerd hotspot meubelbezit, materialentransacties, owner/buildmode-controles en revalidatie op voltooiing. Rug: begin/finish na5 wereldticks, cancel bij interrupt; opgeslagen hotspot/tile/level/materialen opnieuw gecontroleerd, geen materialenverlies of dubbele XP bij replay. Verwijderen meubel geeft rug terug als de inventory plaats heeft; room verwijderen geeft niets terug. Exit portal:10 iron bars+blueprint met inventoryhammer/saw, bruikbaar bij iedere housestyle; laatste portal nooit verwijderen, ook niet via de roomroute. Geen bronloze portalbouwtijd/XP ingevuld; de bestaande constructionqueue moet de bronbewezen actie plannen.

## Cache-/bindingcontract voor Claude

Geen source-id als lokaal id gebruikt. `NigelRewardStore.Contract` bevat alleen echte `Map<Reward,localId>`, de volledige `Skin(unlock,localBaseState,localCosmeticState,acceptedBases=alle_bijbehorende_kleurvarianten)`-lijst en lokaal exit-blueprint-id. Ontbrekende entries geven NOT_READY; `active` is standaard null. Installeer één serverinstantie bij echte plugininitialisatie. Itemdef/note/stat/equip/model/cachewerk blijft Claude.

**IDS-NODIG-VOOR-CLAUDE:** de zeven reward-items met notes, exit-blueprint33122; Armageddon skins29605/29607/29609/29611/29613/29615/29617; Annihilation33021/33023/33025/33027/33029/33031/33033/33035/33036 (met alle links/states). Ancients-deadman-cape34607 ontbreekt in cache2686 en vereist aparte bewezen mapping. Rugobjecten54114-54116, Wilderness-housestyle/chunks, exitportal en beide teleport-AV-contracten ontbreken nog. Bestaande fase1-cachematrix blijft de bron voor base/note/placeholder-ids.

Nigel Trade/shopcallbacks moeten de echte UI/NPC/bereik/sessie valideren en alleen reward-enum/serverslot aan de service doorgeven. Read/fabric/scrolldialoog bevestigt eerst, roept unlock aan en toont de bronbewezen tekst. Blueprints gaan via estate agent, niet Read; style-ontgrendeling moet samen met de echte house-selectie/rendering worden aangesloten. Nigel-opties voor cosmetics en blueprintreclaim roepen dezelfde service aan; geen ongevalideerde callback uit een oude dialoog.

**Open serverbinding na lokale ids:** alle skins moeten in de bestaande special-/ammo-/charge-/passive-/deathfamilies worden geregistreerd. Een omgekleurd toxic staff/bowfa/crystal mag zijn gameplay niet verliezen. `baseItem` is alleen de normalisatie-API, geen bewijs dat alle huidige helpers de nieuwe ids al herkennen. Cape-PvP gebruikt bestaande D4/Perdu-keten; wapens verliezen uiterlijk en volgen daarna de normale charge-/doodregels. Skinconversies mogen niet spelerklaar worden verklaard zolang deze alias-/deathbindingen niet bewezen zijn.

POH-adapter registreert alleen echte serverhotspots met unieke stabiele keys, controleert room/hotspot/bereik in `validHotspot`, annuleert rugbouw bij queue-interrupt en tekent vanuit `installed`. `otherExitPortals` telt de werkelijk overige portals, niet geïmporteerde portals dubbel. Registreer de echte room-key op iedere Slot; deleteRoom valideert de gehele geregistreerde kamer en de resterende portals v??r enig meubel wordt gewist, zonder rugrefund. De daadwerkelijke roomroute moet deze API aanroepen. De huidige PlayerHouse heeft een vaste kamerindeling; geen fictieve nieuwe rooms toegevoegd. Cache/assets/POH-client zijn Claude; de adapter kan pas concreet aangesloten worden met zijn broncontract.

## Validatie en review

Eigen klassen: NigelRewardStoreTests en NigelHouseRewardsTests; tests van koop/sellback/notes/overflow/dubbele unlock/charged-copybehoud/selectie/reclaim/ontbrekende ids, vijf-tickbouw, replay/cancel/full inventory/last portal. GROEN 16-11-2026 20:36: NigelRewardStoreTests8 + NigelHouseRewardsTests4 =12/12,0 failures/errors, verse standaard-XML. Na de extra kleurvariant/standaardkleur-correctie beide klassen opnieuw groen; BUILD SUCCESSFUL in1m40s. Actuele gewijzigde Kotlinbestanden consistent los gecompileerd en echte Gradle Test/JUnit via lokale focused-tests.gradle, --priority low/bouwslot. De twee gewijzigde bindingscripts waren apart al groen gecompileerd. Geen volledige suite, normale full-build-, runtime- of liveclaim.

Claude testclient: zeven producten echt kopen/terugverkopen/consumeren/gebruiken; DP exact gelijk aan UI, andere saldi ongemoeid; relog/restart; volle inventory, stale callback, notes, MAX overflow; elke skin/state/spec/charge/death met normale tegenhanger vergelijken; cape repair en gratis herapplicatie; beide Home-animationmenu's/default/interrupt; rug alle drie objectdelen, meubels versus room verwijderen; style/blueprintreclaim/exitportal/laatste portal. Fixed/resizable/Stretched en NPC-icoon/UI blijven Claude. WIP vereist cachecontract en serverbindingsreview vóór merge/release; geen runtime/live-PASS.
