# Guardian overheads and cabbage reaction

Pinned Wiki Transcript pages/revisions/hashes: GUARDIAN-DIALOGUE-SOURCES.json.
Source6587/6588 each has one exact spawn line. All three Ancient wizard styles
use the three source alternatives; Brassican has four spawn alternatives and
four cabbage reactions containing the account's username.

Spawn text is posted only after the complete guardian group has passed identity,
world, liveness, exact-clue and account checks. An unpublished/rolled-back group
cannot speak through this route. Double agents retain their existing dialogue.
Eating calls the reaction only after successful cabbage removal and Foods.eat.
The mage must belong to the eater's valid clue session, be alive/spawned in that
World and currently target that exact player. Failed eating, banked/replaced
clues, another account, another target, logout and a dead mage cannot trigger it.

The transcript marks the alternatives random without documenting weights. Advice
chosen: use the existing uniform picker for this cosmetic text only. This is an
implementation convention, not evidence of Jagex's hidden text weights. Combat
choice probabilities, stats, rewards, acquisition and source gates are unchanged.

Fresh production-source verification: python tools/clue/verify_guardian_drops.py
--dig-guards,38tests/7classes,all green. This covers source hashes and every
spawn alternative plus personal cabbage reactions, and the six existing
dig/guardian/hotcold/combat/challenge classes. Durable proof:
validation/guardian-dialogue-actual-source.json. Full Gradle/script compilation
follows on frozen production inputs.

Claude's loaded-client check: observe every supported guardian's spawn line,
eat real cabbage during personal Brassican combat, try a second account and
repeat after victory/banking/logout. Q8 still prevents unsupported guardian
visuals from activating; tests do not certify those visuals or their odds.
