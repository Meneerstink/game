package gg.rsmod.plugins.content.areas.fremennik

import gg.rsmod.plugins.content.items.osrs.OsrsInspectTexts
import gg.rsmod.plugins.content.npcs.TranscriptDialogue

/**
 * N557: Olaf the Bard (Rellekka) and the basilisk jaw - OSRS Wiki 'Transcript:Olaf the Bard', option "Do you know any great stories about
 * Basilisk Jaws?" (shown while the player carries a basilisk jaw and has not heard the story). After it the player can make Neitiznot
 * faceguards (serpentine_helm.plugin.kts). Every other talk is his own sourced transcript, as before (talk_to_fallback).
 * ADAPTED: OSRS says the player's Fremennik name; this world has no Fremennik name, so the player's own name is used.
 */
on_npc_option(npc = Npcs.OLAF_THE_BARD, option = "talk-to") {
    val npc = player.getInteractingNpc()
    player.queue {
        if (player.inventory.contains(Items.BASILISK_JAW) && player.attr[OsrsInspectTexts.BASILISK_STORY] != true) {
            chatPlayer("Do you know any great stories about Basilisk Jaws?")
            chatNpc("Why do you ask?")
            itemMessageBox("You show the Basilisk Jaw to Olaf.", Items.BASILISK_JAW)
            chatNpc(
                "Oh my! This must be the jaw of a legendary Basilisk Knight. There's a great song about how the Fremennik warriors of old " +
                    "would use these jaws to enhance their helmets.",
            )
            chatPlayer("I see. I have a nice helmet that Neitiznot's Burgher gave to me. I bet it would work well with that.")
            player.attr[OsrsInspectTexts.BASILISK_STORY] = true
            chatNpc("I'm sure it would, ${gg.rsmod.util.Misc.formatForDisplay(player.username)}. Now, do you want to hear the song?")
            chatPlayer("Err... I think I'll pass. Thanks for the help though.")
            return@queue
        }
        if (!TranscriptDialogue.play(this, npc)) chatNpc("I am afraid I cannot speak to outerlanders. Besides, I am busy composing an epic.")
    }
}
