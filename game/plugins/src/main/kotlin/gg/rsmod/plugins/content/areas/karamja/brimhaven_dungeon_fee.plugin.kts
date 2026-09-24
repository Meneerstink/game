package gg.rsmod.plugins.content.areas.karamja

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports

/*
 * Brimhaven Dungeon entry fee (owner 2026-09-24: "brimhaven dungeon keep 875 coins"). OSRS Wiki "Saniboch": "875 coins to him to gain
 * entrance" - one payment is one entry. Dialogue lines from the 667 Void donor (content/area/karamja/brimhaven/Saniboch.kt). The entrance
 * (5083) itself stays the sourced object-teleport entry; this plugin only gates it.
 */
val ENTRY_FEE = 875
val ENTRANCE = 5083
val PAID = AttributeKey<Boolean>("brimhaven_dungeon_paid")

on_npc_option(npc = Npcs.SANIBOCH, option = "talk-to") {
    player.queue {
        chatNpc("Good day to you, Bwana.", npc = Npcs.SANIBOCH, facialExpression = FacialExpression.CALM_TALK)
        when (options("Can I go through that door please?", "Where does this strange entrance lead?", "Good day to you too.", "I'm impressed, that tree is growing on that shed.")) {
            1 -> {
                chatPlayer("Can I go through that door please?", facialExpression = FacialExpression.CALM_TALK)
                if (player.attr[PAID] == true) {
                    chatNpc("Most certainly, you have already given me lots of nice coins.", npc = Npcs.SANIBOCH, facialExpression = FacialExpression.CALM_TALK)
                    return@queue
                }
                chatNpc("Most certainly, but I must charge you the sum of 875 coins first.", npc = Npcs.SANIBOCH, facialExpression = FacialExpression.CALM_TALK)
                if (player.inventory.getItemCount(Items.COINS_995) < ENTRY_FEE) {
                    noMoney(this)
                    return@queue
                }
                when (options("Okay, here's 875 coins.", "Never mind.", "Why is it worth the entry cost?")) {
                    1 -> pay(this)
                    2 -> chatPlayer("Never mind.", facialExpression = FacialExpression.CALM_TALK)
                    3 -> {
                        chatPlayer("Why is it worth the entry cost?", facialExpression = FacialExpression.CALM_TALK)
                        whereItLeads(this)
                    }
                }
            }
            2 -> {
                chatPlayer("Where does this strange entrance lead?", facialExpression = FacialExpression.CALM_TALK)
                whereItLeads(this)
            }
            3 -> chatPlayer("Good day to you too.", facialExpression = FacialExpression.CALM_TALK)
            4 -> {
                chatPlayer("I'm impressed, that tree is growing on that shed.", facialExpression = FacialExpression.CALM_TALK)
                chatNpc("My employer tells me it is an uncommon sort of tree called the Fyburglars tree.", npc = Npcs.SANIBOCH, facialExpression = FacialExpression.CALM_TALK)
            }
        }
    }
}

on_npc_option(npc = Npcs.SANIBOCH, option = "pay") {
    player.queue {
        if (player.attr[PAID] == true) {
            chatNpc("You have already given me lots of nice coins, you may go in.", npc = Npcs.SANIBOCH, facialExpression = FacialExpression.CALM_TALK)
        } else if (player.inventory.getItemCount(Items.COINS_995) < ENTRY_FEE) {
            chatNpc("I'll want 875 coins to let you enter.", npc = Npcs.SANIBOCH, facialExpression = FacialExpression.CALM_TALK)
            noMoney(this)
        } else {
            pay(this)
        }
    }
}

on_obj_option(obj = ENTRANCE, option = "enter") {
    if (player.attr[PAID] != true) {
        player.queue { chatNpc("You can't go in there without paying!", npc = Npcs.SANIBOCH, facialExpression = FacialExpression.CALM_TALK) }
        return@on_obj_option
    }
    if (ObjectTeleports.tryTeleport(player, player.getInteractingGameObj(), player.getInteractingOption())) {
        player.attr.remove(PAID)
    }
}

suspend fun pay(task: QueueTask) {
    val player = task.player
    if (!player.inventory.remove(Items.COINS_995, ENTRY_FEE, assureFullRemoval = true).hasSucceeded()) return
    player.attr[PAID] = true
    task.messageBox("You pay Saniboch 875 coins.")
    task.chatNpc("Many thanks. You may now pass the door. May your death be a glorious one!", npc = Npcs.SANIBOCH, facialExpression = FacialExpression.CALM_TALK)
}

suspend fun noMoney(task: QueueTask) {
    task.chatPlayer("I don't have the money on me at the moment.", facialExpression = FacialExpression.CALM_TALK)
    task.chatNpc("Well this is a dungeon for the more wealthy discerning adventurer. Begone with you, riff raff.", npc = Npcs.SANIBOCH, facialExpression = FacialExpression.CALM_TALK)
    task.chatPlayer("But you don't even have clothes, how can you seriously call anyone riff raff.", facialExpression = FacialExpression.CALM_TALK)
    task.chatNpc("Hummph.", npc = Npcs.SANIBOCH, facialExpression = FacialExpression.CALM_TALK)
}

suspend fun whereItLeads(task: QueueTask) {
    task.chatNpc(
        "It leads to a huge fearsome dungeon, populated by giants and strange dogs. Adventurers come from all around to explore its depths.",
        npc = Npcs.SANIBOCH, facialExpression = FacialExpression.CALM_TALK,
    )
    task.chatNpc(
        "I know not what lies deeper in myself, for my skills in agility and woodcutting are inadequate, but I hear tell of even greater dangers deeper in.",
        npc = Npcs.SANIBOCH, facialExpression = FacialExpression.CALM_TALK,
    )
}
