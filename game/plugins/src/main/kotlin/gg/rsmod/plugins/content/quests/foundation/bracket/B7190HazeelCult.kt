package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.QuestFight
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q23: Hazeel Cult (bracket 71-90, Ceril's side only; the Hazeel side is not built - dossier BRACKET-QUESTS).
// OSRS Wiki: no requirements, a level 13 cultist; 1 QP, 1,500 Thieving XP, 2,000 coins, the Carnillean armour. ONTGR: none.

private val CERIL_POST = Tile(2565, 3268, 0)
private val CLIVET_POST = Tile(2567, 9683, 0)
private val ALOMONE_FIGHT = Tile(2609, 9670, 0)

object HazeelCult : ShortQuest(
    name = "Hazeel Cult",
    startPoint = "Speak to Ceril Carnillean in East Ardougne, just north of the Clock Tower.",
    rewards = "1 Quest Point, 1,500 Thieving XP, 2,000 coins, the Carnillean armour.",
    pointReward = 1,
    questId = Varps.HAZEEL_CULT_PROGRESS,
    slot = 59,
    completedValue = 4, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.CERIL_CARNILLEAN,
    startNpcName = "Ceril Carnillean",
    startPlace = "East Ardougne",
    startLocation = CERIL_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs = QuestRequirements(extra = listOf("Must be able to defeat a level 13 cultist (Alomone)."))

    override val recommendation = Recommendation.combat(15)

    override val reward =
        QuestReward(
            icon = Items.CARNILLEAN_ARMOUR,
            xp = listOf(Skills.THIEVING to 1_500.0),
            items = listOf(Items.COINS_995 to 2_000),
            lines = listOf("1 Quest Point", "1,500 Thieving XP", "2,000 coins"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "The family armour has been stolen by the Hazeel",
            "cult. I fear my butler is mixed up in it. Will you",
            "find the cult's hideout and get it back?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll help.", "Not right now.", title = "Start Hazeel Cult?") != 1) return false
        chatPlayer("I'll get your armour back.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Clivet, a cultist, is hiding in the caves south of the manor.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.CLIVET,
                journal = listOf("I should find ${red("Clivet")} in the caves south of the Carnillean", "manor."),
                done = listOf("Clivet told me where the cult hides the armour."),
                place = "the caves south of the manor",
                location = CLIVET_POST,
                talk = {
                    chatNpc("The master Alomone keeps the armour in the cult's", "hideout. You will have to fight him for it!", facialExpression = FacialExpression.ANGRY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.CLIVET,
                journal = listOf("I must defeat ${red("Alomone")} (level 13) in the cult's hideout."),
                done = listOf("I defeated Alomone and took the Carnillean armour."),
                place = "the cult's hideout",
                location = ALOMONE_FIGHT,
                tip = "Alomone is only level 13: normal food is enough.",
                gives = listOf(Items.CARNILLEAN_ARMOUR to 1),
                fight = QuestFight(Npcs.ALOMONE, "Alomone", 13, ALOMONE_FIGHT, "You will never leave here alive!"),
                talk = {
                    chatNpc("Alomone is in the hideout, to the east. Take the", "armour from him!", facialExpression = FacialExpression.ANGRY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.CERIL_CARNILLEAN,
                journal = listOf("I should return the ${red("Carnillean armour")} to ${red("Ceril Carnillean")}."),
                done = listOf("I returned the Carnillean armour to Ceril."),
                place = "East Ardougne",
                location = CERIL_POST,
                talk = {
                    if (!player.inventory.contains(Items.CARNILLEAN_ARMOUR)) {
                        chatNpc("You have not brought the armour yet.", facialExpression = FacialExpression.WORRIED)
                        false
                    } else {
                        chatNpc("My family's armour! Thank you - take these 2,000", "coins as a reward.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
        )
}
