import gg.rsmod.plugins.content.skills.summoning.SummoningShopStock
import gg.rsmod.plugins.content.mechanics.shops.CoinCurrency
import gg.rsmod.game.model.shop.PurchasePolicy


create_shop("Pikkupstix's Summoning Shop", CoinCurrency(), purchasePolicy = PurchasePolicy.BUY_STOCK) {
    sampleItems[0] = ShopItem(Items.SPIRIT_SHARDS, 7)
    sampleItems[1] = ShopItem(Items.POUCH, 1)
    sampleItems[2] = ShopItem(Items.GOLD_CHARM, 1)
    sampleItems[3] = ShopItem(Items.WOLF_BONES, 1)

    items[0] = ShopItem(Items.ANTLERS, 10)
    items[1] = ShopItem(Items.LIZARD_SKULL, 10)
    items[2] = ShopItem(Items.FEATHER_HEADDRESS, 0)
    items[3] = ShopItem(Items.FEATHER_HEADDRESS_12213, 0)
    items[4] = ShopItem(Items.FEATHER_HEADDRESS_12216, 0)
    items[5] = ShopItem(Items.FEATHER_HEADDRESS_12219, 0)
    items[6] = ShopItem(Items.FEATHER_HEADDRESS_12222, 0)
    items[7] = ShopItem(Items.SPIRIT_SHARDS, 65000)
    items[8] = ShopItem(Items.POUCH, 5000)
    items[9] = ShopItem(Items.SPIRIT_SHARD_PACK, 65000)
    items[10] = ShopItem(Items.WOLF_BONES, 0)
}

create_shop("Summoning Supplies", CoinCurrency(), containsSamples = false, purchasePolicy = PurchasePolicy.BUY_STOCK) {
    items[0] = ShopItem(Items.ANTLERS, 10)
    items[1] = ShopItem(Items.LIZARD_SKULL, 10)
    items[2] = ShopItem(Items.FEATHER_HEADDRESS, 0)
    items[3] = ShopItem(Items.FEATHER_HEADDRESS_12213, 0)
    items[4] = ShopItem(Items.FEATHER_HEADDRESS_12216, 0)
    items[5] = ShopItem(Items.FEATHER_HEADDRESS_12219, 0)
    items[6] = ShopItem(Items.FEATHER_HEADDRESS_12222, 0)
    items[7] = ShopItem(Items.SPIRIT_SHARDS, 65000)
    items[8] = ShopItem(Items.POUCH, 5000)
    items[9] = ShopItem(Items.SPIRIT_SHARD_PACK, 65000)
}

// Owner 2026-10-03 (bug 210035): every familiar pouch and every scroll, split into shop windows that fit (SummoningShopStock).
SummoningShopStock.pages.forEach { page ->
    create_shop(
        page.title,
        CoinCurrency(),
        stockSize = gg.rsmod.game.model.shop.Shop.CLIENT_VISIBLE_SLOTS,
        containsSamples = false,
        purchasePolicy = PurchasePolicy.BUY_NONE,
    ) {
        page.stock.forEachIndexed { index, (item, amount) ->
            val price = SummoningShopStock.price(world.definitions, item)
            if (price <= 0) gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.ITEM) { "shop '${page.title}' item $item has no price" }
            items[index] = ShopItem(item, amount, sellPrice = price, resupplyAmount = maxOf(1, amount / 100))
        }
    }
}

on_npc_option(Npcs.PIKKUPSTIX, "trade") {
    player.queue {
        val choices = listOf("Summoning supplies.") + SummoningShopStock.pages.map { it.option }
        val choice = options(*choices.toTypedArray(), title = "What would you like to buy?")
        when {
            choice == 1 -> player.openShop("Pikkupstix's Summoning Shop")
            choice in 2..choices.size -> player.openShop(SummoningShopStock.pages[choice - 2].title)
        }
    }
}

on_npc_option(Npcs.BOGROG, "trade") {
    player.openShop("Summoning Supplies")
}

on_obj_option(Objs.WISHING_WELL, "make-wish") {
    player.openShop("Summoning Supplies")
}
