package gg.rsmod.plugins.content.skills.fletching.bolttips

import gg.rsmod.plugins.api.cfg.Items

enum class BoltTipData(
    val gem: Int,
    val tip: Int,
    val levelRequirement: Int,
    val experience: Double,
    val animation: Int,
) {
    OPAL(gem = Items.OPAL, tip = Items.OPAL_BOLT_TIPS, levelRequirement = 11, experience = 1.5, animation = 886),
    JADE(gem = Items.JADE, tip = Items.JADE_BOLT_TIPS, levelRequirement = 26, experience = 2.0, animation = 886),
    PEARL(gem = Items.OYSTER_PEARL, tip = Items.PEARL_BOLT_TIPS, levelRequirement = 41, experience = 2.0, animation = 886),
    RED_TOPAZ(gem = Items.RED_TOPAZ, tip = Items.TOPAZ_BOLT_TIPS, levelRequirement = 48, experience = 3.9, animation = 887),
    SAPPHIRE(gem = Items.SAPPHIRE, tip = Items.SAPPHIRE_BOLT_TIPS, levelRequirement = 56, experience = 4.0, animation = 888),
    EMERALD(gem = Items.EMERALD, tip = Items.EMERALD_BOLT_TIPS, levelRequirement = 58, experience = 5.5, animation = 889),
    RUBY(gem = Items.RUBY, tip = Items.RUBY_BOLT_TIPS, levelRequirement = 63, experience = 6.3, animation = 887),
    DIAMOND(gem = Items.DIAMOND, tip = Items.DIAMOND_BOLT_TIPS, levelRequirement = 65, experience = 7.0, animation = 890),
    DRAGONSTONE(gem = Items.DRAGONSTONE, tip = Items.DRAGON_BOLT_TIPS, levelRequirement = 71, experience = 8.2, animation = 885),
    ONYX(gem = Items.ONYX, tip = Items.ONYX_BOLT_TIPS, levelRequirement = 73, experience = 9.4, animation = 2717),
    ;

    companion object {
        val values = enumValues<BoltTipData>()
        val byGem = values().associateBy { it.gem }
    }
}
