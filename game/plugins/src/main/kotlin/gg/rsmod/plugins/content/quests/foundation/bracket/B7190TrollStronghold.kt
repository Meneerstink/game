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

// PROJECT48 Q20: Troll Stronghold (bracket 71-90). OSRS Wiki: Death Plateau (a non-bracket quest = done for everybody), 15 Agility
// (boostable), a level 113 Troll General; 1 QP, 2,000 Agility XP, the Law talisman.
// ONTGR: Dunstan sells the Law talisman again for 1,000 coins (OSRS Wiki; the BracketNpc.extra of Dunstan).

private val DENULTH_POST = Tile(2898, 3528, 0)
private val DAD_FIGHT = Tile(2920, 3606, 0)
private val GENERAL_FIGHT = Tile(2832, 10088, 2)
private val GODRIC_POST = Tile(2826, 10078, 0)
private val DUNSTAN_POST = Tile(2919, 3576, 0)

object TrollStronghold : ShortQuest(
    name = "Troll Stronghold",
    startPoint = "Speak to Denulth in Burthorpe.",
    rewards = "1 Quest Point, 2,000 Agility XP, a Law talisman.",
    pointReward = 1,
    questId = Varps.TROLL_STRONGHOLD_PROGRESS,
    slot = 113,
    completedValue = 5, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.DENULTH,
    startNpcName = "Denulth",
    startPlace = "Burthorpe",
    startLocation = DENULTH_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.AGILITY to 15),
            quests = listOf("Death Plateau"),
            boostable = setOf(Skills.AGILITY),
            extra = listOf("Must be able to defeat a level 113 Troll General (it can be safespotted)."),
        )

    override val recommendation = Recommendation.combat(70)

    override val reward =
        QuestReward(
            icon = Items.LAW_TALISMAN,
            xp = listOf(Skills.AGILITY to 2_000.0),
            items = listOf(Items.LAW_TALISMAN to 1),
            lines = listOf("1 Quest Point", "2,000 Agility XP", "A Law talisman", "Access to Trollheim and the Troll Stronghold"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "The trolls have taken Godric, Dunstan's son, to their",
            "stronghold. The pass is full of them, and their general",
            "keeps the prison key. Can you free him?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll free Godric.", "Not right now.", title = "Start Troll Stronghold?") != 1) return false
        chatPlayer("I'll bring him home.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Start with Dad, the troll who guards the pass on Trollheim.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.DENULTH,
                journal = listOf("I must defeat ${red("Dad")} (level 101), the troll who guards the pass", "up Trollheim."),
                done = listOf("I defeated Dad and may enter the stronghold."),
                place = "the Trollheim pass",
                location = DAD_FIGHT,
                tip = "Dad hits hard in melee and can be safespotted.",
                fight = QuestFight(Npcs.DAD, "Dad", 101, DAD_FIGHT, "Raaargh! Nobody passes Dad!"),
                talk = {
                    chatNpc("Dad is the troll on the Trollheim pass. Beat him and the", "way to the stronghold is open.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.DENULTH,
                journal = listOf("I must defeat the ${red("Troll General")} (level 113) in the stronghold", "for the prison key."),
                done = listOf("I defeated the Troll General and took the prison key."),
                place = "the Troll Stronghold",
                location = GENERAL_FIGHT,
                tip = "The Troll General hits hard. Bring food.",
                gives = listOf(Items.PRISON_KEY to 1),
                fight = QuestFight(Npcs.TROLL_GENERAL, "Troll General", 113, GENERAL_FIGHT, "Puny human! Stronghold is mine!"),
                talk = {
                    chatNpc("The Troll General carries the prison key. Take it", "from him!", facialExpression = FacialExpression.ANGRY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GODRIC,
                journal = listOf("I should free ${red("Godric")} in his cell with the ${red("prison key")}."),
                done = listOf("I freed Godric from the prison."),
                place = "the Troll Stronghold prison",
                location = GODRIC_POST,
                talk = {
                    if (!player.inventory.contains(Items.PRISON_KEY)) {
                        chatNpc("The cell is locked. You need the prison key.", facialExpression = FacialExpression.SAD)
                        false
                    } else {
                        player.inventory.remove(Items.PRISON_KEY)
                        chatNpc("You came for me! Thank you - Dunstan will be so", "relieved. I will find my own way out.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.DUNSTAN,
                journal = listOf("I should tell ${red("Dunstan")}, the blacksmith, that Godric is free."),
                done = listOf("Dunstan thanked me and gave me a Law talisman."),
                place = "Burthorpe",
                location = DUNSTAN_POST,
                talk = {
                    chatNpc("My son is safe! Please take this Law talisman as", "thanks - the stronghold has an altar for it.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}

/**
 * ONTGR (Troll Stronghold): after the quest Dunstan hands out a lost Law talisman again for 1,000 coins (OSRS Wiki).
 * True when he spoke (offer taken or declined).
 */
suspend fun QueueTask.lawTalismanReclaim(): Boolean {
    if (!gg.rsmod.plugins.content.quests.QuestStatus.isCompleted(player, "Troll Stronghold")) return false
    chatNpc("Lost your Law talisman? I can make another for 1,000 coins.", facialExpression = FacialExpression.CALM_TALK)
    if (options("Buy a Law talisman (1,000 coins).", "No thanks.", title = "Law talisman") != 1) return true
    if (player.inventory.contains(Items.LAW_TALISMAN)) {
        chatNpc("You already have one.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }
    if (player.inventory.getItemCount(Items.COINS_995) < 1_000) {
        chatNpc("You do not have 1,000 coins.", facialExpression = FacialExpression.SAD)
        return true
    }
    player.inventory.remove(Items.COINS_995, 1_000)
    FoundationRewards.grant(player, Items.LAW_TALISMAN, 1)
    chatNpc("Here you go. Keep it safe this time.", facialExpression = FacialExpression.HAPPY)
    return true
}
