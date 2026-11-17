# CLUE - actuele overdracht aan Claude, 17 november

ChatGPT-B, `gpt-b/clue-scrolls-7`. De volledige kaart hoort bij B.
Claude doet cache-apply, testclient, merge en live. Geen volledige AF-claim:
Q1/Q8 en de hieronder genoemde client/live-bewijzen blijven expliciet open.
Dit is de actuele overdracht. Oude voorstellen en voortgang staan uitsluitend
in [het historische dossier](history/CLAUDE-HANDOFF-through-20261117-2054.md).
**Voer geen oude CREATE-plannen opnieuw uit.**

## Huidige reparaties en bewijs

* `9c15f3b2c`: Wiki-redactieprefix verwijderd uit 20 hints; 23 emote-stappen
  totaal, waarvan drie al correcte tekst hadden. Generator ook gecorrigeerd.
* `35d37c1ca`: zolang Mimic niet gereed is openen elite/78-kisten gewoon met
  hun normale tabel; geen onbeschikbare-Mimic-verwijzing, worp of omzetting.
  Drop Viewer gebruikt dezelfde gate. Opt-in/RECEIVED/pogingen blijven behouden.
* `aea40c2b5`: kaarten, fallbackkaart, schuifpuzzel en lightbox gebruiken
  MAIN_SCREEN (fixed 548:9, resizable 746:12). Alle harde puzzelidentiteiten,
  inclusief 2795 -> 25022, hebben Open. Losse dozen openen een persoonlijk
  speelbaar bord; zonder actief cluecontract ontstaat geen cluecredit.
* `9d0c257e2`: alleen de nieuwe testtelling gecorrigeerd van 20 naar 23.
* Claude clientronde 2, dossier 17-Nov21:4x: in RESIZABLE kaart medium
  'West of the Crafting Guild', vier losse puzzeldozen en gewone 78-kist
  opnieuw getest; alle vier fouten uit ronde 1 opgelost. Bewijs door Claude:
  `C:/RSPS/screens/CLUE-R2/sheet.png`, `03b-kist78.png`.

Integratie 193256: 90 tests, 89 groen / 1 fout in de toenmalige testtelling;
alle spelcode/scripts compileerden, 5598 inputhashes ongewijzigd. Geen groene
acceptatie van die run. Herhaling 195429 meldde remote BUILD SUCCESSFUL maar
haalde na sessieonderbreking geen verse XML/summary terug: niet geaccepteerd.
Nieuwe 13-klassenrun **215433 is geaccepteerd: 92 tests groen**, geen
failures/errors/skips/missing; alle 13 XML-rapporten vers en alle 5598
inputhashes gelijk. Verplichte rgradle-route, remote BUILD SUCCESSFUL6m32s,
spelcode en alle scripts gecompileerd/gevalideerd. De trage rapportoverdracht
is volledig afgerond; geen proces is gestopt. Bewijs:
`validation/integration-20261117-215433.json`.
De twee geraakte klassen zijn ook onafhankelijk tegen vijf vers gecompileerde
productieklassen uitgevoerd: **20 tests groen**; bewijs
`validation/casket-client-actual-source.json`.
Testcommit `71cc0ce0e`; handoff `a26fbf3f3`, assetbewijs `d52179c9c`.
De runner weigert voortaan oude lokale XML als nieuwe remote bewijsvoering.

## Cache: toegepast, niet opnieuw importeren

Claude heeft de volgende batches in BEIDE testcaches toegepast en de mappings
in deze branch gecommit. Actuele identiteit komt uit `data/cfg/clue/*.tsv`;
PREFLIGHT-voorstellen zijn geen runtime-idbron.

| Onderdeel | Commit / transacties | Actuele identiteit |
|---|---|---|
| 248 bronitems / 443 defs + core | e71b17b93; tx162527/162707 | 78-scroll 25274, kist 25275 |
| Itemmodelherstel >65535 | a944dfc8f; tx170303/170405 | 252/252 modellen geverifieerd; ids gelijk |
| Goud/onyx presentatie | Claude tx170610/170639 | 25274/25275; ontwerp N415 |
| 10 NPCs | 3b0814176; tx162827 | 15321..15330, `npcs.tsv` |
| 23 STASH + Watson + setting + bucket | ea9eb6e19 | Watson 14406 -> 15331/15332; control2409; STASH control2408; setting261:26-28 |
| Reward/tekst/26kaarten/puzzel/Watsonpapier | 00b9d919b | reward1207, tekst1208, puzzel1235; `interfaces.tsv` |
| Chart/lightbox/16bewaker-seqs | cb3fcbcd2 | chart1238/control2410, lightbox1239/control2411; `combat-assets.tsv` |
| Bloodhound Dig | 363749298; tx175755/175836 | seq14498 -> 17769; NPC14631/14844 vierde optie Dig; `pet-dig.tsv` |
| Verplaatste Mimic-kamer | 363749298; tx175928 | bron10819 -> regio4160, Keyhole34727 -> 66998 |

Alle tx-nummers hierboven hebben prefix `tx-20261117-`.
Clientpatches wide IF3/model en loc-opcode242 staan volgens Claude in
clientcommit `c1de063f`. Ze moeten samen met deze cache worden gereleased.
Native regio10819 nooit overschrijven. Beide geplaatste kamers hebben read-only
250 plaatsingen/6 named defs geverifieerd; bewijs `validation/mimic-arena-installed.json`.
B publiceerde geen arena-runtime-mapping; Claude mag de read-only verifier uit
[MIMIC-ARENA.md](MIMIC-ARENA.md) gebruiken en pas na collision/render-controle
publiceren. Dat schakelt het gevecht niet in.

**Geen nieuwe cachebatch vereist voor de huidige clientreparaties of tests.**
Exacte bronids/batchcommando's en journals blijven bewaard in het historische
handoffdossier, `ITEM-PREFLIGHT.tsv`, `NPC-PREFLIGHT.tsv`,
`COMBAT-PREFLIGHT.txt`, [BLOODHOUND-DIG.md](BLOODHOUND-DIG.md) en
[MIMIC-ARENA.md](MIMIC-ARENA.md). Geen oude voorstel-ids handmatig invullen.

## Wat de speler krijgt

Vijf niveaus: easy/medium/hard/elite/78 (= OSRS master); geen beginner, Charlie,
beginnerbronnen of beginner-logpagina. OSRS-tabellen/rollen met eigenaarsexceptie:
kits alleen winkels, hun uitkomst wordt opnieuw gewogen over toegestane tabel;
3rd Age/gilded ook uit kisten. Dezelfde echte kansen gaan naar de bestaande
Drop Viewer-opmaak. Zeven logpagina's = vijf tiers + shared + scroll cases.
Scroll boxes/cases, inventory/bank/grond/graf/Death-bezit, NPC/skilling/impling-
verwerving, Watson, STASH, Falo/Sherlock en puzzels zijn gekoppeld.

107 bronrecords = 38 bronvaste oorspronkelijke + 69 unieke toevoegingen;
29/29/25/12/12 per niveau. Drie torn parts vormen samen een keuze. Offline
bronpredicaten + 72 gedecodeerde echte definities lieten 102 records / 100
gegroepeerde keuzes toe. Verse herhaling22:59 met14huidige bronpredicaten:
29easy/29medium/25hard/10elite/9master records (7masterkeuzes na torn-grouping).
Dit zijn38oorspronkelijke+64beschikbare nieuwe records; vijf van69toevoegingen
zijn bewust uitgeschakeld. Exacte hashes/bewijs:
`validation/applied-step-predicates-20261117-215753.json`.
Dat is geen boot-, registratie- of clientbewijs.
`STEP-HANDLER-AUDIT.json`, `ORIGINAL-POOL-AUDIT.json`, `EXCLUDED-AREAS.tsv`
en `NPC-SOURCE-GAPS.json` noemen de overige bron-/gebiedsuitsluitingen.

78-opening: echte OSRS-jingle **193**, Wiki `Treasure! (Treasure Trails)`;
broncache2686 index11/group193 en lokale jingle zijn byte-identiek
(`ui-asset-evidence.json`, SHA b16c26f35d53799ff189e62e99a8b5fcf8468527d75de7dc54de054c0501a192).
De gouden wereldmelding geldt voor de mega-rare uitkomsten uit de mastertabel.
Geen willekeurig gekozen gfx. De nieuwe transactietest selecteert een echte
3rd age axe-uitkomst en controleert beide accounts, ??n jingle/log/teller en
geen tweede betaling of melding via een stale klik; gewone loot wordt niet goud.
Dit packetbewijs vervangt geen gehoorde jingle of bekeken gouden melding.

## Expliciete besluiten en brongaten

* **Q1, eigenaar15-Nov1A / Claude17-Nov18:2x:** Mimic-gevecht UIT. Candy/forward-
  kans, landingpatroon en ballistiek zijn niet bewezen uit de twee montagevideo's.
  `ENCOUNTER_IMPLEMENTED=false` en null-bronvelden blijven staan; gewone kisten
  blijven openen. Opt-in/pogingen/arena/candy/minions/death/reward-backend zijn
  aanwezig, maar er is geen OSRS-correcte encounteracceptatie. Geen verdere
  videometing volgens Claude's laatste besluit.
* **Q8:** vijf stappen niet geselecteerd: twee elite-coordinaten
  02.35S11.52E/05.07S13.26E, twee master-coordinaten
  03.26N12.18E/03.50N09.07E en master Jorral. Per-NPC-aanval/gfx/projectiel-
  bewijs en elite-keuzekans ontbreken. Geen verzonnen rig- of 50/50-defaults.
  Zie `COMBAT-SOURCE-AUDIT.md`; complete backend blijft behouden.
* **Q7:** de verborgen OSRS-lightboxgenerator/typeweging is niet gepubliceerd.
  De gekozen compatibiliteitsverdeling (gelijke twee typen/vier plaatjes,
  oplosbare aanvankelijk onopgeloste netwerken) staat met beperking in
  `LIGHTBOX-SOURCE-AUDIT.md`; geen bewezen verborgen OSRS-verdeling geclaimd.
* **Q5 besloten:** geen POH Study/chart of nutteloze blueprintverkoop;
  Watsons noticeboard blijft de STASH-route (eigenaar10-Oct, Claude15-Nov).

## Client- en breekronde voor Claude

1. Fixed EN resizable/Stretched: alle kaartvensters en tekst/emoteopdrachten,
   X/Esc, masterplaatjes/lightboxknoppen; puzzelgeluiden1859/2304 en576/580.
   Ronde2 bewees vier reparaties in resizable; niet de hele kaart.
2. Vijf kisten met lege/volle inventory, stacked/dubbelklik: ieder item exact
   eenmaal in inventory of private overflow; teller/log en jingle193.
   78 mega-rare: gouden melding ook op tweede account; normale loot niet goud.
   Muziek/jinglevolume aan. Elite/78 met Mimic UIT mag nooit verwijzen/blokkeren.
3. Alle zeven logpagina's/scrollbars/tellers, volledige bronitems/cases,
   viewer-kansen op twee accounts met verschillende cluecapaciteit; winkel/GE,
   examines/trade/death-venster. Geen wijziging aan viewer-opmaak.
4. Echte NPC-uitgifte -> puzzel oplossen -> sluiten -> inleveren; verkeerde
   clue/gear/antwoord/NPC, relog, bank/drop/dood/vervanging/tweede account.
   Losse 25022/25279/25381/25363..25365/25448 en lightbox25366: spelen kan,
   credit niet. Bank20282 weigert met de bronmelding zonder itemverlies.
5. Watson eerste/herhaalde dialoog, vier losse/partiele/duplicate hand-ins,
   inventory vol/stored master, Check/NPC Contact/relog, persoonlijke menus
   op twee accounts, statistics/pinboard (laatste naam) en STASH-chart.
   Alle23units bouwen/vullen/legen, equipped-setting, verkeerde set, bank vol.
6. Gewone stappen per type/tier via ADMIN `::cluestep <tier> [list|id|tekst]`:
   eerst normale clue vasthouden, geen pending challenge/puzzel/gevecht.
   Bijvoorbeeld `::cluestep hard BLUE GRIM`, `::cluestep 78 list`.
   Dig/agent: verkeerde plek/emote/gear, kill + juiste vervolgactie vereist;
   combatgear tussendoor toegestaan. Wildy singles met drie accounts/PJ en
   queued projectile/familiar; geen globale npc-uitzondering.
7. Droppen/oppakken/herloggen, twee clues zelfde tier, boxes/casescapaciteit,
   PvM/PvP-dood D1-D20 binnen/buiten wildy; totals inventory+bank+grave+Death
   gelijk aan bedoelde verliesregels. Entrana gewone tabellen, 78 geweigerd.
8. Bloodhound beide vormen: Dig zonder spade/Call follower, verkeerde tegel,
   relog/dismiss/death/tweede account, drie embedded sounds; hard-dig guard intact.
   Arena alleen inspecteren: bronplane1, wide/steep render, muren/deur/5x5-
   collision, Keyhole/ambience2124, twee private instances, dood/logout/terug.
   Q1-gevecht niet inschakelen om de kamer te bekijken.

`CURRENT-ACCEPTANCE.md` bevat de component/ketenmatrix. A6-A9 en livebewijs
blijven Claude's werk. Bronnen = gepinde OSRS Wiki/cache; donors alleen codehulp.
Geen nieuwe eigenaarstoestemming nodig; geen blokkade op nakijk/cache-ids.
