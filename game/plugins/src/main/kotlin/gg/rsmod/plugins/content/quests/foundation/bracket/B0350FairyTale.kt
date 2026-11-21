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
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q08: Fairy Tale I - Growing Pains (bracket 3-50). OSRS Wiki: Lost City + Nature Spirit (+ their quests), beat a level 111 Tanglefoot.

private val MARTIN_POST = Tile(3077, 3257, 0)
private val GODFATHER_POST = Tile(2449, 4430, 0)
private val NUFF_POST = Tile(2445, 4430, 0)
private val MALIGNIUS_POST = Tile(3001, 3267, 0)
private val TANGLEFOOT_LAIR = Tile(2456, 4474, 0)

object FairyTaleI : ShortQuest(
    name = "Fairy Tale I - Growing Pains",
    startPoint = "Speak to Martin the Master Gardener in Draynor Village.",
    rewards = "2 Quest Points, 3,500 Farming XP, 2,000 Attack XP, 1,000 Magic XP, Magic secateurs.",
    pointReward = 2,
    questId = Varbits.FAIRY_TALE_I_PROGRESS,
    slot = 42,
    completedValue = 6, // internal: steps.size + 1 (ownSteps)
    usesVarbits = true,
    startNpc = Npcs.MARTIN_THE_MASTER_GARDENER,
    startNpcName = "Martin the Master Gardener",
    startPlace = "Draynor Village",
    startLocation = MARTIN_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs = QuestRequirements(quests = listOf("Lost City", "Nature Spirit"), extra = listOf("Must be able to defeat a level 111 Tanglefoot."))

    override val recommendation = Recommendation.combat(50)

    override val reward =
        QuestReward(
            icon = Items.MAGIC_SECATEURS,
            xp = listOf(Skills.FARMING to 3_500.0, Skills.ATTACK to 2_000.0, Skills.MAGIC to 1_000.0),
            items = listOf(Items.MAGIC_SECATEURS to 1),
            lines = listOf("3,500 Farming XP", "2,000 Attack XP", "1,000 Magic XP", "Magic secateurs", "Access to Fairy Tale II"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Everywhere I look the crops are failing! The fairies say",
            "their queen is ill, and that the sickness flows into",
            "every field. Will you go to Zanaris and help?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll go to Zanaris.", "Not right now.", title = "Start Fairy Tale I?") != 1) return false
        chatPlayer("I'll see what the fairies know.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Speak to the Fairy Godfather in Zanaris.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.FAIRY_GODFATHER_11328,
                journal = listOf("I should speak to the ${red("Fairy Godfather")} in Zanaris."),
                done = listOf("The Fairy Godfather sent me to Fairy Nuff."),
                place = "Zanaris",
                location = GODFATHER_POST,
                talk = {
                    chatNpc("The Fairy Queen is sick and the land suffers with her.", "Speak to Fairy Nuff: she knows what must be done.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.FAIRY_4443,
                journal = listOf("I should speak to ${red("Fairy Nuff")} in Zanaris."),
                done = listOf("Fairy Nuff says the queen needs Magic secateurs;", "Malignius Mortifer can make them."),
                place = "Zanaris",
                location = NUFF_POST,
                talk = {
                    chatNpc("Only a pair of magic secateurs can cut the thorns", "that bind the queen. Malignius Mortifer, south of", "Falador, can enchant a pair.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.MALIGNIUS_MORTIFER,
                journal = listOf("I should take secateurs to ${red("Malignius Mortifer")} south of Falador", "to have them enchanted."),
                done = listOf("Malignius enchanted the secateurs into Magic secateurs."),
                place = "south of Falador",
                location = MALIGNIUS_POST,
                gives = listOf(Items.MAGIC_SECATEURS to 1),
                talk = {
                    if (!player.inventory.contains(Items.SECATEURS)) {
                        chatNpc("Magic secateurs? I can make them - from plain", "secateurs. Bring me a pair.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.SECATEURS)
                        chatNpc("A pinch of fairy dust... done. Do mind the thorns.", facialExpression = FacialExpression.HAPPY)
                        FoundationRewards.grant(player, Items.MAGIC_SECATEURS, 1)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.FAIRY_4443,
                journal = listOf("I must defeat ${red("Tanglefoot")} (level 111) in his lair in Zanaris", "with the Magic secateurs."),
                done = listOf("I cut down Tanglefoot with the Magic secateurs."),
                place = "Zanaris",
                location = TANGLEFOOT_LAIR,
                tip = "In OSRS only the Magic secateurs hurt Tanglefoot. Bring food.",
                fight = QuestFight(Npcs.TANGLEFOOT, "Tanglefoot", 111, TANGLEFOOT_LAIR, "Grrraaarrr!"),
                talk = {
                    chatNpc("Tanglefoot lurks in the thorns. Use the magic secateurs.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.FAIRY_GODFATHER_11328,
                journal = listOf("I should tell the ${red("Fairy Godfather")} that Tanglefoot is gone."),
                done = listOf("The Fairy Godfather thanked me."),
                place = "Zanaris",
                location = GODFATHER_POST,
                talk = {
                    chatNpc("Tanglefoot is gone! The queen can breathe again.", "The fairies thank you - keep the secateurs.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}
