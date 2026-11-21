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
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q12: Plague City (bracket 51-70). OSRS Wiki: no requirements; 1 QP, 2,425 Mining XP.
// ONTGR: the Ardougne Teleport spell is cast only after this quest (magic\teleports\teleport_spells.plugin.kts).

private val EDMOND_POST = Tile(2571, 3333, 0)
private val ALRENA_POST = Tile(2573, 3333, 0)
private val BRAVEK_POST = Tile(2536, 3314, 1)
private val PLAGUE_HOUSE = Tile(2541, 9672, 0)

object PlagueCity : ShortQuest(
    name = "Plague City",
    startPoint = "Speak to Edmond in the north-west house of East Ardougne.",
    rewards = "1 Quest Point, 2,425 Mining XP, the Ardougne Teleport spell.",
    pointReward = 1,
    questId = Varps.PLAGUE_CITY_PROGRESS,
    slot = 83,
    completedValue = 5, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.EDMOND,
    startNpcName = "Edmond",
    startPlace = "East Ardougne",
    startLocation = EDMOND_POST,
) {
    override val ownSteps: Boolean get() = true

    override val reward =
        QuestReward(
            icon = Items.GAS_MASK,
            xp = listOf(Skills.MINING to 2_425.0),
            lines = listOf("2,425 Mining XP", "The Ardougne Teleport spell"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "My daughter Elena went to West Ardougne to find a",
            "cure for the plague, and has not come back. The",
            "wall is closed. Will you find her?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll find Elena.", "Not right now.", title = "Start Plague City?") != 1) return false
        chatPlayer("I'll bring her home.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Speak to my wife Alrena - she has a gas mask for you.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.ALRENA,
                journal = listOf("I should speak to ${red("Alrena")}, Edmond's wife, in the same house."),
                done = listOf("Alrena gave me a gas mask."),
                place = "East Ardougne",
                location = ALRENA_POST,
                gives = listOf(Items.GAS_MASK to 1),
                talk = {
                    chatNpc("Elena is in West Ardougne. Take this gas mask - the", "air there is thick with plague.", facialExpression = FacialExpression.WORRIED)
                    FoundationRewards.grant(player, Items.GAS_MASK, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BRAVEK,
                journal = listOf("I should speak to ${red("Bravek")}, the city warden of West", "Ardougne, for a warrant to search the plague house."),
                done = listOf("Bravek gave me a warrant."),
                place = "West Ardougne",
                location = BRAVEK_POST,
                gives = listOf(Items.WARRANT to 1),
                talk = {
                    chatNpc("A warrant? ...Very well, if it means you stop pestering", "me. Take it.", facialExpression = FacialExpression.ANGRY)
                    FoundationRewards.grant(player, Items.WARRANT, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ELENA_715,
                journal = listOf("I should show the ${red("warrant")} to the mourners and free ${red("Elena")}", "in the plague house."),
                done = listOf("I freed Elena from the plague house."),
                place = "the plague house in West Ardougne",
                location = PLAGUE_HOUSE,
                talk = {
                    if (!player.inventory.contains(Items.WARRANT)) {
                        chatNpc("The mourners will not let you in without a warrant.", facialExpression = FacialExpression.WORRIED)
                        false
                    } else {
                        player.inventory.remove(Items.WARRANT)
                        chatNpc("You came for me! Thank you - tell my father I am", "safe. I will follow you out.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.EDMOND,
                journal = listOf("I should tell ${red("Edmond")} that Elena is safe."),
                done = listOf("Edmond thanked me for finding Elena."),
                place = "East Ardougne",
                location = EDMOND_POST,
                talk = {
                    chatNpc("Elena is safe! I cannot thank you enough. Please", "accept this - and the Ardougne Teleport spell.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}
