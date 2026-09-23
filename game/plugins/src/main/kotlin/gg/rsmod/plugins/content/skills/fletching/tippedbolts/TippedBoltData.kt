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

    /**
     * OSRS-IMPORT: OSRS Wiki "<gem> dragon bolts" item pages (raw wikitext, fetched 2026-09-16, one page per
     * tier): "Dragon bolts" (`OSRS_DRAGON_BOLTS`, distinct from the 667-native dragon-tipped runite bolts above)
     * plus 10 of the matching gem's bolt tips, all at Fletching 84. XP per bolt is independently sourced per
     * tier, not assumed from the lower runite-shaft tier above - Sapphire in particular differs (4.7 here vs
     * 2.4 for SAPPHIRE above). Dragonstone is the one gem with no dedicated "Dragonstone bolt tips" item in this
     * cache (only the finished Dragonstone dragon bolts were imported) - CONTEXT_UNAVAILABLE, not built, no tip
     * material to guess an id for.
     */
    OPAL_DRAGON(
        plainBolt = Items.OSRS_DRAGON_BOLTS,
        tip = Items.OPAL_BOLT_TIPS,
        product = Items.OPAL_DRAGON_BOLTS,
        levelRequirement = 84,
        experience = 1.6,
    ),
    JADE_DRAGON(
        plainBolt = Items.OSRS_DRAGON_BOLTS,
        tip = Items.JADE_BOLT_TIPS,
        product = Items.JADE_DRAGON_BOLTS,
        levelRequirement = 84,
        experience = 2.4,
    ),
    PEARL_DRAGON(
        plainBolt = Items.OSRS_DRAGON_BOLTS,
        tip = Items.PEARL_BOLT_TIPS,
        product = Items.PEARL_DRAGON_BOLTS,
        levelRequirement = 84,
        experience = 3.2,
    ),
    TOPAZ_DRAGON(
        plainBolt = Items.OSRS_DRAGON_BOLTS,
        tip = Items.TOPAZ_BOLT_TIPS,
        product = Items.TOPAZ_DRAGON_BOLTS,
        levelRequirement = 84,
        experience = 3.9,
    ),
    SAPPHIRE_DRAGON(
        plainBolt = Items.OSRS_DRAGON_BOLTS,
        tip = Items.SAPPHIRE_BOLT_TIPS,
        product = Items.SAPPHIRE_DRAGON_BOLTS,
        levelRequirement = 84,
        experience = 4.7,
    ),
    EMERALD_DRAGON(
        plainBolt = Items.OSRS_DRAGON_BOLTS,
        tip = Items.EMERALD_BOLT_TIPS,
        product = Items.EMERALD_DRAGON_BOLTS,
        levelRequirement = 84,
        experience = 5.5,
    ),
    RUBY_DRAGON(
        plainBolt = Items.OSRS_DRAGON_BOLTS,
        tip = Items.RUBY_BOLT_TIPS,
        product = Items.RUBY_DRAGON_BOLTS,
        levelRequirement = 84,
        experience = 6.3,
    ),
    DIAMOND_DRAGON(
        plainBolt = Items.OSRS_DRAGON_BOLTS,
        tip = Items.DIAMOND_BOLT_TIPS,
        product = Items.DIAMOND_DRAGON_BOLTS,
        levelRequirement = 84,
        experience = 7.0,
    ),
    ONYX_DRAGON(
        plainBolt = Items.OSRS_DRAGON_BOLTS,
        tip = Items.ONYX_BOLT_TIPS,
        product = Items.ONYX_DRAGON_BOLTS,
        levelRequirement = 84,
        experience = 9.4,
    ),
    ;

    companion object {
        val values = enumValues<TippedBoltData>()
        val byProduct = values().associateBy { it.product }
    }
}
