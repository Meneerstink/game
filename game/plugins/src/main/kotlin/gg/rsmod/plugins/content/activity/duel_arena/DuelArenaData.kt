package gg.rsmod.plugins.content.activity.duel_arena

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.EquipmentType

/**
 * The 12 real Duel Arena rule toggles. `id631`/`id637` are the real component ids on this
 * project's own generated interfaces 631 (staked duel) / 637 (friendly, no-stake duel) -
 * independently confirmed present in this project's real 667 cache via
 * `runInterfaceHookProbeTool ... interface 631/637`, byte-identical in label/description/default
 * to Novite's `DuelArena.java` rule-button switch (case 55-66 for 631, case 25-36 for 637).
 * `defaultActive` is the real cache-sourced default checkbox state, not assumed.
 */
enum class DuelRule(val id631: Int, val id637: Int, val label: String, val defaultActive: Boolean) {
    NO_RANGED(55, 25, "No Ranged", false),
    NO_MELEE(56, 26, "No Melee", false),
    NO_MAGIC(57, 27, "No Magic", false),
    FUN_WEAPONS(58, 28, "Fun Weapons", false),
    NO_FORFEIT(59, 29, "No Forfeit", false),
    NO_DRINKS(60, 30, "No Drinks", false),
    NO_FOOD(61, 31, "No Food", false),
    NO_PRAYER(62, 32, "No Prayer", false),
    NO_MOVEMENT(63, 33, "No Movement", false),
    OBSTACLES(64, 34, "Obstacles", false),
    ENABLE_SUMMONING(65, 35, "Enable Summoning", true),
    NO_SPECIAL_ATTACKS(66, 36, "No Special Attacks", true),
}

/**
 * The 11 real equipment-slot locks. Component order/id independently confirmed against this
 * project's own real cache for both interfaces (631: 21-31, 637: 43-53) via
 * `runInterfaceHookProbeTool` - real text ("If selected, neither player can wear items on
 * their..."), real order (head/cape/amulet/weapon/body/shield/legs/gloves/boots/ring/ammo),
 * matched 1:1 against this project's own [EquipmentType] enum.
 */
enum class DuelEquipLock(val id631: Int, val id637: Int, val slot: EquipmentType) {
    HEAD(21, 43, EquipmentType.HEAD),
    CAPE(22, 44, EquipmentType.CAPE),
    AMULET(23, 45, EquipmentType.AMULET),
    WEAPON(24, 46, EquipmentType.WEAPON),
    BODY(25, 47, EquipmentType.CHEST),
    SHIELD(26, 48, EquipmentType.SHIELD),
    LEGS(27, 49, EquipmentType.LEGS),
    GLOVES(28, 50, EquipmentType.GLOVES),
    BOOTS(29, 51, EquipmentType.BOOTS),
    RING(30, 52, EquipmentType.RING),
    AMMO(31, 53, EquipmentType.AMMO),
}

/**
 * Real static Al Kharid Duel Arena tile data, ported verbatim from Novite's own
 * `DuelArena.java` constants (`POSSIBLE_TILE_CENTRE`, `LOBBY_TELEPORTS`) - a fixed set of shared
 * rooms, NOT a per-duel dynamically generated arena, so this content is not blocked by this
 * engine's missing instance/zone manager (unlike Stealing Creation's game or Dungeoneering).
 */
object DuelArenaLocations {
    val REGULAR = listOf(Tile(3346, 3251, 0), Tile(3376, 3232, 0))
    val OBSTACLES = listOf(Tile(3345, 3231, 0), Tile(3376, 3213, 0))
    val SUMMONING = listOf(Tile(3346, 3214, 0))
    val LOBBY =
        listOf(
            Tile(3367, 3275, 0), Tile(3360, 3275, 0), Tile(3358, 3270, 0), Tile(3363, 3268, 0),
            Tile(3370, 3268, 0), Tile(3367, 3267, 0), Tile(3376, 3275, 0), Tile(3377, 3271, 0),
            Tile(3375, 3269, 0), Tile(3381, 3277, 0),
        )

    // Ported verbatim from Novite's own arena-select ternary (`rules.getRule(24) ? summoning :
    // rules.getRule(6) ? obstacles : regular`) - summoning takes priority over obstacles there,
    // preserved as sourced rather than re-ordered to a more "intuitive" priority.
    fun pick(rules: Set<DuelRule>): Tile =
        when {
            DuelRule.ENABLE_SUMMONING in rules -> SUMMONING.random()
            DuelRule.OBSTACLES in rules -> OBSTACLES.random()
            else -> REGULAR.random()
        }

    /** How far from its room's centre [DuelArenaRules.battleTiles] places a fighter (Chebyshev radius). */
    const val PLACEMENT_RADIUS = 7

    /** Every centre a duel can be fought around. */
    val CENTRES: List<Tile> get() = REGULAR + OBSTACLES + SUMMONING

    /**
     * The fight rooms, for the SafeDeath registration: every centre's placement square widened to its room walls (x +/-12,
     * z +/-9), all on level 0 below the lobby. Overnight 2026-10-02 (conform deadman "confirmed: both players are in the
     * arena" measured a fighter at 3339,3217): the old box started at x 3340, but [DuelArenaRules.battleTiles] places
     * fighters up to [PLACEMENT_RADIUS] from the summoning centre 3346 / the obstacles centre 3345 - x 3338 and 3339 - so a
     * fighter could stand in the arena without his death counting as a (safe) duel death.
     */
    fun inArena(tile: Tile): Boolean =
        tile.height == 0 && tile.z < LOBBY.minOf { it.z } - 4 &&
            CENTRES.any { kotlin.math.abs(tile.x - it.x) <= ROOM_HALF_WIDTH && kotlin.math.abs(tile.z - it.z) <= ROOM_HALF_DEPTH }

    /** Half the size of a fight room around its centre (rooms are about 25 x 18 tiles). */
    const val ROOM_HALF_WIDTH = 12
    const val ROOM_HALF_DEPTH = 9
}
