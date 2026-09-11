package gg.rsmod.plugins.content.areas.draynor

import gg.rsmod.plugins.content.quests.*
import gg.rsmod.plugins.content.quests.impl.PrinceAliRescue

/**
 * Ported from Void donor `content/area/misthalin/draynor_village/LadyKeli.kt`.
 * Drives the Prince Ali Rescue stage 6-7 hand-off: once the guard is drunk, using rope on Lady
 * Keli ties her up and advances the quest, alerting the nearby guard to attack the player.
 * Freeing Prince Ali himself (stage 7-8) is not ported yet - tracked in
 * RSPS_DONOR_PORT_PROGRESS.md.
 */
val ladyKeliPrinceAliRescue = PrinceAliRescue

on_npc_option(npc = Npcs.LADY_KELI, option = "talk-to") {
    player.queue {
        when (player.getCurrentStage(ladyKeliPrinceAliRescue)) {
            in 1..5 -> {
                chatNpc("What do you want?")
                chatPlayer("Nothing?")
                chatNpc("Clear off then.")
            }

            6 -> {
                if (player.inventory.contains(Items.ROPE)) {
                    chatPlayer("Hello! I'm here to tie you up!")
                    chatNpc("What?")
                    tieUpKeli(this)
                } else {
                    messageBox("You cannot tie Keli up until you have all the equipment and dealt with the guard.")
                }
            }

            in 7..8 -> chatNpc("You tricked me, and tied me up! Guards, kill this stranger!")

            else ->
                if (player.finishedQuest(ladyKeliPrinceAliRescue)) {
                    chatNpc("You tricked me, and tied me up! Guards, kill this stranger!")
                } else {
                    chatNpc("What do you want?")
                }
        }
    }
}

on_item_on_npc(item = Items.ROPE, npc = Npcs.LADY_KELI) {
    player.queue {
        if (player.getCurrentStage(ladyKeliPrinceAliRescue) == 6) {
            tieUpKeli(this)
        } else {
            chatPlayer("I don't think she'd be interested in that.")
        }
    }
}

suspend fun tieUpKeli(it: QueueTask) {
    it.messageBox("You overpower Keli, tie her up, and put her in a cupboard.")
    it.player.inventory.remove(Items.ROPE)
    it.player.advanceToNextStage(ladyKeliPrinceAliRescue)
    val guard = it.player.world.npcs.firstOrNull { npc ->
        npc.id == Npcs.JAIL_GUARD_917 && npc.tile.isWithinRadius(it.player.tile, 10)
    }
    guard?.forceChat("Yes M'lady!")
    guard?.attack(it.player)
}
