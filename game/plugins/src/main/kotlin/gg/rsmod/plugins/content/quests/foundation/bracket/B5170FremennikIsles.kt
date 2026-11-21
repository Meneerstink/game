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

// PROJECT48 Q11: The Fremennik Isles (bracket 51-70). OSRS Wiki: The Fremennik Trials, Construction 20 (boostable).
// ONTGR: Helm of Neitiznot is worn after the quest (OsrsQuestRequirements.WEAR) and bought from Mawnis for 50,000 coins.

private val MORD_POST = Tile(2644, 3709, 0)
private val GJUKI_POST = Tile(2407, 3802, 0)
private val MAWNIS_POST = Tile(2335, 3799, 0)
private val TROLL_CAVE = Tile(2362, 3846, 0)
private val TROLL_KING_LAIR = Tile(2366, 3856, 0)

object FremennikIsles : ShortQuest(
    name = "The Fremennik Isles",
    startPoint = "Speak to Mord Gunnars at the northernmost dock in Rellekka.",
    rewards = "1 Quest Point, 5,000 Construction and Crafting XP, 10,000 Woodcutting XP, two lamps of 10,000 combat XP, the Helm of Neitiznot.",
    pointReward = 1,
    questId = Varbits.THE_FREMENNIK_ISLES_PROGRESS,
    slot = 118,
    completedValue = 6, // internal: steps.size + 1 (ownSteps)
    usesVarbits = true,
    startNpc = Npcs.MORD_GUNNARS,
    startNpcName = "Mord Gunnars",
    startPlace = "the northern docks of Rellekka",
    startLocation = MORD_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.CONSTRUCTION to 20),
            quests = listOf("The Fremennik Trials"),
            boostable = setOf(Skills.CONSTRUCTION),
            extra = listOf("Must be able to defeat a level 122 Ice Troll King."),
        )

    override val recommendation = Recommendation.combat(50)

    override val reward =
        QuestReward(
            icon = Items.HELM_OF_NEITIZNOT,
            xp = listOf(Skills.CONSTRUCTION to 5_000.0, Skills.CRAFTING to 5_000.0, Skills.WOODCUTTING to 10_000.0),
            lamps =
                listOf(
                    QuestLamp(10_000, listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION), 1, "Fremennik Isles combat"),
                    QuestLamp(10_000, listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION), 1, "Fremennik Isles combat"),
                ),
            items = listOf(Items.HELM_OF_NEITIZNOT to 1),
            lines = listOf("5,000 Construction and Crafting XP", "10,000 Woodcutting XP", "Two lamps of 10,000 combat XP", "The Helm of Neitiznot", "Access to the isles of Neitiznot and Jatizso"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "The isles of Neitiznot and Jatizso have been at odds",
            "since the trolls came down from the north. Speak to",
            "King Gjuki on Jatizso - he needs a hero.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll go to Jatizso.", "Not right now.", title = "Start The Fremennik Isles?") != 1) return false
        chatPlayer("I'll see what I can do.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.KING_GJUKI_SORVOTT_IV,
                journal = listOf("I should speak to ${red("King Gjuki Sorvott IV")} on Jatizso."),
                done = listOf("King Gjuki sent me to Mawnis on Neitiznot."),
                place = "Jatizso",
                location = GJUKI_POST,
                talk = {
                    chatNpc("The trolls threaten both our isles. Go to Mawnis", "Burowgar on Neitiznot - together we will stop them.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.MAWNIS_BUROWGAR_5503,
                journal = listOf("I should speak to ${red("Mawnis Burowgar")} on Neitiznot."),
                done = listOf("Mawnis asked me to drive back the ice trolls."),
                place = "Neitiznot",
                location = MAWNIS_POST,
                talk = {
                    chatNpc("The ice trolls grow bolder by the day. Clear their", "cave north of the village, then face their king.", facialExpression = FacialExpression.ANGRY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.MAWNIS_BUROWGAR_5503,
                journal = listOf("I must defeat the ${red("ice trolls")} (levels 74-82) in the troll cave."),
                done = listOf("I drove the ice trolls out of their cave."),
                place = "the troll cave north of Neitiznot",
                location = TROLL_CAVE,
                tip = "The trolls come one after another. Bring food.",
                fight =
                    QuestFight(
                        Npcs.ICE_TROLL_RUNT, "Ice troll runt", 74, TROLL_CAVE, "Grrrrr!",
                        chain =
                            listOf(
                                Npcs.ICE_TROLL_MALE to "Rrrraaargh!",
                                Npcs.ICE_TROLL_FEMALE to "Smash!",
                                Npcs.ICE_TROLL_GRUNT to "Me crush you!",
                            ),
                    ),
                talk = {
                    chatNpc("The troll cave lies north of the village. Fight them", "one by one.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.MAWNIS_BUROWGAR_5503,
                journal = listOf("I must defeat the ${red("Ice Troll King")} (level 122) and take his head."),
                done = listOf("I defeated the Ice Troll King and took his head."),
                place = "the Ice Troll King's lair",
                location = TROLL_KING_LAIR,
                gives = listOf(Items.DECAPITATED_HEAD to 1),
                tip = "The Ice Troll King hits very hard. Bring plenty of food.",
                fight = QuestFight(Npcs.ICE_TROLL_KING, "Ice Troll King", 122, TROLL_KING_LAIR, "You dare enter my lair?"),
                talk = {
                    chatNpc("The Ice Troll King lives deeper in the cave.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.MAWNIS_BUROWGAR_5503,
                journal = listOf("I should bring the ${red("decapitated head")} to ${red("Mawnis Burowgar")}."),
                done = listOf("Mawnis took the troll king's head and gave me the Helm", "of Neitiznot."),
                place = "Neitiznot",
                location = MAWNIS_POST,
                talk = {
                    if (!player.inventory.contains(Items.DECAPITATED_HEAD)) {
                        chatNpc("Bring me proof: the Ice Troll King's head.", facialExpression = FacialExpression.CALM_TALK)
                        if (FoundationRewards.waitingInBank(player, listOf(Items.DECAPITATED_HEAD))) chatNpc(FoundationRewards.IN_BANK_LINE, facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.DECAPITATED_HEAD)
                        chatNpc("The troll king is dead! Neitiznot is in your debt.", "Take this helm of our people.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
        )
}
