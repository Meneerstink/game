# DMM-PAKKET-2 — ChatGPT-A, fase 1, 16 november 2026

Onderzoek en voorstel; GEEN bouwakkoord. Branch `gpt-a/dmm-pakket-2`, worktree `C:\RSPS\work\gpt-a`.
Alleen onderzoeksbestanden toegevoegd. Geen gamecode, cache, `items.yml`, saves, logs, `data/ge`, `game.yml`, server, client of live gewijzigd.
De ongevolgde map `tools/clue/` bestond bij hervatten en is onaangeroerd. Geen donorcode gebruikt.

## 1. Uitkomst voor de regie

De opdracht omvat zes OSRS-DMM-trinkets (advanced weaponry, fairies, undead, avarice, fortuity, vengeance), acht corrupted weapons met geladen/ongeladen/noted-vormen, khopesh, de reguliere OSRS amulet of fire, drie NPC-diensten, twee verschillende valuta-systemen en starteruitrusting. Burden-trinkets N423 vormen een afzonderlijk bestaand eigenaarsontwerp.

Belangrijkste correcties op het vooronderzoek:

* `Corrupted weapons` redirect naar **Trinket of advanced weaponry**. De acht individuele pagina's zijn wel apart vastgelegd. Canonieke hoofdletters zijn van belang bij scythe en shadow.
* Fortuity **50M permanent / 10M Annihilation** is een verschil tussen spelvarianten, geen tegenstelling die alleen een nieuwere revisiedatum oplost. Het incomplete transcript noemt alleen 10M.
* Undead geeft sinds 21 januari 2026 **keuze uit drie thralls**, hoewel de examine en de samenvatting op de Annihilation-pagina nog 'random' zeggen.
* Khopesh **33041** is de DMM-versie met **60%** special. **30388** is de Leagues-versie; die mag niet als gedragssjabloon dienen.
* **Amulet of fire 34425 is reguliere OSRS-content van 2 september 2026**, met een Fire ruby-drop en een Runecraft-recept. Geen DMM-chestbeloning verzinnen.
* OSRS Nigel heeft **blood money voor drie armour-unlocks** en daarnaast een **Reward Store met Deadman Points**. De huidige Breach Trader verkoopt weer andere spullen. 'Breach-punten vervangen' bepaalt nog niet de bestemming van zijn assortiment of de valuta van de Reward Store.
* De huidige drie uitgesloten OSRS-trinkets worden niet alleen niet gedropt: `RemovedItems` **verwijdert bestaande exemplaren bij inloggen**. Die route moet tegelijk met de herintroductie aangepast worden.
* D14 noemt **burden trinkets en ornament kits**. D14 beschermt niet automatisch alle OSRS-DMM-trinkets. Permanente OSRS-DMM-trinkets worden bij dood dust; het combineren met onze PvM-grafregels vraagt een expliciet besluit.
* Safe Deposit Box is een eigen **gedeelde opslag van tien individuele items per speler**, bereikbaar bij beide NPC's; een normale deposit box naar de bank is iets anders.

Dit rapport bevat een uitvoerbaar voorstel en benoemt de resterende bron- en eigenaarskeuzes. Het geeft GEEN implementatie-, runtime- of live-PASS. Ook de nog niet vastgestelde UI/icon/sound-koppelingen hieronder zijn geen ingevulde feiten.

## 2. Bronnen, bewijs en afbakening

Wiki via publieke API, User-Agent `rsps-research`, opgehaald 16-11-2026. **Bijlage A** geeft per pagina revisie-id, revisiedatum UTC en permanent link. `wiki-manifest.json` bevat de SHA256 van de opgeslagen wikitext; `wiki/supplement.json` bewijst redirects. Afwezig gemelde transcriptpagina's zijn niet als bronnen gebruikt.

Cache: `C:\RSPS\import-source\openrs2-2686\cache`; archive 2/group 10 items en group 9 NPC's, archive 3 interfaces. Hashes in `source-cache-provenance.json`; item/NPC-defs in `cache2686-items.json`/`cache2686-npcs.json`. Modellen, sequences, spotanims en geselecteerde sprites zijn op aanwezigheid plus SHA256 gecontroleerd in `source-assets-presence.json`. Aanwezigheid bewijst niet dat een animatie of geluid bij een bepaalde handeling hoort.

Lokale cache = **de cache in deze worktree**, niet een livecontrole of vergelijking van beide distributiecaches. `local-items.json` bevat de oorspronkelijke brede scan (incl. burden-trinkets); `local-all-source-name-matches.json` vult de naamvergelijking voor alle geselecteerde bronitems aan. Een gelijknamige gewone Tuna is niet dezelfde definitie als starter-Tuna. `items.yml` is apart op de relevante namen gecontroleerd. Geen cache-import/allocator/CacheTransaction uitgevoerd, ook geen `--apply`.

Code: geen `.codegraph/` of `graphify-out/` in deze worktree. Daarom rechtstreekse broninventaris; de index van de andere worktree is niet gebruikt. `code-inventory.md/json` is het hervatte bewijs; `inventory-supplement.md/json` scant main/test Kotlin plus `commands.yml` en `new_player.yml` opnieuw, met regelnummers en bestandshashes. Een commentaarhit is geen actieve afhankelijkheid. De functionele beoordeling staat hieronder.

Eigenaarsbesluiten: **Bijlage C bevat letterlijke passages**, met bronbestanden en bronregelnummers. `owner-decisions-literal.txt` bewaart ook de ruimere historische extractie. De oude doodinventaris van 24 oktober is geen vervanging voor D1-D20 van 1 november.

## 3. Corrupted weapons — OSRS | wij | verschil | actie | cache | risico

Alle acht komen met kans **1/8** uit advanced weaponry. Bron: de individuele itempagina's plus de redirect/hoofdpagina in bijlage A. Alle acht hebben value 250.000. Bijlage B noemt modellen en note/placeholder-links; bijlage D geeft ALLE aanval-/defensiebonussen per toestand.

|Wapen, OSRS-id|Eisen; tempo/bereik|Gedrag/afwijking van basiswapen|Wij / te bouwen|
|---|---|---|---|
|Voidwaker 28531|65 Attack; 4 ticks / 1|Bonussen ongeveer 20% lager; gebruik expliciete tabel, niet generiek afronden.|Niet aanwezig in lokale naam/cfg-scan; shared Voidwaker-special uitbreiden na ids.|
|Dragon claws 28534|40 Attack; 4 / 1; 2h|Lagere bonussen, strength 48.|Bestaande `DragonClaws.sliceAndDice` + spec-plugin voor gewone claws; nieuwe variant registreren en op huidige OSRS-special testen.|
|Armadyl godsword 28537|65 Attack; 6 / 1; 2h|Slash/strength 106, crush 64, prayer 7.|Bestaande AGS-plugin; actual code gebruikt 1.375 en 2x accuracy. Oude header zegt iets anders: code en afronding beoordelen.|
|Twisted bow 28540|70 Ranged; 6 / 10; 2h|Ranged attack 56, ranged strength **2**, niet 16.|Bestaande bow/formula/requirements/ammo-routes herkennen deze id nog niet. Magic-level-passive gericht vergelijken.|
|Scythe 28543 charged / 28545 uncharged|60 Attack +70 Strength; 5 / 1; 2h|300 blood runes per 100 charges; geen blood vials. Charged onruilbaar, uncharged ruilbaar.|Geen corrupted- of normale Vitur-combatimplementatie aangetroffen in de onderzochte weapon-routes; eigen scythe-mechaniek en chargecyclus nodig.|
|Tumeken's shadow 28547 charged /28549 uncharged|75 Magic; 5 / 8; 2h|Worn magic accuracy/damage x2, in ToA x3; 2 soul +5 chaos/cast, max20.000 charges.|`PoweredStaves.SHADOW` bestaat voor gewone ToA-shadow (85 Magic, x3/x4). Variant expliciet modelleren, niet de bestaande booleaan blind hergebruiken.|
|Dark bow 29599|50 Ranged; korte range6; snelheid bronconflict hieronder|Ranged attack95; twee pijlen; korter bereik en sneller dan gewone dark bow.|`Bows`, `BowType`, `RangedCombatStrategy`, ammo- en specregistratie uitbreiden; geen algemene dark-bow range10 laten overrulen.|
|Volatile Nightmare staff 29602|50 Magic +20 HP; speed5; spec range6|Magic attack16, defence14, damage15%; Immolate55%, accuracy1.5; geen runes; base max `min(58,floor(58*visibleMagic/99))` volgens formulebeschrijving.|`NightmareStaves`, special reach en requirements uitbreiden; gewone versie heeft spec range10.|

Specials van de basiswapens, als vergelijkingsbron: Voidwaker 50% energie, gegarandeerde magic-hit van 50–150% melee max, 2 Magic XP/damage; AGS 50%, dubbele slash-accuracy en **opeenvolgend afgerond** 1.1 en1.25 damage; claws 50%, vier hits met de bestaande OSRS-distributie; dark bow 55%, twee pijlen en arrow-afhankelijk effect. Tbow/scythe/shadow hebben geen gewone damage-special zoals deze wapens. De corrupted-pagina's beschrijven niet elk geërfd detail opnieuw: hergebruik van de ouderimplementatie is een **voorstel**, geen afzonderlijk gemeten bewijs voor elke corrupted-special.

Degradatie: geen bron gevonden voor een algemene 30-minuten-verdwijnregel van deze acht. Scythe/shadow hebben rune-charges; de andere zes mogen niet in de oude native-corrupt degrade/delete-lijsten komen. N457 gaat over native Ancient Warriors-corrupt, niet deze acht.

Dood/PvP: tradeable/charged-untradeable toestand is relevant vóór death-resolve. Scythe/shadow vereisen een bronbevestigde conversie van charged item en resterende runes, plus attributenbehoud bij bank/trade/dood. Gewone shadow verliest zijn multiplier in toegestane PvP-situaties; niet zonder bewijs aannemen dat de corrupted powered staff in iedere PvP-context bruikbaar is. Onze D1-D20 zijn leidend waar expliciet anders besloten. Dit wordt een eigen death-testmatrix, geen generieke 'untradeable dus Perdu'-route.

**Bronproblemen voor bouwdeel wapens:** corrupted dark bow heeft in DEZELFDE revisie infobox speed6/range10, prose range6/twee ticks sneller en CombatStyles speed7/range6. Advies: range6 aanhouden als onderbouwde gemeenschappelijke waarde; snelheid eerst verifiëren met OSRS-observatie/aanvullende Jagex-bron, dan besluit noteren. Exacte corrupted-special-inheritance, charge-dooddetails en AV-timing waar niet beschreven blijven expliciete bronvragen; geen willekeurige constants invullen.

## 4. Alle trinkets

Disambiguatiebron noemt zes DMM-soorten plus dust. Crystal trinket is een questitem en valt buiten DMM2. Onze vijf burden-trinkets blijven een aparte familie met eigen bind/BoB/doodregels.

|Trinket / OSRS-id|OSRS|Wij en verschil|Actie en cache voor Claude|Risico|
|---|---|---|---|---|
|Advanced weaponry28567|Open: 1 van8, 1/8 elk; omgeving vrij van obstakels/spelers, gezicht noord; breach + chest.|Ontbreekt, oude rare-table verwijdert zijn gewicht.|Import item/note/placeholder; server opening als ondeelbare transactie met collision/bodycheck, lock tegen dubbelklik, animatie annuleren veilig.|Dupe/verlies bij interrupt, logout, volle inv; geen resultaat genereren vóór commit.|
|Fairies28564|Fairy-ring/Spirit-tree/Tool-leprechauns/Last-destination; maximaal wildy30.|Local23822, model63897, vier handlers aanwezig; shared teleport/Tele Block. Dramen-vrij gebruik en leprechaunlijst zijn bestaand ADAPTED beleid.|Hergebruik, bestaande routes toetsen; huidige bron heeft value100k. Geen tweede import.|Teleblock/deadman7s/instance-lock bypass, last-destination ongevalideerd.|
|Undead28570|Keuze Lil Zuk(melee)/Lil Kal(ranged)/Lil Wisp(magic), greater-thrallgedrag; cooldown10s, geen level/rune-eisen; hits0–3 altijd raak. Sigil kan0–15 geven.|Local23824/model63903/3 cacheopties, maar uitgesloten en gepurged; geen actieve handler gevonden.|Geen nieuw item nodig, wel NPC15569/15570/15571 + animatie/gfx/audio en minion-engine. Geen sigil implementeren onder deze kaart zonder bestaande unlockbron.|Samenloop met onze familiars/pets, doelwitregels, dmg-credit en PvP; keuze voor followerkanaal expliciet.|
|Avarice33044|Auto-loot, optional noting, Value-drempel; ook bones sinds18feb; gebruikt OSRS looting bag waar mogelijk.|Local23823/model63900, opties al goed; geen handler; uitgesloten/purge.|Hergebruik item; centrale owned-drop-afhandeling hook, settings persistent, inventory-only wegens N561. Geen looting bag terugbrengen.|Geen andermans grondloot; dubbele toekenning of foutieve noted stack bij volle inv; Value-definitie bron nog incompleet.|
|Fortuity33047 inactive/33050 active|Permanent50M, Annihilation10M; **+1 op multiplier** (6x→7x), niet x2; charged teleport7s, alleen7s en niet extra out-of-combat-wacht; lading naar killer bij Annihilation PvP.|Local inactive23825/model63911; active ontbreekt. Geen gedrag, purge inactive.|Import active-vorm + links; charge/uncharge atomic; bonus in gedeelde drop-rate-provider én Drop Viewer; gate vóór combat-refusal als bron dat voorschrijft.|Gratis multiplier vanuit bank/safebox, verlies/dubbele coins bij dood/uncharge, boost tweemaal toepassen.|
|Vengeance28561; AllStars2-charge33428/1-charge33431|Cast zonder spell-eisen, gedeelde30s vengeance-cooldown. Normale versie niet in Annihilation; AllStars-versie vergaat na2 casts.|Niet aanwezig.|Omdat eigenaar ALLE trinkets vraagt: normale versie meenemen als voorstel; charged eventvariant apart laten kiezen. Bestaande Vengeance spell-service hergebruiken.|Twee onafhankelijke cooldowns, reflect recursie, rune/skill-eis ten onrechte toegepast.|
|Dust30689|Permanent-DMM overblijfsel bij dood; onruilbaar en niet bankbaar.|Niet aanwezig.|Alleen importeren/activeren nadat death-policy vastligt; niet behandelen als normale loot of burden-trinket.|D14 verkeerd toegepast; onverwacht vernietigen van items bij PvM.|

Raakvlak `RuinousBookDrops`: gebruikt dezelfde bossclassificatie als `BurdenTrinketDrops`, maar eigen onafhankelijke, ongebooste rolls (CHEST 1/25, BREACH_MONSTER 1/300, BOSS 1/400, NEX 1/150, REVENANT 1/1000), eligibility en eigendomscontrole; award naar inventory of owned ground met Collection Log/wereldbericht. Dit is geen handler voor OSRS-trinkets. Die eigen roll en de burden-rolls blijven bij herintroductie van de zes DMM-trinkets apart; fortuity mag ze niet zonder nieuw besluit alsnog boosten.

Dropbronnen: `Template:DeadmanAnnihilationBreachDropTable` geeft advanced60/9576, fairies20/9576, avarice10/9576, undead10/9576, fortuity1/9576 (benaderde kansen); rare totaal133/9576, daarnaast de bestaande zes Ancient Warriors-wapenregels. De bron heeft een mega-rare-subroll; eindkansen moeten met die subroll kloppen. Lootgerechtigd zijn de **eerste16** die damage doen; **punten** zijn top100. Dit zijn verschillende aantallen.

Wij hebben nu alleen de zes wapenregels (gewicht32) achter een rare-trigger133/9576. Terugplaatsen van de trinketgewichten verlaagt dus de frequentie van die wapens; niet ongemerkt de oude ~4,2x frequentie behouden. N100 bracket-boost één keer toepassen, dezelfde effectieve kansen tonen in Drop Viewer. Extra chest-list-roll/burden-drops/prayer-scrolls/petregels zijn aparte besluiten, geen reden om ze bij valuta-removal te wissen. Chest-kansen voor nieuwe rewards zijn op de wiki deels Unknown: advies is één gelabelde configuratie met expliciet goed te keuren gewichten, niet een verzonnen 'OSRS-kans'.

## 5. Thunder khopesh en Amulet of fire

**Khopesh33041:** Attack60, stab65/slash110/strength100, speed4/range1. Uit Deadman's chest; Inspect moet bestaan. Normale succesvolle hit:20% kans op vertraagde lightning op de oorspronkelijke doelwittegel,3x3, max50% melee max, zonder accuracy-roll; ontwijken door bewegen, geen XP van lightning. Corpbane: volledige damage op Corp. Special60%: PvM twee initiële hits; bij ten minste één success extra bolt tot100% max. De PvP-beschrijving is apart: initial damage+30%, vier bolts vanaf tick7 daarna elke2 ticks,30–45% van original damage, ontlopen vanaf2 tegels; melee prayer tegen initial, magic tegen bolts. Prose heeft onduidelijkheden over twee hits versus 'original hit', rounding en bolt-snapshot: vóór bouw meten/bronbevestigen, niet de Leagues-special kopiëren.

Wij missen item en mechanic. Hooks: specials/Combat.dealHit, tile-based scheduled hits, target legality/single-multi/guard/protection, hit-credit, Corp-damagefilter, attack-animation/weapon look, Inspect, equipment requirements. Cachebron: invmodel55637, male55757/female55756; note33042/placeholder33043; seq11812 + effectseq11810/11811, gfx3030–3033 zijn aanwezige bronkandidaten; exacte timing/soundbinding moet Claude bewijzen. Risico: lightning die na target move toch homet, spelers buiten toegestane multi raakt, een dood target/relog blijft raken, of XP geeft.

**Amulet of fire34425:**59 Magic om te dragen; magic attack+10 en overige bonussen0. +2 base max voor fire spells vóór gear magic-damage/elemental-weakness modifiers; additief met chaos gauntlets tot+5 bij bolts. Bron zegt ook effect tegen Salarin. Recept:Fire ruby34422 + Amulet of magic +100 fire runes bij Fire Altar,21 Runecraft(boostable),125 XP,3 ticks. Fire ruby van Infernal Mage **1/64**, ook Collection Log; dropboost volgens N100 waar van toepassing. Elemental-amulet-combinatie is een aangrenzende productroute, geen stilzwijgende opdracht om alle vier elementamulets te importeren.

Wij missen ruby en amulet; `MagicCombatFormula` heeft al chaos-gauntlets/elemental-weakness. De plaats van de base+2 moet echt vóór beide stappen komen; gewone powered-staffhits en non-fire-spells blijven gelijk. Cache:34422/note34423, model61875;34425/note34426, inv61877, male61881/female61884; creation seq14497/gfx4020 aanwezig. Server ook Fire-Altar-item-use, Infernal Mage-droptable, Drop Viewer en bestaande slayer-logpagina, equip-eis, GE/trade/note/examine. Risico: verkeerd afronden, magic damage nogmaals opschalen, dubbele rewards bij 3-tick-receptinterrupt. Geen zelfgekozen shop/chestbron.

## 6. Starter pack en drie verschillende 'chests'

OSRS item22330 → bestaande local23881 is het **starter pack**. Onze **PvP-respawnkist** is local24623 met78k+skull. **Deadman's chest scenery** is het bestaande world-event-object. Armour met naam Deadman's chest is weer een borststuk (bron13317 DMM/24189 cosmetic), geen van deze kisten.

OSRS pack: Starter sword22331, Starter bow22333, Starter staff22335 (cache bevat ook28559/28555/28557-versies),12 speciale Tuna26149, Chronicle13660 met10 toegevoegde charges, DMM chest-armour13317, legs13318, Starter cape33063, Dramen staff772, skull33065. Elf vrije inventarisplekken volgens de pagina. Dit impliceert speciale stackbare starter-Tuna en de juiste slotberekening; gewone Tuna361 is geen uitwisselbare definitie. Pack is onruilbaar, Open/Destroy, niet naar Entrana; bij dood uitzetbaar; onbeperkt terugclaimen sinds4feb bij tutors/Brugsen Bursen. De Annihilation-overzichtspagina vermeldt tevens looting bag: ons N561 heeft voorrang, dus die blijft weg.

De sword heeft geen wield-eis, rune-scimitarvergelijkbare stats maar streng PvM-only; bow geen eisen, ranged attack20/strength7, ingebouwde ammo2000 charges, speed4/range7; staff geen eisen, ingebouwde Fire Strike500 charges, speed4/range6 volgens CombatStyles (infobox melee-range1). Alle drie mogen niet tegen spelers. Niet alleen de pack-inhoud wijzigen en lege wapenmodellen uitdelen.

Wij: `NewPlayerStart` geeft eenmalig na introductie/skip/interruption (`deadman_starter_pack_given`); `new_player.yml` en defaults geven1M coins+100 house tabs+skull. `TutorIntro.finish` roept de grant aan. `starter_kit` heeft Open/Destroy/reset-intro en PvP-kist handlers. `PvpRespawnChest`:1 kist/uur/slachtoffer (`pvp_respawn_chest_at`);78k en alleen skull als nergens al eigendom; fallback inventory→bank. Het uur is als **Claude's choice** beschreven, niet als bewezen OSRS-regel. Na uitgevoerde PvP-death met killer wordt die grant gedaan.

Voorstel V2: maak het echte wiki-pack de reclaimbare uitrusting, zonder bag; behoud de expliciete D9-PvP-kist apart. Beslis expliciet of bestaande1M+100tabs een aparte eenmalige introbeloning blijven of vervallen. Onbeperkt claimbare packs mogen NOOIT1M/100tabs/78k genereren. Migratie van ongeopende bestaande packs is nodig om geen oude beloning af te nemen of dubbele cash te geven. Claude doet cache/items.yml; GPT-A de grant/open/reclaim/PvM-only/charges na eigenaarsbesluit.

## 7. Blood money, Nigel, Reward Store, XP-slot

### 7a. OSRS-gedrag

Blood money13307 is een onruilbaar stackbaar item, value1, niet alchable; permanent Deadman geeft **1 per playerkill** (Deadman Mode-bron). Op Nigel gebruiken unlockt de drie DMM armourdelen voor1 per stuk. Transcript heeft branches voor3/2/1/0 nog beschikbare delen; armour is na verlies gratis te reclaimen volgens Deadman Mode. Dit is geen onbeperkte armourverkoop met telkens een extra coin van de speler.

AllStars seizoen3 gebruikte een **teamsaldo** met per-dag/gebied verschillende killscores,death−1 en een finale-shop. Dat is geen bron voor een permanent individuele-walletprijs of een algemene breach-monsterdrop. Bestaande blood-money-items bij dood/groundownership en ondeelbare banking vereisen nadere bronbevestiging; een onruilbaar item automatisch naar onze algemene Perdu-route sturen zou een onbewezen keuze zijn. Geen1,5M blood money per emblem afleiden uit de huidige BreachPoints-inruil.

Nigel8391(main)/8390(DMM) heeft Talk-to/Trade, bij DMM ookTeleport. Wiki sinds3nov:main in GE3152,3512; DMM blijft Lumbridge3245,3200. Eigenaar wil GE: die afwijking is al opgedragen. Plaats passend in onze GE, behoud één spawnbron `world_edits.json`; niet blind de OSRS-tegel nemen met onze hall-layout. Local NPC3681 heet ook Nigel maar is niet de DMM-Nigel; Stiles' tekst noemt zijn vroegere naam Nigel en is evenmin dit systeem.

Alle Nigel-diensten geïnventariseerd: informatie over DMM/dood/protectie, XP aan/uit (HP of combat), Trade Reward Store, blood-money armour-use, dagelijkse tijdelijke-DMM-teleport naar Ardougne/Falador/Seers/GnomeStronghold/Varrock/Kourend, reward-scroll-unlocks en cosmetic add/remove, gratis exit-blueprints na house-theme-unlock, tijdelijke-event accountconversie/reset met waarschuwingen. Die reset/worldtransfer heeft bij ons geen equivalent en mag GEEN spelersave-reset worden. Informatie over bankraids/XP-verlies uit het OSRS-transcript botst met ons spel: niet als feit aan spelers tonen. Selecteer de toepasselijke takken en leg noodzakelijke tekstafwijkingen ter goedkeuring voor. De wiki meldt bovendien een ancient-cape-bug; die niet nabouwen.

### 7b. De zeven Reward Store-beloningen (OSRS buy = sellback)

|Reward bron-id|Prijs DP|Werkende inhoud, niet alleen winkelstock|
|---|---|---|
|Armageddon weapon scroll29625|12.000|Scroll eenmalig consumeren → permanent accountunlock; Nigel onbeperkt gratis AGS/Voidwaker/Dark bow/Volatile uiterlijk wisselen; normale stats/charges behouden; PvP-killer normale variant.|
|Armageddon cape fabric29628|6.000|Permanente cape-ornament-unlock en vrije wissels; PvP breekt cape/verwijdert cosmetic,bron96k killer-coins; D4/D14 en bestaande godcape-regels afstemmen. Ancient Deadman cape via ancient fabric op de juiste cape; transcript is hier inconsistent/incompleet.|
|Armageddon teleport scroll29622|9.000|Consumeert voor permanente Home Teleport-animation unlock; selection via Animation-menu, terugwisselen en na relog behouden.|
|Armageddon rug29619|3.000|POH rug:28Construction,4 cloth+saw/hammer+rug,283XP,5ticks. Verwijderen meubel geeft rug terug; verwijderen kamer geeft hem niet terug. Drie bronobjecten54114/54115/54116.|
|Annihilation weapon scroll33012|12.000|Permanent unlock:toxic staff,corrupted bowfa,crystal helm/body/legs; charged/inactive/uncharged-vormen en charges bewaren; revert naar default colour; PvP normale variant.|
|Annihilation blueprints33015|15.000|Estate agent consumeert bij eerste Wilderness-house-style; permanente styleunlock; geeft exit-blueprints33122, later gratis bij Nigel; portal bouwen met10 iron bars, hammer/saw; geen remove van laatste portal.|
|Annihilation teleport scroll33018|10.000|Permanente Wilderness-lever Home Teleport-animation met selectie en opslag.|

OSRS stock is onbeperkt; sellback sinds14aug2024 **dezelfde prijs**. Het bestaande `PointCurrency`/BUY_NONE is dus geen direct passend Reward Store-model. Transactie moet buy/sellback in dezelfde valuta doen, met inventory/stack-overflowchecks; consumeerde unlocks zijn geen losse scrolls die nog terugverkocht kunnen worden.

Wij: geen reward-id/cosmetic/unlock-handlers voor deze zeven gevonden; wel onderliggende bowfa/crystal/toxic weapons en Construction. Cache-afgeleiden per vorm in bijlage B. Bron-store is DP, niet breachpunten of blood money. **V3 eerst beslissen.** Alle zeven volledig ondersteunen impliceert ook Construction- en Home Teleport/clientwerk; dit mag niet verdwijnen achter 'GPT-A alleen Nigel-dialogue'.

### 7c. XP-slot

Brontranscript geeft twee onafhankelijke toggles met Yes/No-confirmation:combat experience en HP experience; opnieuw spreken schakelt terug. OSRS-combatset:Attack/Strength/Defence/Ranged/Magic/Prayer/Hitpoints. Summoning bestaat niet in OSRS. Ons bestaande `AnnihilationTier.isCombatSkill` sluit Summoning al expliciet uit (VRAGEN 33 = B, codecomment). Advies: die bestaande indeling hergebruiken; Summoning blijft skilling en wordt niet stilzwijgend door het combat-XP-slot geblokkeerd.

Wij hebben `Player.addXp` als centrale route (1070+), met XP-orb en skill/level-up-events erna, geen Nigel-lock. `SkillSet.setXp/setBaseXp` zijn bypasses voor directe setters/rewards. Bouw persistent lockbeleid vóór XP-mutatie/events, inventariseer ook questlamps/directe skillupdates; geen tijdelijke multiplier0 met alsnog taak-events. Combat-level-taken mogen niet als voltooid worden gemarkeerd door geblokkeerde XP; bestaande achievements die door reeds behaalde stats gehaald zijn blijven volgens bestaand gedrag. HP-only moet andere combat-XP doorlaten. SpecialAttackXp, controlled/defensive spreads, Prayer-offerings/questrewards en familiars moeten dezelfde policy zien. Bestaande DP uit Deadman Tasks blijft een ander saldo; niet verwijderen omdat het woord Breach in een task staat.

### 7d. Volledige verwijder-/migratiekaart breach-punten

|Bestand/route|Huidig gedrag|Te doen na besluit|
|---|---|---|
|breach/BreachPoints.kt|`breach_points`, `breach_points_earned`; top100,1/damage,75k lifetimecap; tier5 emblem1.500.000; PointBalance-saturatie.|Retire actieve API, versioned migratie van spendable saldo; lifetime earned niet nogmaals uitbetalen.|
|BreachContribution.kt|Sorteert topdamage met `BreachPoints.EARNERS`.|Neutraliseer valuta-afhankelijkheid; bijdrage/loottoewijzing bewaren.|
|DeadmanBreach.kt:dropLoot|BreachPoints.award bij eligible topdamagers; loot/DeadmanTasks/emblem daarnaast.|Alleen puntenbetaling vervangen; geen breach-event of taken verwijderen.|
|breach_points.plugin|`::breachpoints`, emblem→punten op NPC14478.|Command/UI verwijderen of goedgekeurd vervangen; emblem-destination opnieuw besluiten.|
|breach_shop.plugin|NPC14478,PointCurrency,BREACH_SHOP,24 stockregels; BUY_NONE.|Breach Trader weghalen/vervangen; elk artikel een besloten nieuwe bron/prijs.|
|BreachSim/BreachConformance + eigen tests|Puntensaldo-/damagecaps/specverwachtingen.|Eigen gewijzigde testklassen aanpassen na bouw; geen suite draaien.|
|GeHomeHall + world_edits|Breach Trader plaats en HALL_NPCS; world_edits enige spawnbron.|Claude verwijdert oude spawn/naam/icoon en plaatst Nigel; geen dubbele NPC.|
|grandexchange_unlocks|Ancient sceptre en quartz-route; comment zegt tevens Breach Trader.|Eblis/Archaeologist-route behouden; dubbele winkelbesluiten expliciet overrulen of verplaatsen.|
|store/store.plugin|`givepoints <player> breach <amount>`, `maxpoints` vullen oude balans.|Alle actieve schrijf- en berichtpaden vervangen; geen nieuwe verborgen oude valuta.|
|PointBalance.kt + JsonPlayerSerializer|Wrapped-balance-repair voor `breach_points`; persistente attributes worden generiek geladen.|Legacy sleutel als leesschema voor migratie houden zolang offline accounts kunnen terugkomen; geen batchbewerking saves.|
|commands.yml + QuestTabInfo|Balancecommand en Breach Points/Trader-infotext.|Hernoemen/verwijderen, nieuwe uitleg op werkelijke valuta.|
|PointCurrency + importertools|Algemene shopcode bevat Breach Trader-voorbeelden; historische importbatches/cosmetic looks.|Generieke pointshop niet verwijderen; actieve oude currencylabel verwijderen, historie als historie laten.|
|WildernessHotspot|Legacy hotspot/retired WildernessBreach, geen huidige BreachPoints.betaling.|Geen actief puntensysteem hier geconstateerd; geen onnodige aanpassing.|
|DeadmanChestWorld|Guardian geeft tier5-emblem, geen BreachPoints; event gebruikt breach services.|Bij emblem-bestemming meenemen; world chest niet verwijderen.|
|DeadmanTasks/DeadmanEmblem/StoreCatalogue|Eigen DP; taken BREACH_DAMAGE/BREACH_KILL/COMBAT_LEVEL; DP-credit via eigen currency.|Bewaren tenzij eigenaar uitdrukkelijk anders beslist; Nigel winkel mag niet stilzwijgend hun balans herinterpreteren.|

Huidige24 stockregels:blighted manta5000,angler5000,karambwan3000,restore(4)50000,entangle sack2000,teleport sack3000,vengeance sack3000,ice sack2500;ether250,MSB-scroll100000,maul-handle1250000,Trouver250000,Ancient sceptre1000000;Craw/Thammaron/Viggora uncharged elk2500000;Venenatis fangs/Vetion skull/Callisto claws elk5000000;vijf halos lokaal23858–23862 elk500000. Dit zijn **huidige breachpuntprijzen**, geen voorgestelde blood-moneyprijzen.

Migratievoorstel V1:eenmalig bij inloggen, één schema-versie/marker, lees oude balans, zet om volgens vastgesteld tarief, voeg volledige beloning veilig toe aan gekozen itemwallet/bank, wis oude spendingkeys pas na aantoonbaar geslaagde boeking en persist één consistente snapshot. Bij volle inventory/bank/int-overflow niets afboeken, retry zonder dubbele uitbetaling. Gebruik Long voor rekensommen. Alleen login-code, geen spelersaves openen of aanpassen. Tarief bestaat nog niet:advies 'koopkracht behouden' is nog geen getal. Offlinesaves vereisen blijvende legacy-reader; 'volledig weg' betekent geen actieve earn/spend-routes, niet onleesbaar maken vóór migratie.

## 8. Financial Wizard/Seer en Safe Deposit Box

OSRS Wizard6510/6514/6515/6516/6517/6518/6519/10391 plus unused6703; Seer6485. Beide echte diensten Talk-to/Bank/Collect/Deposit-box. De GE-bankvariant en NPC6510 mogen niet verward worden;6510 is Void Outpost. Eigenaar vraagt beide in GE:één Wizard en één Seer met **dezelfde** per-player safebox. Geen twee keer10 plaatsen.

Bron:10 individuele tradeable items, niet10 stacks;10 air runes vullen10 plaatsen. Onruilbare items weigeren. Items veilig voor dood en niet bruikbaar; skull blokkeert toegang. Transcript Wizard bevat bank/PIN/Collect/place-info/safebox en note-unnoting; Seer bank/PIN/Collect/more bank slots/safebox. 'More bank slots' en delen van Wizard-transcript zijn ontbrekend gemarkeerd:gebruik bestaande bankdienst, geen zelfbedachte OSRS-dialogue. Skull-weigeringstekst staat letterlijk in de bron. Een bestaande normale bank-deposit-box-hook is niet voldoende voor deze dienst.

Wij:geen safebox-container of handlers aangetroffen. Architectuur bestaat: `ContainerKey`, `register_container_key`, `Player.containers`, generieke JsonPlayerSerializer-load/persist en `ContainerStackType.NO_STACK`. Maak capaciteit10 met NO_STACK en serverzijdig amount/slot/item-id/tradeable-validatie. Noted items tellen elk als1; de hoeveelheid mag geen bankstack10.000 worden. Beide NPC's openen dezelfde containerkey. Bank/Collect moeten bestaande normale bank/GE-collection-service oproepen en blijven afzonderlijke opties.

Bij UI openen én elke deposit/withdraw opnieuw skull/afstand/interface/container/slot controleren; skull kan veranderen terwijl venster openstaat. Geen bulk 'all' zonder capcheck, geen verlies bij inventory vol, drag/packet replay/negative quantity/relog. Persist bij overdracht zoals bestaande trade/death. DeathResolver mag alleen carried containers behandelen, niet de safebox. `Possessions` moet items wel als eigendom herkennen voor uniqueness/Perdu, maar geen actieve trinket-,charge-,ammo-,risk-,resource- of combatbonus uit safebox gebruiken. `RemovedItems` en sommige migraties lopen alle containers door:bedoeld migratiegedrag documenteren; protected opslag is geen toestemming om retired items te bewaren. Graven/Death's Office/recovery mogen nooit onbedoeld deze opslag leegmaken.

Cache:OSRS interfaces **230(17 componenten),232(3)** succesvol gedecodeerd, vaste teksten0/10 en sprites829/828/830/170/1041/1042. Dat zijn BRON-interfaces; **geen vrije local667 ids**. Claude kiest clone/import/mapping plus container scripts en packetcomponenten. Gewone bank-interface is al762; niet230 als nieuwe local interface hardcoden. NPC-modellen/heads per variant staan in JSON; Wizard6510 body230,246,301,27139,10218,14565,181,27151,heads63/14445;Seer6485 body408,417,21914,21899,10706,21911,358,437,heads125/136;Nigel8390/8391 body29473,29637,29633,29636,29634,29635,29638,29639,head29640.

Risico:andere containerkey bij laden verliest opgeslagen spullen; NORMAL in plaats van NO_STACK maakt unlimited runestacks mogelijk; alleen UI-check geeft skulled packetbypass; owner-check zonder usable-check geeft effecten uit beveiligde opslag.

## 9. Cache/client-handoff en bestaande content

Bijlage B is de complete gedecodeerde item/model/note/placeholdermatrix van deze scan; JSON behoudt params/opties en overige velden. **Source-id ≠ local-id.** Onderliggende bestaande items niet opnieuw importeren wegens een andere bron-id. Nieuwere gameval noemt ancient Deadman cape base-id 34607 (placeholder 34608), maar 34607 ontbreekt in cache2686: die nieuwere reward-route kan niet uit deze pin worden geimporteerd. Bestaande MA2-importmapping of een nieuwe geautoriseerde bronpin is daarvoor nodig; zie source-missing-item-candidates.json. Controleer ook sourcevormen zonder '(Deadman)' in de zichtbare naam:33021–33038 voor bowfa/crystal/toxic; skin-statistiek is niet een corrupted-wapennerf.

Aan Claude na akkoord:

1. Maak PLAN per importgroep: acht corrupted weapons met charged/noted vormen; advanced/vengeance/dust/fortuity-active; khopesh; amulet/ruby; starterweapons/Tuna/Chronicle/cape/armour; reward-items met alle skins en POH-objecten; NPCs/thralls; UI/mapicon/AV. Bestaande 23822–23825/23856/23881, Dramen staff 772 en bestaande bowfa/crystal/toxic hergebruiken.
2. `items.yml`:alle equipment stats/eisen/slot/speed/range/examine/ruilbaar/noted/opties correct; bron-examine versus historische cache spelling expliciet. Source33k is niet zomaar een beschikbare667 item-id.
3. Nigel icoon:wiki PNG is Deadman Tutor-icoon; bron-sprites **1524 en1681 zijn kandidaten**. Pixels visueel gecontroleerd:beide rode skullvarianten, maar geen pixel-exacte wiki-match. Config2/group35 tekstscan gaf geen named Deadman-element. Kandidaten77/589 bevatten1524/1681 als spriteverwijzing, hun volledige element-relatie is nog niet semantisch bewezen. **Niet verklaren 'icoon-id klaar'**. Client/minimap/worldmaprouting in eigen cache en een werkelijke icon-render naast Nigel moeten Claude bewijzen. Geen gewone PK-skull of oude gameframe-sprite4815 als vervanging.
4. Reward Store:UI-tekstscan identificeerde **geen unieke Reward Store-root**;230 is safebox,419 is DMM-intro,846 DMM-interface. Live RuneLite naam `BR_REWARD_SHOP=645` is slechts een locator, geen bewezen Nigel-UI in cache2686. Claude moet mapping/scripts lokaliseren of een exact goedgekeurde equivalent bouwen vóór dit deel spelerklaar kan heten.
5. AV:bronseqs/gfx in `source-assets-presence.json` aanwezig en gehasht, maar geluid-ids, trinket-opening/tile-footprint en alle delayed-hit-frames vereisen een echte koppeling uit cache/client-observatie. Alleen aanwezige resource-ids zijn nog geen importplan voor frames/bases/skeletons/sounddeps. POH Wilderness-style vereist room/style/texture/object-dependencycensus; rugobjecten54114–54116 en portal zijn de start, geen bewezen volledige house-import.
6. Claude alleen:beide caches transactioneel aanpassen, mappings terugmelden, client/server tests en visuele controle, daarna passende release. GPT-A doet GEEN cache/client/live-acties.

N457 native Ancient Warriors-corrupt blijft verwijderd; DMM-corrupted moet daar niet op naam bij worden geveegd. N423 burden-trinkets zijn local 24232–24236; dit onderzoek vervangt hun bind/eigendom/BoB/doodstatus niet. N540 winkelbesluit staat LATER; herintroductie geeft geen impliciete prijs of toestemming voor alle Donator/Deadman/Loyaltyshops. Bestaande GE/StoreCatalogue-filters (`DeadmanChestLoot.FIXED_IDS`, RemovedItems, ownership) en DropSources/CollectionLog moeten dezelfde verkrijgbare ids tonen. Collection Log alleen OSRS waar geen eigen besluit geldt; Fire ruby wel toevoegen aan de slayerpagina, geen verzonnen DMM-logpagina. Bestaande eigen chest/breach/burden-logbesluiten behouden.

## 10. Plan in delen — voorstel ter goedkeuring

|Volgorde|GPT-A na akkoord|Claude|Uitgangscriterium|
|---|---|---|---|
|0 Besluiten en ontbrekende bronnen|Verwerk V1–V12 en leg definitief ontwerp vast; geen runtimecode vóór akkoord.|Regie:beantwoord met eigenaar, reserveer gedeelde bestanden voor clues/CoX.|Akkoord in DMM2-dossier met variant/valuta/starter/deathmatrix en bronbewijzen.|
|1 Identiteiten+cachecontract|Maak server id-binding op geleverde mappings, geen placeholders live laten werken.|Alle PLAN/cache/client/assets/items.yml; NPC-plaatsing en UI/componentmapping.|Gevalideerd source→localcontract, geen collisions, complete charge/skin/notes-families.|
|2 Valuta+Nigel basis|Blood-money earn/spend/armourclaim; versioned breachsaldo-migratie, oude earn/spend/UI/commands weg; DP apart.|Breach Trader-spawn/labels vervangen, modellen/icoon controleren.|Migratie tweemaal/restart/vol/overflow; geldige en ongeldige kills; elke oude balanceschrijver behandeld.|
|3 XP-slot|Persistent HP/combatpolicy centraal plus alle bypassroutes; Nigel transcript-takken.|Dialoog/UI en clientcontrole.|XP/stats/orb/events/taken niet veranderd bij blokkade; andere skills wel; relog/restart.|
|4 Safebox|Eigen 10 x NO_STACK-container, beide NPC's dezelfde key; skullgate, transacties, persistence en ownership.|Safebox-UI/import/pakketkoppeling, NPC Bank/Collect/PIN/Deposit-box en screenshots.|10 air runes = vol; 11e geweigerd; volle inventory, noted, untradeable, skull na openen, relog/dood; geen effecten uit safebox.|
|5 Itemgedrag|Trinkets, acht corrupted, khopesh, fire-amulet en loot/death/formules in beperkte subdelen.|Volledige AV/equiplooks en cache/client, Drop Viewer en Log.|Alle vormen/acties/brongetallen; elk wapen echte hit/spec/dood; geen regressie gewone shadow/weapons/BoB.|
|6 Starter|Grant/open/reclaim, migratie van oude eenmalige beloningen, PvM-only startercombat en charges.|Items, gear, Tuna, Chronicle, Entrana, tutor-NPC's en client.|Geen geldfarm of dubbele grants; slotgrenzen, geen PvP; charges/save/reclaim/dood volgens besluit.|
|7 Reward Store HEEL|Alle zeven rewards, permanente unlocks, skinconversies, koop/sellback en POH/teleport-serverlogica.|House-assets, client, Home Teleport-selectie en volledige visuele werking.|Geen dood item in stock; elk product bruikbaar, alle vormen, charges behouden en D1–D20.|
|8 Review+release|Gerichte eigen tests groen, code-review, commit in eigen branch; geen push/merge.|Claude reviewt alles, integreert en doet testclient/breektest/live/lijsten.|Geen AF/PASS vóór visuele keten en A9; afzonderlijke rollbacks voor migratie/cache.|

Gedeelde bestanden zoals `Items.kt`, `items.yml`, `CollectionLog`, `DropSources`, `world_edits` raken GPT-B/Claude. GPT-A wijzigt die alleen volgens een expliciete bestandsverdeling; bij daadwerkelijke overlap stoppen en melden. Deze onderzoeksronde heeft ze alleen gelezen.

## 11. Vragen met advies (nog GEEN besluiten)

* **V1 Bestaande breachsaldo's:** welke omrekening en doelwallet? Advies: koopkracht behouden met één vaste, vooraf berekende conversietabel; eerst nieuwe prijzen en verdiensten kiezen. Bestaande lifetime-earned niet bijtellen. Geen automatische 1:1 tegen één blood money per kill of tegen nieuwe DP-prijzen.
* **V2 Starter:** welke eerdere cash/respawnbesluiten vervallen? Advies: wiki-uitrusting zonder looting bag; D9 aparte 78k-kist blijft; eenmalige oude 1M + 100 tabs alleen behouden als expliciete introkeuze, nooit in een onbeperkt claimbaar pack. Ook migratie van uitstaande oude packs vastleggen.
* **V3 Reward Store + oude voorraad:** OSRS-DP of blood money; waarheen de 24 oude breachartikelen? Advies: Reward Store volgens OSRS met DP, alle zeven prijzen en gelijke terugverkoopwaarde; blood money voor Nigel-armour; oude voorraad en emblembron apart besluiten. Als eigenaar één blood-money-economie bedoelt, is dat ADAPTED en vereist het een eigen verhouding tussen verdiensten en prijzen.
* **V4 Fortuity variant:** permanent 50M of Annihilation 10M? Advies: 10M bij ons bestaande Annihilation-bracketmodel; +1 multiplier en 10M PvP-deposit dan als samenhangend besluit. 'Nieuwste wiki' kiest niet tussen beide modes.
* **V5 XP/taken:** hoe past het slot bij Deadman Tasks en directe XP-beloningen? Advies: de zeven OSRS-skills, Summoning volgens de bestaande skilling-indeling; XP-lock voorkomt skill/leveltaak-events; reeds bereikte progressie blijft. Geen DP-saldo herinterpreteren.
* **V6 OSRS-trinket-dood:** permanente dust-regel of Annihilation/PvM-grafbeleid? Advies: per familie expliciete PvM/PvP/guard/alt/safe-deathmatrix; D14 alleen burden/kits. Fortuity-deposit naar geldige PvP-killer, zonder dubbele uitbetaling bij logout/guard; bedrag volgt V4. Geen destructieve defaults vóór dit besluit.
* **V7 Blood-money-verdiensten:** alleen OSRS 1 per geldige PK, of ook oude breachdamage/emblems? Advies: 1 per `ValidPkKill.VALID`; bestaande antifarmregels behouden; geen blood money uit damage/emblems zonder expliciete nieuwe prijzen en bron. Breach-event, loot en taken blijven bestaan.
* **V8 Onvolledige wapenbronnen:** dark-bow-snelheid, khopesh PvP-hits, geërfde corrupted-specials, charged death en AV. Advies: eerst gerichte OSRS-observatie of aanvullende primaire bron, geen getallen gokken. Dit zijn bronvragen voor Claude, geen balansstemming.
* **V9 Nigel overige opties:** tijdelijke eventteleport/accountconversie en afwijkende informatietekst? Advies: GE + rewards + armour + XP + cosmetics + blueprint-reclaim; geen accountreset; dagteleport alleen indien ook deze tijdelijke DMM-feature gewenst is. Informatietekst over onze eigen regels expliciet goedkeuren.
* **V10 'Alle trinkets':** ook normale vengeance en de beperkte AllStars-versie; hoe undead naast familiars? Advies: normale vengeance erbij vanwege 'alle'; beperkte eventversies alleen op specificatie; thrall apart van pet/BoB met gedeelde damagecredit/targetregels. Geen pet of familiar stilzwijgend verwijderen.
* **V11 Fire amulet verkrijgen:** advies: OSRS Infernal Mage 1/64 + Fire Altar-recept en Log; geen exclusieve Deadman-beloning. Elemental-amuletcombinatie in latere gear-census tenzij expliciet ook in DMM2.
* **V12 Cache/UI-brongaten en lootgewichten:** advies: Claude bewijst Nigel-mapicon, Reward Store-UI, POH-style/portaldependencies, AV en avarice Value-definitie vóór bouw van het betreffende deel; Unknown chestkansen als apart goed te keuren voorstel. Geen onbewezen ids of 'OSRS-kans' invullen.

## 12. Validatie en wat Claude in de testclient nakijkt

Deze ronde: bronhashes, actuele codehashes, decoder-errorlijsten en aanwezigheid van geselecteerde cacheassets gecontroleerd (`validation.json`). Python-onderzoekshulpen geven alleen bewijsbestanden terug; geen Gradle nodig voor alleen onderzoek. **GEEN gametests, testclient of live gedraaid.** De integriteitscheck zegt niets over ingamegedrag.

Na bouwen alleen eigen testklassen met `--priority low`, één build via het bestaande bouwslot. Testontwerp per deel: exacte beloningsverdeling/open-transactie; charge/refund/behoud van totale waarde bij dood; dropboost slechts één keer; XP-toggles/bypasses; SafeBox-packets/relog/skull; valutamigratie tweemaal uitvoeren en overflow; Reward Store koop/sellback/unlock; startergrant-farming. Geen hele mechanics-/combat-suite; Claude plant gedeelde regressies voor release.

Testclient door Claude: drie NPC's zichtbaar met alle opties; één Nigel-icon op minimap én wereldkaart; SafeBox 0/10 → 10/10 en alle weigeringen; HP/combat-XP aan/uit met orb/taken; alle acht wapens voor man/vrouw in 667- en OSRS-gear, iedere style/spec/charged/note-vorm; khopesh beweging/prayers/single/multi/Corp; alle trinkets inclusief 10s/30s cooldown; fortuity teleport/PvP-deposit; avarice volle inventory en bones; Fire Altar en amulet-maxhit; starter 11 slots/charges/reclaim/PvP-death; alle zeven rewards ook POH-rug/style/portal/Home Teleport/skin-revert; dood via PvM/PvP/alt/guard/killer-logout/safe minigame; Drop Viewer/Log/GE/prijzen/examine. Bewijs per K-ITEM/K-NPC/K-WINKEL/K-UI/K-DOOD/K-GELUID/K-FIX; runtime en A9 door Claude.

Volgende stap: Claude/eigenaar beoordelen V1–V12 en het plan, leggen akkoord in het DMM2-dossier vast en leveren de bestandsverdeling en het cachecontract. Tot dan GEEN fase 2.



<!-- DMM2_APPENDICES -->

## Bijlage A — wiki-revisies

# Wiki revision index

Retrieved through public MediaWiki API with rsps-research User-Agent on 2026-11-16.

|Page|Revision|UTC revision date|Saved source|
|---|---|---|---|
|[Amulet of fire](https://oldschool.runescape.wiki/w/Special:PermanentLink/15396852)|15396852|2026-11-08T15:54:52Z|wiki/Amulet_of_fire.wiki.txt|
|[Annihilation blueprints](https://oldschool.runescape.wiki/w/Special:PermanentLink/15288053)|15288053|2026-08-05T17:34:06Z|wiki/Annihilation_blueprints.wiki.txt|
|[Annihilation exit portal blueprints](https://oldschool.runescape.wiki/w/Special:PermanentLink/15286513)|15286513|2026-08-03T17:55:09Z|wiki/Annihilation_exit_portal_blueprints.wiki.txt|
|[Annihilation teleport scroll](https://oldschool.runescape.wiki/w/Special:PermanentLink/15194299)|15194299|2026-04-22T05:02:24Z|wiki/Annihilation_teleport_scroll.wiki.txt|
|[Annihilation weapon scroll](https://oldschool.runescape.wiki/w/Special:PermanentLink/15301001)|15301001|2026-08-14T11:58:15Z|wiki/Annihilation_weapon_scroll.wiki.txt|
|[Armadyl godsword](https://oldschool.runescape.wiki/w/Special:PermanentLink/15329390)|15329390|2026-09-03T11:47:01Z|wiki/Armadyl_godsword.wiki.txt|
|[Armageddon cape fabric](https://oldschool.runescape.wiki/w/Special:PermanentLink/15401338)|15401338|2026-11-12T18:56:24Z|wiki/Armageddon_cape_fabric.wiki.txt|
|[Armageddon rug](https://oldschool.runescape.wiki/w/Special:PermanentLink/15238900)|15238900|2026-06-24T19:50:31Z|wiki/Armageddon_rug.wiki.txt|
|[Armageddon teleport scroll](https://oldschool.runescape.wiki/w/Special:PermanentLink/15238901)|15238901|2026-06-24T19:50:32Z|wiki/Armageddon_teleport_scroll.wiki.txt|
|[Armageddon weapon scroll](https://oldschool.runescape.wiki/w/Special:PermanentLink/15299526)|15299526|2026-08-14T03:24:52Z|wiki/Armageddon_weapon_scroll.wiki.txt|
|[Blood money](https://oldschool.runescape.wiki/w/Special:PermanentLink/15380671)|15380671|2026-10-23T22:06:09Z|wiki/Blood_money.wiki.txt|
|[Bounty Hunter Shop (Deadman Mode)](https://oldschool.runescape.wiki/w/Special:PermanentLink/15345703)|15345703|2026-09-16T14:25:45Z|wiki/Bounty_Hunter_Shop_(Deadman_Mode).wiki.txt|
|[Chronicle](https://oldschool.runescape.wiki/w/Special:PermanentLink/15366319)|15366319|2026-10-05T22:39:10Z|wiki/Chronicle.wiki.txt|
|[Corrupted Scythe of Vitur](https://oldschool.runescape.wiki/w/Special:PermanentLink/15350112)|15350112|2026-09-18T00:40:02Z|wiki/Corrupted_Scythe_of_Vitur.wiki.txt|
|[Corrupted Volatile Nightmare staff](https://oldschool.runescape.wiki/w/Special:PermanentLink/15303344)|15303344|2026-08-16T19:03:05Z|wiki/Corrupted_Volatile_Nightmare_staff.wiki.txt|
|[Corrupted armadyl godsword](https://oldschool.runescape.wiki/w/Special:PermanentLink/15191593)|15191593|2026-04-22T04:45:40Z|wiki/Corrupted_armadyl_godsword.wiki.txt|
|[Corrupted dark bow](https://oldschool.runescape.wiki/w/Special:PermanentLink/15192587)|15192587|2026-04-22T04:51:40Z|wiki/Corrupted_dark_bow.wiki.txt|
|[Corrupted dragon claws](https://oldschool.runescape.wiki/w/Special:PermanentLink/15191592)|15191592|2026-04-22T04:45:39Z|wiki/Corrupted_dragon_claws.wiki.txt|
|[Corrupted tumeken's shadow](https://oldschool.runescape.wiki/w/Special:PermanentLink/15191600)|15191600|2026-04-22T04:45:43Z|wiki/Corrupted_tumeken's_shadow.wiki.txt|
|[Corrupted twisted bow](https://oldschool.runescape.wiki/w/Special:PermanentLink/15191594)|15191594|2026-04-22T04:45:40Z|wiki/Corrupted_twisted_bow.wiki.txt|
|[Corrupted voidwaker](https://oldschool.runescape.wiki/w/Special:PermanentLink/15191591)|15191591|2026-04-22T04:45:39Z|wiki/Corrupted_voidwaker.wiki.txt|
|[Dark bow](https://oldschool.runescape.wiki/w/Special:PermanentLink/15360512)|15360512|2026-10-02T17:47:25Z|wiki/Dark_bow.wiki.txt|
|[Deadman Mode](https://oldschool.runescape.wiki/w/Special:PermanentLink/15375850)|15375850|2026-10-19T09:47:26Z|wiki/Deadman_Mode.wiki.txt|
|[Deadman Reward Store](https://oldschool.runescape.wiki/w/Special:PermanentLink/15394269)|15394269|2026-11-05T00:21:19Z|wiki/Deadman_Reward_Store.wiki.txt|
|[Deadman armour](https://oldschool.runescape.wiki/w/Special:PermanentLink/15226533)|15226533|2026-06-05T21:53:41Z|wiki/Deadman_armour.wiki.txt|
|[Deadman rug](https://oldschool.runescape.wiki/w/Special:PermanentLink/15122909)|15122909|2026-02-07T03:44:39Z|wiki/Deadman_rug.wiki.txt|
|[Deadman starter pack](https://oldschool.runescape.wiki/w/Special:PermanentLink/15188784)|15188784|2026-04-22T04:24:14Z|wiki/Deadman_starter_pack.wiki.txt|
|[Deadman's cape](https://oldschool.runescape.wiki/w/Special:PermanentLink/15239882)|15239882|2026-06-25T22:52:05Z|wiki/Deadman's_cape.wiki.txt|
|[Deadman's chest](https://oldschool.runescape.wiki/w/Special:PermanentLink/15386524)|15386524|2026-10-30T17:19:22Z|wiki/Deadman's_chest.wiki.txt|
|[Deadman's chest (scenery)](https://oldschool.runescape.wiki/w/Special:PermanentLink/15389083)|15389083|2026-11-01T19:58:26Z|wiki/Deadman's_chest_(scenery).wiki.txt|
|[Deadman's legs](https://oldschool.runescape.wiki/w/Special:PermanentLink/15360469)|15360469|2026-10-02T17:29:23Z|wiki/Deadman's_legs.wiki.txt|
|[Deadman's skull](https://oldschool.runescape.wiki/w/Special:PermanentLink/15194310)|15194310|2026-04-22T05:02:29Z|wiki/Deadman's_skull.wiki.txt|
|[Deadman: All Stars Season 3](https://oldschool.runescape.wiki/w/Special:PermanentLink/15351317)|15351317|2026-09-19T18:17:41Z|wiki/Deadman%3A_All_Stars_Season_3.wiki.txt|
|[Deadman: Annihilation](https://oldschool.runescape.wiki/w/Special:PermanentLink/15275551)|15275551|2026-07-26T00:51:06Z|wiki/Deadman%3A_Annihilation.wiki.txt|
|[Dragon claws](https://oldschool.runescape.wiki/w/Special:PermanentLink/15362682)|15362682|2026-10-03T14:53:36Z|wiki/Dragon_claws.wiki.txt|
|[File:Deadman Tutor icon.png](https://oldschool.runescape.wiki/w/Special:PermanentLink/8049367)|8049367|2018-11-26T00:24:19Z|wiki/File%3ADeadman_Tutor_icon.png.wiki.txt|
|[Financial Seer](https://oldschool.runescape.wiki/w/Special:PermanentLink/15390113)|15390113|2026-11-02T19:29:12Z|wiki/Financial_Seer.wiki.txt|
|[Financial Wizard](https://oldschool.runescape.wiki/w/Special:PermanentLink/15364116)|15364116|2026-10-04T10:35:18Z|wiki/Financial_Wizard.wiki.txt|
|[Fire ruby](https://oldschool.runescape.wiki/w/Special:PermanentLink/15396853)|15396853|2026-11-08T15:57:06Z|wiki/Fire_ruby.wiki.txt|
|[Greater ghostly thrall](https://oldschool.runescape.wiki/w/Special:PermanentLink/15350895)|15350895|2026-09-19T12:28:48Z|wiki/Greater_ghostly_thrall.wiki.txt|
|[Greater zombified thrall](https://oldschool.runescape.wiki/w/Special:PermanentLink/15350897)|15350897|2026-09-19T12:29:51Z|wiki/Greater_zombified_thrall.wiki.txt|
|[Infernal Mage](https://oldschool.runescape.wiki/w/Special:PermanentLink/15403365)|15403365|2026-11-15T21:01:22Z|wiki/Infernal_Mage.wiki.txt|
|[Nigel](https://oldschool.runescape.wiki/w/Special:PermanentLink/15401296)|15401296|2026-11-12T18:13:26Z|wiki/Nigel.wiki.txt|
|[Resurrect Greater Skeleton](https://oldschool.runescape.wiki/w/Special:PermanentLink/15327964)|15327964|2026-09-02T01:32:17Z|wiki/Resurrect_Greater_Skeleton.wiki.txt|
|[Scythe of Vitur](https://oldschool.runescape.wiki/w/Special:PermanentLink/15398553)|15398553|2026-11-11T00:39:18Z|wiki/Scythe_of_Vitur.wiki.txt|
|[Starter bow](https://oldschool.runescape.wiki/w/Special:PermanentLink/15188786)|15188786|2026-04-22T04:24:15Z|wiki/Starter_bow.wiki.txt|
|[Starter cape](https://oldschool.runescape.wiki/w/Special:PermanentLink/15194309)|15194309|2026-04-22T05:02:29Z|wiki/Starter_cape.wiki.txt|
|[Starter staff](https://oldschool.runescape.wiki/w/Special:PermanentLink/15188787)|15188787|2026-04-22T04:24:16Z|wiki/Starter_staff.wiki.txt|
|[Starter sword](https://oldschool.runescape.wiki/w/Special:PermanentLink/15260147)|15260147|2026-07-10T19:21:37Z|wiki/Starter_sword.wiki.txt|
|[Template:DeadmanAnnihilationBreachDropTable](https://oldschool.runescape.wiki/w/Special:PermanentLink/15293993)|15293993|2026-08-12T18:23:28Z|wiki/Template%3ADeadmanAnnihilationBreachDropTable.wiki.txt|
|[The dogsword](https://oldschool.runescape.wiki/w/Special:PermanentLink/15237680)|15237680|2026-06-22T22:05:55Z|wiki/The_dogsword.wiki.txt|
|[Thunder khopesh](https://oldschool.runescape.wiki/w/Special:PermanentLink/15237675)|15237675|2026-06-22T22:05:32Z|wiki/Thunder_khopesh.wiki.txt|
|[Thunder khopesh (Deadman Mode)](https://oldschool.runescape.wiki/w/Special:PermanentLink/15262511)|15262511|2026-07-13T15:38:51Z|wiki/Thunder_khopesh_(Deadman_Mode).wiki.txt|
|[Transcript:Financial Seer](https://oldschool.runescape.wiki/w/Special:PermanentLink/14996472)|14996472|2025-09-29T15:21:08Z|wiki/Transcript%3AFinancial_Seer.wiki.txt|
|[Transcript:Financial Wizard](https://oldschool.runescape.wiki/w/Special:PermanentLink/14868949)|14868949|2025-03-23T15:53:01Z|wiki/Transcript%3AFinancial_Wizard.wiki.txt|
|[Transcript:Nigel](https://oldschool.runescape.wiki/w/Special:PermanentLink/15394041)|15394041|2026-11-04T18:44:19Z|wiki/Transcript%3ANigel.wiki.txt|
|[Transcript:Trinket of advanced weaponry](https://oldschool.runescape.wiki/w/Special:PermanentLink/15355107)|15355107|2026-09-24T05:35:30Z|wiki/Transcript%3ATrinket_of_advanced_weaponry.wiki.txt|
|[Transcript:Trinket of fortuity](https://oldschool.runescape.wiki/w/Special:PermanentLink/15120414)|15120414|2026-02-04T06:50:15Z|wiki/Transcript%3ATrinket_of_fortuity.wiki.txt|
|[Trinket (disambiguation)](https://oldschool.runescape.wiki/w/Special:PermanentLink/15343713)|15343713|2026-09-14T20:54:50Z|wiki/Trinket_(disambiguation).wiki.txt|
|[Trinket dust](https://oldschool.runescape.wiki/w/Special:PermanentLink/15193183)|15193183|2026-04-22T04:55:39Z|wiki/Trinket_dust.wiki.txt|
|[Trinket of advanced weaponry](https://oldschool.runescape.wiki/w/Special:PermanentLink/15355050)|15355050|2026-09-24T03:52:56Z|wiki/Trinket_of_advanced_weaponry.wiki.txt|
|[Trinket of avarice](https://oldschool.runescape.wiki/w/Special:PermanentLink/15286577)|15286577|2026-08-03T18:19:52Z|wiki/Trinket_of_avarice.wiki.txt|
|[Trinket of fairies](https://oldschool.runescape.wiki/w/Special:PermanentLink/15191597)|15191597|2026-04-22T04:45:42Z|wiki/Trinket_of_fairies.wiki.txt|
|[Trinket of fortuity](https://oldschool.runescape.wiki/w/Special:PermanentLink/15286578)|15286578|2026-08-03T18:20:04Z|wiki/Trinket_of_fortuity.wiki.txt|
|[Trinket of undead](https://oldschool.runescape.wiki/w/Special:PermanentLink/15298165)|15298165|2026-08-13T23:52:21Z|wiki/Trinket_of_undead.wiki.txt|
|[Trinket of vengeance](https://oldschool.runescape.wiki/w/Special:PermanentLink/15351326)|15351326|2026-09-19T18:21:31Z|wiki/Trinket_of_vengeance.wiki.txt|
|[Tumeken's shadow](https://oldschool.runescape.wiki/w/Special:PermanentLink/15373159)|15373159|2026-10-15T17:28:46Z|wiki/Tumeken's_shadow.wiki.txt|
|[Tuna (Deadman starter pack)](https://oldschool.runescape.wiki/w/Special:PermanentLink/15233336)|15233336|2026-06-14T01:55:53Z|wiki/Tuna_(Deadman_starter_pack).wiki.txt|
|[Twisted bow](https://oldschool.runescape.wiki/w/Special:PermanentLink/15385385)|15385385|2026-10-29T11:14:54Z|wiki/Twisted_bow.wiki.txt|
|[Vengeance](https://oldschool.runescape.wiki/w/Special:PermanentLink/15359389)|15359389|2026-10-01T09:01:13Z|wiki/Vengeance.wiki.txt|
|[Voidwaker](https://oldschool.runescape.wiki/w/Special:PermanentLink/15327980)|15327980|2026-09-02T01:59:59Z|wiki/Voidwaker.wiki.txt|
|[Volatile Nightmare staff](https://oldschool.runescape.wiki/w/Special:PermanentLink/15384147)|15384147|2026-10-27T23:46:51Z|wiki/Volatile_Nightmare_staff.wiki.txt|
|[Wilderness (theme)](https://oldschool.runescape.wiki/w/Special:PermanentLink/15287903)|15287903|2026-08-05T16:31:27Z|wiki/Wilderness_(theme).wiki.txt|

Aliases: Corrupted weapons -> Trinket of advanced weaponry; Deadman Points -> Deadman Reward Store; Safe Deposit Box -> Deadman Mode#Bank raiding and insurance. API redirects saved in wiki/supplement.json.
Missing transcript pages for undead, fairies and avarice are evidence gaps, not successful fetches. Case-sensitive scythe and shadow aliases resolved to canonical pages listed above.


## Bijlage B — cache-items, modellen en vormen

# Source item definitions and existing local items

Source IDs are OSRS IDs, never assigned local import IDs. Model opcodes: 44 inventory, 45 male wear, 46/47 extra male, 48 female wear, 49/50 extra female; 90-93 heads. Notes/placeholders retain their source links.

|Source ID|Name|Models (opcode:id)|Note link|Placeholder|Local name match|
|---|---|---|---|---|---|
|772|Dramen staff|44:35763, 45:30556, 48:31195|-|17325|772|
|13307|Blood money|44:2484|-|18218|absent in local name scan|
|13317|Deadman's chest|44:29749, 45:29637, 46:29636, 48:29745, 49:29744|-|18219|absent in local name scan|
|13318|Deadman's legs|44:29752, 45:29020, 48:29743|-|18220|absent in local name scan|
|13319|Deadman's cape|44:29748, 45:29633, 48:29725|-|18221|absent in local name scan|
|13658|Teleport card|44:31242|13659|14402|absent in local name scan|
|13660|Chronicle|44:31245, 45:31193, 48:31194|-|14403|absent in local name scan|
|22330|Deadman starter pack|44:7685|-|-|23881|
|22331|Starter sword|44:49503, 45:49484, 48:49483|-|22332|absent in local name scan|
|22333|Starter bow|44:49505, 45:49486, 48:49482|-|22334|absent in local name scan|
|22335|Starter staff|44:49504, 45:49481, 48:49485|-|22336|absent in local name scan|
|24189|Deadman's chest|44:29749, 45:29637, 46:29636, 48:29745, 49:29744|-|-|absent in local name scan|
|24190|Deadman's legs|44:29752, 45:29020, 48:29743|-|-|absent in local name scan|
|24191|Deadman's cape|44:29748, 45:29633, 48:29725|-|-|absent in local name scan|
|26149|Tuna|44:2355|-|-|361|
|28531|Corrupted voidwaker|44:49500, 45:49472, 48:49471|28532|28533|absent in local name scan|
|28534|Corrupted dragon claws|44:49496, 45:49468, 48:49467|28535|28536|absent in local name scan|
|28537|Corrupted armadyl godsword|44:49502, 45:49469, 48:49470|28538|28539|absent in local name scan|
|28540|Corrupted twisted bow|44:49499, 45:49473, 48:49474|28541|28542|absent in local name scan|
|28543|Corrupted scythe of vitur|44:49497, 45:49478, 48:49476|-|28544|absent in local name scan|
|28545|Corrupted scythe of vitur (uncharged)|44:49501, 45:49475, 48:49477|28546|-|absent in local name scan|
|28547|Corrupted tumeken's shadow|44:49498, 45:49479, 48:49480|-|28548|absent in local name scan|
|28549|Corrupted tumeken's shadow (uncharged)|44:49498, 45:49479, 48:49480|28550|-|absent in local name scan|
|28555|Starter bow|44:49505, 45:49486, 48:49482|-|28556|absent in local name scan|
|28557|Starter staff|44:49504, 45:49481, 48:49485|-|28558|absent in local name scan|
|28559|Starter sword|44:49503, 45:49484, 48:49483|-|28560|absent in local name scan|
|28561|Trinket of vengeance|44:49495|28562|28563|absent in local name scan|
|28564|Trinket of fairies|44:49494|28565|28566|23822|
|28567|Trinket of advanced weaponry|44:49493|28568|28569|absent in local name scan|
|28570|Trinket of undead|44:49495|28571|28572|23824|
|29598|Deadman rug|44:54198|-|-|absent in local name scan|
|29599|Corrupted dark bow|44:54205, 45:54213, 48:54212|29600|29601|absent in local name scan|
|29602|Corrupted Volatile Nightmare staff|44:54204, 45:54210, 48:54214|29603|29604|absent in local name scan|
|29605|Armadyl godsword (Deadman)|44:49502, 45:49469, 48:49470|-|29606|absent in local name scan|
|29607|Voidwaker (Deadman)|44:49500, 45:49472, 48:49471|-|29608|absent in local name scan|
|29609|Volatile Nightmare staff (Deadman)|44:54204, 45:54210, 48:54214|-|29610|absent in local name scan|
|29611|Dark bow (Deadman)|44:54205, 45:54213, 48:54212|-|29612|absent in local name scan|
|29613|Imbued Zamorak cape (Deadman)|44:54203, 45:54216, 48:54215|-|29614|absent in local name scan|
|29615|Imbued Guthix cape (Deadman)|44:54203, 45:54216, 48:54215|-|29616|absent in local name scan|
|29617|Imbued Saradomin cape (Deadman)|44:54203, 45:54216, 48:54215|-|29618|absent in local name scan|
|29619|Armageddon rug|44:54208|29620|29621|absent in local name scan|
|29622|Armageddon teleport scroll|44:54206, 45:54211, 48:54211|29623|29624|absent in local name scan|
|29625|Armageddon weapon scroll|44:54207|29626|29627|absent in local name scan|
|29628|Armageddon cape fabric|44:54201|29629|29630|absent in local name scan|
|30388|Thunder khopesh|44:55637, 45:55757, 48:55756|-|30389|absent in local name scan|
|30689|Trinket dust|44:2742|-|-|absent in local name scan|
|33012|Annihilation weapon scroll|44:54207|33013|33014|absent in local name scan|
|33015|Annihilation blueprints|44:28470|33016|33017|absent in local name scan|
|33018|Annihilation teleport scroll|44:54206, 45:54211, 48:54211|33019|33020|absent in local name scan|
|33021|Bow of Faerdhinen (c)|44:28476, 45:59942, 48:59943|-|33022|22736|
|33023|Crystal body|44:59961, 45:59940, 48:59954|-|33024|22739|
|33025|Crystal body (inactive)|44:59962, 45:59941, 48:59955|-|33026|22740|
|33027|Crystal legs|44:59959, 45:59938, 48:59952|-|33028|22741|
|33029|Crystal legs (inactive)|44:59960, 45:59939, 48:59953|-|33030|22742|
|33031|Crystal helm|44:59957, 45:59935, 48:59949, 51:59930, 53:59932|-|33032|22737|
|33033|Crystal helm (inactive)|44:59958, 45:59936, 48:59950, 51:59931, 53:59933|-|33034|22738|
|33035|Toxic staff (uncharged)|44:28897, 45:59946, 48:59947|-|-|22761|
|33036|Toxic staff (Deadman)|44:41576, 45:59944, 48:59945|-|33037|absent in local name scan|
|33038|The dogsword|44:55628, 45:55751, 48:55764|33039|33040|absent in local name scan|
|33041|Thunder khopesh|44:55637, 45:55757, 48:55756|33042|33043|absent in local name scan|
|33044|Trinket of avarice|44:28471|33045|33046|23823|
|33047|Trinket of fortuity (inactive)|44:28473|33048|33049|23825|
|33050|Trinket of fortuity (active)|44:28474|33051|33052|absent in local name scan|
|33063|Starter cape|44:37197, 45:37019, 48:37051|-|33064|absent in local name scan|
|33065|Deadman's skull|44:28459|-|-|23856|
|33122|Annihilation exit portal blueprints|44:28470|-|33123|absent in local name scan|
|33428|Trinket of vengeance (2)|44:49495|33429|33430|absent in local name scan|
|33431|Trinket of vengeance (1)|44:49495|33432|33433|absent in local name scan|
|34422|Fire ruby|44:61875|34423|34424|absent in local name scan|
|34425|Amulet of fire|44:61877, 45:61881, 48:61884|34426|34427|absent in local name scan|


## Bijlage C — letterlijke besluiten, bronregels en ouderdom

Dit zijn broncitaten, geen nieuwe goedkeuring. Nieuwere D1-D20 gaan voor oudere doodregels; DMM2-opdracht gaat voor de oude trinket-exclusie. Historische statuslabels zijn niet opnieuw getest.


### C:\RSPS\HOOFDLIJST.txt L297-L302
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-15 14:1x (eigenaar, chat): CHAMBERS OF XERIC fase 1 = ALLES ADVIES A (1a-10a, dossier COX.txt): (1) alleen ingangsgebied
  Mount Quidamortem (4919) importeren + bereikbaar via de minigame/grouping-teleport | (2) eerst alleen Normaal, Challenge Mode later |
  (3) max 35 min zoals ToA (kamers korter, bazen iets minder hp; volgorde + loot OSRS) | (4) voorbereiding (skilling/potions/opslag) zoals
  OSRS binnen de duurgrens | (5) groep 1-8 | (6) dood zoals OSRS (veilig, punten kwijt) | (7) beloningen precies OSRS incl. eigen CoX-kits
  (zoals ToA E4) + Drop Viewer + Collection Log | (8) CoX-items uit de winkels zodra de drop live is, wie kocht houdt ze (zoals ToA E1) |
  (9) willekeurige layouts uit de OSRS-layouts | (10) zoals ToA: geen CA's/PB's, scorebord = server-records, familiars mogen.
```

### C:\RSPS\HOOFDLIJST.txt L328-L330
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-14 22:1x (eigenaar, clue-vragen 1-6): (1) ORNAMENT KITS blijven ALLEEN in de winkels - niet uit clue-kisten (kit-uitkomst = gewone
  worp op de rest) | (2) 3rd Age + gilded OOK uit clue-kisten zoals OSRS (de Donator store-aanbieding blijft) | (3) scroll boxes/cases MEENEMEN |
  (4) OSRS-dropkansen voor clues | (5) doodgaan met clue/kist zoals OSRS | (6) Mimic zoals OSRS (opt-in).
```

### C:\RSPS\HOOFDLIJST.txt L334-L336
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-14 21:3x (eigenaar chat): Mage Arena 2 in de COLLECTION LOG alleen als OSRS dat heeft (OSRS heeft geen MA2-pagina -> niets), WEL in de
  DROP VIEWER (de 3 bewakers met hun overblijfsel, onder Bosses).
  + 21:3x: alle MA2-capes terugkopen bij Perdu, OOK de nieuwe Imbued ancient cape (zelfde voorwaarden als de god capes: 250k, na MA2).
```

### C:\RSPS\HOOFDLIJST.txt L337-L341
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-14 20:0x (vragen in de chat): MA2 oude 'voltooid'-vinkjes = B ZO LATEN (wie op voltooid staat houdt dat) | imbued max
  capes = A UIT de Donator store zodra MA2 live is, wie kocht houdt hem | 'Fired up'/shortcut-icoon west van de GE = B WEG (N542) |
  bankgeluid bij storten = A zo laten (N575 dicht) | loot keys bij monster-dood weg = A OSRS laten (N512 dicht) | prayer-tab-overlap
  (N585) = eigenaar maakt later een melding | helderheid = ALLEEN ToA iets donkerder (N615, geen globale knop) | vastzittende npc
  in een smalle gang (N417) = A zo laten (OSRS).
```

### C:\RSPS\HOOFDLIJST.txt L342-L351
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-14 16:0x-16:1x (GPT naast ToA, eigenaar): CLUE SCROLLS (GPT-B): '2 alleen die we niet hebben snap je wel mimic watson als t goed
  hebben we al veel' + 16:1x 'de agent moet kijken wat we hebben en de rest van osrs aanvullen inclusief watson house mimic' | stappen in
  gebieden die we niet hebben weglaten + lijst (3A) | kistbeloningen precies OSRS (4A). MAGE ARENA 2 (GPT-A): miniquest VOLLEDIG zoals OSRS
  + de bosses (5B; 'als t goed is hebben we de monsters al in onze breaches') | de paarse Zaros-cape ('osrs heeft em is nieuwe update je
  moet peekayer verslaan wij doen alleen de cape maar ook bij mage arena 2'): alleen de cape, ook via MA2 te verdienen, de nieuwe baas niet.
  GEAR (GPT-A, na MA2): imbued/saturated heart, amulet of fire 'en veel meer die we niet hebben' = een gear-census van alle OSRS PvP/PvM-gear
  die we missen (8A), daarna kiest de eigenaar. ROLLEN (16:1x, VERVANGT '1B Claude keurt alles' + 'GPT-A alleen onderzoek'): 'beide gpt
  accounts mogen allebei onderzoeken en bouwen en keuren. jij gaat gewoon verder met toa en monitort ze allebei'; 'gpt b mag helemaal op
  verbruikt worden en gpt a mag tot 70% van de weekly usage'. VOOR ALLES: 'volledig integreren in onze rsps dropviewer collectionlogg en
  meer waar nodig'; 'kwaliteitswerk geen dingen vezinnen volledig osrs en veel is al aanwezig in onze server'.
```

### C:\RSPS\HOOFDLIJST.txt L356-L359
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-13 23:5x (eigenaar 'ja mag in toa', tegen Claude's advies A in): FAMILIARS MOGEN IN ToA (Summoning, bv. steel titan) - geen
  beperking bij de ingang. Testbot mag een steel titan gebruiken voor grafische/route-tests (sneller), NIET bij het meten van de raidduur.
  Testbot speelt verder als een echte speler: echte potions (::drink), Ruinous-gebeden (::pray: melee Decimate / ranged Annihilate +
  Dampen + Berserker; Intensify niet), geladen quiver, steel titan + scrolls + ::spec continu, kruisboog Rapid, rennen aan; 14-11 00:4x: GEEN ring of suffering (Ultor/Venator = meer dps) -> overhead Vindication, ::restore laat boosts staan (eigenaar 13-11 23:4x-23:5x).
```

### C:\RSPS\HOOFDLIJST.txt L401-L405
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-06 19:1x (N462 ToA-vragen E1-E4, GPT-A 80-vragen.txt): E1 Osmumten's fang, Masori en Thread of Elidinis UIT de 78 Store (eigenaar koos
  tegen het advies in) - pas weghalen op het moment dat de ToA-drop live is | E2 wie Beneath Cursed Sands (FoundationQuests) al af heeft HOUDT dat
  en mag ToA in; alleen nieuwe/niet-voltooide accounts doen de korte versie | E3 Protect Item blijft UIT, ook bij ToA-retrieval (D1 ongewijzigd),
  rest van de ToA-dood zoals OSRS | E4 ToA geeft zijn eigen OSRS-kits (Cursed phalanx, Masori crafting kit, Menaphite ornament kit) als beloning
  zoals OSRS; alle andere kits blijven alleen uit winkels.
```

### C:\RSPS\HOOFDLIJST.txt L406-L410
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-06 16:4x-16:5x: volgende grote klus = ToA (Tombs of Amascut) ZOALS OSRS: route A echte skeletal-client (geen bake), Necropolis
  importeren als ingang, dood in de raid veilig (OSRS), exacte OSRS-beloningen/kansen, groep 1-8 (invocations VERVANGEN door de 3 modes,
  besluit 21:4x); pet-audit (N407) niet meer als grote klus voorstellen. 16:5x: toegang = quest Beneath Cursed Sands
  (OSRS Wiki ToA: 'players will have to complete the Beneath Cursed Sands quest'; raid in de Jaltevas Pyramid, Necropolis) - eigenaar:
  'die moet ook werken maar niet volledig net als de andere questen die we hebben' = korte versie zoals onze FoundationQuests (SHORT).
```

### C:\RSPS\HOOFDLIJST.txt L419-L421
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-05 18:0x: oude Callisto/Vet'ion (+hounds, Reborn) krijgen per aanval/dood/spawn de geluiden van de HUIDIGE OSRS Callisto/Vet'ion
  (Jagex-namen wbr_callisto_*/wbr_vetion_*/wbr_vention_hellhound_*, al in onze cache via Artio/Calvar'ion) + bron-bewezen shockwave 586
  (KBD-ijsadem) en skeletal_hellhound 1185-1189. Bronnen: team\dossiers\N522-GELUIDSBRONNEN.txt. Maledictus: bron nog zoeken.
```

### C:\RSPS\HOOFDLIJST.txt L422-L426
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-05 17:4x (N522-FIX-00 V1-V6, eigenaar 'alles wat gpt zegt doe je oke'): V1 geluid stille bosses alleen met bewezen OSRS-cues,
  niets gokken | V2 vier Escape-schachten, geen vijfde; logout in een GROTE lair -> bovengronds buiten de Escape Caves (Jagex-hotfix
  25-03-2026, nieuwsbericht nagekeken), gewone uitgangen blijven ondergronds | V3 PvP-killer krijgt de werkelijk betaalde fee (escrow), geen
  geld erbij | V4 Escape-drain run=prayer blijft | V5 alleen bestaande wereldmeldingen/Deadman-taken, niets nieuws | V6a vroeg ophalen bij
  Death = 15% op de bestaande office-waardering, verlopen = 5% | V6b wildy-dood: eten/potions/Phoenix necklace verdwijnen zoals OSRS (niet naar graf).
```

### C:\RSPS\HOOFDLIJST.txt L427-L430
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-05 15:5x: ('STOP nieuwe OSRS-imports' VERVANGEN door SCOPE 11-06 'eerst fixen, importeren waar nodig') | dood: lockbare items (Infernal cape e.d.) bij een
  monster-dood ZOALS OSRS - alleen GELOCKT (l) blijft extra, ongelockt = graf of telt mee in de 3 duurste (VERVANGT besluit 10-04 'lockable gelockt
  of niet altijd houden') | ophalen bij Death terwijl het graf nog staat = 15% (N591; graf zelf blijft gratis) | graf-icoon/pijl = LATER |
  Peek bij de lairs: eerst uitgebreid onderzoek, GEEN volledige OSRS-diaries.
```

### C:\RSPS\HOOFDLIJST.txt L431-L435
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-05 12:5x (eigenaar via telefoon, alle vragen in de chat): N547 Intensify 40% HOUDEN (balans N496) | N546 boekwissel = Trailblazer unlock-
  geluid + rode regel | N540 trinkets-tab + Fairy trinket/alle trinkets in de winkel: LATER beslissen | N543 icoon: later (screenshot) -> 14:4x screenshot gegeven:
  rode ronde verbodsring over het Protect Item-gebed in alle prayerbooks (= OSRS-sprite 1193) |
  N501 DT2-winkel = optie A (alle DT2-beloningen, maakkosten onderdelen, quartz zonder GE-prijs 75k, Ancient sceptre OOK bij Breach Trader) |
  N527 Wildywyrm = de 12 echte Darkan-plekken (shaunySpawns), ook al liggen ze in 1 gebied.
```

### C:\RSPS\HOOFDLIJST.txt L442-L444
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-04 15:1x: oude 667 Forinthry Dungeon (oude revcave) WEG zoals OSRS | Wildywyrm naar de wilderness-BOVENGROND (RS 2011) |
  Forinthry bracelet UIT DE GAME (geen drop/winkel, bestaande weg bij inloggen) | breach-winkel houdt zijn sceptres + Archaeologist krijgt alle
  DT2-beloningen | looting bag compleet uit de game (N561) | kleine dingen geen tests, grote wel; testclient altijd max gear.
```

### C:\RSPS\HOOFDLIJST.txt L456-L458
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-01 14:1x: N517 Deadman chest 1 per uur + naam 'Deadman chest' = goed | N72 Minigame Teleport (alle 3 boeken) GRATIS met eigen
  20 min wachttijd zoals OSRS (samen met de Task List-minigame-teleports N409) | N72 Teleport to Target WEGLATEN (geen Bounty Hunter) |
  N410 de ECHTE onhaalbare max capes (infernal, imbued god, masori assembler + ontbrekende) in de Donator store, niet alleen het uiterlijk.
```

### C:\RSPS\HOOFDLIJST.txt L465-L465
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-01 02:5x: D9 starter pack na een PvP-dood = Deadman chest met 78.000 coins + Deadman's skull (N517; niet het nieuwe-speler-pack met 1M).
```

### C:\RSPS\HOOFDLIJST.txt L468-L469
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-01 02:1x: N409 Task List-tab = minigame-teleports | N259 Help-knop = Help-menu (Deadman-uitleg, Bug melden, Discord, Regels) |
  N153 nieuwe RSA-sleutel vlak voor de lancering met de lanceringsclient | N235 AGS-special alleen het OSRS-geluid.
```

### C:\RSPS\HOOFDLIJST.txt L470-L478
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-11-01 01:5x DOOD + KILL (N502, 20 besluiten D1-D20, detail onderaan dossier N502; vervangt eerdere doodregels waar ze botsen):
  D1 Protect Item UIT in alle 3 boeken, wel zichtbaar met het OSRS High Risk-icoon | D2 monster-dood: 3 duurste houden zonder skull, 0 met skull |
  D3 monster-dood ruilbare items -> graf (zoals nu) | D4 speler-kill andere untradeables: breken + Perdu (zoals nu) | D5 PvP-loot 1 min killer,
  dan Wilderness +4 / buiten +60 min | D6 kill grace na ELKE single-combat kill (stopt bij aanval/dood/logout/teleport), punten+emblem alleen
  geldige kill | D7 guard+skull: loot meteen op de grond, 60 min | D8 killer uitgelogd: grond, PvP-tijden | D9 starter pack na PvP-dood |
  D10 alt-kill (zelfde IP): spullen naar graf slachtoffer | D11 Practice PvP veilig | D12 loot keys PvM-dood weg (OSRS) | D13 pet bij PvP -> graf |
  D14 burden trinkets + kits -> graf bij elke dood | D15 skull-kleur bij respawn herberekenen | D16 familiar sterft -> zijn items op de grond |
  D17 upgraded pet oppakken: items blijven in de pet | D18 Items Kept on Death standaard 'Killed by a player' + vinkje 'PvM death' |
  D19 melding bij inloggen als pets nog items dragen | D20 guard+skull/killer weg: untradeables breken, het killer-geld komt OP DE GROND (eigenaar 02:0x). ALLES AKKOORD (D1-D20).
```

### C:\RSPS\HOOFDLIJST.txt L479-L481
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-10-31 23:4x (N502, VERVANGT 10-24 'pet altijd behouden bij dood'): bij doodgaan gaan PETS en andere untradeables (ornament kits enz.)
  standaard naar het GRAF (na de timer naar Death's Office); de inhoud van een burden/BoB-pet gaat VERLOREN bij een PvP-kill.
  Items Kept on Death opent standaard op 'Killed by a player'; aanvinkoptie wordt 'PvM death / killed by an NPC' zoals OSRS.
```

### C:\RSPS\HOOFDLIJST.txt L483-L484
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-10-31 20:0x (vervangt 19:5x): Home Teleport (alle 3 boeken) is GRATIS, gaat naar de GRAND EXCHANGE, niet instant: lange cast met
  animatie zoals OSRS Lumbridge Home Teleport + wachttijd (OSRS 30 min). Alle ANDERE teleports kosten weer runen.
```

### C:\RSPS\HOOFDLIJST.txt L488-L490
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-10-31 13:4x: N463 Ruinous Powers = OSRS Deadman 2026 (24 gebeden), derde boek ('alleen Prayer-level' VERVANGEN 17:3x: DT2 + boek), wisselen bij de altaren
  en niet in gevecht, Protect Item (Ruinous) telt niet bij een PvP-dood | N11 alleen Dwarf multicannon | B: bosses, Kree'arra eerst |
  N458 Esc zo laten | wensen N481 (Summoning+Prayer trager) en N482 (prayer filtering, 3 boeken).
```

### C:\RSPS\HOOFDLIJST.txt L491-L492
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-10-31: pets (N407): Maggot marquess Emote = schreeuw seq 13922 | Clockwork cat Shoo = zoals RS (Perdu geeft terug) |
  paarse wereldmelding bij een pet-drop.
```

### C:\RSPS\HOOFDLIJST.txt L493-L493
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-10-31 10:5x: Void Knight Deflector = net als de rest van Void (onruilbaar + zelfde doodregel) - N472.
```

### C:\RSPS\HOOFDLIJST.txt L500-L501
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-10-30: Drop Viewer VOLLEDIG GOEDGEKEURD (perfect) - zelf niet meer wijzigen zonder vraag; nieuwe drops (trinkets e.d.)
  wel erin in dezelfde stijl.
```

### C:\RSPS\HOOFDLIJST.txt L502-L506
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-10-25 (lock-regel bij monster-dood: zie 11-05 15:5x): defenders lockable vanaf mithril | alle ornament kits onruilbaar, ook item met kit, alleen uit winkels | metamorphosis
  alle vormen open (zelfde pet) | loopgeluid zo | pets zonder boss in kist (lage kans), exclusieve pets alleen Donator (voorstel eerst)
  | verloren pet gratis bij Perdu | onhaalbare max capes WEL in de Donator store (eigenaar 10-25 correctie: "ik zei toch onhaalbare max capes wel naar de donator store"); later Mage Arena 1+2 (N415) | max cape-opties zoals OSRS |
  scaling ook in fixed | Rigour/Augury cache-fix mag | Hill giants altijd agressief | Snowman top hat weg | Task List-tab =
  minigame-teleports (ontwerp eerst) | Drop Viewer: links alleen namen, alles perfect/high quality.
```

### C:\RSPS\HOOFDLIJST.txt L507-L508
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- 2026-10-24: alle 1078 overige untradeables terugkopen bij Perdu na PvP-dood (B) | emblems: PvP-kill -> killer, anders weg |
  vaste XP-beloningen exact (geen bracket) | pet altijd behouden bij dood (VERVANGEN 10-31 23:4x: pets naar het graf).
```

### C:\RSPS\HOOFDLIJST.txt L509-L515
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- Doodregels per itemgroep: team\dossiers\DOOD-OVERZICHT-PER-GROEP-2026-10-24.txt. Goedgekeurde ontwerpen:
  team\dossiers\GOEDGEKEURDE-ONTWERPEN.txt (exact dat bouwen). Oudere besluiten (12-22 okt): team\dossiers\BESLUITEN-OKTOBER-2026.txt
  (samengevat; het archief VRAGEN.txt is op 14-11 gewist). Nieuwere besluiten hierboven gaan altijd voor.

=====================================================================================================================
8. OMGEVING EN COMMANDO'S
=====================================================================================================================
```

### C:\RSPS\HOOFDLIJST.txt L522-L523
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- Echte doodtest: $env:RSPS_COMMAND_PORT='50217'; tools\conformance\Send-GameCommand.ps1 conform deadman untradeables
  (rapport bugs\conformance\deadman-<tijd>\report.txt). Per item: gradlew :game:plugins:test --tests '*UntradeableDeathReportTests*'.
```

### C:\RSPS\HOOFDLIJST.txt L524-L524
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- Spawn-commando's (TEST-COMMANDS is van 10-24: looting bag + Forinthry zijn sindsdien UIT de game): team\dossiers\TEST-COMMANDS-2026-10-24.txt, team\dossiers\PETS-SPAWN-COMMANDS.txt (::pets 1-14).
```

### C:\RSPS\HOOFDLIJST.txt L595-L804
SHA256 605e81274bfbcfc01fad4303fc7aa2f5d8eb7f976cc4936c085865572c037325
```text
- Naslag (alleen als je klus erom vraagt): BEHEER.txt (VPS, website, backups), ROOT_CAUSES.md (eerdere oorzaken), TRACES.md,
  skills in .claude\skills (rsps-server, rsps-client, rsps-cache-import, rsps-economy, rsps-ops, rsps-anticheat).

=====================================================================================================================
9. KETENS - AFVINKLIJSTEN PER SOORT WERK (was KETENS.txt; sinds 11-06 hier, eigenaar: 3 documenten)
=====================================================================================================================
KETENS - AFVINKLIJSTEN PER SOORT WERK (78legacy)  -  eigenaar 2026-11-02: 'de hele keten'
=====================================================================================================================
SCOPE: zie sectie 2 (eerst fixen, importeren waar nodig).
WAAROM: de eigenaar meldt een bug na ~10 seconden testen; elke melding is een VOORBEELD. Een wens of bug ('fix X') = X werkt PRECIES
zoals OSRS, met alles eromheen. Agents leverden te vaak half werk (1 op 6 bugs heropend: Collection Log zonder alle items, kaarticonen
die alleen in de testclient werkten, Stretched alleen het symptoom). Deze lijst maakt 'de hele keten' concreet en controleerbaar.

HOE GEBRUIKEN (VERPLICHT bij ELKE bug en wens, Claude en Codex/ChatGPT):
1. Kies de keten(s) die passen (meestal meer dan een: een OSRS-boss = BOSS + ITEM voor elke drop + GEBIED + GELUID ...; bestaande
   content nalopen/fixen = altijd ook K-FIX).
   Zet ze in de BUGLIJST-regel: 'KETEN: ITEM+DOOD' en kopieer de punten naar het dossier onder het kopje KETEN-BEWIJS.
2. Werk ELK punt af voor ALLE varianten (alle items/npc's/plekken van dezelfde soort, niet alleen het gemelde voorbeeld).
3. Per punt een regel: OK + bewijs (screenshot screens\<nr>\..., logregel, testnaam, wiki-zin) | NVT + waarom | FOUT -> eerst fixen.
   Een punt zonder bewijs telt als NIET gedaan. Niets verzinnen: bron = OSRS Wiki / OSRS-cache (donors Darkan, Void, Novite, Kronos,
   Zaryx alleen als hulp voor code); geen bron = vraag aan de eigenaar (noteren, advies kiezen), nooit zelf invullen.
4. Pas als ALLE punten OK/NVT zijn EN de algemene punten A1-A10 gedaan zijn: status AF (regel naar archief, dossier wissen) + verslag met spawn-commando's.

---------------------------------------------------------------------------------------------------------------------
A. ALGEMEEN - hoort bij ELKE keten
---------------------------------------------------------------------------------------------------------------------
A1  OSRS-REFERENTIE: het OSRS-gedrag opgeschreven met bron (wiki-zin, cache-waarde, donorbestand:regel) VOOR je bouwt.
A2  HELE KLASSE: alle varianten gezocht (codegraph/grep/cache: (i), (or), (l), broken, noted, kleuren, andere ids, andere vormen) en
    allemaal meegenomen; lijst van ids in het dossier.
A3  ALLE ROUTES: elke manier om er te komen of het te gebruiken (klik, rechtsklik, item-op-item, item-op-object, teleport, commando,
    dood, uitloggen/inloggen, ander account).
A4  GERELATEERDE SYSTEMEN: alle gebruikers/afhankelijken gezocht (codegraph) en nagelopen: Drop Viewer, Collection Log, winkels, GE,
    Perdu/dood, Deadman-taken, kaart/minimap, geluid, examines, tests. Owner-goedgekeurde dingen ernaast niet stuk.
A5  TESTS: testklassen van gewijzigde bestanden groen; voor live een keer de geraakte packages.
A6  TESTCLIENT als de EIGENAAR: zijn situatie (gear/positie uit de bugmelding), OpenGL/zijn venstermodus waar het om beeld gaat,
    LOW alleen als beeld er niet toe doet; screenshots in screens\<nr>\.
A7  NAAST OSRS: het resultaat vergeleken met OSRS (wiki-plaatje/video/RuneLite-referentie): zelfde tekst, zelfde getallen, zelfde gedrag.
A8  BREEKTEST: de BREEK-regel uit het dossier + zelf: 5x herhalen, andere volgorde, dubbelklik, inventory/bank vol, uitloggen of
    teleport midden in, doodgaan, tweede account, randwaarden (0, max, leeg).
A9  LIVE-BEWIJS: na de release in een GEWONE 78-client (opnieuw gestart, zoals de eigenaar speelt) nagekeken op de live-server - wat
    alleen in de testclient werkt is NIET af (les N227/N249: kaarticonen).
A10 LIJSTEN + VERSLAG: BUGLIJST-status, dossier (KETEN-BEWIJS ingevuld), verslag aan de eigenaar met bug-NAMEN, wat getest is, wat niet,
    spawn-commando's; 'gefixt en live, door mij getest (hoe)'.

---------------------------------------------------------------------------------------------------------------------
K-ITEM. ITEM (elk item, zeker OSRS-geporte items en gear)
---------------------------------------------------------------------------------------------------------------------
I1  Naam, icoon (inventory + bank + winkel + grond + Items Kept on Death, zelfde kwaliteit), examine-tekst EXACT OSRS.
I2  ALLE opties (inventory, gedragen, grond) bestaan zoals OSRS en DOEN iets; Inspect/Check/Charges: juiste tekst en venster.
I3  Bonussen, eisen (skill-levels, quest), aanvalssnelheid/afstand, speciale effecten/setbonus - getallen naast de wiki.
I4  Model gedragen: man EN vrouw, voor/zij/achter, stilstaan/lopen/vechten, ARMEN/handen zichtbaar, geen strepen of gaten, met andere
    gear gecombineerd; grondmodel en inventory-model.
I5  Varianten: (i), (or)/kits, (l) locked, broken, uncharged/charged, noted - elk apart door I1-I4.
I6  Ruilbaar/onruilbaar zoals OSRS: trade, GE (kopen/verkopen/gidsprijs), drop/destroy; (i)-items onruilbaar.
I7  Dood: wat gebeurt er (houden/graf/breken/Perdu/coins voor killer) bij monsterdood en PvP-dood, in en buiten de wilderness (>20);
    Items Kept on Death toont PRECIES dat; repareren (Perdu/Bob/winkel) werkt; Trouver-lock.
I8  Hoe kom je eraan: drop/winkel/maken/quest - werkt, juiste prijs/kans; Drop Viewer + Collection Log tonen het.
I9  Geluid bij gebruik/aantrekken/special (zie K-GELUID).
I10 DUPE-CHECK: totaal (inventory+bank+graf+Perdu+GE) voor en na elke handeling (dood, oppakken, terugkopen, repareren) gelijk.

---------------------------------------------------------------------------------------------------------------------
K-BOSS. BOSS / MONSTER (elk gevecht-npc, zeker OSRS-geporte bosses)
---------------------------------------------------------------------------------------------------------------------
B1  Plek en gebied exact OSRS (spawn, loopgebied, lair/grot, single/multi, wilderness-level); geen vastzitten bij aankomst.
B2  Agressie zoals OSRS (wie, vanaf welke afstand, tolerantie).
B3  ELKE aanval/special/mechanic uit de wiki-pagina en de 'Strategies'-pagina: kansen, max hits, effecten (terugduwen, gif, drain,
    disarm, minions, heal, fases), met animatie + gfx + projectiel + geluid; overhead-teksten exact.
B4  Stats, bonussen, hitpoints, aanvalssnelheid, respawn-tijd naast de wiki.
B5  Droptabel exact (always, main, uniques, tertiair, pet-kans; noted/hoeveelheden); elke drop door K-ITEM.
B6  Pet als echte drop (ook als die pet nu in een winkel staat) + pet-checklist K02.
B7  Drop Viewer + Collection Log (pagina, alle items, kill count) + kill-count-melding na de kill.
B8  Wereldkaart- en minimap-icoon (boss/lair-ingang) - LIVE nagekeken (A9).
B9  Entree/fee/teleports/uitgangen; dood en PvP-regels in het gebied; loot voor top-damager/groep zoals OSRS.
B10 Deadman-taken, wereldmelding bij pet/zeldzame drop, geluiden bij drop.
--- uitgebreid 2026-11-05 (eigenaar: 'elke wilderness boss heeft meer punten') - B11-B30 horen er ALTIJD bij ---
B11 GELUID per onderdeel (bron: OSRS-cache seq-framegeluiden van ELKE seq van de boss + wiki-audio): aanval per stijl, special, projectiel
    start + inslag, treffer op de speler, blok/defend, dood, spawn/opstaan, fase-overgang, minion-aanval + minion-dood, omgeving (lair).
    Bewijs: [avtrace]-regel per geluid EN gehoord in de testclient; tweede account hoort het ook waar OSRS dat doet (K-GELUID).
B12 ANIMATIE + GFX per onderdeel: aanval, special, defend, dood, spawn, idle/lopen; geen 667-animatie waar OSRS een eigen seq heeft;
    projectiel-vlucht (hoogte, snelheid, vertraging) zoals OSRS.
B13 TICK-TIMING per aanval: aanvalssnelheid, hit-delay (projectiel = vertraging), special-cooldowns, hoe vaak elke aanval komt
    (aanvalscyclus/rotatie), wanneer een special telegraafeert en wanneer hij raakt.
B14 FASES + OVERGANGEN: hp-drempels, vorm-wissel (bv. Vet'ion -> Reborn), immuun tijdens overgang, minions per fase, wat er reset.
B15 MINIONS (elke minion = eigen mini-keten B1-B13 + B16): aantal, wanneer ze komen, max aantal, wat ze doen (heal, schild, aanval),
    of de boss immuun is zolang ze leven, despawn bij boss-dood/reset, geen drops waar OSRS ze niet geeft.
B16 GEBED + ZWAKTES: welke aanvallen door Protect from X geblokkeerd worden (volledig/deels), prayer-drain/disable, zwakte (stijl/
    element), immuniteiten (gif, venom, freeze, stun, special attacks, cannon toegestaan of niet).
B17 STATS PER STIJL naast de wiki: attack/strength/defence/magic/ranged levels + ALLE offensieve en defensieve bonussen; max hit per
    stijl; accuracy-check met een echte hit (trace) tegen max gear.
B18 PATHING + POSITIE: grootte (tegels), hoe hij loopt/achtervolgt, safespots zoals OSRS (bestaan of juist niet), terugduwen/trekken
    (niet door muren, wel tegen een muur waar OSRS dat doet), leash/terug naar spawn, niet vast op objecten.
B19 AGRESSIE + DOELWIT: wie hij kiest (dichtstbij, laatste aanvaller, willekeurig), wisselen van doelwit, multi-combat: meerdere spelers
    tegelijk, wat hij doet als het doelwit wegloopt/teleporteert/doodgaat/uitlogt; tolerantie-timer.
B20 MEERDERE SPELERS: schaal (hp/aanvallen per aantal spelers waar OSRS dat doet), top-damager/loot-verdeling, kc voor wie, pet-roll voor
    wie, groepsgedrag (bv. Venenatis >10 spelers).
B21 OVERHEAD + CHAT: overhead-teksten exact (wiki/transcript), wanneer ze komen; spelermeldingen (game-berichten) exact.
B22 DOOD VAN DE BOSS: dood-animatie, wanneer de drop valt (direct na de animatie), waar (tegel), wie hem ziet; respawn-tijd exact;
    wat er gebeurt met minions en effecten op spelers (gif, drain) na de dood.
B23 DOOD VAN DE SPELER bij de boss: doodregels D1-D20, graf-plek (in de lair? beloopbaar?), loot voor de PvP-killer in de lair,
    Escape Caves/uitgang, teleport-lock.
B24 LAIR/GEBIED: ingang (fee, eisen, Peek), binnenkomst-plek, teleport-lock, uitloggen in de lair, uitgangen + waar je uitkomt,
    single/multi-grenzen, wilderness-level in de lair, PvP tussen spelers in de lair.
B25 SLAYER: Krystilia/andere masters - task telt (ook superior/varianten), Slayer-unlocks die de boss raken (bv. 'Like a Boss'),
    slayer-helm-bonus.
B26 EXAMINE + NAAM + COMBAT LEVEL + rechtsklik-opties van boss EN minions exact OSRS.
B27 DEADMAN-SPECIFIEK: emblem-drop (kans, melding, grond of inventory volgens besluit), risk/skull-regels, Deadman-punten/taken.
B28 HERHAAL-GEDRAG: 5 kills achter elkaar zonder rare toestand (staat reset goed na kill, na speler-dood, na uitloggen, na server-restart).
B29 BRONLIJST per boss: wiki-pagina + Strategies + changelog-datum + cache-ids (npc, seqs, spotanims, geluiden) in het dossier; wat geen
    bron heeft = vraag aan de eigenaar.
B30 RAPPORT per boss in team\dossiers\N522-FIX-<boss>.txt (of het eigen dossier): elk punt B1-B29 OSRS | ons | verschil | bewijs. Een
    boss is pas AF als er geen OPEN/DEELS/FOUT meer in zijn rapport staat.

---------------------------------------------------------------------------------------------------------------------
K-NPC. NPC (praten, winkel, dienst)
---------------------------------------------------------------------------------------------------------------------
N1  Plek, uiterlijk, naam, examine, rechtsklik-opties zoals OSRS; elke optie doet iets.
N2  Dialoog: OSRS-transcript (wiki 'Transcript:'), juist hoofd + animatie, alle takken; geen standaardtekst.
N3  Winkel/dienst (Trade, repair, teleport): opent, prijzen, ook als je 'niets hebt' (bv. Perdu zonder verloren items).
N4  Werkt ook op afstand waar OSRS dat kan (NPC Contact e.d.).

---------------------------------------------------------------------------------------------------------------------
K-GEBIED. GEBIED / KAART / OBJECT
---------------------------------------------------------------------------------------------------------------------
G1  Kaart zoals OSRS (import of bestaande grond): geen zwarte vlakken, geen 667-resten die OSRS niet heeft (volcano), niets weghalen
    dat niet weg hoeft.
G2  Collision: overal lopen waar het hoort, nergens door muren/barrieres; geen afgesloten vakken; teleport-landingen beloopbaar.
G3  Ingangen/uitgangen (en wat alleen in of alleen uit gaat zoals OSRS), deuren, barrieres (+ regels zoals teleblock bij Ferox).
G4  Alle teleports ernaartoe (spreuken, sieraden, obelisken, tablets) en hun landingen.
G5  Wereldkaart- en minimap-iconen kloppen; iconen van content die we niet hebben weg - LIVE nagekeken (A9).
G6  Spawns in het gebied, wilderness-level, multi/single, safe/dangerous zone, HUD.
G7  Wat er al lag (clue-stappen, hide&seek, burning amulet/max cape-landingen, obelisken, andere bosses) nog werkend.
G8  Objecten: naam, examine, opties, model zichtbaar (niet onzichtbaar/zwart), animatie.

---------------------------------------------------------------------------------------------------------------------
K-UI. INTERFACE
---------------------------------------------------------------------------------------------------------------------
U1  Naast een OSRS-screenshot: indeling, tekst passend (niets afgekapt), kleuren, iconen, hover-effecten.
U2  Elke knop/regel/tab klikbaar en juist; scrollen; sluiten (X, Esc) zoals OSRS.
U3  Fixed, resizable, Stretched; open blijven/sluiten bij tab-wissel, gevecht (auto-retaliate), dood, teleport.
U4  Inhoud = echte data (bv. Items Kept on Death = de echte doodregel; Collection Log = ALLE items).
U5  Geen melding/tick-spam naar de client (netwerk).

---------------------------------------------------------------------------------------------------------------------
K-DOOD. DOOD / PvP / PERDU
---------------------------------------------------------------------------------------------------------------------
D1  Doodregels D1-D20 (HOOFDLIJST sectie 6) per itemgroep: monsterdood vs PvP, skull vs geen skull, wilderness <20/>20, guard-dood.
D2  Graf, Death's Office, Perdu (lost property, terugkopen, repair, ook met lege lijst), Bob, coins voor de killer.
D3  Items Kept on Death-venster toont exact wat er gebeurt.
D4  Pets/trinkets/kits/burden-inhoud volgens de besluiten; loot keys.
D5  Geluiden + meldingen bij dood (ook guard-dood), respawn-plek, skull na respawn.
D6  DUPE-CHECK (I10) over de hele dood-route: niets twee keer (opgepakt EN in Perdu).

---------------------------------------------------------------------------------------------------------------------
K-GELUID. GELUID / ANIMATIE / GFX
---------------------------------------------------------------------------------------------------------------------
S1  Juiste OSRS/667-id (bron: seq-framegeluiden, wiki-audio) in [avtrace] EN gehoord in de client.
S2  Alle varianten (andere specials/wapens van dezelfde familie, andere npc's van dezelfde soort).
S3  Volume gelijk aan vergelijkbare geluiden; anderen horen het ook (tweede account) waar OSRS dat doet.

---------------------------------------------------------------------------------------------------------------------
K-WINKEL. WINKEL / ECONOMIE / GE
---------------------------------------------------------------------------------------------------------------------
W1  Juiste categorie/groepering/volgorde (bv. trinkets niet onder Pets), alle items die er horen (niets ontbreekt).
W2  Getoonde prijs = betaalde prijs; punten/valuta; inventory vol; noted.
W3  Preview klopt; gekocht item werkt (K-ITEM).
W4  GE: alleen ruilbare items; gidsprijs zinnig. W5 DUPE-CHECK (I10).

---------------------------------------------------------------------------------------------------------------------
K-SPREUK. SPREUK / GEBED / SKILL-ACTIE
---------------------------------------------------------------------------------------------------------------------
P1  Eisen (level, runen/punten, quest/unlock), kosten, XP exact OSRS; weigeren zonder runen/punten met de OSRS-melding.
P2  Effect + duur + getallen (Show stat adjustments) naast de wiki/OSRS Deadman.
P3  Animatie, gfx, geluid; boek-wissel met melding/geluid; slepen/filter/quick-prayers in alle 3 boeken.
P4  Waarschuwingen/bevestigingen zoals OSRS (bv. boek lezen, avarice).

---------------------------------------------------------------------------------------------------------------------
K-CLIENT. CLIENT-WEERGAVE (Stretched, schaal, renderers, HUD)
---------------------------------------------------------------------------------------------------------------------
C1  Naast de RuneLite/OSRS-referentie in ELKE modus (fixed/resizable, Stretched aan/uit, OpenGL/DirectX/Software).
C2  Elke instelling over het hele bereik (pijltjes EN typen), na herstart onthouden, eigen prefs (nooit die van de eigenaar).
C3  Nieuwe client gereleased en in een gewone 78-client nagekeken (A9).

---------------------------------------------------------------------------------------------------------------------
K-QUEST. QUEST / BELONING / UNLOCK
---------------------------------------------------------------------------------------------------------------------
Q1  Start, alle stappen, herkansing, relog per stap, voltooiing, beloningen (ook volle inventory -> bank).
Q2  Oude accounts (legacy) krijgen wat ze horen; verloren beloning terug te krijgen (zoals OSRS of besluit).
Q3  Quest-tab/journaal/Quest Guide-tekst past en klopt.

---------------------------------------------------------------------------------------------------------------------
K-PET. PET - de 22-punten-checklist in HOOFDLIJST sectie 5 / K02 (team\dossiers\PET-AUDIT.txt).

---------------------------------------------------------------------------------------------------------------------
K-FIX. FIX-AUDIT VAN BESTAANDE CONTENT (eigenaar 2026-11-05/11-06: eerst fixen, importeren waar nodig - voor alles wat al in de game zit)
---------------------------------------------------------------------------------------------------------------------
F1  INVENTARIS: alles van het onderdeel opgesomd (npc's + minions, items, locs, seqs, geluiden, interfaces, gebieden) met ids, uit de
    code (codegraph) EN de cache - niets overslaan omdat het 'al live' of 'AF' is.
F2  OUDE CLAIMS WANTROUWEN: dossier/commit/GPT-verslag zegt 'OK' -> zelf opnieuw bewijzen. Staat er 'DEELS / OPEN / VRAAG / niet
    OSRS-bewezen', dan is dat punt FOUT tot het bewezen is.
F3  PER PUNT van de passende keten(s) (K-BOSS B1-B30, K-ITEM, K-GEBIED ...): OSRS-waarde met bron | onze waarde (code/trace/screenshot)
    | verschil. Alles in EEN rapport per onderdeel.
F4  IN-GAME TESTEN als speler (testclient, max gear, 2 accounts waar multi/PvP telt): elke aanval/optie/route echt uitgevoerd,
    [avtrace]/[trace]-regels erbij. Code lezen alleen is geen bewijs.
F5  ELK verschil = fixen in dezelfde run (hele klasse); wat geen bron heeft = vraag aan de eigenaar, verzamelen, advies erbij.
F6  OPRUIMEN: geen 667-resten die OSRS niet heeft, geen dubbele handlers, geen dode code van oude versies.
F7  REGRESSIE: conformance-suite van het domein + gerichte tests; wat de eigenaar al goedkeurde (BUGLIJST AF) blijft werken.
F8  STATUS: pas AF als het rapport geen OPEN/DEELS/FOUT meer heeft + A9 LIVE-bewijs.
=====================================================================================================================


```

### C:\RSPS\team\dossiers\GOEDGEKEURDE-ONTWERPEN.txt L1-L138
SHA256 e5b0106f89f727014c91ce941e964bf3f66fc4713f27973fc70955b98030364b
```text
GOEDGEKEURDE ONTWERPEN - de eigenaar keurt ze goed, er wordt EXACT dit gebouwd (pas bouwen na zijn "go")
Bijgehouden sinds 2026-10-24. Nieuwe goedkeuring = hier bijschrijven (datum, plaatje, bron, wat er precies is afgesproken).

1. Drop Viewer / Collection Log / quest-tab 4 tabs / Deadman Tasks-venster (2026-10-24 03:05-03:20)
   screens\team\N110\GOEDGEKEURD\01-06 (bron\*.html) - spec team\dossiers\NACHTOPDRACHT-DROPVIEWER-QUESTTAB-2026-10-24.txt
   LET OP: de Drop Viewer-tabel is vervangen door ontwerp 3, de Deadman-tab door 4 en de Collection Log door 5 hieronder.

2. Deadman Info-venster + Info-knop (2026-10-24 10:4x)  - GEBOUWD, LIVE 12:14 (N274)
   screens\team\INFO\info-v1.png (bron info-v1.html)

3. Drop Viewer sortering + Wiki-stijl (2026-10-24 12:3x / 12:5x)  - GEBOUWD, LIVE 14:24 (N279) - DEELS VERVANGEN door punt 6
   - Groepen met kopjes (variant A uit screens\team\SORT\sort-v1.png): Pets, Charms, Uniques, Weapons and armour,
     Runes and ammo, Materials, Herbs and seeds, Food and potions, Coins and other, Extra rolls, Drop tables;
     lege groepen weg.
   - BINNEN elke groep: zeldzaamste bovenaan (normale kans); gelijke kans -> hetzelfde item bij elkaar; charms vaste
     volgorde (gold, green, crimson, blue). De prijs-sortering is AFGEKEURD.
   - Rare / Gem drop table: ALTIJD open (items meteen zichtbaar).
   - Stijl B5: screens\team\SORT\dropviewer-stijl-v5.png (RECHTS), bron screens\team\SORT\dvw.html#mix5:
     lijst links donker RuneScape met lichte plaatjes en rustige gouden kopjes; droptabel als OSRS Wiki-raster
     (perkament d8ccb4 / icoonvak e2dbc8, kopregel b8a282, lijnen 94866d - gemeten op oldschool.runescape.wiki),
     icoon in eigen vakje, sectiekop = vette tekst met lijn eronder (geen aantal), itemnaam + kans in p12 (kans vet op
     het gekleurde Wiki-vakje), aantal + Normal in p11 donker, Normal altijd ingevuld (grijs als gelijk), geen Pet-label
     (het kopje Pets zegt het), bracket-regel als gouden tekst in de kop onder de labels, licht perkament model-vak,
     GEEN "Combat 138" (N284), npc-model draait NIET (N283).

4. Deadman-tab (quest-tab 2) A3 (2026-10-24 13:0x)  - GEBOUWD, LIVE 14:24 (N288); 14:35 puntenrijen -> Info op die punten (eigenaar "ja")
   screens\team\INFO\dmtab-v3.png (midden = geen kist, rechts = kist staat er), bron screens\team\INFO\dmtab-v3.html.
   - Tab-icoon 2 = het DMM-schedel-icoon van de gameframe (OSRS sprite 4815, QuestTabInterfaceImportTool.GAMEFRAME_ICON),
     niet de witte skull.
   - Info-knop: blauwe i precies in het midden van de knophoogte.
   - Bracket-vak: badge "Bracket / 96+" + twee regels "Combat XP 20x" en "Rare drops 5x" (p12); de regel "Fights lvl" is WEG.
   - Skull / Kill grace als twee vakjes (labels p12); skull-tijd = precies de tijd van de DMM HUD (N286).
   - Punten als rijen met het HELE getal rechts (p12), met de iconen van de 78 Store in de GE: Loyalty = blauwe munt
     (sprite 7957), Donator = diamant (7955), Deadman = rode munt (7956). Niet meer via de tab de stores openen (N280).
   - Archaic Emblem-rij apart (3 px ruimte), met pijltje: klikken = Info-venster op Archaic Emblem (tier, waarde en meer).
   - Breach + Deadman's Chest: 2 regels; kist actief = Map-knop naast de titel, plek op regel 2 (lange namen kort).
   - Overal gelijke ruimtes (3 px), lichtere grijze labels, niets overlapt (ook niet met saldo's van 2,1 miljard).

5. Collection Log = A (2026-10-24 13:2x)  - GEBOUWD, LIVE 14:24 (N297)
   screens\team\SORT\collectionlog-v1.png LINKS (A), bron screens\team\SORT\cl.html#a: zoals OSRS (donker), beter leesbaar -
   lijst in p12, items die je hebt VOL in kleur met gele teller linksboven, items die je mist gedimd (ongeveer 1/3, iets grijs),
   kop met naam, Obtained x/y en de kill-/open-teller. B en C (Wiki-stijl / voortgang) zijn NIET gekozen.
   Volgorde op de kist- en breach-pagina: eerst armour/wapens, pets ONDERAAN; Clues zonder noted items; geen Zaryte bow;
   geen GWD-pets op kist/breach.

6. Drop Viewer v6 - wijzigingen op B5 (2026-10-24 15:0x-15:3x, eigenaar per vraag)  - GEBOUWD 2026-10-24 17:xx (zie uitvoering)
   Voorbeeld: screens\team\SORT\dv6.png (bron dv6.html, echte data). Per punt (details + bug-ids in BUGLIJST N304-N317):
   - N305 de npc-plaatjes (lijst links + groot model-vak) weer op de OUDE DONKERE RS-achtergrond, niet het Wiki-perkament.
     Stilstaande 3/4-hoek blijft. De Wiki-tabel rechts (perkament, lijnen, kleurvakken) blijft.
   - N310 rijen 26 px met scherpe item-plaatjes ~30x26; Always-balk grotere plaatjes.
   - N308 Normal-kolom ook een Wiki-kleurvak, LICHTERE tint (banden blijven OSRS Wiki).
   - N315 binnen een groep sorteren op YOUR CHANCE, zeldzaamste bovenaan (vervangt "normale kans" uit punt 3).
   - N304 groep Extra rolls direct onder Pets.
   - N307 breach Always: "Archaic emblem (tier 5) (250+ dmg, 1x per breach)".
   - N312 breach-tabelregel heet "Chest reward" (1 item from the Deadman's Chest), items eronder met echte eindkans.
   - N311 GWD-minions ingesprongen onder hun boss in Bosses.
   - N314 echte OSRS-sluitknop; "Show on map" nette knop, WEG als er geen plek is.
   - N317 geen "al gekregen"-vinkje in de Drop Viewer (alleen Collection Log).
   - N309 Qty-kolom 46 px + grote getallen afkorten (9K-11K), zoals dv6.png (eigenaar A, 15:3x).
   Regels pets (N306/N313/N316, eigenaar): boss-pet alleen van zijn boss met OSRS-kans + bracket-boost; kist alleen pets zonder
   boss op de server; breach geen kist-pets. Collection Log blijft zoals OSRS (N318).
   UITVOERING (zo gebouwd, Claude 2026-10-24, nagekeken in de testclient screens\N319):
   - Kolommen in een rij: icoon 0-32 | item 32-158 | Qty 158-206 (46 px tussen de lijnen) | Your chance 206-269 | Normal 269-314.
     Your chance werd 62 breed (vet "1/2,600" paste niet in 55), Normal 44.
   - Item-plaatjes 28x25: de client verkleint het 36x32 item-sprite met middeling (RspsObjSmooth), niet meer met weggevallen
     pixelrijen; zo passen ze scherp in de 26 px rij (ook in de Always-balk).
   - Lange namen: p12 op 1 regel, anders p11 op 2 regels (ook in de lijst links); "..." alleen als 2 regels niet genoeg zijn.
   - Qty: getallen die niet passen in duizendtallen (9K-11K, 20.5-21K), anders op 2 regels; "(noted)" onder het aantal.
   - Always-balk: naam + kleinere 2e regel met het aantal (Coins 5,000-25,000) en/of de uitleg "(250+ dmg, 1x per breach)".
   - Kansen vanaf een miljoen als M ("1/3.86M"); een ander monster opent de tabel bovenaan.
   - Sluitknop = OSRS sprite 535 in alle N108-vensters (Drop Viewer, Collection Log, Info, Deadman Tasks).

7. Loyalty-pets (2026-10-24 15:0x, eigenaar per vraag)  - GEBOUWD 2026-10-24 19:xx (commits 85cbd60e0 + 6d3d903b3), nog niet live; loopgeluiden eigenaar C (benadering: draakjes Dragon Slayer-stap, honden voetstap, krab scuttle)
   - N257 hoofden in dialoog + Follower Details met de cache-pose voor dierenhoofden (8373 / 8374), bewezen screens\N257\anim.
   - N301 per soort 2-3 willekeurige grappige gesprekjes (speler + pet), alleen Loyalty store-pets; dierengeluid alleen bij
     regels waarin het dier geluid maakt (*Woof!*). Voorbeelden in dv6.png sectie 4.
   - N299 apen: apengeroep uit de jungle; gekko/kameleon: hagedis; pinguin: pinguinroep (geen museumgeluiden).
   - Loopgeluiden zacht, alleen draakjes, honden/puppies, krab.
   - N300 Pet rock uit de Loyalty store (wie hem heeft houdt hem) - uitvoering in pakket 4 winkels.

8. Beast of burden trinkets N423 (2026-10-29 22:3x-23:3x, eigenaar per vraag; foto v6 HELEMAAL AKKOORD)  - NOG NIET GEBOUWD
   Foto: screens\N423\N423-GOEDGEKEURD-v6.png (per trinket groot: screens\N423\v6-<plekken>-detail.png). Generator:
   work\claude-scripts\n423-trinkets\trinkets5.py (+ trinkets4_base.py). Afgekeurd: v1 (wolf), v2 (eigen tekeningen), v3-v5 (details).
   - 5 items: Kalphite burden trinket (6 plekken), Bull ant burden trinket (9), Terrorbird burden trinket (12), Tortoise burden
     trinket (18), Pack yak burden trinket (30). Summoning zelf blijft gelijk.
   - Uiterlijk: PRECIES de grijze Trinket of fairies (23822), de paddenstoelen vervangen door het ECHTE icoon van de pouch
     (Spirit kalphite 12063 / Bull ant 12087 / Spirit terrorbird 12007 / War tortoise 12031 / Pack yak 12093), exact, in het
     midden, met een dunne donkere rand; grijs blijft voor alle 5. Geen eigen tekening, geen andere kleuren.
   - Opties op de trinket: Bind / Check / Unbind / Examine. Trinket GAAT NIET OP: blijft een item (mag in de bank liggen),
     onruilbaar, nooit kwijt (bij elke dood behouden, gratis terug bij Perdu). 1 trinket = 1 pet tegelijk. Bind = ook de
     trinket op de pet gebruiken. Grotere binden op een pet met een kleinere: kleinere wordt losgemaakt (blijft van jou).
     Unbind alleen met een lege pet; de pet heet dan weer gewoon <naam>.
   - Bind: uitleg-dialoog met trinket-plaatje ('Bind this trinket to your Beef? It will carry 30 items for you - just like a
     Pack yak. You keep the trinket and can unbind it at any time.'), dan 'Select an Option': 'Yes, bind it to my Beef.' /
     'No, not now.'; daarna premium geluid + korte paarse gloed op de pet + chat: 'You bind the Pack yak burden trinket to
     your Beef.' en paars 'Your Beef has become an Upgraded Beef: it now carries 30 items for you!'
   - Check: 'Your Pack yak burden trinket is bound to your Upgraded Beef (30 storage).' (of: not bound to a pet).
   - Pet met trinket heet 'Upgraded <naam>' voor IEDEREEN (boven de pet, rechtsklik-menu, Follower Details).
   - Examine (ook andere spelers): de gewone examine + paarse regel 'This pet has been upgraded with a <trinket>.' (GEEN aantal).
   - BoB werkt PRECIES als onze BoB-familiar (Pack yak): rechtsklik op de pet Talk-to, Store, Pick-up (+ Metamorphosis/
     Interact als de pet die heeft), Walk here, Examine - Store opent het Familiar Inventory-venster (671 + 665);
     Follower Details 4 knoppen: Take BoB + Store (op de plek van Renew, rugzak-icoon) boven, Call + Pick-up onder;
     orb (rechtsklik, zelfde volgorde als BoB-familiar): Call Follower, Take BoB, Pick-up, Follower Details, Select left-click
     option (geen Cast/Renew). Pick-up met spullen = als Dismiss (spullen naar Death's Office, gewone fee); PvM-dood -> graf,
     PvP-dood -> killer. Upgrade hoort bij de pet op het account (Pick-up, bank, Metamorphosis, uitloggen, Perdu).
   - Herkomst: Kalphite 6 / Bull ant 9 / Terrorbird 12 = drop van Deadman's Chest, breach-monsters, breach-bosses en ALLE
     bosses + in de Drop Viewer, kansen (23:5x): boss-kill incl. breach-boss 1/250 / 1/500 / 1/1.000, breach-monster 1/1.000 /
     1/2.000 / 1/4.000, Deadman's Chest 1 op 100 / 200 / 400, GEEN bracket-boost; wereldmelding bij elke drop; ALLE 5 in de
     Collection Log; meerdere (ook dubbele) trinkets mogen, elk aan een eigen pet; Tortoise 18 / Pack yak 30 voorlopig Donator store
     (eigenaar beslist later; besluit 'donaties alleen cosmetisch' bewust tijdelijk losgelaten).
   - Bind/Unbind overal behalve in gevecht. Examine trinket: 'The spirit of a <pack yak>, sealed in stone.' (per soort).
   - Alle kleine details als een BoB-familiar (uit pakken = bukanimatie 827 + uit-pak-geluid, inleggen = in-stop-geluid).
   - Samen met N424: looting bag volledig uit het spel (geen omzetting, 'er spelen geen mensen').
- 2026-10-30 N356 stats-tab onderkant: ONTWERP A - 'Total level' in EEN vak over de volle breedte van de onderste rij (kader = skill-vak
  sprite 3030, uitgerekt met vaste hoeken), tekst 'Total level:' boven het getal, midden. Plaatje: screens\N356\mock-A-en-B-zoom.png (links).

=== DOOD + KILL - GOEDGEKEURD DOOR DE EIGENAAR 2026-11-01 01:5x-02:0x ('allles akkoord', D20 aangepast). Detail + bronnen: team\dossiers\N502.txt en N502-DOOD-KILL-ONDERZOEK-2026-11-01.html. Vervangt eerdere doodregels waar ze botsen (26-09 Protect Item 1, 04-10 lockables houden, 10-24 pet altijd houden, D-09 guard 2 minuten). ===
  D1 Protect Item UIT in alle 3 de gebedenboeken; het gebed blijft zichtbaar met het OSRS High Risk-icoon (zoals op een OSRS high-risk wereld).
  D2 Monster-dood: je houdt je 3 duurste items als je GEEN skull hebt, 0 als je WEL een skull hebt (OSRS Deadman).
  D3 Monster-dood: je ruilbare items gaan naar je graf (zoals nu; besluit 04-10 'PvM is veilig').
  D4 Speler-kill: andere untradeables breken + Perdu repareert / koopt terug (zoals nu).
  D5 PvP-loot op de grond: 1 minuut alleen voor de killer, daarna in de Wilderness nog 4 minuten, buiten de Wilderness nog 60 minuten voor iedereen (OSRS).
  D6 Kill grace: na ELKE kill in single-combat 1 minuut niet aan te vallen; stopt bij aanvallen, doodgaan, uitloggen en teleporteren; punten en emblems alleen bij een geldige kill.
  D7 Guard doodt een speler met skull: loot meteen op de grond voor iedereen, 60 minuten.
  D8 Killer al uitgelogd: loot op de grond voor iedereen, met de PvP-tijden van D5.
  D9 Starter pack na een PvP-dood (OSRS Deadman: Annihilation).
  D10 Kill door je eigen alt (zelfde IP of apparaat): je spullen naar je eigen graf, niets naar de killer.
  D11 Practice PvP: veilige dood (niets verliezen, geen loot).
  D12 Loot keys bij een monster-dood: keys + inhoud weg, zoals OSRS.
  D13 Pet bij een speler-kill: naar je graf (de killer krijgt hem nooit).
  D14 Burden trinkets en ornament kits: naar je graf bij elke dood.
  D15 Skull-kleur bij het respawnen opnieuw uitrekenen (nooit respawnen met de oude kleur).
  D16 Je familiar sterft terwijl jij leeft: zijn spullen vallen op de grond waar hij stierf (OSRS).
  D17 Upgraded pet oppakken (Pick-up): zijn spullen blijven in de pet bewaard.
  D18 Items Kept on Death-scherm: standaard 'Killed by a player', met een vinkje 'PvM death' (OSRS).
  D19 Melding bij inloggen als je pets nog spullen dragen.
  D20 Guard + skull of killer uitgelogd: untradeables breken en het killer-geld komt op de grond.

```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L8-L8
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (A25, POH exit portal) De exit portal gaat naar het midden van de garden, zoals jij vroeg (in OSRS en bij de donors -> A (owner 2026-10-16: ruilen - boom naar de hoek linksboven, waar de portal nu staat)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L9-L9
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (A22, obelisk) De POH-obelisk krijgt de OSRS-opties Activate / Teleport to destination / Set destination + het OSRS- -> A (owner 2026-10-16: niets veranderen - alleen de POH-obelisk krijgt het scherm)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L12-L12
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (A30, Deadman HUD in fixed) De HUD staat op jouw plek 427,249 (linkerbovenhoek). Met skull-timer, emblem of -> A (owner 2026-10-16: zo laten - alleen omhoog schuiven als hij anders over de chat loopt)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L16-L16
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (A11, Ring of suffering imbue bij Perdu) Je koos op 10-11: imbue via een NPC, charges blijven. In OSRS kost een imbue -> A) 600 Deadman Points, alleen Ring of suffering en (r) (eigenaar 10-17)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L22-L22
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (A17, ::mod-venster) Het venster krijgt de 4 tabs die jij koos: Players / Punish / Teleport / Info (+ zoekknop). Nu zijn er -> A) Recent en Gear allebei weg als tab (eigenaar 10-17)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L25-L25
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (N29, X-log bij een boss) Als je bij een boss je client sluit of je verbinding kwijtraakt, blijft je lichaam nu staan -> A - zoals OSRS: lichaam blijft staan zolang je geraakt wordt, max 60 seconden, ook bij PvP (eigenaar 10-17 15:40)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L27-L27
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (N92) Je schreef: 'our rsps needs the same exp as dmm anihilation'. Welke XP-snelheid bedoel je? -> A - XP-rates van Deadman Annihilation: team zoekt de exacte getallen op, eigenaar keurt ze goed (eigenaar 10-17 15:40)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L33-L33
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (N77, gif/venom/burn-timers) Je melding 10-16: "the timer needs to reset when trying to log out or teleport". Wat het spel -> A - gif/venom blijft na inloggen, klok tot volgende tik opnieuw (18 sec), teleport verandert niets, verder OSRS (eigenaar 10-17 15:25)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L35-L35
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (N91 + N90, teleport in gevecht) Je meldingen 10-17 ("wegrennen en teleporteren: you can't use that teleport at the -> B - zoals OSRS Deadman: geen timer, melding met de seconden, daarna direct (eigenaar 10-17 15:20)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L37-L37
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (N111, punten die al omgeslagen zijn) N106 zorgt dat Loyalty/Donator/Deadman/PK/Slayer/Breach Points voortaan stoppen -> A - het maximum (2.147.483.647), eenmalig bij inloggen (eigenaar 2026-10-18)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L38-L38
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (N92, XP-rates) Summoning bestaat niet in OSRS, maar telt bij ons mee voor je combat-level (net als Prayer). Welke -> B - Summoning krijgt de skilling-rate, altijd 10x (eigenaar 2026-10-18)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L39-L39
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (N100, drop rate) Bij de meeste monsters geeft de hoofdtabel ALTIJD 1 item (zoals op de wiki). Dan kan niet elke regel -> a) A - zeldzaam eerst: elk item x3/x3/x4/x5 kans, gewone items (munten, runes) iets minder, nog steeds 1 item per kill uit de hoofdtabel (advies Claude op 'adviseer': geen inflatie van munten/runes). b) A + B + C - monsters en bosses bij kill, Barrows-kist + Godwars-sleutelstukken, breach-monsters (BreachLoot); NIET clues/boxes/skilling. Eigenaar: 'dit moet allemaal in de npc drop viewer' - de drop viewer (N108) toont de geboostte kansen per bracket voor al deze bronnen (eigenaar 2026-10-18)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L40-L40
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (A36, A37, A38 - je meldingen van 10-11 over de Deadman-tabjes) Je nieuwe wensen van 10-17 dekken ze: A36 "Deadman -> A - alle drie samen afsluiten met N109/N103, N108 en N102; drop viewer = venster in het spel (N108), zoals de eigenaar 10-17 al besloot (advies Claude op 'adviseer', eigenaar 2026-10-18)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L45-L45
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (N102, schedel-tab 'Deadman 78' in de quest-tab - onderzoeker 2026-10-18 05:26) Je zei 10-17: eerst de indeling, dan -> A - losse regels, strak lijstje zonder vakjes (eigenaar 2026-10-18 10:18)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L47-L47
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (N106/N111, kleine negatieve saldi) Jouw keuze bij 32 = omgeslagen saldo wordt eenmalig het maximum. Een saldo als -> A - ook het maximum (zoals N106 nu gebouwd is) (eigenaar 2026-10-18 10:18)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L51-L51
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (team, 2026-10-18 08:45 - Claude-hoofdchat) Het N92-pakket op de VPS-keurplek zei 'nieuwste bouw', maar bevatte de server van -> A - klaarzetten weigert een pakket waarvan de jar niet echt van die commit gebouwd is (eigenaar 2026-10-18 10:18)
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L55-L55
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (N92 XP-rates Annihilation - Claude-hoofdchat 2026-10-21 11:42, op verzoek keurmeester N92 r4) Je zei bij 22: team zoekt de -> A op a-e (2026-10-21 20:50, eigenaar klikte 'Ja, goed zo' op samenvatting: combat 10/15/15/20x, skilling 10x, drops oplopend; alles zoals gebouwd en getest mag vrijdag mee live).
```

### C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt L59-L59
SHA256 bec743bf4e7a87062b479787e3f2e85f2298d45b163a2c42374b91787ac3db8b
```text
- (N97, PJ-timer in Deadman - onderzoeker 2026-10-21 20:40) Als iemand jou aanvalt in een single-way gebied, mag een ander -> B (2026-10-21 20:50, eigenaar): 17 ticks (10,2 s) voor iedereen, zijn keuze van 25-09 terug.
```

### C:\RSPS\team\dossiers\DOOD-OVERZICHT-PER-GROEP-2026-10-24.txt L1-L11
SHA256 2ec6940f68ae27ee6c4422baa466a214bb533d85baae48170098381247b14453
```text
LET OP (2026-11-01): dit overzicht is van 24-10. De eigenaar keurde op 11-01 nieuwe doodregels goed (D1-D20): zie team\dossiers\GOEDGEKEURDE-ONTWERPEN.txt (blok DOOD + KILL) en N502.txt. Waar ze botsen, gelden D1-D20.

DOOD-OVERZICHT PER ITEMGROEP - live release 20261024-220050 (24-10-2026 22:30)
Gemaakt door de echte doodregels van de server door te rekenen voor elk item (UntradeableDeathReportTests) en getest met echte
doden op de testserver (bot doodt bot op Wilderness 5 en 25, Hill giant doodt bot: 56/56 goed). Zonder Protect Item.
Protect Item houdt altijd 1 item extra (het duurste). Volledige lijst per item: UNTRADEABLES-DOOD-PER-ITEM-2026-10-24.txt

WAT ER GEBEURT ALS JE GRAF VERLOOPT
- Het graf staat 15 minuten (de tijd loopt alleen als je ingelogd en actief bent, en wordt verlengd als je opnieuw doodgaat).
- Daarna gaat ALLES uit het graf naar Death's Office (onbeperkt veel plek). Ophalen is gratis onder 100.000 waarde,
  daarboven 5% van de waarde. Er gaat dus niets verloren bij een monster-dood.
```

Groep A. BREEKBARE ITEMS (61); volledige itemopsomming in owner-decisions-literal.txt.

### C:\RSPS\team\dossiers\DOOD-OVERZICHT-PER-GROEP-2026-10-24.txt L14-L19
SHA256 2ec6940f68ae27ee6c4422baa466a214bb533d85baae48170098381247b14453
```text
   Monster-dood: je HOUDT ze heel.
   PvP <=20: BROKEN in je inventory (echt OSRS broken-plaatje), Perdu repareert ze (OSRS-prijs), killer krijgt die prijs in coins.
   PvP >20: Fire capes en defenders: ook BROKEN in je inventory (OSRS). De rest wordt VERNIETIGD, killer krijgt coins;
            Perdu geeft ze terug voor de reparatieprijs (EIGEN REGEL 30-09, in OSRS ben je ze dan echt kwijt).
   Gelockt met Trouver parchment ((l)): PvP <=20 broken + blijft gelockt; PvP >20 MANGLED + blijft gelockt (reparatie 500k).

```

Groep B. ALTIJD HOUDEN (4); volledige itemopsomming in owner-decisions-literal.txt.

### C:\RSPS\team\dossiers\DOOD-OVERZICHT-PER-GROEP-2026-10-24.txt L21-L22
SHA256 2ec6940f68ae27ee6c4422baa466a214bb533d85baae48170098381247b14453
```text
   Bij elke dood (monster en PvP) houd je ze.

```

Groep C. PERDU LOST PROPERTY (87); volledige itemopsomming in owner-decisions-literal.txt.

### C:\RSPS\team\dossiers\DOOD-OVERZICHT-PER-GROEP-2026-10-24.txt L24-L25
SHA256 2ec6940f68ae27ee6c4422baa466a214bb533d85baae48170098381247b14453
```text
   Monster-dood: naar je GRAF. PvP (<=20 en >20): weg, killer krijgt coins, je KOOPT ZE TERUG bij Perdu (Trade).

```

Groep D. OMZETTEN NAAR HET BASIS-ITEM (189); volledige itemopsomming in owner-decisions-literal.txt.

### C:\RSPS\team\dossiers\DOOD-OVERZICHT-PER-GROEP-2026-10-24.txt L27-L29
SHA256 2ec6940f68ae27ee6c4422baa466a214bb533d85baae48170098381247b14453
```text
   Monster-dood: naar je GRAF. PvP: de killer krijgt het gewone item (bv. Ring of suffering (i) -> Ring of suffering,
   Dark bow-kleur -> Dark bow, Barrows -> broken Barrows, (or)-item -> item + kit). Bij een Deadman/Bounty Hunter-kit gaat de kit naar JOUW graf.

```

Groep E. ANDERE ONVERHANDELBARE ITEMS (803); volledige itemopsomming in owner-decisions-literal.txt.

### C:\RSPS\team\dossiers\DOOD-OVERZICHT-PER-GROEP-2026-10-24.txt L31-L33
SHA256 2ec6940f68ae27ee6c4422baa466a214bb533d85baae48170098381247b14453
```text
   Monster-dood: naar je GRAF. PvP (<=20 en >20): weg, killer krijgt de alch-waarde in coins.
   NIEUW (eigenaar B, 24-10 22:4x, gebouwd, live met bundel 5): je KOOPT ZE TERUG bij Perdu voor hun winkelwaarde.

```

Groep F. VERHANDELBARE ITEMS (alles wat je in de GE/trade kunt verhandelen; volledige itemopsomming in owner-decisions-literal.txt.

### C:\RSPS\team\dossiers\DOOD-OVERZICHT-PER-GROEP-2026-10-24.txt L35-L56
SHA256 2ec6940f68ae27ee6c4422baa466a214bb533d85baae48170098381247b14453
```text
   Monster-dood: naar je GRAF. PvP: naar de KILLER (in zijn loot key).

DONATOR-, LOYALTY- EN DEADMAN-WINKEL
- Donator Shop: 120 items, 81 verhandelbaar (PvP -> killer), 39 onverhandelbaar.
  Verhandelbaar: 3rd Age axe, 3rd Age bow, 3rd Age cloak, 3rd Age druidic robe bottoms, 3rd Age longsword, 3rd Age pickaxe, 3rd Age plateskirt, 3rd Age wand, Amulet of fury (or), Amulet of torture, Ancestral hat, Ancestral robe bottom, Ancestral robe top, Anguish ornament kit, Armadyl godsword ornament kit, Bandos godsword ornament kit, Berserker necklace ornament kit, Blue dark bow paint, Dark infinity colour kit, Dragon boots ornament kit, Dragon chainbody ornament kit, Dragon defender ornament kit, Dragon full helm (or), Dragon full helm (sp), Dragon full helm ornament kit, Dragon full helm ornament kit (or), Dragon full helm ornament kit (sp), Dragon hunter crossbow, Dragon hunter lance, Dragon kiteshield ornament kit, Dragon legs/skirt ornament kit, Dragon platebody (or), Dragon platebody (sp), Dragon platebody ornament kit, Dragon platebody ornament kit (or), Dragon platebody ornament kit (sp), Dragon platelegs (or), Dragon platelegs (sp), Dragon platelegs/skirt ornament kit (or), Dragon platelegs/skirt ornament kit (sp), Dragon plateskirt (or), Dragon plateskirt (sp), Dragon scimitar ornament kit, Dragon sq shield (sp), Dragon sq shield ornament kit, Dragon sq shield ornament kit (or), Dragon sq shield ornament kit (sp), Dragon square shield (or), Eternal boots, Frozen whip mix, Fury ornament kit, Green dark bow paint, Lava staff upgrade kit, Light infinity colour kit, Masori body, Masori chaps, Masori mask, Necklace of anguish, Occult necklace, Occult ornament kit, Osmumten's fang, Pegasian boots, Primordial boots, Ring of suffering, Rune defender ornament kit, Rune scimitar ornament kit (Guthix), Rune scimitar ornament kit (Saradomin), Rune scimitar ornament kit (Zamorak), Saradomin godsword ornament kit, Steam staff upgrade kit, Tormented bracelet, Tormented ornament kit, Torture ornament kit, Toxic blowpipe (empty), Trailblazer reloaded blowpipe ornament kit, Tzhaar-ket-om ornament kit, Uncharged toxic trident, Volcanic whip mix, White dark bow paint, Yellow dark bow paint, Zamorak godsword ornament kit
  Onverhandelbaar: Amulet of torture (or) [PvP: killer gets Amulet of torture + killer gets Torture ornament kit]; Armadyl godsword (or) [PvP: killer gets Armadyl godsword + killer gets Armadyl godsword ornament kit]; Bandos godsword (or) [PvP: killer gets Bandos godsword + killer gets Bandos godsword ornament kit]; Berserker necklace (or) [PvP: killer gets Berserker necklace + killer gets Berserker necklace ornament kit]; Blazing blowpipe (empty) [PvP: killer gets Toxic blowpipe (empty) + killer gets Trailblazer reloaded blowpipe ornament kit]; Dark bow [PvP: killer gets Dark bow]; Dark infinity bottoms [PvP: killer gets Infinity bottoms + killer gets Dark infinity colour kit]; Dark infinity hat [PvP: killer gets Infinity hat + killer gets Dark infinity colour kit]; Dark infinity top [PvP: killer gets Infinity top + killer gets Dark infinity colour kit]; Dragon boots (g) [PvP: killer gets Dragon boots + killer gets Dragon boots ornament kit]; Dragon chainbody (g) [PvP: killer gets Dragon chainbody + killer gets Dragon chainbody ornament kit]; Dragon defender (t) [PvP: LOST (coins to killer)]; Dragon full helm (g) [PvP: killer gets Dragon full helm + killer gets Dragon full helm ornament kit]; Dragon kiteshield (g) [PvP: killer gets Dragon kiteshield + killer gets Dragon kiteshield ornament kit]; Dragon platebody (g) [PvP: killer gets Dragon platebody + killer gets Dragon platebody ornament kit]; Dragon platelegs (g) [PvP: killer gets Dragon platelegs + killer gets Dragon legs/skirt ornament kit]; Dragon plateskirt (g) [PvP: killer gets Dragon plateskirt + killer gets Dragon legs/skirt ornament kit]; Dragon scimitar (or) [PvP: killer gets Dragon scimitar + killer gets Dragon scimitar ornament kit]; Dragon sq shield (g) [PvP: killer gets Dragon sq shield + killer gets Dragon sq shield ornament kit]; Frozen abyssal whip [PvP: killer gets Abyssal whip]; Lava battlestaff [PvP: killer gets Lava battlestaff]; Light infinity bottoms [PvP: killer gets Infinity bottoms + killer gets Light infinity colour kit]; Light infinity hat [PvP: killer gets Infinity hat + killer gets Light infinity colour kit]; Light infinity top [PvP: killer gets Infinity top + killer gets Light infinity colour kit]; Mystic steam staff [PvP: killer gets Mystic steam staff]; Necklace of anguish (or) [PvP: killer gets Necklace of anguish + killer gets Anguish ornament kit]; Occult necklace (or) [PvP: killer gets Occult necklace + killer gets Occult ornament kit]; Rune defender (t) [PvP: killer gets Rune defender + killer gets Rune defender ornament kit]; Rune scimitar [PvP: killer gets Rune scimitar + killer gets Rune scimitar ornament kit (Guthix)]; Rune scimitar [PvP: killer gets Rune scimitar + killer gets Rune scimitar ornament kit (Saradomin)]; Rune scimitar [PvP: killer gets Rune scimitar + killer gets Rune scimitar ornament kit (Zamorak)]; Saradomin godsword (or) [PvP: killer gets Saradomin godsword + killer gets Saradomin godsword ornament kit]; Steam battlestaff [PvP: killer gets Steam battlestaff]; Tormented bracelet (or) [PvP: killer gets Tormented bracelet + killer gets Tormented ornament kit]; Twisted ancestral colour kit [PvP: LOST (coins to killer)]; Twisted ancestral hat [PvP: killer gets Ancestral hat + killer gets Twisted ancestral colour kit]; Twisted ancestral robe bottom [PvP: killer gets Ancestral robe bottom + killer gets Twisted ancestral colour kit]; Twisted ancestral robe top [PvP: killer gets Ancestral robe top + killer gets Twisted ancestral colour kit]; Tzhaar-ket-om (t) [PvP: killer gets Tzhaar-ket-om + killer gets Tzhaar-ket-om ornament kit]; Volcanic abyssal whip [PvP: killer gets Abyssal whip]; Zamorak godsword (or) [PvP: killer gets Zamorak godsword + killer gets Zamorak godsword ornament kit]
- Loyalty Shop: 203 items, 128 verhandelbaar (PvP -> killer), 75 onverhandelbaar.
  Verhandelbaar: Abyssal dagger, Adamant dragon mask, Afro, Ale of the gods, Ankou gloves, Ankou mask, Ankou socks, Ankou top, Ankou's leggings, Arceuus hood, Arceuus scarf, Archers' ring (i), Armadyl crossbow, Bat mask, Belle's folly, Berserker ring (i), Big pirate hat, Black demon mask, Black dragon mask, Blacksmith's helm, Blood moon chestplate, Blood moon helm, Blood moon tassets, Blue dragon mask, Blue h'ween mask, Blue moon chestplate, Blue moon helm, Blue moon spear, Blue moon tassets, Boots of darkness, Bowl wig, Briefcase, Brimstone ring, Bronze dragon mask, Bucket helm, Bucket helm (g), Bunny ears, Cat mask, Christmas cracker, Dark bow tie, Dark trousers, Dark tuxedo cuffs, Dark tuxedo jacket, Dark tuxedo shoes, Deerstalker, Disk of returning, Dragon cane, Dragon pickaxe upgrade kit, Dragon warhammer, Dual macuahuitl, Easter egg, Eclipse atlatl, Eclipse moon chestplate, Eclipse moon helm, Eclipse moon tassets, Eldritch orb, Fancy tiara, Fox mask, Frost dragon mask, Giant boot, Gloves of darkness, Greater demon mask, Green dragon mask, Green h'ween mask, Half moon spectacles, Harmonised orb, Hood of darkness, Hosidius hood, Hosidius scarf, Iron dragon mask, Jungle demon mask, Katana, Lava dragon mask, Left eye patch, Lesser demon mask, Light bow tie, Light trousers, Light tuxedo cuffs, Light tuxedo jacket, Light tuxedo shoes, Lovakengj hood, Lovakengj scarf, Mithril dragon mask, Monocle, Mummy's body, Mummy's feet, Mummy's hands, Mummy's head, Mummy's legs, Musketeer hat, Musketeer pants, Musketeer tabard, Obsidian cape (r), Old demon mask, Penguin mask, Piscarilius hood, Piscarilius scarf, Pumpkin, Red dragon mask, Red h'ween mask, Ring of 3rd Age, Ring of coins, Ring of nature, Robe bottom of darkness, Robe top of darkness, Royal crown, Royal gown bottom, Royal gown top, Rune dragon mask, Sagacious spectacles, Samurai boots, Samurai gloves, Samurai greaves, Samurai kasa, Samurai shirt, Santa hat, Seers' ring (i), Shayzien hood, Shayzien scarf, Sheep mask, Steel dragon mask, Top hat, Uri's hat, Volatile orb, Ward upgrade kit, Warrior ring (i), Wolf mask, Zalcano shard
  Onverhandelbaar: Accumulator max cape [PvP: KEPT]; Anger battleaxe [PvP: LOST (coins to killer)]; Anger mace [PvP: LOST (coins to killer)]; Anger spear [PvP: LOST (coins to killer)]; Anger sword [PvP: LOST (coins to killer)]; Assembler max cape [PvP: BROKEN in inventory]; Baby chameleon [PvP: LOST (coins to killer)]; Baby dragon [PvP: LOST (coins to killer)]; Baby gecko [PvP: LOST (coins to killer)]; Baby giant crab [PvP: LOST (coins to killer)]; Baby icefiend [PvP: LOST (coins to killer)]; Baby monkey [PvP: LOST (coins to killer)]; Baby penguin [PvP: LOST (coins to killer)]; Baby platypus [PvP: LOST (coins to killer)]; Baby raccoon [PvP: LOST (coins to killer)]; Baby squirrel [PvP: LOST (coins to killer)]; Bulldog puppy [PvP: LOST (coins to killer)]; Chocatrice cape [PvP: LOST (coins to killer)]; Dalmatian puppy [PvP: LOST (coins to killer)]; Dizana's max cape [PvP: BROKEN in inventory]; Dizana's quiver (uncharged) [PvP: BROKEN in inventory]; Dragon pickaxe (or) [PvP: LOST (coins to killer)]; Dragon pickaxe [PvP: LOST (coins to killer)]; Eldritch Nightmare staff [PvP: killer gets Nightmare staff + killer gets Eldritch orb]; Elidinis' ward (or) [PvP: killer gets Elidinis' ward + killer gets Arcane sigil + Menaphite ornament kit to grave]; Fire max cape [PvP: BROKEN in inventory]; Ghostly boots [PvP: LOST (coins to killer)]; Ghostly cloak [PvP: LOST (coins to killer)]; Ghostly gloves [PvP: LOST (coins to killer)]; Ghostly hood [PvP: LOST (coins to killer)]; Ghostly robe [PvP: LOST (coins to killer)]; Greyhound puppy [PvP: LOST (coins to killer)]; Grim reaper hood [PvP: LOST (coins to killer)]; Guthix halo [PvP: LOST (coins to killer)]; Harmonised Nightmare staff [PvP: killer gets Nightmare staff + killer gets Harmonised orb]; Hell-kitten [PvP: LOST (coins to killer)]; Herald cape [PvP: LOST (coins to killer)]; Imbued Guthix max cape [PvP: BROKEN in inventory]; Imbued Saradomin max cape [PvP: BROKEN in inventory]; Imbued Zamorak max cape [PvP: BROKEN in inventory]; Infernal cape [PvP: BROKEN in inventory]; Infernal max cape [PvP: BROKEN in inventory]; Jack lantern mask [PvP: LOST (coins to killer)]; Jester hat [PvP: LOST (coins to killer)]; Jester scarf [PvP: LOST (coins to killer)]; Labrador puppy [PvP: LOST (coins to killer)]; Lederhosen hat [PvP: LOST (coins to killer)]; Lederhosen shorts [PvP: LOST (coins to killer)]; Lederhosen top [PvP: LOST (coins to killer)]; Malediction ward [PvP: LOST (coins to killer)]; Masori assembler [PvP: BROKEN in inventory]; Masori assembler max cape [PvP: BROKEN in inventory]; Masori crafting kit [PvP: LOST (coins to killer)]; Menaphite ornament kit [PvP: LOST (coins to killer)]; Mime boots [PvP: LOST (coins to killer)]; Mime gloves [PvP: LOST (coins to killer)]; Mime legs [PvP: LOST (coins to killer)]; Mime mask [PvP: LOST (coins to killer)]; Mime top [PvP: LOST (coins to killer)]; Odium ward [PvP: LOST (coins to killer)]; Reindeer hat [PvP: LOST (coins to killer)]; Rubber chicken [PvP: LOST (coins to killer)]; Santa costume boots [PvP: LOST (coins to killer)]; Santa costume gloves [PvP: LOST (coins to killer)]; Santa costume legs [PvP: LOST (coins to killer)]; Santa costume top [PvP: LOST (coins to killer)]; Saradomin halo [PvP: LOST (coins to killer)]; Sheepdog puppy [PvP: LOST (coins to killer)]; Skeleton mask [PvP: LOST (coins to killer)]; Snowman top hat [PvP: LOST (coins to killer)]; Terrier puppy [PvP: LOST (coins to killer)]; Volatile Nightmare staff [PvP: killer gets Nightmare staff + killer gets Volatile orb]; Web cloak [PvP: LOST (coins to killer)]; Zamorak halo [PvP: LOST (coins to killer)]; Zombie mask [PvP: LOST (coins to killer)]
- Deadman Shop: 20 items, 0 verhandelbaar (PvP -> killer), 20 onverhandelbaar.
  Verhandelbaar: 
  Onverhandelbaar: Dagon'hai hat (or) [PvP: killer gets Dagon'hai hat + Dagon'hai robes ornament kit to grave]; Dagon'hai robe bottom (or) [PvP: killer gets Dagon'hai robe bottom + Dagon'hai robes ornament kit to grave]; Dagon'hai robe top (or) [PvP: killer gets Dagon'hai robe top + Dagon'hai robes ornament kit to grave]; Dagon'hai robes ornament kit [PvP: LOST (coins to killer)]; Elder chaos hood (or) [PvP: killer gets Elder chaos hood + Elder chaos robes ornament kit to grave]; Elder chaos robe (or) [PvP: killer gets Elder chaos robe + Elder chaos robes ornament kit to grave]; Elder chaos robes ornament kit [PvP: LOST (coins to killer)]; Elder chaos top (or) [PvP: killer gets Elder chaos top + Elder chaos robes ornament kit to grave]; Heavy ballista (or) [PvP: killer gets Heavy ballista + Heavy ballista ornament kit to grave]; Heavy ballista ornament kit [PvP: LOST (coins to killer)]; Imbued Guthix cape [PvP: BROKEN in inventory]; Imbued Saradomin cape [PvP: BROKEN in inventory]; Imbued Zamorak cape [PvP: BROKEN in inventory]; Ring of suffering (i) [PvP: killer gets Ring of suffering]; Ring of suffering (ri) [PvP: killer gets Ring of suffering]; Ring of the gods (i) [PvP: killer gets Ring of the gods]; Rune pouch [PvP: KEPT]; Thread of Elidinis [PvP: LOST (coins to killer)]; Treasonous ring (i) [PvP: killer gets Treasonous ring]; Tyrannical ring (i) [PvP: killer gets Tyrannical ring]

ORNAMENT KITS
- 2026-10-25 K01 deel 5 (eigenaar V2/V11/V12, LIVE): ALLE ornament kits (ook colour kits, paints, whip mixes, upgrade kits) en elk
  item MET kit erop zijn ONRUILBAAR (geen GE, geen ruil, geen stake). Kits komen alleen uit de 3 winkels (elite casket gaf er 8,
  die slots geven nu niets). Nightmare-orbs en Zalcano shard zijn geen kits (blijven zoals ze waren).
- Losse kit: PvP -> NOOIT kwijt, gaat naar JOUW graf (niets voor de killer). Monster-dood: naar je graf.
- Item MET kit erop: PvP -> killer krijgt het gewone item, de kit gaat naar JOUW graf (voor alle kits, zoals de OSRS Bounty Hunter-regel).
  Uitzondering: Dragon defender (t) en Rune defender (t) breken (OSRS), de kit gaat dan verloren (Rune defender (t) sinds 10-25 net
  als Dragon (t); daarvoor kreeg de killer de onruilbare Rune defender + kit).
- OPGELOST 22:4x (gebouwd, gaat mee in de volgende live-bundel): Dragon defender (t) breekt bij PvP in Dragon defender (broken), de kit gaat verloren (OSRS).
```

### C:\RSPS\team\dossiers\DMM-PAKKET.txt L36-L73
SHA256 598014a5f60bacfb890f5ded6424026912660b8c71cdbbfe0fc541572e2957b0
```text
=====================================================================================================================
Annihilation brackets by the player's own combat level: 3-60, 61-80, 81-95, 96+ (126; 138 here = 96+).
- ONE function (e.g. DeadmanBracket.of(player)) used by XP, drops, protection, Character Summary, drop viewer.
- NOT the attack range: who may attack whom stays the owner's +/-14 combat levels everywhere (AreaState.kt
  MAX_COMBAT_LEVEL_DIFFERENCE, owner 09-17). Both are shown in the Character Summary, never mixed up.
- Combat level never goes down here, so a bracket never goes down (Annihilation: "cannot move down").
- Bracket change (level-up over 60/80/95) -> RED chat line with the new rates, exactly once per change, not again on
  relog (owner 10-17). Text = PROVISIONAL, owner approves in N110 (e.g. "Your XP rate is now 15x combat / 10x skilling
  and your drop rate 3x.").

=====================================================================================================================
2. N92 XP RATES (owner: VERVANGEN) + N100 DROP RATES
=====================================================================================================================
XP: combat 3-60 10x | 61-80 15x | 81-95 15x | 96+ 20x ; skilling always 10x ; Prayer counts as combat (Annihilation).
- REPLACES the 2011 curve and the time-played bonus for normal XP: Player.addXp (game/src/.../entity/Player.kt ~1042)
  = xp * levelXpModifier (x1.0 lvl 1 -> x5.0 lvl 98) * bonusXpMultiplier (2.7..1.1, game.yml bonus-experience) *
  XpRates.normal (data/cfg/new_player.yml normal_rate 1.0). XpRedirect.handler hooks in there. Put the bracket rate in
  ONE place of this chain; not per skill plugin.
- Lamps and quest XP ALSO get the bracket rate (owner = OSRS). Today modifiers=false calls (8 choice quests, lamps, AFK
  area) skip all factors: decide per caller, list every caller in the dossier. The AFK area (new_player.yml afk_area)
  stays its own fixed numbers unless the owner says otherwise (ask in N110).
- Instances: OSRS Annihilation caps at 3x except Slayer - ask in N110 whether the owner wants that (not decided).
- THE one place: Player.kt levelXpModifier (~r1109, curve * XpRates.normal) + bonusXpMultiplier (~r1115); xpMultiplier()
  (~r1122) is what other code reads. modifiers=false callers (team\prep\xp_modifiers_false_callers.txt):
  FoundationRewards.kt:73 (quest XP -> owner: GETS the rate), quest_foundation.plugin.kts:316 and
  grandexchange_unlocks.plugin.kts:157 (lamps -> GET the rate), AfkArea.kt:120 (own fixed rate, ask N110),
  SummoningKit.kt:69 (wolf whistle), SummoningSuite (test only).
- Check: SummoningKit.kt uses levelCurve*normalXpRate to compute the kit - recompute with the new rate.
- Bonus-XP varp 1878 / XP counter 1801 must still add up.
DROPS (owner: ALL drops, chance x): 3-60 3x | 61-80 3x | 81-95 4x | 96+ 5x on the CHANCE of every non-guaranteed roll,
capped at 100 %; quantities unchanged; guaranteed drops (bones, ashes, "Always") stay 1x. (OSRS boosts uniques only -
owner chose all, on purpose.) Put it in the roll (plugins/content/drops/DropTableFactory) so the drop viewer shows the
real chance automatically. Pets (Pets.kt / SkillingPets.kt dropRate) and the breach/chest tables: same rule unless N110
says otherwise - ask.

=====================================================================================================================
3. N23 BEGINNER PROTECTION ("nooit werkend gezien")
=====================================================================================================================
```

### C:\RSPS\team\dossiers\DMM-PAKKET.txt L99-L120
SHA256 598014a5f60bacfb890f5ded6424026912660b8c71cdbbfe0fc541572e2957b0
```text
coordinates, the boss-cave spots, the broadcast colour (#a53fff) and "breach monsters can spawn a chest":
team\prep\chest_spots.txt.
Announcement: chat broadcast for everyone + location and time left in the Deadman tab (N102) + icon on the minimap /
world map (a loc with a mapfunction shows by itself - check the 667 loc defs first; DeadmanWorldMapOverlay.java exists).
LOOT (owner): all 17 Ancient Warriors pieces (Vesta, Statius, Morrigan incl. 100 javelins / 100 throwing axes, Zuriel),
Trinket of fairies, ALL OSRS pets (tier 1, 2 and 3: data/cfg/pets/pet_tiers.tsv), Voidwaker blade/gem/hilt, Heavy
ballista, Elder chaos hood/top/robe. Chances PROVISIONAL -> N110.
SHOPS: everything in the chest leaves EVERY shop: StoreCatalogue.kt Deadman section (~r310-341: PvP gear 1500, trinket
500, Voidwaker parts 2500, ballista 1000, Elder chaos 300) and StorePets (tier 1 Donator, 2 Deadman, 3 Loyalty).
StoreCatalogue.violations() + store tests must follow. Items players already own stay theirs.
OWN DROPS stay: a pet or item whose own boss/npc exists here keeps that drop (owner 10-17 "ja, ook eigen drop").
BREACH: every breach monster gets the chest loot list on top of its current BreachLoot.kt table (rare); chances
PROVISIONAL -> N110. The new Deadman Shop and Donator Shop stock (to replace what left) = proposal in N110.

=====================================================================================================================
5. N98 RIGOUR / AUGURY
=====================================================================================================================
Rigour = 74 Prayer + 70 Defence + Dexterous prayer scroll (read once); Augury = 77 Prayer + 70 Defence + Arcane prayer
scroll (read once); Rapid Renewal stays Prayer 65 only. Scroll = rare boss drop; which bosses + rate PROVISIONAL -> N110.
Legacy accounts that already use Rigour/Augury: ask in N110 (keep or lose).

=====================================================================================================================
```

### C:\RSPS\team\dossiers\DMM-PAKKET.txt L198-L213
SHA256 598014a5f60bacfb890f5ded6424026912660b8c71cdbbfe0fc541572e2957b0
```text
      Helper (Gertrude's Cat)), Animal Magnetism (Ernest the Chicken, Priest in Peril, The Restless Ghost), Ghosts Ahoy,
      Bone Voyage (The Dig Site), Client of Kourend (X Marks the Spot).
    Song of the Elves: Mourning's End Part II (Mourning's End Part I (Big Chompy Bird Hunting, Sheep Herder, Roving
      Elves (Waterfall Quest, Regicide (Underground Pass (Biohazard (Plague City)))))), Making History (Priest in Peril,
      The Restless Ghost), Druidic Ritual.   (the fetch was partial - verify the full tree)
    Monkey Madness II: Enlightened Journey, The Eyes of Glouphrie, Recipe for Disaster (Freeing King Awowogei) (Monkey
      Madness I (The Grand Tree, Tree Gnome Village)), Troll Stronghold (Death Plateau), Watchtower.
  Many tree quests do not exist in the 667 cache: the list is built by the server/cache from this data, not from enum
  190. Legacy accounts (QuestChoices.COHORT legacy) show everything completed. Test a NEW (choice) and a legacy account.
- GREEN tab (N104): DEADMAN TASKS (Easy/Medium/Hard/Elite like OSRS Combat Achievements, Deadman Points reward; the
  task list + DP amounts are a proposal in N110, built from content that exists: PK, chest, breach bosses, brackets,
  bosses, clues, skills) + a button to a COLLECTION LOG (pets, boss uniques, chest + breach items; reads the same drop
  sources as the rolls; per account, persisted; OSRS message "New item added to your collection log:" style - verify).
- SKULL tab (N102): WORLD INFO as a compact list, one line per subject, title left and value right: Loyalty / Donator /
  Deadman Points, Archaic Emblem, Next Breach, Next Deadman's Chest (where + time left). ONE Info button at the top
  opens ONE compact info window with subjects left (points, emblem, breach, chest, protection, skull, brackets,
```

### C:\RSPS\team\dossiers\DMM-PAKKET.txt L217-L231
SHA256 598014a5f60bacfb890f5ded6424026912660b8c71cdbbfe0fc541572e2957b0
```text

=====================================================================================================================
9. N110 VOORSTELLEN-RONDE (first after A2, NO code)
=====================================================================================================================
One VRAGEN.txt batch, multiple choice (A/B/C, team default marked), short Dutch, each with the numbers ready to
approve:
 a) chest: spot list (dangerous only) and loot chances; b) breach monsters: chest-loot chances on top of BreachLoot;
 c) new Deadman Shop stock + new Donator Shop stock (what replaces the pets/gear that left), with prices;
 d) Deadman Tasks: ~80 tasks (20 per tier) + DP per tier (shop prices today 250-2,500 DP) - start from
    team\prep\deadman_tasks_DRAFT.txt, drop what cannot work here; a) b) c) start from
    team\prep\chest_loot_and_shop_DRAFT.txt;
 e) N98: bosses + rate for the Dexterous / Arcane prayer scroll; legacy users of Rigour/Augury keep or lose;
 f) N92: the red bracket message text; instance XP cap 3x yes/no; AFK area keeps its own rate yes/no; pets x drop
    rate yes/no;
 g) design pictures (PNG in screens\team\N110\): the 4 quest-tab sub-tabs, the info window, the drop viewer (DROPS and
```

### C:\RSPS\team\dossiers\N104.txt L1-L11
SHA256 90d022de5de849334c11fb6a8e34a94d441cbc89fe7a959ad9a3ec0fd73b3d2c
```text
== VOLLEDIGE BUGLIJST-REGEL tot 2026-11-06 (de lijst heeft nu een korte regel) ==
N104 | HEROPEND | TEST S3 interfaces | [KETEN: UI] Achievement Diary-tab (quest-tab, nu 'Coming soon') wordt: (1) Deadman Tasks - taken in Easy/Medium/Hard/Elite (zoals OSRS Combat Achievements) met...

== DETAIL (uit de BUGLIJST, verplaatst 2026-10-30; nieuwste stand achteraan dit blok) ==
  N104
  KLAAR-TEST
  TEST S3 interfaces
  Achievement Diary-tab (quest-tab, nu 'Coming soon') wordt: (1) Deadman Tasks - taken in Easy/Medium/Hard/Elite (zoals OSRS Combat Achievements) met Deadman Points als beloning (PK, Deadman's chest, breach-bosses, brackets, bosses); (2) een knop in dezelfde tab naar een Collection Log (alle zeldzame items: pets, Ancient Warriors uit chest/breach, boss-uniques; wat je hebt en mist). Takenlijst + punten eerst aan eigenaar voorleggen (VRAGEN). Zelfde professionele stijl als de andere tabs, GEEN rommelige scroll-indeling
  owner-melding 10-17 middag (hoofdchat); HOORT BIJ: quest-tab-groep N102 N103 N109; 10-24 03:05 ONTWERP GOEDGEKEURD: tab 3 = Deadman Tasks (tiers + 'closest to done') + volledig Tasks-venster (screens\team\N110\GOEDGEKEURD\ 04-06); Collection Log = eigen venster net als OSRS, knop in tab 4; takenlijst/DP/tier-beloningen nog PROVISIONAL -> aan eigenaar voorleggen; spec in team\dossiers\NACHTOPDRACHT-DROPVIEWER-QUESTTAB-2026-10-24.txt; 10-24 05:28 LIVE: Collection Log-venster (net als OSRS, GOEDGEKEURD 04: Bosses/Deadman/Clues/Minigames/Other, groen bij compleet, Obtained rood/geel/groen, kills, items vol of half doorzichtig + aantal, tooltip + Inspect), registratie uit elke beloningsroute (drops, pets, breach, kist, guardian, Barrows, Fire cape, elite casket) met de KC; KC = de al bestaande teller per npc-id (oude kills tellen mee). Deadman Tasks tab 3 + venster (GOEDGEKEURD 05/06) LIVE met directe DP: lijst (51 taken), DP (5/10/15/25) en tier-bonus (25/50/75/100) zijn VOORLOPIG - eigenaar moet ze nog goedkeuren. TEST: tab 4 > Collection Log; tab 3 > klik Easy
  10:10 controle: 'Stay skulled'-taken telden AFK-minuten - aangepast (commit 73aa82373, test groen), NOG NIET LIVE (volgende herstart)
== EINDE DETAIL ==
```

### C:\RSPS\team\dossiers\N540.txt L1-L6
SHA256 5c922b212a46f1999bfce3ed72b479dda70d303f85193bf544ed65be5c868ec3
```text
== VOLLEDIGE BUGLIJST-REGEL tot 2026-11-06 (de lijst heeft nu een korte regel) ==
N540 | LATER (eigenaar beslist) | K05 S7 store | [KETEN: WINKEL+ITEM] Trinkets staan in de stores onder Pets en zijn slecht gegroepeerd; de Fairy trinket ontbreekt (hoort bij N423-trinkets) (eigenaar 11-02, bugs\bug-20261102-154327-anudd.txt) -> 11-05 11:0x VRAAG: (1) eigen 'Trinkets'-tab = 7e knop (venster heeft er 6, cachewijziging) of onder Gear; (2) Fairy trinket ging op besluit N101 (10-17) UIT de winkel (alleen kist/breach) - terug in de winkel? prijs?

ITEM: N540 | KLUS: K05 S7 store

MELDING eigenaar 2026-11-02 (bug-commando): Trinkets staan in de stores onder Pets en zijn slecht gegroepeerd; de Fairy trinket ontbreekt (hoort bij N423-trinkets)
```

N457: de oorspronkelijke losse dossierfile ontbreekt; de DMM2-taakkaart/het dossier onderscheidt expliciet native Ancient Warriors-corrupt en DMM corrupted weapons. RemovedItems.kt L21 en DeadmanGear.kt documenteren de oude verwijderregel. Dit is afgeleide bevestiging, geen teruggevonden letterlijke eigenaarschat. N457 niet uitbreiden naar nieuwe OSRS-ids.

## Bijlage D — volledige wiki-bonussen van alle corrupted vormen

Aanval en defence: stab/slash/crush/magic/ranged; daarna melee strength/ranged strength/magic damage/prayer. Expliciete wikiwaarden, geen algemene 80%-berekening.

|Item/toestand|A stab|slash|crush|magic|ranged|D stab|slash|crush|magic|ranged|Str|Rstr|Mdmg|Prayer|
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
|Corrupted voidwaker|+56|+64|-2|+4|0|0|+1|0|+2|0|+64|0|0|0|
|Corrupted dragon claws|33|46|-4|0|0|11|21|6|0|0|48|0|0|0|
|Corrupted armadyl godsword|0|+106|+64|+0|+0|0|0|0|0|0|+106|0|0|+7|
|Corrupted twisted bow|0|0|0|0|+56|0|0|0|0|0|0|+2|0|0|
|Corrupted Scythe of Vitur uncharged|+40|+60|+8|-6|0|-2|+5|+0|0|0|+40|0|0|0|
|Corrupted Scythe of Vitur charged|+56|+88|+24|-6|0|-2|+7|+8|0|0|+60|0|0|0|
|Corrupted tumeken's shadow uncharged|0|0|0|+35|0|0|0|0|+20|0|0|0|0|+1|
|Corrupted tumeken's shadow charged|0|0|0|+35|0|0|0|0|+20|0|0|0|0|+1|
|Corrupted dark bow|0|0|0|0|+95|0|0|0|0|0|0|0|0|0|
|Corrupted Volatile Nightmare staff|0|0|0|+16|0|0|0|0|+14|0|+0|0|+15|0|

Donors: GEEN donorimplementatie gebruikt in fase 1. Bestaande lokale special-helpers zijn alleen als mogelijke hergebruikroute onderzocht. Bronnen voor getallen blijven de wiki/cache; AV- en overige brongaten staan in V8/V12.
