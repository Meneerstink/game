package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile

/**
 * Stationed Deadman guard posts - one entry per red pin the owner placed on the map screenshots in
 * `C:\RSPS\foto` (2026-09-16: "Put all the guards on these spots"). Tiles were computed from the
 * pin tips with a per-screenshot scale and anchor, each calibrated on two or more real spawn
 * tiles from the `areas/spawns` files (bankers, shopkeepers, named npcs) that are drawn as icons
 * on the same map (see the comment above each city). Expected accuracy is +/-2 tiles. Every post
 * is snapped to the nearest walkable tile inside a guarded zone at boot
 * ([CityGuards.spawnStationedGuards]); adjust a coordinate here after a live walk-through if a guard
 * ended up on the wrong side of a wall. 2026-09-17: three pins (two Ardougne north wall, one Yanille
 * south-east corner) sat 1-3 tiles outside the exact wiki polygons and were moved inside.
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
        // Varrock + GE: 4 px/tile, anchored on the GE "$" (3165,3487), checked against the west bank
        // (bankers x 3180/3191, z 3433-3445) and the east bank (bankers 3251-3256, z 3418).
        city(
            "Varrock",
            3145 to 3508, 3176 to 3508, 3244 to 3496, 3193 to 3489, 3246 to 3479, 3148 to 3477,
            3149 to 3474, 3180 to 3474, 3183 to 3474, 3222 to 3464, 3205 to 3463, 3246 to 3463,
            3213 to 3449, 3246 to 3448, 3185 to 3440, 3208 to 3433, 3198 to 3429, 3238 to 3429,
            3265 to 3429, 3266 to 3428, 3251 to 3421, 3183 to 3406, 3211 to 3406, 3243 to 3402,
            3210 to 3388, 3212 to 3385,
        ) +
            // Falador: 4 px/tile, anchored on the west bank "$" (bankers 2945-2949,3366) and checked
            // against the east bank "$" (bankers 3010-3014,3353) and the Party Room (Party Pete 3052,3373).
            city(
                "Falador",
                3011 to 3379, 3059 to 3371, 3026 to 3360, 3023 to 3340, 3024 to 3339, 3046 to 3339,
            ) +
            // Catherby: 8 px/tile, bank building 2806-2812 x 3437-3445 (bankers at z 3443).
            city("Catherby bank", 2808 to 3440) +
            // Seers' Village: bank building 2721-2730 x 3489-3498 (bankers at z 3495), pins in the customer half.
            city("Seers' Village bank", 2724 to 3493, 2728 to 3493) +
            // Warriors' Guild: 8 px/tile, whole ground floor 2837-2876 x 3535-3558 (Ajjat 2851,3549,
            // Lidio 2842,3547, Lilly 2845,3549, Jimmy 2872,3535 all land on their rooms).
            city(
                "Warriors' Guild",
                2870 to 3553, 2840 to 3552, 2852 to 3550, 2845 to 3544, 2846 to 3540, 2858 to 3543,
                2868 to 3543,
            ) +
            // Rellekka: 2.83 px/tile, anchored on Yrsa's clothes shop icon (2625,3675) and checked
            // against the harbour anchor icon (Sailor 2629,3693).
            city(
                "Rellekka",
                2659 to 3699, 2663 to 3690, 2637 to 3680, 2638 to 3679, 2657 to 3676, 2667 to 3663,
                2636 to 3663, 2652 to 3663,
            ) +
            // East Ardougne: 4 px/tile, anchored on the north bank "$" (2616,3332), checked against the
            // south bank "$" (2649-2658 x 3280-3287) and the market bakers (2655/2669, 3310).
            city(
                "East Ardougne",
                2588 to 3341, 2612 to 3341, 2634 to 3338, 2640 to 3338, 2648 to 3329, 2580 to 3325,
                2660 to 3308, 2686 to 3307, 2579 to 3300, 2608 to 3299, 2604 to 3291, 2652 to 3286,
                2602 to 3266,
            ) +
            // Tree Gnome Stronghold: 4 px/tile, anchored on the agility course rectangle (z 3418-3438)
            // and the stronghold bank "$" (2442-2448 x 3417-3427).
            city(
                "Tree Gnome Stronghold",
                2440 to 3514, 2464 to 3502, 2466 to 3491, 2438 to 3481, 2465 to 3467, 2467 to 3467,
                2369 to 3429, 2479 to 3429, 2393 to 3428, 2378 to 3417, 2459 to 3395, 2463 to 3394,
            ) +
            // Yanille: 4 px/tile, anchored on the bank "$" (bank 2609-2616 x 3088-3097) and the town walls.
            city(
                "Yanille",
                2614 to 3101, 2543 to 3089, 2615 to 3089, 2561 to 3087, 2578 to 3087, 2602 to 3084,
            ) +
            // Lumbridge: 8 px/tile, anchored on Bob's Axes (Bob 3228,3203), checked against the general
            // store (Shopkeeper 3212,3240), the church (Father Aereck 3244,3205) and the river Lum.
            city(
                "Lumbridge",
                3220 to 3238, 3207 to 3235, 3231 to 3231, 3220 to 3222, 3235 to 3217, 3208 to 3204,
            )
}
