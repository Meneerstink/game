# N439 - Zilyana Jr. (V12)

Cause: `pet_voices.tsv` deliberately used Commander Zilyana's special-attack sound for the pet's voice. `PetVoices.appear`
and `play` correctly played that shared voice once; this is a sound choice problem, not a repeated-spawn bug.

Replacement: OSRS synth **3886**, `godwars_saradomin_avatar_shield`, already imported as local synth **10280**.
It is Commander Zilyana's **defend** animation sound (OSRS seq **6969**, frame **12**, loops **1**, radius **10**), not her special.
The single short shield cue lasts **1500 ms** (including its decay), has one instrument and no loop markers (0/0).
The old special (OSRS 3876 / local 10523) lasts **1400 ms**, but combines four instruments with staggered starts.
Thus the replacement is not shorter in elapsed time; it is a single shield reaction instead of the layered special attack.
The shared voice stays at **255/255**, including its infrequent idle voice. The old N405 generator comment reports the shield
rendering at peak 5/128, so additional attenuation would risk making it inaudible. V12 explicitly replaces the special's sound;
the quiet shield reaction is selected with the standard mix level. Claude must judge audibility in the real client.

Sources / reproducible evidence (read only):

- `C:\RSPS\reference\runelite\gameval\AnimationID.java`: `GODWARS_SARADOMIN_DEFEND = 6969`.
- Pinned OSRS `import-source/openrs2-2686/cache`, index 2/group 12/file 6969: sound tuple (12,3886,1,10).
  Read with existing `team/prep/N88/osrs_pet_sounds.py::seq_osrs`.
- OSRS Wiki [List of sound IDs](https://oldschool.runescape.wiki/w/List_of_sound_IDs) (local donor export
  `team/prep/N88/sound_ids_raw.txt`, line 7773) gives the Jagex sound name.
- `C:\RSPS\OSRS_IMPORT_MASTER.yml`: existing `synth:3886 -> 10280` mapping (tx-20260919-162121).
  Read-only comparison confirmed the local 10280 bytes equal OSRS 3886:
  SHA-256 `491eba21f897dd7c6c95120e1b102d37e13393ed65517706134cd1663c013ddf`.
- Duration decoder: `team/prep/N88/getting-pet-match/synth_duration_probe.py::decode`, ported from the client's
  SynthSound/Instrument metadata parser. OSRS/local 3886/10280: raw and trimmed duration 1500 ms, one instrument 1500+0 ms;
  old local 10523: instruments 300+500, 500+150, 500+0, 1000+400 ms = 1400 ms total.
- Darkan/Void/Novite have no more suitable OSRS pet-specific voice; this selection uses the actual OSRS boss defend sequence.

Update both the runtime TSV and its generator `OsrsPetImportTool.VOICES`, so regenerating the table retains the fix.
No new cache import is needed. Pet movement/standing animations, their frame sounds, other pets, cadence, appearance paths
and one-shot playback remain as before.

Claude's testclient check: place Zilyana Jr., Talk-to, Call follower, teleport and relog. With avtrace, expect local **10280 once**
per voice event at volume **255**, never 10523. The existing generic appearance cue (2739) can still accompany the voice.
Judge the audible result; metadata proves provenance, duration, looping and selected volume, not the owner's listening preference.
Do not alter the five pets' approved standing frame sounds.

Automated checks: `ZilyanaPetVoiceTests` invokes appear, Talk-to and scheduled idle playback, verifies one sound per event,
and compares every other pet's voice with the previous table. `PetSoundsTests` checks the shared pet paths.
`OsrsPetVoiceTests` verifies the generator and every runtime TSV row agree without opening or writing a cache.

Completed 2026-10-30: `ZilyanaPetVoiceTests` 1/1, `OsrsPetVoiceTests` 1/1 and `PetSoundsTests` 5/5 green,
0 failures/errors. Combined with N446: 17 tests green; targeted Gradle exit 0 (BUILD SUCCESSFUL), low priority.
The final run reused the unchanged green core test outputs; all 15 plugin tests executed after the fixture corrections.
No cache importer was run by ChatGPT-A; only source/table data and tests changed.
