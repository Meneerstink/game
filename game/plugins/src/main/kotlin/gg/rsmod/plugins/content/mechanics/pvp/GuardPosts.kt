package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile

/**
 * Stationed Deadman guard posts, owner 2026-09-19 ("100 % OSRS"): exactly the pins of the OSRS Wiki
 * "Guard (Deadman Mode)" Locations table (fetched 2026-09-19, `api.php?action=parse&page=Guard_(Deadman_Mode)`),
 * tile and plane verbatim, grouped under the guarded zone ([GuardedZones]) that contains them.
 *
 * - Pins the wiki lists twice (GE 3149,3474 and 3183,3474; Falador 3022,3340; Lumbridge Castle first
 *   floor 3206,3218, listed under both "Lumbridge" and "Lumbridge Castle") are one post.
 * - Not stationed: Kourend Castle and Prifddinas (not in the 667 world), and the Warriors' Guild,
 *   Ruins of Unkah and Wintertodt pins (not a guarded zone on the wiki Deadman map).
 * - The wiki does not say which pin holds the melee and which the ranged variant, so posts alternate
 *   per zone; Seers' Village and Catherby only have a ranged variant on the wiki ([CityGuards.variantFor]).
 */
object GuardPosts {
    data class Post(
        val city: String,
        val tile: Tile,
        val ranged: Boolean,
    )

    private class Pins(
        val zone: String,
        val plane: Int,
        vararg val xz: Int,
    )

    private val WIKI_PINS: List<Pins> =
        listOf(
            Pins("Void Knights' Outpost", 0, 2652, 2655, 2658, 2658, 2662, 2654),
            // Varrock
            Pins(
                "Varrock", 0,
                3185, 3440, 3265, 3429, 3266, 3428, 3198, 3429, 3209, 3433, 3213, 3449, 3210, 3385, 3212, 3388, 3211, 3406,
                3244, 3496, 3246, 3463, 3246, 3479, 3246, 3448, 3238, 3429, 3251, 3421, 3183, 3406, 3243, 3402,
            ),
            // Grand Exchange (inside the Varrock zone)
            Pins("Varrock", 0, 3176, 3508, 3193, 3489, 3149, 3474, 3183, 3474, 3145, 3508, 3149, 3474, 3148, 3477, 3180, 3474, 3183, 3474),
            // Varrock Palace
            Pins("Varrock", 0, 3205, 3463, 3222, 3464),
            Pins("Rellekka", 0, 2672, 3708, 2678, 3695, 2643, 3679, 2640, 3656, 2670, 3675, 2684, 3657, 2663, 3656),
            Pins("Neitiznot", 0, 2357, 3799, 2332, 3801, 2343, 3810),
            Pins(
                "East Ardougne", 0,
                2588, 3341, 2581, 3323, 2687, 3305, 2661, 3306, 2634, 3336, 2640, 3336, 2648, 3327, 2653, 3284, 2604, 3289,
                2608, 3297, 2613, 3341, 2579, 3298,
            ),
            // Ardougne Zoo (inside the East Ardougne zone)
            Pins("East Ardougne", 0, 2602, 3264),
            Pins("Port Phasmatys", 0, 3667, 3469, 3660, 3503, 3664, 3488, 3681, 3502, 3684, 3486, 3697, 3473),
            Pins(
                "Tree Gnome Stronghold", 0,
                2440, 3513, 2393, 3427, 2369, 3428, 2459, 3393, 2459, 3394, 2463, 3393, 2463, 3394, 2465, 3466, 2467, 3466,
                2479, 3428, 2438, 3480, 2378, 3416,
            ),
            Pins("Tree Gnome Stronghold", 1, 2445, 3416, 2445, 3433),
            // Grand Tree
            Pins("Tree Gnome Stronghold", 0, 2464, 3501, 2466, 3490),
            Pins("Falador", 0, 3025, 3360, 3022, 3340, 3058, 3371, 3045, 3339, 3010, 3379, 3022, 3340, 3023, 3339),
            Pins("Sophanem", 0, 3289, 2784, 3284, 2806, 3304, 2791, 3305, 2804, 3309, 2779, 3295, 2758),
            Pins("Seers' Village bank", 0, 2723, 3492, 2728, 3492),
            Pins("Lumbridge", 0, 3208, 3234, 3221, 3222, 3221, 3238, 3232, 3231, 3236, 3217, 3209, 3204),
            Pins("Lumbridge", 1, 3206, 3218),
            Pins("Lumbridge", 2, 3228, 3219),
            Pins("Lumbridge", 1, 3206, 3218),
            Pins("Lumbridge", 2, 3206, 3219),
            Pins("Yanille", 0, 2599, 3087, 2612, 3092, 2614, 3104, 2575, 3090, 2540, 3092, 2558, 3090),
            Pins("Catherby bank", 0, 2807, 3440),
            Pins("Jatizso", 0, 2393, 3801, 2405, 3808, 2413, 3803, 2413, 3819),
        )

    /** One entry per distinct wiki pin; [Post.ranged] alternates within each zone. */
    val ALL: List<Post> =
        run {
            val seen = HashSet<Tile>()
            val perZone = HashMap<String, Int>()
            val posts = ArrayList<Post>()
            WIKI_PINS.forEach { pins ->
                for (i in pins.xz.indices step 2) {
                    val tile = Tile(pins.xz[i], pins.xz[i + 1], pins.plane)
                    if (!seen.add(tile)) continue
                    val index = perZone.merge(pins.zone, 1, Int::plus)!! - 1
                    val rangedOnly = CityGuards.variantFor(pins.zone).melee == null
                    posts += Post(pins.zone, tile, ranged = rangedOnly || index % 2 == 1)
                }
            }
            posts
        }
}
