# CLUE continuation, 16 November 2026

integration-20261116-162314 passed **202 tests in 50 classes** (14 core, 36 plugins),
0 failures/errors/skips/missing; all 4539 source hashes unchanged. Core, plugins
and every plugin script compiled. Duration 32m59. This accepts fff1c00b2 and its
preceding map/Uri/tree-puzzle work; the interrupted integration155637 stays invalid.
Proof: build/clue/integration-20261116-162314/summary.json and exact command.json.
The accepted summary, command, source commit and unchanged input-manifest hash
are also preserved in validation/integration-20261116-162314.json.

## Guardian state and source review

Nine staged tests passed, durable source checkpoint a05083196. No guardian steps
activated; GUARDIAN-SOURCE-AUDIT.md lists the combat/PvP/loot and source gaps.
This is separate from the 202-test baseline.

The current source ledger has 69 total records (15 easy, 15 medium, 15 hard,
12 elite, 12 master), including three components of one master step. This is
67 possible trail choices before subtype/asset gates, not proof of approximately
25 active choices per tier or 70 additions beyond the original pool. Keep the
owner's count requirement open when completing/reconciling the final step pool.

Three native guardian bulk records corrected against pinned Wiki values:
5144 strength 3->0; 5145 strength 44->40; 1264 strength 44->40 and poison 4->6.
Exact source hashes and before/after values are in GUARDIAN-STAT-DELTAS.json.
New narrow tests exercise these records through the real BulkNpcCombatDefs builder.
Existing animations and unfinished caster attack scripts are not accepted here.

The existing shop/log chain was also checked: StoreUi records purchases with
source "store", and CollectionLog.record includes all source pages for that
route. Shop-only kits therefore remain obtainable for their full clue log pages;
no new shop price/layout or logging rule was introduced.

## Mimic spawn rollback

Six staged tests passed, source checkpoint 9af14ae9c, now promoted into runtime.
Entry also guards recursive spawn hooks and already dead players; the additional
regression is in the next targeted Gradle run. No failed passive spawn spends an
attempt; a refused fighting spawn retains the passive actor.
Candy/minions/forward-step/source gates remain open.

## Removed donor completion

Removed the unreferenced 288-line donor step file, generic map/emote completion
fallback and onAttempt API. Player actions use the source subtype handlers.
Transaction fixtures now call the real advance transaction directly; a new
regression rejects dig/emote completion from map metadata alone.
The rebuilt plugin JAR was inspected: MapClueStep, EmoteClueStep,
MapClueLocation, EmoteClueLocation and TreasureTrailStepsKt are absent.

Fifteen freshly compiled lifecycle/contract/torn tests passed. Initial two
failures were missing MimicRecovery in the small runner's classpath; adding the
compiled actual module resolved both without a gameplay change.

## Cache handoff

Combined 247-item PREFLIGHT tx-20261116-165714 passed with 438 definitions,
proposed IDs 24940..25377. Both caches/ledger/roster/runtime mapping unchanged;
proof: item-preflight-current/read-only-proof.json; rows: ITEM-PREFLIGHT.tsv.
These IDs are proposals, not reservations. Master 19835/19836 currently propose
25192/25193; exact hard tree boxes 19887/23173 propose 25197/25294. Claude reruns
after preceding cache batches and applies the reviewed Gold/onyx presentation.

Current 10-NPC clue-steps PREFLIGHT tx-20261116-170726 also passed: 168 logical
mutations, 70 source asset records, proposals 15314..15323. Both caches, ledger,
runtime NPC mapping and profile hashes unchanged. NPC-PREFLIGHT.tsv lists every
source/NPC/model/head/sequence mapping, including source306 Lumbridge Guide.
Both complete read-only before/after proofs are preserved under validation/.

## Continuation gate

integration-20261116-170010: 4541 frozen inputs, 14 explicitly filtered plugin
classes, no core test task. The helper never emits an unfiltered test task for
an empty module. Accepted: 84 tests/14 classes, no failures/errors/skips/missing, all4541 input
hashes identical. Core/plugins/all scripts compiled; 31m29. Durable evidence:
validation/integration-20261116-170010.json. This validates the donor cleanup,
Mimic spawn rollback and three native combat-stat corrections. Full gameplay
acceptance still requires the open source/asset/client gates. No apply/server/client/live/push.

Worktree-scoped Rsps-Lint -Changed: one modified gameplay source, zero findings.
The lint script was copied under build/clue and its fixed canonical root was
redirected to this worktree. Doctor was not run: its fixed canonical checkout
and crash-log inspection are outside this task card's permitted checks.
