package gg.rsmod.plugins.content.items.potion

import gg.rsmod.plugins.api.cfg.Items

enum class Potion(
    val item: Int,
    val replacement: Int = -1,
    val potionType: PotionType,
) {
    STRENGTH_POTION4(
        item = Items.STRENGTH_POTION_4,
        replacement = Items.STRENGTH_POTION_3,
        potionType = PotionType.STRENGTH,
    ),
    STRENGTH_POTION3(
        item = Items.STRENGTH_POTION_3,
        replacement = Items.STRENGTH_POTION_2,
        potionType = PotionType.STRENGTH,
    ),
    STRENGTH_POTION2(
        item = Items.STRENGTH_POTION_2,
        replacement = Items.STRENGTH_POTION_1,
        potionType = PotionType.STRENGTH,
    ),
    STRENGTH_POTION1(
        item = Items.STRENGTH_POTION_1,
        replacement = Items.VIAL,
        potionType = PotionType.STRENGTH,
    ),

    SUPER_STRENGTH4(
        item = Items.SUPER_STRENGTH_4,
        replacement = Items.SUPER_STRENGTH_3,
        potionType = PotionType.SUPER_STRENGTH,
    ),
    SUPER_STRENGTH3(
        item = Items.SUPER_STRENGTH_3,
        replacement = Items.SUPER_STRENGTH_2,
        potionType = PotionType.SUPER_STRENGTH,
    ),
    SUPER_STRENGTH2(
        item = Items.SUPER_STRENGTH_2,
        replacement = Items.SUPER_STRENGTH_1,
        potionType = PotionType.SUPER_STRENGTH,
    ),
    SUPER_STRENGTH1(
        item = Items.SUPER_STRENGTH_1,
        replacement = Items.VIAL,
        potionType = PotionType.SUPER_STRENGTH,
    ),

    ATTACK4(item = Items.ATTACK_POTION_4, replacement = Items.ATTACK_POTION_3, potionType = PotionType.ATTACK),
    ATTACK3(item = Items.ATTACK_POTION_3, replacement = Items.ATTACK_POTION_2, potionType = PotionType.ATTACK),
    ATTACK2(item = Items.ATTACK_POTION_2, replacement = Items.ATTACK_POTION_1, potionType = PotionType.ATTACK),
    ATTACK1(item = Items.ATTACK_POTION_1, replacement = Items.VIAL, potionType = PotionType.ATTACK),

    SUPER_ATTACK4(
        item = Items.SUPER_ATTACK_4,
        replacement = Items.SUPER_ATTACK_3,
        potionType = PotionType.SUPER_ATTACK,
    ),
    SUPER_ATTACK3(
        item = Items.SUPER_ATTACK_3,
        replacement = Items.SUPER_ATTACK_2,
        potionType = PotionType.SUPER_ATTACK,
    ),
    SUPER_ATTACK2(
        item = Items.SUPER_ATTACK_2,
        replacement = Items.SUPER_ATTACK_1,
        potionType = PotionType.SUPER_ATTACK,
    ),
    SUPER_ATTACK1(
        item = Items.SUPER_ATTACK_1,
        replacement = Items.VIAL,
        potionType = PotionType.SUPER_ATTACK,
    ),

    DEFENCE_POTION4(
        item = Items.DEFENCE_POTION_4,
        replacement = Items.DEFENCE_POTION_3,
        potionType = PotionType.DEFENCE,
    ),
    DEFENCE_POTION3(
        item = Items.DEFENCE_POTION_3,
        replacement = Items.DEFENCE_POTION_2,
        potionType = PotionType.DEFENCE,
    ),
    DEFENCE_POTION2(
        item = Items.DEFENCE_POTION_2,
        replacement = Items.DEFENCE_POTION_1,
        potionType = PotionType.DEFENCE,
    ),
    DEFENCE_POTION1(
        item = Items.DEFENCE_POTION_1,
        replacement = Items.VIAL,
        potionType = PotionType.DEFENCE,
    ),

    SUPER_DEFENCE4(
        item = Items.SUPER_DEFENCE_4,
        replacement = Items.SUPER_DEFENCE_3,
        potionType = PotionType.SUPER_DEFENCE,
    ),
    SUPER_DEFENCE3(
        item = Items.SUPER_DEFENCE_3,
        replacement = Items.SUPER_DEFENCE_2,
        potionType = PotionType.SUPER_DEFENCE,
    ),
    SUPER_DEFENCE2(
        item = Items.SUPER_DEFENCE_2,
        replacement = Items.SUPER_DEFENCE_1,
        potionType = PotionType.SUPER_DEFENCE,
    ),
    SUPER_DEFENCE1(
        item = Items.SUPER_DEFENCE_1,
        replacement = Items.VIAL,
        potionType = PotionType.SUPER_DEFENCE,
    ),

    RANGING_POTION4(
        item = Items.RANGING_POTION_4,
        replacement = Items.RANGING_POTION_3,
        potionType = PotionType.RANGING,
    ),
    RANGING_POTION3(
        item = Items.RANGING_POTION_3,
        replacement = Items.RANGING_POTION_2,
        potionType = PotionType.RANGING,
    ),
    RANGING_POTION2(
        item = Items.RANGING_POTION_2,
        replacement = Items.RANGING_POTION_1,
        potionType = PotionType.RANGING,
    ),
    RANGING_POTION1(
        item = Items.RANGING_POTION_1,
        replacement = Items.VIAL,
        potionType = PotionType.RANGING,
    ),

    MAGIC_POTION4(item = Items.MAGIC_POTION_4, replacement = Items.MAGIC_POTION_3, potionType = PotionType.MAGIC),
    MAGIC_POTION3(item = Items.MAGIC_POTION_3, replacement = Items.MAGIC_POTION_2, potionType = PotionType.MAGIC),
    MAGIC_POTION2(item = Items.MAGIC_POTION_2, replacement = Items.MAGIC_POTION_1, potionType = PotionType.MAGIC),
    MAGIC_POTION1(item = Items.MAGIC_POTION_1, replacement = Items.VIAL, potionType = PotionType.MAGIC),

    FISHING4(item = Items.FISHING_POTION_4, replacement = Items.FISHING_POTION_3, potionType = PotionType.FISHING),
    FISHING3(item = Items.FISHING_POTION_3, replacement = Items.FISHING_POTION_2, potionType = PotionType.FISHING),
    FISHING2(item = Items.FISHING_POTION_2, replacement = Items.FISHING_POTION_1, potionType = PotionType.FISHING),
    FISHING1(item = Items.FISHING_POTION_1, replacement = Items.VIAL, potionType = PotionType.FISHING),
    AGILITY4(item = Items.AGILITY_POTION_4, replacement = Items.AGILITY_POTION_3, potionType = PotionType.AGILITY),
    AGILITY3(item = Items.AGILITY_POTION_3, replacement = Items.AGILITY_POTION_2, potionType = PotionType.AGILITY),
    AGILITY2(item = Items.AGILITY_POTION_2, replacement = Items.AGILITY_POTION_1, potionType = PotionType.AGILITY),
    AGILITY1(item = Items.AGILITY_POTION_1, replacement = Items.VIAL, potionType = PotionType.AGILITY),
    HUNTER4(item = Items.HUNTER_POTION_4, replacement = Items.HUNTER_POTION_3, potionType = PotionType.HUNTER),
    HUNTER3(item = Items.HUNTER_POTION_3, replacement = Items.HUNTER_POTION_2, potionType = PotionType.HUNTER),
    HUNTER2(item = Items.HUNTER_POTION_2, replacement = Items.HUNTER_POTION_1, potionType = PotionType.HUNTER),
    HUNTER1(item = Items.HUNTER_POTION_1, replacement = Items.VIAL, potionType = PotionType.HUNTER),
    CRAFTING4(item = Items.CRAFTING_POTION_4, replacement = Items.CRAFTING_POTION_3, potionType = PotionType.CRAFTING),
    CRAFTING3(item = Items.CRAFTING_POTION_3, replacement = Items.CRAFTING_POTION_2, potionType = PotionType.CRAFTING),
    CRAFTING2(item = Items.CRAFTING_POTION_2, replacement = Items.CRAFTING_POTION_1, potionType = PotionType.CRAFTING),
    CRAFTING1(item = Items.CRAFTING_POTION_1, replacement = Items.VIAL, potionType = PotionType.CRAFTING),

    FLETCHING_POTION4(
        item = Items.FLETCHING_POTION_4,
        replacement = Items.FLETCHING_POTION_3,
        potionType = PotionType.FLETCHING,
    ),
    FLETCHING_POTION3(
        item = Items.FLETCHING_POTION_3,
        replacement = Items.FLETCHING_POTION_2,
        potionType = PotionType.FLETCHING,
    ),
    FLETCHING_POTION2(
        item = Items.FLETCHING_POTION_2,
        replacement = Items.FLETCHING_POTION_1,
        potionType = PotionType.FLETCHING,
    ),
    // RCV-010 A3: every other one-dose potion leaves a vial; this one had no replacement and vanished.
    FLETCHING_POTION1(item = Items.FLETCHING_POTION_1, replacement = Items.VIAL, potionType = PotionType.FLETCHING),

    COMBAT4(item = Items.COMBAT_POTION_4, replacement = Items.COMBAT_POTION_3, potionType = PotionType.COMBAT),
    COMBAT3(item = Items.COMBAT_POTION_3, replacement = Items.COMBAT_POTION_2, potionType = PotionType.COMBAT),
    COMBAT2(item = Items.COMBAT_POTION_2, replacement = Items.COMBAT_POTION_1, potionType = PotionType.COMBAT),
    COMBAT1(item = Items.COMBAT_POTION_1, replacement = Items.VIAL, potionType = PotionType.COMBAT),

    RESTORE4(item = Items.RESTORE_POTION_4, replacement = Items.RESTORE_POTION_3, potionType = PotionType.RESTORE),
    RESTORE3(item = Items.RESTORE_POTION_3, replacement = Items.RESTORE_POTION_2, potionType = PotionType.RESTORE),
    RESTORE2(item = Items.RESTORE_POTION_2, replacement = Items.RESTORE_POTION_1, potionType = PotionType.RESTORE),
    RESTORE1(item = Items.RESTORE_POTION_1, replacement = Items.VIAL, potionType = PotionType.RESTORE),

    SUPER_RESTORE4(
        item = Items.SUPER_RESTORE_4,
        replacement = Items.SUPER_RESTORE_3,
        potionType = PotionType.SUPER_RESTORE,
    ),
    SUPER_RESTORE3(
        item = Items.SUPER_RESTORE_3,
        replacement = Items.SUPER_RESTORE_2,
        potionType = PotionType.SUPER_RESTORE,
    ),
    SUPER_RESTORE2(
        item = Items.SUPER_RESTORE_2,
        replacement = Items.SUPER_RESTORE_1,
        potionType = PotionType.SUPER_RESTORE,
    ),
    SUPER_RESTORE1(
        item = Items.SUPER_RESTORE_1,
        replacement = Items.VIAL,
        potionType = PotionType.SUPER_RESTORE,
    ),

    SUMMONING_POTION5(
        item = Items.SUMMONING_POTION_5,
        replacement = Items.SUMMONING_POTION_4_14279,
        potionType = PotionType.SUMMONING,
    ),
    SUMMONING_POTION4(
        item = Items.SUMMONING_POTION_4,
        replacement = Items.SUMMONING_POTION_3,
        potionType = PotionType.SUMMONING,
    ),
    SUMMONING_POTION4_ALT(
        item = Items.SUMMONING_POTION_4_14279,
        replacement = Items.SUMMONING_POTION_3_14281,
        potionType = PotionType.SUMMONING,
    ),
    SUMMONING_POTION3(
        item = Items.SUMMONING_POTION_3,
        replacement = Items.SUMMONING_POTION_2,
        potionType = PotionType.SUMMONING,
    ),
    SUMMONING_POTION3_ALT(
        item = Items.SUMMONING_POTION_3_14281,
        replacement = Items.SUMMONING_POTION_2_14283,
        potionType = PotionType.SUMMONING,
    ),
    SUMMONING_POTION2(
        item = Items.SUMMONING_POTION_2,
        replacement = Items.SUMMONING_POTION_1,
        potionType = PotionType.SUMMONING,
    ),
    SUMMONING_POTION2_ALT(
        item = Items.SUMMONING_POTION_2_14283,
        replacement = Items.SUMMONING_POTION_1_14285,
        potionType = PotionType.SUMMONING,
    ),
    SUMMONING_POTION1(
        item = Items.SUMMONING_POTION_1,
        replacement = Items.VIAL,
        potionType = PotionType.SUMMONING,
    ),
    SUMMONING_POTION1_ALT(
        item = Items.SUMMONING_POTION_1_14285,
        replacement = Items.VIAL,
        potionType = PotionType.SUMMONING,
    ),

    PRAYER4(
        item = Items.PRAYER_POTION_4,
        replacement =
            Items.PRAYER_POTION_3,
        potionType = PotionType.PRAYER,
    ),
    PRAYER3(
        item = Items.PRAYER_POTION_3,
        replacement = Items.PRAYER_POTION_2,
        potionType = PotionType.PRAYER,
    ),
    PRAYER2(
        item = Items.PRAYER_POTION_2,
        replacement = Items.PRAYER_POTION_1,
        potionType = PotionType.PRAYER,
    ),
    PRAYER1(
        item = Items.PRAYER_POTION_1,
        replacement = Items.VIAL,
        potionType = PotionType.PRAYER,
    ),

    SUPER_PRAYER4(
        item = Items.SUPER_PRAYER_4,
        replacement = Items.SUPER_PRAYER_3,
        potionType = PotionType.SUPER_PRAYER,
    ),
    SUPER_PRAYER3(
        item = Items.SUPER_PRAYER_3,
        replacement = Items.SUPER_PRAYER_2,
        potionType = PotionType.SUPER_PRAYER,
    ),
    SUPER_PRAYER2(
        item = Items.SUPER_PRAYER_2,
        replacement = Items.SUPER_PRAYER_1,
        potionType = PotionType.SUPER_PRAYER,
    ),
    SUPER_PRAYER1(
        item = Items.SUPER_PRAYER_1,
        replacement = Items.VIAL,
        potionType = PotionType.SUPER_PRAYER,
    ),

    SARADOMIN_BREW4(
        item = Items.SARADOMIN_BREW_4,
        replacement = Items.SARADOMIN_BREW_3,
        potionType = PotionType.SARADOMIN_BREW,
    ),
    SARADOMIN_BREW3(
        item = Items.SARADOMIN_BREW_3,
        replacement = Items.SARADOMIN_BREW_2,
        potionType = PotionType.SARADOMIN_BREW,
    ),
    SARADOMIN_BREW2(
        item = Items.SARADOMIN_BREW_2,
        replacement = Items.SARADOMIN_BREW_1,
        potionType = PotionType.SARADOMIN_BREW,
    ),
    SARADOMIN_BREW1(
        item = Items.SARADOMIN_BREW_1,
        replacement = Items.VIAL,
        potionType = PotionType.SARADOMIN_BREW,
    ),

    // TODO: Formulas for alcoholic beverages
    BEER(
        item = Items.BEER,
        replacement = Items.BEER_GLASS,
        potionType = PotionType.BEER,
    ),
    JUG_OF_WINE(
        item = Items.JUG_OF_WINE,
        replacement = Items.HALF_FULL_WINE_JUG,
        potionType = PotionType.JUG_OF_WINE,
    ),
    HALF_FULL_WINE_JUG(
        item = Items.HALF_FULL_WINE_JUG,
        replacement = Items.JUG,
        potionType = PotionType.HALF_FULL_WINE_JUG,
    ),
    WIZARDS_MIND_BOMB(
        item = Items.WIZARDS_MIND_BOMB,
        replacement = Items.BEER_GLASS,
        potionType = PotionType.WIZARDS_MIND_BOMB,
    ),
    DWARVEN_STOUT(
        item = Items.DWARVEN_STOUT,
        replacement = Items.BEER_GLASS,
        potionType = PotionType.DWARVEN_STOUT,
    ),
    ASGARNIAN_ALE(
        item = Items.ASGARNIAN_ALE,
        replacement = Items.BEER_GLASS,
        potionType = PotionType.ASGARNIAN_ALE,
    ),

    ANTIPOISON4(
        item = Items.ANTIPOISON_4,
        replacement = Items.ANTIPOISON_3,
        potionType = PotionType.ANTIPOISON,
    ),
    ANTIPOISON3(
        item = Items.ANTIPOISON_3,
        replacement = Items.ANTIPOISON_2,
        potionType = PotionType.ANTIPOISON,
    ),
    ANTIPOISON2(
        item = Items.ANTIPOISON_2,
        replacement = Items.ANTIPOISON_1,
        potionType = PotionType.ANTIPOISON,
    ),
    ANTIPOISON1(
        item = Items.ANTIPOISON_1,
        replacement = Items.VIAL,
        potionType = PotionType.ANTIPOISON,
    ),

    SUPER_ANTIPOISON4(
        item = Items.SUPER_ANTIPOISON_4,
        replacement = Items.SUPER_ANTIPOISON_3,
        potionType = PotionType.SUPER_ANTIPOISON,
    ),
    SUPER_ANTIPOISON3(
        item = Items.SUPER_ANTIPOISON_3,
        replacement = Items.SUPER_ANTIPOISON_2,
        potionType = PotionType.SUPER_ANTIPOISON,
    ),
    SUPER_ANTIPOISON2(
        item = Items.SUPER_ANTIPOISON_2,
        replacement = Items.SUPER_ANTIPOISON_1,
        potionType = PotionType.SUPER_ANTIPOISON,
    ),
    SUPER_ANTIPOISON1(
        item = Items.SUPER_ANTIPOISON_1,
        replacement = Items.VIAL,
        potionType = PotionType.SUPER_ANTIPOISON,
    ),
    ANTIFIRE4(
        item = Items.ANTIFIRE_4,
        replacement = Items.ANTIFIRE_3,
        potionType = PotionType.ANTIFIRE,
    ),
    ANTIFIRE3(
        item = Items.ANTIFIRE_3,
        replacement = Items.ANTIFIRE_2,
        potionType = PotionType.ANTIFIRE,
    ),
    ANTIFIRE2(
        item = Items.ANTIFIRE_2,
        replacement = Items.ANTIFIRE_1,
        potionType = PotionType.ANTIFIRE,
    ),
    ANTIFIRE1(
        item = Items.ANTIFIRE_1,
        replacement = Items.VIAL,
        potionType = PotionType.ANTIFIRE,
    ),

    SUPER_ANTIFIRE4(
        item = Items.SUPER_ANTIFIRE_4,
        replacement = Items.SUPER_ANTIFIRE_3,
        potionType = PotionType.SUPER_ANTIFIRE,
    ),
    SUPER_ANTIFIRE3(
        item = Items.SUPER_ANTIFIRE_3,
        replacement = Items.SUPER_ANTIFIRE_2,
        potionType = PotionType.SUPER_ANTIFIRE,
    ),
    SUPER_ANTIFIRE2(
        item = Items.SUPER_ANTIFIRE_2,
        replacement = Items.SUPER_ANTIFIRE_1,
        potionType = PotionType.SUPER_ANTIFIRE,
    ),
    SUPER_ANTIFIRE1(
        item = Items.SUPER_ANTIFIRE_1,
        replacement = Items.VIAL,
        potionType = PotionType.SUPER_ANTIFIRE,
    ),

    ENERGY4(
        item = Items.ENERGY_POTION_4,
        replacement = Items.ENERGY_POTION_3,
        potionType = PotionType.ENERGY,
    ),
    ENERGY3(
        item = Items.ENERGY_POTION_3,
        replacement = Items.ENERGY_POTION_2,
        potionType = PotionType.ENERGY,
    ),
    ENERGY2(
        item = Items.ENERGY_POTION_2,
        replacement = Items.ENERGY_POTION_1,
        potionType = PotionType.ENERGY,
    ),
    ENERGY1(
        item = Items.ENERGY_POTION_1,
        replacement = Items.VIAL,
        potionType = PotionType.ENERGY,
    ),

    // RCV-010 A3: 667 dose potions (Novite `Pots.Pot` ids, Items.kt constants) that had no drink handler.
    SUPER_ENERGY4(item = Items.SUPER_ENERGY_4, replacement = Items.SUPER_ENERGY_3, potionType = PotionType.SUPER_ENERGY),
    SUPER_ENERGY3(item = Items.SUPER_ENERGY_3, replacement = Items.SUPER_ENERGY_2, potionType = PotionType.SUPER_ENERGY),
    SUPER_ENERGY2(item = Items.SUPER_ENERGY_2, replacement = Items.SUPER_ENERGY_1, potionType = PotionType.SUPER_ENERGY),
    SUPER_ENERGY1(item = Items.SUPER_ENERGY_1, replacement = Items.VIAL, potionType = PotionType.SUPER_ENERGY),
    ZAMORAK_BREW4(item = Items.ZAMORAK_BREW_4, replacement = Items.ZAMORAK_BREW_3, potionType = PotionType.ZAMORAK_BREW),
    ZAMORAK_BREW3(item = Items.ZAMORAK_BREW_3, replacement = Items.ZAMORAK_BREW_2, potionType = PotionType.ZAMORAK_BREW),
    ZAMORAK_BREW2(item = Items.ZAMORAK_BREW_2, replacement = Items.ZAMORAK_BREW_1, potionType = PotionType.ZAMORAK_BREW),
    ZAMORAK_BREW1(item = Items.ZAMORAK_BREW_1, replacement = Items.VIAL, potionType = PotionType.ZAMORAK_BREW),
    EXTREME_ATTACK4(item = Items.EXTREME_ATTACK_4, replacement = Items.EXTREME_ATTACK_3, potionType = PotionType.EXTREME_ATTACK),
    EXTREME_ATTACK3(item = Items.EXTREME_ATTACK_3, replacement = Items.EXTREME_ATTACK_2, potionType = PotionType.EXTREME_ATTACK),
    EXTREME_ATTACK2(item = Items.EXTREME_ATTACK_2, replacement = Items.EXTREME_ATTACK_1, potionType = PotionType.EXTREME_ATTACK),
    EXTREME_ATTACK1(item = Items.EXTREME_ATTACK_1, replacement = Items.VIAL, potionType = PotionType.EXTREME_ATTACK),
    EXTREME_STRENGTH4(item = Items.EXTREME_STRENGTH_4, replacement = Items.EXTREME_STRENGTH_3, potionType = PotionType.EXTREME_STRENGTH),
    EXTREME_STRENGTH3(item = Items.EXTREME_STRENGTH_3, replacement = Items.EXTREME_STRENGTH_2, potionType = PotionType.EXTREME_STRENGTH),
    EXTREME_STRENGTH2(item = Items.EXTREME_STRENGTH_2, replacement = Items.EXTREME_STRENGTH_1, potionType = PotionType.EXTREME_STRENGTH),
    EXTREME_STRENGTH1(item = Items.EXTREME_STRENGTH_1, replacement = Items.VIAL, potionType = PotionType.EXTREME_STRENGTH),
    EXTREME_DEFENCE4(item = Items.EXTREME_DEFENCE_4, replacement = Items.EXTREME_DEFENCE_3, potionType = PotionType.EXTREME_DEFENCE),
    EXTREME_DEFENCE3(item = Items.EXTREME_DEFENCE_3, replacement = Items.EXTREME_DEFENCE_2, potionType = PotionType.EXTREME_DEFENCE),
    EXTREME_DEFENCE2(item = Items.EXTREME_DEFENCE_2, replacement = Items.EXTREME_DEFENCE_1, potionType = PotionType.EXTREME_DEFENCE),
    EXTREME_DEFENCE1(item = Items.EXTREME_DEFENCE_1, replacement = Items.VIAL, potionType = PotionType.EXTREME_DEFENCE),
    EXTREME_MAGIC4(item = Items.EXTREME_MAGIC_4, replacement = Items.EXTREME_MAGIC_3, potionType = PotionType.EXTREME_MAGIC),
    EXTREME_MAGIC3(item = Items.EXTREME_MAGIC_3, replacement = Items.EXTREME_MAGIC_2, potionType = PotionType.EXTREME_MAGIC),
    EXTREME_MAGIC2(item = Items.EXTREME_MAGIC_2, replacement = Items.EXTREME_MAGIC_1, potionType = PotionType.EXTREME_MAGIC),
    EXTREME_MAGIC1(item = Items.EXTREME_MAGIC_1, replacement = Items.VIAL, potionType = PotionType.EXTREME_MAGIC),
    EXTREME_RANGING4(item = Items.EXTREME_RANGING_4, replacement = Items.EXTREME_RANGING_3, potionType = PotionType.EXTREME_RANGING),
    EXTREME_RANGING3(item = Items.EXTREME_RANGING_3, replacement = Items.EXTREME_RANGING_2, potionType = PotionType.EXTREME_RANGING),
    EXTREME_RANGING2(item = Items.EXTREME_RANGING_2, replacement = Items.EXTREME_RANGING_1, potionType = PotionType.EXTREME_RANGING),
    EXTREME_RANGING1(item = Items.EXTREME_RANGING_1, replacement = Items.VIAL, potionType = PotionType.EXTREME_RANGING),
    OVERLOAD4(item = Items.OVERLOAD_4, replacement = Items.OVERLOAD_3, potionType = PotionType.OVERLOAD),
    OVERLOAD3(item = Items.OVERLOAD_3, replacement = Items.OVERLOAD_2, potionType = PotionType.OVERLOAD),
    OVERLOAD2(item = Items.OVERLOAD_2, replacement = Items.OVERLOAD_1, potionType = PotionType.OVERLOAD),
    OVERLOAD1(item = Items.OVERLOAD_1, replacement = Items.VIAL, potionType = PotionType.OVERLOAD),
    PRAYER_RENEWAL4(item = Items.PRAYER_RENEWAL_4, replacement = Items.PRAYER_RENEWAL_3, potionType = PotionType.PRAYER_RENEWAL),
    PRAYER_RENEWAL3(item = Items.PRAYER_RENEWAL_3, replacement = Items.PRAYER_RENEWAL_2, potionType = PotionType.PRAYER_RENEWAL),
    PRAYER_RENEWAL2(item = Items.PRAYER_RENEWAL_2, replacement = Items.PRAYER_RENEWAL_1, potionType = PotionType.PRAYER_RENEWAL),
    PRAYER_RENEWAL1(item = Items.PRAYER_RENEWAL_1, replacement = Items.VIAL, potionType = PotionType.PRAYER_RENEWAL),
    RECOVER_SPECIAL4(item = Items.RECOVER_SPECIAL_4, replacement = Items.RECOVER_SPECIAL_3, potionType = PotionType.RECOVER_SPECIAL),
    RECOVER_SPECIAL3(item = Items.RECOVER_SPECIAL_3, replacement = Items.RECOVER_SPECIAL_2, potionType = PotionType.RECOVER_SPECIAL),
    RECOVER_SPECIAL2(item = Items.RECOVER_SPECIAL_2, replacement = Items.RECOVER_SPECIAL_1, potionType = PotionType.RECOVER_SPECIAL),
    RECOVER_SPECIAL1(item = Items.RECOVER_SPECIAL_1, replacement = Items.VIAL, potionType = PotionType.RECOVER_SPECIAL),
    SANFEW_SERUM4(item = Items.SANFEW_SERUM_4, replacement = Items.SANFEW_SERUM_3, potionType = PotionType.SANFEW_SERUM),
    SANFEW_SERUM3(item = Items.SANFEW_SERUM_3, replacement = Items.SANFEW_SERUM_2, potionType = PotionType.SANFEW_SERUM),
    SANFEW_SERUM2(item = Items.SANFEW_SERUM_2, replacement = Items.SANFEW_SERUM_1, potionType = PotionType.SANFEW_SERUM),
    SANFEW_SERUM1(item = Items.SANFEW_SERUM_1, replacement = Items.VIAL, potionType = PotionType.SANFEW_SERUM),
    ANTIPOISON_PLUS4(item = Items.ANTIPOISON_4_5943, replacement = Items.ANTIPOISON_3_5945, potionType = PotionType.ANTIPOISON_PLUS),
    ANTIPOISON_PLUS3(item = Items.ANTIPOISON_3_5945, replacement = Items.ANTIPOISON_2_5947, potionType = PotionType.ANTIPOISON_PLUS),
    ANTIPOISON_PLUS2(item = Items.ANTIPOISON_2_5947, replacement = Items.ANTIPOISON_1_5949, potionType = PotionType.ANTIPOISON_PLUS),
    ANTIPOISON_PLUS1(item = Items.ANTIPOISON_1_5949, replacement = Items.VIAL, potionType = PotionType.ANTIPOISON_PLUS),
    ANTIPOISON_PLUS_PLUS4(item = Items.ANTIPOISON_4_5952, replacement = Items.ANTIPOISON_3_5954, potionType = PotionType.ANTIPOISON_PLUS_PLUS),
    ANTIPOISON_PLUS_PLUS3(item = Items.ANTIPOISON_3_5954, replacement = Items.ANTIPOISON_2_5956, potionType = PotionType.ANTIPOISON_PLUS_PLUS),
    ANTIPOISON_PLUS_PLUS2(item = Items.ANTIPOISON_2_5956, replacement = Items.ANTIPOISON_1_5958, potionType = PotionType.ANTIPOISON_PLUS_PLUS),
    ANTIPOISON_PLUS_PLUS1(item = Items.ANTIPOISON_1_5958, replacement = Items.VIAL, potionType = PotionType.ANTIPOISON_PLUS_PLUS),
    MAGIC_ESSENCE4(item = Items.MAGIC_ESSENCE_4, replacement = Items.MAGIC_ESSENCE_3, potionType = PotionType.MAGIC_ESSENCE),
    MAGIC_ESSENCE3(item = Items.MAGIC_ESSENCE_3, replacement = Items.MAGIC_ESSENCE_2, potionType = PotionType.MAGIC_ESSENCE),
    MAGIC_ESSENCE2(item = Items.MAGIC_ESSENCE_2, replacement = Items.MAGIC_ESSENCE_1, potionType = PotionType.MAGIC_ESSENCE),
    MAGIC_ESSENCE1(item = Items.MAGIC_ESSENCE_1, replacement = Items.VIAL, potionType = PotionType.MAGIC_ESSENCE),

    // OSRS-IMPORT potions-combat.
    SUPER_COMBAT4(item = Items.SUPER_COMBAT_POTION_4, replacement = Items.SUPER_COMBAT_POTION_3, potionType = PotionType.SUPER_COMBAT),
    SUPER_COMBAT3(item = Items.SUPER_COMBAT_POTION_3, replacement = Items.SUPER_COMBAT_POTION_2, potionType = PotionType.SUPER_COMBAT),
    SUPER_COMBAT2(item = Items.SUPER_COMBAT_POTION_2, replacement = Items.SUPER_COMBAT_POTION_1, potionType = PotionType.SUPER_COMBAT),
    SUPER_COMBAT1(item = Items.SUPER_COMBAT_POTION_1, replacement = Items.VIAL, potionType = PotionType.SUPER_COMBAT),
    DIVINE_SUPER_COMBAT4(item = Items.DIVINE_SUPER_COMBAT_POTION_4, replacement = Items.DIVINE_SUPER_COMBAT_POTION_3, potionType = PotionType.DIVINE_SUPER_COMBAT),
    DIVINE_SUPER_COMBAT3(item = Items.DIVINE_SUPER_COMBAT_POTION_3, replacement = Items.DIVINE_SUPER_COMBAT_POTION_2, potionType = PotionType.DIVINE_SUPER_COMBAT),
    DIVINE_SUPER_COMBAT2(item = Items.DIVINE_SUPER_COMBAT_POTION_2, replacement = Items.DIVINE_SUPER_COMBAT_POTION_1, potionType = PotionType.DIVINE_SUPER_COMBAT),
    DIVINE_SUPER_COMBAT1(item = Items.DIVINE_SUPER_COMBAT_POTION_1, replacement = Items.VIAL, potionType = PotionType.DIVINE_SUPER_COMBAT),
    DIVINE_SUPER_ATTACK4(item = Items.DIVINE_SUPER_ATTACK_POTION_4, replacement = Items.DIVINE_SUPER_ATTACK_POTION_3, potionType = PotionType.DIVINE_SUPER_ATTACK),
    DIVINE_SUPER_ATTACK3(item = Items.DIVINE_SUPER_ATTACK_POTION_3, replacement = Items.DIVINE_SUPER_ATTACK_POTION_2, potionType = PotionType.DIVINE_SUPER_ATTACK),
    DIVINE_SUPER_ATTACK2(item = Items.DIVINE_SUPER_ATTACK_POTION_2, replacement = Items.DIVINE_SUPER_ATTACK_POTION_1, potionType = PotionType.DIVINE_SUPER_ATTACK),
    DIVINE_SUPER_ATTACK1(item = Items.DIVINE_SUPER_ATTACK_POTION_1, replacement = Items.VIAL, potionType = PotionType.DIVINE_SUPER_ATTACK),
    DIVINE_SUPER_STRENGTH4(item = Items.DIVINE_SUPER_STRENGTH_POTION_4, replacement = Items.DIVINE_SUPER_STRENGTH_POTION_3, potionType = PotionType.DIVINE_SUPER_STRENGTH),
    DIVINE_SUPER_STRENGTH3(item = Items.DIVINE_SUPER_STRENGTH_POTION_3, replacement = Items.DIVINE_SUPER_STRENGTH_POTION_2, potionType = PotionType.DIVINE_SUPER_STRENGTH),
    DIVINE_SUPER_STRENGTH2(item = Items.DIVINE_SUPER_STRENGTH_POTION_2, replacement = Items.DIVINE_SUPER_STRENGTH_POTION_1, potionType = PotionType.DIVINE_SUPER_STRENGTH),
    DIVINE_SUPER_STRENGTH1(item = Items.DIVINE_SUPER_STRENGTH_POTION_1, replacement = Items.VIAL, potionType = PotionType.DIVINE_SUPER_STRENGTH),
    DIVINE_SUPER_DEFENCE4(item = Items.DIVINE_SUPER_DEFENCE_POTION_4, replacement = Items.DIVINE_SUPER_DEFENCE_POTION_3, potionType = PotionType.DIVINE_SUPER_DEFENCE),
    DIVINE_SUPER_DEFENCE3(item = Items.DIVINE_SUPER_DEFENCE_POTION_3, replacement = Items.DIVINE_SUPER_DEFENCE_POTION_2, potionType = PotionType.DIVINE_SUPER_DEFENCE),
    DIVINE_SUPER_DEFENCE2(item = Items.DIVINE_SUPER_DEFENCE_POTION_2, replacement = Items.DIVINE_SUPER_DEFENCE_POTION_1, potionType = PotionType.DIVINE_SUPER_DEFENCE),
    DIVINE_SUPER_DEFENCE1(item = Items.DIVINE_SUPER_DEFENCE_POTION_1, replacement = Items.VIAL, potionType = PotionType.DIVINE_SUPER_DEFENCE),
    DIVINE_RANGING4(item = Items.DIVINE_RANGING_POTION_4, replacement = Items.DIVINE_RANGING_POTION_3, potionType = PotionType.DIVINE_RANGING),
    DIVINE_RANGING3(item = Items.DIVINE_RANGING_POTION_3, replacement = Items.DIVINE_RANGING_POTION_2, potionType = PotionType.DIVINE_RANGING),
    DIVINE_RANGING2(item = Items.DIVINE_RANGING_POTION_2, replacement = Items.DIVINE_RANGING_POTION_1, potionType = PotionType.DIVINE_RANGING),
    DIVINE_RANGING1(item = Items.DIVINE_RANGING_POTION_1, replacement = Items.VIAL, potionType = PotionType.DIVINE_RANGING),
    DIVINE_MAGIC4(item = Items.DIVINE_MAGIC_POTION_4, replacement = Items.DIVINE_MAGIC_POTION_3, potionType = PotionType.DIVINE_MAGIC),
    DIVINE_MAGIC3(item = Items.DIVINE_MAGIC_POTION_3, replacement = Items.DIVINE_MAGIC_POTION_2, potionType = PotionType.DIVINE_MAGIC),
    DIVINE_MAGIC2(item = Items.DIVINE_MAGIC_POTION_2, replacement = Items.DIVINE_MAGIC_POTION_1, potionType = PotionType.DIVINE_MAGIC),
    DIVINE_MAGIC1(item = Items.DIVINE_MAGIC_POTION_1, replacement = Items.VIAL, potionType = PotionType.DIVINE_MAGIC),
    BATTLEMAGE4(item = Items.BATTLEMAGE_POTION_4, replacement = Items.BATTLEMAGE_POTION_3, potionType = PotionType.BATTLEMAGE),
    BATTLEMAGE3(item = Items.BATTLEMAGE_POTION_3, replacement = Items.BATTLEMAGE_POTION_2, potionType = PotionType.BATTLEMAGE),
    BATTLEMAGE2(item = Items.BATTLEMAGE_POTION_2, replacement = Items.BATTLEMAGE_POTION_1, potionType = PotionType.BATTLEMAGE),
    BATTLEMAGE1(item = Items.BATTLEMAGE_POTION_1, replacement = Items.VIAL, potionType = PotionType.BATTLEMAGE),
    BASTION4(item = Items.BASTION_POTION_4, replacement = Items.BASTION_POTION_3, potionType = PotionType.BASTION),
    BASTION3(item = Items.BASTION_POTION_3, replacement = Items.BASTION_POTION_2, potionType = PotionType.BASTION),
    BASTION2(item = Items.BASTION_POTION_2, replacement = Items.BASTION_POTION_1, potionType = PotionType.BASTION),
    BASTION1(item = Items.BASTION_POTION_1, replacement = Items.VIAL, potionType = PotionType.BASTION),
    DIVINE_BATTLEMAGE4(item = Items.DIVINE_BATTLEMAGE_POTION_4, replacement = Items.DIVINE_BATTLEMAGE_POTION_3, potionType = PotionType.DIVINE_BATTLEMAGE),
    DIVINE_BATTLEMAGE3(item = Items.DIVINE_BATTLEMAGE_POTION_3, replacement = Items.DIVINE_BATTLEMAGE_POTION_2, potionType = PotionType.DIVINE_BATTLEMAGE),
    DIVINE_BATTLEMAGE2(item = Items.DIVINE_BATTLEMAGE_POTION_2, replacement = Items.DIVINE_BATTLEMAGE_POTION_1, potionType = PotionType.DIVINE_BATTLEMAGE),
    DIVINE_BATTLEMAGE1(item = Items.DIVINE_BATTLEMAGE_POTION_1, replacement = Items.VIAL, potionType = PotionType.DIVINE_BATTLEMAGE),
    DIVINE_BASTION4(item = Items.DIVINE_BASTION_POTION_4, replacement = Items.DIVINE_BASTION_POTION_3, potionType = PotionType.DIVINE_BASTION),
    DIVINE_BASTION3(item = Items.DIVINE_BASTION_POTION_3, replacement = Items.DIVINE_BASTION_POTION_2, potionType = PotionType.DIVINE_BASTION),
    DIVINE_BASTION2(item = Items.DIVINE_BASTION_POTION_2, replacement = Items.DIVINE_BASTION_POTION_1, potionType = PotionType.DIVINE_BASTION),
    DIVINE_BASTION1(item = Items.DIVINE_BASTION_POTION_1, replacement = Items.VIAL, potionType = PotionType.DIVINE_BASTION),

    // OSRS-IMPORT potions-venom.
    ANTI_VENOM4(item = Items.ANTI_VENOM_4, replacement = Items.ANTI_VENOM_3, potionType = PotionType.ANTI_VENOM),
    ANTI_VENOM3(item = Items.ANTI_VENOM_3, replacement = Items.ANTI_VENOM_2, potionType = PotionType.ANTI_VENOM),
    ANTI_VENOM2(item = Items.ANTI_VENOM_2, replacement = Items.ANTI_VENOM_1, potionType = PotionType.ANTI_VENOM),
    ANTI_VENOM1(item = Items.ANTI_VENOM_1, replacement = Items.VIAL, potionType = PotionType.ANTI_VENOM),
    ANTI_VENOM_PLUS4(item = Items.ANTI_VENOM_PLUS_4, replacement = Items.ANTI_VENOM_PLUS_3, potionType = PotionType.ANTI_VENOM_PLUS),
    ANTI_VENOM_PLUS3(item = Items.ANTI_VENOM_PLUS_3, replacement = Items.ANTI_VENOM_PLUS_2, potionType = PotionType.ANTI_VENOM_PLUS),
    ANTI_VENOM_PLUS2(item = Items.ANTI_VENOM_PLUS_2, replacement = Items.ANTI_VENOM_PLUS_1, potionType = PotionType.ANTI_VENOM_PLUS),
    ANTI_VENOM_PLUS1(item = Items.ANTI_VENOM_PLUS_1, replacement = Items.VIAL, potionType = PotionType.ANTI_VENOM_PLUS),
    EXTENDED_ANTI_VENOM_PLUS4(item = Items.EXTENDED_ANTI_VENOM_PLUS_4, replacement = Items.EXTENDED_ANTI_VENOM_PLUS_3, potionType = PotionType.EXTENDED_ANTI_VENOM_PLUS),
    EXTENDED_ANTI_VENOM_PLUS3(item = Items.EXTENDED_ANTI_VENOM_PLUS_3, replacement = Items.EXTENDED_ANTI_VENOM_PLUS_2, potionType = PotionType.EXTENDED_ANTI_VENOM_PLUS),
    EXTENDED_ANTI_VENOM_PLUS2(item = Items.EXTENDED_ANTI_VENOM_PLUS_2, replacement = Items.EXTENDED_ANTI_VENOM_PLUS_1, potionType = PotionType.EXTENDED_ANTI_VENOM_PLUS),
    EXTENDED_ANTI_VENOM_PLUS1(item = Items.EXTENDED_ANTI_VENOM_PLUS_1, replacement = Items.VIAL, potionType = PotionType.EXTENDED_ANTI_VENOM_PLUS),
}
