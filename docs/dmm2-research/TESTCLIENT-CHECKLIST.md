# DMM2 — één testclientronde voor Claude

GPT-A voert deze ronde volgens de taakkaart niet uit. Dit is een draaiboek, geen runtime-PASS.
Gebruik alleen teamtest1/teamtest2, LOW voor gedrag; voor gedragen modellen ook OpenGL/man/vrouw en de eigenlijke venstermodi.
Noteer per rij resultaat, screenshot onder `screens/DMM-PAKKET-2/`, eventuele trace en verschil met de gelinkte bron.
Een ontbrekend cache-/UI-/AV-contract is een concrete integratiefout; geen knop testen met een gegokt interface-id.

## 0. Voorbereiding en exacte itemcommando's

Claude integreert eerst `PHASE2-INTEGRATION.md`, de uitkomsten van `PREFLIGHT-RESULTAAT.md` en de bronkeuzes in `PHASE3-SOURCES.md`.
De plan-ids zijn voorlopige allocaties op een kopie. Gebruik uitsluitend de gecontroleerde echte `data/cfg/dmm2/ids.tsv` na import.
Genereer per testblok exacte `::item`-commando's (script leest alleen die openbare mapping, benadert geen game/save):

```powershell
python docs/dmm2-research/testclient_commands.py --ids data/cfg/dmm2/ids.tsv --group starter
python docs/dmm2-research/testclient_commands.py --ids data/cfg/dmm2/ids.tsv --group trinkets
python docs/dmm2-research/testclient_commands.py --ids data/cfg/dmm2/ids.tsv --group weapons
python docs/dmm2-research/testclient_commands.py --ids data/cfg/dmm2/ids.tsv --group fire
python docs/dmm2-research/testclient_commands.py --ids data/cfg/dmm2/ids.tsv --group rewards
python docs/dmm2-research/testclient_commands.py --ids data/cfg/dmm2/ids.tsv --group blood
python docs/dmm2-research/testclient_commands.py --ids data/cfg/dmm2/ids.tsv --group ancient
```

Het script weigert de HELE groep bij een ontbrekende mapping; geen bron-id of plan-id als lokaal item gebruiken.
Per karakter: `::gear_max` (teamtest1), `::gear_maxrange` (teamtest2), of `::gear_maxmage` voor de magieproeven.
Bij een bot de bestaande buffstap uitvoeren:

```bash
bash work/claude-scripts/live/bot_buff.sh melee teamtest1
```

Gebruik per daadwerkelijke set de overeenkomstige gear-parameter van dat bestaande script. Na een dood de set opnieuw geven.
Aanvullende bestaande commando's: `::restore`, `::bank`, `::mypos`, `::avtrace`, `::spec`, `::male`, `::female`.
Commandopoort 50217: `teststate teamtest1`, `testevents teamtest1`, `kill teamtest1` (dit laatste is een bronloze PvM-dood, geen geldige PK).
Voor verplaatsen via de commandopoort: `tp teamtest1 3164 3487 0` controleert beloopbaarheid; kies de gemelde dichtstbijzijnde vrije tegel als hij weigert.
`::tele 3164 3487 0` is het bestaande clientcommando voor de GE. Inspecteer op locatie de werkelijk geplaatste NPC's.

Inventariseer vóór elk blok inventory/bank/equipment/safebox/graf/Perdu/GE en relevante charges/saldi. Tel na elke verplaatsing opnieuw.
Verwachte verbruikskosten en conversies hieronder zijn de enige toegestane verschillen; tel DP en ingeleverde rewards samen op waarde.
Tijdens een I10-meting geen items administratief verwijderen of bijspawnen. Herhaal transacties vijf keer en stuur ook een oude klik opnieuw.

## 1. NPC's, icoon en UI

- GE: Nigel bron8391, Financial Wizard bron6516, Financial Seer bron6485 zichtbaar, juiste naam/examine/rechtsklik-opties.
- Nigel Talk-to/Trade, XP, cosmetics, armour en blueprint-reclaim doorlopen. Geen reset of dagteleport (V9).
- Eén Deadman Tutor-icoon op minimap én wereldkaart, ook na clientherstart; vergelijk [Nigel rev15401296](https://oldschool.runescape.wiki/w/Special:PermanentLink/15401296).
  Kandidaten1524/1681 zijn nog geen bewezen mapping. Geen oud Breach Trader-icoon laten staan na release.
- Wizard en Seer: Talk-to, Bank, Collect en Deposit-box. Collect via bestaande banker_collect, geen dubbele handler.
  Bank/PIN/Collect blijven de bestaande diensten. Onvolledige unnoting-/bankuitbreidingstakken staan afzonderlijk in het bronnenrapport.
- Safebox230/232 en Reward Store in fixed/resizable/Stretched: tekst/knoppen/scroll/X/Esc; vertrek, dood of sluiten maakt oude klikken ongeldig.
- NPC weg/despawn, andere plane, afstand groter dan twee: UI-transacties moeten weigeren zonder items/saldo te veranderen.

## 2. XP-slot

Bron: [Transcript:Nigel rev15394041](https://oldschool.runescape.wiki/w/Special:PermanentLink/15394041), V5.

1. Stel teststats op 90: `::setlvl attack 90`, `::setlvl strength 90`, `::setlvl defence 90`, `::setlvl hp 90`,
   `::setlvl ranged 90`, `::setlvl prayer 90`, `::setlvl magic 90`. Zo blijven de vaste maxgear-sets bruikbaar en zijn levels onder 99.
2. Nigel → alleen Hitpoints-XP uit. Doe gewone melee/ranged/magic-hits: aanvallende skill-XP stijgt, HP-XP/orb/event niet.
3. Alle combat-XP uit: geen XP in skills0..6, ook niet Prayer/botten of een vaste quest-/lampbeloning.
4. Skilling en Summoning blijven hun bestaande skilling-rate krijgen. DP en afgeronde Deadman Tasks blijven intact.
5. HP apart uit laten, combat weer aan: uitsluitend HP blijft geblokkeerd. Daarna HP weer aan: beide werken weer.
6. Herhaal na logout/login en dood. Controleer dat geblokkeerde XP geen level-/task-events of brawling-chargeverbruik veroorzaakt.
   `::setlvl` is administratie en test de XP-gate zelf niet; de echte acties onder2/3 zijn verplicht.

## 3. Safebox + relog + dood

Bron: [Wizard rev15364116](https://oldschool.runescape.wiki/w/Special:PermanentLink/15364116),
[Seer rev15390113](https://oldschool.runescape.wiki/w/Special:PermanentLink/15390113), V6/D1–D20.

1. Eerst zonder skull: `::item 995 11`; Wizard Deposit-box. Stort tien munten: tien losse vakken, één munt blijft gedragen.
   Elfde weigert. Seer toont exact dezelfde tien vakken. Haal elk vak één keer uit; oude klik geeft niets extra.
2. Stort één lege corrupted scythe en één active Fortuity; noteer de10M-lading. Bewaar ook gewone noted/tradeable items.
   Charged corrupted scythe/shadow en blood money zijn onruilbaar en moeten weigeren, met ongewijzigde charges/aantallen.
   Overige untradeables/burden-trinkets weigeren; noted items blijven dezelfde vorm. Test attribuutbehoud met een werkelijk ruilbaar item.
3. Sluit UI, logout/login: contents blijven, oude toegang niet. Bank/safebox geeft geen Fortuity-,Avarice-,Vengeance-,Undead- of fairy-effect.
4. Vul gewone inventory (echte items) en probeer opname. Alles blijft in de box. Maak één vak vrij en probeer opnieuw.
   Test ook een bestaande coinsstack op Int.MAX_VALUE met daarnaast vrije vakken: geen tweede overflowstack.
5. Skull pas NA bovenstaande ongeskullde proeven: `::skullme` geeft een echte vijfminutenskull.
   Zowel nieuw openen als een nog openstaande transfer weigert met de bronmelding. Heropenen na skull vereist opnieuw NPC-toegang.
   `::skullme off` reset alleen de administratieve icoonkleur en verwijdert de echte timer NIET.
6. `kill teamtest1` via de commandopoort: PvM-dood, box exact gelijk. Herhaal zonder skull en met skull.
7. Echte aanval/dood door teamtest2: box blijft veilig bij normale PvP, guard/skull, offline killer en alt-dood.
   Inventory-trinkets volgen V6 de gewone D2/D3, geen universele dustregel. D14 geldt voor burden/kits.
8. Vergelijk Items Kept on Death en echte resultaten. Box-items mogen nergens in graf, Perdu, loot key of op de grond verschijnen.

## 4. Starter en Chronicle

Bron: [pack rev15188784](https://oldschool.runescape.wiki/w/Special:PermanentLink/15188784),
[update rev15121071](https://oldschool.runescape.wiki/w/Special:PermanentLink/15121071), V2 en D9.

1. Claim via de daadwerkelijk gekoppelde combat tutors/Brugsen. Nieuw account: éénmalig1M+100house-tabs;
   ongeopend pack in bank/inventory voorkomt een tweede ongeopend pack. Relog en vijfmaal reclaim verdubbelt geen geld.
2. `::item 23881 1` is de bestaande pack-id, alleen voor een afzonderlijke inventarisproef.
   Open met21 benodigde uitkomstvakken beschikbaar: sword/bow/staff,12NIET-stackbare Tuna,Chronicle,chest,legs,cape,Dramen staff,skull;
   geen looting bag en geen cash uit het herhaalbare pack. Bow2000/staff500 charges, Chronicle-accountcharges+10 tot maximaal1000.
3. Met precies elf vrije vakken maar onvoldoende plaats voor alle echte items: huidige gekozen veilige regel weigert ondeelbaar.
   Pack/charges/geld blijven gelijk. Meet OSRS als Claude het eleven-slotconflict wil oplossen; geen vissen laten verdwijnen.
4. Verlies/vernietig Chronicle en haal opnieuw: accountcharges blijven. Teleport cards13658 (echte lokale mapping) laden dezelfde teller,
   limiet1000; doubleclick/verplaats book tijdens dialoog verbruikt geen losse kaarten. Check zowel gedragen als inventory.
5. Chronicle teleport naar Champions' Guild: één charge, juiste bestemming/AV. Teleblock/Wilderness/Deadman7s weigert vóór chargeverbruik.
6. Bow/staff op een NPC: charge ook bij miss; laatste charge verwijdert wapen pas na geldige aanval/XP.
   Op een speler weigeren alle starterwapens vóór hit, special, XP of chargeverbruik.
7. Tuna:10HP en normale eetcooldown. Ongeopend pack op Entrana weigeren (ook Law-Altar-route).
8. PvP-dood behoudt de aparte D9-kist78k+skull; geen herhaalbaar1M-pack als PvP-cashbron. Legacy ongeopend/ooit betaald zonder flags
   apart nalopen; ontbrekend historisch bewijs geeft conservatief geen extra1M.

## 5. Nigel Reward Store en armour

Bron: [store rev15394269](https://oldschool.runescape.wiki/w/Special:PermanentLink/15394269), V3.
`::points` toont bestaande DP; `::givepoints teamtest1 deadman 67000` voegt 67k toe (stelt niet op 67k).
Noteer beginsaldo en gebruik bij herhaling de benodigde delta; geen `maxpoints` tijdens waardebehoudmetingen.

|Product|Koop EN terugverkoop DP|Na gebruik|
|---|---:|---|
|Armageddon weapon scroll|12000|permanente skins|
|Armageddon cape fabric|6000|permanente cape-unlock|
|Armageddon teleport scroll|9000|permanente teleportstijl|
|Armageddon rug|3000|fysiek POH-materiaal|
|Annihilation weapon scroll|12000|permanente skins|
|Annihilation blueprints|15000|Wilderness-housestyle + exit-blueprint|
|Annihilation teleport scroll|10000|permanente teleportstijl|

1. Koop ieder product, sellback ongebruikt (ook note), saldo exact terug; DP+rewardwaarde constant.
   Test0DP, éénDPtekort, volle inventory, stale packet, hoeveelheid0/MAX, terugverkoop met MAX-DP: geen afboeken/verdwijnen/overflow.
2. Scroll/unlock één keer consumeren, relog, opnieuw Read/use: niets extra verbruiken. Permanente unlock is geen verkoopbaar item.
3. Nigel cosmetics: iedere base/skin/charged/inactive/kleurstaat heen/terug; charges en attributen exact gelijk.
   Annihilation revert geeft de canonieke kleur. Vergelijk gameplay met gewone base: darkbow2pijlen, toxic/crystalcharges, godspellCharge.
4. Blood money op Nigel: chest/legs/cape elk1physicalmoney voor permanente armour-unlock, daarna gratis reclaim.
   Geen DP-afschrijving, geen aankoop zonder money; dubbele unlock weigert zonder extra geld uit te geven.
5. Ancient Deadman cape bron34607: Ancient fabric op een van drie imbued Deadman god capes → één nieuwe cape, fabric+bron verbruikt.
   Geef Ancient fabric met het bestaande `::spawn ancient_fabric 1`; de cape-ids komen uit de generatorgroep `ancient`.
   Gewone ancient cape aan Nigel mag NIET rechtstreeks34607 worden. PvP-breken/96k-repair via normale ancient-cape-keten;
   ornament gaat verloren. Deze aanvullende bron ontbreekt in2686, dus eerst Claude's echte import+mappings.
   Bron: [rev15403804](https://oldschool.runescape.wiki/w/Special:PermanentLink/15403804); de wiki markeert niet-heraanbrengen na dood deels met fact-tag.

## 6. Blood money en breachmigratie (aparte testrelease)

V1/V7:200BP=1DP, naar boven, eenmalig bijlogin;1bloodmoney per bestaand VALID-PK-verdict.
De vlag `rsps.dmm2.release` staat standaardUIT. Claude test de vlag pas in zijn geplande testserverstart; geen saves handmatig migreren.

1. Op eigen wegwerp-testkarakter vóór release: `::givepoints teamtest1 breach 75000`, noteer overige saldi.
   Na geplande teststart met releasevlag enlogin:375DP erbij, oude75k in auditkeys, actieveBP weg, markering gezet.
2. Vijfmaal relog: geen tweede krediet. Te hoog DP-saldo: geen migratie/afboeking/markering; na genoeg ruimte éénmaal slagen.
   Randwaarden0/1/199/200/201/MAX zijn daarnaast in de unitproeven berekend.
3. Breach-damage,hotspot,chest,emblems,admin en oude trader kunnen na release geen BP meer verdienen/uitgeven.
   Nieuwe Archaic-tier5drops uit; bestaande exemplaren blijven bezit. Oude24stockbestemmingen volgen `PHASE2-CURRENCY.md`.
4. Geldige PvP-kill: éénphysicalbloodmoney als owned drop; geen geld bij alt/same-IP/ongeldig/practice/self.
   Twee laptopaccounts op hetzelfdeIP moeten dus0 geven; dit is de verwachte antifarmuitkomst, geen mislukte1money-test.
   Gebruik een daadwerkelijk VALID beoordeelde proef voor de1-route; de geïsoleerde unitproef dekt beide verdicts zonder IP-uitzondering.
5. Blood money is onruilbaar (wiki rev15380671/cache2686): trade/GE/safebox weigeren. Controleer dood/owned grondloot
   via de gewone onruilbare-itemmatrix; geen tweede virtuele wallet en geen onbewezen OSRS-doodconversie toevoegen.
   Ontbrekende mapping → persistent pending; na mapping eenmalig leveren, volle inventory blijft retrybaar, overflow geen tweede stack.

## 7. Alle trinkets

- **Advanced**: vrij veld, Open, naar het noorden kijken en juiste AV, één van acht gelijke uitkomsten; scythe/shadow leeg. Herhaal bij muur, speler ernaast,
  verplaatsen/logout/dood tijdens animatie en dubbele klik: geen gratis uitkomst of verlies. [rev15355050](https://oldschool.runescape.wiki/w/Special:PermanentLink/15355050).
- **Fairies**: bestaande fairy/spirit-tree-menu's, eisen, laatste bestemming, Wilderness/teleblock/7s; inventory nodig.
  Geen tweede handler. [rev15191597](https://oldschool.runescape.wiki/w/Special:PermanentLink/15191597).
- **Undead**: drie Summon-opties, één eigen thrall naast gewone pet EN BoB; 10s (17 ticks) gedeelde cooldown, ook na relog;
  duur = huidige Magic in ticks, 0..3 damage per vier ticks, geen XP/PvP. Vervangen/logout/instance verlaten ruimt alleen thrall op.
  [rev15298165](https://oldschool.runescape.wiki/w/Special:PermanentLink/15298165).
- **Avarice**: Note/Unnote, Value nul/positief, notes/bones; alleen nieuwe eigen drops. Vol/overflow geeft normale owned ground-drop,
  geen duplicaat in inventory. Bank/safebox/andermans grondloot geen effect; geen looting bag.
  Drempeladvies = GE-stackwaarde in Long, clamp op MAX; dezelfde provider voor gewone NPC en breach. [rev15286577](https://oldschool.runescape.wiki/w/Special:PermanentLink/15286577).
- **Fortuity**: `::item 995 10000000`, Charge → exact 10M weg; Uncharge → exact 10M terug. Volle coinsstack weigert heel.
  Boss-multiplier +1 precies eenmaal; gewone NPC/breach-monster geen +1. Drop Viewer Normal-kolom 1x; zoekindex eveneens bronafhankelijk.
  Controleer teleport-countdown en combat-gate. PvP: active → inactive + 10M naar geldige killer; PvM: gewone D2/D3; box: geen bonus/refund.
  [rev15286578](https://oldschool.runescape.wiki/w/Special:PermanentLink/15286578),V4/V6.
- **Vengeance**: Cast zonder runes/skill; 30s gedeelde spellcooldown, item blijft; bank/box geeft geen Cast.
  Geen AllStars-varianten met 1/2 ladingen. Normale variant is volgens de bron niet verkrijgbaar in Annihilation; geen dropkans verzonnen.
  [rev15351326](https://oldschool.runescape.wiki/w/Special:PermanentLink/15351326).

## 8. Acht corrupted weapons

Gebruik weapons-commando's uit blok 0. Per item: Inspect/Check, alle combatstyles, manuele cast, autocast, ammo, special, eisen, GE, notes en dood.
Man en vrouw, voor/achter/lopen/vechten met 667- en OSRS-gear; screenshot per wapen. `::spec` vóór aanval, `::restore` voor energie.
De exacte bonus-/requirements-tabellen en acht wikirevisies staan in `REPORT.md`/`wiki-manifest.json`.

|Wapen|Kerncontrole|
|---|---|
|Voidwaker|bestaande special, fysieke corrupted-stats, normaal energie-/accuracymodel|
|Dragon claws|vier hits, bestaand claw-algoritme, corrupte bonussen|
|Armadyl godsword|bestaande AGS-special, corrupte bonussen|
|Twisted bow|passive versus target-Magic, alle pijlen/quiver, snelheid/bereik|
|Dark bow|twee pijlen, zeven ticks normaal/zes Rapid, bereik zes, 55% special; cache2686 param13=6/param14=7|
|Volatile staff|bestaande Nightmare-special, manual/autocast, corrupte bonussen|
|Scythe|1x1/2x2/3x3: max/half/kwart; drie hit-slots bij cleave, primair eerst, multi/collision; één charge bij damage|
|Shadow|75 Magic, vijf ticks, bereik acht, 20k charges, twee soul + vijf chaos; worn x2/ToA x3; gewone shadow blijft x3/x4|

Scythe: `::item 565 600`, gebruik blood runes op empty → 200 charges; laatste charge → echte lege vorm.
Uncharge buiten de well vernietigt charges. Bij de well alleen hele batches van 100 charges → 300 blood per batch (199 → 300 runes).
Herhaal met volle inventory/MAX-runes, relog, laatste charge en dood. Charged PvP-verlies → empty, geen charge-refund; PvM behoudt charges.
Shadow: `::item 566 200`, `::item 562 500`; charge/uncharge via echte handler, limiet 20k, geen powered spell in PvP.

## 9. Thunder khopesh

[DMM rev15262511](https://oldschool.runescape.wiki/w/Special:PermanentLink/15262511): bron33041, niet Leagues30388.
20% proc op accurate gewone hit; 3x3 rond de snapshottegel, twee tegels weg ontwijkt, geen lightning-XP, Corp krijgt volledige damage.
PvM-special 60%: twee melee-hits en een bolt als minstens één accuraat is. PvP: één 30% sterkere initial-hit, daarna op tick7/9/11/13
een bolt voor 30..45% van oorspronkelijke schade. Melee-prayer op initial-hit, magic-prayer op bolts; geen dubbele melee-protection op passive-bolt.
Eerste PvP-impact gebruikt het gekozen bestaande melee-delay1 + PID. Exacte OSRS AV-offset blijft meetpunt; geen bewezen timingclaim.
Test single/multi, bewegend target, andere speler in snapshot, plane/logout/dood/teleport tussen bliksems: geen ongeldige targets, credit of XP.

## 10. Fire ruby / Amulet of fire

[amuletrev15396852](https://oldschool.runescape.wiki/w/Special:PermanentLink/15396852),[InfernalMagerev15403365](https://oldschool.runescape.wiki/w/Special:PermanentLink/15403365).
Fire ruby: tertiair 1/64 van Infernal Mage, boost volgens bestaande bronregels; Drop Viewer/Collection Log door Claude.
`::setlvl runecrafting 21`, `::item 1727 1`, `::item 554 100`, één ruby via fire-commando's.
Ga naar de Fire Altar bij Duel Arena via bestaande altar-teleport/lopen. Ruby op altar → drie ticks, inputs weg, één amulet, 125 basis-XP.
Verplaatsen/logout/ingrediënt weg/volle inventory/dubbelklik: geen verloren of gedupte inputs/output.
Vuurspreuken +2 basis vóór gear/weakness; Chaos gauntlets bij Fire Bolt +3 extra; geen bonus voor water/ancient/powered.
Salarin is een expliciet bron-/integratiepunt: de baseline heeft nog geen bewezen strike-only/fixed-hitpolicy.
Meet verwachte bolt-tier fixed9/10/11/12 en fire-amulet +2; overige gearbonus mag daar niet meetellen.
Dit kan pas PASS worden nadat Claude de onderliggende Salarin-route corrigeert/naloopt; zie `PHASE3-SOURCES.md`.

## 11. POH en Home Teleport

Housebezit via het bestaande systeem, `::house`. Echte hotspots/style/exitportal-contracten door Claude.
Rug: `::item 8794 1` (saw), `::item 2347 1` (hammer), `::item 8790 4` (cloth), Armageddon rug;
28 Construction, vijf ticks, 283 basis-XP. Interrupt/logout vóór finish kost niets; replay geeft geen extra XP/meubel.
Meubel verwijderen → alleen rug terug als er plaats is; kamer verwijderen → niets. Laatste exitportal mag ook via kamerroute niet verdwijnen.
Exit: tien iron bars (`::item 2351 10`), blueprint en tools, iedere style; gratis Nigel blueprint-reclaim na permanente unlock.
Annihilation blueprint via estate agent → Wilderness-style + exitblueprint; style-selectie en rooms moeten werkelijk tekenen.
Beide Home Teleport-stijlen kiezen na permanente unlock: bron-AV en gewone 30min-cooldown/7s-gate/GE-bestemming/interrupts behouden.
Ontbrekende AV verspilt geen items; defaultstijl blijft werken. Relog onthoudt keuze, unlocks en POH-meubels.

## 12. Aftekening

Claude vult resultaat in per blok1–11. A8 omvat stale/dubbele/volle/relog/andere-volgordeproeven; I10 telt vóór/na over alle eigendomslocaties.
Na NPC/UI/cache-integratie: normale bootcheck en bestaande gedeelde combat-packagechecks bij Claude's mergebundel.
Vervolgens A9 in de gewone opnieuw gestarte 78-client op live, inclusief minimap/wereldkaart. Groene unitproeven zijn geen runtime-aftekening.

## 13. Aanvulling fase 5 — concrete hertest van de botbevindingen

Bronnen, gekozen adviezen en importgrenzen: `PHASE5-SUPPLEMENT.md`. Sinds fase6 geldt Claude's nieuwe bestand met151 mappings/163 bestandsregels (zie `PHASE6-WINDOWS.md`); de oorspronkelijke143 mappings blijven gelijk. Geen PLAN-id gebruiken.

1. **Fire Altar.** `::tele 2578 4845 0` is onze bestaande Altar.FIRE-ingang; loop naar altaar2482. `::item 25016 1`, `::item 1727 1`, `::item 554 100`, `::setlvl runecrafting 21`. Gebruik ruby op altaar: rode amulet-enchant (visuele bron Creating amulet of fire.webp), geluid, drie ticks, één25018 en precies de drie ingrediënten verbruikt. Niet de rune-craft791/186 verwachten. Tweede oude klik maakt geen extra item.
2. Alleen ruby of Runecrafting20: onmiddellijke receptgegevens; geen mislukte craftanimatie of mutatie. De melding is een feitelijk receptoverzicht, GEEN bewezen letterlijke OSRS-refusal. Boost terugvallen, runen verwijderen, lopen/logout/dood tijdens de drie ticks: geen gedeeltelijke kosten of dubbel resultaat. Geluid145/hoogte92 naast echte OSRS-craft vergelijken: gekozen advies, geen audio-/hoogtemeting.
3. **Avarice.** `::item 23823 1`; echte rechtsklik **Value is optie2**, Note/Unnote optie1. Botoptie3 is leeg in beide caches en test deze functie niet. Value op bijvoorbeeld100000 zetten, relog, NPC-drop onder/boven drempel, gestapelde drop en bones; controleer note-toggle en normale gronddrop wanneer inventory vol is. GE-stackwaardering is het vastgelegde lokale advies; geen OSRS-prijsclaim.
4. **Gewone/corrupted claws ijken.** Neem een nieuw levend NPC-doelwit. Eerst `::item 14484 1`, daarna `::item 24960 1`, echt equippen en elke beschikbare melee-stijl aanvallen. Beide horen dezelfde claw-swingcue te geven. Herhaal nabij teamtest2 en controleer op dubbele cues bij sequences met eigen framegeluiden.
5. **Specials.** Herstel energie met `::restore`; klik de echte specialbalk in de client, controleer de ingeschakelde staat en val pas daarna een nieuw levend doelwit aan. Herhaal voor24958 voidwaker,24960 claws,24962 AGS,24976 corrupted dark bow en24978 corrupted Volatile. Controleer de ids vooraf in ids.tsv; het mappinggeneratorcommando uit blok0 is leidend. Kosten50/50/50/55/55%, hetzelfde aanvalstype als de basis, fysieke corrupted stats, geen kosten bij ongeldige aanval. Bot+`::spec` was ook voor gewone claws onbruikbaar en is geen special-aftekening.
6. **Rest van de familie.** Gewone aanvallen met twisted bow, beide scythes, beide shadows, Volatile-bash/autocast, khopesh en de zestien skins. Scythe-specifieke8056 pas na echte extra mapping; normale Scythe-categoryaudio is gekozen advies en moet naast Vitur worden beluisterd. Shadowcast gebruikt de gedeelde gewone Shadow-presentatie; verschil versus de basis melden. Khopesh-special blijft pas beschikbaar na installatie van het echte appearance-contract; controleer dat hij zonder contract geen energie verbruikt.
7. **UI-ronde na Claude's imports én adapters.** 230/232: quillfontgetallen0/10, alle tien items, deposit inventory/equipment, withdraw1/5/10/X/All, inventoryopties, skull/afstand/relog/dood, closing/replay. Reward Store: OMNISHOP819/806, bronrow929, zeven rewards, welkomst-/helptekst en prijzen naast `phase5-sources/reward-store.png`; alle buy/sellbackroutes op echte DP. Statische PLAN-successen zijn nog geen widget-/callback-PASS.

## 14. Fase6: één ronde voor de werkelijke vensters

Claude heeft de append-widgets op 17 november om 15:45 al toegepast (`tx-20261117-144453`, dossier 15:4x).
Na deze serverhervatting is geen extra import nodig voor de worn-slotguard of testfixes. Gebruik de actuele
mappings en componentaantallen230=41,232=31,819=193,806=83; geen PLAN-ids.

1. `::tele 3151 3513 0`, `::empty`, `::item 995 100`. **Geen skull.** Wizard15314 → Deposit-box:
   Safe Deposit Box met0/10, tien vakken, inventory rechts. Deposit-1,5,10,X,All op coins:
   één coin neemt één vak, maximumtien. Bij100 coins en volle box blijft90 in inventory.
   Elfde coin kost niets. Withdraw-1/5/10/X/All: weer100 coins, geen restant in box.
   Vijfmaal heen/terug, ook via Seer15315, steeds100 totaal.
2. `::empty`, `::item 4151 1`, echte whip equippen. Open box, Deposit worn items: whip naar box,
   speler verliest weapon-bonussen/look. Herhaal met een uitgerust ammo-stack van100: tien in box,
   negentig blijven uitgerust, geen onterechte volledige unequip. Een door de bestaande unequip-slotguard
   geblokkeerd item blijft gedragen met dezelfde charges; na opheffen van die guard werkt opslag weer.
   Untradeable (`::item 772 1`) blijft buiten box.
   Charge-item bewaren/opnemen: exact dezelfde charges. Tel equipment+inventory+box, niet alleen iconen.
3. Box met whip, inventory vol met nonstackables: withdrawal weigert zonder itemverlies.
   Depositing-X openen, **sluiten/heropenen**, antwoord10: oude prompt mag niets verplaatsen.
   Herhaal met logout/login, wisselen van de stack, lopen buiten2tegels en skull na openen.
   Skull is ook bij de Seer geblokkeerd; na verstrijken opnieuw openen.
4. Sluit metX/Esc, open Bank/Collect en wissel naar Nigel. Geen achterblijvende side-overlay of bruikbare oude knop.
   PvM/PvP/guard/alt-death met inhoud: box blijft veilig, geen effect vanuit opgeslagen Fortuity/Undead/Avarice,
   geen kopie in graf/lootkey/Perdu. Relog bewaart box/charges en opent geen oude sessie.
5. Nigel15316 **Trade**, daarna Talk-to → `I'd like to access the Reward Store.`: dezelfde winkel.
   Welkomsttekst/zeven iconen/naam/prijs/scrollbars naast `phase5-sources/reward-store.png`.
   Check aanklikken op kaartachtergrond én item/naam. Klikgeluid bij selecteren/bulkdeposit/koopknop:
   bron2266 is bytegelijk met667 `INTERFACE_SELECT1`; geen dubbele cues.
6. `::empty`, `::givepoints teamtest1 deadman 12000` (controleer bestaande commandusage; het commando voegt toe).
   Select Armageddon weapon scroll → Buy1: DP12000 lager en één echte24996; gebruik de generator uitblok0
   als de echte mapping verschilt. Inventory Select → Value12000DP → Sell1: precies hetzelfde saldo terug.
   Rechtsklik Buy/Sell1/5/10/50 en de knoppen, ook notes; een geconsumeerde unlock is niet refundbaar.
   Doe vijf rondes en herhaal voor alle zeven prijzen12000/6000/9000/3000/12000/15000/10000.
7. Geen DP maar wel bloodmoney: geen aankoop. Volle inventory, gewijzigd item en refund bij maximaalDP:
   geen gedeeltelijke debet/credit, geen verdwijnen/dupe. Sluit/heropen/relog/teleport/NPCdespawn/dood tussen
   selecteren en knop: oude selectie doet niets. Tweede testaccount krijgt geen selectie of saldo van de eerste.
8. Hele ronde Fixed, Resizable en Stretched; X/Esc herstelt normale inventory. Het huidige serverport gebruikt
   desktop normal-view; optional expanded/mobile/hover branches staan expliciet als grens in PHASE6-WINDOWS.
   Native PLAN en unitproeven zijn geen grafische of A9-aftekening.

### 14b. Laatste controls-aanvulling

Na merge van de nieuwe controls moet Claude `dmm2-ui --reuse` opnieuw plannen/applyen:819=211,
script7155=layoutbridge en7153=op-caption/graphicbridge, plus4735/4737/8386/8387/788. Kopieer de echte interface-map naar
`cfg/dmm2/interfaces.tsv` in de testdata;151basisids hoeven niet opnieuw genummerd te worden.

- Open Nigel normaal: footer-DP, grijze `No filters available` met pijl; geen actieve filterkeuzes.
- View-pijl (Compact): zeven kleine iconen in het compacte raster, namen verborgen, DP-header bovenaan; selecteren
  opent Item Info in eigen steelframe. Koop1/5/10/50 rekent dezelfde DP-prijzen. Close van Item Info
  keert terug naar de compacte producten en sluit niet onbedoeld de hele winkel.
- Help toont de echte Help-tekst; Buy na Help doet niets zolang er geen product is geselecteerd.
- View terug: portretraster, namen, rechterpaneel en footer hersteld. Vijf keer heen/terug, daarna
  kopen/refund, hoofd-X/Esc, relog; geen selectie of ander accountsaldo dat doorlekt.
- Fixed/Resizable/Stretched: borders, dropdown, beide view-iconen en Helphover naast OSRS;
  controleer geen overlap, afgesneden tekst of onjuiste kliklagen. Core-/breektests vervangen dit niet.


## 15. Fase 7 serverbewijs en resterende beeldronde

De actuele verse uitslag per testklasse staat in `phase7-output/regression/` en `PHASE7-VALIDATION.json`.
Een unit-PASS bewijst geen clientbeeld of A9. Brongaten staan in `PHASE7-SOURCES.md`.

| Kaartpunt | Verwacht en serverproeven | Resterend beeldbewijs |
|---|---|---|
|7.4a valuta/migratie|V1/KOERS bovenaan dossier: **200 BP -> 1 DP, naar boven**, eenmaal; geen blood-money-conversie. Dmm2CurrencyTests + Dmm2BreakTests: disabled/retry/reload/overflow, alle ongeldige verdicts, pending claim, owned drop. Nieuwe D5-proef: 1 munt, slachtoffertegel, eigenaar,100/500 of100/6100 ticks.|Bij releasevlag login van echt legacy-testaccount: DP eenmaal, oudebalans/audit intact bij overflow. Same-IP/practice/self0munten; geldige PK1ownedmunt. Geen saves handmatig veranderen.|
|7.4b Nigel armour/rewards|Dmm2CurrencyTests/BreakTests: elk stuk1bloodmoney, permanenteunlock, gratisreclaim, dubbelunlock/volleinv/geenmunt. Dmm2BootTests: alle3 echte IDs D2/D4, unlock blijft. Dmm2WindowTests/NigelRewardStoreTests: alle7rewards, stale/refund/0/MAX/vol/relog.|Nigel armour na dood gratisclaim; Reward Store vaste/uitgebreideview+sellback, saldo blijft DP.|
|7.4c acht corrupted weapons|Dmm2WeaponPresentationTests/Dmm2ItemServicesTests: basisaanvalsgeluid, vijf echte specialroutes via SpecialAttacks, geen special voor tbow/scythe/shadow, charges/depletion/PvPconversie. Bot+::spec blijft ongeldig specbewijs.|Spawn via testclient_commands.py --group weapons, normale swing per melee/ranged/magic; hoor/trace dezelfde cue als basis. Special met echte player UI, niet botpad.|
|7.4d trinkets|Dmm2ItemServicesTests/SourcePolicyTests/BreakTests: Fortuity exact10M/refund/MAX, avarice GE-stackthresholdclamp+pickup, fairies gate, vengeance/cooldown, carriedvsbox, D14burden/kits bestaande doodtests.|Avarice Value-menu/invoer; fairies beide reisroutes; vengeance juiste melding/animatie; Fortuity hover/chargecheck; advanced AV nog ONZEKER.|
|7.4e starter/Chronicle|DeadmanStarterPackTests/StarterPackInteractionsTests/BreakTests: geen dubbeleintro/reclaim, chargesaccount1000max, kaart13658dezelfdeaccountteller, verlies/destroygeenreset, spendidentity/Wilderness. Nieuw:0chargesmelding werkt ook zolang AV ontbreekt.|Chronicle Teleport heeft nog geen volledig bewezen AV/timingcontract; verwacht tot die import GEEN teleport. Niet als spelerklaar afvinken. Pack11slots-vs21inhoud blijft bronconflict zoals dossier18:5x.|
|7.4f Fire ruby/POH|Dmm2ItemServicesTests/BreakTests/NigelHouseRewardsTests/NigelRewardStoreTests: level/ingredients/completion, exact125XP, rug/portal5ticks materiaal, interrupted/full/relog, homekeuzepermanent.|Fire Altar met Fire ruby + rubyamulet +100firerunes,21RC: juiste LVL3anim/gfx/geluid; rug/exitportal/wildernessstyle+estateagent en beide homeanimaties naast OSRS.|

7.1 BEWEZEN DOOR CLAUDE: dossier17nov22:52/22:5x, Wisp15320/seq17773 elke4ticks,
projectiel3472 + impact3473; beeld f8-wisp-sheet.png/f8-wisp-zoom.png zonder misvormde skin.
De cache-import hoeft niet opnieuw. Alleen als de mergebundel het thrallpad verandert:
`testclient_commands.py --group trinkets` -> Lil Wisp -> levend doel op1en6tegels (KQ1158 +god,
geen kip die in1ticksterft); transparantie/idle/walk/volgen/4tickattack behouden.
Keuze MAGIC01 blijft expliciet advies, geen OSRS-DMM-thrallvideobewijs.

VOOR CLAUDE TESTCLIENT 7.2: Chronicle zonderlading moet de transcriptmelding tonen; in Wilderness weigeren zonderchargeverlies.
Geladen Chronicle blijft ONZEKER totdat ononderbroken OSRS-timing + seq/gfx-match bewezen is; video's/tijdstempels in PHASE7-SOURCES.

VOOR CLAUDE TESTCLIENT 7.3: advanced bij muur of andere speler binnen1tegel ->
'You must have nothing surrounding you to open this trinket.' en item blijft. Vrijveld blijft AV-ONZEKER.
Pas bij bewezen AV-contract: noorden/animatie en precies1van8wapens, scythe/shadow ongeladen; geen gratis herhaalde opening.
Cachebron is inmiddels deels opgelost: loc49564/49565 en locanim10426, benoemde batch `dmm2-advanced-loot`.
Dit is scenery, geen playerseq; spelerpose/plaatsing/serverduur blijven ONZEKER. Zie PHASE7-ADVANCED-PLAN.md.

Undead-weigertekst: API18nov bevestigt dat Transcript:Trinket of undead ontbreekt; alle9784client-scriptgroepen
in cache2686 bevatten geen bron voor COOLDOWN/NO_TRINKET/NOT_READY. Stilleweigering blijft het gelabelde advies;
geen zelfbedachte OSRS-melding verwachten. Details + hashes in PHASE7-SOURCES.md.
