package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.QuestFight
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q28: Heroes' Quest (bracket 91-110). OSRS Wiki: 55 quest points, Cooking 53, Fishing 53, Herblore 25, Mining 50 (boostable),
// Shield of Arrav, Lost City, Merlin's Crystal, Dragon Slayer; Ice Queen (111), Entrana firebird (2), Grip (22); 1 QP, XP in 12 skills.
// ONTGR: the Heroes' Guild's Helemos shop, the Fountain of Heroes (glory recharging) and wielding the Dragon battleaxe and Dragon mace
// (OsrsQuestRequirements.WEAR). The Heroes' Guild door itself is not gated (see dossier BRACKET-QUESTS). The partner of the original
// quest is not built: the Phoenix route (Straven) is the only route.

private val ACHIETTIES_POST = Tile(2900, 3512, 0)
private val ICE_QUEEN_FIGHT = Tile(2863, 9959, 0)
private val FIREBIRD_FIGHT = Tile(2847, 3386, 0)
private val STRAVEN_POST = Tile(3246, 9781, 0)
private val GRIP_FIGHT = Tile(2774, 3197, 0)
private val GERRANT_POST = Tile(3013, 3221, 0)

object HeroesQuest : ShortQuest(
    name = "Heroes' Quest",
    startPoint = "Speak to Achietties outside the Heroes' Guild, between Taverley and Burthorpe.",
    rewards = "1 Quest Point, XP in twelve skills, access to the Heroes' Guild, the Dragon battleaxe and the Dragon mace.",
    pointReward = 1,
    questId = Varps.HEROES_QUEST_PROGRESS,
    slot = 60,
    completedValue = 8, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.ACHIETTIES,
    startNpcName = "Achietties",
    startPlace = "outside the Heroes' Guild",
    startLocation = ACHIETTIES_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.COOKING to 53, Skills.FISHING to 53, Skills.HERBLORE to 25, Skills.MINING to 50),
            quests = listOf("Shield of Arrav", "Lost City", "Merlin's Crystal", "Dragon Slayer"),
            questPoints = 55,
            boostable = setOf(Skills.COOKING, Skills.FISHING, Skills.HERBLORE, Skills.MINING),
        )

    override val recommendation = Recommendation.combat(70)

    override val reward =
        QuestReward(
            icon = Items.DRAGON_BATTLEAXE,
            xp =
                listOf(
                    Skills.ATTACK to 3_075.0, Skills.DEFENCE to 3_075.0, Skills.STRENGTH to 3_075.0, Skills.CONSTITUTION to 3_075.0,
                    Skills.RANGED to 2_075.0, Skills.FISHING to 2_725.0, Skills.COOKING to 2_825.0, Skills.WOODCUTTING to 1_575.0,
                    Skills.FIREMAKING to 1_575.0, Skills.SMITHING to 2_275.0, Skills.MINING to 2_575.0, Skills.HERBLORE to 1_325.0,
                ),
            lines = listOf("1 Quest Point", "XP in twelve skills (Attack ... Herblore)", "Access to the Heroes' Guild", "The Dragon battleaxe and Dragon mace"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "So you want to join the Heroes' Guild? Bring me a fire",
            "feather, a master thieves' armband and a lava eel,",
            "and the guild is yours.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll prove myself.", "Not right now.", title = "Start Heroes' Quest?") != 1) return false
        chatPlayer("I'll bring the three things.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("A fire feather lies with the Entrana firebird, but its", "lair is guarded by the cold: bring ice gloves, from the Ice", "Queen.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.ACHIETTIES,
                journal = listOf("I must defeat the ${red("Ice Queen")} (level 111) below White Wolf Mountain", "for her ice gloves."),
                done = listOf("I defeated the Ice Queen and took the ice gloves."),
                place = "the Ice Queen's lair",
                location = ICE_QUEEN_FIGHT,
                tip = "The Ice Queen is level 111: bring good food and prayer.",
                gives = listOf(Items.ICE_GLOVES to 1),
                fight = QuestFight(Npcs.ICE_QUEEN, "Ice Queen", 111, ICE_QUEEN_FIGHT, "Who dares enter my domain?"),
                talk = {
                    chatNpc("The Ice Queen waits in the cave below White Wolf", "Mountain. Defeat her and take the gloves.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ACHIETTIES,
                journal = listOf("I must defeat the ${red("Entrana firebird")} (level 2) wearing the ${red("ice gloves")} for a", "fire feather."),
                done = listOf("I took a fire feather from the firebird."),
                place = "Entrana",
                location = FIREBIRD_FIGHT,
                tip = "Wear the ice gloves: the fire feather burns you.",
                gives = listOf(Items.FIRE_FEATHER to 1),
                fight = QuestFight(Npcs.ENTRANA_FIREBIRD, "Entrana firebird", 2, FIREBIRD_FIGHT, "Squawk!"),
                talk = {
                    chatNpc("The firebird roosts on Entrana. Wear the ice gloves!", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.STRAVEN,
                journal = listOf("I should speak to ${red("Straven")} of the Phoenix Gang about the armband."),
                done = listOf("Straven told me to fetch Pete's candlestick."),
                place = "the Phoenix Gang hideout",
                location = STRAVEN_POST,
                talk = {
                    chatNpc("You want a master thieves' armband? Steal Scarface", "Pete's candlestick from his manor in Brimhaven, then", "come back.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GRIP,
                journal = listOf("I must defeat ${red("Grip")} (level 22) in Scarface Pete's manor for the candlestick."),
                done = listOf("I took Pete's candlestick from Grip."),
                place = "Scarface Pete's manor, Brimhaven",
                location = GRIP_FIGHT,
                tip = "Grip is only level 22.",
                gives = listOf(Items.PETES_CANDLESTICK to 1),
                fight = QuestFight(Npcs.GRIP, "Grip", 22, GRIP_FIGHT, "Woof! Intruder!"),
                talk = {
                    chatNpc("Scarface Pete's head guard Grip keeps the candlestick.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.STRAVEN,
                journal = listOf("I should give the ${red("candlestick")} to ${red("Straven")}."),
                done = listOf("Straven gave me a master thieves' armband."),
                place = "the Phoenix Gang hideout",
                location = STRAVEN_POST,
                gives = listOf(Items.THIEVES_ARMBAND to 1),
                talk = {
                    if (!player.inventory.contains(Items.PETES_CANDLESTICK)) {
                        chatNpc("Bring me Scarface Pete's candlestick first.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.PETES_CANDLESTICK)
                        chatNpc("Excellent work! Here is your master thieves' armband.", facialExpression = FacialExpression.HAPPY)
                        FoundationRewards.grant(player, Items.THIEVES_ARMBAND, 1)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.GERRANT,
                journal = listOf("I should speak to ${red("Gerrant")} in Port Sarim for a lava eel."),
                done = listOf("Gerrant gave me a lava eel."),
                place = "Port Sarim",
                location = GERRANT_POST,
                gives = listOf(Items.LAVA_EEL to 1),
                talk = {
                    chatNpc("A lava eel for the Heroes' Guild? Cook it carefully:", "here is one, fresh from the lava.", facialExpression = FacialExpression.HAPPY)
                    FoundationRewards.grant(player, Items.LAVA_EEL, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ACHIETTIES,
                journal = listOf("I should bring the ${red("fire feather")}, ${red("master thieves' armband")} and ${red("lava eel")}", "to ${red("Achietties")}."),
                done = listOf("Achietties welcomed me into the Heroes' Guild."),
                place = "outside the Heroes' Guild",
                location = ACHIETTIES_POST,
                talk = {
                    val p = player
                    val things = listOf(Items.FIRE_FEATHER, Items.THIEVES_ARMBAND, Items.LAVA_EEL)
                    if (things.any { !p.inventory.contains(it) }) {
                        chatNpc("You need the fire feather, the master thieves' armband", "and the lava eel.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        things.forEach { p.inventory.remove(it) }
                        chatNpc("Welcome to the Heroes' Guild, hero!", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
        )
}
