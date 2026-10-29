package gg.rsmod.plugins.content.areas.kandarin.castlewars

import gg.rsmod.plugins.content.mechanics.shops.ItemCurrency

/**
 * K07 N412 part 2: Lanthus' Castle Wars ticket exchange with the OSRS stock and prices (OSRS Wiki "Lanthus"): decorative armour red
 * (this cache: "Basic"), white ("Detailed") and gold ("Intricate") - helm 4/40/400, platebody 8/80/800, platelegs 6/60/600, sword
 * 5/50/500, shield 6/60/600 - the team hoods and cloaks 10 and the three halos 75. The same title as the generated wiki shop
 * (wiki_shops.plugin.kts, RuneScape stock), which therefore steps aside: its RuneScape-only rows (profound decorative armour,
 * ballista parts, Castle Wars achievement capes, faithful shield, halos at 300) are not OSRS. This cache has no decorative full helm,
 * plateskirt, boots, magic or ranged armour, or team banners.
 */
val LANTHUS_SHOP = "Castle Wars Ticket Exchange"

create_shop(
    LANTHUS_SHOP,
    ItemCurrency(Items.CASTLE_WARS_TICKET, "Castle wars ticket", "Castle wars tickets"),
    containsSamples = false,
    purchasePolicy = PurchasePolicy.BUY_NONE,
) {
    val stock =
        listOf(
            // Red / Basic
            4071 to 4, 4069 to 8, 4070 to 6, 4068 to 5, 4072 to 6,
            // White / Detailed
            4506 to 40, 4504 to 80, 4505 to 60, 4503 to 50, 4507 to 60,
            // Gold / Intricate
            4511 to 400, 4509 to 800, 4510 to 600, 4508 to 500, 4512 to 600,
            // Team hoods and cloaks
            4513 to 10, 4514 to 10, 4515 to 10, 4516 to 10,
            // Halos: Saradomin, Zamorak, Guthix
            18745 to 75, 18746 to 75, 18744 to 75,
        )
    stock.forEachIndexed { i, (item, price) -> items[i] = ShopItem(item, 1000, sellPrice = price) }
}

on_npc_option(Npcs.LANTHUS, option = "trade-with") { player.openShop(LANTHUS_SHOP) }
