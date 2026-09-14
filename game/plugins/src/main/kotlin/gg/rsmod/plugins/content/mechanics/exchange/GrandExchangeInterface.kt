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
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.sendItemContainer
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
            val gold = minOf(offer.quantityFilled.toLong() * offer.pricePerItem, Int.MAX_VALUE.toLong()).toInt()
            UpdateStockmarketSlotMessage(slot, status(offer), offer.itemId, offer.pricePerItem, offer.totalQuantity, offer.quantityFilled, gold)
        }

    /** Void `selectItem`: the offer price must stay within `ceil(guide * 0.95)..ceil(guide * 1.05)`. */
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

    /** The price after pressing [component], always kept inside [priceRange] of the selection's guide price. */
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

    fun clampPrice(
        selection: GeSelection,
        price: Int,
    ): Int {
        val range = priceRange(selection.guide)
        return price.coerceIn(range.first, range.last)
    }

    /** Items that may be offered: tradeable, not coins, and never the noted form (offers are made for the real item). */
    fun exchangeable(def: ItemDef): Boolean = def.tradeable && !def.noted && def.id != Items.COINS_995

    /** The unnoted id of [def]. */
    fun unnoted(def: ItemDef): Int = if (def.noted) def.noteLinkId else def.id

    fun validate(selection: GeSelection?): String? {
        if (selection == null || selection.itemId == -1) return MSG_CHOOSE_FIRST
        if (selection.quantity < 1 || selection.price < 1) return MSG_CHOOSE_FIRST
        if (selection.price.toLong() * selection.quantity > Int.MAX_VALUE) return MSG_TOO_VALUABLE
        if (selection.price !in priceRange(selection.guide)) return MSG_CHOOSE_FIRST
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
    }

    /** Novite `ExchangeManagement.sendSummary`: the six offer boxes. */
    fun openMain(player: Player) {
        val service = service(player) ?: return
        player.attr.remove(SELECTION_ATTR)
        resetConfigs(player)
        player.openInterface(MAIN, InterfaceDestination.MAIN_SCREEN)
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
        player.openInterface(SELL_INVENTORY, InterfaceDestination.TAB_AREA)
        player.runClientScript(SCRIPT_INVENTORY_OPTIONS, (SELL_INVENTORY shl 16) or SELL_INVENTORY_ITEMS, INVENTORY_INTERFACE_KEY, 4, 7, 0, -1, "Offer", "", "", "", "")
        player.setInterfaceEvents(interfaceId = SELL_INVENTORY, component = SELL_INVENTORY_ITEMS, range = 0..27, setting = 1026)
        player.sendItemContainer(INVENTORY_INTERFACE_KEY, player.inventory)
    }

    /** Picks [itemId] for the current buy/sell selection (item search result or sell-inventory click). */
    fun select(
        player: Player,
        itemId: Int,
    ) {
        val service = service(player) ?: return
        val selection = player.attr[SELECTION_ATTR] ?: return
        val def = player.world.definitions.getNullable(ItemDef::class.java, itemId) ?: return
        val real = player.world.definitions.getNullable(ItemDef::class.java, unnoted(def)) ?: return
        if (!exchangeable(real)) {
            player.message(MSG_NOT_TRADEABLE)
            return
        }
        selection.itemId = real.id
        selection.guide = service.guidePrice(real.id, OsrsGuidePrices.seed(real))
        selection.price = clampPrice(selection, selection.guide)
        selection.quantity = 1
        sendSelection(player, selection)
    }

    fun ownedCount(
        player: Player,
        itemId: Int,
    ): Int {
        val def = player.world.definitions.getNullable(ItemDef::class.java, itemId) ?: return 0
        val noted = if (!def.noted && def.noteLinkId > 0) player.inventory.getItemCount(def.noteLinkId) else 0
        return minOf(player.inventory.getItemCount(itemId).toLong() + noted, Int.MAX_VALUE.toLong()).toInt()
    }

    /** Novite/Void confirm: escrow the coins or items first, then submit; anything refused is handed straight back. */
    fun confirm(player: Player): Boolean {
        val service = service(player) ?: return false
        val selection = player.attr[SELECTION_ATTR]
        validate(selection)?.let {
            player.message(it)
            return false
        }
        selection!!
        val username = username(player)
        if (service.offerInSlot(username, selection.slot) != null) return false
        if (selection.type == OfferType.BUY) {
            val total = selection.price * selection.quantity
            if (player.inventory.getItemCount(Items.COINS_995) < total) {
                player.message("You don't have enough coins.")
                return false
            }
            player.inventory.remove(Items.COINS_995, total, assureFullRemoval = true)
            if (service.submit(username, OfferType.BUY, selection.itemId, selection.price, selection.quantity, selection.slot) == null) {
                player.inventory.add(Items.COINS_995, total)
                return false
            }
        } else {
            if (ownedCount(player, selection.itemId) < selection.quantity) return false
            val def = player.world.definitions.get(ItemDef::class.java, selection.itemId)
            var removed = player.inventory.remove(selection.itemId, selection.quantity, assureFullRemoval = false).completed
            if (removed < selection.quantity && def.noteLinkId > 0) {
                removed += player.inventory.remove(def.noteLinkId, selection.quantity - removed, assureFullRemoval = false).completed
            }
            if (removed < selection.quantity ||
                service.submit(username, OfferType.SELL, selection.itemId, selection.price, selection.quantity, selection.slot) == null
            ) {
                player.inventory.add(selection.itemId, removed)
                return false
            }
            player.closeInterface(dest = InterfaceDestination.TAB_AREA)
        }
        player.attr.remove(SELECTION_ATTR)
        resetConfigs(player)
        player.runClientScript(SCRIPT_CLOSE_SEARCH)
        refreshAll(player, service)
        notifyCounterparties(player, service)
        return true
    }

    /** Refreshes the boxes of every other online owner whose offer just traded against this one. */
    private fun notifyCounterparties(
        player: Player,
        service: GrandExchangeService,
    ) {
        val self = username(player)
        player.world.players.forEach { other ->
            if (other == player) return@forEach
            val name = (other as? Client)?.loginUsername ?: return@forEach
            if (name == self) return@forEach
            val changed = service.offersFor(name).any { it.quantityFilled > 0 || it.status != OfferStatus.ACTIVE }
            if (changed) refreshAll(other, service)
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
        service.releaseIfDrained(username, offer.id)
        refresh(player, service, slot)
        player.sendItemContainer(collectContainerKey(slot), collectItems(player.world.definitions, service.offerInSlot(username, slot)))
        return added > 0
    }

    fun back(player: Player) {
        player.attr.remove(SELECTION_ATTR)
        resetConfigs(player)
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
