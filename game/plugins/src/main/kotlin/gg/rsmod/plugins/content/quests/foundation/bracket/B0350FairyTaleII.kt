package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Varbits
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.QuestFight
import gg.rsmod.plugins.content.quests.foundation.QuestLamp
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q09: Fairy Tale II - Cure a Queen (bracket 3-50). OSRS Wiki: Fairy Tale I, Thieving 40 (not boostable),
// Farming 49 and Herblore 57 (boostable). ONTGR: the fairy rings open from step 2 (FairyRings.ringsUnlocked).

private val MARTIN_POST2 = Tile(3077, 3257, 0)
private val GODFATHER_POST2 = Tile(2449, 4430, 0)
private val VERY_WISE_POST = Tile(2451, 4425, 0)
private val GORAK_PLANE = Tile(3036, 5346, 0)
private val COSMIC_PLANE = Tile(2162, 4833, 0)

object FairyTaleII : ShortQuest(
    name = "Fairy Tale II - Cure a Queen",
    startPoint = "Speak to Martin the Master Gardener in Draynor Village.",
    rewards = "2 Quest Points, 3,500 Herblore XP, 2,500 Thieving XP, an antique lamp (2,500 XP), access to the fairy ring network.",
    pointReward = 2,
    questId = Varbits.FAIRY_TALE_II_PROGRESS,
    slot = 43,
    completedValue = 7, // internal: steps.size + 1 (ownSteps)
    usesVarbits = true,
    startNpc = Npcs.MARTIN_THE_MASTER_GARDENER,
    startNpcName = "Martin the Master Gardener",
    startPlace = "Draynor Village",
    startLocation = MARTIN_POST2,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.THIEVING to 40, Skills.FARMING to 49, Skills.HERBLORE to 57),
            quests = listOf("Fairy Tale I - Growing Pains"),
            boostable = setOf(Skills.FARMING, Skills.HERBLORE),
        )

    override val recommendation = Recommendation.combat(55)

    override val reward =
        QuestReward(
            icon = Items.DRAMEN_STAFF,
            xp = listOf(Skills.HERBLORE to 3_500.0, Skills.THIEVING to 2_500.0),
            lamps = listOf(QuestLamp(2_500, FoundationRewards.ALL_SKILLS, 30, "Fairy Tale II")),
            lines = listOf("3,500 Herblore XP", "2,500 Thieving XP", "An antique lamp (2,500 XP in any skill over level 30)", "Access to the fairy ring network"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "The Fairy Queen is not only ill - she has been poisoned!",
            "The fairies need someone who can brew a cure and",
            "who is not afraid of Gorak. Will you help?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll cure the queen.", "Not right now.", title = "Start Fairy Tale II?") != 1) return false
        chatPlayer("Count me in.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Go to Zanaris and see the Fairy Godfather.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.FAIRY_GODFATHER,
                journal = listOf("I should speak to the ${red("Fairy Godfather")} in Zanaris."),
                done = listOf("The Fairy Godfather opened the fairy rings for me."),
                place = "Zanaris",
                location = GODFATHER_POST2,
                talk = {
                    chatNpc(
                        "You are a friend of the fairies. From now on the fairy",
                        "rings answer your call - with a dramen or lunar staff.",
                        "Go to the Fairy Very Wise in the hideout.",
                        facialExpression = FacialExpression.HAPPY,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.FAIRY_VERY_WISE,
                journal = listOf("I should speak to the ${red("Fairy Very Wise")} in the fairy resistance", "hideout about the queen's poison."),
                done = listOf("The Fairy Very Wise needs a Gorak claw and a star flower."),
                place = "the fairy hideout",
                location = VERY_WISE_POST,
                talk = {
                    chatNpc(
                        "The queen was poisoned by Gorak's claws. To cure her I",
                        "need a Gorak claw and a star flower from the Cosmic",
                        "Being. Bring me both.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.FAIRY_VERY_WISE,
                journal = listOf("I must defeat ${red("Gorak")} (level 145) on the Gorak Plane and take", "his claw."),
                done = listOf("I took a Gorak claw from Gorak."),
                place = "the Gorak Plane",
                location = GORAK_PLANE,
                gives = listOf(Items.GORAK_CLAWS to 1),
                tip = "Gorak is a strong melee fighter. Bring food and prayer.",
                fight = QuestFight(Npcs.GORAK, "Gorak", 145, GORAK_PLANE, "Rarrrgh!"),
                talk = {
                    chatNpc("Gorak lives on the Gorak Plane, east of the fairy ring", "d-l-q. Take his claw.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.COSMIC_BEING,
                journal = listOf("I should speak to the ${red("Cosmic Being")} on the Cosmic Entity's plane", "for a star flower."),
                done = listOf("The Cosmic Being gave me a star flower."),
                place = "the Cosmic Entity's plane",
                location = COSMIC_PLANE,
                gives = listOf(Items.STAR_FLOWER to 1),
                talk = {
                    chatNpc("A star flower, for a sick queen? Take it, mortal.", facialExpression = FacialExpression.CALM_TALK)
                    FoundationRewards.grant(player, Items.STAR_FLOWER, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.FAIRY_VERY_WISE,
                journal = listOf("I should bring the Gorak claw and the star flower to the", "${red("Fairy Very Wise")}."),
                done = listOf("The Fairy Very Wise brewed the cure."),
                place = "the fairy hideout",
                location = VERY_WISE_POST,
                talk = {
                    if (!player.inventory.contains(Items.GORAK_CLAWS) || !player.inventory.contains(Items.STAR_FLOWER)) {
                        chatNpc("I need both a Gorak claw and a star flower.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.GORAK_CLAWS)
                        player.inventory.remove(Items.STAR_FLOWER)
                        chatNpc("Splendid! The cure is brewed. Take it to the queen.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.FAIRY_QUEEN_4437,
                journal = listOf("I should give the cure to the ${red("Fairy Queen")}."),
                done = listOf("The Fairy Queen woke - cured."),
                place = "the fairy hideout",
                location = VERY_WISE_POST,
                talk = {
                    chatNpc("I... I feel myself again. You have saved Zanaris.", "The fairy rings are yours to use.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}
