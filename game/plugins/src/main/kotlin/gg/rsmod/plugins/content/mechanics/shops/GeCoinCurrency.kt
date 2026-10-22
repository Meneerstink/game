package gg.rsmod.plugins.content.mechanics.shops

import gg.rsmod.game.model.World
import gg.rsmod.game.model.shop.ShopItem
import gg.rsmod.plugins.content.mechanics.exchange.GePrice

/**
 * Coins at the Grand Exchange price of the moment (N191, owner 2026-10-22: "prices in quarter master shop ... need osrs prices just
 * like our grand exchange"): every row costs [GePrice.unit] - the OSRS hourly average, refreshed daily - instead of a price fixed
 * when the server started. High alchemy never pays more than that price (Alchemy.coins), so no alchemy floor is needed.
 */
class GeCoinCurrency : ItemCurrency(gg.rsmod.plugins.api.cfg.Items.COINS_995, singularCurrency = "coin", pluralCurrency = "coins") {
    override fun sellPriceOf(
        world: World,
        shopItem: ShopItem,
    ): Int {
        val ge = GePrice.unit(world, shopItem.item)
        return if (ge > 0) ge else super.sellPriceOf(world, shopItem)
    }
}
