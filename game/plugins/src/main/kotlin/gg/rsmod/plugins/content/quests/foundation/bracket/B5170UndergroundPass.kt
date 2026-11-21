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
import gg.rsmod.plugins.content.quests.foundation.QuestSearch
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q14: Underground Pass (bracket 51-70). OSRS Wiki: Ranged 25 (boostable), Biohazard; 5 QP; 3,000 Agility + 3,000 Attack XP;
// Iban's staff (the Iban Blast spell needs it, OsrsQuestRequirements.WEAR) and Klank's gauntlets.
// The tiles below are estimated from the OSRS map; the real floor check is the P48ContentTests tile test + the KIJKLIJST.

private val LATHAS_UP = Tile(2575, 3297, 1)
private val KOFTIK_POST = Tile(2438, 3313, 0)
private val PALADIN_HALL = Tile(2415, 9688, 0)
private val DEMON_CAVE = Tile(2389, 9677, 0)
private val KALRAG_LAIR = Tile(2392, 9684, 0)
private val IBAN_WELL = Tile(2322, 9807, 0)

object UndergroundPass : ShortQuest(
    name = "Underground Pass",
    startPoint = "Speak to King Lathas in Ardougne Castle.",
    rewards = "5 Quest Points, 3,000 Agility XP, 3,000 Attack XP, Iban's staff (Iban Blast), Klank's gauntlets.",
    pointReward = 5,
    questId = Varps.UNDERGROUND_PASS_PROGRESS,
    slot = 114,
    completedValue = 7, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.KING_LATHAS,
    startNpcName = "King Lathas",
    startPlace = "Ardougne Castle",
    startLocation = LATHAS_UP,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.RANGED to 25),
            quests = listOf("Biohazard"),
            boostable = setOf(Skills.RANGED),
        )

    override val recommendation = Recommendation.combat(50)

    override val reward =
        QuestReward(
            icon = Items.IBANS_STAFF,
            xp = listOf(Skills.AGILITY to 3_000.0, Skills.ATTACK to 3_000.0),
            lines = listOf("3,000 Agility XP", "3,000 Attack XP", "Iban's staff and the Iban Blast spell", "Klank's gauntlets"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "My brother Tyras has gone west through the Underground",
            "Pass to fight the dark lord Iban. The pass is full of",
            "danger. Will you follow and bring him back?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll follow him.", "Not right now.", title = "Start Underground Pass?") != 1) return false
        chatPlayer("I'll go through the pass.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Speak to Koftik at the pass entrance, west of West Ardougne.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.KOFTIK,
                journal = listOf("I should speak to ${red("Koftik")} at the entrance of the Underground", "Pass, west of West Ardougne."),
                done = listOf("Koftik told me what waits in the pass."),
                place = "the Underground Pass entrance",
                location = KOFTIK_POST,
                talk = {
                    chatNpc(
                        "The pass is guarded: three paladins turned bad, three",
                        "demons, and Kalrag, Iban's spider. Defeat them all.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KOFTIK,
                journal = listOf("I must defeat the three ${red("paladins")} (level 62) in the pass: Sir", "Jerro, Sir Carl and Sir Harry."),
                done = listOf("I defeated the three paladins and took their badges."),
                place = "the Underground Pass",
                location = PALADIN_HALL,
                gives = listOf(Items.PALADINS_BADGE to 1),
                tip = "The paladins come one after another. Bring food.",
                fight =
                    QuestFight(
                        Npcs.SIR_JERRO, "Sir Jerro", 62, PALADIN_HALL, "For Iban!",
                        chain = listOf(Npcs.SIR_CARL to "You will not pass!", Npcs.SIR_HARRY to "Die, intruder!"),
                    ),
                talk = {
                    chatNpc("The paladins hold the pass beyond the first bridge.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KOFTIK,
                journal = listOf("I must defeat the three ${red("demons")} (level 91) deeper in the pass:", "Doomion, Othainian and Holthion."),
                done = listOf("I defeated the three demons."),
                place = "the Underground Pass",
                location = DEMON_CAVE,
                tip = "The demons hit hard. Bring food and prayer.",
                fight =
                    QuestFight(
                        Npcs.DOOMION, "Doomion", 91, DEMON_CAVE, "Rrrraaaah!",
                        chain = listOf(Npcs.OTHAINIAN to "Another soul for Iban!", Npcs.HOLTHION to "You will burn!"),
                    ),
                talk = {
                    chatNpc("Three demons guard the next cave.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KOFTIK,
                journal = listOf("I must defeat ${red("Kalrag")} (level 89) and smear Iban's doll with", "his blood."),
                done = listOf("I defeated Kalrag and smeared the doll of Iban with his", "blood."),
                place = "the Underground Pass",
                location = KALRAG_LAIR,
                gives = listOf(Items.DOLL_OF_IBAN to 1),
                tip = "Kalrag is a giant spider. Bring an antipoison.",
                fight = QuestFight(Npcs.KALRAG, "Kalrag", 89, KALRAG_LAIR, "Hsssss!"),
                talk = {
                    chatNpc("Kalrag lurks in the last cave before Iban's temple.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KOFTIK,
                journal = listOf("I should throw the doll of Iban into the well in ${red("Iban's temple")}."),
                done = listOf("The doll fell into the well; Iban is destroyed."),
                place = "Iban's temple",
                location = IBAN_WELL,
                gives = listOf(Items.IBANS_STAFF to 1),
                search =
                    QuestSearch(
                        IBAN_WELL, "You throw the doll into the well. Iban is destroyed! You take his staff.", 3,
                        gives = listOf(Items.IBANS_STAFF to 1, Items.KLANKS_GAUNTLETS to 1),
                        needs = listOf(Items.DOLL_OF_IBAN to 1),
                        missing = "You need the doll of Iban to destroy him.",
                    ),
                talk = {
                    chatNpc("Iban's temple lies at the end of the pass. Take the doll there.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KING_LATHAS,
                journal = listOf("I should tell ${red("King Lathas")} that Iban is destroyed."),
                done = listOf("King Lathas thanked me."),
                place = "Ardougne Castle",
                location = LATHAS_UP,
                talk = {
                    chatNpc("Iban is gone, and the pass is safe. Ardougne thanks you.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}
