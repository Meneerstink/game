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
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestSearch
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q21: The Eyes of Glouphrie (bracket 71-90). OSRS Wiki: The Grand Tree, 5 Construction and 46 Magic (neither boostable);
// 2 QP, 12,000 Magic XP, 2,500 Woodcutting XP, 6,000 Runecraft XP, 250 Construction XP, the crystal saw.
// The evil creatures have no combat definition on this server (cache npc 4615 level 0, no stats) = a search step (brief 7b/7d); noted.
// ONTGR: Brimstail hands out a lost crystal saw again after the quest (OSRS Wiki).

private val BRIMSTAIL_POST = Tile(2410, 9819, 0)
private val HAZELMERE_POST = Tile(2677, 3088, 1)
private val STRONGHOLD_HUNT = Tile(2460, 3444, 0)

object TheEyesOfGlouphrie : ShortQuest(
    name = "The Eyes of Glouphrie",
    startPoint = "Speak to Brimstail in his cave in the south-west of the Tree Gnome Stronghold.",
    rewards = "2 Quest Points, 12,000 Magic XP, 2,500 Woodcutting XP, 6,000 Runecraft XP, 250 Construction XP, a crystal saw.",
    pointReward = 2,
    questId = Varbits.THE_EYES_OF_GLOUPHRIE_PROGRESS,
    slot = 41,
    completedValue = 5, // internal: steps.size + 1 (ownSteps)
    usesVarbits = true,
    startNpc = Npcs.BRIMSTAIL,
    startNpcName = "Brimstail",
    startPlace = "the Tree Gnome Stronghold",
    startLocation = BRIMSTAIL_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.CONSTRUCTION to 5, Skills.MAGIC to 46),
            quests = listOf("The Grand Tree"),
        )

    override val recommendation = Recommendation.combat(30)

    override val reward =
        QuestReward(
            icon = Items.CRYSTAL_SAW,
            xp = listOf(Skills.MAGIC to 12_000.0, Skills.WOODCUTTING to 2_500.0, Skills.RUNECRAFTING to 6_000.0, Skills.CONSTRUCTION to 250.0),
            items = listOf(Items.CRYSTAL_SAW to 1),
            lines = listOf("2 Quest Points", "12,000 Magic XP", "2,500 Woodcutting XP", "6,000 Runecraft XP", "250 Construction XP", "A crystal saw"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Glouphrie's eyes were stolen long ago, and now a",
            "strange illusion hides something in the Stronghold. I",
            "need help to understand it. Will you go to Hazelmere?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll help.", "Not right now.", title = "Start The Eyes of Glouphrie?") != 1) return false
        chatPlayer("I'll ask Hazelmere about the illusion.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("He lives on the island east of Yanille. Thank you!", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.HAZELMERE,
                journal = listOf("I should ask ${red("Hazelmere")}, on his island east of Yanille, about", "the illusion."),
                done = listOf("Hazelmere explained the illusion to me."),
                place = "Hazelmere's island, east of Yanille",
                location = HAZELMERE_POST,
                talk = {
                    chatNpc("Glouphrie's eyes... an illusion is hiding evil creatures in", "the Stronghold. Tell Brimstail to build his machine.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BRIMSTAIL,
                journal = listOf("I should tell ${red("Brimstail")} what Hazelmere said."),
                done = listOf("Brimstail built the machine; the 'cute creature' was a spy."),
                place = "Brimstail's cave",
                location = BRIMSTAIL_POST,
                talk = {
                    chatNpc("The machine is built - and look, that cute creature was a", "spy! Evil creatures are loose in the Stronghold. Hunt", "them down!", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BRIMSTAIL,
                journal = listOf("I must hunt down the ${red("evil creatures")} in the ${red("Tree Gnome Stronghold")}."),
                done = listOf("I hunted down the evil creatures."),
                place = "the Tree Gnome Stronghold",
                location = STRONGHOLD_HUNT,
                tip = "The evil creatures have 1 hitpoint each: walk to the marked spot in the Stronghold to deal with them.",
                search = QuestSearch(STRONGHOLD_HUNT, "You hunt down the evil creatures one by one - each falls with a single blow.", 3),
                talk = {
                    chatNpc("The creatures are somewhere in the Stronghold. Find", "and crush them!", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BRIMSTAIL,
                journal = listOf("I should return to ${red("Brimstail")} for my reward."),
                done = listOf("Brimstail thanked me and gave me a crystal saw."),
                place = "Brimstail's cave",
                location = BRIMSTAIL_POST,
                talk = {
                    chatNpc("The illusion is gone and the Stronghold is safe. Take", "this crystal saw - it makes carpentry easier.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}

/**
 * ONTGR (The Eyes of Glouphrie): after the quest Brimstail hands out the crystal saw again when it is gone (OSRS Wiki).
 * True when he spoke.
 */
suspend fun QueueTask.crystalSawReclaim(): Boolean {
    if (!gg.rsmod.plugins.content.quests.QuestStatus.isCompleted(player, "The Eyes of Glouphrie")) return false
    if (options("I lost my crystal saw.", "Nothing, thanks.", title = "Brimstail") != 1) return true
    if (player.inventory.contains(Items.CRYSTAL_SAW) || player.equipment.contains(Items.CRYSTAL_SAW)) {
        chatNpc("You already carry one.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }
    FoundationRewards.grant(player, Items.CRYSTAL_SAW, 1)
    chatNpc("Here is another one. Do not lose it again.", facialExpression = FacialExpression.HAPPY)
    return true
}
