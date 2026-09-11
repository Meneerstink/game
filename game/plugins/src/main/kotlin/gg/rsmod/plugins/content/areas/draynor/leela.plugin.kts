package gg.rsmod.plugins.content.areas.draynor

import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.content.quests.*
import gg.rsmod.plugins.content.quests.impl.PrinceAliRescue

/**
 * Ported from Void donor `content/area/misthalin/draynor_village/Leela.kt`.
 * Drives the Prince Ali Rescue stage-2 hand-off (disguise/key briefing) near the Draynor jail.
 * Stages 4-7 (guard weakness talk, beer, Lady Keli tie-up, freeing Prince Ali) are Guard/Joe/
 * LadyKeli/PrinceAli-side and not ported yet - the quest stays gated at stage 3 until those NPCs
 * are ported.
 */
val leelaPrinceAliRescue = PrinceAliRescue

private val escapeKit = listOf(Items.WIG, Items.PINK_SKIRT, Items.PASTE, Items.ROPE)

on_npc_option(npc = Npcs.LEELA, option = "talk-to") {
    player.queue {
        when (player.getCurrentStage(leelaPrinceAliRescue)) {
            2 -> {
                chatPlayer("I am here to help you free the prince.")
                chatNpc("Your employment is known to me. Now, do you know all that we need to make the break?")
                leelaInfoMenu(this)
                player.advanceToNextStage(leelaPrinceAliRescue)
            }

            3 -> {
                if (escapeKit.all { player.inventory.contains(it) }) {
                    chatNpc(
                        "Okay now, you have all the basic equipment. Now we just need a copy of",
                        "the key. Take some soft clay to Lady Keli, get her to show you the key,",
                        "then bring the imprint and a bronze bar to Osman.",
                    )
                } else {
                    chatNpc("You're back. How are things going?")
                    if (!player.inventory.contains(Items.WIG)) {
                        chatNpc("You still need a blonde wig. There's an Old Sailor nearby who may be able to help.")
                    } else if (!player.inventory.contains(Items.PINK_SKIRT)) {
                        chatNpc("You still need a pink skirt, same as Keli's. A Clothes Shop should have one.")
                    } else if (!player.inventory.contains(Items.PASTE)) {
                        chatNpc("You still need some skin colouring paste. There's a Witch close to here who may know how to make it.")
                    } else if (!player.inventory.contains(Items.ROPE)) {
                        chatNpc("You still need some rope, to tie up Keli. A rope maker should be around here.")
                    }
                }
            }

            in 4..7 -> chatNpc("Speak with Osman if you need a reminder of what's left to do to free the prince.")

            8 -> chatNpc("Thank you, Al-Kharid will forever owe you for your help.")

            else ->
                if (player.finishedQuest(leelaPrinceAliRescue)) {
                    chatNpc("Thank you, Al-Kharid will forever owe you for your help. I think that if there is ever anything that needs to be done, you will be someone they can rely on.")
                } else {
                    chatPlayer("What are you waiting here for?")
                    chatNpc("That is no concern of yours, adventurer.", facialExpression = FacialExpression.SUSPICIOUS)
                }
        }
    }
}

suspend fun leelaInfoMenu(it: QueueTask) {
    when (
        it.options(
            "I must make a disguise. What do you suggest?",
            "I need to get the key made.",
            "What can I do with the guards?",
            "I will go and get the rest of the escape equipment.",
        )
    ) {
        FIRST_OPTION -> {
            it.chatPlayer("I must make a disguise. What do you suggest?")
            it.chatNpc(
                "Only the lady Keli can wander about outside the jail. The guards will",
                "shoot to kill if they see the prince out, so we need a disguise good",
                "enough to fool them at a distance.",
            )
            it.chatNpc(
                "You need a blonde wig, a pink skirt like hers, and something to colour",
                "the prince's skin lighter. You'll also need rope, to tie Keli up.",
            )
        }

        SECOND_OPTION -> {
            it.chatPlayer("I need to get the key made.")
            it.chatNpc(
                "Yes, that is most important. There is no way you can get the real key.",
                "It is on a chain around Keli's neck. Almost impossible to steal.",
            )
            it.chatNpc(
                "Get some soft clay and get her to show you the key somehow. Then take",
                "the print, with bronze, to my father.",
            )
        }

        THIRD_OPTION -> {
            it.chatPlayer("What can I do with the guards?")
            it.chatNpc(
                "Most of the guards will be easy. The disguise will get past them. The",
                "only guard who will be a problem will be the one at the door.",
            )
            it.chatNpc("We can discuss this more when you have the rest of the escape kit.")
        }

        FOURTH_OPTION -> {
            it.chatPlayer("I will go and get the rest of the escape equipment.")
            it.chatNpc("Good, I shall await your return with everything.", facialExpression = FacialExpression.SUSPICIOUS)
        }
    }
}
