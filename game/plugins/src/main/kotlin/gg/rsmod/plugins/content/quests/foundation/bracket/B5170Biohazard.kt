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
import gg.rsmod.plugins.content.quests.foundation.QuestFight
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q13: Biohazard (bracket 51-70). OSRS Wiki: Plague City; 3 QP; 1,250 Thieving XP; free passage through the West Ardougne gate.

private val ELENA_HOUSE = Tile(2592, 3336, 0)
private val OMART_POST = Tile(2559, 3266, 0)
private val MOURNER_HQ = Tile(2543, 3328, 0)
private val GUIDOR_POST = Tile(3283, 3381, 0)
private val LATHAS_POST = Tile(2575, 3297, 1)

object Biohazard : ShortQuest(
    name = "Biohazard",
    startPoint = "Speak to Elena in East Ardougne.",
    rewards = "3 Quest Points, 1,250 Thieving XP, free passage through the West Ardougne gate.",
    pointReward = 3,
    questId = Varps.BIOHAZARD_PROGRESS,
    slot = 21,
    completedValue = 6, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.ELENA,
    startNpcName = "Elena",
    startPlace = "East Ardougne",
    startLocation = ELENA_HOUSE,
) {
    override val ownSteps: Boolean get() = true

    override val needs = QuestRequirements(quests = listOf("Plague City"))

    override val recommendation = Recommendation.combat(10)

    override val reward =
        QuestReward(
            icon = Items.PLAGUE_SAMPLE,
            xp = listOf(Skills.THIEVING to 1_250.0),
            lines = listOf("1,250 Thieving XP", "Free passage through the West Ardougne gate", "Use of the Combat Training Camp"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Thank you for freeing me before - but the",
            "plague is not natural. The Mourners are hiding",
            "something. Will you help me find out what?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll help you, Elena.", "Not right now.", title = "Start Biohazard?") != 1) return false
        chatPlayer("Tell me what you need.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Go to West Ardougne and find Omart, near the wall.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.OMART,
                journal = listOf("I should speak to ${red("Omart")} near the wall of West Ardougne."),
                done = listOf("Omart told me about the Mourners' headquarters."),
                place = "West Ardougne",
                location = OMART_POST,
                talk = {
                    chatNpc("Mourners run the city, and their headquarters lie", "to the east. Take a distillator from one of them.", facialExpression = FacialExpression.SECRETLY_TALKING)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.OMART,
                journal = listOf("I must defeat a ${red("Mourner")} (level 11) at the Mourner headquarters", "and take the distillator."),
                done = listOf("I defeated a Mourner and took his distillator."),
                place = "the Mourner headquarters",
                location = MOURNER_HQ,
                gives = listOf(Items.DISTILLATOR to 1),
                fight = QuestFight(Npcs.MOURNER, "Mourner", 11, MOURNER_HQ, "You should not be here!"),
                talk = {
                    chatNpc("The Mourners' headquarters is to the east of here.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ELENA,
                journal = listOf("I should take the distillator to ${red("Elena")} in East Ardougne."),
                done = listOf("Elena gave me a plague sample and touch paper."),
                place = "East Ardougne",
                location = ELENA_HOUSE,
                gives = listOf(Items.PLAGUE_SAMPLE to 1, Items.TOUCH_PAPER to 1),
                talk = {
                    if (!player.inventory.contains(Items.DISTILLATOR)) {
                        chatNpc("I need the Mourners' distillator.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.DISTILLATOR)
                        chatNpc("This will let me analyse the plague. Take the", "sample and this touch paper to Guidor in Varrock.", facialExpression = FacialExpression.HAPPY)
                        FoundationRewards.grant(player, Items.PLAGUE_SAMPLE, 1)
                        FoundationRewards.grant(player, Items.TOUCH_PAPER, 1)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.GUIDOR,
                journal = listOf("I should take the plague sample and touch paper to ${red("Guidor")}", "in south-east Varrock."),
                done = listOf("Guidor found that the plague is not real."),
                place = "south-east Varrock",
                location = GUIDOR_POST,
                talk = {
                    if (!player.inventory.contains(Items.PLAGUE_SAMPLE) || !player.inventory.contains(Items.TOUCH_PAPER)) {
                        chatNpc("I need both the plague sample and the touch paper.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.PLAGUE_SAMPLE)
                        player.inventory.remove(Items.TOUCH_PAPER)
                        chatNpc("This plague is a fake! There is nothing here to", "harm a soul. Tell King Lathas at once.", facialExpression = FacialExpression.ANGRY)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.KING_LATHAS,
                journal = listOf("I should tell ${red("King Lathas")} in Ardougne Castle about Guidor's", "findings."),
                done = listOf("King Lathas will open the gate to West Ardougne."),
                place = "Ardougne Castle",
                location = LATHAS_POST,
                talk = {
                    chatNpc("A false plague? Then the wall must come down. West", "Ardougne is free to you - the gate is open.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}
