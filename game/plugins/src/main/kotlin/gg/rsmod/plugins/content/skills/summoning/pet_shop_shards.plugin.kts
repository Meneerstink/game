package gg.rsmod.plugins.content.skills.summoning

/*
 * "Sell-shards" on the Taverley and Yanille pet shop owners: "He sells pet dogs and buys spirit shards for 25 coins each" (RuneScape
 * Wiki "Pet shop owner"); conversation from the 667 Void donor (content/skill/summoning/pet/PetShopOwner.kt). The cache offered the
 * option with nothing bound.
 */
val SHARD_PRICE = 25

listOf(Npcs.PET_SHOP_OWNER, Npcs.PET_SHOP_OWNER_6893).forEach { owner ->
    on_npc_option(npc = owner, option = "sell-shards") {
        player.queue {
            chatPlayer("Are you interested in buying spirit shards?", facialExpression = FacialExpression.UNCERTAIN)
            chatNpc("I certainly am. Lots of them, too!")
            val held = player.inventory.getItemCount(Items.SPIRIT_SHARDS)
            if (held <= 0) {
                chatPlayer("Thanks, I'll bear that in mind.", facialExpression = FacialExpression.CALM_TALK)
                return@queue
            }
            val requested = inputInt("How many will you sell? ($SHARD_PRICE coins each, you have $held)")
            val toSell = minOf(requested, player.inventory.getItemCount(Items.SPIRIT_SHARDS))
            if (toSell <= 0) return@queue
            val payout = toSell.toLong() * SHARD_PRICE
            val coins = player.inventory.getItemCount(Items.COINS_995).toLong()
            if (coins + payout > Int.MAX_VALUE || (coins == 0L && player.inventory.isFull && held > toSell)) {
                player.message("You don't have enough inventory space.")
                return@queue
            }
            if (player.inventory.remove(Items.SPIRIT_SHARDS, toSell).hasSucceeded()) {
                player.inventory.add(Items.COINS_995, payout.toInt())
                messageBox("You sell $toSell spirit shard${if (toSell == 1) "" else "s"} for $payout coins.")
            }
        }
    }
}
