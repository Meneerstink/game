package gg.rsmod.game.model.shop

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player

/**
 * Represents the currency exchange when performing transactions in a [Shop].
 *
 * @author Tom <rspsmods@gmail.com>
 */
interface ShopCurrency {
    /**
     * Called when a player selects the "value" option a [ShopItem].
     */
    fun onSellValueMessage(
        p: Player,
        shopItem: ShopItem,
        freeItem: Boolean,
    )

    /**
     * Called when a player selects the "value" option on one of their own
     * inventory items.
     */
    fun onBuyValueMessage(
        p: Player,
        shop: Shop,
        item: Int,
    )

    /**
     * Get the price at which the shop will sell [item] for.
     */
    fun getSellPrice(
        world: World,
        item: Int,
    ): Int

    /**
     * Get the price at which the shop will buy [item] for.
     */
    fun getBuyPrice(
        stock: Int = 0,
        world: World,
        item: Int,
    ): Int

    /**
     * N42/N114: what one [shopItem] costs, as the shop window shows it under the cell ([Shop.PRICE_CONTAINER]) - the same price
     * the purchase charges. 0 or less = not for sale.
     */
    fun priceOf(
        world: World,
        shopItem: ShopItem,
    ): Int = shopItem.sellPrice ?: getSellPrice(world, shopItem.item)

    /**
     * Called when a player attempts to buy a [ShopItem].
     */
    fun sellToPlayer(
        p: Player,
        shop: Shop,
        slot: Int,
        amt: Int,
    )

    /**
     * Called when a player attempts to take a free [ShopItem].
     */
    fun giveToPlayer(
        p: Player,
        shop: Shop,
        slot: Int,
        amt: Int,
    )

    /**
     * Called when a player attempts to sell an inventory item to the shop.
     */
    fun buyFromPlayer(
        p: Player,
        shop: Shop,
        slot: Int,
        amt: Int,
    )

    /**
     * The currency item of the shop
     */
    val currencyItem: Int
}
