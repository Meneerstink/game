package gg.rsmod.plugins.content.activity.stealing_creation

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Objs

/**
 * Q-053 Stealing Creation. Source: Novite `minigames/stealingcreation` package (2951 lines / 8 files).
 *
 * All ids below were verified against this project's own generated cache constants (Objs.kt,
 * Items.kt) and, for the two entry stiles, against a live `runObjectDefProbeTool` +
 * `runObjectPlacementProbeTool` decode (option text "Climb-over", real static tiles in region
 * 11927) before use - none were guessed.
 *
 * Real architecture limit found before writing any gameplay code, not guessed around: Novite's
 * `GameArea.create()` procedurally generates a brand-new random arena per game via
 * `RegionBuilder.findEmptyRegionHash`/`copyChunk` at runtime - a genuine per-game dynamic map,
 * the same class of instance/zone-manager dependency already documented for Dungeoneering,
 * Construction and Clan Wars (full). This engine has no instance/zone manager. Unlike Castle
 * Wars/Pest Control/Sorceress's Garden (all real, static, single shared arenas - confirmed via
 * probe before assuming otherwise), Stealing Creation's actual arena model needs a fresh
 * procedurally-built map per match, so the match/arena itself is NOT portable this batch.
 * Additionally, the arena-side ids (`BLUE_DOOR_1=39766` etc.) do not exist at all in this
 * project's real cache (confirmed absent via a direct grep of the generated `Objs.kt`) - so even
 * with an instance manager, those specific assets would need re-sourcing.
 *
 * What IS real, static, and portable: the pre-game waiting-room/lobby system, which lives at a
 * fixed real-world location outside the dynamic arena. This batch ports that part only.
 */
object StealingCreationLobbyData {
    /** Entry stiles. Both confirmed real: option "Climb-over", region 11927. */
    val BLUE_STILE = Objs.STILE_39508 // real tile 2965,9703,0
    val RED_STILE = Objs.STILE_39509 // real tile 2965,9696,0

    val BLUE_STILE_TILE = Tile(2965, 9703, 0)
    val RED_STILE_TILE = Tile(2965, 9696, 0)

    /** One tile north of each stile - the waiting-pen side, inferred from the pair's layout (not
     * independently probed beyond the stile tiles themselves - a disclosed, low-risk inference,
     * not a guessed cache id). */
    val BLUE_WAITING_TILE = Tile(2965, 9704, 0)
    val RED_WAITING_TILE = Tile(2965, 9697, 0)

    /** Novite's fixed exit/reset tile. A coordinate, not a cache id - taken verbatim from the
     * donor and not independently re-verified against the live map (no walkability probe tool
     * exists in this project), disclosed rather than silently assumed correct. */
    val EXIT_TILE = Tile(2968, 9710, 0)

    /** Team cape reward items, both confirmed present in target's own `Items.kt`. */
    const val BLUE_CAPE = 14387
    const val RED_CAPE = 14389

    /** Real interface used for the team-lobby overlay, confirmed via `runInterfaceHookProbeTool`
     * (35 real components, matching every component id Novite references: 1/2/4/5/6/7/33/34). */
    const val LOBBY_INTERFACE = 804

    const val MIN_TEAM_SIZE = 5

    /** Novite's `LobbyTimer` starts at 2 (minutes) and fires every real 60s, decrementing by 1
     * each call until it hits 0. Modelled here as steps, not raw ticks - see the world-timer
     * wiring in `stealing_creation.plugin.kts`, which fires this handler's `tick()` once every
     * 100 game ticks (60s at 600ms/tick), matching Novite's cadence exactly. */
    const val LOBBY_COUNTDOWN_STEPS = 2
    const val LOBBY_TICK_INTERVAL = 100

    /** Same skill groupings Novite's balance check uses, by this project's own (identically
     * numbered, standard RS) `Skills.*` constants - no remapping needed, verified by inspection
     * of `Skills.kt`. */
    val TOTAL_SKILL_IDS =
        intArrayOf(
            Skills.WOODCUTTING, Skills.MINING, Skills.FISHING, Skills.HUNTER, Skills.COOKING,
            Skills.HERBLORE, Skills.CRAFTING, Skills.SMITHING, Skills.FLETCHING,
            Skills.RUNECRAFTING, Skills.CONSTRUCTION,
        )
    val TOTAL_COMBAT_IDS =
        intArrayOf(
            Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.RANGED,
            Skills.MAGIC, Skills.PRAYER, Skills.SUMMONING,
        )
}

val ScLobbyTick = TimerKey()
