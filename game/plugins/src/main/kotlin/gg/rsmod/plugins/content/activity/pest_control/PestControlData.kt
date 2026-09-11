package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.cfg.Npcs

/**
 * Q-050 Pest Control. Neither donor has a real controller (Void's PestControlBotContext.kt is
 * bot-AI hooks assuming the minigame exists; Novite's PestMonsters.java is unrelated generic
 * monster defs). Source per project rule (read-only GitHub, same rsmod-family Kotlin arch):
 * https://github.com/downthecrop/2009scape-mirror repo, path
 * Server -slash- src -slash- main -slash- content -slash- minigame -slash- pestcontrol
 * (path spelled with placeholders above to avoid nested-comment issues with slash-star in a
 * KDoc block - see the shooting_star/sorceress_garden batches' comment-nesting bug this session).
 *
 * All npc/object ids below verified against this project's own generated Npcs.kt/Objs.kt (exact
 * numeric + name match to the 2009-mirror source), and the physical map placement of the
 * barricade objects was independently confirmed via runObjectPlacementProbeTool at region 10536
 * -> real static content native to this 667 cache, not guessed.
 *
 * This engine has no instance/zone manager (same documented limit as Dungeoneering/Construction/
 * Clan-Wars-full/Sorceress's-Garden-adjacent findings this session). The donor spawns a fresh
 * DynamicRegion clone of region 10536 per concurrent game session (so novice/intermediate/
 * veteran, or several novice games, can run in parallel). Ported here as ONE shared game in
 * progress at a time on the real static region 10536, regardless of tier - the same deliberate
 * scoped-down substitute pattern already used for Clan Wars (full).
 */
enum class PestControlTier(
    val displayName: String,
    val combatReq: Int,
    val portalBaseId: Int,
    val pointsPerWin: Int,
    val leaveTile: Tile,
) {
    NOVICE("Novice", 40, Npcs.PORTAL, 2, Tile(2657, 2639, 0)),
    INTERMEDIATE("Intermediate", 70, Npcs.PORTAL_6150, 3, Tile(2644, 2644, 0)),
    VETERAN("Veteran", 100, Npcs.GENERAL_KHAZARD_7551, 4, Tile(2638, 2653, 0)),
}

/** Region 10536's real base tile, derived from its region id and confirmed via the barricade
 *  object placements (id 14230 found at local offsets (13,12)/(13,13)/(52,12)/(52,13), matching
 *  the donor's own OBJECT_OFFSETS table exactly once translated through this base). */
val PC_REGION_BASE = Tile(2624, 2560, 0)

/** Portal local offsets, in donor spawn order (index 0=purple/western, 1=blue/eastern,
 *  2=yellow/south-eastern, 3=red/south-western). */
val PC_PORTAL_OFFSETS = arrayOf(4 to 31, 56 to 28, 45 to 10, 21 to 9)

val PC_SQUIRE_OFFSET = 32 to 32
val PC_SPAWN_X_RANGE = 0..3
val PC_SPAWN_Z_RANGE = 0..5
val PC_SPAWN_BASE = 32 to 49

/** Void Knight squire ids - donor randomly picks one of two lookalikes per game. */
val PC_SQUIRE_IDS = intArrayOf(Npcs.VOID_KNIGHT, Npcs.VOID_KNIGHT_3785)

val PC_SPLATTER_IDS = intArrayOf(Npcs.SPLATTER, Npcs.SPLATTER_3728, Npcs.SPLATTER_3729, Npcs.SPLATTER_3730, Npcs.SPLATTER_3731)
val PC_SHIFTER_IDS = intArrayOf(Npcs.SHIFTER, Npcs.SHIFTER_3733, Npcs.SHIFTER_3734, Npcs.SHIFTER_3735, Npcs.SHIFTER_3736, Npcs.SHIFTER_3737, Npcs.SHIFTER_3738, Npcs.SHIFTER_3739, Npcs.SHIFTER_3740, Npcs.SHIFTER_3741)
val PC_RAVAGER_IDS = intArrayOf(Npcs.RAVAGER, Npcs.RAVAGER_3743, Npcs.RAVAGER_3744, Npcs.RAVAGER_3745, Npcs.RAVAGER_3746)
val PC_SPINNER_IDS = intArrayOf(Npcs.SPINNER, Npcs.SPINNER_3748, Npcs.SPINNER_3749, Npcs.SPINNER_3750, Npcs.SPINNER_3751)
val PC_TORCHER_IDS = intArrayOf(Npcs.TORCHER, Npcs.TORCHER_3753, Npcs.TORCHER_3754, Npcs.TORCHER_3755, Npcs.TORCHER_3756, Npcs.TORCHER_3757, Npcs.TORCHER_3758, Npcs.TORCHER_3759, Npcs.TORCHER_3760, Npcs.TORCHER_3761)
val PC_DEFILER_IDS = intArrayOf(Npcs.DEFILER, Npcs.DEFILER_3763, Npcs.DEFILER_3764, Npcs.DEFILER_3765, Npcs.DEFILER_3766, Npcs.DEFILER_3767, Npcs.DEFILER_3768, Npcs.DEFILER_3769, Npcs.DEFILER_3770, Npcs.DEFILER_3771)
val PC_BRAWLER_IDS = intArrayOf(Npcs.BRAWLER, Npcs.BRAWLER_3773, Npcs.BRAWLER_3774, Npcs.BRAWLER_3775, Npcs.BRAWLER_3776)

/** Real durations, ported verbatim from PestControlActivityPlugin/PestControlSession. */
const val PC_MIN_TEAM_SIZE = 5
const val PC_MAX_TEAM_SIZE = 25
const val PC_LOBBY_DEPARTURE_TICKS = 500
const val PC_GAME_DURATION_TICKS = 2000
val PC_SHIELD_DROP_TICKS = intArrayOf(50, 100, 150, 200)

/** Pest points balance, this project's per-currency AttributeKey pattern (see PointCurrency.kt -
 *  every real point-shop balance gets its own AttributeKey rather than sharing LOYALTY_POINTS). */
val PEST_POINTS = AttributeKey<Int>(persistenceKey = "pest_points")

val PC_MASTER_TICK = TimerKey()
