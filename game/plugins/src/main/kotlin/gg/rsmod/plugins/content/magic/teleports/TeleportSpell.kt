package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.game.model.Area
import gg.rsmod.plugins.content.magic.TeleportType

enum class TeleportSpell(
    val spellName: String,
    val type: TeleportType,
    val endArea: Area,
    val xp: Double,
    val spriteId: Int? = null,
) {
    /**
     * Standard.
     */
    VARROCK("Varrock Teleport", TeleportType.MODERN, Area(3210, 3423, 3216, 3425), 35.0),
    MOBILISING_ARMIES("Mobilising Armies Teleport", TeleportType.MODERN, Area(2410, 2847, 2416, 2851), 19.0),
    LUMBRIDGE("Lumbridge Teleport", TeleportType.MODERN, Area(3221, 3218, 3222, 3219), 41.0),
    FALADOR("Falador Teleport", TeleportType.MODERN, Area(2961, 3376, 2969, 3385), 47.0),
    CAMELOT("Camelot Teleport", TeleportType.MODERN, Area(2756, 3476, 2758, 3480), 55.5),
    ARDOUGNE("Ardougne Teleport", TeleportType.MODERN, Area(2659, 3300, 2665, 3310), 61.0),
    WATCHTOWER("Watchtower Teleport", TeleportType.MODERN, Area(2551, 3113, 2553, 3116), 68.0),
    TROLLHEIM("Trollheim Teleport", TeleportType.MODERN, Area(2888, 3675, 2890, 3678), 68.0),
    APE_ATOLL("Teleport to Ape Atoll", TeleportType.MODERN, Area(2760, 2781, 2763, 2784), 74.0),

    /**
     * Ancient Magicks (interface 193). Destinations from the 2009scape AncientTeleportPlugin, cross-checked
     * with Matrix 718 Magic.processAncientSpell; experience from the 2011 RuneScape Wiki.
     */
    PADDEWWA("Paddewwa Teleport", TeleportType.ANCIENT, Area(3097, 9881, 3099, 9883), 64.0),
    SENNTISTEN("Senntisten Teleport", TeleportType.ANCIENT, Area(3319, 3336, 3322, 3339), 70.0),
    KHARYRLL("Kharyrll Teleport", TeleportType.ANCIENT, Area(3491, 3470, 3494, 3473), 76.0),
    LASSAR("Lassar Teleport", TeleportType.ANCIENT, Area(3003, 3469, 3006, 3472), 82.0),
    DAREEYAK("Dareeyak Teleport", TeleportType.ANCIENT, Area(2966, 3695, 2969, 3697), 88.0),
    CARRALLANGAR("Carrallangar Teleport", TeleportType.ANCIENT, Area(3216, 3675, 3218, 3677), 94.0),
    ANNAKARL("Annakarl Teleport", TeleportType.ANCIENT, Area(3286, 3883, 3289, 3886), 100.0),
    GHORROCK("Ghorrock Teleport", TeleportType.ANCIENT, Area(2972, 3872, 2975, 3874), 106.0),

    /**
     * Lunar spellbook (interface 430). Destinations from the 2009scape LunarListeners; experience from the
     * 2011 RuneScape Wiki (South Falador/North Ardougne/Trollheim values are the wiki's 2011 figures).
     */
    MOONCLAN("Moonclan Teleport", TeleportType.LUNAR, Area(2110, 3915, 2112, 3917), 66.0),
    OURANIA("Ourania Teleport", TeleportType.LUNAR, Area(2468, 3246, 2470, 3248), 69.0),
    WATERBIRTH("Waterbirth Teleport", TeleportType.LUNAR, Area(2526, 3738, 2528, 3740), 71.0),
    BARBARIAN("Barbarian Teleport", TeleportType.LUNAR, Area(2543, 3571, 2545, 3573), 76.0),
    KHAZARD("Khazard Teleport", TeleportType.LUNAR, Area(2655, 3156, 2657, 3158), 80.0),
    FISHING_GUILD("Fishing Guild Teleport", TeleportType.LUNAR, Area(2610, 3392, 2612, 3394), 89.0),
    CATHERBY("Catherby Teleport", TeleportType.LUNAR, Area(2803, 3432, 2805, 3434), 92.0),
    ICE_PLATEAU("Ice Plateau Teleport", TeleportType.LUNAR, Area(2971, 3872, 2973, 3874), 96.0),
    SOUTH_FALADOR("South Falador Teleport", TeleportType.LUNAR, Area(3010, 3329, 3012, 3331), 70.0),
    NORTH_ARDOUGNE("North Ardougne Teleport", TeleportType.LUNAR, Area(2650, 3355, 2652, 3357), 76.0),
    LUNAR_TROLLHEIM("Trollheim Teleport", TeleportType.LUNAR, Area(2830, 3676, 2832, 3678), 92.0, spriteId = 7685),

    ;

    companion object {
        val values = enumValues<TeleportSpell>()
    }
}
