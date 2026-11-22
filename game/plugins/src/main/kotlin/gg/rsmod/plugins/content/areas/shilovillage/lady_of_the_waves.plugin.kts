package gg.rsmod.plugins.content.areas.shilovillage

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction
import gg.rsmod.plugins.content.quests.foundation.bracket.ShiloVillage

/**
 * PROJECT48 run 4 O19: the Lady of the Waves (Shilo Village unlock). Seravel (514, upstairs in Fernahei's hut) sells ship tickets for
 * 25 coins; Captain Shanks (518, on the ship south of Cairn Isle) sails a ticket holder one way to Port Khazard or Port Sarim, or sells
 * a ticket himself for a price that changes every talk (20-50 coins).
 *
 * Sources: OSRS Wiki "Lady of the Waves", "Ship ticket", "Transcript:Captain Shanks", "Transcript:Seravel" (all lines below verbatim,
 * incl. the rum-dice message). Arrival tiles = the charter ship docks of Port Khazard / Port Sarim (areas\portsarim\Ports.kt); the
 * fade = the charter fade interfaces 115/170. Before Shilo Village both npcs only give their 'zombies' line, as in OSRS.
 */
val FADE_IN = 115
val FADE_OUT = 170
val SERAVEL_PRICE = 25
val KHAZARD = Tile(2674, 3144)
val SARIM = Tile(3038, 3192)

fun shiloDone(player: Player): Boolean = ShiloVillage.isFinished(player)

on_npc_option(npc = Npcs.SERAVEL, option = "Talk-to") {
    player.queue {
        if (!shiloDone(player)) {
            chatNpc("I'm getting out of here Bwana, the place is infested with zombies.", npc = Npcs.SERAVEL)
            return@queue
        }
        chatPlayer("Hello")
        chatNpc("Hello Bwana. Are you interested in buying a ticket for the 'Lady of the Waves'?", npc = Npcs.SERAVEL)
        var pick = options("Yes, that sounds great!", "No thanks.", "Tell me more about the ship.")
        if (pick == 3) {
            chatNpc(
                "It's a ship that can take you to either Port Sarim or Port Khazard. The ship lies west of Shilo Village and south of " +
                    "Cairn Island. The tickets cost 25 coins. Would you like to purchase a ticket Bwana?",
                npc = Npcs.SERAVEL,
            )
            pick = options("Yes, that sounds great!", "No thanks.")
        }
        when (pick) {
            1 -> {
                chatPlayer("Yes, that sounds great!")
                if (player.inventory.getItemCount(Items.COINS_995) < SERAVEL_PRICE) {
                    chatNpc("Sorry Bwana, you don't have enough money. Come back when you have 25 coins.", npc = Npcs.SERAVEL)
                    return@queue
                }
                if (player.inventory.remove(Items.COINS_995, SERAVEL_PRICE).hasSucceeded()) {
                    player.inventory.add(Items.SHIP_TICKET, 1)
                }
                chatNpc("Great, nice doing business with you.", npc = Npcs.SERAVEL)
            }
            2 -> {
                chatPlayer("No thanks.")
                chatNpc("Fair enough Bwana, let me know if you change your mind.", npc = Npcs.SERAVEL)
            }
        }
    }
}

on_npc_option(npc = Npcs.CAPTAIN_SHANKS, option = "Talk-to") {
    player.queue {
        val n = Npcs.CAPTAIN_SHANKS
        if (!shiloDone(player)) {
            chatNpc(
                "Oh dear, this ship is in a terrible state. And I just can't get the items I need to repair it because Shilo village is " +
                    "overrun with zombies.",
                npc = n,
            )
            return@queue
        }
        chatNpc("Hello there shipmate! I sail to Khazard Port and to Port Sarim. Where are you bound?", npc = n)
        if (!player.inventory.contains(Items.SHIP_TICKET)) {
            val price = world.random(20..50)
            chatNpc(
                "I see you don't have a ticket for the ship, my colleague normally only sells them in Shilo village. But I could sell you " +
                    "one for a small additional charge. Shall we say $price coins?",
                npc = n,
            )
            when (options("Yes, I'll buy a ticket for the ship.", "No thanks, not just at the moment.", title = "Buy a ticket for $price coins?")) {
                1 -> {
                    chatPlayer("Yes, I'll buy a ticket for the ship.")
                    if (player.inventory.getItemCount(Items.COINS_995) < price ||
                        !player.inventory.remove(Items.COINS_995, price).hasSucceeded()
                    ) {
                        chatNpc(
                            "Sorry me old shipmate, but you seem to be financially challenged at the moment. Come back when your coffers are full!",
                            npc = n,
                        )
                        return@queue
                    }
                    player.inventory.add(Items.SHIP_TICKET, 1)
                    chatNpc("It's a good deal and no mistake. Here you go, me old shipmate, here's your ticket.", npc = n)
                    chatNpc("Okay. Now you have your ticket, do you want to sail anywhere?", npc = n)
                }
                else -> {
                    chatPlayer("No thanks, not just at the moment.")
                    chatNpc("Very well, me old shipmate. Come back if you change your mind now.", npc = n)
                    return@queue
                }
            }
        } else {
            chatNpc("Where would you like to go?", npc = n)
        }
        when (options("Khazard Port please.", "Port Sarim please.", "Nowhere just at the moment thanks.")) {
            1 -> {
                chatPlayer("Khazard Port please.")
                chatNpc("Very well then me old shipmate, I'll just take your ticket and then we'll set sail.", npc = n)
                sail(player, KHAZARD, "You arrive safely in the quiet fishing port of Khazard.")
            }
            2 -> {
                chatPlayer("Port Sarim please.")
                chatNpc("Very well then me old shipmate, I'll just take your ticket and then we'll set sail.", npc = n)
                sail(player, SARIM, "The ship arrives in busy Port Sarim.")
            }
            else -> {
                chatPlayer("Nowhere just at the moment thanks.")
                chatNpc("Very well then me old shipmate, Just let me know if you change your mind.", npc = n)
            }
        }
    }
}

/** The one-way trip: the ticket is taken when the countdown lets the ship leave (a refused trip keeps the ticket). */
fun sail(player: Player, dest: Tile, arrival: String) {
    DeadmanTimerGate.requestRoute(player, SevenSecondAction.Kind.TRANSPORT) {
        if (!player.inventory.remove(Items.SHIP_TICKET, 1).hasSucceeded()) return@requestRoute
        player.lockingQueue(TaskPriority.WEAK) {
            player.openInterface(FADE_IN, InterfaceDestination.MAIN_SCREEN_FULL)
            wait(4)
            player.openInterface(FADE_OUT, InterfaceDestination.MAIN_SCREEN_FULL)
            player.moveTo(dest)
            val rum = player.inventory.remove(Items.KARAMJAN_RUM, 28)
            if (rum.getLeftOver() < 28) {
                player.message("During the trip you lose your rum to a sailor in a game of dice. Better luck next time!")
            }
            wait(3)
            player.closeMainInterface()
            player.queue(TaskPriority.WEAK) { messageBox(arrival) }
        }
    }
}
