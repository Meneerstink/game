package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.message.impl.UpdateStockmarketSlotMessage
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.INVENTORY_INTERFACE_KEY
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.api.ext.playJingle
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.sendItemContainer
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.api.ext.setVarp
import kotlin.math.ceil

/** The offer being built on the buy/sell page of the Grand Exchange screen. */
data class GeSelection(
    val slot: Int,
    val type: OfferType,
    var itemId: Int = -1,
    var quantity: Int = 0,
    var price: Int = 1,
    var guide: Int = 0,
)

/**
 * RCV-010 C3: the native revision-667 Grand Exchange screens (main 105, sell inventory 107, collection box 109, chatbox
 * item search 389), on top of the existing escrow-safe [GrandExchangeService].
 *
 * Sources: every clickable component id and its op label is read from the 667 cache (interface 105/107/109 layout;
 * `GrandExchangeInterfaceTests` re-proves each one); the slot packet layout and status bits come from the client's own
 * `StockmarketOffer` and `ScriptRunner` (0 empty, 1 adding, 2 stable, 5 finished, +8 sell); varps 1109-1114, scripts
 * 570/571/149 and the collect/abort rules come from Novite 667 `GrandExchangeListener`/`ExchangeManagement` and agree
 * with Void (`GrandExchangeControls`/`Confirm`/`Collection`, varps 1109-1116); the ±5 % range and its `ceil` bounds are
 * Void's `selectItem`.
 */
object GrandExchangeInterface {
    const val MAIN = 105
    const val SELL_INVENTORY = 107
    const val COLLECTION_BOX = 109
    const val ITEM_SEARCH = 389

    const val VARP_ITEM = 1109
    const val VARP_QUANTITY = 1110
    const val VARP_PRICE = 1111
    const val VARP_SLOT = 1112
    const val VARP_PAGE = 1113
    const val VARP_GUIDE = 1114
    const val WARNING_CONTAINER = 196
    const val WARNING_DISMISS = 220

    /** The item description under the item name on the buy/sell page (Void `grand_exchange:examine`). */
    const val EXAMINE_TEXT = 143

    const val SCRIPT_ITEM_SEARCH = 570
    const val SCRIPT_CLOSE_SEARCH = 571
    const val SCRIPT_INVENTORY_OPTIONS = 149

    val VIEW_OFFER = intArrayOf(19, 35, 51, 70, 89, 108)
    val MAKE_BUY = intArrayOf(31, 47, 63, 82, 101, 120)
    val MAKE_SELL = intArrayOf(32, 48, 64, 83, 102, 121)
    const val BACK = 128
    const val DECREASE_QUANTITY = 155
    const val INCREASE_QUANTITY = 157
    const val ADD_1 = 160
    const val ADD_10 = 162
    const val ADD_100 = 164
    const val ADD_1000 = 166
    const val EDIT_QUANTITY = 168
    const val DECREASE_PRICE = 169
    const val INCREASE_PRICE = 171
    const val OFFER_GUIDE_PRICE = 175
    const val EDIT_PRICE = 177
    const val PLUS_FIVE_PERCENT = 179
    const val MINUS_FIVE_PERCENT = 181
    const val CONFIRM = 186
    const val CHOOSE_ITEM = 190
    const val ABORT = 200
    val COLLECT = intArrayOf(206, 208)
    const val SELL_INVENTORY_ITEMS = 18
    val COLLECTION_BOX_OFFERS = intArrayOf(19, 23, 27, 32, 37, 42)

    /** Client container key of offer box `slot`'s two collect items (Novite `sendItems(523 + slot)`). */
    fun collectContainerKey(slot: Int) = 523 + slot

    const val STATE_EMPTY = 0
    const val STATE_ADDING = 1
    const val STATE_STABLE = 2
    const val STATE_FINISHED = 5
    const val SELL_BIT = 8

    const val OPCODE_OP1 = 61
    const val OPCODE_OP2 = 64

    val SELECTION_ATTR = AttributeKey<GeSelection>()

    const val MSG_CHOOSE_FIRST = "You must choose an item first."
    const val MSG_NOT_TRADEABLE = "You can't trade that item on the Grand Exchange."
    const val MSG_TOO_VALUABLE = "The total value of your offer cannot exceed 2147m coins."
    const val MSG_ABORT = "Abort request acknowledged. Please be aware that your offer may have already been completed."
    const val MSG_UPDATED = "One or more of your Grand Exchange offers have been updated."
    const val MSG_NO_SPACE = "Not enough space in your inventory."
    const val MSG_ITEMS_WAITING = "You have items waiting in your Grand Exchange collection box!"

    fun service(player: Player): GrandExchangeService? = player.world.getService(GrandExchangeService::class.java)

    fun username(player: Player): String = (player as Client).loginUsername

    /** The status byte the client reads with `& 0x8` (type) and `& 0x7` (state). */
    fun status(offer: GrandExchangeOffer?): Int {
        if (offer == null) return STATE_EMPTY
        val sell = if (offer.type == OfferType.SELL) SELL_BIT else 0
        return sell or if (offer.status == OfferStatus.ACTIVE) STATE_STABLE else STATE_FINISHED
    }

    fun slotMessage(
        slot: Int,
        offer: GrandExchangeOffer?,
    ): UpdateStockmarketSlotMessage =
        if (offer == null) {
            UpdateStockmarketSlotMessage(slot, STATE_EMPTY, 0, 0, 0, 0, 0)
        } else {
            // OSRS shows the coins actually exchanged (a buy filled below its offer price shows the lower total);
            // offers saved before coinsTraded existed fall back to the offer price.
            val traded = if (offer.coinsTraded > 0 || offer.quantityFilled == 0) offer.coinsTraded else offer.quantityFilled.toLong() * offer.pricePerItem
            val gold = minOf(traded, Int.MAX_VALUE.toLong()).toInt()
            UpdateStockmarketSlotMessage(slot, status(offer), offer.itemId, offer.pricePerItem, offer.totalQuantity, offer.quantityFilled, gold)
        }

    /**
     * Void `selectItem`: `ceil(guide * 0.95)..ceil(guide * 1.05)`. No longer an offer limit (OSRS prices are free); it
     * bounds how far one trade may move the guide price ([GrandExchangeService.guidedTradePrice]) and how far the guide
     * may move per hour ([GeGuidePrice]).
     */
    fun priceRange(guide: Int): IntRange = ceil(guide * 0.95).toInt()..ceil(guide * 1.05).toInt()

    /**
     * The quantity after pressing [component]. Novite 667: on a buy offer the Add buttons add, on a sell offer they set
     * the amount (the last one selling everything owned); a sell offer can never ask for more than [owned].
     */
    fun adjustQuantity(
        selection: GeSelection,
        component: Int,
        owned: Int,
    ): Int {
        val sell = selection.type == OfferType.SELL
        val current = selection.quantity.toLong()
        val next =
            when (component) {
                DECREASE_QUANTITY -> current - 1
                INCREASE_QUANTITY -> current + 1
                ADD_1 -> if (sell) 1L else current + 1
                ADD_10 -> if (sell) 10L else current + 10
                ADD_100 -> if (sell) 100L else current + 100
                ADD_1000 -> if (sell) owned.toLong() else current + 1000
                else -> current
            }
        val max = if (sell) owned.toLong() else Int.MAX_VALUE.toLong()
        return next.coerceIn(0L, max).toInt()
    }

    /** The price after pressing [component]; the -5%/+5% buttons move the current price by 5 %. */
    fun adjustPrice(
        selection: GeSelection,
        component: Int,
    ): Int {
        val price = selection.price
        val next =
            when (component) {
                DECREASE_PRICE -> price - 1
                INCREASE_PRICE -> price + 1
                PLUS_FIVE_PERCENT -> ceil(price + price * 0.05).toInt()
                MINUS_FIVE_PERCENT -> ceil(price - price * 0.05).toInt()
                OFFER_GUIDE_PRICE -> selection.guide
                else -> price
            }
        return clampPrice(selection, next)
    }

    /**
     * OSRS Grand Exchange: an offer may name any price of at least 1 coin (no ±5 % lock around the guide price, which
     * was the 2011 rule). The house deals only at its fixed quote ([GeHousePricing], bid <= ask), so a free price
     * cannot be turned into a money loop.
     */
    @Suppress("UNUSED_PARAMETER")
    fun clampPrice(
        selection: GeSelection,
        price: Int,
    ): Int = price.coerceAtLeast(1)

    /** Items that may be offered: tradeable, not coins, never the noted form (offers are made for the real item), never a removed item. */
    fun exchangeable(def: ItemDef): Boolean =
        def.tradeable && !def.noted && def.id != Items.COINS_995 && !gg.rsmod.plugins.content.mechanics.removed.RemovedItems.isRemoved(def)

    /** The unnoted id of [def]. */
    fun unnoted(def: ItemDef): Int = if (def.noted) def.noteLinkId else def.id

    fun validate(selection: GeSelection?): String? {
        if (selection == null || selection.itemId == -1) return MSG_CHOOSE_FIRST
        if (selection.quantity < 1 || selection.price < 1) return MSG_CHOOSE_FIRST
        if (selection.price.toLong() * selection.quantity > Int.MAX_VALUE) return MSG_TOO_VALUABLE
        return null
    }

    /** The two collect items of an offer box: index 0 is the item stock, index 1 the coins. */
    fun collectItems(
        definitions: DefinitionSet,
        offer: GrandExchangeOffer?,
    ): ItemContainer {
        val container = ItemContainer(definitions, 2, ContainerStackType.STACK)
        if (offer != null) {
            if (offer.collectableItems > 0) container[0] = gg.rsmod.game.model.item.Item(offer.itemId, offer.collectableItems)
            if (offer.collectableCoins > 0) {
                container[1] = gg.rsmod.game.model.item.Item(Items.COINS_995, minOf(offer.collectableCoins, Int.MAX_VALUE.toLong()).toInt())
            }
        }
        return container
    }

    /**
     * Novite/Void collect rule: op1 takes a stack of more than one as notes and a single item as the item, op2 the other
     * way round. Items without a noted form always come as themselves.
     */
    fun collectedId(
        def: ItemDef,
        amount: Int,
        op1: Boolean,
    ): Int {
        val wantNotes = (amount > 1 && op1) || (amount == 1 && !op1)
        return if (wantNotes && !def.noted && !def.stackable && def.noteLinkId > 0) def.noteLinkId else def.id
    }

    /* ----------------------------------------- player-facing flow ----------------------------------------- */

    fun refresh(
        player: Player,
        service: GrandExchangeService,
        slot: Int,
    ) = player.write(slotMessage(slot, service.offerInSlot(username(player), slot)))

    fun refreshAll(
        player: Player,
        service: GrandExchangeService,
    ) = (0 until GrandExchangeService.SLOTS).forEach { refresh(player, service, it) }

    private fun resetConfigs(player: Player) {
        player.setVarp(VARP_ITEM, -1)
        player.setVarp(VARP_QUANTITY, 0)
        player.setVarp(VARP_PRICE, 1)
        player.setVarp(VARP_SLOT, -1)
        player.setVarp(VARP_PAGE, -1)
        player.setVarp(VARP_GUIDE, 0)
    }

    fun sendSelection(
        player: Player,
        selection: GeSelection,
    ) {
        player.setVarp(VARP_SLOT, selection.slot)
        player.setVarp(VARP_PAGE, selection.type.ordinal)
        player.setVarp(VARP_ITEM, selection.itemId)
        player.setVarp(VARP_QUANTITY, selection.quantity)
        player.setVarp(VARP_PRICE, selection.price)
        player.setVarp(VARP_GUIDE, selection.guide)
        hideGuidePriceWarning(player)
    }

    /** Void `selectItem`: the chosen item's description under its name. */
    fun sendExamine(
        player: Player,
        itemId: Int,
    ) {
        val text = if (itemId == -1) "" else player.world.definitions.getNullable(ItemDef::class.java, itemId)?.examine ?: ""
        player.setComponentText(MAIN, EXAMINE_TEXT, text)
    }

    /**
     * Keeps the cache's "far less than its guide price" panel off the screen (owner 2026-09-20: "everytime i try
     * to sell an item i get the popup ... remove this"). The panel blocked every sell; the owner removed it.
     */
    fun hideGuidePriceWarning(player: Player) {
        player.setComponentHidden(interfaceId = MAIN, component = WARNING_CONTAINER, hidden = true)
    }

    /** Novite `ExchangeManagement.sendSummary`: the six offer boxes. */
    fun openMain(player: Player) {
        val service = service(player) ?: return
        player.attr.remove(SELECTION_ATTR)
        resetConfigs(player)
        player.openInterface(MAIN, InterfaceDestination.MAIN_SCREEN)
        // The client script shows the "far less than its guide price" warning (container 196); its Dismiss button (layer 220,
        // label component 0 in the rev-667 cache) only receives clicks once op1 events are enabled for it (owner 2026-09-18:
        // the warning could not be dismissed, blocking every sell).
        player.setInterfaceEvents(interfaceId = MAIN, component = WARNING_DISMISS, range = -1..-1, setting = 2)
        hideGuidePriceWarning(player)
        refreshAll(player, service)
    }

    fun beginBuy(
        player: Player,
        slot: Int,
    ) {
        val service = service(player) ?: return
        if (service.offerInSlot(username(player), slot) != null) return
        val selection = GeSelection(slot, OfferType.BUY)
        player.attr[SELECTION_ATTR] = selection
        sendSelection(player, selection)
        sendExamine(player, -1)
        openItemSearch(player)
    }

    fun openItemSearch(player: Player) {
        player.openInterface(parent = 752, child = 7, interfaceId = ITEM_SEARCH, type = 1)
        player.runClientScript(SCRIPT_ITEM_SEARCH, "Grand Exchange Item Search")
    }

    fun beginSell(
        player: Player,
        slot: Int,
    ) {
        val service = service(player) ?: return
        if (service.offerInSlot(username(player), slot) != null) return
        val selection = GeSelection(slot, OfferType.SELL)
        player.attr[SELECTION_ATTR] = selection
        sendSelection(player, selection)
        sendExamine(player, -1)
        player.openInterface(SELL_INVENTORY, InterfaceDestination.TAB_AREA)
        player.runClientScript(SCRIPT_INVENTORY_OPTIONS, (SELL_INVENTORY shl 16) or SELL_INVENTORY_ITEMS, INVENTORY_INTERFACE_KEY, 4, 7, 0, -1, "Offer", "", "", "", "")
        player.setInterfaceEvents(interfaceId = SELL_INVENTORY, component = SELL_INVENTORY_ITEMS, range = 0..27, setting = 1026)
        player.sendItemContainer(INVENTORY_INTERFACE_KEY, player.inventory)
    }

    /** Picks [itemId] for the current buy/sell selection (item search result or sell-inventory click). */
    fun select(
        player: Player,
        itemId: Int,
        quantity: Int = 1,
    ) {
        val service = service(player) ?: return
        val selection = player.attr[SELECTION_ATTR] ?: return
        val def = player.world.definitions.getNullable(ItemDef::class.java, itemId) ?: return
        val real = player.world.definitions.getNullable(ItemDef::class.java, unnoted(def)) ?: return
        if (!exchangeable(real)) {
            player.playSound(GrandExchangeSounds.TRADE_ERROR)
            player.message(MSG_NOT_TRADEABLE)
            return
        }
        selection.itemId = real.id
        selection.guide = service.guidePrice(real.id, OsrsGuidePrices.seed(real))
        selection.price = clampPrice(selection, selection.guide)
        // Void `stock_side:items` / OSRS: a sell offer starts at the clicked stack's amount (a whole stack or note pile).
        selection.quantity = if (selection.type == OfferType.SELL) quantity.coerceIn(1, ownedCount(player, real.id).coerceAtLeast(1)) else 1
        sendSelection(player, selection)
        sendExamine(player, real.id)
    }

    fun ownedCount(
        player: Player,
        itemId: Int,
    ): Int {
        val def = player.world.definitions.getNullable(ItemDef::class.java, itemId) ?: return 0
        val noted = if (!def.noted && def.noteLinkId > 0) player.inventory.getItemCount(def.noteLinkId) else 0
        return minOf(player.inventory.getItemCount(itemId).toLong() + noted, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Inventory forms removed for a sell offer must be restored in their original forms when the
     * service rejects the submit (for example because the selected slot was taken meanwhile).
     * The offer itself remains unnoted, but a failed transaction must not silently turn notes into
     * ordinary items.
     */
    fun restoredSellItems(
        def: ItemDef,
        unnotedRemoved: Int,
        notedRemoved: Int,
    ): List<Pair<Int, Int>> =
        buildList {
            if (unnotedRemoved > 0) add(def.id to unnotedRemoved)
            if (notedRemoved > 0 && def.noteLinkId > 0) add(def.noteLinkId to notedRemoved)
        }

    /** Novite/Void confirm: escrow the coins or items first, then submit; anything refused is handed straight back. */
    fun confirm(player: Player): Boolean {
        val placed = placeOffer(player)
        if (!placed) player.playSound(GrandExchangeSounds.TRADE_ERROR)
        return placed
    }

    /** Messages why an offer could not be placed; [confirm] plays GE_TRADE_ERROR for every refusal (2009scape). */
    private fun placeOffer(player: Player): Boolean {
        val service = service(player) ?: return false
        val selection = player.attr[SELECTION_ATTR]
        validate(selection)?.let {
            player.message(it)
            return false
        }
        selection!!
        val username = username(player)
        if (service.offerInSlot(username, selection.slot) != null) return false
        val fills: List<GeFill>
        if (selection.type == OfferType.BUY) {
            // Long: price * quantity overflowed Int for large offers, and a negative "total" passed
            // the coin check and then *added* coins on remove.
            val totalCost = selection.price.toLong() * selection.quantity
            if (totalCost <= 0 || totalCost > Int.MAX_VALUE) {
                player.message(MSG_TOO_VALUABLE)
                return false
            }
            val total = totalCost.toInt()
            if (player.inventory.getItemCount(Items.COINS_995) < total) {
                player.message("You don't have enough coins.")
                return false
            }
            if (player.inventory.remove(Items.COINS_995, total, assureFullRemoval = true).hasFailed()) {
                return false
            }
            // Audit E-08: player save (with a pending marker) before the book write, see GeEscrow.
            val submitted =
                GeEscrow.place(player, listOf(Items.COINS_995 to total), restore = { player.inventory.add(Items.COINS_995, total) }) { token ->
                    service.submit(username, OfferType.BUY, selection.itemId, selection.price, selection.quantity, selection.slot, token)
                } ?: return false
            fills = submitted.second
        } else {
            if (ownedCount(player, selection.itemId) < selection.quantity) {
                player.message("You do not have enough of this item in your inventory to cover the offer.")
                return false
            }
            val def = player.world.definitions.get(ItemDef::class.java, selection.itemId)
            val unnotedRemoved = player.inventory.remove(selection.itemId, selection.quantity, assureFullRemoval = false).completed
            val notedRemoved =
                if (unnotedRemoved < selection.quantity && def.noteLinkId > 0) {
                    player.inventory.remove(def.noteLinkId, selection.quantity - unnotedRemoved, assureFullRemoval = false).completed
                } else {
                    0
                }
            val removed = unnotedRemoved + notedRemoved
            val restore = {
                restoredSellItems(def, unnotedRemoved, notedRemoved).forEach { (itemId, amount) ->
                    player.inventory.add(itemId, amount)
                }
            }
            if (removed < selection.quantity) {
                restore()
                return false
            }
            // Audit E-08: player save (with a pending marker) before the book write, see GeEscrow.
            val submitted =
                GeEscrow.place(player, restoredSellItems(def, unnotedRemoved, notedRemoved), restore) { token ->
                    service.submit(username, OfferType.SELL, selection.itemId, selection.price, selection.quantity, selection.slot, token)
                } ?: return false
            fills = submitted.second
            player.closeInterface(dest = InterfaceDestination.TAB_AREA)
        }
        player.attr.remove(SELECTION_ATTR)
        resetConfigs(player)
        sendExamine(player, -1)
        player.runClientScript(SCRIPT_CLOSE_SEARCH)
        player.playSound(GrandExchangeSounds.PLACE_ITEM)
        refreshAll(player, service)
        announceFills(player.world, service, fills)
        // The debited inventory was saved before the book write and again after it (GeEscrow.place).
        return true
    }

    /**
     * OSRS offer update message for [offer]: "Grand Exchange: Finished buying 10 x Iron ore." once complete, otherwise
     * "Grand Exchange: Bought 4 / 10 x Iron ore." (selling: "Finished selling" / "Sold").
     */
    fun progressMessage(
        offer: GrandExchangeOffer,
        itemName: String,
    ): String {
        val buy = offer.type == OfferType.BUY
        return if (offer.remaining <= 0) {
            "Grand Exchange: Finished ${if (buy) "buying" else "selling"} ${offer.totalQuantity} x $itemName."
        } else {
            "Grand Exchange: ${if (buy) "Bought" else "Sold"} ${offer.quantityFilled} / ${offer.totalQuantity} x $itemName."
        }
    }

    /**
     * Tells every online owner of an offer that just traded: the offer boxes refresh, one message per offer and the
     * offer-updated jingle once (2009scape `GrandExchangeTimer`). Offline owners hear about it at login ([onLogin]).
     */
    fun announceFills(
        world: gg.rsmod.game.model.World,
        service: GrandExchangeService,
        fills: List<GeFill>,
    ) {
        if (fills.isEmpty()) return
        val offerIds = fills.flatMap { listOfNotNull(it.buyOfferId, it.sellOfferId) }.distinct()
        val byOwner = offerIds.mapNotNull { service.offer(it) }.groupBy { it.username }
        byOwner.forEach { (owner, offers) ->
            val player = world.players.firstOrNull { (it as? Client)?.loginUsername == owner } ?: return@forEach
            refreshAll(player, service)
            offers.forEach { offer ->
                val name = player.world.definitions.getNullable(ItemDef::class.java, offer.itemId)?.name ?: "item"
                player.message(progressMessage(offer, name))
            }
            player.playJingle(GrandExchangeSounds.OFFER_UPDATED_JINGLE)
        }
    }

    /** Void `GrandExchange.login` / 2009scape `GrandExchangeRecords`: boxes refresh; anything owed is announced. */
    fun onLogin(player: Player) {
        val service = service(player) ?: return
        // Audit E-08: settle an offer placement a crash interrupted, before anything else can touch the book.
        GeEscrow.reconcile(player, service, username(player))
        refreshAll(player, service)
        val waiting = service.offersFor(username(player)).any { it.collectableCoins > 0 || it.collectableItems > 0 }
        if (waiting) {
            player.message(MSG_ITEMS_WAITING)
            player.playJingle(GrandExchangeSounds.OFFER_UPDATED_JINGLE)
        }
    }

    fun viewOffer(
        player: Player,
        slot: Int,
    ) {
        val service = service(player) ?: return
        val offer = service.offerInSlot(username(player), slot) ?: return
        player.attr.remove(SELECTION_ATTR)
        player.setVarp(VARP_PAGE, offer.type.ordinal)
        player.setVarp(VARP_SLOT, slot)
        player.sendItemContainer(collectContainerKey(slot), collectItems(player.world.definitions, offer))
        COLLECT.forEach { player.setInterfaceEvents(interfaceId = MAIN, component = it, range = -1..-1, setting = 6) }
    }

    fun abort(
        player: Player,
        slot: Int,
    ) {
        val service = service(player) ?: return
        val offer = service.offerInSlot(username(player), slot) ?: return
        service.cancel(username(player), offer.id)
        player.message(MSG_ABORT)
        refresh(player, service, slot)
        player.sendItemContainer(collectContainerKey(slot), collectItems(player.world.definitions, service.offerInSlot(username(player), slot)))
    }

    /** Collects index 0 (items) or 1 (coins) of offer box [slot]; the slot is freed once the offer is drained. */
    fun collect(
        player: Player,
        slot: Int,
        index: Int,
        op1: Boolean,
    ): Boolean {
        val service = service(player) ?: return false
        val username = username(player)
        val offer = service.offerInSlot(username, slot) ?: return false
        val coins = index == 1
        val owed = service.takePart(username, offer.id, coins)
        if (owed <= 0) return false
        val itemId = if (coins) Items.COINS_995 else offer.itemId
        val amount = minOf(owed, Int.MAX_VALUE.toLong()).toInt()
        val def = player.world.definitions.get(ItemDef::class.java, itemId)
        val deliverId = if (coins) itemId else collectedId(def, amount, op1)
        val added = player.inventory.add(deliverId, amount, assureFullInsertion = false).completed
        val leftover = owed - added
        if (leftover > 0) {
            service.restoreCollectable(offer.id, if (coins) leftover else 0, if (coins) 0 else leftover.toInt())
            player.message(MSG_NO_SPACE)
        }
        // 2009scape collect: GE_COLLECT_COINS / GE_COLLECT_ITEMS, GE_TRADE_ERROR when nothing fitted.
        player.playSound(
            when {
                added <= 0 -> GrandExchangeSounds.TRADE_ERROR
                coins -> GrandExchangeSounds.COLLECT_COINS
                else -> GrandExchangeSounds.COLLECT_ITEMS
            },
        )
        service.releaseIfDrained(username, offer.id)
        refresh(player, service, slot)
        player.sendItemContainer(collectContainerKey(slot), collectItems(player.world.definitions, service.offerInSlot(username, slot)))
        if (added > 0) player.persistNow()
        return added > 0
    }

    fun back(player: Player) {
        player.attr.remove(SELECTION_ATTR)
        resetConfigs(player)
        sendExamine(player, -1)
        player.runClientScript(SCRIPT_CLOSE_SEARCH)
        player.closeInterface(dest = InterfaceDestination.TAB_AREA)
        player.openInterface(dest = InterfaceDestination.INVENTORY_TAB)
        service(player)?.let { refreshAll(player, it) }
    }

    /** Novite `ExchangeManagement.openCollectionBox`: the collection box (109) with every box's two items. */
    fun openCollectionBox(player: Player) {
        val service = service(player) ?: return
        player.openInterface(COLLECTION_BOX, InterfaceDestination.MAIN_SCREEN)
        for (slot in 0 until GrandExchangeService.SLOTS) {
            refresh(player, service, slot)
            player.sendItemContainer(collectContainerKey(slot), collectItems(player.world.definitions, service.offerInSlot(username(player), slot)))
            player.setInterfaceEvents(interfaceId = COLLECTION_BOX, component = COLLECTION_BOX_OFFERS[slot], range = 0..2, setting = 6)
        }
    }
}
