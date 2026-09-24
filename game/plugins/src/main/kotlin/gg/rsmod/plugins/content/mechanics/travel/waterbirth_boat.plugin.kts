package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction

/*
 * Rellekka <-> Waterbirth Island (Dagannoth Kings) with Jarvald. The Rellekka Jarvald is transform npc 2435 (varbit 814 = varp 520 bit
 * 13): 0 shows 2436 (Talk-to only), 1 shows 2437 with "Travel-Waterbirth". Nothing set the bit and neither travel option was bound, so
 * Waterbirth Island had no way in. OSRS Wiki "Jarvald": the trip costs 1,000 coins before The Fremennik Trials (quests are parked here,
 * so always 1,000); the return is free. Lines from OSRS Wiki "Transcript:Jarvald". Arrival tiles beside each Jarvald: ADAPTED.
 */
val JARVALD_TRAVEL_VARBIT = 814
val WATERBIRTH_FEE = 1_000
val WATERBIRTH = Tile(2544, 3759, 0)
val RELLEKKA_JARVALD = Tile(2620, 3684, 0)

on_login {
    player.setVarbit(JARVALD_TRAVEL_VARBIT, 1)
}

on_npc_option(npc = Npcs.JARVALD_2437, option = "travel-waterbirth") {
    player.queue {
        chatNpc("I will allow you to escort us, but you must pay me a sum of money first.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Let us say... 1,000 coins. Payable in advance, of course.", facialExpression = FacialExpression.CALM_TALK)
        if (options("YES", "NO") != 1) return@queue
        if (!player.inventory.remove(Items.COINS_995, WATERBIRTH_FEE, assureFullRemoval = true).hasSucceeded()) {
            player.message("You don't have enough coins.")
            return@queue
        }
        voyage(player, WATERBIRTH, "I suggest you head to the cave with some urgency outerlander, the cold air out here might be too much for the likes of you...", Npcs.JARVALD_2438)
    }
}

on_npc_option(npc = Npcs.JARVALD_2438, option = "travel-rellekka") {
    player.queue {
        chatPlayer("I wish to return to Rellekka.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Then let us away; There will be death to bring here another day!", facialExpression = FacialExpression.CALM_TALK)
        voyage(player, RELLEKKA_JARVALD, null, -1)
    }
}

fun voyage(player: Player, destination: Tile, arrivalLine: String?, arrivalNpc: Int) {
    DeadmanTimerGate.requestRoute(player, SevenSecondAction.Kind.TRANSPORT) {
        player.lockingQueue {
            player.openInterface(115, InterfaceDestination.MAIN_SCREEN_FULL)
            wait(3)
            player.moveTo(destination)
            player.openInterface(170, InterfaceDestination.MAIN_SCREEN_FULL)
            wait(2)
            player.closeMainInterface()
            if (arrivalLine != null) chatNpc(arrivalLine, npc = arrivalNpc, facialExpression = FacialExpression.CALM_TALK)
        }
    }
}
