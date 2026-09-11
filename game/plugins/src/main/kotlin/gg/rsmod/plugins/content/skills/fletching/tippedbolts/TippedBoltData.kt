package gg.rsmod.plugins.content.skills.fletching.tippedbolts

import gg.rsmod.plugins.api.cfg.Items

enum class TippedBoltData(
    val plainBolt: Int,
    val tip: Int,
    val product: Int,
    val levelRequirement: Int,
    val experience: Double,
) {
    OPAL(
        plainBolt = Items.BRONZE_BOLTS,
        tip = Items.OPAL_BOLT_TIPS,
        product = Items.OPAL_BOLTS,
        levelRequirement = 11,
        experience = 1.6,
    ),
    BARBED(
        plainBolt = Items.BRONZE_BOLTS,
        tip = Items.BARB_BOLTTIPS,
        product = Items.BARBED_BOLTS,
        levelRequirement = 51,
        experience = 9.5,
    ),
    JADE(
        plainBolt = Items.BLURITE_BOLTS,
        tip = Items.JADE_BOLT_TIPS,
        product = Items.JADE_BOLTS,
        levelRequirement = 26,
        experience = 2.4,
    ),
    PEARL(
        plainBolt = Items.IRON_BOLTS,
        tip = Items.PEARL_BOLT_TIPS,
        product = Items.PEARL_BOLTS,
        levelRequirement = 41,
        experience = 3.2,
    ),
    RED_TOPAZ(
        plainBolt = Items.STEEL_BOLTS,
        tip = Items.TOPAZ_BOLT_TIPS,
        product = Items.TOPAZ_BOLTS,
        levelRequirement = 48,
        experience = 3.9,
    ),
    SAPPHIRE(
        plainBolt = Items.MITHRIL_BOLTS,
        tip = Items.SAPPHIRE_BOLT_TIPS,
        product = Items.SAPPHIRE_BOLTS,
        levelRequirement = 56,
        experience = 2.4,
    ),
    RUBY(
        plainBolt = Items.ADAMANT_BOLTS,
        tip = Items.RUBY_BOLT_TIPS,
        product = Items.RUBY_BOLTS,
        levelRequirement = 63,
        experience = 6.3,
    ),
    DIAMOND(
        plainBolt = Items.ADAMANT_BOLTS,
        tip = Items.DIAMOND_BOLT_TIPS,
        product = Items.DIAMOND_BOLTS,
        levelRequirement = 65,
        experience = 7.0,
    ),
    DRAGON(
        plainBolt = Items.RUNITE_BOLTS,
        tip = Items.DRAGON_BOLT_TIPS,
        product = Items.DRAGON_BOLTS,
        levelRequirement = 71,
        experience = 8.2,
    ),
    ONYX(
        plainBolt = Items.RUNITE_BOLTS,
        tip = Items.ONYX_BOLT_TIPS,
        product = Items.ONYX_BOLTS,
        levelRequirement = 73,
        experience = 9.4,
    ),
    ;

    companion object {
        val values = enumValues<TippedBoltData>()
        val byProduct = values().associateBy { it.product }
    }
}
