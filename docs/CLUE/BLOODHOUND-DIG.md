# Bloodhound Dig continuation, 17 November

Wiki Bloodhound revision15375113 dates Dig to8September2026. Call follower
revision15338208 explicitly uses the player's current tile; Dog revision15389611
confirms this replaces a spade for Treasure Trails, excluding other activities.
All three pins/hashes are in sources/manifest.json. No dog quest, new breeds,
Barrows or Mole shortcut is introduced by this card.

Pinned cache2686 predates this update and has no sequence14498. The exact
post-update classic clip is from OpenRS2 cache2762/build242,11November2026:
`DOG_UPDATE_DIG`14498, frameset14566, base1491. Base1491 is byte-identical to
the existing Bloodhound's source2686 ready-sequence skeleton, SHA256
9b97d183abedf3b8b800a945c9f61425f708108e3071816053181336db8913b0.
No model, NPC, pet item or replacement rig is needed.

`prepare_pet_dig_source.py` downloads only the required original containers
and writes a private source fixture under build/clue/pet-dig/source-2762/cache.
It never opens a target-cache writer. PET-DIG-SOURCE.json records container
URLs/hashes and the exact sequence. Eight five-cycle frames carry source sounds
12114,12115,12116 on frames1,3,5; weight100,loops1,radius5,retain0.
The common importer drops the *fields* weight/retain because667 has none;
these particular values are deterministic/default, with no weighted alternative
or retained playback to reproduce.

Runtime scope: both existing followers14631/14844; only the actual spawned,
living personal Bloodhound in the same world can dig. The pet animates; the
player does not acquire a spade animation. The existing held-scroll/site/guardian
contract controls progress and all rewards. Wrong location may animate without
completing a step. Foreign/stale/dead followers spend nothing. Both the new menu
option and the existing Pets.call route reach this contract. Ordinary followers
return without animation or clue progress.

Six fresh staged tests pass, including both forms, foreign account, wrong world,
logout/death/despawn, unavailable animation, wrong site and malformed mappings.
The real Pets source is compiled with these tests. Whole-branch script/Gradle
and loaded-client checks remain separate gates.

## Claude-only cache order

1. `python tools/clue/prepare_pet_dig_source.py` prepares the exact source fixture.
2. For the **single importer JVM**, set RSPS_OSRS_SOURCE_CACHE to
   C:/RSPS/work/gpt-b/build/clue/pet-dig/source-2762/cache and run
   `OsrsFxImportTool clue-pet-dig` for a fresh preflight. The batch checks the
   pinned sequence hash; default source2686 must fail rather than fabricate it.
   Then Claude may run that same batch with `--apply`.
3. `python tools/clue/build_combat_mapping.py --pet-dig --write` publishes
   cfg/clue/pet-dig.tsv **only after** its ledger-owned sequence is verified in
   both target caches. Do not hand-fill a proposed local id.
4. `CluePetDigCacheTool <report-tsv>` preflights the two existing NPC definitions;
   Claude may add `--apply`. Only fourth option Dig is appended, preserving
   Talk-to, Store, Pick-up and all original bytes. Applied=true is written only
   after verification. Restart the server with mappings and cache together.

Production-source read-only preflight174043:23logical mutations,44CREATE/2NO_OP across both caches;
proposals seq14498->17769,frameset14566->6012,synth12114..12116->10843..10845.
The existing base is reused. Menu preflight174059 has two mutations, one per pet.
Both target caches and ledger were unchanged. These ids are **not reservations**;
rerun after preceding imports. Earlier preflights170237/170613 overlapped Claude's
item/recolour application and are rejected by the before/after guard.

Client: both pet forms; menu and Call follower; exact player tile with no spade;
wrong tile, second account, logout, dead/dismissed follower; hard wizard dig must
still require the exact kill and second dig. Compare the clip against the updated
OSRS Bloodhound and hear all three embedded digging sounds. Preserve the
existing pet pickup, upgraded Store and ownership behaviour.

Claude applied this batch17-Nov18:5x: tx175755 sequence14498->17769,
tx175836 two Dig menus; pet-dig.tsv was published in363749298. Do not repeat
these CREATE plans. The staged6-test result was also repeated against promoted
production source (validation/pet-dig-actual-source.json, matching all5source
hashes on resume). Actual loaded-client checks above remain required.
