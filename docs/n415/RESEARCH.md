# N415: source comparison and implementation contract

Read on 2026-11-14 from OSRS Wiki raw pages (`?action=raw`). Source hashes are in source-digests.json. Donors were not used as OSRS behavioural evidence. Code, cache, client and live gates are separate.

## Mage Arena I

Sources: https://oldschool.runescape.wiki/w/Mage_Arena_I , https://oldschool.runescape.wiki/w/Kolodion , https://oldschool.runescape.wiki/w/Transcript:Kolodion .

Requires unboosted 60 Magic. Five forms: human, ogre, spider, ghost, demon. All attack using god magic (20 maximum); only Magic harms them. Attack delays 7/7/7/7/4 ticks. HP 3/65/78/78/107; Attack 15/55/69/28/85; Strength 10/70/78/23/98; Defence 20/72/47/58/105; Magic 15/50/70/30/400; Ranged 1. A player can leave and resume at the same form. Normal Wilderness PvP remains possible. Winning returns the player to Mage Bank. Multiple capes, including different gods, may be collected from the statues. First matching staff is free; further staves cost 80,000 coins. Each god spell requires 100 casts inside the arena before use outside; splashes count.

Verified local paths: kolodion.plugin.kts, mage_arena.plugin.kts, chamber_guardian.plugin.kts, spawns_12349.plugin.kts, Combat.canEngage, MagicCombatStrategy, MagicCombatFormula, NpcCombatDsl.

Corrections to initial preflight: a shared Kolodion 907 IS spawned at 3105,3933; a missing start spawn alone is not the root cause. NpcCombatDsl deliberately divides authored HP by TEN. Thus 650 means 65 real HP; changing it to 65 would be wrong. Actual defects: no owner/progression, no 60 Magic gate, reusable global final kill grants completion, forms 3/4 are 667 troll/dark beast rather than OSRS spider/ghost, form 3 HP 65 instead of 78, wrong combat stats and melee fallback in later forms, huge defence instead of an explicit magic-only damage gate, bones on first form, statue forbids other gods, first staff consumed on full inventory or without a cape, no god-spell training state.

Local NPCs (read-only cache): 907 human; 908 ogre; 11301 troll; 11302 dark beast; 911 demon. 909 is not a usable spider; 910 is Lava-flow miner. Do NOT reuse OSRS ids in 667. OSRS forms 1604/1605,1606,1607,1608,1609 need a verified import and rig-matching combat sequences. These visual gaps stay open until Claude applies the cache transaction.

## Mage Arena II

Sources: https://oldschool.runescape.wiki/w/Mage_Arena_II , https://oldschool.runescape.wiki/w/Transcript:Mage_Arena_II , https://oldschool.runescape.wiki/w/Enchanted_symbol , https://oldschool.runescape.wiki/w/Map:God_followers_spawn_locations .

Full miniquest, not a short-quest choice. Requires MA1, unboosted 75 Magic, all three trained god spells. Existing list slot193/varp1918 is always auto-completed; replacing it requires independent earned state and a cache progress-case change (currently complete at1). Existing players' auto-completed varp is NOT proof they earned MA2. Legacy migration is an owner decision; advice: use an independent earned flag and preserve already owned equipment.

Symbol21800 (upstream): Activate/Destroy, one owned, free replacement at Kolodion, above-ground Wilderness only. Random self-damage up to16 can kill. Prompt filters handed-in remains; one choice activates directly; after completion filters currently held remains. Individual player locations rotate after about45 minutes spent inside Wilderness only. Wiki map circles are candidate areas with radius5, not exact validated walkable spawn tiles. Do NOT silently turn approximate centres into exact spawn tiles. Need collision validation and exact heat/distance message thresholds before activating tracking.

Three remains may be delivered separately and cannot be banked. Losing an unhanded-in drop requires another kill. All three complete the miniquest and fund the first imbue of a normal god cape; later capes need corresponding remains or Lost Property purchase for250,000. The symbol page mentions early individual imbues before the final hand-in: reconcile with transcript/reward account before implementing. Duplicate remains do not produce multiple first-imbue credits.

Regular guardians must have separate imported NPC ids from Breach14446/14447/14448. Source ids: Zachariah5977 spawn/7858 combat; Derwen7513 spawn/7859 combat; Porazdir7515 spawn/7860 combat; Guthix healer7514. Each regular boss has320HP, 6-tick speed, +200 stab/slash/crush/ranged defence and -60 magic defence. Porazdir: Attack250/Strength150/Defence100/Magic180/Ranged1, magic attack80/damage80. Zachariah:500/250/100/180/1, melee attack200, magic attack80/damage80. Derwen:250/150/100/80/1, magic attack180/damage80 (NOT Breach Magic180/attack80). Poison/venom immune. Magic/melee maxima43/16; Zachariah26/43. Only matching god spell hurts each; recoil exception needs verification. Porazdir counts as black demon. XP bonuses Porazdir/Derwen20%, Zachariah55%.

Common mechanics: dodge Ice Barrage by leaving cast tile; Tele Block expires on boss death, Protect from Magic duration one minute. Pages do not source exact selection chances, unprotected TB duration, cast/impact delays, prayer reduction factors or timeout. Keep open, not copied from Breach.

Porazdir: typeless energy ball, maximum30 nearby, zero at>=12tiles. Exact distance formula, probability, cooldown and delay unsourced. Zachariah: blue wave targets cast tile; moving one tile avoids it, otherwise drags into melee and binds. Bind duration/collision/timing unsourced. Derwen:20HP ground balls heal5 every few seconds, any style can kill them. Exact period, count/chance/spawn/expiry unsourced. Need additional OSRS recordings/script evidence before inventing timings.

## Purple Zaros cape

Sources: https://oldschool.runescape.wiki/w/Imbued_ancient_cape , https://oldschool.runescape.wiki/w/Ancient_fabric , https://oldschool.runescape.wiki/w/Imbued_ancient_max_cape , https://oldschool.runescape.wiki/w/Update:Pete_Kayer%27s_Challenges . Released7 October2026. Exact name: Imbued ancient cape. Cosmetic, same imbued stats: +15 magic attack/defence, +3 stab/slash/crush defence, +2% magic damage, 0 other bonuses;75Magic. Ancient fabric from Pete Kayer's final challenge irreversibly converts any imbued god cape. Repeat fabrics from Pete's crate after unlock. NO Pete boss/NPC in this task.

Owner authorizes MA2 acquisition. Its repeat/cost condition is not specified by OSRS because this is an alternate acquisition route. Advice: unlock repeat fabric at Kolodion after earning MA2, preserve fabric+imbued-cape recipe. No invented fourth guardian/heart. Record owner decision.

Upstream: cape34633/broken34635/locked34637; darker34639 exists in gameval, availability unverified. Fabric34641; ancient max34625/broken34629; hood needs cache verification. No local ids allocated here. Cape PvP repair96,000; ancient max99,000 (local D1-D20/N522 decisions take precedence). Max+hood+ancient recipe; fabric also converts existing three imbued max capes with matching hood. Knife splits ancient max into max+ancient; fabric never restores the old god colour. Register Zaros GWD protection and all lock/broken/mangled/Trouver/perk consumers after allocation.

CACHE BLOCKER: pinned2686 provenance is2026-09-02/build240. Read-only Osrs.get(34633)/get(34641) return None. They postdate this cache. Do not replace pinned source or invent models/ids. Claude needs an isolated post2026-10-07 source for this import only (existing RSPS_OSRS_SOURCE_CACHE override), provenance/hash and CacheTransaction. Existing2686 remains default elsewhere.

## Integration gates

Drop Viewer: regular-guardian remains on their own tables; no quest remains on Breach. Collection Log: a cape reward does not automatically imply a slot; official announcement proposed excluding cosmetic. Confirm current log before creating slots. Quest tab: MA2 currently fake-complete; MA1 has no row and needs a cache row allocated by Claude. Map/minimap: existing MA1 location; do not invent permanent boss markers for roaming MA2 guardians. Import NPC minimap flags/quest icons. Examines/options from Wiki/cache only.

Use generic PvP/death/Perdu/Trouver and N522 wildy behaviour; no safe-death shortcut. Remains/symbol destruction and unbankable metadata need explicit integration. Shops/GE untradeability; no changes under data/ge. Three existing imbued max recipes work once component capes become obtainable. Donator rule concerns unobtainable real capes; MA2 makes these obtainable, but removal/pricing/refunds of existing offers is an owner decision. Do not silently reprice/remove cosmetics.

## Work order

1. Source inventory and cache batches, no cache execution.
2. MA1 ownership/progression, level gate, stats/magic-only, statue and atomic first-staff reward; targeted tests/commit.
3. Full MA2 earned-state, journal/transcript, symbol/hand-in/reward transactions after mapping and migration decision.
4. Separate regular-boss scripts once unresolved timing/asset evidence arrives; B1-B30 each; preserve Breach.
5. Isolated modern cape import and all death/max/perk/affiliation consumers.
6. Claude cache preflight/apply, two-player screenshots/audio/death tests, release and A9. No AF before all gates.
