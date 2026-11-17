# DMM2 fase 5 — overdracht ChatGPT-A

Hervat vanaf ce08b66d7 met het bestaande ongecommitte boot/drops/Collection Log-werk. Dat werk is behouden en afgerond. Werk uitsluitend op `gpt-a/dmm-pakket-2`; geen push, merge, cache-apply, server-/clientbediening of liveactie.

## Wat is geleverd

- **156c8b0c9 — echte boot/data-integratie.** `data/cfg/dmm2/skins.tsv`, `Dmm2Boot.kt`, `DropSources.kt`, `CollectionLogPages.kt` en hun proeven: 143 echte mappings installeren, zestien cosmetic-stateparen met chargebehoud, zeven rewards kopen/refunden, starter/NPC-contracten, bronafhankelijke Fortuity-index en eigen Infernal Mage Fire-ruby-log. Twee verouderde regressiefixtures volgen nu de actuele special-energie-API en de aparte Shadow-rune-economie.
- **dad215f — aanvullend bron- en native import-plan.** Font645 bewezen als named quillfont in beide caches; Reward Store als OMNISHOP819/806 bewezen uit cacheDBrow929 + valuta1206 + zeven productrows + bron-CS2 + wiki-afbeelding. Bestaande OsrsInterfacePortTool en OsrsFxImportTool hebben benoemde aanvullende selectors. Alle plannen uitsluitend op twee fysieke cachekopieën. De Git-blob van ids.tsv bewaart nu ook byte-exact Claude's oorspronkelijke handoff; geen mapping hernummerd.
- **1ddef6dac — Fire Altar-serveraanvulling.** `FireAmulet.kt`, `dmm2.plugin.kts`, `Dmm2ItemServicesTests.kt`: controle vóór presentatie, enchant-animatie/gfx/geluid en opnieuw atomaire controle na drie ticks. Afgenomen boost/gewijzigde inventory/dubbele klik verbruiken niets gedeeltelijk. Het weigeringsoverzicht geeft uitsluitend de bewezen receptgegevens; de letterlijke OSRS-servertekst is niet teruggevonden.
- **e21b7f522 — Wapengeluid/-presentatie.** `CorruptedWeapons.kt`, `CombatConfigs.kt`, `MeleeCombatStrategy.kt`, `Dmm2WeaponPresentationTests.kt`: base-look/audioherkenning zonder fysieke stats/items te vervangen; echte corrupted claws delen gewone claw-cues per stijl; geen tweede fallback bij framegeluid. Alle acht families, khopesh en skins zijn in de bron-/routetabel beschreven. Vijf corrupted specialaliases zijn met echte import-ids door de dispatcher getest, ook bij onvoldoende energie.
- **Avarice.** Geen fictieve handler toegevoegd: broncache en eigen cache hebben Value op **optie2**; botoptie3 was leeg. De bestaande Value-binding en GE-stackwaardering zijn beschreven met het bron-/adviesonderscheid. Exacte hertest in checklist13.

## Testbewijs en reproduceerbaarheid

`RUN-PHASE5-TESTS.ps1`: 281/281 proeven in39 gerichte klassen groen, nul failures/errors/skips. Dit omvat de eerdere eigen DMM2-kernen plus de gevraagde bestaande combat/drops/quest-tab-regressies. Actuele gewijzigde Kotlin en negen eigen scripts gecompileerd met de gerichte harness, Gradle `--priority low`, centraal bouwslot. De twee importerproeven zijn afzonderlijk groen: totaal283 unieke proeven. De laatste Scythe-categoryaanvulling is bovendien opnieuw gecompileerd en in de drie geraakte klassen groen getest (24/24); de definitieve XML/totalen staan in `PHASE5-VALIDATION.json`.

`PREFLIGHT-UI-AV.sh`: alle drie native PLANs en de importerproeven exit0;78/78 cachekopiebestanden bytegelijk vóór/na; journal leeg. Exacte opdrachten/classpaths en uitvoer in `phase5-ui-output/`. UI-plan meldt alle ontbrekende dynamische callbacks; er is geen zichtbare UI-PASS geclaimd. De broncache blijft gepind2686. Data-validator controleert143 mappings,73 metadatarecords,10 exacte corrupted bonussets en16 skin-stateparen.

JUnit-XML: `phase5-output/regression-final/` en `regression-sound-final/`; de eerste failures en hun fixturecorrecties zijn eveneens bewaard. `validate_phase5.py` controleert de actuele proeven, data, bron-/codehashes en cachekopiebewijs. Doctor/Rsps-Lint ontbreken in deze worktree; gerichte compiler/JUnit, Pythoncompile, bash syntax en git diff-check zijn de beschikbare checks. Geen volledige test-suite gedraaid.

## Bronnen en donors

OSRS Wiki API-revisies/datum, originele creatie-animatie en winkelafbeelding; cache2686 inclusief volledige IF3-decode/listeners, DBrows, CS2 en glyphnamen; RuneLite gameval en officiële cacheloaders. Alle nieuwe responses/hashes in `phase5-sources/` en uitleg in `PHASE5-SUPPLEMENT.md`. Geen donorimplementatie toegevoegd. Bestaande lokale combat-, enchant- en importhelpers hergebruikt.

## Claude: concrete resterende integratie en testclient

`TESTCLIENT-CHECKLIST.md` blok13 is de directe hertestronde, naast de volledige blokken1–12. Echte client: beide claws per stijl beluisteren; alle vijf specials via de echte specialbalk (bot+`::spec` was ook bij gewone claws ongeldig); Fire Altar succes/weigering/interrupt/relog/boost en verbruik; Avarice Value-optie2 met threshold/notes/volle inventory; gewone/corrupted Shadow vergelijken.

Cache/UI door Claude: aanvullende echte sequentiële imports/mappings, behoud van232→1203 via `--reuse`, SafeBox stoneframe/grid/bulk/slotcallbacks en OMNISHOP819/806 koop-/sellbackwidgets aan Dmm2Interactions. De statische layouts alleen maken geen werkende UI. Source seq8056/8057 voor Scythe,721/spot116 voor enchant, source callbacks en toolgrenzen staan exact in het aanvullende rapport. Daarna alle eerdere relog/skull/death/I10-routes, NPC-icoon, overige AV/POH/Home/Chronicle-integratie en live-A9 onder Claude.

## Open bronpunten met gekozen advies

Geen nieuwe vraag blokkeert GPT-A's serverwerk. Exacte Fire Altar-refusaltekst en fire-craftaudio/hoogte, specifieke Vitur-audio, khopesh timing/appearance, Avarice prijstype/unit-versus-stack, starter11slots en Salarin-baseline blijven expliciete bewijsgrenzen. Deze ronde koos feitelijk receptoverzicht, ruby-enchantaudio en bestaande Scythe-categorycues als herkenbaar gelabelde adviezen; geen van die keuzes wordt als gemeten exacte OSRS-uitkomst verkocht. Ancient34607, Nigel-icoon en de eerder genoemde ontbrekende asset-/UI-contracten blijven Claude's merge-/cachewerk. Release-/chestvlaggen zijn niet door GPT-A geactiveerd.

Dit is een geteste server-WIP-overdracht. Spelerklaar, testclient, exact OSRS op alle open bronpunten en live zijn niet afgemeld.
