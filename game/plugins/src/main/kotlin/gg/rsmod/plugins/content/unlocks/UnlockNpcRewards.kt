package gg.rsmod.plugins.content.unlocks

import gg.rsmod.game.model.attr.AttributeKey

/**
 * The saved unlock flags of the quest unlocks (read by the gates of each unlock). Owner 2026-09-27 (final check): the old
 * claim npcs of the Grand Exchange hall are gone - Recipe for Disaster, While Guthix Sleeps and Monkey Madness are
 * completed for every account through QuestStatus, which their unlocks read; the *_REWARDED keys only stay so old saves
 * keep loading.
 */
object UnlockNpcRewards {
    // Owner 2026-09-26 (new-player foundation): Desert Treasure, Lunar Diplomacy, The Temple at Senntisten, Desert
    // Treasure II and Dragon Slayer II are no longer handed out here. Their unlocks come only from their short quests or
    // the Quest Guide's permanent choice (quests/foundation, FoundationRewards.complete), which set the flags below;
    // Summoning is the one-time start of newplayer/SummoningKit.
    val ANCIENT_CURSES_REWARDED = AttributeKey<Boolean>(persistenceKey = "ge_ancient_curses_rewarded")
    val SUMMONING_REWARDED = AttributeKey<Boolean>(persistenceKey = "ge_summoning_rewarded")
    val ANCIENT_MAGIC_REWARDED = AttributeKey<Boolean>(persistenceKey = "ge_ancient_magic_rewarded")
    val LUNAR_MAGIC_REWARDED = AttributeKey<Boolean>(persistenceKey = "ge_lunar_magic_rewarded")
    val RFD_REWARDED = AttributeKey<Boolean>(persistenceKey = "ge_recipe_for_disaster_rewarded")
    val DESERT_TREASURE_II_REWARDED = AttributeKey<Boolean>(persistenceKey = "ge_desert_treasure_ii_rewarded")
    val DRAGON_SLAYER_II_REWARDED = AttributeKey<Boolean>(persistenceKey = "ge_dragon_slayer_ii_rewarded")
    val WHILE_GUTHIX_SLEEPS_REWARDED = AttributeKey<Boolean>(persistenceKey = "ge_while_guthix_sleeps_rewarded")
    val MONKEY_MADNESS_REWARDED = AttributeKey<Boolean>(persistenceKey = "ge_monkey_madness_rewarded")

    val ANCIENT_MAGIC_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "ancient_magic_unlocked")
    val LUNAR_MAGIC_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "lunar_magic_unlocked")
    val DESERT_TREASURE_II_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "desert_treasure_ii_unlocked")
    val DRAGON_SLAYER_II_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "dragon_slayer_ii_unlocked")
    val MYTHS_GUILD_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "myths_guild_unlocked")
    val SCAR_ESSENCE_MINE_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "scar_essence_mine_unlocked")
    val ANCIENT_RINGS_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "ancient_rings_unlocked")
    val FORGOTTEN_FOUR_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "forgotten_four_unlocked")
    val DEMONIC_BRUTUS_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "demonic_brutus_unlocked")
    val AVAS_ASSEMBLER_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "avas_assembler_unlocked")
    val TORMENTED_DEMONS_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "tormented_demons_unlocked")
    val DEMONBANE_WEAPONS_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "demonbane_weapons_unlocked")
}
