package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.EquipmentType
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

// PROJECT48 Q19: Monkey Madness (bracket 71-90, Master: 8 steps). OSRS Wiki: The Grand Tree + Tree Gnome Village, a level 195
// Jungle demon; 3 QP, 10,000 coins, 3 diamonds, Daero's training (35,000 XP in two skills + 20,000 in two), the Dragon scimitar.
// ONTGR: the Dragon scimitar is worn only after this quest (items\osrs\OsrsQuestRequirements.kt; Daga's shop already sells it only
// after the quest); Ape Atoll stays open for everybody who has the greegree (the trip itself is the quest). The Quest Helper tip names
// the monkey greegree rule of step 5. Daero's training is offered by King Narnode in the last conversation (Daero is shared with
// Monkey Madness II, whose Daero post has its own talk).

private val NARNODE_POST = Tile(2466, 3497, 0)
private val HANGAR_POST = Tile(2648, 4517, 0)
private val WAYDAR_POST = Tile(2891, 2724, 0)
private val GARKOR_POST = Tile(2805, 2762, 0)
private val ZOOKNOCK_POST = Tile(2803, 9145, 0)
private val AWOWOGEI_POST = Tile(2793, 2765, 0)
private val DEMON_FIGHT = Tile(2808, 2766, 0)

object MonkeyMadness : ShortQuest(
    name = "Monkey Madness",
    startPoint = "Speak to King Narnode Shareen in the Grand Tree.",
    rewards = "3 Quest Points, 10,000 coins, 3 diamonds, Daero's training, the Dragon scimitar.",
    pointReward = 3,
    questId = Varps.MONKEY_MADNESS_PROGRESS,
    slot = 73,
    completedValue = 9, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.KING_NARNODE_SHAREEN,
    startNpcName = "King Narnode Shareen",
    startPlace = "the Grand Tree",
    startLocation = NARNODE_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            quests = listOf("The Grand Tree", "Tree Gnome Village"),
            extra = listOf("Must be able to defeat a level 195 Jungle demon."),
        )

    override val recommendation = Recommendation.combat(80)

    override val reward =
        QuestReward(
            icon = Items.MONKEYSPEAK_AMULET,
            items = listOf(Items.COINS_995 to 10_000, Items.DIAMOND to 3),
            lines = listOf("3 Quest Points", "10,000 coins", "3 diamonds", "Daero's training (35,000 + 20,000 XP)", "The right to wield the Dragon scimitar"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "The 10th squad of gnomes has gone missing on Ape",
            "Atoll, and the monkeys' king may be involved. Take",
            "the royal seal and find out what happened.",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll go.", "Not right now.", title = "Start Monkey Madness?") != 1) return false
        chatPlayer("I'll find the 10th squad.", facialExpression = FacialExpression.CALM_TALK)
        FoundationRewards.grant(player, Items.GNOME_ROYAL_SEAL, 1)
        chatNpc("Daero in the glider hangar will take you south.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.DAERO,
                journal = listOf("I should speak to ${red("Daero")} in the glider hangar."),
                done = listOf("Daero agreed to fly me towards Ape Atoll."),
                place = "the glider hangar",
                location = HANGAR_POST,
                talk = {
                    chatNpc("The royal seal! I can fly you as far as Crash Island.", "Waydar will help you across.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.WAYDAR,
                journal = listOf("I should speak to ${red("Waydar")} on ${red("Crash Island")}."),
                done = listOf("Waydar arranged my passage to Ape Atoll."),
                place = "Crash Island",
                location = WAYDAR_POST,
                talk = {
                    chatNpc("A gnome seal... The way to Ape Atoll is by boat from", "here. Speak to Garkor of the 10th squad over there.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GARKOR,
                journal = listOf("I should speak to ${red("Garkor")}, the sergeant of the 10th squad,", "on ${red("Ape Atoll")}."),
                done = listOf("Garkor told me the 10th squad is held in the monkey temple."),
                place = "Ape Atoll",
                location = GARKOR_POST,
                talk = {
                    chatNpc("The squad is held below the temple. Zooknock, the", "monkey expert in the tunnels, can give you a way to", "talk to the monkeys.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ZOOKNOCK,
                journal = listOf("I should find ${red("Zooknock")} in the tunnels below Ape Atoll."),
                done = listOf("Zooknock gave me a monkeyspeak amulet and a greegree."),
                place = "the tunnels below Ape Atoll",
                location = ZOOKNOCK_POST,
                gives = listOf(Items.MONKEYSPEAK_AMULET to 1, Items.MONKEY_GREEGREE to 1),
                tip = "Wield the greegree and wear the amulet before you speak.",
                talk = {
                    chatNpc("Take this amulet - it lets you understand the monkeys - and", "a greegree, so you can pass as one of them.", facialExpression = FacialExpression.HAPPY)
                    FoundationRewards.grant(player, Items.MONKEYSPEAK_AMULET, 1)
                    FoundationRewards.grant(player, Items.MONKEY_GREEGREE, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.AWOWOGEI,
                journal = listOf("I should speak to ${red("King Awowogei")} while wielding the monkey", "greegree and wearing the monkeyspeak amulet."),
                done = listOf("King Awowogei agreed to an alliance."),
                place = "King Awowogei's palace",
                location = AWOWOGEI_POST,
                tip = "Wield the greegree: the king only speaks to monkeys.",
                talk = {
                    if (!player.hasEquipped(EquipmentType.WEAPON, Items.MONKEY_GREEGREE)) {
                        chatNpc("Ook! Ook! (A human! Guards - no, wait... only a monkey", "may speak to the king!)", facialExpression = FacialExpression.ANGRY)
                        false
                    } else {
                        chatNpc("Ook ook. (A fellow monkey. The 10th squad? It is", "held below. I will hold my guards back.)", facialExpression = FacialExpression.CALM_TALK)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.GARKOR,
                journal = listOf("I should return to ${red("Garkor")} for the 10th squad's sigil."),
                done = listOf("Garkor gave me the 10th squad sigil."),
                place = "Ape Atoll",
                location = GARKOR_POST,
                gives = listOf(Items._10TH_SQUAD_SIGIL to 1),
                talk = {
                    chatNpc("Take the squad's sigil. It teleports you to the arena", "where the Jungle demon waits. Be careful!", facialExpression = FacialExpression.TOUGH)
                    FoundationRewards.grant(player, Items._10TH_SQUAD_SIGIL, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GARKOR,
                journal = listOf("I must defeat the ${red("Jungle demon")} (level 195) in the arena."),
                done = listOf("I defeated the Jungle demon."),
                place = "the Jungle demon arena",
                location = DEMON_FIGHT,
                tip = "The Jungle demon uses all three styles. Bring prayer.",
                fight = QuestFight(Npcs.JUNGLE_DEMON, "Jungle demon", 195, DEMON_FIGHT, "You cannot stop what has begun!"),
                talk = {
                    chatNpc("The demon waits in the arena. Use the sigil and finish it!", facialExpression = FacialExpression.TOUGH)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KING_NARNODE_SHAREEN,
                journal = listOf("I should report to ${red("King Narnode Shareen")} in the Grand Tree."),
                done = listOf("King Narnode rewarded me and Daero offered his training."),
                place = "the Grand Tree",
                location = NARNODE_POST,
                talk = {
                    chatNpc("The 10th squad is free and the monkeys are at peace. The", "gnomes are in your debt. Daero offers you his training.", facialExpression = FacialExpression.HAPPY)
                    when (options("Attack and Defence (35,000 XP each).", "Strength and Hitpoints (35,000 XP each).", title = "Daero's training")) {
                        1 -> {
                            player.addXp(Skills.ATTACK, 35_000.0)
                            player.addXp(Skills.DEFENCE, 35_000.0)
                            player.addXp(Skills.STRENGTH, 20_000.0)
                            player.addXp(Skills.CONSTITUTION, 20_000.0)
                        }
                        else -> {
                            player.addXp(Skills.STRENGTH, 35_000.0)
                            player.addXp(Skills.CONSTITUTION, 35_000.0)
                            player.addXp(Skills.ATTACK, 20_000.0)
                            player.addXp(Skills.DEFENCE, 20_000.0)
                        }
                    }
                    true
                },
            ),
        )
}
