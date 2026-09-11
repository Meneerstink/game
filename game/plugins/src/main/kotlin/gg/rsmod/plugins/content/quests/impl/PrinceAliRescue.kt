package gg.rsmod.plugins.content.quests.impl

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.quests.*

/**
 * Ported from Void donor `content/quest/free/prince_ali_rescue/PrinceAliRescue.kt` journal text
 * and `content/area/kharidian_desert/al_kharid/Hassan.kt` reward logic (3 Quest Points, 700 coins).
 * NPC dialogue wiring for Hassan/Osman/Leela/LadyKeli/PrinceAli/Joe (Draynor Village + Al Kharid)
 * is not ported yet - tracked as remaining work in RSPS_DONOR_PORT_PROGRESS.md.
 */
object PrinceAliRescue : Quest(
    name = "Prince Ali Rescue",
    startPoint = "Speak to Chancellor Hassan in the Al Kharid Palace.",
    requirements = emptyList(),
    requiredItems = "None.",
    combat = "None.",
    rewards = "3 Quest Points and 700 Coins.",
    pointReward = 3,
    questId = Varps.PRINCE_ALI_RESCUE_PROGRESS,
    spriteId = 2381,
    slot = 10,
    stages = 8,
) {
    init {
        addQuest(this)
    }

    override fun getObjective(
        player: Player,
        stage: Int,
    ): QuestStage =
        when (stage) {
            1 ->
                QuestStage(
                    objectives =
                        listOf(
                            "I spoke to Hassan, the Chancellor to the Emir of Al Kharid, in",
                            "the Al Kharid Palace. He asked for my help with an urgent",
                            "matter, and directed me to speak to ${red("Osman")}, Al Kharid's",
                            "${red("Spymaster")}, just outside the ${red("Palace.")}",
                        ),
                )

            2 ->
                QuestStage(
                    objectives =
                        listOf(
                            striked("I spoke to Hassan, the Chancellor to the Emir of Al Kharid, in"),
                            striked("the Al Kharid Palace. He asked for my help with an urgent"),
                            striked("matter, and directed me to speak to Osman, Al Kharid's"),
                            striked("Spymaster."),
                            "I spoke to ${red("Osman")} outside the Al Kharid Palace. He informed me",
                            "that ${red("Prince Ali,")} the Emir's heir, was captured by a group of",
                            "${red("Bandits")} and taken to an ${red("Abandoned Jail")} east of Draynor",
                            "Village. Osman asked for my help in rescuing Prince Ali, and",
                            "suggested I speak with ${red("Leela,")} who I can find spying on the",
                            "Jail.",
                        ),
                )

            3 ->
                QuestStage(
                    objectives =
                        listOf(
                            striked("I spoke to Osman outside the Al Kharid Palace. He informed me"),
                            striked("that Prince Ali, the Emir's heir, was captured by a group of"),
                            striked("Bandits and taken to an Abandoned Jail east of Draynor"),
                            striked("Village. Osman asked for my help in rescuing Prince Ali, and"),
                            striked("suggested I speak with Leela in Draynor Village."),
                            "To free ${red("Prince Ali,")} I need to create him a disguise to make",
                            "him look like ${red("Lady Keli,")} the leader of the Bandits. I also",
                            "need to make a copy of the key to his cell.",
                            "",
                            "According to Leela, I need a ${red("Blonde Wig,")} a ${red("Pink Skirt")} and some",
                            "${red("Skin Paste")} for the disguise. Apparently there's an ${red("Old Sailor")}",
                            "living in Draynor Village who might be able to make a Wig for",
                            "me to then dye. A Pink Skirt can be purchased from a Clothes",
                            "Shop. As for the Skin Paste, Leela thinks a local ${red("Witch")} could",
                            "make me some.",
                        ),
                )

            4 ->
                QuestStage(
                    objectives =
                        listOf(
                            striked("I spoke to Osman outside the Al Kharid Palace. He informed me"),
                            striked("that Prince Ali, the Emir's heir, was captured by a group of"),
                            striked("Bandits and taken to an Abandoned Jail east of Draynor"),
                            striked("Village. Osman asked for my help in rescuing Prince Ali, and"),
                            striked("suggested I speak with Leela in Draynor Village."),
                            striked("With help from Osman and Leela, I created a disguise to make"),
                            striked("Prince Ali look like Lady Keli, the leader of the Bandits. I also"),
                            striked("made a copy of the key to his cell."),
                            "Before I can free Prince Ali, I need to deal with his ${red("Personal")}",
                            "${red("Guard.")} Leela suggested I speak with the Guard to try and",
                            "determine any weaknesses he might have.",
                        ),
                )

            5 ->
                QuestStage(
                    objectives =
                        listOf(
                            striked("With help from Osman and Leela, I created a disguise to make"),
                            striked("Prince Ali look like Lady Keli, the leader of the Bandits. I also"),
                            striked("made a copy of the key to his cell."),
                            "Before I can free Prince Ali, I need to deal with his ${red("Personal")}",
                            "${red("Guard.")} Luckily, it seems the Guard has a love for ${red("Beer.")} If I",
                            "bring him some, I should be able to get him drunk.",
                        ),
                )

            6 ->
                QuestStage(
                    objectives =
                        listOf(
                            striked("With help from Osman and Leela, I created a disguise to make"),
                            striked("Prince Ali look like Lady Keli, the leader of the Bandits. I also"),
                            striked("made a copy of the key to his cell."),
                            "To stop Prince Ali's ${red("Personal Guard")} from being a problem, I",
                            "gave him some ${red("Beer")} to get him drunk. The last thing I need to",
                            "do is deal with ${red("Lady Keli.")} Leela might know how I can do this.",
                        ),
                )

            7 ->
                QuestStage(
                    objectives =
                        listOf(
                            striked("With help from Osman and Leela, I created a disguise to make"),
                            striked("Prince Ali look like Lady Keli, the leader of the Bandits. I also"),
                            striked("made a copy of the key to his cell."),
                            striked("To stop Prince Ali's Personal Guard from being a problem, I"),
                            striked("gave him some Beer to get him drunk."),
                            "To get ${red("Lady Keli")} out of the way, I tied her up and put her in a",
                            "${red("Cupboard.")} I can now free ${red("Prince Ali.")} I'll need to make sure I",
                            "give him his disguise when I do.",
                        ),
                )

            8 ->
                QuestStage(
                    objectives =
                        listOf(
                            striked("With help from Osman and Leela, I created a disguise to make"),
                            striked("Prince Ali look like Lady Keli, the leader of the Bandits. I also"),
                            striked("made a copy of the key to his cell."),
                            striked("To stop Prince Ali's Personal Guard from being a problem, I"),
                            striked("gave him some Beer to get him drunk."),
                            striked("To get Lady Keli out of the way, I tied her up and put her in a"),
                            striked("Cupboard."),
                            "With ${red("Lady Keli")} dealt with, I was able to free ${red("Prince Ali")} and get",
                            "him to safety. I should now return to Chancellor Hassan in the",
                            "Al Kharid Palace.",
                            questCompleteText,
                        ),
                )

            else ->
                QuestStage(
                    objectives =
                        listOf(
                            "I can start this quest by talking to ${red("Chancellor Hassan")} in ${red("Al Kharid Palace.")}",
                        ),
                )
        }

    override fun finishQuest(player: Player) {
        player.advanceToNextStage(this)
        player.inventory.add(Items.COINS_995, 700)
        player.setVarp(Varps.QUEST_POINTS, player.getVarp(Varps.QUEST_POINTS).plus(pointReward))
        player.buildQuestFinish(
            this,
            item = Items.COINS_995,
            rewards = arrayOf("3 Quest Points", "700 Coins"),
        )
    }
}
