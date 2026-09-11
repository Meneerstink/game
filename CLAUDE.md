# RSPS — Permanent Engineering Rules

## Project identity

Project root:
C:\RSPS

Repository root:
C:\RSPS\game\game

The project remains a revision 667 / late-2011 pre-EoC RuneScape server/client.

Preserve the revision-667 architecture and identity.
Do not migrate the project to 718, 742, EoC, RS3, or another revision as a shortcut.

## Authority and current truth

Different sources have different authority.

For CURRENT IMPLEMENTATION STATE:
1. real filesystem;
2. real game-server cache;
3. real file-server cache;
4. actual test/build/runtime results;
5. RSPS_CURRENT_SPRINT.json and durable status documents.

For OWNER-APPROVED PRODUCT SCOPE AND DESIGN:
1. RSPS_DEFINITIEF_MASTERPLAN.md;
2. explicit owner decisions in RSPS_DECISIONS.md.

When a later explicit owner decision clearly changes an older master-plan decision,
the newer explicit owner decision takes precedence for that specific point.

For MODERN IMPORT PROVENANCE:
1. RSPS_IMPORT_MANIFEST.yml;
2. RSPS_IMPORT_ASSET_MAP.yml.

Never treat stale documentation or sprint state as stronger evidence than current
filesystem/cache/test/runtime state.

Never treat missing implementation as cancellation of an owner-approved requirement.

## Session orientation

For development work, start with:

C:\RSPS\RSPS_CURRENT_SPRINT.json

Then retrieve only information directly relevant to the active task from:

C:\RSPS\RSPS_IMPORT_MANIFEST.yml
C:\RSPS\RSPS_IMPORT_ASSET_MAP.yml
C:\RSPS\RSPS_WORK_STATUS.md
C:\RSPS\RSPS_CODEBASE_MAP.md
C:\RSPS\RSPS_DECISIONS.md
C:\RSPS\RSPS_DEFINITIEF_MASTERPLAN.md

Use targeted rg/search, known paths, symbols, headings, and narrow reads.

Do not fully reread large project documents unless the current task genuinely requires it.

Do not reread RSPS_AUTONOMOUS_LOG.md except to investigate one specific historical incident.

Do not broadly scan or summarize the repository when targeted inspection is sufficient.

Do not rediscover architecture already recorded with adequate evidence unless new evidence conflicts with it.

## Worktree safety and Git usage

Local Git tracking is enabled for this repository (owner decision, 2026-09-11), used as a general
local change-tracking tool, not a restriction. It replaces the old ad-hoc robocopy snapshot habit
for this repo.

Allowed and expected:
- `git status -s` / `git diff -- <specific-file>` first, for token-efficient inspection.
- `git add <specific paths>` and a local checkpoint commit once per completed batch, when useful.
- Committing files this repo's own work actually changed (donor dirs are separate, read-only repos
  and are never touched from here).

Never run without the owner's explicit approval:
- `git reset` / `git reset --hard`
- `git clean`
- `git stash`
- broad `git checkout` / broad `git restore`
- broad `git revert` or history rewrites
- `git push` / `git pull` / `git fetch` (no remote operations at all — local-only)
- `git add .` / `git add -A` blindly without reviewing what is staged

Do not delete unrelated untracked files. Do not overwrite unrelated work. Do not perform repository
hygiene during unrelated development work. Do not stop unrelated development processes or Gradle
daemons without a demonstrated need.

Avoid dumping a large unrestricted `git diff` into context — use targeted `git diff -- <path>` or
`git diff --stat` instead.

Other writable codebases the workflow may need to modify (client, file-server, router, harness) may
each get their own separate local Git repo when work there is actually needed — do not turn all of
`C:\RSPS` into one giant repo.

## Scope discipline

Implement only the active task and dependencies genuinely required for it.

Avoid:
- opportunistic refactors;
- unrelated cleanup;
- speculative abstractions;
- universal frameworks for hypothetical future requirements;
- broad rewrites;
- duplicate subsystems where an existing architecture can be extended safely.

Prefer the smallest reusable architecture-compatible solution that satisfies the proven requirement.

## Writer discipline

The main agent is the exclusive writer for:
- source code;
- production caches;
- manifests;
- sprint state;
- durable project documentation.

Do not create agent teams.

Use subagents only when an investigation is genuinely independent, read-only, context-heavy, and meaningfully reduces main-agent context usage.

Parallelize independent read-only investigation when useful.

Keep dependent operations and production mutations sequential where ordering affects correctness.

## Cache safety

Production cache mutations must use the safest currently approved transactional path.

If the transactional path has a known unresolved correctness or recovery defect,
repair and verify that defect before performing further production cache mutations
that depend on it.

Do not silently perform production writes through legacy single-target tools when
that could create divergence between:

C:\RSPS\game\game\data\cache

and:

C:\RSPS\file-server\cache

A partial, uncertain, or recovery-required production cache transaction has higher priority than new content work.

Do not begin another production import while an earlier production transaction is unresolved.

Never silently overwrite unknown existing cache content.

Preserve baseline backups and transaction evidence.

Do not stage or commit cache backup binaries.

## Source and provenance discipline

Never guess mandatory:
- IDs;
- stats;
- formulas;
- requirements;
- mechanics;
- animations;
- GFX;
- projectiles;
- messages;
- cache mappings;
- provenance.

For modern binary/cache assets, use the pinned reproducible Jagex-sourced cache selected for the active import batch.

For gameplay mechanics and combat-critical values, use the project's established source hierarchy and cross-check where risk requires it.

When credible sources conflict, record SOURCE_CONFLICT with the conflicting evidence.

Block only the conflicted field or mechanic when independent safe work remains.

Distinguish factual source data from deliberate provisional balance values.

## Engineering and verification lifecycle

Use this lifecycle where applicable:

IMPLEMENT
-> targeted verification
-> targeted automated tests
-> reread real filesystem/cache state
-> broader regression testing
-> runtime/server/client validation
-> durable status update

A task or gate may be marked PASSED only when every required acceptance criterion has concrete evidence.

Evidence must identify what was actually demonstrated, such as:
- exact test/task executed;
- actual pass result;
- decoded cache state;
- matching hashes;
- server boot;
- client load;
- runtime behavior.

Never claim a test passed unless it was actually run.

Never claim server, client, visual, or in-game verification unless it actually occurred.

Manual-only validation must remain explicitly pending rather than being represented as VERIFIED.

Do not weaken, delete, bypass, or rewrite a valid test merely to make an implementation pass.

## Failure handling

Keep blockers as narrow as possible.

When one narrow blocker appears:
1. record the blocker accurately;
2. preserve the current safe state;
3. continue independent approved work when possible.

Do not stop merely because:
- research is required;
- a modern asset is absent from revision 667;
- a feature is difficult;
- a later approved phase must be entered;
- manual visual validation is pending.

Do not hide an unresolved correctness problem behind DONE or PASSED.

## Context efficiency

Treat context as a limited engineering resource.

Prefer:
- targeted reads;
- compact structured state;
- direct known paths;
- reusable durable findings;
- concise milestone reporting.

Avoid:
- repeated architecture discovery;
- unnecessary repository summaries;
- repeated full-document reads;
- unnecessary shell output;
- large chat reports;
- repeated research of already-proven facts.

Reason to the depth required by the task, but do not reopen settled decisions without contradictory evidence.

When context or usable session capacity is becoming constrained:
- do not begin a new large atomic operation;
- finish or safely recover the current atomic operation;
- run the required minimum verification;
- persist accurate sprint state;
- record the exact next action for a fresh session.

## Autonomy

After minimal orientation, execute the active approved task.

Do not stop to ask for approval for ordinary safe, reversible engineering decisions already covered by the master plan and permanent owner authorization.

Choose the smallest correct architecture-compatible solution when a normal engineering decision is required.

Do not ask whether to continue when approved dependency-safe work remains.

Keep user-facing progress output concise.

Put durable technical evidence in the project state files rather than lengthy chat narration.

## Temporary files

Create scratch/helper files only when they materially help the task.

Clean up only temporary files that were created by the current work and are known to be disposable.

Never delete unfamiliar or pre-existing files as cleanup.
