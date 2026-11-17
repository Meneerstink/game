# DMM2 fase 5 — aanvullende testserverbevindingen, 17 november 2026

Dit is server-/bron-/PLAN-bewijs voor Claude, geen testclient- of live-PASS. De echte 143 mappings uit de eerste cacheronde blijven ongewijzigd. Geen nieuwe PLAN-allocatie is als productie-id opgeslagen.

Provenancecorrectie: de eerste fase5-commit normaliseerde ids.tsv via core.autocrlf naar LF in de Git-blob, terwijl de werkfile wel bytegelijk aan Claude's CRLF-handoff was. Een lokale data/cfg/dmm2/.gitattributes bewaart nu de originele bytes. De mapping zelf verandert niet; zowel Git-blob als werkfile moeten SHA256 e7efbb117ec161980fe20e20d232da8e8c92ecd8c34fdd71d71795866084c7d4 hebben.

## Fire Altar: bewijs en uitvoering

[Amulet of fire rev15396852](https://oldschool.runescape.wiki/w/Special:PermanentLink/15396852), 2026-11-08T15:54:52Z, bewijst 21 boostable Runecraft, 125 XP, drie ticks en de drie ingrediënten. [Fire ruby rev15396853](https://oldschool.runescape.wiki/w/Special:PermanentLink/15396853) bevestigt het gebruik op het Fire Altar. De responses staan in `phase5-sources/wiki.json`.

De primaire [Creating amulet of fire-animatie rev15329402](https://oldschool.runescape.wiki/w/Special:PermanentLink/15329402) toont de rode amulet-enchant, niet de runecrafting-animatie. De gedownloade webp en twaalf inspectieframes staan in `phase5-sources/creating-fire*`; SHA256 webp `84b21eee09bf16ac78f0943ec57142d968a3fbee1179e74027c4aab3ffdb2aa1`. Identificatie: RuneLite gameval HUMAN_ENCHANTAMULETLVL3 seq721 en ENCHANT_AMULET_LVL3 spot116. Dit is een visuele identificatie, geen opgenomen packettrace. De bestaande 667 tegenhangers zijn beschikbaar; nieuwe echte mappings krijgen voorrang na Claude's eventuele AV-import.

`FireAmulet.check` valideert voor de presentatie zonder iets te verbruiken. `craft` herhaalt de volledige transactie na de drie ticks: afgenomen levelboost, verwijderde runen of een tweede oude klik kunnen niets dupliceren of gedeeltelijk afboeken. De plugin speelt de geïdentificeerde enchant-animatie/gfx voor de wachttijd.

**VRAAG/gekozen advies volgens 15-11 14:4x:** wiki, het beschikbare transcript en de volledige broncache leveren geen letterlijke level-/ingrediëntenweigering. `Transcript:Fire ruby` bestaat niet. Daarom toont de weigering uitsluitend de bewezen receptgegevens (`Runecraft: 21. Fire ruby: 1. Amulet of magic: 1. Fire rune: 100.`); dit is lokale gegevensopmaak en uitdrukkelijk GEEN beweerde OSRS-fouttekst. De exacte OSRS-tekst blijft een observatiepunt. Het geluid gebruikt de bestaande ruby-amulet enchant145, bewezen naam `enchant_ruby_amulet` in [List of sound IDs rev15402195](https://oldschool.runescape.wiki/w/Special:PermanentLink/15402195), 2026-11-13T19:51:20Z (`sound-list.json`). De toepassing op deze visueel identieke handeling is gekozen advies, geen audio-opname van het Fire Altar. Hoogte92 volgt onze bestaande amulet-enchantpresentatie en moet visueel worden vergeleken.

## Avarice Value: het gemelde botpad

[Trinket of avarice rev15286577](https://oldschool.runescape.wiki/w/Special:PermanentLink/15286577), 2026-08-03T18:19:52Z, bewijst de instelbare minimumwaarde. Bron33044 én de fysieke bestaande local23823 hebben exact inventoryopties **1 Note/Unnote, 2 Value**, daarna twee lege posities. De botmelding voor **optie3** is daarom geen ontbrekende Value-binding. `dmm2.plugin.kts` bindt al op de echte cachenaam `Value`, die PluginRepository naar optie2 omzet. Geen fictieve derde optie of dubbele handler toegevoegd.

`phase5-sources/cache-probe.json` bevat de read-only decode en SHA256 van de eigen lokale definitie. De ingestelde drempel is persistent; negatieve input wordt geweigerd. Beide bestaande droproutes gebruiken `DeadmanTrinkets.valueOfDrop`. De gekozen prijsbasis blijft de eerder vastgelegde lokale GE-stackwaarde (Long/clamp), omdat de wiki geen unit/stack-/prijstypecontract geeft. OSRS-meting blijft open; deze ronde doet geen nieuwe prijsclaim.

## Alle wapens: normale presentatie, geluid en specials

De fysieke item-id, bonuses en charges blijven behouden. `presentationBase` kiest alleen de bestaande animatie-/audio-definitie; `SpecialAttacks.perform` gebruikt de al bestaande specialalias. De basiswapens worden niet in equipment geplaatst.

|Familie|Normale route|Special en bewijsgrens|
|---|---|---|
|Corrupted voidwaker|Bestaande Voidwaker-look; Slash Sword-categorie voor een stille swing.|Bestaande Voidwaker-special, 50%; [corrupted rev15191591](https://oldschool.runescape.wiki/w/Special:PermanentLink/15191591).|
|Corrupted dragon claws|Basis14484, inclusief dezelfde claw-cue per stijl (slash2500/stab2501). Oorzaak van silent swing opgelost: de category lookup zag eerst alleen local24960.|Bestaande claws-special, 50%; [rev15191592](https://oldschool.runescape.wiki/w/Special:PermanentLink/15191592).|
|Corrupted Armadyl godsword|Basis11694; bestaande godsword-animatie en audio/framegeluiden.|Bestaande AGS-special, 50%; [rev15191593](https://oldschool.runescape.wiki/w/Special:PermanentLink/15191593).|
|Corrupted twisted bow|Basis Twisted bow voor presentatie; bestaande ranged shot-/framegeluidsroute.|Geen special; [rev15191594](https://oldschool.runescape.wiki/w/Special:PermanentLink/15191594).|
|Corrupted scythe, beide states|Benoemde bron8056 SCYTHE_OF_VITUR_ATTACK is opgenomen in de nieuwe PLAN-batch. Pas een echte nieuwe mapping schakelt die specifieke animatie in.|Geen special; [rev15350112](https://oldschool.runescape.wiki/w/Special:PermanentLink/15350112). Vitur-specifieke serveraudio ontbreekt in seq8056. GEKOZEN advies: bestaande OSRS Scythe-categorie: slash2524/stab2525/crush2522, namen bewezen door bovengenoemde sound-list. Exacte Vitur-audio blijft te vergelijken; geen gemeten Vitur-claim.|
|Corrupted shadow, beide states|Dezelfde bestaande PoweredStaffCombatStrategy-shadowcast als de gewone Shadow; bash gebruikt de staff-categorie. Charged/empty en gewone/corrupted kosten blijven apart.|Geen special; [rev15191600](https://oldschool.runescape.wiki/w/Special:PermanentLink/15191600). Shadowcast/frameaudio door Claude naast de gewone Shadow nakijken; deze ronde wijzigt die gedeelde powered-cast niet.|
|Corrupted dark bow|Basis11235 voor presentatie, bestaande dubbele ranged route en frame-/shotgeluid.|Bestaande Dark-bow-special, 55%; [rev15192587](https://oldschool.runescape.wiki/w/Special:PermanentLink/15192587).|
|Corrupted Volatile Nightmare staff|Bestaande Nightmare-look; bash staff-cue; gewone autocast blijft dezelfde echte spellgeluiden gebruiken.|Bestaande Volatile-special, 55%, met de eerder gemaakte corrupted formule; [rev15303344](https://oldschool.runescape.wiki/w/Special:PermanentLink/15303344). Het bestaande shared specialgeluid178 is al lokaal aangepast advies, geen nieuw gemeten DMM2-audiofeit.|
|Thunder khopesh|Wiki [rev15262511](https://oldschool.runescape.wiki/w/Special:PermanentLink/15262511): Slash Sword; klassieke slash/stab-looks en bijbehorende bestaande categoriecue.|60%, eigen ThunderKhopesh.register; blijft gebonden aan een volledig geïnstalleerd appearance-contract. Geen special zichtbaar zolang dat contract ontbreekt. Exacte initial-hit/passive-/PvM-timing blijft PHASE3-SOURCES en Claude's AV-inbouw.|
|Deadman weapon skins|Echte skins.tsv-base state voor looks/audio; charges en fysieke cosmeticavorm blijven intact.|Dezelfde bestaande base-special; geen nieuwe speciale formule.|

De cue-resolver stuurt geen extra fallback wanneer de sequence al framegeluid heeft. Unitproeven vergelijken de **echte local24960** met gewone14484 voor alle melee-stijlen en testen de vijf corrupted specialaliases met onvoldoende/voldoende energie, eenmaal uitvoeren en fysieke chargebehoud. Deze dispatcherproef vervangt geen visuele specialtest met de echte wapenhandlers.

Claude's ijking blijft leidend: ook gewone claws specialden niet via bot + `::spec`. Dat pad bewijst geen DMM2-specialfout. Echte specials via de attack-tab aanklikken; energie vooraf controleren; nieuw levend doelwit gebruiken.

## UI230-font en de juiste Reward Store-interface

Font645 is **QUILL_OBLIQUE_LARGE** in RuneLite gameval SpriteID. Beide cache-namehashes wijzen naar `quill_oblique_large`, ook in onze cache naar645; dit is dezelfde semantische fontresolutie als de bestaande p11/p12/b12-mapping. Ruwe OSRS/667-spritebytes verschillen (HD/export), dus geen pixelgelijke glyphclaim. Namen, ids en hashes staan in `cache-probe.json`; de importer weigert een ontbrekende named font in plaats van een ander lettertype te kiezen.

De juiste winkel is **OMNISHOP_MAIN819 + OMNISHOP_SIDE806**, niet BR_REWARD_SHOP645. Primair cachebewijs:

- DBrow929 (table39) bevat de titel en welkomsttekst voor de Deadman Reward Store.
- Zijn valuta verwijst naar DBrow1206: Deadman Point/Deadman Points.
- Zeven gekoppelde productrows1355,3609,3610,3611,9572,9573,9574 bevatten exact de zeven bronitems29625,29628,29622,29619,33012,33015,33018 en prijzen12000/6000/9000/3000/12000/15000/10000.
- Script7142 leest zijn row-argument met DB_GETFIELD159744 (= table39, column0), vult 819 en bouwt de frame via228. Scripts7143/7144 tekenen de producten;7147 geeft precies de helptekst uit de wiki-afbeelding. De winkelafbeelding, herkomst en SHA-bewijs staan in `reward-store*`, `shop-reward-rows.json`, `shop-config.json`, `omnishop-scripts.json`. UI645 noemt aantoonbaar Justine/LMS.

`phase5_supplement_sources.py` decodeert alle bytes van de relevante IF3-componenten, inclusief listeners/triggers, met een eind-offsetcontrole. De schema-adapter behoudt maten, modi, kleuren, tekst, opties en events. RuneLite-loader/gameval URLs en volledige bronhashes staan in `runelite-provenance.json`; geen nieuwe externe dependency ingebouwd.

### Exact uitgevoerde PLANs

Reproduceerbaar: `bash docs/dmm2-research/PREFLIGHT-UI-AV.sh` (geen argumenten). Centrale Gradle-slot, `--priority low`; twee fysieke phase4-cachekopieën, geen echte server-/fileservercache als target. Exacte Java-argv, classpath, output en exitcodes staan onder `phase5-ui-output/`.

|Bestaande tool/selectie|Uitkomst op de oude, onveranderde kopie|
|---|---|
|OsrsInterfacePortTool `dmm2-ui`|230→1185 (17),232→1186 (3),819→1187 (55),806→1188 (14); zes spritegroepen8819..8824;95 mutations,190 CREATE over twee targets.|
|OsrsFxImportTool `dmm2-weapon-av`|seq721→16221,8056→16222,8057→16223;49 mutations,2 NO_OP +96 CREATE.|
|OsrsFxImportTool `dmm2-fire-av`|spot116→3220, model3103→12089, afhankelijke seq679→16221;15 mutations,30 CREATE.|

**Deze ids zijn uitsluitend PLAN-uitkomsten.** De onafhankelijke plannen delen hun vrije allocatieruimte; de twee seq16221-uitkomsten zijn dus geen productieconflict. Claude laat een echte sequentiële transactie de actuele ids kiezen en publiceert daarna de echte mappings. Niets uit deze tabel handmatig in ids.tsv kopiëren.

Twee eigen importerproeven groen; alle vier procesuitkomsten0. `verification.json`:78/78 bestanden bytegelijk voor/na, journal leeg. Geen apply. Oude dmm2-batches blijven gelijk; de aanvullende benoemde selectors veranderen hun allocatievolgorde niet.

### Wat Claude na zijn cachehandeling moet aansluiten

Een statische import alleen maakt nog geen functionele interface. De importer meldt elke niet uitgevoerde source callback expliciet:

- SafeBox:703→711 stoneframe,1151→1155→1156 tien withdrawslots,1158→1160→1161 deposit-inventorygrid; opties/bulkbuttons9/11 en drag/clickvalidatie naar `Dmm2Interactions`/`SafeDepositBox`, inclusief skull/afstand/close. Source1156 heeft Withdraw-1/5/10/X/All;1161 Deposit-1/5/10/X/All. Services blijven atomaire serverautoriteit.
- Reward:7142 init/frame,7143/7144 productrooster,7147 help/selectie,7245 koopknoppen; side-panel7246/7248/7254/7255/7256 voor sellback. Echte zeven NigelRewardStore-producten/DP, geen LMS-/League-stock. Gebruik de bronrow929 en de wiki-afbeelding voor layout/tekst.
- Pas de eigen `cfg/dmm2/interfaces.tsv` toe bij hergebruik (`--reuse`); zo behoudt reeds geïmporteerde232 zijn echte1203. Nieuwe interface-/sprite-/seq-/spot-mappings uit de werkelijke tooluitvoer publiceren. Bestaande ids.tsv blijft in dit GPT-WIP143 regels.
- Ready8057/attack8056 en enchant721/116: exacte echte mappings leveren; verticale offset8/stretches zijn gerapporteerde toolgrenzen, dus 667-clientvergelijking vereist. Geen ongecontroleerde inline vervanging van gedeelde native seqs.

Aanvullende `IDS-NODIG-VOOR-CLAUDE`: interface230/819/806; seq721/8056/8057 en de afhankelijke679; spot116/model3103 en zes source sprites170/828/829/830/1041/1042. De tools plannen alle echte dependencies. De oudere ancient34607/Nigel-icoon/thrall-/Home-/POH-/Chroniclecontracten blijven de al beschreven afzonderlijke Claude-integratiepunten.

## Testclient en open bronpunten

Volg de aanvulling onderaan `TESTCLIENT-CHECKLIST.md`. Exacte OSRS-refusaltekst Fire Altar, audio van de specifieke fire-craft/Vitur, khopesh-timing, Avarice prijssoort, starter11slots, Salarin en de bestaande AV-/UI-/POH-punten zijn niet als bewezen afgemeld. Waar een keuze nodig was, staat het gekozen advies hierboven expliciet naast het bewijs. Geen donorcode gebruikt; bestaande lokale combat/enchant/importhelpers hergebruikt.
