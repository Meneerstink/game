package gg.rsmod.plugins.content.areas.varrock

/*
 * PROJECT48 run 4 O22: the Sawmill operator (4250, Varrock Lumber Yard) had a dead "Talk-to" and "Buy-plank" (only "Trade" =
 * Construction supplies, bulk_shops). OSRS Wiki 'Sawmill operator' + 'Transcript:Sawmill operator' (dialogue verbatim) and the
 * plank-making window of our own 667 cache (interface 403: components 12-15 = wood/oak/teak/mahogany with "Buy 1/5/10/X/All",
 * 20-23 = "Cost = 100/250/500/1500" - the same fees as the OSRS Wiki 'Sawmill' table).
 * Not enough logs: "You'll need to bring me some more logs."; not enough coins for the whole order: "Those planks cost [X] coins.
 * You don't have enough money for all of them." (nothing is made, as in the transcript).
 */

val SAWMILL_INTERFACE = 403

/** Component -> (log, plank, fee). */
val SAWMILL_PLANKS = mapOf(
    12 to Triple(Items.LOGS, Items.PLANK, 100),
    13 to Triple(Items.OAK_LOGS, Items.OAK_PLANK, 250),
    14 to Triple(Items.TEAK_LOGS, Items.TEAK_PLANK, 500),
    15 to Triple(Items.MAHOGANY_LOGS, Items.MAHOGANY_PLANK, 1500),
)

// Button opcodes as the client sends them (see pricecheck.plugin.kts): Buy 1, Buy 5, Buy 10, Buy X, Buy All.
val SAW_OP1 = 61
val SAW_OP2 = 64
val SAW_OP3 = 4
val SAW_OP4 = 52
val SAW_OP5 = 81

fun openSawmill(p: Player) {
    p.openInterface(interfaceId = SAWMILL_INTERFACE, dest = InterfaceDestination.MAIN_SCREEN)
    SAWMILL_PLANKS.keys.forEach { p.setInterfaceEvents(SAWMILL_INTERFACE, it, -1..-1, 0x3E) }
}

on_npc_option(Npcs.SAWMILL_OPERATOR, "talk-to") {
    player.queue {
        chatNpc(
            "Hello there. Do you want me to make some planks for you? I can make planks from wood, oak, teak and mahogany logs. " +
                "Or would you like to buy some other housing supplies?",
        )
        when (options("Yes, please make me some planks.", "Can I buy some housing supplies?", "I'm good, thanks.")) {
            1 -> {
                chatPlayer("Yes, please make me some planks.")
                openSawmill(player)
            }
            2 -> {
                chatPlayer("Can I buy some housing supplies?")
                chatNpc("Of course!")
                player.openShop("Construction supplies")
            }
            3 -> {
                chatPlayer("I'm good, thanks.")
                chatNpc("Well come back when you want some. You'll struggle to find quality planks anywhere but here!")
            }
        }
    }
}

on_npc_option(Npcs.SAWMILL_OPERATOR, "buy-plank") {
    openSawmill(player)
}

SAWMILL_PLANKS.forEach { (component, plank) ->
    on_button(interfaceId = SAWMILL_INTERFACE, component = component) {
        val (log, made, fee) = plank
        val want = when (player.getInteractingOpcode()) {
            SAW_OP1 -> 1
            SAW_OP2 -> 5
            SAW_OP3 -> 10
            SAW_OP5 -> Int.MAX_VALUE
            SAW_OP4 -> -1
            else -> return@on_button
        }
        // Close the window first: a STANDARD queue does not start while a modal interface is open (bot run 22-11 23:49: stuck queue).
        player.closeInterface(SAWMILL_INTERFACE)
        player.queue {
            val asked = if (want == -1) inputInt("Enter amount:") else want
            if (asked <= 0) return@queue
            val amount = minOf(asked, player.inventory.getItemCount(log))
            if (amount <= 0) {
                chatNpc("You'll need to bring me some more logs.", npc = Npcs.SAWMILL_OPERATOR)
                return@queue
            }
            val cost = amount.toLong() * fee
            if (player.inventory.getItemCount(Items.COINS_995) < cost) {
                chatNpc("Those planks cost $cost coins. You don't have enough money for all of them.", npc = Npcs.SAWMILL_OPERATOR)
                return@queue
            }
            // Remove first, then add exactly what was removed (no dupe on a full or changed inventory).
            val logs = player.inventory.remove(log, amount).completed
            if (logs <= 0) return@queue
            player.inventory.remove(Items.COINS_995, logs * fee)
            player.inventory.add(made, logs)
        }
    }
}

on_button(interfaceId = SAWMILL_INTERFACE, component = 29) {
    player.closeInterface(SAWMILL_INTERFACE)
}
