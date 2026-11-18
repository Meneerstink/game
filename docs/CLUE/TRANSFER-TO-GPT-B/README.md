# Watson / Mimic prototypes for GPT-B

Owner/regie update in `C:\RSPS\team\dossiers\CLUE.txt` transferred Watson/Mimic to
GPT-B after these independent drafts had already been written. They were moved
out of runtime source, without further development, to avoid overlapping work.

**UNTESTED. Do not paste, merge or deploy them without review.** The first GPT-A
build failed: Mimic item attributes were added after core compilation had already
started, so plugin compilation could not see them. The separate Gson API problem
in GPT-A's own catalogue has been corrected. No tests ran in that attempt.

The Watson draft covers atomic partial hand-ins, duplicates, four-for-one,
full inventory, master ownership in bank/grave/Death and separate accounts. Its
dialogue is incomplete; Check uses an adapted 667 message box, not the OSRS UI;
NPC Contact, blueprint/STASH options, scroll-box eligibility and first dialogue
still need work. Its NPC spawn comes from the Wiki pin 1646,3574, radius 4.

The Mimic draft is only a per-item attempt/settlement model and stomp formula,
not an encounter, arena, combat script, death retrieval or reward implementation.
Its proposed ItemAttribute names were removed from GPT-A's runtime enum:
`MIMIC_SOURCE_TIER`, `MIMIC_FAILURES`, `MIMIC_DEFEATED`,
`MIMIC_ATTEMPT_SERIAL`, `MIMIC_SETTLED_SERIAL`. Source-tier ordinals must match the
final FIVE-tier enum; beginner is now excluded. Nothing using these attributes
has been deployed or saved in a player account.

Shared source evidence is in `docs/CLUE/sources/` (manifest plus Wiki pages).
GPT-A owns `content/scrolls/` reward/lifecycle APIs, log and viewer; coordinate
interfaces with GPT-A through the CLUE dossier. Core Mimic item 23729 and NPCs
14401-14405, Watson 14406, Strange casket 62743 already exist. These drafts are
convenience code only, not evidence of a completed feature.
