package gg.rsmod.plugins.content.unlocks

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Varbits
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.quests.Quest
import gg.rsmod.plugins.content.quests.QuestStage
import gg.rsmod.plugins.content.quests.buildQuestFinish

/**
 * Small, idempotent reward service for the convenience NPCs in the Grand Exchange hub.
 * The completion popup is the normal revision-667 quest interface (277), so these rewards do
 * not rely on a bespoke client interface or silently disappear when the inventory is full.
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

    fun completeRecipeForDisaster(player: Player): Boolean {
        if (player.attr[RFD_REWARDED] == true) {
            player.message("Recipe for Disaster is already completed.")
            return false
        }
        player.attr[RFD_REWARDED] = true
        player.setVarbit(Varbits.RECIPE_FOR_DISASTER_PROGRESS, 5)
        player.addXp(Skills.COOKING, 28_000.0)
        player.addXp(Skills.SLAYER, 1_000.0)
        player.addXp(Skills.CRAFTING, 3_500.0)
        player.addXp(Skills.FISHING, 1_000.0)
        player.addXp(Skills.SMITHING, 1_000.0)
        player.addXp(Skills.FARMING, 1_000.0)
        player.addXp(Skills.MAGIC, 2_500.0)
        player.addXp(Skills.WOODCUTTING, 1_500.0)
        player.addXp(Skills.RANGED, 1_500.0)
        player.addXp(Skills.CONSTITUTION, 4_000.0)
        player.addXp(Skills.AGILITY, 10_000.0)
        grant(player, Items.ANTIQUE_LAMP_11753)
        complete(
            player,
            name = "Recipe for Disaster",
            icon = Items.ANTIQUE_LAMP_11753,
            "28,000 Cooking experience",
            "1,000 Slayer experience",
            "3,500 Crafting experience",
            "1,000 Fishing experience",
            "1,000 Smithing experience",
            "1,000 Farming experience",
            "2,500 Magic experience",
            "1,500 Woodcutting experience",
            "1,500 Ranged experience",
            "4,000 Constitution experience",
            "10,000 Agility experience",
            "20,000 experience antique lamp for any skill above level 50 (can be banked)",
            "Access to the Culinaromancer's Chest in the Lumbridge cellar to buy gloves",
        )
        return true
    }

    fun completeWhileGuthixSleeps(player: Player): Boolean {
        if (player.attr[WHILE_GUTHIX_SLEEPS_REWARDED] == true) {
            player.message("While Guthix Sleeps is already completed.")
            return false
        }
        player.attr[WHILE_GUTHIX_SLEEPS_REWARDED] = true
        player.attr[TORMENTED_DEMONS_UNLOCKED] = true
        player.attr[DEMONBANE_WEAPONS_UNLOCKED] = true
        player.setVarbit(Varbits.WHILE_GUTHIX_SLEEPS_PROGRESS, 910)
        player.setVarp(Varps.QUEST_POINTS, player.getVarp(Varps.QUEST_POINTS) + 5)
        player.addXp(Skills.THIEVING, 80_000.0)
        player.addXp(Skills.FARMING, 75_000.0)
        player.addXp(Skills.HERBLORE, 75_000.0)
        player.addXp(Skills.HUNTER, 50_000.0)
        complete(
            player,
            name = "While Guthix Sleeps",
            icon = Items.EMBERLIGHT,
            "5 Quest Points",
            "80,000 Thieving experience",
            "75,000 Farming experience",
            "75,000 Herblore experience",
            "50,000 Hunter experience",
            "You can now kill Tormented demons",
            "You can now wear Emberlight, the Scorching bow and the Purging staff",
        )
        return true
    }

    fun completeMonkeyMadness(player: Player, focusedPair: MonkeyPair): Boolean {
        if (player.attr[MONKEY_MADNESS_REWARDED] == true) {
            player.message("Monkey Madness is already completed.")
            return false
        }
        player.attr[MONKEY_MADNESS_REWARDED] = true
        player.setVarp(Varps.MONKEY_MADNESS_PROGRESS, 9)
        when (focusedPair) {
            MonkeyPair.STRENGTH_CONSTITUTION -> {
                player.addXp(Skills.STRENGTH, 35_000.0)
                player.addXp(Skills.CONSTITUTION, 35_000.0)
                player.addXp(Skills.ATTACK, 20_000.0)
                player.addXp(Skills.DEFENCE, 20_000.0)
            }
            MonkeyPair.ATTACK_DEFENCE -> {
                player.addXp(Skills.ATTACK, 35_000.0)
                player.addXp(Skills.DEFENCE, 35_000.0)
                player.addXp(Skills.STRENGTH, 20_000.0)
                player.addXp(Skills.CONSTITUTION, 20_000.0)
            }
        }
        grant(player, Items.COINS_995, 10_000)
        grant(player, Items.DIAMOND, 3)
        complete(
            player,
            name = "Monkey Madness",
            icon = Items.DRAGON_SCIMITAR,
            "110,000 combat experience",
            "10,000 coins",
            "3 cut diamonds",
            "Access to Ape Atoll and its Agility Course",
            "Ability to wield the dragon scimitar",
        )
        return true
    }

    enum class MonkeyPair { STRENGTH_CONSTITUTION, ATTACK_DEFENCE }

    private fun grant(player: Player, item: Int, amount: Int = 1) {
        val result = player.inventory.add(item, amount)
        val left = amount - result.completed
        if (left > 0) player.world.spawn(GroundItem(item, left, player.tile, player))
    }

    private fun complete(player: Player, name: String, icon: Int, vararg rewards: String) {
        // The standard quest UI; buildQuestFinish plays the sourced quest-complete jingle (152) itself.
        player.buildQuestFinish(UnlockQuest(name), icon, *rewards)
    }

    private class UnlockQuest(name: String) :
        Quest(name, "Grand Exchange", emptyList(), "None", "None", "", 0, 0, 0, 0, 1) {
        override fun getObjective(player: Player, stage: Int): QuestStage = QuestStage(emptyList())
        override fun finishQuest(player: Player) = Unit
    }
}
