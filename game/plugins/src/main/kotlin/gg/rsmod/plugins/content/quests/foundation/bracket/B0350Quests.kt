package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.QuestFight
import gg.rsmod.plugins.content.quests.foundation.QuestLamp
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

/** PROJECT48: the mini-quests of combat bracket 3-50 (Q01-Q09), in the order of VOORTGANG.tsv. */
object B0350Quests {
    val ALL: List<ShortQuest> = listOf(ErnestTheChicken)

    /** Veronica and Professor Oddenstein stand in the world already (spawns_12340) but have no Talk-to plugin: only bound here. */
    val NPCS: List<BracketNpc> =
        listOf(
            BracketNpc(Npcs.VERONICA, null, gg.rsmod.game.model.Direction.NORTH_WEST, listOf("I do hope Ernest is all right in there.")),
            BracketNpc(Npcs.PROFESSOR_ODDENSTEIN, null, gg.rsmod.game.model.Direction.EAST, listOf("Be careful what you touch in here,", "young one. Some of it bites.")),
        )
}

private val VERONICA_POST = Tile(3110, 3330, 0)
private val ODDENSTEIN_POST = Tile(3111, 3367, 2)
private val MANOR_GROUND = Tile(3112, 3368, 0)

// ------------------------------------------------------------------------------------------ Q01 Ernest the Chicken

object ErnestTheChicken : ShortQuest(
    name = "Ernest the Chicken",
    startPoint = "Speak to Veronica south of Draynor Manor.",
    rewards = "4 Quest Points, 300 coins, an antique lamp (500 XP in a skill of your choice).",
    pointReward = 4,
    questId = Varps.ERNEST_THE_CHICKEN_PROGRESS,
    slot = 5,
    completedValue = 4, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.VERONICA,
    startNpcName = "Veronica",
    startPlace = "south of Draynor Manor",
    startLocation = VERONICA_POST,
) {
    override val ownSteps: Boolean get() = true

    // OSRS Wiki: "The ability to defend from a level 22 Skeleton."
    override val recommendation = Recommendation.combat(10)

    override val reward =
        QuestReward(
            icon = Items.RUBBER_TUBE,
            lamps = listOf(QuestLamp(500, FoundationRewards.ALL_SKILLS, 1, "Ernest the Chicken")),
            items = listOf(Items.COINS_995 to 300),
            lines = listOf("300 coins", "An antique lamp (500 XP in any skill)"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Oh, I'm so worried! My fiance Ernest went into the",
            "manor to ask for directions, and that was an hour",
            "ago. He has not come out again.",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll find Ernest for you.", "Not right now.", title = "Start Ernest the Chicken?") != 1) return false
        chatPlayer("I'll go in and find Ernest for you.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Oh, thank you! Be careful in there - the manor is", "full of the undead.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.PROFESSOR_ODDENSTEIN,
                journal = listOf("I should climb to the top floor of ${red("Draynor Manor")}", "and speak to ${red("Professor Oddenstein")}."),
                done = listOf("Professor Oddenstein turned Ernest into a chicken -", "his machine needs a rubber tube."),
                place = "Draynor Manor's top floor",
                location = ODDENSTEIN_POST,
                talk = {
                    chatNpc("Aha, a visitor! Your friend Ernest is the chicken", "over there. My machine went wrong.", facialExpression = FacialExpression.LAUGH_EXCITED)
                    chatNpc(
                        "To turn him back I need a new rubber tube. A",
                        "skeleton downstairs has been chewing on mine.",
                        "Fetch it and I shall put him right.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.PROFESSOR_ODDENSTEIN,
                journal = listOf("I must defeat the ${red("skeleton")} (level 22) on the ground", "floor of Draynor Manor and take back the rubber tube."),
                done = listOf("I beat the skeleton and took the rubber tube."),
                place = "Draynor Manor's ground floor",
                location = MANOR_GROUND,
                gives = listOf(Items.RUBBER_TUBE to 1),
                tip = "A level 22 skeleton: bring food and fight it in the open.",
                fight = QuestFight(Npcs.SKELETON, "skeleton", 22, MANOR_GROUND, "Rattle... rattle..."),
                talk = {
                    chatNpc("It is in the room below. Do hurry!", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.PROFESSOR_ODDENSTEIN,
                journal = listOf("I should bring the rubber tube to ${red("Professor Oddenstein")}", "on the top floor of Draynor Manor."),
                done = listOf("Oddenstein fixed his machine and Ernest is a man again."),
                place = "Draynor Manor's top floor",
                location = ODDENSTEIN_POST,
                talk = {
                    if (!player.inventory.contains(Items.RUBBER_TUBE)) {
                        chatNpc("Ernest is still a chicken. Where is that tube?", facialExpression = FacialExpression.ANGRY)
                        if (FoundationRewards.waitingInBank(player, listOf(Items.RUBBER_TUBE))) chatNpc(FoundationRewards.IN_BANK_LINE, facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.RUBBER_TUBE)
                        chatNpc("Splendid! The machine hums again. Stand back...", facialExpression = FacialExpression.LAUGH_EXCITED)
                        chatNpc("There! Ernest is himself again. Veronica will be", "pleased - and so am I.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
        )
}
