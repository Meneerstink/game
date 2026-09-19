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
import gg.rsmod.plugins.api.ext.playJingle
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.quests.Quest
import gg.rsmod.plugins.content.quests.QuestStage
import gg.rsmod.plugins.content.quests.buildQuestFinish
import gg.rsmod.plugins.content.skills.summoning.Familiar

/**
 * Small, idempotent reward service for the convenience NPCs in the Grand Exchange hub.
 * The completion popup is the normal revision-667 quest interface (277), so these rewards do
 * not rely on a bespoke client interface or silently disappear when the inventory is full.
 */
object UnlockNpcRewards {
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

    fun unlockAncientCurses(player: Player): Boolean {
        player.attr[AncientCurses.NPC_UNLOCKED_ATTR] = true
        player.attr[AncientCurses.UNLOCKED_ATTR] = true
        if (player.attr[ANCIENT_CURSES_REWARDED] == true) {
            player.message("The Ancient Curses are already unlocked.")
            return false
        }
        player.attr[ANCIENT_CURSES_REWARDED] = true
        player.addXp(Skills.PRAYER, 10_000.0)
        grant(player, Items.EXPERIENCE_LAMP)
        grant(player, Items.COMBAT_LAMP_15390, 2)
        if (!player.inventory.contains(Items.ANCIENT_HYMNAL)) grant(player, Items.ANCIENT_HYMNAL)
        complete(
            player,
            name = "Ancient Curses",
            icon = Items.ANCIENT_HYMNAL,
            "10,000 Prayer experience",
            "23,000 skills experience lamp for any chosen skill (level 50+)",
            "Two 20,000 combat level experience lamps for any chosen combat skill (level 50+)",
            "Access to a new set of prayers, known as the Ancient Curses.",
            "You must read the Ancient hymnal, given to you by Azzanadra, in order to activate them.",
        )
        return true
    }

    fun unlockSummoning(player: Player): Boolean {
        if (player.attr[SUMMONING_REWARDED] == true) {
            player.message("Summoning is already unlocked.")
            return false
        }
        player.attr[SUMMONING_REWARDED] = true
        Familiar.unlockInterface(player)
        player.addXp(Skills.SUMMONING, 20_000.0)
        complete(
            player,
            name = "Summoning",
            icon = Items.WOLF_WHISTLE,
            "You have unlocked Summoning",
            "20,000 Summoning experience",
        )
        return true
    }

    fun unlockAncientMagic(player: Player): Boolean {
        player.attr[ANCIENT_MAGIC_UNLOCKED] = true
        if (player.attr[ANCIENT_MAGIC_REWARDED] == true) {
            player.message("Ancient Magicks are already unlocked.")
            return false
        }
        player.attr[ANCIENT_MAGIC_REWARDED] = true
        player.addXp(Skills.MAGIC, 20_000.0)
        grant(player, Items.RING_OF_VISIBILITY)
        grant(player, Items.ANCIENT_STAFF)
        grant(player, Items.BANDIT_CAMP_TELEPORT)
        complete(
            player,
            name = "Ancient Magicks",
            icon = Items.ANCIENT_STAFF,
            "20,000 Magic experience",
            "Ancient Magicks unlocked",
            "Ring of visibility added to your inventory",
            "You can now buy and wield the ancient staff",
            "Access to the Bandit Camp home teleport",
        )
        return true
    }

    fun unlockLunarMagic(player: Player): Boolean {
        player.attr[LUNAR_MAGIC_UNLOCKED] = true
        if (player.attr[LUNAR_MAGIC_REWARDED] == true) {
            player.message("Lunar Magicks are already unlocked.")
            return false
        }
        player.attr[LUNAR_MAGIC_REWARDED] = true
        player.addXp(Skills.MAGIC, 20_000.0)
        player.addXp(Skills.RUNECRAFTING, 10_000.0)
        // This is the cache-backed completed Lunar Diplomacy value used by the quest tab and
        // Lunar Isle teleport requirement; it is not a guessed private flag.
        player.setVarbit(Varbits.LUNAR_DIPLOMACY_PROGRESS, 190)
        complete(
            player,
            name = "Lunar Magicks",
            icon = Items.LUNAR_ISLE_TELEPORT,
            "20,000 Magic experience",
            "10,000 Runecrafting experience",
            "Access to Lunar Isle",
            "Access to the Lunar spellbook",
        )
        return true
    }

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

    fun completeDragonSlayerII(player: Player): Boolean {
        if (player.attr[DRAGON_SLAYER_II_REWARDED] == true) {
            player.message("Dragon Slayer II is already completed.")
            return false
        }
        player.attr[DRAGON_SLAYER_II_REWARDED] = true
        player.attr[DRAGON_SLAYER_II_UNLOCKED] = true
        player.attr[MYTHS_GUILD_UNLOCKED] = true
        player.attr[AVAS_ASSEMBLER_UNLOCKED] = true
        player.setVarp(Varps.QUEST_POINTS, player.getVarp(Varps.QUEST_POINTS) + 5)
        player.addXp(Skills.RANGED, 80_000.0)
        player.addXp(Skills.MINING, 60_000.0)
        player.addXp(Skills.AGILITY, 50_000.0)
        player.addXp(Skills.THIEVING, 50_000.0)
        complete(
            player,
            name = "Dragon Slayer II",
            icon = Items.VORKATHS_HEAD,
            "5 Quest Points",
            "80,000 Ranged experience",
            "60,000 Mining experience",
            "50,000 Agility experience",
            "50,000 Thieving experience",
            "Access to Myth's Guild",
            "Ability to create Ava's assembler",
        )
        return true
    }

    fun completeDesertTreasureII(player: Player): Boolean {
        if (player.attr[DESERT_TREASURE_II_REWARDED] == true) {
            player.message("Desert Treasure II is already completed.")
            return false
        }
        player.attr[DESERT_TREASURE_II_REWARDED] = true
        player.attr[DESERT_TREASURE_II_UNLOCKED] = true
        player.attr[SCAR_ESSENCE_MINE_UNLOCKED] = true
        player.attr[ANCIENT_RINGS_UNLOCKED] = true
        player.attr[FORGOTTEN_FOUR_UNLOCKED] = true
        player.attr[DEMONIC_BRUTUS_UNLOCKED] = true
        player.setVarp(Varps.QUEST_POINTS, player.getVarp(Varps.QUEST_POINTS) + 5)
        // Owner 2026-09-19 ("we forgot to add ring of shadows reward"): OSRS hands the uncharged Ring of shadows at the end
        // of the quest; it is charged with blood, soul, death and law runes (ring_of_shadows.plugin.kts).
        grant(player, Items.RING_OF_SHADOWS_UNCHARGED)
        complete(
            player,
            name = "Desert Treasure II",
            icon = Items.RING_OF_VISIBILITY,
            "5 Quest Points",
            "Three ancient lamps, each offering 100,000 experience in Attack, Strength, Defence, Constitution, Ranged, Magic or Prayer at level 60+",
            "Access to Scar essence mine",
            "Ring of shadows",
            "Ability to wear ancient rings",
            "Ability to repeat the Forgotten Four encounters and challenge their awakened variants",
            "Ability to fight Demonic Brutus using abyssal potatoes",
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
        // Jingle 61 is the already-used completion jingle in this revision's server (Slayer task
        // completion uses the same effect); the visible reward popup is the standard quest UI.
        player.playJingle(61)
        player.buildQuestFinish(UnlockQuest(name), icon, *rewards)
    }

    private class UnlockQuest(name: String) :
        Quest(name, "Grand Exchange", emptyList(), "None", "None", "", 0, 0, 0, 0, 1) {
        override fun getObjective(player: Player, stage: Int): QuestStage = QuestStage(emptyList())
        override fun finishQuest(player: Player) = Unit
    }
}
