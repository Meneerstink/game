package gg.rsmod.plugins.content.areas.draynor

import gg.rsmod.plugins.content.quests.*
import gg.rsmod.plugins.content.quests.impl.PrinceAliRescue

/**
 * Ported from Void donor `content/area/misthalin/draynor_village/PrinceAli.kt`.
 * Drives the Prince Ali Rescue stage 7-8 hand-off: once Lady Keli is tied up, giving Prince Ali
 * the wig/pink skirt/paste disguise (or talking to him) hands over the disguise and the bronze
 * key, transmogs him into his disguised form and hides him for 60 seconds while he "escapes",
 * advancing the quest to stage 8. The jail cell door open handler is not ported - Void's
 * `draynor_prison_door_closed`/`draynor_prison_door_opened` cache ids (2881/1541) do not exist in
 * this project's target 667 cache (`Objs.kt` has no entry for either id), so the correct door
 * object/tile for this cache needs separate investigation before it can be wired up. This does
 * not block freeing the prince, since Void's own escape flow frees him via dialogue/item-on-npc,
 * not the door.
 */
val princeAliPrinceAliRescue = PrinceAliRescue

private val disguise = listOf(Items.WIG, Items.PINK_SKIRT, Items.PASTE)

on_npc_option(npc = Npcs.PRINCE_ALI, option = "talk-to") {
    player.queue {
        when (player.getCurrentStage(princeAliPrinceAliRescue)) {
            7 -> escapePrinceAli(this)
            8 -> leavePrinceAli(this)
            else ->
                if (player.finishedQuest(princeAliPrinceAliRescue)) {
                    chatNpc("I owe you my life for that escape. You cannot help me this time, they know who you are. Go in peace, friend of Al-Kharid.")
                } else {
                    chatNpc("...")
                }
        }
    }
}

on_item_on_npc(item = Items.WIG, npc = Npcs.PRINCE_ALI) {
    player.queue {
        if (player.getCurrentStage(princeAliPrinceAliRescue) == 7) {
            escapePrinceAli(this)
        } else {
            chatPlayer("I don't think he needs that right now.")
        }
    }
}

on_item_on_npc(item = Items.PINK_SKIRT, npc = Npcs.PRINCE_ALI) {
    player.queue {
        if (player.getCurrentStage(princeAliPrinceAliRescue) == 7) {
            escapePrinceAli(this)
        } else {
            chatPlayer("I don't think he needs that right now.")
        }
    }
}

suspend fun escapePrinceAli(it: QueueTask) {
    it.chatPlayer("Prince, I've come to rescue you.")
    it.chatNpc("That is very very kind of you, how do I get out?")
    if (!disguise.all { item -> it.player.inventory.contains(item) }) {
        it.chatPlayer("I've already dealt with Lady Keli and the guard. I'm going to get you a disguise so the guards outside don't spot you leaving. I'll be back once I have it.")
        return
    }
    it.chatPlayer("With a disguise. I have removed the Lady Keli. She is tied up, but will not stay tied up for long.")
    it.chatPlayer("Take this disguise, and this key.")
    it.messageBox("You hand the disguise and the key to the prince.")
    it.player.inventory.remove(Items.WIG)
    it.player.inventory.remove(Items.PINK_SKIRT)
    it.player.inventory.remove(Items.PASTE)
    it.player.inventory.remove(Items.BRONZE_KEY)
    val princeAli = it.player.getInteractingNpc()
    princeAli.setTransmogId(Npcs.PRINCE_ALI_921)
    it.player.advanceToNextStage(princeAliPrinceAliRescue)
    leavePrinceAli(it)
}

suspend fun leavePrinceAli(it: QueueTask) {
    it.chatNpc("Thank you, my friend. I must leave you now, but my father will pay you well for this.")
    it.chatPlayer("Go to Leela, she is close to here.")
    val princeAli = it.player.getInteractingNpc()
    princeAli.invisible = true
    princeAli.queue {
        wait(100)
        princeAli.invisible = false
    }
    it.messageBox("The prince has escaped, well done! You are now a friend of Al-Kharid and may pass through the Al-Kharid toll gate for free.")
}
