package gg.rsmod.plugins.content.areas.karamja

import gg.rsmod.game.model.Tile

/*
 * Brimhaven <-> Shilo Village travel cart (667 cache: Hajedy 510 / Vigroy 511 "Pay-fare", travel carts 2230 / 2265 "Board" and
 * "Pay-fare" - none were bound). Fare, destinations, dialogue and messages from the 667 Void donor (content/area/karamja/brimhaven/
 * Hajedy.kt, shilo_village/Vigroy.kt): 10 coins, a monkey steals any Karamjan rum on the way.
 */
val CART_FARE = 10
val BRIMHAVEN = Tile(2778, 3210)
val SHILO_VILLAGE = Tile(2834, 2951)
val FADE_IN = 115
val FADE_OUT = 170

data class Cart(val driver: Int, val cart: Int, val destination: Tile, val destinationName: String)

val CARTS =
    listOf(
        Cart(Npcs.HAJEDY, Objs.TRAVEL_CART, SHILO_VILLAGE, "Shilo Village"),
        Cart(Npcs.VIGROY, Objs.TRAVEL_CART_2265, BRIMHAVEN, "Brimhaven"),
    )

CARTS.forEach { cart ->
    on_npc_option(npc = cart.driver, option = "pay-fare") {
        player.queue { ride(this, cart, offer = false) }
    }
    on_obj_option(obj = cart.cart, option = "board") {
        player.queue { ride(this, cart, offer = true) }
    }
    on_obj_option(obj = cart.cart, option = "pay-fare") {
        player.queue { ride(this, cart, offer = false) }
    }
}

suspend fun ride(task: QueueTask, cart: Cart, offer: Boolean) {
    val player = task.player
    if (offer) {
        task.chatNpc(
            "I am offering a cart ride to ${cart.destinationName}, if you're interested. It will cost $CART_FARE coins. Is that okay?",
            npc = cart.driver,
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (task.options("Yes please, I'd like to go to ${cart.destinationName}.", "No thanks.") != 1) {
            task.chatPlayer("No thanks.", facialExpression = FacialExpression.CALM_TALK)
            task.chatNpc("Okay, Bwana, let me know if you change your mind.", npc = cart.driver, facialExpression = FacialExpression.CALM_TALK)
            return
        }
        task.chatPlayer("Yes please, I'd like to go to ${cart.destinationName}.", facialExpression = FacialExpression.CALM_TALK)
    }
    if (!player.inventory.remove(Items.COINS_995, CART_FARE, assureFullRemoval = true).hasSucceeded()) {
        task.chatNpc(
            "Sorry, but it looks as if you don't have enough money. Come and see me when you have enough for the ride.",
            npc = cart.driver,
            facialExpression = FacialExpression.CALM_TALK,
        )
        return
    }
    player.lockingQueue {
        player.openInterface(FADE_IN, InterfaceDestination.MAIN_SCREEN_FULL)
        wait(3)
        val rum = player.inventory.getItemCount(Items.KARAMJAN_RUM)
        if (rum > 0) {
            player.inventory.remove(Items.KARAMJAN_RUM, rum)
            player.message("Oh no! On the trip a monkey swung out from the trees and stole your rum!")
        }
        player.moveTo(cart.destination)
        player.openInterface(FADE_OUT, InterfaceDestination.MAIN_SCREEN_FULL)
        wait(2)
        player.closeMainInterface()
        if (offer) {
            messageBox(
                "You hop into the cart and the driver urges the horses on. You take a taxing journey through the jungle to ${cart.destinationName}. " +
                    "You feel tired from the journey, but at least you didn't have to walk all that distance.",
            )
        } else {
            player.filterableMessage("You feel tired from the journey, but at least you didn't have to walk all that distance.")
        }
    }
}
