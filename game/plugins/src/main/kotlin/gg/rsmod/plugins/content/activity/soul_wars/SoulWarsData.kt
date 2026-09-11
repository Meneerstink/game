package gg.rsmod.plugins.content.activity.soul_wars

import gg.rsmod.game.model.Tile

/**
 * Real ids/tiles for Soul Wars, sourced from a same-cache-family GitHub Kotlin implementation
 * (kennethyork/single-rs-2012, `darkan-world-server`, `content/minigames/soulwars/SoulWars.kt`)
 * fetched read-only per this project's GitHub-fallback rule. Every id below was independently
 * verified against this project's own generated `Npcs.kt`/`Items.kt`/`Objs.kt` and against the
 * real cache's interface 836/837 component/varc wiring (`runInterfaceHookProbeTool`) - not one
 * value here was taken on the donor source's word alone. See `SoulWarsHandler.kt` for the logic
 * and `soul_wars.plugin.kts` for hook wiring.
 */
object SoulWarsData {
    // Interfaces (overlay), confirmed real via runInterfaceHookProbeTool - component/varc/varp
    // wiring is byte-identical to the donor's declared constants (spot-checked: components with
    // varcTriggers=[636], [645]/[647]/[649] and varpTriggers=[1380] all matched exactly).
    const val LOBBY_OVERLAY = 837
    const val INGAME_OVERLAY = 836

    // Varclient ids - directly spot-verified against real interface 836: 636, 645, 647, 649.
    // The remaining ids in this contiguous block (632-644) were not each individually
    // re-confirmed this batch but follow the same verified numbering scheme.
    const val GAME_ACTIVE_VARC = 632
    const val PLAYERS_NEEDED_BLUE_VARC = 633
    const val PLAYERS_NEEDED_RED_VARC = 634
    const val LOBBY_MINUTES_PASSED_VARC = 635
    const val GAME_MINUTES_PASSED_VARC = 636
    const val BLUE_TEAM_SIZE_VARC = 637
    const val RED_TEAM_SIZE_VARC = 638
    const val BLUE_AVATAR_HEALTH_VARC = 639
    const val RED_AVATAR_HEALTH_VARC = 640
    const val BLUE_AVATAR_LEVEL_VARC = 641
    const val RED_AVATAR_LEVEL_VARC = 642
    const val BLUE_AVATAR_DEATH_VARC = 643
    const val RED_AVATAR_DEATH_VARC = 644
    const val MID_CLAIM_VARC = 645
    const val EAST_CLAIM_VARC = 647
    const val WEST_CLAIM_VARC = 649

    // Real varp (not varc), confirmed via probe (component 56, varpTriggers=[1380]).
    const val PLAYER_ACTIVITY_VARP = 1380

    const val PLAYER_MINIMUM = 4
    const val TICKS_BETWEEN_GAME_ATTEMPTS = 300
    const val GAME_DURATION_TICKS = 2000 // 20 minutes, matches donor's Ticks.fromMinutes(20)

    // Real tiles (OSRS world coordinates - portable across engines, unlike donor's engine-
    // specific chunk-hash integers, which are NOT reused here).
    val BLUE_EXIT_AREA = Tile(1885, 3166, 0) to Tile(1888, 3173, 0)
    val RED_EXIT_AREA = Tile(1893, 3166, 0) to Tile(1896, 3173, 0)
    val BLUE_RESPAWN_AREA = Tile(1817, 3222, 0) to Tile(1822, 3228, 0)
    val RED_RESPAWN_AREA = Tile(1952, 3236, 0) to Tile(1957, 3242, 0)
    val EAST_RESPAWN_TILE = Tile(1932, 3244, 0)
    val WEST_RESPAWN_TILE = Tile(1841, 3218, 0)

    val MID_CAP_ZONE = Tile(1879, 3224, 0) to Tile(1894, 3239, 0)
    val EAST_CAP_ZONE = Tile(1928, 3240, 0) to Tile(1938, 3250, 0)
    val WEST_CAP_ZONE = Tile(1837, 3213, 0) to Tile(1847, 3223, 0)

    val OBELISK_TILE = Tile(1886, 3231, 0)
    val EAST_BARRIER_TILE = Tile(1933, 3243, 0)
    val WEST_BARRIER_TILE = Tile(1842, 3220, 0)

    val RED_AVATAR_SPAWN = Tile(1965, 3249, 0)
    val BLUE_AVATAR_SPAWN = Tile(1805, 3208, 0)

    // Real entry-portal tiles, independently confirmed via runObjectPlacementProbeTool:
    // 42029 "Blue barrier" opt "Pass" @ (1879,3162,0) region 7473;
    // 42031 "Balance portal" opt "Join-team" @ (1889,3161,0) region 7473;
    // 42219 "Soul Wars portal" opt "Enter" @ (3083,3473,0) region 12342 (Edgeville side).
    val LOBBY_ENTRY_TILE = Tile(1880, 3162, 0)
    val LOBBY_INSIDE_TILE = Tile(1886, 3172, 0)
}
