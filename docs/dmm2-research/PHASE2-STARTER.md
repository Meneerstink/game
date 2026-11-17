# DMM2 deel 6: starter, server-WIP

Besluit V2 (16-11-2026): onbeperkte wiki-uitrusting zonder looting bag/geld/tabs; 1M + 100 house tabs blijven eenmalige introductiebeloningen. D9/PvpRespawnChest blijft afzonderlijk. Geen wijzigingen aan cache, Items.kt, items.yml, NPC-spawns of client.

Bronnen zijn de reeds opgeslagen wikitext/cachedefs met hashes in wiki-manifest.json en cache2686-items.json. Geen donorcode. Deadman starter pack rev15188784 (2026-04-22), Starter sword rev15260147 (2026-07-10), bow rev15188786/staff rev15188787 (2026-04-22). Chronicle rev15366319 (2026-10-05 22:39:10 UTC) opnieuw geverifieerd via wiki-API op 16 november; dezelfde revisie als fase 1.

## Correcties op fase 1

- Starter sword is **mithril-scimitarsterkte**, niet rune-scimitarsterkte. De opgeslagen bron en broncache zijn eensgezind: stab5/slash21/crush-2, strength20, defence slash1, speed4. Claude moet deze echte bronwaarden gebruiken voor de import.
- Chronicle-charges blijven behouden na verlies/vervanging. Daarom accountkey `chronicle_charges`, maximaal1000, +10 per geopend pack; geen charges op elk los boek. Bow/staff hebben wel eigen ItemAttribute.CHARGES (2000/500).

## Gedrag

`NewPlayerStart.grantStarterPack` migreert eerst, geeft maximaal één introductiepack en betaalt de eenmalige beloning apart. Reclaim staat via `DeadmanStarterPack.reclaim` los van de introductie en voegt nooit cash/tabs toe. Een ongeopend pack in enige accountcontainer voorkomt een tweede pack. Na openen kan weer geclaimd worden.

`dmm2_starter_intro_reward` is persistent: 1=nog verschuldigd, 2=betaald of reeds geopend vóór migratie. Bij de eerste migratie wordt een oud account met `deadman_starter_pack_given=true` én een bestaand pack als onbetaald herkend; zonder bestaand pack als reeds geopend. Alleen een echt nieuw account (NEW_ACCOUNT_ATTR) is zonder oude grantflag nog cash-eligible; onbekende legacyflags bewijzen geen onbetaalde beloning. Daarna verandert packbezit de markering nooit meer. Oud beleid bevatte geen afzonderlijke open-markering: **een oud verloren/vernietigd ongeopend pack is niet te onderscheiden van een geopend pack**. Conservatief geen tweede geldgrant; dit bekende historische informatiegat staat in het dossier.

Introductiebeloning wordt op kopieën van inventory/bank gepland; bij overflow/volle opslag niets gedeeltelijk uitbetalen. Bij succes worden containers plus marker samen gepersist. Bij falen blijft eligibility voor login/retry bestaan. Legacy config-rows `starterChestContents` leveren geen herhaalbare cash meer. De twee oude cash-in-pack-tests in NewPlayerStartTests zijn aangepast aan V2; geen andere tests of PvpRespawnChest gewijzigd.

Open vereist elf werkelijk vrije inventoryslots (wiki), neemt één pack en voegt de tien wiki-regels ondeelbaar toe. Geen gewone Tuna361, looting bag of geld als vervanging. Zolang de echte ids ontbreken geeft de API NOT_READY en blijft het pack intact.

`Combat.canEngage` blokkeert de drie starterwapens tegen spelers vóór spell-/specialkosten. RangedAmmo gebruikt bij de bow geen ammo/quiver; de ranged route verbruikt een charge per aanval, ook miss. Staff heeft een eigen built-in spell route (basis8, bestaand magic accuracy/damage-framework, 2 Magic/1.33 HP per schade; defensive spread zoals powered staves), geen rune- of spell-level-eis. Geen charges uit bank/safebox. Laatste charge verwijdert het wapen; kopieën delen hun attributen niet. Staff- en bow-aanvallen weigeren totdat Claude het echte AV-contract registreert; geen verzonnen effect-id.

## IDS-NODIG-VOOR-CLAUDE

Nog geen nieuwe lokale ids in Items.kt/items.yml van deze worktree. Installeer bij plugin-initialisatie, vóór speleracties:

```kotlin
DeadmanStarterPack.install(DeadmanStarterPack.Ids(
    sword = /* local source22331 */, bow = /* local source22333 */,
    staff = /* local source22335 */, tuna = /* local source26149 */,
    chronicle = /* local source13660 */, chest = /* local source13317 */,
    legs = /* local source13318 */, cape = /* local source33063 */,
))
```

Dit zijn handoffcommentaren, geen uitvoerbare placeholder-ids. Kies bij de cache-import expliciet de gepinde startervariant (22331/22333/22335 versus alternatieve cache28559/28555/28557). Bestaande Dramen staff772 en Items.DEADMANS_SKULL worden hergebruikt; pack blijft config23881. Geef de speciale Tuna zijn eigen stack-/eetgedrag; de gewone Tuna is geen equivalent.

Registreer bewezen lokale `StarterWeaponCombat.Appearance(animation, cast, projectile, impact)` voor bow en staff. Ontbrekende AV blokkeert de aanval. Claude bindt de bestaande combat tutors/Brugsen aan reclaim, Check/Destroy/equipment-opties, speciale Tuna en Chronicle aan de echte itemhandlers; Chronicle leest de gedeelde charges, weigert Wilderness, gebruikt de gedeelde teleportgate en boekt alleen een geslaagde teleport af. Entrana moet het pack expliciet weigeren (de generieke equipmentfilter ziet een ongeopende kist niet). Dat is open bindingswerk, geen bestaande werkende route.

## Validatie en testclient

Eigen tests: DeadmanStarterPackTests, StarterWeaponCombatTests, gewijzigde NewPlayerStartTests. GROEN op 16-11-2026 20:32: 13 tests, 0 failures/errors (5 + 4 + 4). Alle actuele gewijzigde Kotlinbestanden consistent gecompileerd; beide gewijzigde bindingscripts apart groen met de echte Kotlin-scriptcompiler. Daarna, na de laatste-charge/XP-volgordefix, dezelfde starterklassen opnieuw groen in de gecombineerde run met de twee Reward Store-klassen (25/25, BUILD SUCCESSFUL in2m10s). Alleen eigen klassen, --priority low/bouwslot, echte Gradle Test/JUnit/standaard-XML via lokale focused-tests.gradle; geen volledige suite of full-buildclaim.

Claude: echt nieuw/legacy account; intro finish/skip/interrupt; oude pack open en oude al-betaalde account; bank/inventory vol; pack in bank/graf/BoB; herhaald reclaim/open, logout/restart; exact één cashbeloning, juiste elf-slotsgrens, speciale Tuna en gedeelde Chronicle-charges. Bow2000/staff500, miss/laatste charge, XP-lock, PvM, directe PvP/manual spell/special/auto-retaliate; ammo/quiver blijft gelijk. Itemstats naast gepinde bron, looks/AV en worn Check. D9-kist blijft78k+skull volgens eigen beleid. Geen testclient/live-PASS door GPT-A.
