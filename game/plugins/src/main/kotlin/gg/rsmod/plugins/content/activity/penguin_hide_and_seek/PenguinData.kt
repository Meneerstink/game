package gg.rsmod.plugins.content.activity.penguin_hide_and_seek

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.plugins.api.cfg.Npcs

/**
 * Q-056 (part 3/3): Penguin Hide and Seek. Location/disguise data sourced verbatim from Void's
 * `data/activity/penguin_hide_and_seek/{penguin_locations_easy,penguin_locations_hard}.toml`
 * (plain overworld tile coordinates, not cache ids, so they carry over unchanged between donor
 * caches). 4 hard-list entries (`prifddinas_south_gate`, `south_of_elf_camp`, `lletya`,
 * `north_of_tyras_camp`) were dropped - Song of the Elves / post-2011 elf-city content, matching
 * this project's own precedent of excluding that era's content elsewhere (Farming/Thieving
 * batches).
 *
 * Disguise NPC ids (barrel/bush/cactus/crate/rock/toadstool) confirmed present in this project's
 * own generated `Npcs.kt` (native to this 667 cache), with a real "Spy-on" option confirmed via
 * `runNpcDefProbeTool` against ids 8094/8105/8107. Seasonal disguises (`snowman_penguin`,
 * `pumpkin_penguin`) are NOT ported: `pumpkin_penguin`'s id (8077) resolves to `Npcs.CHILD_8077`
 * in this cache, not a penguin - a real content mismatch between Void's rev-634 cache and this
 * project's 667 cache, so it was left out rather than guessed at a substitute id.
 */
enum class PenguinDisguise(val npcId: Int) {
    BARREL(Npcs.BARREL),
    BUSH(Npcs.BUSH),
    CACTUS(Npcs.CACTUS),
    CRATE(Npcs.CRATE_8108),
    ROCK(Npcs.ROCK_8109),
    TOADSTOOL(Npcs.TOADSTOOL),
}

data class PenguinSpot(val disguise: PenguinDisguise, val tile: Tile)

val PenguinEasySpots =
    listOf(
        PenguinSpot(PenguinDisguise.CACTUS, Tile(3311, 3163, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2532, 3588, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2740, 3233, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2458, 3095, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2846, 3059, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(2872, 3603, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(3339, 3424, 0)),
        PenguinSpot(PenguinDisguise.CRATE, Tile(3116, 3335, 0)),
        PenguinSpot(PenguinDisguise.BARREL, Tile(2732, 5326, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2311, 3519, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2938, 2978, 0)),
        PenguinSpot(PenguinDisguise.BARREL, Tile(2810, 3383, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(2606, 3006, 0)),
        PenguinSpot(PenguinDisguise.CRATE, Tile(3314, 3551, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2513, 3154, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2386, 3454, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2949, 3510, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(3013, 3501, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(2524, 3625, 0)),
        PenguinSpot(PenguinDisguise.TOADSTOOL, Tile(3211, 3156, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(3349, 3310, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2633, 3501, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2989, 3121, 0)),
        PenguinSpot(PenguinDisguise.CRATE, Tile(2870, 3156, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2398, 3361, 0)),
        PenguinSpot(PenguinDisguise.CRATE, Tile(2440, 3206, 0)),
        PenguinSpot(PenguinDisguise.BARREL, Tile(2662, 3152, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(2675, 3717, 0)),
        PenguinSpot(PenguinDisguise.CACTUS, Tile(3257, 3055, 0)),
        PenguinSpot(PenguinDisguise.CACTUS, Tile(3252, 2963, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(2859, 3506, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(2733, 3283, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(3110, 3152, 0)),
        PenguinSpot(PenguinDisguise.TOADSTOOL, Tile(2420, 4472, 0)),
    )

val PenguinHardSpots =
    listOf(
        PenguinSpot(PenguinDisguise.BARREL, Tile(2751, 2700, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2802, 2806, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(2340, 3064, 0)),
        PenguinSpot(PenguinDisguise.CRATE, Tile(3824, 3562, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2578, 2909, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(3600, 3487, 0)),
        PenguinSpot(PenguinDisguise.CRATE, Tile(3637, 3486, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2355, 3848, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(2413, 3846, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(2438, 3050, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(2909, 10210, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(2118, 3942, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2534, 3871, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(3472, 3392, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(3545, 3439, 0)),
        PenguinSpot(PenguinDisguise.TOADSTOOL, Tile(3417, 3438, 0)),
        PenguinSpot(PenguinDisguise.BARREL, Tile(3738, 3001, 0)),
        PenguinSpot(PenguinDisguise.BUSH, Tile(2353, 3834, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(2357, 3797, 0)),
        PenguinSpot(PenguinDisguise.CRATE, Tile(2322, 3658, 0)),
        PenguinSpot(PenguinDisguise.BARREL, Tile(3654, 3491, 0)),
        PenguinSpot(PenguinDisguise.CACTUS, Tile(3276, 2797, 0)),
        PenguinSpot(PenguinDisguise.CACTUS, Tile(3433, 3000, 0)),
        PenguinSpot(PenguinDisguise.CACTUS, Tile(3281, 2908, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(3236, 3927, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(3019, 3866, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(3108, 3837, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(2991, 3824, 0)),
        PenguinSpot(PenguinDisguise.ROCK, Tile(3169, 3650, 0)),
    )

/** Persisted per-player state, same `AttributeKey(persistenceKey=...)` pattern Slayer points use. */
val PenguinPoints = AttributeKey<Int>(persistenceKey = "penguin_points")
val PenguinWeek = AttributeKey<Int>(persistenceKey = "penguin_week")

/** Bitmask (bit N = spot index N already spied on this week), same Int-only persistence rule Slayer follows. */
val PenguinFoundMask = AttributeKey<Int>(persistenceKey = "penguin_found_mask")
