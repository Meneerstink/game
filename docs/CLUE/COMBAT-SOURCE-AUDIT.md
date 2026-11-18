# Dig guardians and Mimic continuation, 17 November

Source authority remains pinned OSRS Wiki pages/manifest and cache2686.
No donor combat numbers, random weights, projectile geometry or IDs were copied.
The source numbers cover all14guardian/minion profiles. `ClueGuardianProfiles`
converts actual HP once through the existing DSL, retaining XP, styles and bonuses.

The production fight controller now supports the saved hot/cold site as well as
fixed coordinate/emote sites. The Ancient trio must all die for this exact
scroll/account/progress; only a second valid dig advances. The saved target is
independent of guardian victory. Spawn publication validates the whole group and
rolls back a partial allocation. Banking, dropping/replacing the real clue,
death and logout cannot credit a later stale kill.

`ClueGuardianCombat` provides exact attack maxima and the special source rules:
Brassican uses ordinary magical accuracy against magic defence and bypasses
both Protect and Deflect; Ancient melee's eight poison uses the existing
accurate-impact hook, including a prayer-blocked accurate hit. The flying guard
permits halberd melee. Its name was removed from Krystilia's Aviansie alias;
the Wiki explicitly excludes that guardian from Aviansie Slayer credit.

## Q8: visual roles and elite guardian selection

The source cache identifies each rig/equipped model, not its server-side attack
script. Human weapon/casting roles, ork combat and aviansie sword combat have
source IDs in gameval AnimationID. Importing those candidates does not prove
that a particular guardian uses them. The source Wiki image
https://oldschool.runescape.wiki/images/Armadylean_guard.png?b0ab8
shows two blades, making GODWARS_ARMADYL_SWORD_ATTACK6957 the candidate to compare;
this is an inference, not an observed attack. The mage and ranged projectile
identities/timing likewise require primary visual evidence. No guessed projectile
is substituted from the NPC's max hit or drop-table ammunition.

Advice chosen: preserve every complete backend and prepare the candidate assets,
but require a verified per-source visual row before installing the unfinished
combat types. No 50/50 elite guardian choice is claimed: that source probability
is still unknown. `TreasureTrailDigGuards.dig` takes a sourced choice callback;
without one an alternative-choice step stays outside selection. Hard native
coordinate wizards and already accepted double agents remain implemented.
This is an explicit source gate, not a request to delay other cache batches.

`ClueGuardianCombat.visuals` reads optional cfg/clue/combat-visuals.json. Each row
needs `verified:true`, source sequence IDs `attack`, `block`, `death`, and the
verified local `NpcAttacks` `projectiles`, `casting`, `impact` records. Source
sequence identities resolve only through the applied dual-cache mapping. Missing
rows or assets do not activate placeholders. There is deliberately no production
file that silently certifies the above candidates.

## Supplemental import (no NPC/item/UI roster change)

OsrsFxImportTool batch `clue-guard-combat`, source sequences:
376,378,390,424,426,427,727,836,1162,1167,4320,4321,4322,6957,6958,6959.
Read-only PREFLIGHT tx-20261117-082149 succeeded,294logical mutations;
both caches and ledger remained unchanged. Exact output COMBAT-PREFLIGHT.txt;
validation/combat-preflight-read-only.json preserves the before/after proof.
Proposed IDs are not reservations. Claude reruns after predecessors and applies.
`python tools/clue/build_combat_mapping.py --write` publishes only this
worktree's TSV, after all16applied ledger identities match both caches. B did
not run --write and never applies the cache batch.

## Mimic known mechanics versus owner Q1

The six-candy transaction now runs against actual source session/boss/token,
keeps three purple and one red/blue/green, publishes delayed effects once,
damages only the actual landing tile, and replaces only dead/missing colours
at the next volley. Cancellation during a visual/spawn callback stops the rest;
a just-spawned unpublished actor is removed. Victory/exit cancel pending effects.
Minions use the same exact source stat/combat adapter and are personal to the
encounter. There is no minion loot or independent automatic respawn. The instance
is marked multi for simultaneous boss/minion combat and removes only marks it
added when leaving. Freeze and full5x5collision constrain the one-tile advance.

The custom combat loop owns the3tick attack cycle; the world tick independently
applies standing-under damage1,1,1,2,2,2,... capped6. The candy projectile and
impact plumbing accepts a supplied measured launch plan and4tick deadline.
Synthetic test geometry/weights are explicitly not OSRS constants. The Wiki's
approximately10candy damage is implemented as10 pending exact visual acceptance.

Owner Q1 remains binding: ENCOUNTER_IMPLEMENTED=false; unknown candy/forward
choice chances remain null. The landing-offset pattern and projectile launch
height/slope/release values are also explicit null fields rather than fabricated
defaults. `ready(world)` additionally requires the complete plan/minion visuals
and existing applied candy assets. Conversion retains the ordinary casket until
ready. These remaining primary-source measurements belong in the handoff;
no standalone/JUnit/Gradle success constitutes full Mimic or live acceptance.
