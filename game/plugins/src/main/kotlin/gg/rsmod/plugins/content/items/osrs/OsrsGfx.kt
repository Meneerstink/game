package gg.rsmod.plugins.content.items.osrs

/**
 * OSRS spotanims imported into both rev-667 caches by `OsrsFxImportTool` batch "fxpilot" (tx-20260914-045201, owner decision (e)
 * 2026-09-14). Local id = the rev-667 spotanim id; the OSRS id and Jagex name come from RuneLite `gameval/SpotanimID.java`.
 * Launch heights, delays and curves are not part of the cache: the callers keep their existing 667 values (ADAPTED).
 */
object OsrsGfx {
    const val SOTD_SPECIAL_START = 2982 // OSRS 1228
    const val SOTD_SPECIAL_EXTRA = 2983 // OSRS 1229
    const val SANGUINESTI_STAFF_TRAVEL = 2984 // OSRS 1539
    const val SANGUINESTI_STAFF_CASTING = 2985 // OSRS 1540
    const val SANGUINESTI_STAFF_IMPACT = 2986 // OSRS 1541
    const val SANGUINESTI_STAFF_HEAL = 2987 // OSRS 1542
    const val TOXIC_TOTS_CASTING = 2988 // OSRS 665 (Trident of the swamp)
    const val TOXIC_TOTS_PROJECTILE = 2989 // OSRS 1040
    const val TOXIC_TOTS_IMPACT = 2990 // OSRS 1042
    const val SLAYER_TOTS_CHARGE = 2991 // OSRS 1250 (Trident of the seas)
    const val SLAYER_TOTS_CASTING = 2992 // OSRS 1251
    const val SLAYER_TOTS_PROJECTILE = 2993 // OSRS 1252
    const val SLAYER_TOTS_IMPACT = 2994 // OSRS 1253
    const val TOXIC_BLOWPIPE_SPECIALATTACK = 2995 // OSRS 1043
    const val ACB_SPECIALATTACK = 2996 // OSRS 301
    const val ZCB_SPECIALATTACK = 2997 // OSRS 1995
    const val ACB_CROSSBOWBOLT_TRAVEL = 2998 // OSRS 1181
    const val DRAGON_CROSSBOWBOLT_TRAVEL = 2999 // OSRS 1468
    const val BALLISTA_SPECIAL = 3000 // OSRS 344
    const val DRAGON_JAVELIN_TRAVEL = 3001 // OSRS 1301
    const val AMETHYST_JAVELIN_TRAVEL = 3002 // OSRS 1386
    const val ABYSSAL_DAGGER_SPECIAL = 3003 // OSRS 1283
    const val DRAGON_WARHAMMER_SPECIAL = 3004 // OSRS 1292
    const val VOIDWAKER_IMPACT = 3005 // OSRS 2363 FX_VOIDWAKER_IMPACT
    const val VOIDWAKER_SPECIAL = 3006 // OSRS 2834 FX_VOIDWAKER02_SPECIAL
    const val NOXIOUS_HALBERD_SPECIAL = 3007 // OSRS 2930
    const val FAERDHINEN_ARROW_TRAVEL = 3008 // OSRS 1887
    const val FAERDHINEN_ARROW_LAUNCH = 3009 // OSRS 1888
    const val AMETHYST_DART_TRAVEL = 3010 // OSRS 1936
    const val AMETHYST_DART_LAUNCH = 3011 // OSRS 1937

    // Batch "weaponfx2" (tx-20260917-183043, owner 2026-09-17c).
    const val DARK_SPEC_SPOT = 3020 // OSRS 483 (Arclight / Darklight special)
    const val NIGHTMARE_STAFF_VOLATILE_HIT = 3021 // OSRS 1759
    const val NIGHTMARE_STAFF_VOLATILE_CAST = 3022 // OSRS 1760
    const val NIGHTMARE_STAFF_ELDRITCH_HIT = 3023 // OSRS 1761
    const val NIGHTMARE_STAFF_ELDRITCH_CAST = 3024 // OSRS 1762
    const val NGS_SPECIAL = 3025 // OSRS 1996 (Ancient godsword)
    const val VENATOR_ARROW_LAUNCH = 3026 // OSRS 2289
    const val VENATOR_ARROW_TRAVEL = 3027 // OSRS 2291
    const val WEBWEAVER_LAUNCH = 3028 // OSRS 2354
    const val WEBWEAVER_IMPACT = 3029 // OSRS 2355
    const val SPECIAL_DUAL_MACUAHUITL = 3030 // OSRS 2792
    const val SPECIAL_ATLATL = 3031 // OSRS 2794
    const val ATLATL_PROJECTILE = 3032 // OSRS 2795
    const val ATLATL_IMPACT = 3033 // OSRS 2796
    const val SPECIAL_ATLATL_CAST = 3034 // OSRS 2797
    const val SPECIAL_ATLATL_IMPACT = 3035 // OSRS 2798
    const val SCORCHING_BOW_SPECIAL_ATTACK = 3036 // OSRS 2806
    const val SCORCHING_BOW_PROJECTILE = 3037 // OSRS 2807
    const val SCORCHING_BOW_SPOTANIM = 3038 // OSRS 2808
    const val SCORCHING_BOW_END_SPOTANIM = 3039 // OSRS 2809
    const val SCORCHING_BOW_IMPACT = 3040 // OSRS 2908
    const val EMBERLIGHT_SPEC = 3041 // OSRS 2810
    const val BURNING_CLAWS_SPEC = 3042 // OSRS 2814
    const val OSMUMTEN_SPECIAL = 3043 // OSRS 2833
    const val ROSEWOOD_BLOWPIPE_SPECIAL_TRAVEL = 3044 // OSRS 3486
    const val DRAGON_TKNIFE_TRAVEL = 3045 // OSRS 28
    const val DRAGON_TKNIFE_TRAVEL_P = 3046 // OSRS 697
    const val DRAGON_TKNIFE_TRAVEL_SPEC = 3047 // OSRS 699
    const val DRAGON_TKNIFE_TRAVEL_SPEC_P = 3048 // OSRS 1629
    const val DRAGON_TKNIFE_LAUNCH = 3049 // OSRS 1630

    /*
     * Batch "surge" (owner 2026-09-20: "we need exact OSRS surge animations").
     *
     * All four elements share one casting model/sequence (OSRS model 34617, seq 7857 SURGE_CASTING), one travel
     * model/sequence (34618, seq 7856 SURGE_TRAVEL) and one impact (model 3116, seq 693). What makes a surge look
     * like wind, water, earth or fire is the spotanim's own opcode-40 recolour, e.g. the casting graphic recolours
     * the same three source colours 0x1bc0 / 0x17c0 / 0x03c0 to 0x0052 / 0x007f / 0x003d for wind but
     * 0xa9de / 0x97c0 / 0xabc0 for water. OsrsFxImportTool copies opcode 40 verbatim, so the four stay distinct.
     */
    const val WIND_SURGE_CASTING = 3066 // OSRS 1455 WINDSURGE_CASTING
    const val WIND_SURGE_TRAVEL = 3067 // OSRS 1456 WINDSURGE_TRAVEL
    const val WIND_SURGE_IMPACT = 3068 // OSRS 1457 WINDSURGE_IMPACT
    const val WATER_SURGE_CASTING = 3069 // OSRS 1458 WATERSURGE_CASTING
    const val WATER_SURGE_TRAVEL = 3070 // OSRS 1459 WATERSURGE_TRAVEL
    const val WATER_SURGE_IMPACT = 3071 // OSRS 1460 WATERSURGE_IMPACT
    const val EARTH_SURGE_CASTING = 3072 // OSRS 1461 EARTHSURGE_CASTING
    const val EARTH_SURGE_TRAVEL = 3073 // OSRS 1462 EARTHSURGE_TRAVEL
    const val EARTH_SURGE_IMPACT = 3074 // OSRS 1463 EARTHSURGE_IMPACT
    const val FIRE_SURGE_CASTING = 3075 // OSRS 1464 FIRESURGE_CASTING
    const val FIRE_SURGE_TRAVEL = 3076 // OSRS 1465 FIRESURGE_TRAVEL
    const val FIRE_SURGE_IMPACT = 3077 // OSRS 1466 FIRESURGE_IMPACT

    /*
     * Batch "teleblock" (owner 2026-09-22: Tele Block "osrs animations exactly"). OSRS has no Tele Block casting
     * spotanim; the projectile is 1300 (model 5800, seq 1821 TELE_BLOCK_TRAVEL) and the impact 345 (model 5799,
     * seq 1822 TELE_BLOCK_IMPACT).
     */
    const val TELE_BLOCK_TRAVEL = 3078 // OSRS 1300 TELE_BLOCK_TRAVEL_FORFAIL
    const val TELE_BLOCK_IMPACT = 3079 // OSRS 345 TELE_BLOCK_IMPACT
}
