package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q22: The Dig Site (bracket 71-90). OSRS Wiki: 10 Agility, 10 Herblore, 25 Thieving (all boostable); 2 QP, 15,300 Mining XP,
// 2,000 Herblore XP, 2 gold bars. The three exams collapse into one question (the correct answer of the first exam, OSRS Wiki).
// ONTGR not possible: the Digsite excavations, the Varrock Museum cleaning and the pendant reward do not exist on this server.

private val EXAMINER_POST = Tile(3364, 3342, 0)
private val CURATOR_POST = Tile(3257, 3447, 0)
private val EXPERT_POST = Tile(3355, 3333, 0)
private val DOUG_POST = Tile(3351, 9756, 0)

object TheDigSite : ShortQuest(
    name = "The Dig Site",
    startPoint = "Speak to an examiner in the Exam Centre south of the Digsite.",
    rewards = "2 Quest Points, 15,300 Mining XP, 2,000 Herblore XP, 2 gold bars.",
    pointReward = 2,
    questId = Varps.THE_DIG_SITE_PROGRESS,
    slot = 32,
    completedValue = 6, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.EXAMINER,
    startNpcName = "Examiner",
    startPlace = "the Exam Centre",
    startLocation = EXAMINER_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.AGILITY to 10, Skills.HERBLORE to 10, Skills.THIEVING to 25),
            boostable = setOf(Skills.AGILITY, Skills.HERBLORE, Skills.THIEVING),
        )

    override val recommendation = Recommendation.combat(5)

    override val reward =
        QuestReward(
            icon = Items.TROWEL,
            xp = listOf(Skills.MINING to 15_300.0, Skills.HERBLORE to 2_000.0),
            items = listOf(Items.GOLD_BAR to 2),
            lines = listOf("2 Quest Points", "15,300 Mining XP", "2,000 Herblore XP", "2 gold bars"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Would you like to take the Earth Sciences exam? The",
            "Museum only lets the successful dig at the Digsite.",
            "Take this letter to Curator Haig Halen to be stamped.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll take the exam.", "Not right now.", title = "Start The Dig Site?") != 1) return false
        chatPlayer("I'll get the letter stamped.", facialExpression = FacialExpression.CALM_TALK)
        FoundationRewards.grant(player, Items.UNSTAMPED_LETTER, 1)
        chatNpc("The Varrock Museum is east of Varrock. Good luck!", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.CURATOR_HAIG_HALEN,
                journal = listOf("I should take the ${red("unstamped letter")} to ${red("Curator Haig Halen")} in", "the Varrock Museum."),
                done = listOf("The curator stamped my letter."),
                place = "the Varrock Museum",
                location = CURATOR_POST,
                gives = listOf(Items.SEALED_LETTER to 1),
                talk = {
                    if (!player.inventory.contains(Items.UNSTAMPED_LETTER)) {
                        chatNpc("Bring me the examiner's letter and I shall stamp it.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.UNSTAMPED_LETTER)
                        chatNpc("Ah, a student of the Museum! The letter is stamped -", "take it back to the Exam Centre.", facialExpression = FacialExpression.HAPPY)
                        FoundationRewards.grant(player, Items.SEALED_LETTER, 1)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.EXAMINER,
                journal = listOf("I should take the ${red("sealed letter")} back to the ${red("Examiner")} and take", "the exam."),
                done = listOf("I passed the exam and got a trowel."),
                place = "the Exam Centre",
                location = EXAMINER_POST,
                gives = listOf(Items.TROWEL to 1),
                talk = {
                    if (!player.inventory.contains(Items.SEALED_LETTER)) {
                        chatNpc("You need the stamped letter from the curator.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        chatNpc("Question: which people are allowed to use the Digsite?", facialExpression = FacialExpression.CALM_TALK)
                        val answer = options("Anybody who pays.", "All that have passed the appropriate Earth Sciences exam.", "Only the Museum curator.", title = "The exam")
                        if (answer != 2) {
                            chatNpc("I am afraid that is wrong. Study and try again.", facialExpression = FacialExpression.SAD)
                            false
                        } else {
                            player.inventory.remove(Items.SEALED_LETTER)
                            chatNpc("Correct! You pass. Take this trowel - the", "Archaeological expert will show you the way.", facialExpression = FacialExpression.HAPPY)
                            FoundationRewards.grant(player, Items.TROWEL, 1)
                            true
                        }
                    }
                },
            ),
            QuestStep(
                npc = Npcs.ARCHAEOLOGICAL_EXPERT,
                journal = listOf("I should speak to the ${red("Archaeological expert")} in the Exam Centre."),
                done = listOf("The expert sent me down the dig shaft."),
                place = "the Exam Centre",
                location = EXPERT_POST,
                talk = {
                    chatNpc("A trowel and a pass - splendid. Talk to Doug Deeping", "down the shaft; he found something odd.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.DOUG_DEEPING,
                journal = listOf("I should find ${red("Doug Deeping")} in the cave below the winch."),
                done = listOf("Doug gave me a stone tablet."),
                place = "the cave below the Digsite",
                location = DOUG_POST,
                gives = listOf(Items.STONE_TABLET to 1),
                talk = {
                    chatNpc("This stone tablet fell out of the wall. Take it to the", "expert - I have never seen such markings.", facialExpression = FacialExpression.WORRIED)
                    FoundationRewards.grant(player, Items.STONE_TABLET, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ARCHAEOLOGICAL_EXPERT,
                journal = listOf("I should show the ${red("stone tablet")} to the ${red("Archaeological expert")}."),
                done = listOf("The expert identified the tablet: a great find."),
                place = "the Exam Centre",
                location = EXPERT_POST,
                talk = {
                    if (!player.inventory.contains(Items.STONE_TABLET)) {
                        chatNpc("Bring me the tablet from Doug Deeping.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.STONE_TABLET)
                        chatNpc("Remarkable! An ancient Zarosian tablet. The Museum", "will be delighted. Thank you!", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
        )
}
