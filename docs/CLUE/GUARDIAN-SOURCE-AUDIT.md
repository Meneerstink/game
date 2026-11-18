# Guardian continuation, 16 November 2026

The selected pool contains eleven guarded source steps: five hard/master emotes,
two hard coordinates, two elite coordinates and two master coordinates.
`data/cfg/clue/guardians.json` already pins fourteen Wiki/cache NPC profiles.
This audit does not activate these steps or accept their combat/loot chains.

Source rules checked against the pinned `Double_agent.wiki`,
`Treasure_Trails__Guide__Coordinates.wiki` and tier coordinate pages:

* Medium coordinates have no wizard. Elite emotes have Uri without an agent.
* Hard emotes use source1778 outside Wilderness/source1777 inside; master uses7312.
  The first emote step in a trail has no double agent (Double agent, Trivia).
* Changing into combat gear after summoning is allowed. After victory, Uri appears
  immediately only in clue gear; otherwise re-equip and perform the emote again.
* Coordinate guardians must die before another correct dig can award the clue.
  A master encounter with three wizards requires all three deaths.
* The Wiki's 10 September2015 change explicitly gives PvP priority over treasure
  trail combat NPCs in Wilderness singles. The encounter adapter still needs this
  shared-combat regression; do not assume the existing PJ timer implements it.

Current bulk data is not a source-accurate combat adapter. Examples:
local5144 has strength bonus3 instead of0; local5145 has44 instead of40;
local1264 has44 instead of40 and poison4 instead of6. Existing attack/death
animation rows cite2009scape, which is code assistance rather than the owner's
required OSRS/cache behaviour evidence. No new sequence ID is inferred here.

`NEXT-GUARDIANS/` preserves the tested fight-state controller and nine narrow
tests while the main Gradle inputs stay frozen. It binds each fight to the real
held scroll/account/step/progress, prevents repeat spawns and replay deaths,
requires every group member, and separates victory from the next dig/Uri action.
It persists victory using existing CLUE_TASK fields, scoped to step and progress;
it introduces no new item-attribute ordinal. A live fight is removed on bank,
replacement, death, logout or a real despawn. A corpse remains with the ordinary
death handler, so cleanup does not swallow its loot/death sequence.

The controller's spawn callback is not a gameplay activation. A reviewed adapter
must still supply source/native actor mappings, verified combat, atomic spawn
rollback, exact loot/Drop Viewer and ownership/PvP hooks. Elite guard selection
has no guessed probability; the adapter must supply a sourced choice. Hot/cold
needs its source target selection and device/Jorral route as well.
Before wiring hot/cold, separate the guardian victory attributes from its target
selection: that target also needs CLUE_TASK_KEY. Append dedicated guardian fields
without changing existing ItemAttribute ordinals, then clear them with step changes.
The checkpoint is a coordinate/emote controller, not that combined integration.

Nine staged tests passed after two harness/fixture corrections: add kotlin-reflect
to the small runner's classpath, and capture the actor's actual World before a
MockK verification (rather than recording a chained Player.world lookup).
The main frozen Gradle run does not include this new controller yet.

Mimic follow-up from Claude17:1x/17:2x: videoFRohVjhXiaA supports approximately
four ticks from candy-throw animation to impact. Observed throws39/52/75/85s do
not establish a fixed interval or a per-attack probability. Keep both the candy
choice chance and forward-step chance unresolved. The current encounter only
has melee/stomp; importing its arena alone must not be reported as full Mimic.

## Promoted continuation, B16-Nov21:54

The controller and its twelve narrow tests are now production source files.
The backend is not an activation switch: combat/loot/PvP adapters remain open.
Guardian victory now uses appended CLUE_GUARD_KEY/CLUE_GUARD_PROGRESS fields,
leaving Falo/Sherlock and future hot/cold targets untouched. Existing enum
ordinals are preserved. The manager clears both fields only on real step changes
or donor-key migration; drop/bank/serialization retain them.
Reentrant spawning is refused; callback validation rejects bank/death/logout,
foreign-world/dead/despawned actors, and removes unique failed actors once.
The latest actual-source basic cohort passed117tests including these12 and the
updated lifecycle tests. Separate Gradle/scripts acceptance still required.

## Continuation 17-Nov: PvP priority

`ClueGuardianPvp` now connects the real guardian session identities to the
shared `PawnExt.isAttackedByOtherThan` path. Only an actual guardian belonging
to the player yields to a player (or that player's familiar) in Wilderness
singles; a same-name NPC or another account's guardian is not exempted.
A separate player reservation survives subsequent wizard hits, clue cleanup,
and a projectile already in flight from a third account. It uses the existing
17-tick PJ duration, expires with a dead/offline opponent, and does not impose
reservations on multi areas. Familiar impacts consult the same player rule.

The freshly compiled actual-source run passed19 tests in one JVM: five new
ClueGuardianPvpTests, twelve Guardian controller tests, and the two existing
SinglesPlus regressions (ordinary NPCs/bosses/third attackers). Initial small
compiler invocation needed the existing plugin friend-path for AncientCurses;
no production change was needed for that harness failure. Inputs were frozen
and recorded in validation/guardian-pvp-actual-source.json. This completes the
PvP adapter component; guardian combat/spawn/loot and Gradle acceptance remain.

## Double-agent runtime, B17-Nov03:10

TreasureTrailAgents now joins the existing controller to emote dispatch, the
ordinary melee strategy, personal targeting, death and Uri. All three sourced
profiles retain real HP80/120/160, speed4, Wiki stats/bonuses and XP0/2.5/5%.
Hard Wilderness uses source1777/local5144; other hard uses1778/local5145;
78 uses7312 via the existing ten-NPC import batch, never a guessed local ID.
The first trail step posts Uri without an agent. Later steps require that exact
agent to die; combat gear is allowed, and clue gear at death posts Uri immediately
even after the player moved from the original emote square. Otherwise the
original emote/gear/tile must be repeated. Killing never advances by itself.
Bones are guaranteed through the ordinary DropTableFactory/Drop Viewer path;
owner N424/N561 excludes looting bags (HOOFDLIJST notes they are out of game).
Personal actors reject another player or familiar, and never acquire a stranger
as an aggro target. Existing same-id ordinary NPCs remain ordinary combatants.
Cleanup removes living actors on stale scroll/bank/logout/death; normal death
animation/drop processing owns corpses. Loot settlement is repeat-safe.

The native human animation roles are RuneLite gameval HUMAN_UNARMEDPUNCH422,
HUMAN_UNARMEDBLOCK424 and HUMAN_DEATH836. Attack/death agree with the existing
native rows. Client visual/audio proof remains required; no claim of a newly
measured block animation or OSRS spawn/vanish graphic is made.

31 freshly compiled tests passed in one JVM: PvP5, controller12, existing
SinglesPlus2, agent5 and existing emotes7. Source/input proof is preserved in
validation/agent-runtime-actual-source.json. A fixture initially failed because
master scroll had no applied item mapping; its test-only mapping now models
Claude's import instead of creating an Item(-1). The failed run is not accepted.
The small runner also now includes the current plugin jar when Gradle snapshots
move loose classes. Full Gradle/script acceptance follows on frozen inputs.
Coordinate wizard combat/loot and hot/cold remain separate open work.

## Accepted Gradle integration, B17-Nov03:44

integration-20261117-021033 passed75tests in14fresh classes, no failures/errors/
skips/missing;4563inputs unchanged. Core/plugins/all scripts compile;
BUILD SUCCESSFUL32m47s. Durable proof validation/integration-20261117-021033.json.
This accepts the double-agent component and previous fifteen unique additions;
it does not accept still-open coordinate combat/lightbox/hotcold/Mimic/client.
