package gg.rsmod.plugins.content.skills.fletching.javelins

import gg.rsmod.plugins.api.cfg.Items

/**
 * OSRS-IMPORT javelins (OSRS Wiki "<metal> javelin" item pages, fetched 2026-09-16, one page per tier):
 * "attaching <metal> javelin tips to javelin shafts", one tip + one shaft per javelin, XP quoted per batch of 15
 * (divided here to XP per javelin, matching how `TippedBoltData`/`CrossbowData` already store per-unit values).
 */
enum class JavelinData(
    val shaft: Int,
    val tip: Int,
    val product: Int,
    val levelRequirement: Int,
    val experience: Double,
) {
    BRONZE(Items.JAVELIN_SHAFT, Items.BRONZE_JAVELIN_TIPS, Items.OSRS_BRONZE_JAVELIN, 3, 1.0),
    IRON(Items.JAVELIN_SHAFT, Items.IRON_JAVELIN_TIPS, Items.OSRS_IRON_JAVELIN, 17, 2.0),
    STEEL(Items.JAVELIN_SHAFT, Items.STEEL_JAVELIN_TIPS, Items.OSRS_STEEL_JAVELIN, 32, 5.0),
    MITHRIL(Items.JAVELIN_SHAFT, Items.MITHRIL_JAVELIN_TIPS, Items.OSRS_MITHRIL_JAVELIN, 47, 8.0),
    ADAMANT(Items.JAVELIN_SHAFT, Items.ADAMANT_JAVELIN_TIPS, Items.OSRS_ADAMANT_JAVELIN, 62, 10.0),
    RUNE(Items.JAVELIN_SHAFT, Items.RUNE_JAVELIN_TIPS, Items.OSRS_RUNE_JAVELIN, 77, 12.5),
    AMETHYST(Items.JAVELIN_SHAFT, Items.AMETHYST_JAVELIN_TIPS, Items.OSRS_AMETHYST_JAVELIN, 84, 13.5),
    DRAGON(Items.JAVELIN_SHAFT, Items.DRAGON_JAVELIN_TIPS, Items.OSRS_DRAGON_JAVELIN, 92, 15.0),
    ;

    companion object {
        val values = enumValues<JavelinData>()
        val byProduct = values().associateBy { it.product }
    }
}
