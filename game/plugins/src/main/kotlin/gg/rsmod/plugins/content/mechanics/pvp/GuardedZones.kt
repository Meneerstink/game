package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile

/**
 * Deadman Mode guarded (safe) zones - owner instruction 2026-09-16/17: safe and death zones follow
 * the OSRS Deadman Mode map exactly ("zie https://oldschool.runescape.wiki/w/Deadman_Mode#mapFullscreen
 * voor waar het safe en dangerous is").
 *
 * SOURCE VERIFIED: every polygon below is the wiki's own map data, fetched 2026-09-17 from
 * `https://oldschool.runescape.wiki/api.php?action=query&prop=mapdata&titles=Deadman%20Mode`
 * (the GeoJSON behind that map view), vertex for vertex, in real tile coordinates. The earlier
 * hand-estimated rectangles (calibrated from screenshots) were too generous: they reached streets
 * outside the city walls, which is why guards "teleported onto you in a dangerous zone" - those
 * tiles were inside a rectangle but outside the real Deadman city.
 *
 * Wiki areas that do not exist in this revision-667 world (Kourend Castle, Prifddinas) are omitted.
 * Wiki: "No dungeons or Rooftop Agility Courses are considered safezones, even if
 * accessible from safe city limits" - dungeons live at z >= 6400 in this map data, outside every
 * polygon; upper floors (height 1-3) of buildings inside a city are treated as part of the city.
 */
object GuardedZones {
    /**
     * A simple polygon in tile coordinates (closed implicitly). Containment is tested for the
     * tile's centre (x + 0.5, z + 0.5) with the even-odd rule, so a wiki edge that runs exactly
     * along a wall line puts the wall's own tile on the inside.
     */
    class Zone(
        val name: String,
        /** Flattened x0, z0, x1, z1, ... vertices. */
        private val vertices: IntArray,
        /** Heights considered part of the zone; upper floors count as the city. */
        val heights: IntRange = 0..3,
    ) {
        val minX: Int = (vertices.indices step 2).minOf { vertices[it] }
        val maxX: Int = (vertices.indices step 2).maxOf { vertices[it] }
        val minZ: Int = (1 until vertices.size step 2).minOf { vertices[it] }
        val maxZ: Int = (1 until vertices.size step 2).maxOf { vertices[it] }

        /** A tile guaranteed to be inside the polygon (for tests/tools): the first inside tile
         * scanning the bounding box from its centre outward. */
        val sample: Tile by lazy {
            val cx = (minX + maxX) / 2
            val cz = (minZ + maxZ) / 2
            var found: Tile? = null
            var r = 0
            while (found == null && r <= maxOf(maxX - minX, maxZ - minZ)) {
                for (dx in -r..r) {
                    for (dz in -r..r) {
                        if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != r) continue
                        val t = Tile(cx + dx, cz + dz, 0)
                        if (contains(t)) {
                            found = t
                            break
                        }
                    }
                    if (found != null) break
                }
                r++
            }
            found ?: error("zone $name has no interior tile")
        }

        fun contains(tile: Tile): Boolean {
            if (tile.height !in heights) return false
            if (tile.x < minX || tile.x > maxX || tile.z < minZ || tile.z > maxZ) return false
            val px = tile.x + 0.5
            val pz = tile.z + 0.5
            var inside = false
            val n = vertices.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = vertices[i * 2].toDouble()
                val zi = vertices[i * 2 + 1].toDouble()
                val xj = vertices[j * 2].toDouble()
                val zj = vertices[j * 2 + 1].toDouble()
                if ((zi > pz) != (zj > pz)) {
                    val cross = (xj - xi) * (pz - zi) / (zj - zi) + xi
                    if (px < cross) inside = !inside
                }
                j = i
            }
            return inside
        }
    }

    val ZONES: List<Zone> =
        listOf(
            Zone(
                "Varrock",
                intArrayOf(
                    3182, 3382, 3242, 3381, 3242, 3380, 3245, 3380, 3245, 3382, 3253, 3382, 3253, 3380, 3265, 3380,
                    3265, 3376, 3287, 3376, 3290, 3379, 3290, 3384, 3289, 3385, 3289, 3390, 3288, 3391, 3288, 3407,
                    3287, 3408, 3277, 3408, 3274, 3411, 3274, 3420, 3274, 3437, 3271, 3437, 3271, 3464, 3263, 3472,
                    3263, 3492, 3262, 3493, 3255, 3493, 3252, 3496, 3252, 3502, 3235, 3502, 3229, 3508, 3200, 3508,
                    // Owner 2026-09-20: "in some part of the ge is dangerous this is a big bug ! 3164, 3516, 0 is the
                    // location [...] the whole grand ex should be safe". The wiki polygon's north edge dips to
                    // z 3515 between x 3162 and x 3167 - the recess of the Grand Exchange's north gateway - which
                    // left a 6x3 wedge of dangerous tiles (3160..3167, 3515..3517) standing in the middle of the
                    // Grand Exchange courtyard. The edge is run straight along z 3518 instead, so the whole
                    // enclosure is one safe zone; `GuardedZonesTests` sweeps every tile of it as a guard.
                    3190, 3518, 3142, 3518, 3138, 3514, 3138, 3494,
                    3141, 3491, 3141, 3486, 3138, 3483, 3138, 3472, 3142, 3467,
                    // Owner 2026-09-23: "in grand exchange mag nooit dangerous zijn" (3158,3465 was Dangerous). The wiki
                    // edge ran along z 3467, leaving the strip inside the south wall and the gate passage Dangerous. The
                    // edge now follows the inside of the GE south wall (z 3465, with its diagonal corners) and, at the
                    // gatehouse, the gatehouse front (z 3462) - the owner's "red line" where Dangerous turns Safe.
                    3149, 3467, 3151, 3465, 3159, 3465, 3159, 3462, 3171, 3462, 3171, 3465, 3179, 3465, 3181, 3467,
                    3187, 3467, 3187, 3464, 3185, 3462,
                    3185, 3458, 3186, 3457, 3190, 3457, 3198, 3448, 3180, 3448, 3174, 3448, 3174, 3399, 3182, 3399,
                ),
            ),
            Zone(
                "Falador",
                intArrayOf(
                    2936, 3320, 2936, 3354, 2937, 3354, 2937, 3357, 2936, 3357, 2936, 3388, 2941, 3393, 2943, 3393,
                    2945, 3395, 2949, 3395, 2950, 3394, 2956, 3394, 2957, 3395, 2985, 3395, 2987, 3393, 2996, 3393,
                    2997, 3394, 3002, 3394, 3003, 3395, 3008, 3395, 3011, 3392, 3020, 3392, 3022, 3390, 3040, 3390,
                    3041, 3389, 3047, 3389, 3048, 3390, 3061, 3390, 3066, 3385, 3066, 3369, 3060, 3363, 3060, 3331,
                    3061, 3330, 3061, 3329, 3060, 3328, 3059, 3328, 3058, 3329, 3025, 3329, 3023, 3327, 3016, 3327,
                    3012, 3323, 3008, 3323, 3008, 3324, 3005, 3324, 3005, 3323, 3002, 3323, 2995, 3316, 2992, 3316,
                    2985, 3309, 2967, 3309, 2966, 3310, 2957, 3310, 2956, 3311, 2942, 3311, 2940, 3313, 2940, 3320,
                ),
            ),
            Zone(
                "Lumbridge",
                intArrayOf(
                    3233, 3264, 3213, 3264, 3213, 3257, 3201, 3257, 3201, 3219, 3200, 3219, 3200, 3218, 3201, 3218,
                    3201, 3204, 3204, 3201, 3213, 3201, 3215, 3203, 3221, 3203, 3226, 3208, 3228, 3206, 3227, 3205,
                    3227, 3202, 3228, 3201, 3230, 3201, 3230, 3195, 3238, 3195, 3238, 3191, 3247, 3191, 3247, 3190,
                    3255, 3190, 3257, 3194, 3258, 3200, 3259, 3204, 3258, 3208, 3256, 3214, 3252, 3216, 3251, 3219,
                    3245, 3219, 3243, 3221, 3243, 3227, 3239, 3230, 3238, 3233, 3238, 3235, 3237, 3236, 3237, 3239,
                    3236, 3241, 3236, 3243, 3236, 3247, 3235, 3249, 3235, 3257, 3233, 3257,
                ),
            ),
            Zone("Catherby bank", intArrayOf(2806, 3438, 2806, 3446, 2813, 3446, 2813, 3438)),
            Zone(
                "Seers' Village bank",
                intArrayOf(2721, 3498, 2721, 3497, 2719, 3497, 2719, 3494, 2721, 3494, 2721, 3490, 2724, 3490, 2724, 3487, 2728, 3487, 2728, 3490, 2731, 3490, 2731, 3498),
                // OSRS Wiki Deadman Mode: "Seers' Village bank (Floor 0)" - the ground floor only.
                heights = 0..0,
            ),
            Zone(
                "East Ardougne",
                intArrayOf(
                    2559, 3265, 2559, 3328, 2560, 3328, 2560, 3338, 2564, 3342, 2615, 3342, 2617, 3340, 2668, 3340,
                    2674, 3334, 2682, 3334, 2688, 3328, 2688, 3314, 2687, 3313, 2687, 3312, 2688, 3311, 2688, 3306,
                    2688, 3299, 2686, 3297, 2686, 3290, 2688, 3288, 2688, 3283, 2688, 3274, 2685, 3274, 2685, 3266,
                    2683, 3266, 2683, 3274, 2676, 3274, 2676, 3271, 2675, 3270, 2675, 3269, 2672, 3266, 2671, 3266,
                    2670, 3265, 2667, 3265, 2662, 3267, 2659, 3268, 2658, 3269, 2655, 3269, 2653, 3266, 2650, 3265,
                    2648, 3264, 2638, 3264, 2637, 3263, 2626, 3263, 2625, 3264, 2612, 3264, 2611, 3263, 2611, 3258,
                    2610, 3257, 2603, 3257, 2602, 3258, 2602, 3264, 2586, 3264, 2586, 3263, 2583, 3262, 2581, 3259,
                    2573, 3259, 2571, 3262, 2568, 3262, 2568, 3265,
                ),
            ),
            Zone(
                "Rellekka",
                intArrayOf(
                    2603, 3655, 2607, 3654, 2611, 3653, 2616, 3653, 2628, 3646, 2635, 3646, 2638, 3645, 2645, 3645,
                    2648, 3644, 2652, 3645, 2657, 3644, 2659, 3645, 2667, 3645, 2670, 3645, 2672, 3644, 2676, 3645,
                    2685, 3645, 2692, 3649, 2693, 3653, 2689, 3661, 2689, 3664, 2691, 3667, 2691, 3670, 2690, 3672,
                    2690, 3677, 2693, 3684, 2693, 3690, 2690, 3698, 2693, 3703, 2693, 3706, 2691, 3710, 2691, 3712,
                    2656, 3713, 2655, 3712, 2655, 3711, 2652, 3711, 2652, 3709, 2655, 3709, 2655, 3708, 2654, 3707,
                    2654, 3706, 2655, 3705, 2655, 3704, 2654, 3703, 2654, 3702, 2652, 3700, 2652, 3694, 2650, 3693,
                    2649, 3692, 2647, 3692, 2646, 3691, 2646, 3690, 2644, 3688, 2642, 3688, 2642, 3692, 2640, 3692,
                    2640, 3688, 2639, 3688, 2637, 3686, 2637, 3684, 2636, 3684, 2635, 3683, 2634, 3683, 2633, 3684,
                    2633, 3686, 2632, 3686, 2632, 3685, 2629, 3685, 2629, 3684, 2628, 3683, 2628, 3682, 2627, 3681,
                    2626, 3681, 2625, 3680, 2622, 3680, 2622, 3682, 2620, 3682, 2620, 3679, 2619, 3678, 2617, 3678,
                    2615, 3676, 2613, 3676, 2611, 3674, 2611, 3673, 2609, 3672, 2609, 3671, 2606, 3671, 2606, 3667,
                    2600, 3661, 2600, 3659, 2603, 3656,
                ),
            ),
            Zone(
                "Tree Gnome Stronghold",
                intArrayOf(
                    2459, 3384, 2464, 3384, 2466, 3386, 2466, 3389, 2468, 3391, 2471, 3391, 2472, 3390, 2476, 3390,
                    2477, 3389, 2481, 3389, 2482, 3390, 2485, 3390, 2486, 3389, 2488, 3389, 2488, 3390, 2489, 3391,
                    2493, 3391, 2494, 3390, 2501, 3390, 2502, 3391, 2505, 3391, 2506, 3392, 2506, 3395, 2505, 3396,
                    2502, 3396, 2494, 3404, 2494, 3408, 2495, 3409, 2495, 3412, 2496, 3413, 2496, 3416, 2498, 3418,
                    2498, 3429, 2494, 3433, 2494, 3438, 2496, 3440, 2496, 3456, 2492, 3456, 2491, 3457, 2491, 3459,
                    2490, 3461, 2490, 3469, 2488, 3471, 2488, 3474, 2487, 3474, 2487, 3480, 2488, 3482, 2490, 3484,
                    2493, 3484, 2493, 3494, 2491, 3496, 2491, 3497, 2493, 3499, 2493, 3505, 2488, 3509, 2488, 3517,
                    2479, 3517, 2475, 3513, 2474, 3513, 2472, 3511, 2470, 3511, 2469, 3510, 2465, 3510, 2464, 3511,
                    2461, 3511, 2459, 3513, 2457, 3513, 2453, 3517, 2448, 3517, 2448, 3520, 2432, 3520, 2429, 3523,
                    2428, 3523, 2424, 3521, 2415, 3521, 2413, 3522, 2407, 3522, 2402, 3525, 2400, 3525, 2399, 3524,
                    2394, 3524, 2393, 3523, 2385, 3523, 2384, 3522, 2383, 3522, 2382, 3521, 2381, 3521, 2380, 3520,
                    2380, 3509, 2377, 3506, 2377, 3500, 2375, 3498, 2375, 3487, 2377, 3485, 2377, 3479, 2380, 3476,
                    2381, 3473, 2381, 3464, 2375, 3458, 2375, 3447, 2371, 3443, 2369, 3432, 2369, 3423, 2375, 3417,
                    2375, 3414, 2376, 3412, 2378, 3410, 2381, 3408, 2384, 3407, 2390, 3407, 2392, 3407, 2396, 3410,
                    2398, 3410, 2400, 3405, 2401, 3404, 2405, 3404, 2407, 3407, 2407, 3410, 2403, 3412, 2410, 3412,
                    2414, 3401, 2416, 3399, 2416, 3398, 2421, 3393, 2427, 3393, 2429, 3391, 2435, 3388, 2439, 3388,
                    2440, 3389, 2441, 3389, 2443, 3391, 2446, 3391, 2447, 3390, 2449, 3390, 2450, 3391, 2455, 3391,
                    2457, 3389, 2457, 3386,
                ),
            ),
            Zone(
                "Yanille",
                intArrayOf(
                    2543, 3109, 2541, 3107, 2539, 3107, 2539, 3077, 2541, 3077, 2543, 3075, 2582, 3075, 2584, 3073,
                    2589, 3073, 2591, 3075, 2619, 3075, 2620, 3076, 2620, 3097, 2608, 3109,
                ),
            ),
            Zone(
                "Jatizso",
                intArrayOf(
                    2407, 3797, 2421, 3797, 2423, 3799, 2423, 3821, 2424, 3822, 2424, 3825, 2423, 3826, 2420, 3826,
                    2418, 3824, 2413, 3824, 2413, 3825, 2412, 3826, 2409, 3826, 2408, 3825, 2407, 3824, 2406, 3823,
                    2406, 3819, 2403, 3819, 2402, 3818, 2402, 3809, 2389, 3809, 2388, 3808, 2388, 3807, 2387, 3807,
                    2386, 3806, 2386, 3803, 2387, 3802, 2388, 3802, 2388, 3796, 2387, 3796, 2386, 3795, 2386, 3792,
                    2386, 3791, 2390, 3791, 2391, 3792, 2398, 3792, 2399, 3793, 2406, 3793, 2407, 3794,
                ),
            ),
            Zone(
                "Neitiznot",
                intArrayOf(
                    2329, 3799, 2329, 3812, 2333, 3812, 2335, 3814, 2335, 3815, 2338, 3815, 2339, 3816, 2339, 3817,
                    2347, 3817, 2347, 3813, 2349, 3811, 2350, 3811, 2351, 3810, 2355, 3809, 2357, 3806, 2358, 3806,
                    2360, 3808, 2363, 3808, 2366, 3804, 2367, 3802, 2367, 3798, 2366, 3797, 2366, 3796, 2364, 3794,
                    2364, 3792, 2363, 3791, 2361, 3791, 2357, 3787, 2354, 3786, 2353, 3786, 2351, 3788, 2351, 3796,
                    2344, 3796, 2343, 3795, 2331, 3795, 2331, 3797,
                ),
            ),
            Zone(
                "Port Phasmatys",
                intArrayOf(
                    3661, 3509, 3655, 3509, 3652, 3506, 3652, 3474, 3649, 3471, 3649, 3458, 3651, 3456, 3659, 3456,
                    3661, 3454, 3664, 3454, 3665, 3453, 3674, 3453, 3677, 3455, 3692, 3455, 3694, 3453, 3701, 3453,
                    3703, 3454, 3708, 3454, 3711, 3457, 3711, 3461, 3709, 3463, 3709, 3466, 3702, 3473, 3702, 3477,
                    3700, 3477, 3700, 3481, 3699, 3481, 3699, 3482, 3698, 3482, 3698, 3480, 3690, 3484, 3690, 3486,
                    3691, 3487, 3691, 3493, 3690, 3493, 3690, 3494, 3689, 3494, 3689, 3491, 3688, 3491, 3687, 3492,
                    3687, 3493, 3686, 3494, 3686, 3500, 3685, 3501, 3685, 3502, 3687, 3502, 3687, 3504, 3684, 3504,
                    3683, 3505, 3683, 3506, 3682, 3507, 3681, 3507, 3681, 3518, 3677, 3518, 3668, 3509,
                ),
            ),
            Zone("Sophanem", intArrayOf(3273, 2810, 3324, 2811, 3324, 2747, 3273, 2748)),
            Zone(
                "Tutorial Island",
                intArrayOf(3154, 3136, 3054, 3136, 3054, 3057, 3085, 3040, 3085, 3004, 3125, 3004, 3125, 3037, 3154, 3064),
            ),
            Zone("Void Knights' Outpost", intArrayOf(2624, 2560, 2624, 2681, 2688, 2681, 2688, 2560)),
        )

    fun zoneAt(tile: Tile): Zone? = ZONES.firstOrNull { it.contains(tile) }

    /**
     * Owner 2026-09-21: "make sure the poh is a safezone just like osrs deadmanmode poh". A player-owned house is not
     * a polygon on the world map - it is a private instance - so it is folded in here rather than added to [ZONES],
     * which makes it safe everywhere this one predicate is already consulted: the PvP gate, the guards, the danger
     * warning and the HUD. (The PK skull already stops counting down inside any instance - see
     * `PvpSkull.tickPauseTracking` - so no separate skull rule is needed.)
     */
    fun contains(tile: Tile): Boolean =
        zoneAt(tile) != null || gg.rsmod.plugins.content.areas.poh.PlayerHouse.isSafeTile(tile) ||
            // Death's Office (2026-09-26) is a private instance in OSRS and "a safe area"; here it is one shared map square,
            // so it is folded in like the house: nobody can attack anybody there.
            gg.rsmod.plugins.content.areas.deathsoffice.DeathsOfficeArea.inOffice(tile) ||
            // The AFK skill basement under the Grand Exchange hall (owner 2026-09-26, "safe zone"): its own map square.
            gg.rsmod.plugins.content.newplayer.AfkArea.contains(tile)
}
