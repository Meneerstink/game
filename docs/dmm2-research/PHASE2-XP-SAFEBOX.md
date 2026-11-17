# DMM2 deel 3 + 4 — serveroverdracht aan Claude

Scope: dossierakkoord van 16-11-2026 17:5x; alleen XP-slot en Safe Deposit Box. WIP voor review en UI-integratie. Geen cache, `Items.kt`, `items.yml`, Collection Log, DropSources, world_edits, client, serverstart, live, push of merge.

## Bronnen en beslissingen

Op 16 november opnieuw via de publieke wiki-API gecontroleerd: onderstaande revisies zijn nog actueel.

| Bron | Revisie | Revisiedatum UTC | Gebruik |
| --- | --- | --- | --- |
| [Transcript:Nigel](https://oldschool.runescape.wiki/w/Special:PermanentLink/15394041) | 15394041 | 2026-11-04 18:44:19 | XP-keuzes, bevestiging, herstel en tekst |
| [Financial Wizard](https://oldschool.runescape.wiki/w/Special:PermanentLink/15364116) | 15364116 | 2026-10-04 10:35:18 | Tien individuele items, geen stacks; geen onruilbare items |
| [Financial Seer](https://oldschool.runescape.wiki/w/Special:PermanentLink/15390113) | 15390113 | 2026-11-02 19:29:12 | Dezelfde safebox-dienst |
| [Transcript:Financial Wizard](https://oldschool.runescape.wiki/w/Special:PermanentLink/14868949) | 14868949 | 2025-03-23 15:53:01 | Exacte skull-weigering |
| [Transcript:Financial Seer](https://oldschool.runescape.wiki/w/Special:PermanentLink/14996472) | 14996472 | 2025-09-29 15:21:08 | Wizard/Seer delen de opslag |

Dossier V5: de zeven OSRS-combatskills, inclusief HP en Prayer. Summoning blijft skilling. Geblokkeerde XP geeft geen skill/level-taskevents; DP verandert niet. Deel 4: één gedeelde 10-items NO_STACK-container, skull-gate, persistence en ownership zonder effecten. Noted items tellen elk als één fysieke eenheid, zoals in het goedgekeurde rapport.

Geen externe donorcode gebruikt. Hergebruik van de bestaande lokale AttributeMap, spelercontainer-/JSON-serializer, PvpSkull, RemovedItems, Possessions en persistNow.

## Wijzigingen en integratiecontract

`ExperienceLocks` in core bewaart twee onafhankelijke Int-attributen: `deadman_combat_xp_disabled` en `deadman_hitpoints_xp_disabled`. Ontbrekende sleutels betekenen XP aan. Het combat-slot blokkeert skills 0–6; het HP-slot skill 3. Beide moeten HP toestaan voordat HP-XP toegekend wordt. De vlaggen resetten niet bij dood. Ze blokkeren `Player.addXp` vóór rate, skillmutatie, orb, experience/level-uphooks en anti-cheat-tijdaftrek, ook voor `modifiers=false`. Administrative stat setters en het laden van bestaande XP blijven gewone setters, geen XP-beloningen.

De `PlayerExt.addXp(...checkBrawlingGloves=...)`-route weigert vóór een glove-charge gebruikt wordt. De normale XP-rates zijn ongemoeid.

Claude voegt aan de echte geïmporteerde Nigel Talk-to-dialoog de brontak `Could you stop me from gaining XP?` toe en roept binnen de bestaande QueueTask aan:

```kotlin
NigelExperience.adjust(this, npc = importedNigelId)
```

Deze functie implementeert de twee keuzes en beide yes/no-bevestigingen uit het transcript, bewaart alleen na Yes, en doet `persistNow()` vóór de eindtekst. Geen bestaande native NPC met toevallig dezelfde naam gebonden; geen bron-NPC-id als lokaal id gegokt.

`SAFE_DEPOSIT_BOX_KEY` staat in core bij de containers en is meteen in `PluginRepository.containerKeys` geregistreerd, dus vóór `JsonPlayerSerializer` geladen items opzoekt. Naam `safe_deposit_box`, capaciteit 10, `NO_STACK`. Wizard en Seer krijgen geen eigen tweede key. Niet-lege opslag valt automatisch onder de bestaande JSON-save/load. Alle overdrachten wijzigen beide containers synchroon en slaan daarna dezelfde spelersnapshot op; geen directe savebestanden aangeraakt.

Voor beide financiële NPC's gebruikt Claude dezelfde `SafeDepositBox`-API:

1. Valideer de werkelijke NPC-interactie, bereik, activiteit en UI. Roep `beginAccess(player)` aan. Alleen `Status.OK` mag het safebox-venster openen.
2. Toon `contents(player)` (10 slots met diepe itemkopieën). `null` betekent toegang geweigerd; sluit de UI. De UI mag deze kopieën niet terugschrijven als autoritatieve staat.
3. Elke packet/button-handler valideert de geopende lokale UI, NPC/bereik en de juiste inventory-component. Roep vervolgens `deposit(player, inventorySlot, expectedItemId, amount)` of `withdraw(player, boxSlot, expectedItemId)` aan. Lees daarna de twee actuele containers opnieuw voor de weergave. Gebruik geen `Bank.deposit`/`Bank.withdraw` of `ItemContainer.add(Item)` voor deze opslag.
4. Bij sluiten, Esc, andere modal, teleport, logout of onderbreking: `endAccess(player)`. De toegangsvlag is niet persistent en reset bij dood. Na skull/dead-weigering moet de opslag opnieuw via de NPC geopend worden, ook als de skull daarna verdwijnt.
5. `SKULLED`: toon exact `SafeDepositBox.SKULL_MESSAGE` uit het transcript. Andere `Status`-waarden zijn serverresultaten, geen zelfverzonnen spelerteksten; gebruik de passende bestaande/bronbewezen UI-weigeringen.

De service controleert bij openen, lezen én elke transactie opnieuw de echte `PvpSkull.isSkulled` (dus ook loot-key-skull), doodstatus en toegangssessie. Slotgrenzen, stale id, niet-positieve hoeveelheid, tradeability van de ongenoteerde basisvorm en volledig verwijderde items worden gevalideerd. Een grote stortopdracht verplaatst uitsluitend wat uit het aangeklikte fysieke stackje in de resterende tien plekken past. Niet-stackbare exemplaren worden per slot gestort. Withdraw is één item per boxslot. Een volle inventory of Int.MAX_VALUE-stack geeft geen verlies of overflow. Attributen blijven per exemplaar bewaard; geen uncharge-/creationredirect, noteconversie of gedeelde mutable attributen.

De UI/pakketmapping is bewust geen onderdeel van dit commit. **UI-openstaat, bereik en interrupt/close-callbacks moeten bij Claude's koppeling worden uitgevoerd en getest.** De domeinservice alleen is geen afgeronde ingame bankdienst. NPC Bank/Collect/PIN blijven afzonderlijke bestaande bank-/GE-diensten bij Claude.

`Possessions` scant deze container reeds generiek; het label is toegevoegd. Perdu/uniqueness blijft daardoor de opslag meetellen, ook als de speler geen toegang heeft. Het generieke `Player.hasItem` sluit deze key uit, zodat bijv. hammer/saw/shears in de safebox geen gereedschap leveren. Bestaande combat-/ammo-/rune-/gear-effecten lezen inventory/equipment; de safebox wordt daar niet aan toegevoegd. `hasItem`-aanroepen geïnventariseerd: gereedschappen/resources mogen geen bruikbaarheid uit de safebox krijgen; eigendom en terugkoop horen via `Possessions` te lopen. Het bestaande StoreUi gebruikt hasItem ook voor de optionele Owned-indicatie; Claude kan bij UI-integratie voor die indicatie `Possessions.owns` gebruiken. Geen UI-bestand gewijzigd.

DeathResolver en DeathExecutor behandelen alleen inventory/equipment; safebox-items worden niet aangeboden als protected carried items, killerloot, graf of Perdu-loss. RemovedItems en bestaande geldige itemmigraties blijven, net als bij bankopslag, alle accountcontainers verwerken: een safebox maakt verboden/verwijderde items niet vrijgesteld.

## Validatie

Bestanden in deze WIP (alle paden onder de eigen worktree):

- Core: `game/src/main/kotlin/gg/rsmod/game/model/skill/ExperienceLocks.kt`, `model/entity/Player.kt`, `model/container/key/ContainerKeys.kt` en `plugin/PluginRepository.kt` (de laatste drie onder dezelfde `gg/rsmod/game/`-root).
- Plugins: `game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/deadman/NigelExperience.kt`, `SafeDepositBox.kt` in dezelfde map, `content/mechanics/ownership/Possessions.kt` en `api/ext/PlayerExt.kt` onder dezelfde `gg/rsmod/plugins/`-root.
- Tests: de drie hieronder genoemde eigen klassen onder hun overeenkomstige `src/test/kotlin/`-roots.
- Overdracht: dit document; voortgang buiten Git in `C:\RSPS\team\dossiers\DMM-PAKKET-2.txt`.

Eigen klassen: `DeadmanExperienceLocksTests` (core), `SafeDepositBoxTests` en `DeadmanExperienceExtensionTests` (plugins). Geen bestaande andere tests gewijzigd, geen volledige suite.

```powershell
.\gradlew.bat :game:test --tests '*DeadmanExperienceLocksTests*' :game:plugins:test --tests '*SafeDepositBoxTests*' --tests '*DeadmanExperienceExtensionTests*' --continue --priority low -I build/dmm2/preflight.gradle
```

De lokale, ongevolgde preflight-init onder de genegeerde `build/dmm2/` compileert uitsluitend de gewijzigde Kotlin-bestanden tegen bestaande eigen-worktreejars, binnen het normale machinebrede bouwslot, vóór Gradle de volledige afhankelijkheden bouwt. Dit wijzigt de gedeelde buildconfiguratie niet. Resultaat wordt na de run hieronder vastgelegd.

Testdekking: defaults en beide onafhankelijke XP-schakelaars; alle zeven combatvaardigheden; exacte rewards zonder modifiers; geen orb/task/level-events bij blokkade; Summoning/skilling werken; persisted keys/death-cleanup; geen brawling-chargegebruik. Safe box: tien losse coins/notes, elfde weigeren, onruilbare (ook noted) items, negatieve/MAX-hoeveelheden, verkeerde slot/id, skull na openen, loot-key-skull, close/reopen/andere speler, volle inventory/MAX-stack, chargebehoud zonder aliasing, echte JSON-containerformaat-roundtrip en registratie, eigendom versus bruikbaarheid, PvM/PvP resolve met/zonder skull, doodstatus, 100 herhaalde transactierondes met behoud van totaal.

**Gerichte testuitslag 16-11-2026 20:26: GROEN, 19 tests / 0 failures / 0 errors in de drie genoemde klassen.** Normale core/plugins-productiecompile en testcompile waren eerder groen; de volledige scriptscompile werd gestopt op een vooruit toegevoegde starterbinding na de main-snapshot. Daarom de actuele gewijzigde Kotlinbestanden consistent los gecompileerd en dezelfde drie klassen uitgevoerd met echte Gradle Test/JUnit en de normale XML-rapporten, via de lokale genegeerde `build/dmm2/focused-tests.gradle` (actuele classes v??r bestaande runtimeclasspath, geen ongefilterde suites). Command: dezelfde testfilters hierboven met `-I build/dmm2/focused-tests.gradle`. BUILD SUCCESSFUL in 2m25s. Geen testclient/runtime/liveclaim.

## Claude: testclient en review

- Nigel XP-tak met echte NPC/chathead: beide keuzes, Yes/No/afbreken; HP versus combat onafhankelijk. Test gewone combat, spell/cast-XP, Prayer, lamp/quest/AFK-exact XP en Summoning; controleer XP-orb, skills, task-events en onveranderd DP. Daarna relog en serverherstart.
- Wizard → tien air runes storten → sluiten → Seer ziet exact dezelfde tien items. Elfde weigert. Notes, verschillende exemplaren van een tradeable charged item en behoud van lading na opname/relog/herstart. Tweede speler blijft leeg.
- Skull en loot key vóór openen en na openen; klik/opname/input-X na skullen; daarna skull weg maar oude sessie hergebruiken. Geen toegang via bank/collect/deposit-box-verkeerde component, afstand, teleport, open andere UI, Esc, combat/interrupt of oude packetreplay.
- Volle inventory, bestaande stack op MAX, stale slots, dubbelklik, sluiten tijdens X-input. Vergelijk totaal inventory+box vóór/na.
- PvM/PvP/guard/alt/safe dood met opgeslagen items; niets uit safebox als keep/killerloot/graf/recovery. Perdu/uniqueness ziet eigendom, actieve gereedschappen/trinkets/charges/ammo/risk/bonussen niet.
- UI 0/10–10/10, spriteweergave, vaste/resizable/Stretched-modus, sluitknoppen en bestaande Bank/PIN/Collect. Cache, NPC-plaatsing, AV en live/A9 zijn Claude's gates.

Geen nieuwe eigenaarskeuze voor deze twee bouwdelen. V1/V3 en overige DMM2-delen blijven buiten deze WIP. Open integratiewerk: Claude's echte NPC-id's, UI/componentcontract, bereik-/closevalidatie en zichtbare runtimecontrole.
