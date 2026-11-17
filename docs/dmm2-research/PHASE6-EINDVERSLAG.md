# DMM2 fase 6 ? serveroverdracht, 17 november 2026

Safe Deposit Box en Nigel Reward Store hebben servervensters en transactiebindingen. Deze hervatting
behoudt Claude's eerdere fixes, herstelt de raw packet-opcodemapping en de worn-slotbeveiliging,
en bouwt de View/Help/filter-controls volgens cache2686. De uitvoering is lokaal gecommit op
`gpt-a/dmm-pakket-2`. Dit is geen grafische of live-aftekening; Claude beheert cache, client en release.
Volgens de laatste eigenaarsinstructie pauzeert GPT-A na deze overdracht. Geen fase7 gestart.

De SafeBox gebruikt dezelfde tien NO_STACK-slots bij Wizard en Seer, met skull/afstand/sessionchecks,
stale-X/reopenbeveiliging, bulk inventory/worn en chargebehoud. Worn gebruikt de bestaande
`canUnequipSlot`-guard; inventory-opslag vraagt geen unrelated equipmentcontainer meer op.
Nigel gebruikt V3: bestaande Deadman Points voor de zeven rewards; source currencyrow1206 zegt
Deadman Points. Blood money blijft de armour-route. Buy/refund, notes, consumed unlocks, maxsaldo,
volle inventory, relog/death/replay en I10 blijven op de bestaande services aangesloten.

De venstercallbacks gebruiken `Dmm2Interactions.option(player)` -> `SlayerRewardsUi.opOf` op het
werkelijke packet-opcodeveld. IF_BUTTON1/opcode61 is Select/Deposit1; de derde plek in packets.yml
wordt nooit als Buy5 gelezen. Header14 is in de portretstand verborgen. View gebruikt de broncaption
`Compact`/sprite4735 en daarna `Expand`/4737; Help12 is alleen in de kleinekaartstand zichtbaar.
Filter13 is de lege enum4988: grijze tekst, grijze pijl en geen fictieve filterkeuzes. Help wist selectie;
de eigen Info-Close keert terug naar het raster. De viewwissel behoudt de bestaande transacties.

Belangrijkste bestanden:

- `Dmm2SafeDepositUi.kt`, `Dmm2RewardUi.kt`, `Dmm2Interactions.kt`, `SafeDepositBox.kt`, `dmm2.plugin.kts`.
- `Dmm2Boot.kt`/`Dmm2Ids.kt`: echte optionele script-/sprite-assets uit `cfg/dmm2/interfaces.tsv`,
  conflictcontrole en behoud van de151 gepubliceerde basismappings.
- Bestaande `OsrsInterfacePortTool.kt` + `Dmm2WindowPort.kt`:211widgets voor819, broncontrols,
  brongeometrie en twee gealloceerde native667CS2-adapters; geen nieuwe importtool of vaste lokale ids.
- Eigen Window/UiPlan/Presentation-tests en reproduceerbaar `RUN-PHASE6-TESTS.ps1`,
  `phase6-regression.gradle`, `validate_phase6.py`, `PREFLIGHT-PHASE6.sh` en checklist14b.

Definitieve validatie: **73/73 tests,8 eigen klassen,0 failures/errors/skips**, normale core/plugins/test-
compilatie en negen gerichte bindingscripts groen via rgradle. Uitgevoerde snapshot
`bf887b23175b9c19eb78543aebd279ad64fc96aa`, remote-run53515,11min. Alle acht verse XML's zijn
bewaard in `phase6-output/regression-final`; `PHASE6-VALIDATION.json` bevat timestamps, hashes,
bronbestanden en de gecontroleerde snapshotidentiteit. Eerste volledige snapshot542fc66c2 had
64/73 groen en negen strict-mockfailures op de onnodige equipment-getter; die zijn hersteld zonder
fixtures te verzwakken. Eerste rapporten staan apart in `regression-first`.

Native PLAN: bestaande importer en zeven herhaalde UiPlan-contractproeven exit0. Dit zijn dezelfde
zeven unitgevallen, geen zeven extra unieke tests. Twee fysieke immutable cachekopieen:78/78files
bytegelijk voor/na, journal leeg;417mutaties/834CREATE-uitkomsten op beide targets, geen apply.
PLAN-aantallen230=41,232=31,819=211,806=83. De baseline alloceerde interfaces1185..1188,
scripts5402/5403 en sprites8819..8867. **Dit zijn uitsluitend PLAN-ids op oude kopieen.** De bestaande
runtime-roots1204/1203/1205/1206 en151basismappings blijven ongewijzigd; nooit deze PLAN-ids overnemen.
Exacte argv, raw uitvoer en verificatie staan in `phase6-ui-output`.

Bronnen: cache OpenRS22686, source scripts7142/43/44/47,7152..7156,5771,6388,1972,98,715 en
DBrow929/table39/currencyrow1206/enum4988. De veertien aanvullende scripts en enum zijn gepind in
`phase6-sources/controls.json`; overige source-hashes en het referentiebeeld staan in phase5/6-sources.
Wiki-pins: Reward Store15394269, Wizard15364116, Seer15390113, Wizard-transcript14868949.
De nieuwe native helpers zijn expliciete serververtalingen van bron7155/7153, geen byte-exacte
OSRS-scriptimports. Hun native667primitives/footer zijn binair getoetst. Geen nieuwe donor gebruikt.

Claude moet na de merge de bestaande `OsrsInterfacePortTool dmm2-ui --reuse` op zijn actuele cache
plannen en gebundeld toepassen. Verwacht819=211 en twee werkelijk toegewezen scriptregels
7155(layout)/7153(op-caption/graphic), plus de vijf source-sprites4735/4737/8386/8387/788.
De nieuwe **echte** interface-map moet naar de testdata als `cfg/dmm2/interfaces.tsv`; Boot leest
alleen die werkelijke assets. De193-componentenimport van15:45 bevat dit nog niet.
Daarna checklist14b: beide View-standen, Help, Info-Close, bron-dropdown, alle kaart-/naam-/itemklikken,
1/5/10/50 buy/refund, charges, geen geld/volle inventory/overflow, X/5/10/All bij SafeBox, skull,
relog/death/I10, hoofd-X/Esc en Fixed/Resizable/Stretched naast OSRS. Hover/pressed, fonts, clipping en
lagen vereisen echte-clientcontrole. Claude's eerdere193-widgetronde bewees kopen/refund en gewone
SafeBoxtransfers; de nieuwe controls zijn nog niet grafisch afgetekend.

Open voor de vervolg-/clientronde: letterlijke OSRS-refusaltekst voor onvoldoende DP en niet-skull-
SafeBoxweigeringen is niet bewezen; die routes weigeren zonder mutatie en verversen de echte state.
Er is geen verzonnen OSRS-dialoog toegevoegd. De overige DMM2-rest en live/A9 vallen onder Claude's
fase7/testclientwerk. Geen nieuwe eigenaarsvraag of blokkerend advies bij deze serveroverdracht.

GPT-A deed geen cache-apply, server/client-bediening, live/VPS, push/merge, saves/game.yml/data-ge-
wijziging of cluewerk. Alleen bestaande `tools/clue/` blijft ongevolgd en onaangeroerd. De remote
recovery en prioriteitsdubbeling zijn in het dossier vastgelegd; rgradle en de expliciete class-whitelist
voorkomen een nieuwe lege-forkronde. Doctor/lint-scripts ontbreken in deze worktree. Alle eigen
build/test/PLAN-opdrachten zijn afgerond; geen eigen monitor nodig. Idle daemons en andermans
processen blijven ongemoeid. Na de laatste bewijscommit: pauze zoals opgedragen.
