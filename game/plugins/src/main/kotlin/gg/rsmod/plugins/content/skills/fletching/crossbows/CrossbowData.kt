package gg.rsmod.plugins.content.skills.fletching.crossbows

import gg.rsmod.plugins.api.cfg.Items

enum class CrossbowData(
    val stock: Int,
    val limbs: Int,
    val unstrung: Int,
    val strung: Int,
    val assembleLevelRequirement: Int,
    val assembleExperience: Double,
    val stringLevelRequirement: Int,
    val stringExperience: Double,
    val stringAnim: Int,
    val itemName: String,
) {
    BRONZE(
        stock = Items.WOODEN_STOCK,
        limbs = Items.BRONZE_LIMBS,
        unstrung = Items.BRONZE_CBOW_U,
        strung = Items.BRONZE_CROSSBOW,
        assembleLevelRequirement = 9,
        assembleExperience = 6.0,
        stringLevelRequirement = 9,
        stringExperience = 6.0,
        stringAnim = 6671,
        itemName = "Crossbow",
    ),
    BLURITE(
        stock = Items.OAK_STOCK,
        limbs = Items.BLURITE_LIMBS,
        unstrung = Items.BLURITE_CBOW_U,
        strung = Items.BLURITE_CROSSBOW,
        assembleLevelRequirement = 24,
        assembleExperience = 16.0,
        stringLevelRequirement = 24,
        stringExperience = 16.0,
        stringAnim = 6672,
        itemName = "Crossbow",
    ),
    IRON(
        stock = Items.WILLOW_STOCK,
        limbs = Items.IRON_LIMBS,
        unstrung = Items.IRON_CBOW_U,
        strung = Items.IRON_CROSSBOW,
        assembleLevelRequirement = 39,
        assembleExperience = 22.0,
        stringLevelRequirement = 39,
        stringExperience = 22.0,
        stringAnim = 6673,
        itemName = "Crossbow",
    ),
    STEEL(
        stock = Items.TEAK_STOCK,
        limbs = Items.STEEL_LIMBS,
        unstrung = Items.STEEL_CBOW_U,
        strung = Items.STEEL_CROSSBOW,
        assembleLevelRequirement = 46,
        assembleExperience = 27.0,
        stringLevelRequirement = 46,
        stringExperience = 27.0,
        stringAnim = 6674,
        itemName = "Crossbow",
    ),
    MITHRIL(
        stock = Items.MAPLE_STOCK,
        limbs = Items.MITHRIL_LIMBS,
        unstrung = Items.MITHRIL_CBOW_U,
        strung = Items.MITH_CROSSBOW,
        assembleLevelRequirement = 54,
        assembleExperience = 32.0,
        stringLevelRequirement = 52,
        stringExperience = 32.0,
        stringAnim = 6675,
        itemName = "Crossbow",
    ),
    ADAMANT(
        stock = Items.MAHOGANY_STOCK,
        limbs = Items.ADAMANTITE_LIMBS,
        unstrung = Items.ADAMANT_CBOW_U,
        strung = Items.ADAMANT_CROSSBOW,
        assembleLevelRequirement = 61,
        assembleExperience = 41.0,
        stringLevelRequirement = 61,
        stringExperience = 41.0,
        stringAnim = 6676,
        itemName = "Crossbow",
    ),
    RUNITE(
        stock = Items.YEW_STOCK,
        limbs = Items.RUNITE_LIMBS,
        unstrung = Items.RUNITE_CBOW_U,
        strung = Items.RUNE_CROSSBOW,
        assembleLevelRequirement = 69,
        assembleExperience = 50.0,
        stringLevelRequirement = 69,
        stringExperience = 50.0,
        stringAnim = 6677,
        itemName = "Crossbow",
    ),
    ;

    companion object {
        val values = enumValues<CrossbowData>()
        val byStock = values().associateBy { it.stock }
        val byUnstrung = values().associateBy { it.unstrung }
        val byStrung = values().associateBy { it.strung }
    }
}
