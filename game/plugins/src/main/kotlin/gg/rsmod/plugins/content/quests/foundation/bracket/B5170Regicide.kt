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

// PROJECT48 Q15: Regicide (bracket 51-70). OSRS Wiki: Underground Pass (+ Biohazard, Plague City), Crafting 10 (not boostable),
// Agility 56 (boostable), "defeat a level 110 Tyras guard"; 3 QP, 13,750 Agility XP, 15,000 coins.
// ONTGR: the Dragon halberd is wielded after the quest (OsrsQuestRequirements.WEAR; 60 Attack and 30 Strength stay the item's own).

private val LATHAS_POST_R = Tile(2575, 3297, 1)
private val TRACKER_POST = Tile(2262, 3151, 0)
private val IORWERTH_POST = Tile(2195, 3254, 0)
private val TYRAS_PATH = Tile(2188, 3152, 0)
private val HINING_POST = Tile(2189, 3148, 0)

object Regicide : ShortQuest(
    name = "Regicide",
    startPoint = "Speak to King Lathas in Ardougne Castle.",
    rewards = "3 Quest Points, 13,750 Agility XP, 15,000 coins, access to Tirannwn, the Dragon halberd.",
    pointReward = 3,
    questId = Varps.REGICIDE_PROGRESS,
    slot = 89,
    completedValue = 7, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.KING_LATHAS,
    startNpcName = "King Lathas",
    startPlace = "Ardougne Castle",
    startLocation = LATHAS_POST_R,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.AGILITY to 56, Skills.CRAFTING to 10),
            quests = listOf("Underground Pass"),
            boostable = setOf(Skills.AGILITY),
            extra = listOf("Must be able to defeat a level 110 Tyras guard."),
        )

    override val recommendation = Recommendation.combat(60)

    override val reward =
        QuestReward(
            icon = Items.DRAGON_HALBERD,
            xp = listOf(Skills.AGILITY to 13_750.0),
            items = listOf(Items.COINS_995 to 15_000),
            lines = listOf("13,750 Agility XP", "15,000 coins", "Access to Tirannwn", "The Dragon halberd can be wielded"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "King Tyras, my brother, lives - and I fear he will be",
            "the death of Ardougne. He must be stopped, in the",
            "land of the elves. Will you go?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll go to Tirannwn.", "Not right now.", title = "Start Regicide?") != 1) return false
        chatPlayer("I will find King Tyras.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Look for an elf tracker in the Isafdar forest.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.ELF_TRACKER,
                journal = listOf("I should find an ${red("elf tracker")} in the Isafdar forest."),
                done = listOf("The elf tracker pointed me to Lord Iorwerth's camp."),
                place = "Isafdar",
                location = TRACKER_POST,
                talk = {
                    chatNpc("The human king? Lord Iorwerth will know. His camp", "lies in the forest to the north.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.LORD_IORWERTH,
                journal = listOf("I should speak to ${red("Lord Iorwerth")} at the elf camp."),
                done = listOf("Lord Iorwerth gave me the Big book of bangs."),
                place = "Lord Iorwerth's camp",
                location = IORWERTH_POST,
                gives = listOf(Items.BIG_BOOK_OF_BANGS to 1),
                talk = {
                    chatNpc("The tyrant Tyras is camped to the west. Take this", "book: it explains how to build the weapon you need.", facialExpression = FacialExpression.CALM_TALK)
                    FoundationRewards.grant(player, Items.BIG_BOOK_OF_BANGS, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.LORD_IORWERTH,
                journal = listOf("I must defeat a ${red("Tyras guard")} (level 110) on the path to Tyras", "Camp."),
                done = listOf("I fought through the Tyras guards."),
                place = "the path to Tyras Camp",
                location = TYRAS_PATH,
                tip = "Tyras guards hit hard. Bring food.",
                fight = QuestFight(Npcs.TYRAS_GUARD, "Tyras guard", 110, TYRAS_PATH, "Halt, intruder!"),
                talk = {
                    chatNpc("The path to Tyras Camp is watched by his guards.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GENERAL_HINING,
                journal = listOf("I should speak to ${red("General Hining")} at Tyras Camp and fire", "the catapult at King Tyras."),
                done = listOf("The catapult shot hit King Tyras: he is dead."),
                place = "Tyras Camp",
                location = HINING_POST,
                talk = {
                    chatNpc(
                        "A distraction? Splendid! While I hold the guards,",
                        "load the catapult and loose it at the king's tent.",
                        facialExpression = FacialExpression.HAPPY,
                    )
                    chatNpc("Direct hit - King Tyras is dead!", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.LORD_IORWERTH,
                journal = listOf("I should return to ${red("Lord Iorwerth")} for the message for King", "Lathas."),
                done = listOf("Lord Iorwerth gave me a message for King Lathas."),
                place = "Lord Iorwerth's camp",
                location = IORWERTH_POST,
                gives = listOf(Items.MESSAGE to 1),
                talk = {
                    chatNpc("Tyras is dead. Take this message to King Lathas.", facialExpression = FacialExpression.CALM_TALK)
                    FoundationRewards.grant(player, Items.MESSAGE, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KING_LATHAS,
                journal = listOf("I should deliver the message to ${red("King Lathas")} in Ardougne", "Castle."),
                done = listOf("King Lathas read the message; Arianwyn revealed his lie."),
                place = "Ardougne Castle",
                location = LATHAS_POST_R,
                talk = {
                    if (!player.inventory.contains(Items.MESSAGE)) {
                        chatNpc("You have a message for me?", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.MESSAGE)
                        chatNpc("Tyras is dead... Arianwyn tells me the truth is", "different from what I said. Ardougne owes you much.", facialExpression = FacialExpression.WORRIED)
                        true
                    }
                },
            ),
        )
}
