package gg.rsmod.plugins.content.items.food

import gg.rsmod.plugins.api.cfg.Items

/** OSRS Wiki Pineapple, Fruit and Rotten apple: advertised Eat does not always mean edible. */
object InedibleFoods {
    val refusals = mapOf(
        Items.PINEAPPLE to "You can't eat it whole; maybe you should cut it up.",
        Items.WATERMELON to "You can't eat it whole; maybe you should cut it up.",
        // OSRS explicitly does NOT consume or damage the player here (N11 card's harmful-food assumption was incorrect).
        Items.ROTTEN_APPLE to "Nothing interesting happens.",
    )
}
