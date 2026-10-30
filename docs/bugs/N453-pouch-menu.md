# N453 - cache-opdracht voor Claude (2026-10-30)

Status: oorzaak onderzocht; CACHE NIET GEWIJZIGD. Claude moet de onderstaande wijziging in beide caches uitvoeren.

## Oorzaak en bewijs

De read-only decoder `C:/RSPS/tools/cache667/items.py` vindt bij alle 78 serverpouches
`inventoryMenu = [null, null, null, "Summon", null]`. Summon staat dus op index 3 / optie-slot 4
(opcode 38). De cache bevat in totaal 146 pouch-items met deze Summon-plaatsing; alleen
questpouch 12526 heeft ook `Destroy` op index 4. De 144 noted versies hebben geen Summon.
De originele bytes van deze 290 items zijn op 2026-10-30 uit de servercache gelezen en
byte voor byte vergeleken met de file-servercache: gelijk. De snapshot staat in
`game/plugins/src/test/resources/summoning/N453-pouch-options.tsv` (volledige bytes, ID, naam,
serverondersteuning, note-ID). Geen cache is beschreven.

Het dossier toont `Use / Summon / Drop / Examine` voor Spirit wolf pouch 12047. In combinatie
met het cache-slot en de naamgebonden serverregistratie onderbouwt dat de menuvolgorde als
oorzaak: de ingebouwde Use staat voor de lage-prioriteit Summon. `familiar.plugin.kts` bindt
alle `SummoningPouchData`-items op de naam `summon`; `KotlinPlugin.on_item_option` zoekt de
actuele cache-index en registreert index + 1. `IfButton1Handler` verwerkt optie 1 en 4 apart.
Er is geen pouch-remap naar Use in deze serverroute. De nieuwe slot-1-binding wordt bij het
volgende laden van de plugins vanzelf geregistreerd. Een serverpatch kan het door de client
gebouwde menu niet rechtzetten. Clientcode is niet aangepast; geen testclient gestart.

## Exacte cachewijziging

Voor ELK van de onderstaande 146 ongenoteerde pouch-IDs, cache-index **19**, archive
`id >> 8`, file `id & 255`:

- inventory index **0** / optie-slot **1** / opcode **35**: null -> **"Summon"**.
- inventory index **3** / optie-slot **4** / opcode **38**: **"Summon"** -> null (opcode verwijderen).
- Index 1, 2 en 4 blijven gelijk. Bij **12526** blijft opcode 39 **"Destroy"**.
- Geen notes aanpassen. Alle note-IDs en originele note-bytes staan expliciet in de TSV;
  hun template/link, stapelgedrag, modellen, kosten en alle overige bytes blijven gelijk.
- Ook geen wijziging voor **11014 Beacon ring** of **15354 Calling beads**: die hebben wel
  Summon, maar zijn geen pouches. Empty pouch 12155, scrolls en greyed UI-items evenmin aanpassen.
- Via Claude's bestaande CacheTransaction-route, op beide caches samen; deze kaart autoriseert
  ChatGPT-B niet om dat uit te voeren.

Alle te wijzigen IDs:

```
12007, 12009, 12011, 12013, 12015, 12017, 12019, 12021, 12023, 12025, 12027, 12029, 12031, 12033
12035, 12037, 12039, 12041, 12043, 12045, 12047, 12049, 12051, 12053, 12055, 12057, 12059, 12061
12063, 12065, 12067, 12069, 12071, 12073, 12075, 12077, 12079, 12081, 12083, 12085, 12087, 12089
12091, 12093, 12095, 12097, 12099, 12101, 12103, 12105, 12107, 12123, 12526, 12531, 12776, 12778
12780, 12782, 12784, 12786, 12788, 12790, 12792, 12794, 12796, 12798, 12800, 12802, 12804, 12806
12808, 12810, 12812, 12814, 12816, 12818, 12820, 12822, 14422, 14424, 14426, 14428, 14430, 14623
17935, 17936, 17937, 17938, 17939, 17940, 17941, 17942, 17943, 17944, 17945, 17946, 17947, 17948
17949, 17950, 17951, 17952, 17953, 17954, 17955, 17956, 17957, 17958, 17959, 17960, 17961, 17962
17963, 17964, 17965, 17966, 17967, 17968, 17969, 17970, 17971, 17972, 17973, 17974, 17975, 17976
17977, 17978, 17979, 17980, 17981, 17982, 17983, 17984, 17985, 17986, 17987, 17988, 17989, 17990
17991, 17992, 17993, 17994, 19622, 21444
```

Hiervan zijn 78 pouches uitvoerbaar via de huidige `SummoningPouchData`; de overige 68 zijn
bestaande quest/minigame/overige cachevarianten. De menuwijziging voegt daarvoor geen ontbrekende
familiar-implementatie toe. Het veld `server_supported` maakt dit onderscheid expliciet;
alle 146 menu's krijgen dezelfde optievolgorde.

## Gerichte test en BREEK

`SummoningPouchOptionPlanTests` leest uitsluitend de vastgelegde snapshot en berekent de
voorgestelde wijziging in geheugen met `ItemDefCodec.cloneWithOverrides`. De test controleert
alle 146 slots, de volledige 78-ID serverlijst, het behoud van alle andere bytes, Destroy
op 12526 en alle 144 notes zonder Summon. Dit bewijst het plan; het bewijst niet dat Claude
de cachewijziging al heeft toegepast.

Claude in de testclient na de cachetransactie:

1. Combat (12047 Spirit wolf / 12790 Steel titan), BoB (12093 Pack yak / 12031 War tortoise),
   skilling (12021 Beaver): hover/links-klik = Summon, een pouch wordt gebruikt, familiar verschijnt;
   Use blijft via rechtsklik beschikbaar. Ook laag level, geen summoning points en bestaande follower proberen.
2. Noted versies (12048/12791/12094/12032/12022): geen Summon, geen verbruik/spawn.
3. Pouch in bank-inventory (763:0): links-klik blijft Deposit volgens bankhoeveelheid;
   bankvenster sluiten en daarna links-klik Summon. `Bank.bankInventoryDepositAmount` en
   `bank.plugin.kts` houden de bankroute apart van inventory 679:0.
4. Voor toepassen alle pre-images tegen de TSV controleren; na toepassen alle 146 itemdefs
   en 144 notes uit BEIDE caches opnieuw lezen en de in-memory verwachtingen vergelijken.

## Bronnen

- Eigen beide rev-667 caches: read-only JS5 index 19 + bovengenoemde snapshot.
- Novite: `src/main/java/novite/rs/networking/codec/handlers/InventoryOptionsHandler.java`
  (`handleItemOption6` -> `Summoning.spawnFamiliar`) en
  `game/player/actions/summoning/Summoning.java`: bestaand pouch-summonpad; geen bruikbare
  donorpatch gevonden voor deze gewenste menuwijziging.
- Darkan: `world-server-dev/src/main/java/com/rs/game/content/skills/summoning/Summoning.java`,
  `handleSummonOps`: alle Pouch-IDs, optie op naam `Summon`.
- RS Wiki [Summoning pouches](https://runescape.wiki/w/Summoning_pouches): familiar oproepen
  door op een pouch in de inventory te klikken. Gewenste links-klik expliciet opgelegd in de taakkaart.

Open: alleen uitvoering en clientcontrole door Claude. Niet als opgelost afvinken voor die stappen.
