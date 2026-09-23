# RETIRED HISTORICAL HANDOFF — 2026-09-01

This file is historical evidence only. Do not use its old Q-/RCV-order or status
claims as active instructions. Read `C:\RSPS\RSPS_MASTERPLAN_ACTUEEL.md`,
`C:\RSPS\RSPS_CURRENT_SPRINT.json`, `C:\RSPS\RSPS_FULL_AUDIT_REPORT.md` and
`C:\RSPS\HANDOFF_CURRENT.md` for the current task.

## Doel
Maak deze revision-667 RSPS speelbaar als RuneScape 2011. Geen decoratieve of halve fixes. Een punt is pas DONE wanneer de echte code-route is gevonden, de root cause is opgelost, gerichte tests/build slagen én de eigenaar het live kan controleren. Als data of client-ID’s niet bewezen kunnen worden: rapporteer BLOCKED met bewijs; verzin niets.

## Werkruimte
Repository: C:\RSPS\game\game
Branch: feat/playable-core

Lees eerst:
- SUMMONING_AUDIT.md
- C:\RSPS\OWNER_TASK_STATUS.md
- git status en alle huidige diffs

Claude-bestanden zijn mogelijk onafgemaakt en dirty. Overschrijf of reset ze niet. Behoud untracked bestanden. Werk in kleine onafhankelijke batches, commit iedere geslaagde batch en noteer commit/tests/resultaat. Geen force/reset/clean/delete.

Bekende Codex-commits:
- 7d9a7e58: familiar-death lifecycle en BoB cargo cleanup.
- 1b44b9ac: regressietest voor familiar-death/BoB cleanup.
- Famine is toegevoegd aan bestaande dirty Summoning-bestanden; de gerichte Famine-test/build is geslaagd. Splits Claude’s diff zorgvuldig vóór commit.

## Wat al echt gedaan/gecontroleerd is
- 78 familiar-pouch mappings, summon/pouch-consumptie/XP/points, follow/collision-aware placement, call/teleport recovery, renew, dismiss, expiry en logout/login persistence.
- Familiar combat-basis: queue, damage-attributie, XP/PvP-controles; 72/73 combat rows. Albino Rat animation-data blijft blocker.
- BoB server-side containers en lifecycle-basis; familiar death cleanup is getest.
- Special-energy pool: max 60, +15 per 30 online seconden, persistent accumulator.
- Scrolldata voor 67 scrolls is cross-checked en met tests vastgezet.
- Special dispatcher gebruikt bewezen trigger 747:25; binding wordt via actief familiar-NPC opgelost.
- Meerdere special moves en resource rollback zijn geïmplementeerd en getest. Famine consumeert één target-food, één scroll en correcte energy; afwijzing verliest niets.
- Home heeft gedeeltelijke safe-zone/collision/bank/perimeter/gates, shops, altar, restoration pool, board en glider/carpet-netwerk; boot/self-check is gedaan.
- Beginner protection, Breach-gating, bank-boundary architectuur, equipment-screen routing en PvP-safety fixes bestaan gedeeltelijk.
- Client-side Wilderness-level, graphics en live-interfacegedrag zijn niet bewezen.

## Summoning volledig afmaken
Controleer alle 78 familiars en alle scrolls, niet alleen Steel Titan.

Verplicht:
1. Summon: pouch exact één keer verwijderen; juiste level/points/XP/duration; bereikbare collision-veilige spawn naast speler.
2. Lifecycle: follow stap voor stap met collision en correcte grootte; Call familiar werkt; multicombat-call herordent aanval; Renew werkt; Dismiss; expiry/logout/login/death/familiar-death consistent; revision-667-regels expliciet documenteren.
3. Combat: alle combat definitions/transforms/attack animations/speed/max hit/projectile/graphic/death; alle aanvallende familiars helpen; defensive-only blijft defensive-only; Steel Titan echte combat-route.
4. Scrolls: iedere scroll echte trigger en juiste targetsoort; juiste costs/XP/cooldown/voorwaarden; preflight -> effect -> commit; mislukking geeft geen resourceverlies; geen verkeerde gedeelde target-route; geen gegokte IDs. Werk resterende gaten af of leg bewezen blocker vast: Venom Shot, Generate Compost, Call to Arms, Multichop, Regrowth, Goad, Ambush, Petrifying Gaze, Rise from the Ashes, Immense Heat, Swallow Whole, Famine, Vampire Touch en overige onvolledige routes. Herhaal Famine-test.
5. BoB: Pack Yak, War Tortoise, Spirit Terrorbird en alle BoBs moeten opslaan/ophalen/bekijken, volle/ongeldige input weigeren, geen duplicatie/verlies bij Take BoB/call/renew/dismiss/death/expiry. Interface 671 openen met slot-grid en bruikbare knoppen; anders exact blocker.
6. HUD: ontbrekend familiar/summon-icoon herstellen; Call/renew/dismiss/special-knoppen en synchronisatie bij alle state changes.

## Curses/prayer volledig afmaken
- Ancient Curses moet werkelijk als prayerbook kunnen worden geactiveerd via werkende route, liefst home én testcommando.
- Scheid unlock, ritual en activation; “already performed” mag niet voorkomen als boek niet actief is.
- Controleer alle curses: activation/drain, protection, stat drains/boosts, Soul Split, Turmoil, Anguish, Torment, Leech-varianten, overheads, PvP/PvM, incoming-hit hooks, disable/death.
- Controleer prayer restore/drain en alle interfaces/book tabs.
- Client toont HP/prayer in tientallen: 99, niet 990. Alleen presentatie aanpassen; serverdamage/XP/formules blijven gelijk.
- Tests voor book-switch, unlock, drain, restore, activation en display conversion. Geen guessed child/varbit IDs.

## Home/spawn Design 2
- Verwijder onmiddellijk de blauwe tent/het blauwe object waarin de speler na home-teleport vastzit. Arrival tile moet beloopbaar zijn.
- Home is niet klaar zolang alles op elkaar staat of alleen één bankchest zichtbaar is.
- Duidelijke veilige bank/home-kern, zichtbare muur/perimeter, vier bruikbare uitgangen naar Wilderness/BH.
- Onderzoek zuidwaartse verschuiving voor ruimte; verschuif alleen met collision/object/route-bewijs.
- Nodig: bank, shops, altar, restoration pool, transport hub (spirit tree/glider/fairy ring/carpet/balloon/minecart/charter waar data bestaat), activity board, summoning specialist, minigame/PvM entrances, starter area.
- Drie-tegels-brede routes, geen overlap, NPC’s bereikbaar, bank safe, uitgangen worden Wilderness.
- Live testen: home teleport, death respawn, lopen uit arrival, iedere faciliteit en iedere uitgang.

## Globale NPC/combat/drops audit
De fouten gelden voor alle NPCs.
- Volledige census: elke attackable NPC echte combat definition; juiste level/HP, speed, animations, styles, aggression, size, death/respawn. Geen fallback voor normale content.
- NPC met HP nul moet sterven, verdwijnen en correct respawnen.
- Alle drops revision-667 controleren: IDs, aantallen, chances, rare rolls, noted/ground/inventory.
- Talk-to/Trade/Attack/Enter/Open met één normale klik; geen stale target of tweede klik.
- Dialogues moeten na eerste regel doorlopen.
- Geen dode menu-options; iedere optie echte handler.
- Test representatieve NPCs én census-resultaat; rapporteer IDs die echt geblokkeerd blijven.

## Combat/wapens/specs
Audit alle wapens, minimaal Dragon claws, AGS, BGS, SGS, ZGS, Korasi, Hand cannon, Granite maul, Dragon 2h, halberd, mace, spear, Ancient mace, Barrelchest anchor, Dark bow, Magic shortbow, Chaotic rapier en overige specials.
Per wapen: required weapon type/slot, energy cost, activation route, proven animation/projectile/graphic/sound, hit-count, formula, cooldown, charges/check/recharge, dismantle, equip tijdens combat. Geen serverwaarde aanpassen voor alleen presentatie.

## Wereldinteractie/teleports
Controleer alle deuren, poorten, tunnels, ladders, dungeon entrances en Wilderness rifts in Gielinor.
Specifiek: alle Wilderness rifts, Edge -> Grand Exchange tunnel, Warrior’s Guild-deur, Digsite pendant, Ectophial, teleport crystal, Ring of kinship en overige teleport-items. Corporal Beast op echte cave-locatie. Elke route moet optie, movement, destination, plane, collision, repeat use en failure message correct afhandelen.

## Client/UI/graphics
- Wilderness-level plus danger/multi icons bij login, teleport en grensovergang in fixed/resizable/fullscreen.
- Hitsplats old-style/OSRS-formaat en leesbaar; interne 200 visueel als 20. HP/prayer orbs 99, niet 990.
- Scroll-zoom alleen boven world viewport; interfaces/chat/bank/debugconsole niet.
- Fresh/corrupt profile fixed + lowest; geldige voorkeuren behouden.
- FPS/memory-overlay standaard uit met expliciete toggle.
- Mono/stereo/volume stopt/herbouwt streams direct zonder overlap.
- Test drie display modes en trage laptop.

## Harde uitvoeringsregels
1. Start met status/diffs en een checklist met aantallen.
2. Prioriteit: speler niet vast in home -> Summoning -> Curses -> NPC/combat census -> entrances/teleports -> client UI/graphics.
3. Zoek bestaande route en brondata; hergebruik engine-API’s.
4. Schrijf gerichte regressietest vóór of samen met iedere fix.
5. Run gerichte tests, compile/build en boot-verificatie waar mogelijk.
6. Commit alleen gecontroleerde batches; update status met DONE/PARTIAL/BLOCKED, bewijs en eigenaar-live-test.
7. Werk automatisch door zolang usage/tijd beschikbaar is.
8. Eindrapport moet eerlijk zijn; zeg nooit alles klaar op basis van alleen een build.

Aanvulling wereldinteractie: Barbarian Outpost-deur werkt momenteel niet. Controleer deze expliciet samen met alle deuren, poorten, tunnels, ladders, rifts en dungeon entrances in heel Gielinor; geen whitelist van alleen gemelde locaties.

Aanvulling NPC-dialogue: de meeste NPCs zeggen alleen Hallo en hebben geen uitgebreide vervolgdialogue. Controleer alle Talk-to routes, dialogue states, continuation/next-button verwerking, quest- en non-questdialogues en zorg dat iedere bestaande NPC-content volledig doorloopt; markeer ontbrekende brondata apart.

Aanvulling Summoning: Renew bij de Summoning obelisk geeft momenteel ‘Nothing interesting happens’ en herstelt points niet. Controleer objectoptie, locatie/obelisks, requirements, points restoration, familiar lifetime en client/server feedback.

Aanvulling skills/interactie: fishing spots in het algemeen geven momenteel ‘Nothing interesting happens’. Audit alle fishing-spot NPC/object IDs, opties, tool/bait requirements, animations, catch tables, XP, depletion/respawn en één-klik interactie.

## Algemene skills, quests en interface-audit
Controleer alle skills end-to-end, inclusief Slayer, Agility, Prayer/Curses, Summoning, Construction, Dungeoneering, Fishing en alle overige skills: object/NPC interactie, requirements, XP, levels, timers, interfaces, save/load en edge cases. Controleer iedere geïmplementeerde quest van start tot finish, quest stages/varbits, quest points, Vampire Slayer en persistence na relog. Controleer equipment-bonusscherm expliciet: equip én unequip vanuit de gedragen-items-grid moeten met één klik werken; inventory-grid en worn-grid mogen geen handler delen als hun containeractie verschilt. Controleer ook alle item-use, charge/check, dismantle, bank, trade en shop interfaces.

Aanvulling Firemaking/object-interactie: Add logs op een unlit beacon werkt momenteel niet. Controleer alle beacon-objecten en vergelijkbare item-on-object routes globaal: juiste optie, itemvereiste, skill/level, consumed logs, animatie, XP, state transition naar lit/beacon, timers, extinguish/depletion en hergebruik.

Aanvulling client: inzoomen en uitzoomen met het muiswiel werkt momenteel helemaal niet. Herstel world-viewport zoom in fixed, resizable en fullscreen met veilige min/max; chat, bank, tabs, interfaces en debugconsole mogen niet reageren op dezelfde scroll.

Nieuwe live-regressie met screenshot: Wilderness-level 7 is zichtbaar, maar de getoonde Wilderness-rift geeft herhaaldelijk ‘Nothing interesting happens’ en laat Enter niet uitvoeren. Controleer alle Wilderness-rifts afzonderlijk op juiste object-ID, optie/handler, reachability, collision, bestemming, plane, requirements, repeat-use en failure logging; test daarna alle dungeon entrances globaal.

## Verified owner test commands
Use one colon pair exactly as shown by the client command parser:
- ::maxtest — all skills 99, combat recalculated, HP/prayer/run/spec restored, coins and baseline gear.
- ::testgear — baseline Rune melee, crossbow/bolts and Mystic kit.
- ::restore — restore HP, prayer, run and lowered stats.
- ::infpray — toggle infinite prayer.
- ::infhp — toggle infinite HP.
- ::infrun — toggle infinite run energy.
- ::master — set all skill base levels to 99.
- ::setlvl attack 99 / ::setlvl strength 99 / ::setlvl defence 99 / ::setlvl prayer 99 / ::setlvl summoning 99 / ::setlvl slayer 99.
- ::item 12790 1 — Steel Titan pouch.
- ::item 12093 1 — Pack Yak pouch.
- ::item 12031 1 — War Tortoise pouch.
- ::item 12007 1 — Spirit Terrorbird pouch.
- ::item 12825 1 — Steel of Legends scroll.
- ::item 12435 1 — Winter Storage scroll.
- ::item 12439 1 — Testudo scroll.
- ::item 12441 1 — Tireless Run scroll.
- ::item 12830 1 — Famine scroll.
- ::item 12422 1 — Herbcall scroll.
- ::item 12436 1 — Ophidian Incubation scroll.
- ::item 12829 1 — Immense Heat scroll.
- ::item 12834 1 — Explode scroll.
- ::npc 7343 — Steel Titan NPC test spawn; use ::clearspawns afterward.
- ::npc 50 — generic combat NPC test; replace with a census ID when testing.
- ::npc_inventory — regenerate the complete NPC census CSV.
- ::clearspawns — remove only registered test NPC/object spawns.
- ::home — return to home.
- ::mypos — print exact tile/region/object at current position.
- ::objsearch <name> / ::objsnear <radius> — inspect nearby/cache object IDs.
- ::itemsearch <name> — find exact item IDs.
- ::damage 20 — apply test damage.
- ::hit 20 — apply a magic test hit.
- ::curse unlock — unlock curses.
- ::curse book ancient — switch to Ancient Curses.
- ::curse book normal — switch back to normal prayers.
- ::curse turmoil — toggle Turmoil.
- ::curse soul split / ::curse deflect melee — toggle named curses when supported.
- ::bank — open bank.
- ::shop — open Edgeville test shop.
- ::tele x z height — teleport to exact coordinates; use ::mypos before/after route tests.
- ::noclip — diagnostic only; toggle off immediately after collision diagnosis.

There is no direct summon command in the source: summon by using the pouch item, then test familiar actions through the actual familiar interface/object options. Do not claim a pouch or scroll works merely because it can be spawned.

Nieuwe shop-regressie: veel shops laten vrijwel ieder inventory-item verkopen. Dat hoort niet. Audit alle shops op echte stock- en sell-policy: alleen toegestane itemcategorieën en IDs, correcte shopprijzen en buyback, geen willekeurige wapens, gear of quest-items, correcte noted/unnoted- en stackable-afhandeling, voorraadlimieten, coins en weigermelding. Test iedere shopfamilie afzonderlijk.

Nieuwe deur-regressie met screenshot: zowel Open Door als Pick-lock Door verschijnen, maar voeren geen interactie uit. Dit geldt vermoedelijk globaal. Audit alle deurobjecten en opties op handler-binding, objecttype, collision, route/movement, lockpick requirements, animatie, success/failure state, destination en één-klik repeat-use. Controleer ook Walk here/Examine zodat alleen echte opties worden getoond.

Nieuwe combat/object-regressie met screenshot: Slash Web werkt niet terwijl een godsword is equipped. Controleer weapon type én attack style (godswords zijn melee en slash), item-on-object dispatch, required tool checks en alle vergelijkbare webs/doors/obstacles. Test ieder relevant wapen met slash/stab/crush en voorkom dat weapon_type -1 of een verkeerde style alle melee-wapens blokkeert.

## HOOFDREGEL SCOPE
Iedere bug die de eigenaar meldt is een globale regressie totdat census/audit het tegendeel bewijst. Fix nooit alleen het screenshot-object, één NPC, één wapen, één shop, één deur of één rift. Zoek de gedeelde root cause en controleer daarna alle vergelijkbare gevallen in de volledige cache/codebase. Een onderdeel mag pas DONE heten nadat de globale audit en representatieve plus census-tests slagen.

Nieuwe globale dungeon-regressie met screenshot: Climb-down Staircase doet niets. Controleer ALLE staircases, ladders, trapdoors en dungeon-level transitions in heel Gielinor: Climb-up/down, floor/plane, bestemming, collision, object rotation/type, requirements, repeat-use en failure messages. Dit geldt voor alle dungeons en alle vergelijkbare multi-level entrances, niet alleen dit object.
Uitbreiding globale object-audit: controleer alle gates in heel Gielinor. Open Gate moet de juiste state-change, animatie, collision, movement, bestemming en repeat-use uitvoeren; controleer ook dubbele gates, fence-gates, wilderness-gates en dungeon-gates.
Nieuwe globale Agility-regressie met screenshot: Squ eeze-through Crevice voert geen route uit. Controleer ALLE Agility shortcuts en obstacles in heel Gielinor: juiste object-ID/optie, Agility-level en itemrequirements, XP, animatie, movement/path, collision, bestemming/plane, failure message, repeat-use en alle shortcutvarianten.
Nieuwe globale transport-regressie met screenshot: Mine cart heeft Search Mine cart maar geen werkende interactie. Controleer ALLE minecarts en railtransport in heel Gielinor: Search/Board/Travel opties, destination, requirements, fare/items, animation, movement, plane, collision, repeat-use en failure feedback.
Nieuwe globale entrance-regressie met screenshot: Open Magic Door voert geen route uit. Scope is ALLE entrances in heel Gielinor: gewone en magic doors, gates, rifts, ladders, staircases, trapdoors, tunnels, minecarts, dungeon-, cave-, boss- en minigame-entrances. Iedere optie moet handler, requirements, state-change, collision, movement, destination/plane, repeat-use en correcte failure feedback hebben.

# COMPLETE BUG INVENTORY — alles uit de eigenaarstests

## Scope-regel
Elke onderstaande melding is globaal. Fix de gedeelde root cause en controleer daarna alle vergelijkbare gevallen in de cache en codebase. Een fix voor alleen het screenshot-object of één NPC telt niet.

## Home, spawn en safe-zone
Home/spawn is nog niet volledig volgens Design 2. Controleer en herstel de volledige layout: arrival tile, bank, veilige kern, muur/perimeter, vier uitgangen, shops, altar, restoration pool, transporthub, summoning specialist, activity board, minigame/PvM-entrances en starter area. Verwijder het blauwe tent/blauwe object waarin de speler na home teleport vast komt te zitten. De speler moet direct kunnen lopen. Controleer of het centrum zuidwaarts moet verschuiven en of 25x25 te klein is; vergroot alleen met collision/object/route-bewijs. Los overlap en op elkaar gestapelde faciliteiten op. Home mag niet slechts één bankchest tonen. Test home teleport, death respawn, bankveiligheid, alle routes en iedere uitgang.

## NPC’s, combat, HP, death, dialogue en drops
De eigenaar meldt dat de meeste NPC’s niet attackable zijn, combat definitions missen, HP te laag is, NPC’s bij nul HP blijven staan, animaties fout zijn en drops niet kloppen. Maak een volledige cache-wide NPC-census. Iedere attackable NPC moet correcte combat level, lifepoints/HP, attack speed, attack style, animation, projectile/graphic, size, aggression, death animation, removal, respawn en drops hebben. General Graardor is expliciet gemeld met verkeerde attack movement en te veel magic logs; controleer alle bosses en alle overige NPC’s op dezelfde categorieën.

Talk-to, Trade, Attack, Bank, Enter, Open en Examine moeten met één normale klik werken. Verwijder dode menu-opties en herstel alle handlers. Dialogues mogen niet na alleen “Hello” stoppen; controleer alle vervolgdialogues, next/continue states, questdialogues en non-questdialogues. Controleer stale targets en double-click/interactietiming globaal.

## Summoning
Maak alle 78 familiars en alle scrolls volledig werkend. Controleer summon/pouch-consumptie/XP/points/duration, collision-veilige spawn, follow, Call familiar, Renew via obelisk, Dismiss, expiry, logout/login, death en familiar-death. Steel Titan moet echt aanvallen. Pack Yak, War Tortoise en Spirit Terrorbird moeten echte BoB-storage hebben: store, retrieve, inspect, Take BoB, volle inventory, invalid input, death, expiry en geen duplicatie/verlies. Herstel het ontbrekende Summoning/familiar-icoon en interface 671 met slot-grid en buttons.

Controleer elke special scroll met echte trigger, targettype, costs, energy, XP, cooldown, requirements, effect, animation/projectile/graphic alleen indien bewezen en rollback bij failure. Bekende probleemgevallen: Famine, Steel of Legends/Steel Titan, Winter Storage, Call to Arms, Multichop, Regrowth, Generate Compost, Venom Shot, Goad, Ambush, Petrifying Gaze, Rise from the Ashes, Immense Heat, Swallow Whole, Vampire Touch, Herbcall, Ophidian Incubation, Explode en alle overige scrolls. Geen scroll mag alleen als item bestaan zonder werkende route.

Renew bij Summoning obelisk geeft “Nothing interesting happens”; alle obelisks en Renew-routes controleren.

## Curses, prayer en presentatie
Ancient Curses moet echt unlocken, activeren en als prayerbook kunnen worden gekozen, via home en een betrouwbare testroute. “Already performed the ritual” mag niet verschijnen wanneer alleen activation/book-switch ontbreekt. Controleer alle curses, drains, restores, overheads, protection, Soul Split, Turmoil, Anguish, Torment, Leech-varianten, PvP/PvM, incoming-hit hooks, death en prayer interfaces.

HP en prayer worden nu als 990 getoond; de eigenaar wil 99. Interne x10-serverwaarden mogen niet worden gewijzigd. Alleen client/presentatie moet 99 tonen. Hitsplats tonen interne honderden; visueel moet bijvoorbeeld 20 worden getoond, met leesbaar OSRS/old-style formaat en correcte grootte.

## Wapens, equipment en specials
Controleer globaal alle wapens, weapon_type, attack styles, equipment slots, charges, check/recharge, dismantle, item-on-object routes en equip/unequip tijdens combat. Expliciet gemeld: Dragon claws, AGS, BGS, SGS, ZGS, Korasi, Hand cannon, Granite maul, Dragon 2h, Dragon halberd, Dragon mace, Dragon spear, Ancient mace, Barrelchest anchor, Dark bow, Magic shortbow, Chaotic rapier en alle overige specials.

Special attacks moeten correcte energy cost, activation, hit-count, formula, cooldown, animation, projectile, graphic en sound hebben. D claws gebruiken te weinig energy; godswords gebruiken 40%; meerdere godswords tonen geen animation; Granite maul is niet instant; meerdere dragon specials werken niet; Dark bow en Magic shortbow werken niet; Korasi heeft geen spec. Charged/dungeoneering weapons kunnen vaak niet worden gecheckt. Godswords kunnen niet worden dismantled. Tijdens combat moet een ander wapen kunnen worden equipped zonder handmatig eerst het huidige wapen uit te doen.

Equipment-bonusscherm: equip én unequip vanuit de gedragen-items-grid moet met één klik werken. Inventory-grid en worn-grid mogen geen verkeerde gedeelde handler gebruiken.

Slash Web werkt niet met een equipped godsword. Audit daarom globaal item-on-object dispatch en slash/stab/crush style/weapon-type voor alle wapens en vergelijkbare objects; geen fix alleen voor godswords.

## Wereldobjecten, entrances en routes
Alle onderstaande objectcategorieën moeten globaal worden gecontroleerd, niet alleen de screenshots:
- alle deuren en Open Door;
- alle magic doors;
- alle gates en fence-gates;
- alle pick-lock doors;
- alle levers en switches;
- alle trapdoors, pulleys en vergelijkbare stateful objects;
- alle staircases en ladders, Climb-up/Climb-down;
- alle tunnels, crevices en Agility shortcuts;
- alle Wilderness rifts;
- alle dungeon-, cave-, boss- en minigame-entrances;
- alle minecarts/rail transport.

De opties verschijnen vaak maar geven “Nothing interesting happens”. Controleer object-ID, objecttype, rotation, option-handler, requirements, animation, state-change, collision, path, destination, plane, repeat-use en correcte failure message. Expliciet gemeld: Wilderness rifts, Barbarian Outpost gate, Edge->Grand Exchange tunnel, Warrior’s Guild door, Magic Door, staircase, Crevice, gates en minecart. Alle dungeons en alle entrances in heel Gielinor vallen binnen scope.

## Teleports en vervoer
Controleer Digsite pendant, Ectophial, teleport crystal, Ring of kinship, jewellery, glory/skills necklace, spirit tree, fairy ring, glider, magic carpet, balloon, minecart, charter boat en overige teleport-items/netwerken. Controleer bestemming, requirements, interface, animation, plane, collision, repeat-use en failure feedback. Corporal Beast moet op echte cave-locatie staan; hetzelfde controleren voor alle boss- en dungeonlocaties.

## Shops
Veel shops kopen vrijwel ieder item uit de inventory. Dat is fout. Controleer iedere shopfamilie op toegestane stock/sell-policy, itemcategorieën/IDs, buyback, prijzen, noted/unnoted, stackables, voorraadlimieten, coins, quest-items en weigermeldingen. Een Summoning-shop mag geen willekeurige gear of quest-items inkopen.

## Skills, quests en progression
Audit alle skills end-to-end, niet alleen één gemelde skill: Slayer, Agility, Prayer/Curses, Summoning, Fishing, Firemaking, Mining, Smithing, Crafting, Fletching, Herblore, Cooking, Thieving, Hunter, Runecrafting, Construction, Dungeoneering en alle overige aanwezige skills. Controleer object/NPC-interactie, requirements, XP, levels, timers, animations, depletion/respawn, interfaces, save/load en failure paths.

Fishing spots geven vaak “Nothing interesting happens”; controleer alle fishing spots, tools, bait, catches, XP, animations, depletion en respawn. Add logs op unlit beacon werkt niet; controleer alle beacons en item-on-object Firemaking routes, consumed logs, XP, state-change en timers.

Controleer iedere geïmplementeerde quest van start tot finish, stages/varps/varbits, quest points en persistence na relog. Vampire Slayer en totale questpoints zijn expliciet fout gemeld.

## Client, graphics, audio en controls
Wilderness-level plus danger/multi icons moeten bij login, teleport en grensovergang werken in fixed, resizable en fullscreen. Inzoomen/uitzoomen met muiswiel werkt niet; herstel viewport-only zoom met min/max zonder chat, bank, tabs, interfaces of console te beïnvloeden. Nieuwe/corrupte profielen starten fixed en lowest; geldige voorkeuren blijven behouden. FPS/memory-overlay standaard uit met bewuste toggle. Graphics moeten op trage laptops bruikbaar blijven. Mono/stereo/volume moet bestaande muziek/effects/area streams direct stoppen/herbouwen zonder overlap of blijvende sound.

## Admin testcommands
De eigenaar kreeg “No valid command found” voor commands met verkeerde prefix. Controleer en documenteer één canonical command-prefix in client en server; helpteksten moeten exact overeenkomen. Test daarna maxtest, master, restore, infpray, infhp, infrun, home, mypos, npc_inventory, npc, item, equ, curse en tele.

## Verificatie-eis
Voor iedere batch: bestaande diff lezen, root cause aantonen, gerichte regressietest schrijven, test/build/boot uitvoeren, en exact DONE/PARTIAL/BLOCKED rapporteren. Een succesvolle build zonder gedragsbewijs is niet voldoende. Alle owner screenshots en live failures moeten in de statusledger terugkomen.
Serverbrede batch afgerond: commit 94acb386 normaliseert voorloop-dubbelepunten in ClientCheatHandler, zodat command-invoer met :command of ::command dezelfde commandnaam bereikt. Gecompileerd met :game:compileKotlin. Eigenaar-live-test: probeer :home, :mypos, :master, :infrun en ::home; verwacht geen No valid command found.
