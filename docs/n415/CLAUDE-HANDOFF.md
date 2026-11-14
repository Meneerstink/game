# N415: review and continuation

This is a partial implementation. MA2 remains unimplemented; the existing completed placeholder is untouched until the earned-state/migration decision. No target cache, server, client, VPS, lists or player saves were changed. No push/merge/release was attempted.

## Changes to review

- MageArena.kt and the three wilderness scripts: owner-bound Kolodion duel, saved form progress, explicit launch/impact-style immunity, 60 base Magic, corrected source stats, magic fallback and retained three-spell data-driven attacks in every form, no ordinary form respawn/bones table. Final completion is restricted to the current owner's current duel; stale callbacks cannot advance again.
- spawns_12349.plugin.kts: removed the shared, farmable Kolodion907. Duel actors now originate from the dialogue landing queue.
- Statues permit repeated capes from different gods; cape ground spawns belong to the praying player. Guardian's first reward is atomic, requires MA1+cape, preserves entitlement on full inventory and gives exactly one matching staff. Guardian Trade requires MA1.
- Three importer tables add selectable batches only. They were not executed. Regular MA2 assets use different upstream ids from Breach, whose source and runtime rows were not changed.

## Cache work for Claude

The existing Gradle entry points are :game:runOsrsNpcImportTool, :game:runOsrsFxImportTool and :game:runOsrsItemImportTool. First run plan/preflight without --apply; only Claude may run apply in the normal two-cache window. Each command is a separate low-priority Gradle invocation, never alongside another build.

1. Pinned2686, NPC batch mage-arena. Verify12 actors,17 additional sequences, movement frames/bases/synths; record assigned npc/BAS/seq/model ids. Movement importer alone does not prove correct MA1 attack/block/death animation selection: source those separately, including human god-spell casts on nonhuman rigs.
2. Pinned2686, FX batch mage-arena (Guthix claw1511, projectiles1512/1513/1514, sword wave1515, lightning1518), then item batch mage-arena. Record assigned FX/hand/roots/heart/symbol ids. Review generated metadata: untradeable, unbankable remains, symbol Destroy text and death destruction, exact examines. Do not use upstream ids directly as local ids.
3. Isolated2762, item batch mage-arena-ancient. Set RSPS_OSRS_SOURCE_CACHE only for this invocation to the source path recorded in RESEARCH, then restore the previous environment. Review ancient-source2762.json and ancient-model-comparison.json. Record assigned7 item ids, male/female models and metadata. No darker34639 or event34607 import.
4. Use the actual generated mappings to finish server bindings and all consumers. No imported cache art is currently wired into MageArena.FORMS. Existing local troll/dark-beast art remains a release blocker.
5. MA2 quest slot193/varp1918 needs a real progress case and earned-state migration. A previously auto-completed varp cannot prove quest completion. MA1 currently has no quest-list row. Do not invent a slot/varp without checking the cache namespace.

## Player verification after remaining implementation

Use ordinary Kolodion dialogue at the existing Mage Bank2541,4716, not a manually spawned907 (an unowned debug actor intentionally cannot grant completion).

1. Base Magic59 boosted above60 refuses. Base60 starts; each owner gets their own duel and five consecutive forms. Third form has78 real HP; final107. Human/ogre/spider/ghost/demon art, cast/block/death/transform sequences and sounds must match OSRS before release.
2. Try melee, ranged, powered staff, manual magic, weapon swap while projectile travels, familiar/cannon, recoil, poison/venom. Direct nonmagic and foreign-owner hits must not bypass immunity. Poison/recoil-specific OSRS parity still needs evidence; no blanket PASS.
3. Leave during every form, logout, die to NPC and PKer, relog/restart and return. Form progress must remain; old private actors must vanish. A second account cannot complete or advance the first account's duel. Test five full attempts and stale/double callbacks.
4. All three statues: repeatedly obtain same/different capes, with full inventory. Verify each ground cape's ownership/model/examine/options. First staff: missing cape, worn cape, all three colours carried, full inventory then retry, repeated talk/trade. No entitlement loss or duplicate staff.
5. Pool/lever/web routes, Mage Bank safety versus arena PvP and N522/D1-D20 death behaviour. No safe-death exemption was added. Verify any skill/special restrictions on god staves and100-cast training after that still-open feature is implemented.
6. After full MA2/cape work: symboldamage death, tracking outside/underground Wilderness, one/two/three remaining prompt choices, rotation only during Wilderness time, all normal/special guardian attacks, moving off barrage/wave tile, orb healing/destruction, shared teleport-block cleanup, full inventory/drop/reclaim/hand-in/imbue; two accounts and existing Breach regression.
7. Every imbued/ancient/max/broken/locked item: recipes, irreversible fabric conversion, knife split, hood accounting, equip requirements, Perdu repair/Trouver, PvM/PvP<20/>20/skull/guard preview and actual loot. Zaros GWD affiliation; max guild/pouch/emote consumers. Shop/GE untradeability and explicit Donator decision.
8. Drop Viewer/Collection Log/quest journal/map/minimap/examines/options/audio on testserver, then ordinary-client A9 after Claude's release. No permanent markers or log slots invented for roaming guardians/cosmetics.

## Open decisions and source gaps

- Zaros acquisition plan (requested task adaptation, not an OSRS rule): genuinely earned MA2 unlocks repeatable free fabric from Kolodion, analogous to OSRS's repeated free fabric from Pete's crate after its unlock. Preserve the fabric+imbued-cape recipe,75Magic, unchanged stats and irreversible conversion. No fourth boss/heart/fee. Item-id-dependent implementation remains open.
- Owner: migration of old auto-completed MA2 state and previously unrestricted god spells. Advice: independent earned/training state, preserve already owned equipment; no fabricated proof of earlier completion.
- Owner: Donator availability/prices/refunds once the three imbued max capes become earnable. Do not silently alter the existing cosmetic offers.
- OSRS evidence: MA1 animation/audio/three-god-spell selection probabilities and poison/recoil details; exact100-cast arena boundary; tracking heat thresholds/messages, actual walkable spawn candidates and rotation persistence; MA2 attack chances/cooldowns/delays, TB duration/prayer factors, pull/bind, orb count/heal cadence, despawn. No guessed timings or donor mechanics activated.
- Remaining server work: god-spell training, full MA2/journal/dialogue/state/tracking/bosses/rewards, ancient recipes and every item/area integration. The current MA2 completed placeholder and all Breach behaviour remain as before.
