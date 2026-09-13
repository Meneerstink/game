package gg.rsmod.plugins.content.combat.strategy.ranged.ammo

import gg.rsmod.plugins.api.cfg.Items

/**
 * @author Tom <rspsmods@gmail.com>
 */
object Bolts {
    val BRONZE_BOLTS =
        arrayOf(Items.BRONZE_BOLTS, Items.BRONZE_BOLTS_P, Items.BRONZE_BOLTS_P_6061, Items.BRONZE_BOLTS_P_6062)
    val IRON_BOLTS = arrayOf(Items.IRON_BOLTS, Items.IRON_BOLTS_P, Items.IRON_BOLTS_P_9294, Items.IRON_BOLTS_P_9301)
    val STEEL_BOLTS =
        arrayOf(Items.STEEL_BOLTS, Items.STEEL_BOLTS_P, Items.STEEL_BOLTS_P_9295, Items.STEEL_BOLTS_P_9302)
    val MITHRIL_BOLTS =
        arrayOf(Items.MITHRIL_BOLTS, Items.MITHRIL_BOLTS_P, Items.MITHRIL_BOLTS_P_9296, Items.MITHRIL_BOLTS_P_9303)
    val ADAMANT_BOLTS =
        arrayOf(Items.ADAMANT_BOLTS, Items.ADAMANT_BOLTS_P, Items.ADAMANT_BOLTS_P_9297, Items.ADAMANT_BOLTS_P_9304)
    val BROAD_BOLTS = arrayOf(Items.BROADTIPPED_BOLTS)
    val RUNITE_BOLTS =
        arrayOf(Items.RUNITE_BOLTS, Items.RUNITE_BOLTS_P, Items.RUNITE_BOLTS_P_9298, Items.RUNITE_BOLTS_P_9305)
    val DRAGON_BOLTS = arrayOf(Items.DRAGON_BOLTS)

    /**
     * OSRS-IMPORT: OSRS dragon-metal bolts with their gem-tipped and enchanted variants (OSRS Wiki "Dragon bolts": fired by
     * crossbows that take dragon bolts, not by the Rune crossbow). Separate from the 667 [DRAGON_BOLTS] (dragonstone bolts).
     */
    val OSRS_DRAGON_BOLT_FAMILY =
        arrayOf(
            Items.OSRS_DRAGON_BOLTS,
            Items.OPAL_DRAGON_BOLTS, Items.JADE_DRAGON_BOLTS, Items.PEARL_DRAGON_BOLTS, Items.TOPAZ_DRAGON_BOLTS,
            Items.SAPPHIRE_DRAGON_BOLTS, Items.EMERALD_DRAGON_BOLTS, Items.RUBY_DRAGON_BOLTS, Items.DIAMOND_DRAGON_BOLTS,
            Items.DRAGONSTONE_DRAGON_BOLTS, Items.ONYX_DRAGON_BOLTS,
            Items.OPAL_DRAGON_BOLTS_E, Items.JADE_DRAGON_BOLTS_E, Items.PEARL_DRAGON_BOLTS_E, Items.TOPAZ_DRAGON_BOLTS_E,
            Items.SAPPHIRE_DRAGON_BOLTS_E, Items.EMERALD_DRAGON_BOLTS_E, Items.RUBY_DRAGON_BOLTS_E, Items.DIAMOND_DRAGON_BOLTS_E,
            Items.DRAGONSTONE_DRAGON_BOLTS_E, Items.ONYX_DRAGON_BOLTS_E,
        )

    val BLURITE_BOLTS =
        arrayOf(Items.BLURITE_BOLTS, Items.BLURITE_BOLTS_P, Items.BLURITE_BOLTS_P_9293, Items.BLURITE_BOLTS_P_9300)
    val BONE_BOLTS = arrayOf(Items.BONE_BOLTS)
    val KEBBIT_BOLTS = arrayOf(Items.KEBBIT_BOLTS, Items.LONG_KEBBIT_BOLTS)
    val BOLT_RACKS = arrayOf(Items.BOLT_RACK)
}
