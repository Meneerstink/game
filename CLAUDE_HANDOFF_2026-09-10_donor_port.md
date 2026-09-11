# Handoff: donor port mission (Void + Novite → gg-rsmod target) — 2026-09-10

Read this whole file first, then resume the mission directly. Do not ask the owner questions,
do not write plans or audits, do not give interim reports. Final report only at the very end.

## Mission (owner's instruction, Dutch, summarised faithfully)

Work fully autonomously in `C:\RSPS`. TARGET: `C:\RSPS\game\game` (gg-rsmod / 2011scape fork, Kotlin,
`.plugin.kts` scripts). DONORS, READ ONLY, NEVER MODIFY: `C:\RSPS\Donors\void` (Void, Kotlin, rev 634,
toml/groml data) and `C:\RSPS\Donors\Novite` (Matrix-based Java 667).

Port ALL usable, complete, relevant 2010–2011 / rev-667 functionality from BOTH donors into the target:
- Implement directly; inspect only what is needed; keep going until both donors are exhausted.
- After each finished part, immediately take the next donor part. No milestones, no stopping at compile.
- "Already exists in target" ≠ done: compare, merge, replace or repair.
- Choose the best implementation per part (Void, Novite or combination). Port all dependencies; no partial ports.
- Adapt professionally to gg-rsmod architecture; do not blindly copy an incompatible engine.
- No TODOs, stubs, placeholders, mocks or hacks. Correct donor code where it is wrong for rev 667.
- Compile/test during work and fix errors you cause. Never guess IDs/stats/anims/gfx: source them from donors.
- Ask nothing unless a genuinely missing external dependency makes further work impossible.
- Scope: combat, weapons, specials, NPCs, AI, bosses (GWD + Nex/Ancient Prison, Corp, TDs, dragons/frost dragons,
  KBD, KQ, Revenants, Barrows, Dagannoth/Waterbirth, Chaos Elemental, any other donor boss), Summoning and
  Prayer/Ancient Curses fully, Fight Caves/Jad and Clan Wars. NOT quests or general minigames; other skills no priority.
- Never git reset/clean/stash/commit/push. Don't delete unrelated files. "Verified" only with real evidence.
- End report, very short: 1) what was integrated, 2) compile/test status, 3) only real remaining technical blockers.

## Status: DONE and on disk (uncommitted working tree, compiles clean)

Compile command (≈25 s incremental, must print nothing on success):
```
cd C:/RSPS/game/game && timeout 590 ./gradlew :game:compileKotlin :game:plugins:compileKotlin --offline -q --console=plain 2>&1 | grep -E "^e: |BUILD"
```
Last run: passed with zero errors on 2026-09-10 (session 2, after WildyWyrm). Server boot and the plugin test suite have NOT been run.

Plugins root: `game/plugins/src/main/kotlin/gg/rsmod/plugins/content/`

- **God Wars Dungeon** — `areas/godwars/GodWars.kt`, `areas/godwars/godwars_dungeon.plugin.kts`; aggro hook in
  `mechanics/aggro/npc_aggro.plugin.kts`. Kill counts (persisted attrs), overlay 601, item protection, boss doors (40 KC),
  altars, all entrance obstacles, Zaros prison door 57258.
- **Nex / Ancient Prison** — `areas/godwars/nex/NexEncounter.kt`, `NexCombatScript.kt`, `nex_arena.plugin.kts`,
  `npcs/definitions/godwars/nex.plugin.kts`. Five phases, four minions, drop table, wrath, restart.
- **Fight Caves** — `areas/tzhaar/fightcaves/FightCaveWaves.kt`, `FightCaves.kt`, `FightCaveCombatScripts.kt`,
  `fight_caves.plugin.kts`; `npcs/definitions/other/fight_cave_creatures.plugin.kts`;
  data `data/cfg/minigames/tzhaar_fight_cave_waves.toml`. Instanced, Jad + healers, rewards, login resume.
- **Barrows** — `npcs/definitions/barrows/Barrows.kt` + `barrows.plugin.kts` rewritten, `brothers.plugin.kts` hp 1000.
- **Dagannoth Kings** — `combat/scripts/impl/DagannothKingsCombatScript.kt`, `npcs/definitions/dagannoth/dagannoth_kings.plugin.kts`.
- **Metal/frost dragons, skeletal wyverns** — `combat/scripts/impl/MetalDragonCombatScript.kt`.
- **Glacors** — `combat/scripts/impl/GlacorCombatScript.kt`, `npcs/definitions/other/glacor.plugin.kts`.
- **Strykewyrms** — `npcs/definitions/other/Strykewyrms.kt`, `strykewyrms.plugin.kts`.
- **Bork** — `areas/wilderness/bork.plugin.kts`. **Clan Wars FFA** — `areas/wilderness/clan_wars_ffa.plugin.kts`.
- **SafeDeath registry** — `mechanics/death/SafeDeath.kt`, early return in `death.plugin.kts`.
- Bindings appended to `combat/scripts/combat_script_binding.plugin.kts`.

Engine (`game/src/main/kotlin/gg/rsmod/game/`):
- `model/entity/Pawn.kt`: `var hitModifier: ((Hit) -> Unit)?` invoked in `hitsCycle()` before a hit is applied.
- `model/instance/InstancedMapAllocator.kt`: public `release(world, map)`.
- `plugin/PluginRepository.kt`: `addMultiCombatRegion(region)`.
- `message/impl/IfSetModelMessage.kt`, `message/encoder/IfSetModelEncoder.kt`, registered in `MessageEncoderSet.kt`;
  `data/packets.yml` opcode 58 (hash INT MIDDLE, model SHORT ADD). `PlayerExt.setComponentModel(interfaceId, component, model)`.

Known simplifications (report as blockers at the end unless fixed):
- Nex minion animations inferred from the Nex rig (no donor value found).
- Barrows door varbits 469–484 all set to 1 (open) instead of per-door state.
- Fight cave instance chunks marked multi-combat at allocation time.

## Status: DONE in session 2 (2026-09-10, owner-requested safe stop; compiles clean, NOT booted, NOT client-tested)

- **Corporeal Beast lair access** — `areas/wilderness/corporeal_beast_lair.plugin.kts`: entrance 38815/37749 with the
  23 Summoning / 37 Woodcutting / 45 Mining / 47 Firemaking / 55 Prayer check → 2885,4372 plane 2; exit 37928 → 3214,3782;
  passages 37929/38811 (+3,+2 east / -1,+2 west of the anchor); entering the lair shows warning interface 650
  (17 enter, 18 close, 20 don't-ask toggle, 21 toggle row shown from the 6th view, varbit 5366 counts 0..7 as in Void);
  "Peek-in" reports lair occupancy.
- **Corporeal Beast rewrite** — `combat/scripts/impl/CorporealBeastCombatScript.kt` + `npcs/definitions/other/corporeal_beast.plugin.kts`:
  Void stats (20000 lp, att/str 320, def 310, mag 350, rng 150, attack bonus 50, respawn 50, poison immune), full Void drop
  table (513-roll main incl. sigil sub-table, clue tertiary 512, charm roll 1000; big bones kept guaranteed), stomp 30-51,
  melee 51, magic 65 / drain 55 / scatter 40-30-20 with impact gfx 1806, corpbane halving (only spear/halberd on stab deals
  full damage — hooked in `combat/PawnExt.kt` dealHit next to the Tormented demon hook), Protect/Deflect Magic lets 60%
  through (Novite; new `MagicCombatFormula.getUnprotectedAccuracy`), regeneration every 7 ticks (full heal with lair empty,
  +25+5/player lp at 8+ players; per-life token so respawns don't stack loops), Dark energy core 8127 (1/8 on each attack
  while damaged or on any 32+ hit taken; flies in on proj 1828, drains 1-13 lp every 2 ticks / 12 while poisoned with the
  Void message and heals the beast, hops with anim 10393 to a random lair player when its victim moves, 25 lp, death anim
  10391, drops ashes, `LockState.FULL` so it is attackable but never walks or retaliates; removed on beast death / empty lair).
  DSL addition: `configs { poisonImmune = true }` in `api/dsl/NpcCombatDsl.kt`.
- **Spinolyps** — `combat/scripts/impl/SpinolypCombatScript.kt`, `npcs/definitions/dagannoth/spinolyps.plugin.kts`:
  Void numbers chosen over Novite's (max 10 not 30; poison 2 on every landed hit; water strike proj 2703 / impact 2708;
  anim 2868/2869/2866; 100 lp, ranged 100, respawn 10, bones), Ranged accuracy so Protect from Missiles blocks it.
- **WildyWyrm** — `combat/scripts/impl/WildyWyrmCombatScript.kt`, `areas/wilderness/wildywyrm.plugin.kts`: Novite combat
  (hits every player within 15: 2/3 ranged 5-40 proj 2313 + 10% poison 6, 1/3 magic 17 proj/impact 2315 with 1/11 freeze
  5 ticks gfx 369), Novite bonuses (atk 500 / def 300), bulk row lp 10000 / str 715 / anims 12791-3, Novite static spawn
  3093,10123 (Forinthry Dungeon). PROVISIONAL: attack/ranged/magic levels = 715 (no donor level source). No donor drop table.
- Bindings for the three scripts appended to `combat/scripts/combat_script_binding.plugin.kts`.

Owner retest list for session 2 (all PENDING_HUMAN_RETEST):
- Corp lair: enter 38815 with/without the skill reqs, walk both passages both directions, warning screen buttons
  (enter / close / don't-ask toggle), Peek-in with someone inside, exit 37928.
- Corp fight: 5 attack types visible (stomp when under it, melee, spiky ball, drain ball with stat message, scatter ball
  splashes), Protect from Magic still takes ~60% magic damage, non-spear damage halved vs Zamorakian spear on stab full,
  core appears / drains / hops / dies, beast heals to full when everyone leaves, drops incl. charms.
- Spinolyps (Waterbirth dungeon): water-strike projectile, max 10, poison, blocked by Protect from Missiles.
- WildyWyrm at 3093,10123: AoE ranged/magic, freeze, poison; accuracy feel (levels are provisional).

## Status: NOT DONE (continue here, in this order)

3. Check and port if present in donors: Jadinko lair (Novite `player/controlers/impl/JadinkoLair.java`,
   `npc/combat/impl/JadinkoCombat.java` + `JadinkoMaleCombat.java`, `npc/others/Jadinko.java`; nothing in Void), KBD lever
   gating (Novite ObjectHandler; target has `areas/wilderness/king_black_dragon_lair.plugin.kts` with only the ladder),
   Waterbirth dungeon extras (Void `data/area/fremennik_province/waterbirth_island/` — doors/ladders/rock-lobster etc.),
   Aviansie/GWD bodyguard behaviour audit (bulk combat-defs handle ranged/magic generically).
4. Sweep both donors for any remaining boss/combat/summoning/prayer content not yet in the target (compare, don't assume).
5. Run plugin tests if feasible (`./gradlew :game:plugins:test --offline`), optionally boot check, fix what you broke.
6. Deliver the final short report (three points only).

## Target API cheat sheet (learned the hard way; trust these)

- Plugin DSL: `on_obj_option(obj=, option=)`, `on_npc_combat(npc=…)` (use named arg to avoid overload ambiguity),
  `on_npc_death(id)`, `on_npc_killed{killer,npc}`, `on_npc_pre_death`, `on_player_pre_death`, `on_player_death`
  (runs AFTER respawn at home), `on_login/on_logout`, `on_enter_region/on_exit_region`, `on_timer(TimerKey)`,
  `on_world_init`, `on_button(interfaceId, component)`, `on_item_option`, `on_item_on_obj`, `on_equip_to_slot`,
  `can_attack{attacker,target}`, `set_combat_def(npc){configs{} stats{} bonuses{} anims{} aggro{}}`, `set_multi_combat_chunk`.
- `CombatScript` objects (`override val ids`, `suspend fun handleSpecialCombat(it: QueueTask)`), bound via
  `on_npc_combat(*X.ids){ npc.queue{ X.handleSpecialCombat(this) } }`. kts top-level `object`s that capture script
  scope must live in `.kt` files ("captures the script class instance" error).
- `Pawn.dealHit(target, minHit=0.1, maxHit, landHit, delay, onHit={}, hitType)` MULTIPLIES by 10: pass 36.9 for 369 LP.
  Use named args `hitType = …, onHit = { hit -> … }` (trailing lambda fails). `Pawn.hit(damage, HitType, delay)` is raw x10.
  Lifepoints x10 (Nex 30000).
- `world.players` is a PawnList (NOT Iterable): forEach/any/none/count/firstOrNull only; build lists via forEach.
- `decrementCurrentLevel(skill, value, capped = false)` needs `capped`. `!player.inventory.contains(id)` not `!in`.
- Smart casts fail on captured vars in closures: copy to a local `val` first. Avoid var+fun same name (JVM clash).
- Overlays: `player.openInterface(dest = InterfaceDestination.PVP_OVERLAY, interfaceId = …)`; `closeInterface(dest)`.
- Projectiles `npc.createProjectile(target, gfx, ProjectileType.MAGIC/ARROW/THROWN/FIERY_BREATH)`;
  hit delays `MagicCombatStrategy.getHitDelay(from, to)`; accuracy `MagicCombatFormula.getAccuracy(npc, target)`.
- Instances: `InstancedChunkSet.Builder().set(...)`, `InstancedMapConfiguration.Builder()...build()`,
  `world.instanceAllocator.allocate(world, chunks, config)`; instance space x 6400..9600.
- Constants that exist: `SHARDS_OF_ARMADYL`, `RUNITE_ORE`, `Anims.DIG_SPADE`, `Anims.LADDER_CLIMB`,
  `Anims.MODERN_TELEPORT_START`, `Gfx.CURSE_SPELL/CURSE_SPELL_PROJ`, `Npcs.TZKIH_2734`. Grep `Objs/Npcs/Items/Anims/Gfx`
  before using a name.
- Bulk data: `data/cfg/npcs/combat-defs.json`, `data/cfg/npcs/drop-tables.json`; hand-written `set_combat_def` overrides.
- PvP allowed everywhere outside safe zones; true safe minigames use `SafeDeath.register { predicate }`.

## Environment quirks

- Bash output is compressed by `rtk`; use `rtk proxy <cmd>` for raw output. If the Bash tool fails via a hook error,
  switch to PowerShell for that step instead of retrying.
- Read tool output gets compressed above ~50 lines: read in ≤50-line chunks. Python is not installed; use sed/Edit.
- Grep tool works reliably. Never drive the live client via GUI automation.
- Stray files in repo root (`0`, `0)`, `T)`, `hs_err_pid*.log`, `graphify-out/`) were NOT created by this mission; leave them.
