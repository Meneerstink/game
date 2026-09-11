package gg.rsmod.plugins.content.areas.alkharid

import gg.rsmod.plugins.content.quests.*
import gg.rsmod.plugins.content.quests.impl.PrinceAliRescue

/**
 * Ported from Void donor `content/area/kharidian_desert/al_kharid/Hassan.kt`.
 * Drives the Prince Ali Rescue start/hand-in dialogue with Chancellor Hassan.
 */
val princeAliRescue = PrinceAliRescue

on_npc_option(npc = Npcs.HASSAN, option = "talk-to") {
    player.queue {
        when (player.getCurrentStage(princeAliRescue)) {
            0 -> {
                chatNpc("Greetings! I am Hassan, Chancellor to the Emir of Al Kharid.")
                when (
                    options(
                        "Can I help you? You must need some help here in the desert.",
                        "It's just too hot here. How can you stand it?",
                        "Do you mind if I just kill your warriors?",
                        "I'd better be off.",
                    )
                ) {
                    FIRST_OPTION -> {
                        chatPlayer("Can I help you? You must need some help here in the desert.")
                        chatNpc(
                            "I need the services of someone, yes. If you are interested, see the",
                            "spymaster, Osman. I manage the finances here. Come to me when you",
                            "need payment.",
                        )
                        player.startQuest(princeAliRescue)
                    }

                    SECOND_OPTION -> tooHotDialogue(this)
                    THIRD_OPTION -> killWarriorsDialogue(this)
                    FOURTH_OPTION -> chatPlayer("I'd better be off.")
                }
            }

            8 -> {
                chatNpc(
                    "You have the eternal gratitude for the Emir for rescuing his son. I am",
                    "authorised to pay you 700 coins.",
                )
                if (player.inventory.freeSlotCount < 1 && !player.inventory.contains(Items.COINS_995)) {
                    chatPlayer("I don't have enough room for that. I'll come back later.")
                } else {
                    princeAliRescue.finishQuest(player)
                }
            }

            else ->
                if (player.finishedQuest(princeAliRescue)) {
                    chatNpc("Thank you for being a friend to Al Kharid. You are always welcome here.")
                } else {
                    chatNpc(
                        "Hello again. I hear you have agreed to help rescue Prince Ali. On behalf",
                        "of the Emir, I will have a reward ready for you upon your success.",
                    )
                }
        }
    }
}

suspend fun tooHotDialogue(it: QueueTask) {
    it.chatPlayer("It's just too hot here. How can you stand it?")
    it.chatNpc("We manage, in our humble way. We are a wealthy town and we have water. It", "cures many thirsts.")
    it.player.inventory.add(Items.JUG_OF_WATER)
    it.itemMessageBox("The chancellor hands you some water.", Items.JUG_OF_WATER)
}

suspend fun killWarriorsDialogue(it: QueueTask) {
    it.chatPlayer("Do you mind if I just kill your warriors?")
    it.chatNpc("Kill our warriors? I assume this is some sort of joke?", facialExpression = FacialExpression.CONFUSED)
    it.chatPlayer("I'll take that as a no. Forget I asked.")
}
