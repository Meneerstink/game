package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.Tile

/**
 * PROJECT48 run 4 O18: the flight paths of the magic carpets (Void 634 data/area/kharidian_desert/magic_carpet/magic_carpet.patrols.toml,
 * recorded from OSRS videos; generated, do not edit by hand). Key = "<from>_to_<to>".
 */
internal object MagicCarpetRoutes {
    val PATHS: Map<String, List<Tile>> =
        mapOf(
            "shantay_pass_to_north_pollnivneach" to listOf(Tile(3308, 3106), Tile(3310, 3105), Tile(3313, 3104), Tile(3315, 3102), Tile(3317, 3100), Tile(3319, 3098), Tile(3319, 3097), Tile(3319, 3094), Tile(3321, 3092), Tile(3322, 3091), Tile(3322, 3088), Tile(3322, 3085), Tile(3324, 3083), Tile(3325, 3082), Tile(3325, 3079), Tile(3325, 3076), Tile(3325, 3073), Tile(3327, 3071), Tile(3329, 3069), Tile(3330, 3068), Tile(3332, 3066), Tile(3334, 3065), Tile(3336, 3063), Tile(3337, 3061), Tile(3339, 3059), Tile(3341, 3057), Tile(3343, 3055), Tile(3343, 3052), Tile(3343, 3049), Tile(3345, 3047), Tile(3346, 3046), Tile(3346, 3007)),
            "north_pollnivneach_to_shantay_pass" to listOf(Tile(3346, 3007), Tile(3346, 3046), Tile(3345, 3047), Tile(3343, 3049), Tile(3343, 3052), Tile(3343, 3055), Tile(3341, 3057), Tile(3339, 3059), Tile(3337, 3061), Tile(3336, 3063), Tile(3334, 3065), Tile(3332, 3066), Tile(3330, 3068), Tile(3329, 3069), Tile(3327, 3071), Tile(3325, 3073), Tile(3325, 3076), Tile(3325, 3079), Tile(3325, 3082), Tile(3324, 3083), Tile(3322, 3085), Tile(3322, 3088), Tile(3322, 3091), Tile(3321, 3092), Tile(3319, 3094), Tile(3319, 3097), Tile(3319, 3098), Tile(3317, 3100), Tile(3315, 3102), Tile(3313, 3104), Tile(3310, 3105), Tile(3308, 3106)),
            "bedabin_camp_to_shantay_pass" to listOf(Tile(3193, 3043), Tile(3199, 3049), Tile(3202, 3049), Tile(3208, 3055), Tile(3259, 3055), Tile(3262, 3058), Tile(3262, 3070), Tile(3280, 3088), Tile(3283, 3088), Tile(3295, 3094), Tile(3295, 3100), Tile(3295, 3103), Tile(3296, 3104), Tile(3297, 3104), Tile(3300, 3107), Tile(3307, 3107)),
            "shantay_pass_to_uzer" to listOf(Tile(3313, 3108), Tile(3314, 3109), Tile(3319, 3109), Tile(3322, 3112), Tile(3390, 3112), Tile(3391, 3111), Tile(3394, 3111), Tile(3396, 3113), Tile(3408, 3113), Tile(3409, 3112), Tile(3466, 3112)),
            "uzer_to_shantay_pass" to listOf(Tile(3466, 3112), Tile(3409, 3112), Tile(3408, 3113), Tile(3396, 3113), Tile(3395, 3112), Tile(3394, 3111), Tile(3391, 3111), Tile(3390, 3112), Tile(3322, 3112), Tile(3319, 3109), Tile(3314, 3109), Tile(3313, 3108)),
            "south_pollnivneach_to_nardah" to listOf(Tile(3352, 2938), Tile(3353, 2937), Tile(3363, 2937), Tile(3388, 2916), Tile(3393, 2916)),
            "nardah_to_south_pollnivneach" to listOf(Tile(3393, 2916), Tile(3388, 2916), Tile(3363, 2937), Tile(3353, 2937), Tile(3352, 2938)),
            "south_pollnivneach_to_sophanem" to listOf(Tile(3349, 2939), Tile(3342, 2932), Tile(3342, 2914), Tile(3336, 2908), Tile(3336, 2906), Tile(3330, 2902), Tile(3322, 2894), Tile(3320, 2894), Tile(3302, 2876), Tile(3302, 2875), Tile(3295, 2868), Tile(3295, 2844), Tile(3289, 2838), Tile(3289, 2817)),
            "sophanem_to_south_pollnivneach" to listOf(Tile(3289, 2838), Tile(3295, 2844), Tile(3295, 2868), Tile(3302, 2875), Tile(3302, 2876), Tile(3320, 2894), Tile(3322, 2894), Tile(3330, 2902), Tile(3336, 2906), Tile(3336, 2908), Tile(3342, 2914), Tile(3342, 2932), Tile(3349, 2939), Tile(3351, 2939)),
            "south_pollnivneach_to_menaphos" to listOf(Tile(3349, 2939), Tile(3342, 2932), Tile(3342, 2914), Tile(3330, 2902), Tile(3327, 2901), Tile(3327, 2901), Tile(3327, 2901), Tile(3289, 2863), Tile(3289, 2863), Tile(3289, 2862), Tile(3274, 2862), Tile(3271, 2859), Tile(3271, 2845), Tile(3256, 2845), Tile(3245, 2834), Tile(3245, 2815)),
            "menaphos_to_south_pollnivneach" to listOf(Tile(3245, 2834), Tile(3256, 2845), Tile(3271, 2845), Tile(3271, 2859), Tile(3274, 2862), Tile(3289, 2862), Tile(3289, 2863), Tile(3289, 2863), Tile(3327, 2901), Tile(3327, 2901), Tile(3327, 2901), Tile(3330, 2902), Tile(3342, 2914), Tile(3342, 2932), Tile(3349, 2939)),
        )
}
