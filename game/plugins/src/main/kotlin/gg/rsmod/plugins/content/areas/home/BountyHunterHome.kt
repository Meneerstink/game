package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.SimplePolygonArea
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getWildernessLevel

/**
 * Rev-667 object ids of the Ferox Enclave content imported by `FeroxImportTool`
 * (transaction tx-20260905-052852, source OpenRS2 cache 2499 build 236). Every id below is the
 * local LocType allocated by that import - see `OSRS_IMPORT_MASTER.yml` "Ferox Enclave world
 * import" for the upstream->local table. Nothing here is guessed: the placements were re-read
 * from the production cache after the apply (`Rev667RegionProbeTool locs`).
 */
object FeroxObjects {
    /** Modern 26711 'Bank chest' @3130,3632 - options [Use, -, Collect]. */
    const val BANK_CHEST = 62421

    /** Modern 39651 'Pool of Refreshment' 2x2 @3128,3633 and @3128,3638 - option [Drink]. */
    const val POOL_OF_REFRESHMENT = 62433

    /** Modern 39642 'Altar' 3x1 @3177,3625 - option [Pray-at]. */
    const val ALTAR = 62562

    /** Modern 39652/39653 'Barrier' wall pairs - option [Pass-Through]. */
    const val BARRIER_A = 62434
    const val BARRIER_B = 62435

    /**
     * Modern 39656 (option-less energy-field loc, models 2300/2301) on the same four barrier tiles.
     * The click slab above is an all-alpha-254 model the 2011 client cannot target, so the visible
     * field carries the same "Pass-Through" option (FeroxBarrierOptionTool, cache transaction).
     */
    const val BARRIER_FIELD = 62438

    /** Modern content that has no server-side counterpart here and is safely disabled. */
    const val DEATHS_DOMAIN = 62430
    const val CLAN_WARS_CHALLENGE_PORTAL = 62419
    const val CLAN_WARS_FFA_PORTAL = 62420
    const val CASTLE_WARS_PORTAL = 62554
    const val BOUNTY_HUNTER_PORTAL = 62576
    const val LMS_CASUAL = 62559
    const val LMS_COMPETITIVE = 62560
    const val LMS_HIGH_STAKES = 62561
    const val REWARD_CHEST = 62548
    const val SCOREBOARD = 62549
    const val COFFER = 62552
    const val TWISTED_CRATE = 62569
    const val STAIRS_A = 62431
    const val STAIRS_B = 62432
    const val STAIRS_WALK_DOWN = 62564
}

/**
 * The Ferox Enclave safe hub. Since the Ferox world import the physical enclave lives at fixed
 * world coordinates in map squares 12344/12600, so the safe zone is no longer a shape derived
 * from `game.yml`'s home tile: it is the enclave's own walled footprint. `game.yml`'s home tile
 * only chooses where inside the enclave players arrive; `home_verify.plugin.kts` asserts at boot
 * that it is inside this zone and walkable.
 *
 * Footprint (from the imported wall placements, `ModernRegionProbeTool` census):
 *  * main compound x 3123..3155, z 3617..3646 (barrier exits west @x3123 z3628-3629,
 *    south @z3617 x3134-3135, north @z3639 x3134-3135, and an internal pair @x3154 z3634-3635);
 *  * eastern annex + garden x 3156..3187, z 3603..3646 (altar, lift, stairs).
 * The south-west pocket x 3123..3155, z 3603..3616 is outside the walls and stays Wilderness.
 *
 * The one-tile step immediately beyond the polygon is ordinary Wilderness (level 12-13); there
 * is no transition-safe strip. The object name is kept because every caller (AreaState,
 * DeathResolver, wilderness plugin, tests) binds to it.
 */
object BountyHunterHome {
    const val MAIN_MIN_X = 3123
    const val MAIN_MAX_X = 3155
    const val MAIN_MIN_Z = 3617
    const val MAX_Z = 3646
    const val EAST_MIN_X = 3156
    const val EAST_MAX_X = 3187
    const val EAST_MIN_Z = 3603

    /** L-shaped simple polygon: main compound plus the eastern annex/garden. */
    private val POLYGON_VERTICES =
        arrayOf(
            Tile(MAIN_MIN_X, MAIN_MIN_Z, 0),
            Tile(EAST_MIN_X, MAIN_MIN_Z, 0),
            Tile(EAST_MIN_X, EAST_MIN_Z, 0),
            Tile(EAST_MAX_X, EAST_MIN_Z, 0),
            Tile(EAST_MAX_X, MAX_Z, 0),
            Tile(MAIN_MIN_X, MAX_Z, 0),
        )

    private val SAFE_POLYGON = SimplePolygonArea(POLYGON_VERTICES)

    /** The polygon is fixed in the world; [home] is accepted for source compatibility only. */
    @Suppress("UNUSED_PARAMETER")
    fun safeArea(home: Tile): SimplePolygonArea = SAFE_POLYGON

    /**
     * A barrier exit: the barrier tile itself, the direction of travel when leaving, and the
     * landing tiles on each side (same contract `home_gates.plugin.kts` used).
     */
    data class GateInfo(
        val tile: Tile,
        val direction: Direction,
        val innerLanding: Tile,
        val outerLanding: Tile,
        /** false for the barrier pair that only separates the plaza from the eastern garden. */
        val exitsToWilderness: Boolean = true,
    )

    /** Every 'Barrier' placement of the imported enclave, as re-read from the production cache. */
    @Suppress("UNUSED_PARAMETER")
    fun gates(home: Tile): List<GateInfo> =
        listOf(
            GateInfo(Tile(3123, 3628, 0), Direction.WEST, Tile(3124, 3628, 0), Tile(3122, 3628, 0)),
            GateInfo(Tile(3123, 3629, 0), Direction.WEST, Tile(3124, 3629, 0), Tile(3122, 3629, 0)),
            GateInfo(Tile(3134, 3617, 0), Direction.SOUTH, Tile(3134, 3618, 0), Tile(3134, 3616, 0)),
            GateInfo(Tile(3135, 3617, 0), Direction.SOUTH, Tile(3135, 3618, 0), Tile(3135, 3616, 0)),
            GateInfo(Tile(3134, 3639, 0), Direction.NORTH, Tile(3134, 3638, 0), Tile(3134, 3640, 0), exitsToWilderness = false),
            GateInfo(Tile(3135, 3639, 0), Direction.NORTH, Tile(3135, 3638, 0), Tile(3135, 3640, 0), exitsToWilderness = false),
            GateInfo(Tile(3154, 3634, 0), Direction.EAST, Tile(3153, 3634, 0), Tile(3155, 3634, 0), exitsToWilderness = false),
            GateInfo(Tile(3154, 3635, 0), Direction.EAST, Tile(3153, 3635, 0), Tile(3155, 3635, 0), exitsToWilderness = false),
        )

    fun gateTiles(home: Tile): List<Tile> = gates(home).map { it.tile }

    /**
     * Every floor inside the enclave walls is protected (owner decision 2026-09-13, "fix the upper floors" - no source
     * states otherwise). The south-east tower is outside the walls and stays Wilderness (OSRS Wiki).
     */
    fun isSafe(
        tile: Tile,
        home: Tile,
    ): Boolean = SAFE_POLYGON.containsTile(Tile(tile.x, tile.z, 0))

    fun isSafe(player: Player): Boolean = isSafe(player.tile, player.world.gameContext.home)

    fun isDangerousWilderness(
        tile: Tile,
        home: Tile,
    ): Boolean = tile.getWildernessLevel() > 0 && !isSafe(tile, home)

    fun isDangerousWilderness(player: Player): Boolean = isDangerousWilderness(player.tile, player.world.gameContext.home)

    fun canPlayersFight(
        attacker: Player,
        target: Player,
    ): Boolean =
        (isDangerousWilderness(attacker) && isDangerousWilderness(target)) ||
            gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp.areMatched(attacker, target)

    /**
     * RCV-011 (owner: Ferox like OSRS). OSRS Wiki "Barrier (Ferox Enclave)" / "Tele Block": a tele-blocked player cannot
     * enter the enclave through a barrier; leaving is allowed and being in combat does not stop entry. Only the
     * Wilderness-facing barriers are an entry; the internal plaza/garden pair is not. No source quotes the refusal text;
     * [BARRIER_TELEBLOCK_MESSAGE] was chosen by the owner (2026-09-13).
     */
    fun barrierRefusesEntry(
        goingOutward: Boolean,
        gate: GateInfo,
        teleblocked: Boolean,
    ): Boolean = teleblocked && !goingOutward && gate.exitsToWilderness

    /** Owner decision 2026-09-13 (no source text exists). */
    const val BARRIER_TELEBLOCK_MESSAGE = "A magical force prevents you from passing through the barrier while you are tele-blocked."

    /** OSRS Wiki "Pool of Refreshment". */
    const val POOL_MESSAGE = "You feel reinvigorated after drinking from the pool."
}
