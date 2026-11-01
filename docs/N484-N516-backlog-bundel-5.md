# N484 / N516 — ChatGPT-B

Branch: `gpt-b/backlog-bundel-5`. No cache, client, save, live or death-mechanics changes. Conformance remains Claude's test-server check.

## N484 findings

* **POH: stale test.** `wilderness_obelisk.plugin.kts` already binds Activate, Teleport to Destination and Set Destination for all six obelisks, including house loc 14826. `activateObelisk` recognises the POH and passes the fixed destination into `houseObelisk`. A22 added the cache options and corresponding handlers; the test's route inventory only listed Activate.
  The resumed run also exposed N506's `Last-destination` on jewellery box 62926. Existing commit `311b563eb` binds it through `JewelleryBoxRecent.OPTION`; include that existing route in the inventory too. No cache change is needed.
* **HP: false positive in test.** `GeDrinks.chalice` reads the base Constitution level (1:1 HP). At 99 HP its 15%, 5%, 30% heals are 14, 4, 29; damage outcomes are 4 or 49, limited to leave four HP. Dividing a percentage by 100 is not a donor ledger value. The scanner treated every integer >=100 anywhere in a mutation argument as x10. Exclude only an integer percentage divisor of 100, retaining detection of large additive/numerator literals, larger divisors and nested arguments. No GeDrinks allow-list exception or extra HP conversion.
* **Barrows: stale test after N469.** `git log -S 'combineSameItems(items)' -- .../Barrows.kt` identifies `540d8ba42` (2026-10-30). Dossier N469 records the owner's duplicate rune reward report and the deployed correction: identical table rewards combine into one stack; equipment is selected without replacement. N100's 2026-10-18 rarest-first bracket boost remains unchanged. Seven rolls can yield three visible reward rows. Statistical expectations must use seven rolls per chest; key half counts use quantities after merging.

### HP class search

Searched all plugin Kotlin/script sources for `lifepoints`/`Lifepoints`, multiplication/division by 10, and healing/damage mutation calls; also searched HP/health/Constitution percentage formulas and the game core for combined lifepoint/x10 expressions (no additional core matches). Read every `GeDrinks` branch, the consumable ledger boundary, Foods' kebab boundary, potion HP paths, and familiar HP/damage boundaries. Death mechanics were excluded from review/edits as required by the card.

Reviewed matches:

* `GeDrinks`: all five HP outcomes use 1:1 base HP, no x10 multiplier. Damage rechecks the four-HP floor at hit resolution.
* `consumables.plugin.kts`, `Foods`, `PotionType`/`PotionEffects`: consumable/food ledger converted once; Zamorak brew `current / 10 + 2` is 10% +2 HP; Saradomin brew uses 15% +2 HP.
* `FamiliarCombat`, `SummoningSpecialMoves`, `NpcAttacks`, `BulkNpcCombatDefs`: explicit donor ledger conversions; phoenix missing HP converted to the ledger before the shared damage boundary.
* `runetek5.plugin.kts`: existing once-only persisted-unit migration, untouched.
* `EnchantedBolts` blood-forfeit, lunar spell cost, Ape Atoll obstacle, Redemption, PC spinner healing: `/10` means 10%, not a second unit conversion.
* Dharok's `/100.0` factors multiply lost HP by max HP divided by 10,000 (dimensionless damage multiplier).
* NPC DSL `hitpoints = ... * 10` uses the definition ledger, converted by the DSL; old x10 comments in humanoid definitions do not describe the live HP unit.
* Conformance-only HP setup in PvpWeaponsSuite, BreachConformance and CursesSuite: deliberately boosts/lowers test HP, not a player healing formula. Existing parked Fight Caves +100 heal remains the scanner's documented pre-existing exception, outside this card's gameplay scope.

## N516 findings

Report `deadman-20261101-044109/report.txt`: five failures; Howl on a man already PASS. PASS lines include failure diagnostics as their `measured` text, so that text alone must not be interpreted as a failure.

* Removed `::dungeon` cannot exercise a remote gate. Use the existing `::pestcontrolnovice` packet route, assert the exact novice boarding tile and lobby enrollment, and remove the bot from its lobby during reset/cleanup.
* That route exposed a real code gap: `boardRemote` checked only `RemoteAccess.refuse` before moving to the outpost. Tele Block was checked at later game departure. All three tiers now use `RemoteAccess.travel` before the initial move, sharing the normal teleport checks/countdown and completion recheck.
* Familiar command and scroll routes already call `Combat.canEngage`, which checks the credited owner's guard and PvP rules before targeting. The scenario incorrectly treated **any** familiar ATTACK_DELAY as fighting the newly requested target. Source analysis shows the earlier control man can keep retaliating and owner assist can continue the old fight; this explanation still requires Claude's conformance confirmation. Isolate fights, clear pending hits/retaliation state, check command acceptance immediately and track only the requested target. Keep the strict no-start/no-hit expectations.

## Sources

* Owner decisions: dossiers N100 and N469; task card's guard rule, A9 familiar single-combat policy already documented in code; D1–D20 untouched.
* [OSRS Wiki Guard (Deadman Mode)](https://oldschool.runescape.wiki/w/Guard_%28Deadman_Mode%29): guards only attackable inside the guarded area while skulled (retrieved 2026-11-01 via search; direct page blocked).
* [OSRS Wiki Chest (Barrows)](https://oldschool.runescape.wiki/w/Chest_%28Barrows%29): seven rolls at six brothers and the equipment uniqueness rule (retrieved 2026-11-01 via search). Owner dossier N469 determines the already deployed merged-row behaviour.
* Existing Void-derived Barrows/DropBoost and familiar routes; existing N11 poison-chalice source table and enumerated tests in `docs/N11-bundle-5.md`. No new donor mechanics invented.

## Claude checks

Run affected test packages and `conform deadman` on the test server. In particular: all three remote PC tiers from death zone/safe zone/Tele Block; clean lobby after the scenario; familiar guard without/with skull inside/outside the guarded area; players at gap 14/15 and 138 vs84; no target assignment on rejection; Howl on man and woman (not a guard), one scroll/points deduction per valid cast. POH all three obelisk options; Barrows seven rolls with merged rows and unchanged bracket boost; poison chalice percentages and four-HP floor. No test server/client was started for this card.

## Validation

The interrupted session preserved the three original failing N484 XML reports under `docs/N484-N516-evidence/baseline-*.xml`. The resumed first combined run found four failures: jewellery-box inventory and three new familiar assertions caused by relaxed NPC mocks returning `isSpawned() == false`. Explicit spawned stubs corrected the fixtures; the actual combat gate remains active. Targeted POH + familiar command + scroll classes then passed (57 seconds).

Final combined run: **BUILD SUCCESSFUL**, 43 tests, 0 failures/errors (1m22s, 2026-11-01 11:13 CET). `:game:plugins:compileKotlin :game:plugins:test` with filters `*PohObjectRouteTests`, `*MixedLifepointUnitTests`, `*AnnihilationRatesTests`, `*FoodAndDrinkLifepointUnitTests`, `*FamiliarAttackGateTests`, `*SummoningSpecialMoveTests`, `*PestControlRemoteRouteTests`, all with `--priority low`. Final XML reports are retained as `green-TEST-*.xml` alongside the baselines. Package runs and server conformance are deliberately left for Claude as required by the card.

`Rsps-Lint.ps1 -Changed`, with its hardcoded root changed only in memory to this worktree: 2 production files, 0 findings. `git diff --check`: clean. Read-only Doctor returned 14 OK / 2 WARN / 0 FAIL on the shared host (main checkout jar behind unrelated sources; existing crash-log entries). Its cache hashes could not be read because of existing process locks; its printed cache-equality result is therefore not evidence of equality. No processes were started or stopped by Doctor.
