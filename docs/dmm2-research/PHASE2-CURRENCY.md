# DMM2 deel 2 — Blood money, Nigel en afscheid van Breach Points

Status: serverwijzigingen als WIP voor Claude's review; migratie staat standaard UIT. Geen spelersaves gelezen of rechtstreeks gewijzigd. Geen cache, Items.kt/items.yml, wereldspawns, logs, data/ge, game.yml, testserver/client of live aangeraakt.

## Koers: gekozen advies200 oude breachpunten per1 DP

In het dossier stond bij hervatten geen Claude-koers. De nieuwe kaart geeft opdracht zelf voor te rekenen en achter een releasevlag te bouwen. Vergelijking met dezelfde utility-beloning: OSRS **Annihilation** Rune pouch note100.000 archaic points, onze bestaande Rune pouch500DP (`StoreCatalogue.kt`). Bron: Bounty Hunter Shop (Deadman Mode), rev15345703 (2026-09-16). Dus100.000/500=200. De permanente paginahelft noemt1.200.000: dat zou2400geven, maar is niet ons gekozen Annihilation-model. De aanvankelijke2400-notitie is in het dossier expliciet gecorrigeerd vóór enige activering.

Dit is een lokaal migratieadvies op basis van koopkracht, geen bestaande OSRS-wisselkoers. Afronden naar boven per account voorkomt waardeverlies voor een saldo onder200; maximaal minder dan1DP extra.75.000→375DP;1.500.000→7500DP;Int.MAX→10.737.419DP. Een equivalent oude tier5-emblemwaarde kan zo2,5ruggen of5/8van een12kweaponscroll kopen; een volle75kdamagecap geeft375DP. Dit maakt de invloed van oude emblems expliciet, zonder er nieuwe blood-money-verdiensten van te maken.

`BreachRetirement.migrate` draait uitsluitend bij `-Drsps.dmm2.release=true`, via login. Standaardfalse. Exact één player-save-snapshot bevat het nieuwe bestaandeDP-saldo, oorspronkelijke balance/earned-auditvelden, verwijderde oude active keys en de migratiemarkering. Een negatieve historische balance wordt volgens N111 als Int.MAX gewaardeerd; alle sommen Long. Bij overflow blijven **alle** oude keys, DP en markering ongewijzigd; na DP uitgeven en opnieuw inloggen kan het opnieuw. Opnieuw uitvoeren na succes verandert niets.

## Nieuwe valuta en Nigel

Blood money blijft fysiek onruilbaar item13307, geen DP-attribute en geen nieuwe breach-wallet. De bestaande **ene** `ValidPkKill`-uitkomst in `DeadmanEmblem.onPvpDeath` betaalt1stuk als de verdict VALID is. Alle bestaande same-address/machine, staff, fed kill, repeat-victim, low-risk, daily-cap en safe-minigameweigeringen blijven. Er komt geen tweede evaluate/record. Grant is een killer-owned gronditem op de victimtegel, zoals de wiki-dropbron; als de mapping ontbreekt blijft een persistente claim over voor de volgende login. Geen blood money uit breach-damage, emblems of Deadman Tasks.

Bronnen: Blood money rev15380671 (2026-10-23); Deadman Mode rev15375850 (2026-10-19,1perkill); Deadman armour rev15226533; Transcript:Nigel rev15394041 (2026-11-04). Drie armour-unlocks kosten elk1bloodmoney. Unlock + item + afboeking zijn ondeelbaar; bestaande unlock consumeert geen tweede money; verloren unlocked chest/legs/cape zijn gratis terug te halen. Reward Store blijft het al bestaandeDP-saldo gebruiken. Gewone Deadman Points/PK-points/Deadman-emblemregels blijven bestaan.

Blood money heeft geen bijzondere bewezen afwijkende doodmatrix in de wiki. Er is geen “drop al je money naar de killer”-regel verzonnen: het fysieke onruilbare item volgt de bestaande eigenaar-D2/D3/D4-route, net als andere onruilbare items. Claude controleert met echte definities of de resulterende Perdu/coinweergave bij deze valuta gewenst is; de specifieke afwijking kan pas als apart expliciet besluit/bron worden ingebouwd. Armour-free-reclaim wordt beheerd door de eigen unlockflags, niet door het bezit van een starter-armourstuk.

## Oude routes bij release

|Oude route|Voor releasevlag|Na releasevlag|
|---|---|---|
|Breach-damagepunten / top100 payout|Bestaand|Geen nieuwe Breach Points; loot voor eerste16, chest-extra, burden, Ruinous en taken blijven.|
|Breach Trader-shop/dialogue/rewards|Bestaand|Niet geregistreerd. Spawn/icon weghalen uit world_edits/cache is Claude.|
|Tier5 Archaic emblem→breachpunten|Bestaand|Niet geregistreerd. Er is geen automatische blood-money- of nieuwe DP-inruil verzonnen.|
|breachpoints-command|Bestaand|Niet geregistreerd; commands.yml-helpvermelding moet Claude bij de release laten vervallen.|
|givepoints breach/maxpoints|Bestaand|Kan geen oude balance meer aanmaken of aanvullen; gewone valuta blijven werken.|
|BreachSim / BreachContribution|Historische helper kan punten meten|De gedeelde awardfunctie weigert; ranking/loot-ledger en simulatie blijven bruikbaar.|
|WildernessHotspot / DeadmanChestWorld / GE-unlocks|Bij onderzoek geen actieve BreachPoints-gebruiker buiten eigen breach-code gevonden|Geen andere valuta omgezet, geen onterechte verwijdering van hotspot/guardian/taken.|

Archaic emblems zijn apart van de oude balance. Gekozen aanvullend advies bij hervatten: stop NIEUWE tier5-drops uit breach en chest-guardian zodra de releasevlag aan staat, omdat hun enige BP-inruil vervalt. Bestaande exemplaren blijven behouden; geen purge, stilzwijgende itemmigratie, blood-money-uitbetaling of nieuwe DP-bron. De nieuwe release sluit de oude trader/exchange af. QuestTabInfo vertelt dan geen oude punten-/emblembeloning meer. Claude moet de gegarandeerde legacy-emblemregel in DropSources/Drop Viewer en de wereldspawn/helpvermelding bij dezelfde release uitzetten; die bestanden zijn zijn eigendom. DeadmanEmblem (het eigen tier-systeem) is een andere itemfamilie en blijft functioneel.

## Oude24artikelen: advies bestemming, geen nieuwe shop uitgevonden

|Artikelen|Bestaande route / gekozen advies|
|---|---|
|Blighted manta ray, anglerfish, karambwan, super restore(4)|Bestaande Wilderness boss/revenant/zombie-pirate-droptabellen; ruilbare exemplaren via bestaandeGE.|
|Blighted entangle sack, teleport spell sack, vengeance sack, ancient ice sack|Bestaande breach supplies/revenants en de bestaandeGE voor ruilbare definities; geen blood-money-prijs.|
|Revenant ether|Bestaande revenant drop en bracelet dismantle;GE.|
|Magic shortbow scroll|BestaandeGE-route; geen nieuwe winkelprijs.|
|Ornate maul handle, Trouver parchment|BestaandeGE voor ruilbare definities; eventuele boss-/slayerbronnen uit de bestaande tabellen behouden.|
|Ancient sceptre|Archaeologist's bestaande DT2-winkel blijft (60.015staff+75.000icon); de oude breachpuntenkoop vervalt.|
|Craw's bow(u), Thammaron's sceptre(u), Viggora's chainmace(u)|Bestaande revenant drops, charge/upgrade-mechanics enGE.|
|Fangs of Venenatis, Skull of Vet'ion, Claws of Callisto|Bestaande eigen Wilderness-bossdrops enGE; geen tweede shop.|
|Armadyl/Bandos/Seren/Ancient/Brassica halo (local23858..23862)|Breach-verkoop vervalt; held copies behouden. Er is in deze worktree geen andere bewezen specifieke drop/winkel voor deze vijf aangetroffen. Advies is voorlopig geen nieuwe bron verzinnen; als de cache ze via de bestaandeGE toelaat blijft die bestaande route bestaan.|

Deze tabel is expliciet de gekozen overgang, geen claim dat elk artikel al een natuurlijke nieuwe drop heeft gekregen. Geen bestaande items gepurged en geen balansprijzen voor een alternatieve shop verzonnen.

## Testclient voor Claude

Test releaseflag UIT, dan aan op een geïsoleerde testaccount; nooit live-spelersaves bewerken. Migration0/1/199/200/201/75k/1,5M/Int.MAX en DP-overflow; herhaald relog/restart; gemigreerde keys versus auditvelden; oud shopvenster/packet/redeem/commands geblokkeerd. Echte geldigePK geeft1physicalmoney, elke afgewezen verdict0; volle inventory geeft owned ground-drop, mappingloze claim wordt later éénmaal betaald. Drie1moneyarmour-unlocks + gratis reclaim na verlies; herhaald klikken/volle inventory/laatste money. Reward Store uitgaven en sellback gebruiken alleenDP; gewone Deadman Tasks en emblems blijven.

Gerichte eigen testklassen: `Dmm2CurrencyTests` (8 scenario's), plus de bestaande eigen transactietests en nieuwe NPC-sessietests. Definitieve verse XML/commit staan onderaan het dossier. Geen volledige suite of runtime/live-PASS door GPT-A.

Claude bevestigde de koers200:1naar-boven ook expliciet om23:4x in het dossier; dit is bij hervatten geen onbevestigd tarief meer. Nieuwe test: bij release weigeren zowel award als de admin-balanshelper iedere legacy-mutatie (ook afboeken), zonder balance/earned/audit te wijzigen.
