package gg.rsmod.plugins.content.areas.varrock

import gg.rsmod.plugins.content.mechanics.shops.CoinCurrency

/**
 * @author Alycia <https://github.com/alycii>
 */

create_shop(
    "Zaff's Superior Staves",
    CoinCurrency(),
    containsSamples = false,
    purchasePolicy = PurchasePolicy.BUY_STOCK,
) {
    items[0] = ShopItem(Items.STAFF, 10)
    items[1] = ShopItem(Items.MAGIC_STAFF, 10)
    items[2] = ShopItem(Items.STAFF_OF_AIR, 10)
    items[3] = ShopItem(Items.STAFF_OF_WATER, 10)
    items[4] = ShopItem(Items.STAFF_OF_EARTH, 10)
    items[5] = ShopItem(Items.STAFF_OF_FIRE, 10)
}

on_npc_option(npc = Npcs.ZAFF, option = "Trade") {
    player.openShop("Zaff's Superior Staves")
}

/**
 * DIARIES-OSRS Varrock rewards (OSRS Wiki "Varrock Diary"): Zaff sells 15 / 30 / 60 / 120 noted battlestaves as one stack every day
 * (00:00 UTC) after the easy / medium / hard / elite claim, for 7,000 coins each. Coins go first, then exactly that stack (no dupe on a
 * full inventory: the noted stack needs one free slot or an existing noted stack).
 */
val ZAFF_STAVES_DAY = gg.rsmod.game.model.attr.AttributeKey<Int>(persistenceKey = "diary_zaff_staves_day")
val ZAFF_STAVE_PRICE = 7_000

suspend fun gg.rsmod.game.model.queue.QueueTask.zaffBattlestaves() {
    val claimed = gg.rsmod.plugins.content.diaries.Diaries.claimed(player, gg.rsmod.plugins.content.diaries.Diaries.Diary.VARROCK)
    val amount = when (claimed) { 1 -> 15; 2 -> 30; 3 -> 60; 4 -> 120; else -> 0 }
    if (amount == 0) return
    val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay().toInt()
    if (player.attr[ZAFF_STAVES_DAY] == today) {
        chatNpc("I've already sold you your battlestaves for today. Come back tomorrow.")
        return
    }
    val cost = amount * ZAFF_STAVE_PRICE
    if (options("Yes, $amount for ${"%,d".format(cost)} coins.", "No, thanks.", title = "Buy $amount noted battlestaves?") != 1) return
    val noted = Items.BATTLESTAFF + 1
    if (player.inventory.getItemCount(Items.COINS_995) < cost) {
        chatNpc("You don't have enough coins for that.")
        return
    }
    if (player.inventory.freeSlotCount == 0 && !player.inventory.contains(noted)) {
        chatNpc("You don't have room for them.")
        return
    }
    if (!player.inventory.remove(Items.COINS_995, cost).hasSucceeded()) return
    player.attr[ZAFF_STAVES_DAY] = today
    player.inventory.add(noted, amount)
    gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.ITEM) { "zaff-battlestaves ${player.username} $amount cost=$cost" }
    chatNpc("Here you are. $amount battlestaves.")
}

on_npc_option(npc = Npcs.ZAFF, "talk-to") {
    player.queue {
        chatNpc("Would you like to buy or sell some staffs?")
        val diary = gg.rsmod.plugins.content.diaries.Diaries.claimed(player, gg.rsmod.plugins.content.diaries.Diaries.Diary.VARROCK) > 0
        val pick = if (diary) options("Yes, please!", "Can I buy some battlestaves?", "No, thank you.", title = "Select an Option")
            else options("Yes, please!", "No, thank you.", title = "Select an Option").let { if (it == 2) 3 else it }
        when (pick) {
            2 -> zaffBattlestaves()
            1 -> {
                player.openShop("Zaff's Superior Staves")
            }
            3 -> {
                chatPlayer("No, thank you.")
                chatNpc("Well, 'stick' your head in again if you change", "your mind.")
                chatPlayer("Huh, terrible pun! You just can't get the 'staff' these", "days!")
            }
        }
    }
}
