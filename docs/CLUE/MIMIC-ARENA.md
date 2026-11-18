# Mimic arena relocation, 17 November

The source square10819 (m42_67) is **occupied native667 content** in both target
caches. The old in-place batch therefore fails safely. The owner's recorded
14September2026 Q10/17 answer is "unused square, instance" (owner-answer table
in OSRS_IMPORT_MASTER.yml). Never set replaceSquares or overwrite10819.

plan_mimic_arena_location.py proves that proposed4160 (m16_64/l16_64) has neither
named group in either cache. This is an internal relocation, not a changed OSRS
location or a reservation. Source10819 -> target4160 uses squareOffset(-26,-3).
Immediately recheck before apply; if occupied, select another empty square and
update the batch/config/tests together. The complete original room stays intact:
250placements,43loc definitions,46models,all four terrain planes and no exclusions.

cache/mimic-arena-source.json contains the original cache2686 terrain/locations,
their hashes, every placement/definition and the native sound comparison.
The source Keyhole34727 has Use/Exit and one placement at local31,23,plane1,
type10,rotation2: source2719,4311,1 -> relocated1055,4119,1.

MimicEncounter now requires a verified arenaRegionId, rejects occupied10819 and
copies all256chunks from the relocated room. Instance-relative player/boss/candy
coordinates keep their original source offsets, rotations and planes. Missing or
invalid mapping allocates no room and spends no attempt. Thirty-five fresh
Mimic tests pass, including this refusal and all256encoded source chunks.

The arena's ambient source2124 is byte-identical at the same id in BOTH targets,
SHA256 e389de5ee7a9181f914e28bc778d4456ea257aaeda845961b81ac23330e6806a.
The source decoder retains its radius/retain fields. This batch preserves the
native sound only after comparing both target payloads; nonzero retain or a
different/missing sound blocks instead of substituting an unrelated id.
Other region batches retain their existing conversion behaviour.
Two core tests cover source room/exit placement and ambient decoder/identity
validation. The initial relative-fixture-path run failed; its corrected ancestor
lookup works from both the worktree and module test directories.

## Claude-only application

After the tested source changes are committed:

1. Recheck `python tools/clue/plan_mimic_arena_location.py`.
2. Fresh `OsrsRegionImportTool clue-mimic-arena` preflight; verify source2686,
   target4160, CREATE-only map groups and unchanged10819. Claude alone adds
   `--apply`. The tool publishes source loc/model mappings and target zero XTEA.
3. Run `python tools/clue/build_mimic_arena_mapping.py --write` (Claude only).
   It verifies all250 mapped placements and the six named definitions/options
   in BOTH caches, then publishes cfg/clue/mimic-arena.tsv from the finalized
   ledger record. Runtime derives the region and Keyhole34727 identity from it;
   legacy arenaRegionId/keyholeLocalId JSON placeholders stay null. Set
   arenaImported=true only after dual-cache verification/client collision.
4. Retain ENCOUNTER_IMPLEMENTED=false and all unknown attack parameters. Arena
   application cannot enable the encounter by itself. Owner15-Nov1A and
   Claude17-Nov18:2x explicitly leave it disabled; two montage videos did not
   prove the remaining attack choices or landing distribution.

MimicArenaScenery installs the six exact examines at world init after validating
every name/option first. Pins: Keyhole (The Mimic)rev15334464, Coins (The Mimic)
14914004, Sweets15334486 (two objects), Sweet15344088 and Amulet of Glory
(scenery)15330215. A wrong actor publishes no partial set. Two focused tests
cover malformed/duplicate/unapplied mappings and atomic examine installation.

Claude's round2 applied the arena in tx175928 (dossier18:5x): region4160,
Keyhole66998, both caches equal. Do NOT rerun the CREATE-only room batch or
the unused-square assertion after this application. The remaining step is the
read-only verifier above, then Claude's --write/runtime/client collision checks.

Production-source PREFLIGHT173926 succeeds:91logical mutations,182CREATE across both caches;
loc proposals66961..67003 (Keyhole34727->66998),model proposals67448..67493,new map groups6946/6947,
43source locs/46models/250placements. Full source list and allocation output:
MIMIC-ARENA-PREFLIGHT.txt. Replan after predecessors; proposals are not reserved.
Both target caches and ledger were unchanged. Model37128/37129/37130/37132/
37134/37135 face z-offsets are not representable in667 and remain an explicit
loaded-client overlap/decal check. The earlier ambient-sound omission is fixed.

Claude client checks: all room walls/floors/decor at wide zoom and steep angle
against OSRS; source plane1, clipping of the whole5x5boss and doorway, two private
instances with no shared NPCs, Exit keyhole path, ambience2124, death/logout/return.
Do not enable an incomplete fight to test the room; use the existing admin
teleport/inspection facilities. Q1 still gates fight acceptance separately.

B read-only verification17-Nov20:34 passed after Claude's application: both
installed250-placement rooms and all6named scenery definitions match, including
identical terrain/loc payloads and per-definition hashes. Durable proof:
validation/mimic-arena-installed.json. No runtime TSV was published by B;
--write and loaded-client collision/render/audio remain Claude's steps.
