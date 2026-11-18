# DMM2 fase 7 - hervatting en bewijs

Deze stand beschrijft uitsluitend het werk van GPT-A. Claude beheert cache, testserver, testclient en live.

## Afgerond voor deze hervatting

- **7.0:** synchronisatie bevestigd in `f6cb45131`; integratiecommits `a0152e13f`, `1faa9fe08` en `eafb1fdb3` aanwezig.
- **7.1:** smalle Wisp-extra-seqmodus, bronvergelijking en vier tooltests in `61b9042f9`. Claude accepteerde de twee checksum-bewaakte index-appends en paste zelf `tx-20261117-205101` toe. De bronmapping bevat nu 170 regels, inclusief seq 10241. De toewijzing van MAGIC_01 aan de echte DMM-thrall blijft een gedocumenteerd advies.
- **7.2:** vier OSRS-video's onderzocht. Standaardteleport is een visuele inferentie, exacte timing niet bewezen. Geen gegokt AV-contract. Wilderness- en nul-chargeweigering werken voor de AV-gate; vijf StarterPackInteractionsTests groen in de tussentijdse run. Commit `da1e784ec`.

## Deze hervatting vanaf 22:08

- Bestaande niet-gecommitte code en bronnen behouden; niets dubbel uitgevoerd of teruggezet. `tools/clue/` blijft volledig onaangeroerd.
- **7.3:** cache 2686 en ObjectID1.java bewijzen locs 49564/49565 en hun objectanimatie 10426. Batches in de bestaande Fx/Loc-importtools; ontbrekende seqmapping weigert de locimport. Een dependent PLAN gebruikt uitsluitend een expliciete voorlopige ledgerregel in een fysieke cachekopie. Spelerpose, plaatsing en serverduur blijven ONZEKER; `advancedAppearance` blijft null. De bewezen obstructiemelding is bereikbaar voor die gate.
- **7.4:** BloodMoney gebruikt nu de bestaande PvP-loottimers (D5). Extra tests controleren een owned munt op de slachtoffertegel, binnen/buiten Wilderness; alle drie echte Nigel-armour-IDs onder D2/D4; Wisp met/zonder mapping; en migratiecrash tijdens of direct na de atomische bestandswrite. Die crashproef gebruikt uitsluitend tijdelijke synthetische JSON, nooit spelersaves.
- **7.5:** normale installDist plus 23 expliciet genoemde testklassen gestart via rgradle, snapshot `1fa966803`. De wrapper meldt dat de normale voorgangersrun van 21:55 nog remote draait; de hervattingsrun staat achter dezelfde werkmaplock. De laatste crashproef/dependencyguard zijn van na snapshot 1fa966803 en vereisen een vervolgrun. Geen volledige of beperkte-scriptcompile-PASS claimen zonder de uiteindelijke taakuitvoer.

## Volgende stap

PLAN-resultaten en SHA256-controle op de verse fysieke cachekopieen afronden; testresultaten ophalen en eventuele codefouten herstellen. Daarna per punt committen, de aftekening in TESTCLIENT-CHECKLIST/PHASE7-EINDVERSLAG vastleggen en dossierregels met hashes toevoegen. Bekende ontbrekende-cachefouten op de werklaptop afzonderlijk melden; niet als geslaagde regressie verpakken.

Geen cache-apply, runtimecommando, client/server-start, live, VPS, spelersave, push of werk aan clues uitgevoerd.

## Hervatting 18 november

- Voorgangersdiff volledig behouden. De oude run1fa966803 is geen definitief bewijs.
- Actuele normale rgradle-run f7268db31, gestart02:17, bevat alle laatste productie-/testwijzigingen: installDist,3coreklassen+23plugin-klassen, expliciete classincludes, geen dmm2FocusedScripts. Core15/15 groen in uitvoer; plugin- en volledige scriptcontrole lopen nog.
- De 80 bestanden in de voorbereide advanced-PLAN-root zijn opnieuw SHA256-gelijk aan before.json; ieder bestand heeft1link en is geen alias van file-server/cache. Journal leeg. Definitieve dependentPLAN volgt na deze ene eigen build.
- ids.tsv170regels bytegelijk aan integratiebranch, SHA256dcd2930d403ceb1d06fcd4d14d6aa60aec6ecf90005283ffe20e455074f461bc. Claude heeft Wisp al in beeld bewezen; geen herimport/herhaling gevraagd.
- Extra Undead-broncontrole: transcript ontbreekt, alle9784client-scriptgroepen gelezen zonderfouten, geen letterlijke cooldowntekst. Advies stilleweigering behouden; response/hashuitvoer in phase7-sources.
