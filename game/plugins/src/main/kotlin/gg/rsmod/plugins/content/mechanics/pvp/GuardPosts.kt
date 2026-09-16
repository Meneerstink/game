package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile

/**
 * Stationed Deadman guard posts - one entry per red pin the owner placed on the map screenshots in
 * `C:\RSPS\foto` (2026-09-16: "Put all the guards on these spots"). Tiles were computed from the
 * pin tips with per-screenshot calibration (Varrock/Ardougne/Yanille/Grand Tree at 4 px per tile
 * anchored on the Grand Exchange, Ardougne north bank, Yanille walls and the Stronghold south gate;
 * Lumbridge/Falador/Catherby at 5.66 px per tile; Camelot/Warriors' Guild at 8 px per tile;
 * Rellekka at 2.83 px per tile) and are accurate to roughly +/-3 tiles. Every post is snapped to
 * the nearest walkable tile at boot ([CityGuards.spawnStationedGuards]); adjust a coordinate here
 * after a live walk-through if a guard ended up on the wrong side of a wall.
 *
 * [ranged] alternates within each city so every city gets both melee and ranged guards (OSRS:
 * "Attack Styles: Slash or Ranged (varies by location/variant)").
 */
object GuardPosts {
    data class Post(
        val city: String,
        val tile: Tile,
        val ranged: Boolean,
    )

    private fun city(
        name: String,
        vararg xz: Pair<Int, Int>,
    ): List<Post> = xz.mapIndexed { index, (x, z) -> Post(name, Tile(x, z, 0), ranged = index % 2 == 1) }

    val ALL: List<Post> =
        city(
            "Varrock",
            3145 to 3508, 3176 to 3508, 3244 to 3496, 3193 to 3489, 3246 to 3479, 3148 to 3477,
            3149 to 3474, 3180 to 3474, 3183 to 3474, 3222 to 3464, 3205 to 3463, 3246 to 3463,
            3213 to 3449, 3246 to 3448, 3185 to 3440, 3208 to 3433, 3198 to 3429, 3238 to 3429,
            3265 to 3429, 3266 to 3428, 3251 to 3421, 3183 to 3406, 3211 to 3406, 3243 to 3402,
            3210 to 3388, 3212 to 3385,
        ) +
            city(
                "Falador",
                2991 to 3376, 3025 to 3370, 3002 to 3363, 3000 to 3349, 3000 to 3348, 3016 to 3348,
            ) +
            city("Catherby bank", 2807 to 3439) +
            city("Seers' Village bank", 2723 to 3492, 2728 to 3492) +
            city(
                "Warriors' Guild",
                2870 to 3553, 2840 to 3552, 2852 to 3550, 2845 to 3544, 2846 to 3540, 2858 to 3543,
                2868 to 3543,
            ) +
            city(
                "Rellekka",
                2658 to 3677, 2662 to 3668, 2636 to 3658, 2637 to 3656, 2656 to 3654, 2666 to 3641,
                2635 to 3640, 2651 to 3640,
            ) +
            city(
                "East Ardougne",
                2588 to 3343, 2612 to 3343, 2634 to 3338, 2640 to 3338, 2648 to 3329, 2580 to 3325,
                2660 to 3308, 2686 to 3307, 2579 to 3300, 2608 to 3299, 2604 to 3291, 2652 to 3286,
                2602 to 3266,
            ) +
            city(
                "Tree Gnome Stronghold",
                2440 to 3503, 2464 to 3491, 2466 to 3480, 2438 to 3470, 2465 to 3456, 2467 to 3456,
                2369 to 3418, 2479 to 3418, 2393 to 3417, 2378 to 3406, 2459 to 3384, 2463 to 3383,
            ) +
            city(
                "Yanille",
                2612 to 3101, 2538 to 3089, 2610 to 3089, 2557 to 3087, 2574 to 3087, 2598 to 3084,
            ) +
            city(
                "Lumbridge",
                3232 to 3246, 3214 to 3240, 3247 to 3236, 3232 to 3223, 3253 to 3216, 3215 to 3204,
            )
}
