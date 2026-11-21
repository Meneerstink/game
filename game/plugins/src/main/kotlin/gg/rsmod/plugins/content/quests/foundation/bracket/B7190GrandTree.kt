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

// PROJECT48 Q18: The Grand Tree (bracket 71-90). OSRS Wiki: 25 Agility (boostable), a level 172 Black demon; 5 QP, 18,400 Attack XP,
// 7,900 Agility XP, 2,150 Magic XP. ONTGR: the gnome gliders (mechanics\travel\gnome_glider.plugin.kts) and the Gnome Stronghold rocks
// shortcut (skills\agility\sourced_shortcuts.plugin.kts, with Tree Gnome Village). The mine under the Grand Tree does not exist here.
// King Narnode Shareen already has a Talk-to plugin (grandexchange_unlocks.plugin.kts) that calls FoundationQuests.talk first.

private val NARNODE_POST = Tile(2466, 3497, 0)
private val HAZELMERE_POST = Tile(2677, 3088, 1)
private val CHARLIE_POST = Tile(2464, 3494, 3)
private val FOREMAN_POST = Tile(2999, 3043, 0)
private val DEMON_FIGHT = Tile(2485, 9864, 0)

object TheGrandTree : ShortQuest(
    name = "The Grand Tree",
    startPoint = "Speak to King Narnode Shareen in the Grand Tree.",
    rewards = "5 Quest Points, 18,400 Attack XP, 7,900 Agility XP, 2,150 Magic XP, the gnome gliders and the Stronghold rocks.",
    pointReward = 5,
    questId = Varps.THE_GRAND_TREE_PROGRESS,
    slot = 56,
    completedValue = 7, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.KING_NARNODE_SHAREEN,
    startNpcName = "King Narnode Shareen",
    startPlace = "the Grand Tree",
    startLocation = NARNODE_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.AGILITY to 25),
            boostable = setOf(Skills.AGILITY),
            extra = listOf("Must be able to defeat a level 172 Black demon (it can be safespotted)."),
        )

    override val recommendation = Recommendation.combat(70)

    override val reward =
        QuestReward(
            icon = Items.DACONIA_ROCK,
            xp = listOf(Skills.ATTACK to 18_400.0, Skills.AGILITY to 7_900.0, Skills.MAGIC to 2_150.0),
            lines = listOf("5 Quest Points", "18,400 Attack XP", "7,900 Agility XP", "2,150 Magic XP", "Use of the gnome gliders", "The Stronghold rocks shortcut"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "The Grand Tree is dying, and I do not know why. Take",
            "this bark sample to Hazelmere, the hermit on the island",
            "east of Yanille - he may know what ails it.",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll help.", "Not right now.", title = "Start The Grand Tree?") != 1) return false
        chatPlayer("I'll find out what is wrong.", facialExpression = FacialExpression.CALM_TALK)
        FoundationRewards.grant(player, Items.BARK_SAMPLE, 1)
        chatNpc("Thank you. Here is the sample.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.HAZELMERE,
                journal = listOf("I should take the ${red("bark sample")} to ${red("Hazelmere")}, on the island", "east of Yanille."),
                done = listOf("Hazelmere studied the bark sample and gave me a scroll."),
                place = "Hazelmere's island, east of Yanille",
                location = HAZELMERE_POST,
                gives = listOf(Items.HAZELMERES_SCROLL to 1),
                talk = {
                    if (!player.inventory.contains(Items.BARK_SAMPLE)) {
                        chatNpc("Bring me the bark sample from King Narnode.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.BARK_SAMPLE)
                        chatNpc("The tree is being poisoned from within - someone fed it", "Daconia rocks. Take this scroll to the king.", facialExpression = FacialExpression.WORRIED)
                        FoundationRewards.grant(player, Items.HAZELMERES_SCROLL, 1)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.CHARLIE,
                journal = listOf("I should speak to ${red("Charlie")}, the prisoner at the top of the", "Grand Tree."),
                done = listOf("Charlie told me that Glough paid him for the rocks."),
                place = "the top floor of the Grand Tree",
                location = CHARLIE_POST,
                talk = {
                    chatNpc("Glough paid me to fetch the rocks from Hazelmere! I", "did not know what he wanted them for. Ask at the", "Karamja shipyard - password: Ka-Lu-Min.", facialExpression = FacialExpression.SAD)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.FOREMAN,
                journal = listOf("I should visit the ${red("foreman")} at the Karamja shipyard."),
                done = listOf("The foreman gave me a lumber order."),
                place = "the Karamja shipyard",
                location = FOREMAN_POST,
                gives = listOf(Items.LUMBER_ORDER to 1),
                talk = {
                    chatPlayer("Ka-Lu-Min.", facialExpression = FacialExpression.CALM_TALK)
                    chatNpc("Ah, you know the password. Glough ordered a huge", "amount of lumber. Take this order to the king.", facialExpression = FacialExpression.CALM_TALK)
                    FoundationRewards.grant(player, Items.LUMBER_ORDER, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KING_NARNODE_SHAREEN,
                journal = listOf("I should show ${red("Hazelmere's scroll")} and the ${red("lumber order")} to", "${red("King Narnode")}."),
                done = listOf("King Narnode learnt that Glough is the traitor."),
                place = "the Grand Tree",
                location = NARNODE_POST,
                talk = {
                    if (!player.inventory.contains(Items.HAZELMERES_SCROLL) || !player.inventory.contains(Items.LUMBER_ORDER)) {
                        chatNpc("Bring me Hazelmere's scroll and the foreman's lumber", "order as proof.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.HAZELMERES_SCROLL)
                        player.inventory.remove(Items.LUMBER_ORDER)
                        chatNpc("Glough! After all I did for him... Go to his house and", "find out what lies under the trapdoor.", facialExpression = FacialExpression.ANGRY)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.KING_NARNODE_SHAREEN,
                journal = listOf("I must defeat the ${red("Black demon")} (level 172) that Glough set on me", "under the Grand Tree."),
                done = listOf("I defeated the Black demon under the Grand Tree."),
                place = "the tunnels under the Grand Tree",
                location = DEMON_FIGHT,
                tip = "The Black demon can be safespotted in OSRS. Bring good food and protect from melee.",
                fight = QuestFight(Npcs.BLACK_DEMON_677, "Black demon", 172, DEMON_FIGHT, "Glough sends his regards!"),
                talk = {
                    chatNpc("Glough's trapdoor leads to the tunnels. Be careful -", "he has left a guard.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KING_NARNODE_SHAREEN,
                journal = listOf("I should tell ${red("King Narnode")} that the Black demon is dead."),
                done = listOf("King Narnode thanked me; the Daconia rocks are gone."),
                place = "the Grand Tree",
                location = NARNODE_POST,
                talk = {
                    chatNpc("The Daconia rocks are gone and Glough is exposed. The", "Grand Tree is saved - the gnomes are in your debt.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}
