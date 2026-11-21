package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.QuestFight
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q10: The Fremennik Trials (bracket 51-70). OSRS Wiki: no skill requirements; 3 QP; 2,812.4 XP in ten skills.
// ONTGR: the four Fremennik helms need this quest to wear (items\osrs\OsrsQuestRequirements.WEAR).

private val BRUNDT_POST = Tile(2658, 3667, 0)
private val SIGLI_POST = Tile(2660, 3653, 0)
private val THORVALD_POST = Tile(2666, 3693, 0)
private val DRAUGEN_HUNT = Tile(2672, 3640, 0)
private val KOSCHEI_ARENA = Tile(2665, 3687, 0)

object FremennikTrials : ShortQuest(
    name = "The Fremennik Trials",
    startPoint = "Speak to Brundt the Chieftain in the longhall in Rellekka.",
    rewards = "3 Quest Points, 2,812 XP in ten skills, access to Neitiznot, Jatizso, Miscellania and Etceteria, the Fremennik helms.",
    pointReward = 3,
    questId = Varps.THE_FREMENNIK_TRIALS_PROGRESS,
    slot = 49,
    completedValue = 6, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.BRUNDT_THE_CHIEFTAIN,
    startNpcName = "Brundt the Chieftain",
    startPlace = "the longhall in Rellekka",
    startLocation = BRUNDT_POST,
) {
    override val ownSteps: Boolean get() = true

    override val reward =
        QuestReward(
            icon = Items.BERSERKER_HELM,
            xp =
                listOf(
                    Skills.AGILITY, Skills.ATTACK, Skills.CRAFTING, Skills.DEFENCE, Skills.FISHING,
                    Skills.FLETCHING, Skills.CONSTITUTION, Skills.STRENGTH, Skills.THIEVING, Skills.WOODCUTTING,
                ).map { it to 2_812.4 },
            lines = listOf("2,812 XP in ten skills", "Access to Neitiznot, Jatizso, Miscellania and Etceteria", "The Fremennik helms can be worn"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "You are an outlander. If you wish to be one of us you",
            "must win the votes of the council of Rellekka. Prove",
            "yourself in the trials.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll prove myself.", "Not right now.", title = "Start The Fremennik Trials?") != 1) return false
        chatPlayer("I will win your votes.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Begin with Sigli the Huntsman. He sets a hunt.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.SIGLI_THE_HUNTSMAN,
                journal = listOf("I should speak to ${red("Sigli the Huntsman")} in Rellekka."),
                done = listOf("Sigli sent me to hunt the Draugen."),
                place = "Rellekka",
                location = SIGLI_POST,
                talk = {
                    chatNpc("Hunt the Draugen, the beast that stalks the fells", "east of town, and I will give you my vote.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SIGLI_THE_HUNTSMAN,
                journal = listOf("I must defeat the ${red("Draugen")} (level 69) near Rellekka."),
                done = listOf("I slew the Draugen."),
                place = "east of Rellekka",
                location = DRAUGEN_HUNT,
                tip = "The Draugen hits hard. Bring food.",
                fight = QuestFight(Npcs.THE_DRAUGEN, "Draugen", 69, DRAUGEN_HUNT, "Raaah!"),
                talk = {
                    chatNpc("The Draugen haunts the land east of town.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.THORVALD_THE_WARRIOR,
                journal = listOf("I should speak to ${red("Thorvald the Warrior")} in Rellekka."),
                done = listOf("Thorvald told me to face Koschei the Deathless."),
                place = "Rellekka",
                location = THORVALD_POST,
                talk = {
                    chatNpc("Show me your courage: fight Koschei the Deathless", "in the cellar below the longhall.", facialExpression = FacialExpression.ANGRY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.THORVALD_THE_WARRIOR,
                journal = listOf("I must defeat ${red("Koschei the Deathless")} in all four of his forms."),
                done = listOf("I defeated Koschei the Deathless."),
                place = "the cellar below the longhall",
                location = KOSCHEI_ARENA,
                tip = "In OSRS you fight Koschei without weapons or armour. Four forms follow one another.",
                fight =
                    QuestFight(
                        Npcs.KOSCHEI_THE_DEATHLESS, "Koschei the Deathless", 40, KOSCHEI_ARENA, "You cannot kill me!",
                        chain =
                            listOf(
                                Npcs.KOSCHEI_THE_DEATHLESS_1291 to "I am still here!",
                                Npcs.KOSCHEI_THE_DEATHLESS_1292 to "Fool - I cannot die!",
                                Npcs.KOSCHEI_THE_DEATHLESS_1293 to "Now you face my final form!",
                            ),
                    ),
                talk = {
                    chatNpc("Koschei waits in the cellar. Fight him bare-handed.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BRUNDT_THE_CHIEFTAIN,
                journal = listOf("I should return to ${red("Brundt the Chieftain")} in the longhall."),
                done = listOf("Brundt declared me a Fremennik."),
                place = "the longhall in Rellekka",
                location = BRUNDT_POST,
                talk = {
                    chatNpc("You have the votes of the council. From today you", "are a Fremennik. Rellekka is open to you.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}
