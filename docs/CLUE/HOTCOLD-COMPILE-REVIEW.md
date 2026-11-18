# Hot/cold whole-module compile correction,17November

Frozen integration115813 failed with unchanged4605inputs. Core compiled, but
plugin compilation found two top-level TreasureTrailHotCold definitions: the old
pure helper in TreasureTrailChallenges.kt and the new persistent service. The
32 earlier isolated guardian tests did not compile the old helper and therefore
could not catch that declaration conflict. No plugin/whole-branch acceptance is
claimed from that failed integration; its stale/missing XML is not new evidence.

The duplicate pure object is removed. The actual master service now owns the
shared temperature/distance/dig/damage functions. Its real Feel uses the same
damage helper; long arithmetic prevents coordinate subtraction overflow. The
shared challenge test follows the already-pinned master-specific0..4shaking
boundary/radius4, rather than the old generic0..3helper.

Fresh production-source check includes BOTH source files and all six changed
classes:36tests green, including guardian state, hard wizards, master hot/cold,
combat rules and shared hot/cold/sliding/light algorithms. Durable source hashes
and JUnit output:validation/hotcold-duplicate-declaration-fix.json. The combined
Gradle/scripts repetition is still required and will include this challenge
class explicitly. No cache roster, cache bytes, server, client or live changes.
