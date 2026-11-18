# Fase 7: bewijs en grenzen

## 7.1 Wisp

Cache 2686 NPC 12157 (pet) en 15571 (thrall): exact dezelfde modellen [49222,49218,49221,49224],
idle 10230, walk 10233, schaal 40/40. Bronbytes en SHA256: phase7-sources/wisp-comparison.json.
De bestaande pet-emote attack is 10234 NPC_WHISPERER_01_ATTACK_MELEE_01 (pet_emotes.tsv), geen magie.
De andere geimporteerde emotes 10249/10253/10262 zijn bind/special/death. Het embedded skel_index bevat alleen
thrall idle/walk, geen geimporteerde magic-seq; phase7-sources/existing-wisp-sidecar.txt.

Gameval benoemt 10241..10246 NPC_WHISPERER_01_ATTACK_MAGIC_01..06. Cacheduur:
10241/10242 = 90 clientcycli; 10243/10244 = 60; 10245/10246 = 30. 10241 bevat twee bron-framegeluiden.
Whisperer/Strategies beschrijft reguliere magievolleys, maar bewijst niet welke variant de DMM-thrall kiest.
**Gekozen advies: 10241, de eerste volledige magieworp; toewijzing aan DMM blijft ONZEKER**.
Dit is geen claim dat de wiki een seq-id noemt. Wiki-revisies/datum: phase7-sources/revisions.json.

Het onderbroken werk voegde een smalle modus aan de BESTAANDE OsrsPetImportTool toe, geen aparte tool.
Herbruik rig/skin/modellen/base; bak alleen nieuwe frames tegen bestaande labels, geen volledige NPC-herimport.
De pet-melee-seq hergebruiken voor magie zou het verkeerde aanvalstype tonen. Daarom dit beperkte advies.
De bestaande client leest een gedeelde skel_index: een nieuwe LOGISCHE seq-regel vereist een fysieke APPEND
van die groep. Dat is een REPLACE per cache, ook als alle oude bytes exact prefixgelijk blijven.
**De harde eis 0 fysieke REPLACE is daardoor niet gehaald**; geen semantische CREATE als fysieke CREATE rapporteren.
De tool weigert elke andere REPLACE; indexappend heeft een checksumguard, rig/skin moeten bytegelijk zijn.
Geen clientprotocol uitgebreid of bestaande data stil hernummerd. Claude beoordeelt deze afgebakende import bij review.

## 7.2 Chronicle

Wiki Chronicle rev15366319 (2026-10-05T22:39:10Z), Transcript:Chronicle rev14828423
(2024-12-28T09:03:12Z). Bronkopieen en revisions.json in phase7-sources.
Webzoekopdrachten: `OSRS Chronicle teleport animation video`, `osrs chronicle teleport`.
Twee gevonden OSRS-opnames van VatoMato, 2023-09-25:

- https://www.youtube.com/watch?v=qzFlKMFGZOU&t=54 : 54.96-55.44 armen uit, rond55.60 paarse bol,
  55.84-56.80 gekrompen/verdwenen; rond56.96 montageknip naar Champions Guild.
- https://www.youtube.com/watch?v=0IN6OGvoXCE : langere versie van dezelfde opname, geen onafhankelijke timingmeting.

Download: yt-dlp -f134 (eerste poging worst[ext=mp4]/worst gaf geen format; formatlijst gevolgd door134 werkt).
Lokale downloads staan alleen in build/dmm2/phase7-videos; SHA256/URL in chronicle-videos.json.
Contactbladen chronicle-detail.png en chronicle-full-detail.png zijn eigen frame-extracties.
**Visuele match/inferentie**: gewone HUMAN_CASTTELEPORT714 / reverse715, spot TELEPORT_CASTING111.
Cache714=79 cycli,715=78; dit bewijst de clientanimatielengte, niet de serververtraging tot aankomst.
De montage verhindert een exact tickgetal. Geen precieze duur/arrivalgfx/audio verzonnen.
**ONZEKER; chronicleAppearance blijft null tot volledige match+timing bewezen** (kaart7.2).
Wel de gewone Wilderness/0-chargecontrole VOOR de AV-gate geplaatst: ontbrekende presentatie verbergt niet meer
de reeds bewezen transcriptmelding, en verbruikt geen lading. Accountcharges/spend/cardroutes blijven getest.

## 7.3 Advanced weaponry

Trinket_of_advanced_weaponry rev15355050 (2026-09-24T03:52:56Z); File-pagina
rev15123705 (2026-02-08T11:29:28Z). Video 450x600,6.583sec:
https://oldschool.runescape.wiki/w/File:Trinket_of_advanced_weaponry_opening.webm
URL/hashmetadata: media.json; advanced-opening.webm en advanced-contact.png.
Start actie ongeveer2.1sec, hand omhoog rond2.4, rode kristal+gekruiste zwaarden rond3.6, fade rond4.2;
de bestandslengte is GEEN actieduur.

Onderzocht: gameval TRINKET/WEAPONRY/DEADMAN/DM_; seq11444/137xx home-teleportfamilies;
8524 HUMAN_RELIC_UNLOCK en9274 vrouwelijke variant (beide143clientcycli); spot1749/1835/2020/2603/3044/3456;
SIGIL_ATTUNE_COMBAT1970 en modellen3090/38957/40986/43396/50021/55692/57429/49466..49488.
Cachebron: sequence-candidates.json, spot-candidates.json, advanced-spot-scan.json, dm-spot-candidates.json;
eigen model-contactbladen erbij. De modelvormen/kleuren leveren geen sluitende match op.
advanced-spot-scan stopt expliciet bij onbekend opcode10 (spot3882); scan daarvoor is niet de hele cache.
**ONZEKER**: geen OSRS-seq/spot/delay toegewezen op basis van alleen een vergelijkbare animatie.
AdvancedAppearance blijft null. De bewezen obstructiemelding wordt nu wel voor de AV-gate gecontroleerd.
Service-tests dekken alle8 uitkomsten, lege scythe/shadow, obstruction, stale/dubbelklik, volleinventory/relog.

## Geen donors

Deze ronde gebruikt OSRS Wiki/API, cache2686 en RuneLite gameval voor gegevens; geen donor voor gedrag of code.

### Aanvullende Chronicle-poging

YouTube's eigen zoekresultaten via yt-dlp ytsearch5 leverden ook 31aelL8GV7I,
esRfe2hUd5g,fdpTieygr2s,8hFWn88c-NA. De eerste extra opname gedownload en bekeken:
https://www.youtube.com/watch?v=31aelL8GV7I&t=92 (OSRSMoneyMaker,2020-04-12).
Contactblad/uitsnede en metadata+hash in phase7-sources/chronicle-31ael-*.
Menu verdwijnt rond92.7, kaartwissel93.6; het personage toont geen volledige krimpfase in deze opname.
Die timing strookt niet met de vorige vorm/cacheduur en is dus geen betrouwbare tickmeting.
Geen exact teleportcontract afgeleid uit een montage of versneld/uitgedund beeld.

### Aanvullende cachecensus advanced

Read-only `phase7_red_models.py`: 1844 verschillende spotmodellen uit volledig decodeerbare spotdefs;
39 spotdefs met onbekende opcodes expliciet uitgesloten (red-spot-census.json). 342 rode static meshes
op zes contactbladen bekeken; daarnaast62 door spotrecolour rood gemaakte modellen bekeken.
ModernModelDecoder.decodeBody is de bron voor de onderzoeksdecoder; dit is geen importer of runtimecode.
Geen sluitende combinatie van rode steen en grijze gekruiste zwaarden. De relevante relicmodellen hebben
geen verborgen alpha255-kristalvlakken (advanced-hidden-faces.json/png). Bronitemmodel49493 is een vlakke grijze steen.
NPC-modelzoektocht naar49493/49495/49494/49466/49497 leverde geen NPC op; advanced-npc-candidates.json.
Ruwe seq-zoektocht naar hand-override28567 of28567+512 (beide op6/7) leverde niets op;
advanced-hand-overrides.json. Dit bewijst niet dat het effect ontbreekt: tijdelijke item/modelpresentatie of
een andere familie blijft mogelijk. Geen lokale id/seq/gfx verzonnen; status blijft ONZEKER.

### Vierde Chronicle-video

https://www.youtube.com/watch?v=esRfe2hUd5g&t=50 (The Lumbridge Knight,2023-07-06),
OSRS F2P Fast Travel Guide. Gedownload format134; metadata/hash+contactbladen chronicle-travel-*.
51.9 menu,52.4 armenuit,52.7 verdwenen,53.0 aankomst met armenuit. Deze bewerkte gids geeft weer
een veel kortere tijd dan de bronanimatie714=79cycli, dus geen betrouwbare server-tickmeting.
De pose ondersteunt de standaardteleport-inferentie, niet een precieze duur. Geen nieuw AV-contract ingevuld.

### Advanced: bronroute gevonden in scenery

De spotanimzoektocht was de verkeerde namespace voor het zwevende wapeneffect. Cache2686 plus RuneLite
`ObjectID1.java` (niet alleen het oudere ObjectID.java) geven:

| OSRS loc | Gameval | Bronmodellen | Bronanimatie |
|---|---|---|---|
|49564|DEADMAN_BLIGHTED_WEAPONS_LOOT_CYCLE|49489,59964|10426 FX_CYCLE_6_ITEMS|
|49565|DEADMAN_BLIGHTED_WEAPONS_LOOT_CHOSEN_WEAPON|49491,59964|geen|

Beide hebben de naam `Loot!`. Het eigen contactblad advanced-loc-models.png toont de wapenoverlay en rode
effectvlakken. `phase7_loc_sources.py` leest alleen; advanced-loot-sources.json bevat volledige bronbytes,
SHA256, modellen en animatieverwijzing. Seq10426 heeft24frames/150clientcycli (5serverticks clientlengte);
10425 FX_CYCLE_6_ITEMS_EXTENDED heeft48frames/570cycli, maar de gevonden loc verwijst daar NIET naar.
Daarom importeert de benoemde batch alleen10426 en niet de langere variant.

PREFLIGHT7.3: bestaande OsrsFxImportTool SEQ_BATCHES `dmm2-advanced-loot` = [10426], daarna bestaande
OsrsLocImportTool BATCHES `dmm2-advanced-loot` = [49564,49565]. Geen apply door GPT-A.
Exacte PLAN-opdracht en resultaten volgen in PHASE7-ADVANCED-PLAN.md.

Dit lost de identiteit van de scenery op; het bewijst niet de spelerpose, plaatsing, gekozenwapenmodelwissel
of serververtraging. Het huidige AdvancedAppearance-contract heeft alleen een playeranimation+ticks en
kan deze scenery nog niet correct presenteren. **Runtime blijft ONZEKER/null** totdat die delen bewezen
en het contract uitgebreid zijn. Een locanimatie als speleranimatie invullen zou aantoonbaar de verkeerde
namespace gebruiken. Gekozen advies: bewezen objectassets voorbereiden; geen onvolledige opening inschakelen.

### Wisp-update van Claude

Dossier21:4x accepteert expliciet de twee checksum-bewaakte index-appends als minimale uitbreiding.
Claude paste21:51 tx-20261117-205101 toe, EXTRA_SEQ17773, ids.tsv170regels. Dit is externe voortgang,
geen apply door GPT-A. De code blijft via Dmm2Ids.seq(10241) werken; vaste lokale ids staan alleen in
de gepubliceerde mapping en boottest. Toewijzing MAGIC01 aan de echte OSRS-thrall blijft als advies gemarkeerd.


### Laatste primaire broncontrole 22:2x

De API bevestigt ook de update van 4 februari 2026: `Update:Deadman: Annihilation Tweaks, Shellbane Gryphon CAs & More!`,
rev15121071, 2026-02-05T00:58:40Z. De fix maakt de wapens bij de advanced-opening beter zichtbaar;
de wikiwebm is van 8 februari en toont dus het nieuwere uiterlijk. Volledige API-response:
`phase7-sources/advanced-visual-update.json`. Geen playerseq of servertickduur in die update.

Aanvullende webzoekopdrachten: `OSRS Chronicle teleport animation uncut chronicle teleport guide` en
`OSRS "trinket of advanced weaponry" "animation"`. De primaire resultaten voegen bestemmings-/inhoudsbewijs
en bovenstaande visuele hotfix toe, geen ononderbroken timingcapture of seq-id. Geen inferentie tot bronfeit verheven.

### Hervatting 18 november: Undead-weigerteksten

Claude meldde op 17 november 22:52 dat COOLDOWN/NO_TRINKET/NOT_READY stil blijven. De actuele
Wiki-API is opnieuw gelezen: Trinket of undead rev15298165 (2026-08-13T23:52:21Z), Resurrect Greater
Ghost rev15300140 (2026-08-14T04:43:09Z); `Transcript:Trinket of undead` bestaat nog steeds niet.
De response staat in `phase7-sources/undead-feedback.json`. De via web geopende gewone pagina was
aantoonbaar verouderd (random thrall in plaats van drie keuzes); daarom is de API-pin leidend.

Read-only `phase7_undead_feedback.py` doorzocht alle 9784 client-scriptgroepen van cache2686,
zonder leesfouten, op thrall/trinket/undead/resurrect/cooldown. Vier hits zijn farming/status/FSW-
teksten, geen undead-refusal. Bronhashes en strings staan in `undead-client-strings.json`.
Dit sluit een servertekst niet uit. **ONZEKER: geen letterlijke OSRS-weigertekst gevonden**.
Gekozen advies blijft de bestaande stille weigering behouden, zoals Claude voor andere onbewezen
refusals op 17 november 18:2x besloot; geen spreukenmelding ten onrechte aan dit item toeschrijven.
COOLDOWN verbruikt geen trinket en verandert de bestaande actor niet; de gerichte service- en
relogproeven leveren het serverbewijs. Geen extra fictieve melding of requirement gebouwd.
