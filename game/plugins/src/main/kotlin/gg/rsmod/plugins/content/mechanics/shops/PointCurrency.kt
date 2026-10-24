package gg.rsmod.plugins.content.mechanics.shops

import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.LOYALTY_POINTS
import gg.rsmod.game.model.attr.PointBalance
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.game.model.shop.ShopCurrency
import gg.rsmod.game.model.shop.ShopItem
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.format
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setComponentText
import mu.KLogging
import kotlin.math.floor
import kotlin.math.min

/**
 * R10.3: a real, separate point-currency shop needs its own balance, not
 * [LOYALTY_POINTS] under a different label - [balanceAttr] is that hook. Every currently
 * existing shop still uses [LoyaltyPointsCurrency] (the only subclass, wired to
 * [LOYALTY_POINTS] explicitly), so this is a genuine capability fix, not a behaviour change
 * for existing content: a future Slayer/PK/Void-points shop can now `PointCurrency("slayer
 * point", "slayer points", SLAYER_POINTS_ATTR)` and get a real independent balance instead of
 * silently sharing the loyalty one.
 *
 * @author Alycia <https://github.com/alycii>
 */
open class PointCurrency(
    val singularCurrency: String,
    private val pluralCurrency: String,
    private val balanceAttr: AttributeKey<Int> = LOYALTY_POINTS,
) : ShopCurrency {
    override fun onSellValueMessage(
        p: Player,
        shopItem: ShopItem,
        freeItem: Boolean,
    ) {
        val unnoted = Item(shopItem.item).toUnnoted(p.world.definitions)
        val value = shopItem.sellPrice ?: getSellPrice(p.world, unnoted.id)
        val name = unnoted.getName(p.world.definitions)
        val currency = if (value != 1) pluralCurrency else singularCurrency
        p.message("$name: currently costs ${value.format()} $currency.")
    }

    override fun onBuyValueMessage(
        p: Player,
        shop: Shop,
        item: Int,
    ) {
        p.message(CANT_SELL)
    }

    /**
     * R10.3: was a hard `TODO()` crash. Every real point shop in this codebase sets
     * [ShopItem.sellPrice] explicitly per item (the only real caller path, [sellToPlayer]/
     * [onSellValueMessage], only reaches this as a fallback when it's null) - a missing price
     * is a content-authoring gap, not something that should crash the player's shop
     * interaction. Logs a warning and treats it as free rather than throwing.
     */
    override fun getSellPrice(
        world: World,
        item: Int,
    ): Int {
        logger.warn { "PointCurrency.getSellPrice fallback hit for item $item - shop entry is missing an explicit sellPrice." }
        return 0
    }

    /** Point shops never buy (PurchasePolicy.BUY_NONE); 0 means "won't buy" to every caller. */
    override fun getBuyPrice(
        stock: Int,
        world: World,
        item: Int,
    ): Int = 0

    override fun sellToPlayer(
        p: Player,
        shop: Shop,
        slot: Int,
        amt: Int,
    ) {
        val shopItem = shop.items[slot] ?: return

        val currencyCost = shopItem.sellPrice ?: getSellPrice(p.world, shopItem.item)
        if (currencyCost <= 0) {
            // Finding 11 (audit): a missing/zero sellPrice used to fall through to
            // floor(balance / 0) below, which is either Infinity or NaN -> Int.MAX_VALUE or 0
            // after toInt(), letting any player with >0 points buy the full stock for free.
            // A content-authoring gap must never become a free-item exploit.
            p.message("This item is not currently available for purchase.")
            return
        }
        val currencyCount = p.attr[balanceAttr] ?: 0

        if (amt <= 0 || currencyCount <= 0) {
            p.notEnough("You don't have enough $pluralCurrency.")
            return
        }

        var amount = min(floor(currencyCount.toDouble() / currencyCost.toDouble()).toInt(), amt)

        if (amount <= 0) {
            p.notEnough("You don't have enough $pluralCurrency.")
            return
        }

        val moreThanStock = amount > shopItem.currentAmount

        amount = Math.min(amount, shopItem.currentAmount)

        if (amount <= 0) {
            p.filterableMessage("The shop has run out of stock.")
            return
        }

        if (moreThanStock) {
            p.filterableMessage("The shop has run out of stock.")
        }

        val totalCost = currencyCost.toLong() * amount.toLong()
        if (totalCost > Int.MAX_VALUE) {
            return
        }

        if (currencyCount < totalCost) {
            p.notEnough("You don't have enough $pluralCurrency.")
            return
        }

        // Debit first, but refund every uninserted unit below. This is a transaction: a full
        // inventory must not consume points, and a negative client amount must never credit them.
        // N106: through the one balance helper (never below 0; the check above guarantees the points are there).
        if (!PointBalance.take(p.attr, balanceAttr, totalCost.toInt())) {
            p.notEnough("You don't have enough $pluralCurrency.")
            return
        }

        val add = p.inventory.add(item = shopItem.item, amount = amount, assureFullInsertion = false)
        if (add.completed == 0) {
            p.message("You don't have enough inventory space.")
        }

        if (add.getLeftOver() > 0) {
            val refund = add.getLeftOver() * currencyCost
            PointBalance.add(p.attr, balanceAttr, refund)
        }

        if (add.completed > 0 && shopItem.amount != Int.MAX_VALUE) {
            shop.items[slot]!!.currentAmount -= add.completed

            /*
             * Check if the item is temporary and should be removed from the shop.
             */
            if (shop.items[slot]?.currentAmount == 0 && shop.items[slot]?.temporary == true) {
                shop.items[slot] = null
            }

            shop.refresh(p.world)
            showBalance(p, shop)
        }
    }

    /** "Breach Points" - the currency as a title. */
    val title: String get() = pluralCurrency.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

    fun balance(p: Player): Int = p.attr[balanceAttr] ?: 0

    /**
     * N275 (owner 2026-10-24, the Breach Trader showed no balance): the balance at the top of the shop window, in the title
     * ("Breach Trader - Breach Points: 1,234"), and on the window's help line. Set when the shop opens and after every purchase.
     */
    fun showBalance(
        p: Player,
        shop: Shop,
    ) {
        p.setComponentText(interfaceId = 620, component = 20, text = "${shop.name} - $title: <col=ffffff>${balance(p).format()}</col>")
        p.setComponentText(interfaceId = 620, component = 24, text = "You currently have ${balance(p).format()} $pluralCurrency.")
    }

    /** The chat line when the shop opens. */
    fun balanceMessage(p: Player): String = "You have ${balance(p).format()} $title."

    /*
     * Audit E-10: these used to throw. Selling an item to a point shop (Breach Trader) reached buyFromPlayer from the
     * inventory's Sell option and crashed the click; a refusal message is the retail behaviour.
     */
    override fun giveToPlayer(
        p: Player,
        shop: Shop,
        slot: Int,
        amt: Int,
    ) {
        p.message("This item is not currently available for purchase.")
    }

    override fun buyFromPlayer(
        p: Player,
        shop: Shop,
        slot: Int,
        amt: Int,
    ) {
        p.message(CANT_SELL)
    }

    override val currencyItem = -1

    companion object : KLogging() {
        const val CANT_SELL = "You can't sell this item to this shop."
    }
}
