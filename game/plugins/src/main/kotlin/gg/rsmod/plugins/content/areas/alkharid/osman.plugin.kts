package gg.rsmod.plugins.content.areas.alkharid

import gg.rsmod.plugins.content.quests.*
import gg.rsmod.plugins.content.quests.impl.PrinceAliRescue

/**
 * Ported from Void donor `content/area/kharidian_desert/al_kharid/Osman.kt`.
 * Drives the Prince Ali Rescue stage-1 hand-off from Hassan to Leela/Draynor Village.
 * Stages 2-7 (disguise, key, guard, Lady Keli) are Leela/Draynor-side and not ported yet.
 */
val osmanPrinceAliRescue = PrinceAliRescue

on_npc_option(npc = Npcs.OSMAN, option = "talk-to") {
    player.queue {
        when (player.getCurrentStage(osmanPrinceAliRescue)) {
            0 -> {
                chatNpc("Hello. I am Osman. What can I assist you with?")
                when (
                    options(
                        "You don't seem very tough. Who are you?",
                        "Nothing. I'm just being nosy.",
                    )
                ) {
                    FIRST_OPTION -> {
                        chatPlayer("You don't seem very tough. Who are you?")
                        chatNpc("I work for Al Kharid's Emir. That is all you need to know.")
                    }

                    SECOND_OPTION -> {
                        chatPlayer("Nothing. I'm just being nosy.")
                        chatNpc("That bothers me not. The secrets of Al Kharid protect themselves.")
                    }
                }
            }

            1 -> {
                chatPlayer("The chancellor trusts me. I have come for instructions.")
                chatNpc(
                    "Our prince is captive by the Lady Keli. We just need to make the",
                    "rescue. There are two things we need you to do.",
                )
                chatNpc(
                    "The prince is guarded by some stupid guards and a clever woman. The",
                    "woman is our only way to get the prince out. Only she can walk freely",
                    "about the area.",
                )
                chatNpc(
                    "I think you will need to tie her up. One coil of rope should do for",
                    "that. Then, disguise the prince as her to get him out without",
                    "suspicion.",
                )
                chatNpc(
                    "Get a skirt like hers. Same colour, same style. Get a blonde wig,",
                    "too. Something to colour the skin of the prince as well.",
                )
                chatNpc(
                    "We need the key, or we need a copy made. If you can get some soft",
                    "clay then you can copy the key, if you can convince Lady Keli to show",
                    "it to you for a moment.",
                )
                chatNpc(
                    "Bring the imprint to me, with a bar of bronze. My daughter and top",
                    "spy, Leela, can help you. She's lurking somewhere near Draynor",
                    "Village now.",
                )
                player.advanceToNextStage(osmanPrinceAliRescue)
            }

            8 -> chatNpc("The prince is safe and on his way home with Leela. You can pick up your payment from the chancellor.")

            else ->
                if (player.finishedQuest(osmanPrinceAliRescue)) {
                    chatNpc("Well done. A great rescue. I will remember you if I have anything dangerous to do.")
                } else {
                    chatNpc("You'll need to find Leela near the Draynor Village jail. She's expecting you.")
                }
        }
    }
}
