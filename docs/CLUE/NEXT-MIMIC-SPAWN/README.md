Tested spawn/attempt rollback fix, outside runtime during integration162314.
Six fresh tests passed on16-Nov. SHA-256 uses UTF-8 with LF-normalized newlines.

MimicEncounter.enter now allocates/spawns before issuing an attempt token, and
rolls back the actor/map if the held casket changes or the player dies during
spawn. An active attempt cannot start another entry. The fighting transformation
spawns successfully before removing the passive actor. Refusal leaves it usable
for retry. Existing arena coordinates, stats, loot and attempt limits are unchanged.

Tests cover refused passive/fighting spawns, missing assets, banked/replaced
casket, active/duplicate entry, death during spawn, exact teleport, release once,
one failure on leave and separate accounts. No cache/client/server was operated.

This is not the missing candy/minion/forward-step implementation or full Mimic
acceptance. Promote these exact sources after the frozen integration finishes,
then include MimicEncounterTests in the next narrow Gradle gate.
