package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Entry
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Kind
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Shop
import mu.KLogging
import java.io.File
import java.time.LocalDateTime

/**
 * The "78 Store" window (interface 1151, built by `StoreInterfaceImportTool`): kit grid, big preview of the ornamented result with a
 * carousel, base requirement, "cosmetic only" line, price and balance, and the Buy button. All state lives in transient attributes;
 * the currencies are persistent account attributes ([StoreCatalogue.Currency]).
 */
object StoreUi : KLogging() {
    const val INTERFACE_ID = 1151

    // Component ids - must match StoreInterfaceImportTool.
    const val TITLE = 12
    const val CLOSE = 13
    const val TAB_FIRST = 16
    const val TAB_STRIDE = 5
    const val SLOT_BACKGROUND_FIRST = 31
    const val SLOT_FIRST = 61
    const val SLOT_COUNT = 30
    const val PAGE_PREVIOUS = 91
    const val PAGE_TEXT = 92
    const val PAGE_NEXT = 93
    const val PREVIEW_MODEL = 97
    const val CAROUSEL_PREVIOUS = 98
    const val RESULT_NAME = 99
    const val CAROUSEL_NEXT = 100
    const val KIT_NAME = 101
    const val REQUIREMENT = 102
    const val COSMETIC = 103
    const val PRICE = 104
    const val BALANCE = 105
    const val BUY_LAYER = 106
    const val BUY_TEXT = 110
    const val EMPTY_TEXT = 111

    /** 667 ids of the loot-key button caps (LootKeyInterfaceImportTool.sprite: 7912 + index 21..26). */
    private const val GREY_LEFT = 7933
    private const val GREY_MIDDLE = 7934
    private const val GREY_RIGHT = 7935
    private const val RED_LEFT = 7936
    private const val RED_MIDDLE = 7937
    private const val RED_RIGHT = 7938

    private val SHOP_ATTR = AttributeKey<Int>()
    private val PAGE_ATTR = AttributeKey<Int>()
    private val SELECTED_ATTR = AttributeKey<Int>()
    private val CAROUSEL_ATTR = AttributeKey<Int>()

    private const val OP1 = 0x2
    private const val OP1_OP10 = 0x2 or 0x400

    fun shop(player: Player): Shop = Shop.values()[player.attr[SHOP_ATTR] ?: 0]

    fun entries(player: Player): List<Entry> = StoreCatalogue.entries(shop(player))

    fun isOpen(player: Player): Boolean = player.isInterfaceVisible(INTERFACE_ID)

    fun open(
        player: Player,
        shop: Shop,
    ) {
        player.attr[SHOP_ATTR] = shop.ordinal
        player.attr[PAGE_ATTR] = 0
        player.attr[SELECTED_ATTR] = 0
        player.attr[CAROUSEL_ATTR] = 0
        if (!isOpen(player)) {
            player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        }
        player.setInterfaceEvents(INTERFACE_ID, CLOSE, -1..-1, OP1)
        for (i in 0 until Shop.values().size) player.setInterfaceEvents(INTERFACE_ID, TAB_FIRST + i * TAB_STRIDE, -1..-1, OP1)
        for (slot in 0 until SLOT_COUNT) player.setInterfaceEvents(INTERFACE_ID, SLOT_FIRST + slot, -1..-1, OP1_OP10)
        listOf(PAGE_PREVIOUS, PAGE_NEXT, CAROUSEL_PREVIOUS, CAROUSEL_NEXT, BUY_LAYER).forEach { player.setInterfaceEvents(INTERFACE_ID, it, -1..-1, OP1) }
        render(player)
    }

    fun switchShop(
        player: Player,
        tab: Int,
    ) {
        if (tab !in Shop.values().indices) return
        open(player, Shop.values()[tab])
    }

    fun pages(player: Player): Int = maxOf(1, (entries(player).size + SLOT_COUNT - 1) / SLOT_COUNT)

    fun turnPage(
        player: Player,
        delta: Int,
    ) {
        val page = ((player.attr[PAGE_ATTR] ?: 0) + delta).coerceIn(0, pages(player) - 1)
        player.attr[PAGE_ATTR] = page
        render(player)
    }

    fun select(
        player: Player,
        slot: Int,
    ) {
        val index = (player.attr[PAGE_ATTR] ?: 0) * SLOT_COUNT + slot
        if (index !in entries(player).indices) return
        player.attr[SELECTED_ATTR] = index
        player.attr[CAROUSEL_ATTR] = 0
        renderPreview(player)
    }

    fun slotEntry(
        player: Player,
        slot: Int,
    ): Entry? = entries(player).getOrNull((player.attr[PAGE_ATTR] ?: 0) * SLOT_COUNT + slot)

    fun turnCarousel(
        player: Player,
        delta: Int,
    ) {
        val entry = selected(player) ?: return
        val size = entry.previewItems.size
        if (size <= 1) return
        player.attr[CAROUSEL_ATTR] = Math.floorMod((player.attr[CAROUSEL_ATTR] ?: 0) + delta, size)
        renderPreview(player)
    }

    fun selected(player: Player): Entry? = entries(player).getOrNull(player.attr[SELECTED_ATTR] ?: 0)

    private fun name(
        player: Player,
        item: Int,
    ): String = player.world.definitions.getNullable(ItemDef::class.java, item)?.name ?: "item $item"

    fun render(player: Player) {
        val shop = shop(player)
        val list = entries(player)
        val page = player.attr[PAGE_ATTR] ?: 0
        player.setComponentText(INTERFACE_ID, TITLE, "78 Store - ${shop.title}")
        Shop.values().forEachIndexed { i, s ->
            val base = TAB_FIRST + i * TAB_STRIDE
            val selected = s == shop
            player.setComponentSprite(INTERFACE_ID, base + 1, if (selected) RED_LEFT else GREY_LEFT)
            player.setComponentSprite(INTERFACE_ID, base + 2, if (selected) RED_MIDDLE else GREY_MIDDLE)
            player.setComponentSprite(INTERFACE_ID, base + 3, if (selected) RED_RIGHT else GREY_RIGHT)
        }
        for (slot in 0 until SLOT_COUNT) {
            val entry = list.getOrNull(page * SLOT_COUNT + slot)
            player.setComponentHidden(INTERFACE_ID, SLOT_BACKGROUND_FIRST + slot, entry == null)
            player.setComponentHidden(INTERFACE_ID, SLOT_FIRST + slot, entry == null)
            // IF_SETOBJECT with -1 dereferences a null ObjType in the 667 client: empty slots are hidden instead.
            if (entry != null) player.setComponentItem(INTERFACE_ID, SLOT_FIRST + slot, entry.purchaseItem, 1)
        }
        player.setComponentText(INTERFACE_ID, PAGE_TEXT, "Page ${page + 1} / ${pages(player)}")
        player.setComponentText(INTERFACE_ID, EMPTY_TEXT, if (list.isEmpty()) "This shop has no stock yet." else "")
        renderPreview(player)
    }

    fun renderPreview(player: Player) {
        val entry = selected(player)
        val currency = shop(player).currency
        val balance = player.attr[currency.attr] ?: 0
        player.setComponentText(INTERFACE_ID, BALANCE, "You have: ${balance.format()}")
        player.setComponentHidden(INTERFACE_ID, PREVIEW_MODEL, entry == null)
        if (entry == null) {
            listOf(RESULT_NAME, KIT_NAME, REQUIREMENT, COSMETIC, PRICE).forEach { player.setComponentText(INTERFACE_ID, it, "") }
            player.setComponentHidden(INTERFACE_ID, BUY_LAYER, true)
            return
        }
        val index = (player.attr[CAROUSEL_ATTR] ?: 0).coerceIn(0, entry.previewItems.size - 1)
        val preview = entry.previewItems[index]
        // The preview is the real ornamented/result item id; it is only drawn, never given.
        player.setComponentItem(INTERFACE_ID, PREVIEW_MODEL, preview, 1)
        val several = entry.previewItems.size > 1
        player.setComponentHidden(INTERFACE_ID, CAROUSEL_PREVIOUS, !several)
        player.setComponentHidden(INTERFACE_ID, CAROUSEL_NEXT, !several)
        player.setComponentText(INTERFACE_ID, RESULT_NAME, name(player, preview) + if (several) " (${index + 1}/${entry.previewItems.size})" else "")
        val tier = if (entry.tier.label.isNotEmpty()) " - ${entry.tier.label}" else ""
        player.setComponentText(INTERFACE_ID, KIT_NAME, name(player, entry.purchaseItem) + tier)
        val base = entry.requiredBaseItems.getOrNull(index)
        player.setComponentText(
            INTERFACE_ID,
            REQUIREMENT,
            when {
                base == null -> "No base item required"
                entry.kind == Kind.UPGRADE -> "Exchanges your ${name(player, base)}"
                else -> "Use on your ${name(player, base)}"
            },
        )
        player.setComponentText(
            INTERFACE_ID,
            COSMETIC,
            when (entry.kind) {
                Kind.KIT -> "Stats unchanged - cosmetic only"
                Kind.UPGRADE -> "Stats unchanged - base item required"
                Kind.ITEM -> entry.category
            },
        )
        player.setComponentText(INTERFACE_ID, PRICE, "Price: ${entry.price.format()} ${if (entry.price == 1) currency.singular else currency.plural}")
        player.setComponentHidden(INTERFACE_ID, BUY_LAYER, false)
        player.setComponentText(INTERFACE_ID, BUY_TEXT, if (entry.kind == Kind.KIT) "Buy kit" else "Buy")
    }

    /**
     * Buys the selected entry: all-or-nothing. The currency is only taken once the item is in the inventory (full inventory and a
     * missing base item leave the balance untouched), and every purchase is appended to `data/logs/store_purchases.log` so a
     * rollback can be traced.
     */
    fun buy(player: Player) {
        val entry = selected(player) ?: return
        val currency = entry.currency
        val balance = player.attr[currency.attr] ?: 0
        if (balance < entry.price) {
            player.message("You need ${entry.price.format()} ${currency.plural} to buy that; you have ${balance.format()}.")
            return
        }
        val bought: Boolean =
            when (entry.kind) {
                Kind.KIT, Kind.ITEM -> {
                    val result = player.inventory.add(entry.purchaseItem, 1, assureFullInsertion = true)
                    if (!result.hasSucceeded()) player.message("You don't have enough inventory space.")
                    result.hasSucceeded()
                }
                Kind.UPGRADE -> {
                    val base = entry.requiredBaseItems.first()
                    val slot = player.inventory.getItemIndex(base, skipAttrItems = false)
                    if (slot == -1) {
                        player.message("You need a ${name(player, base)} in your inventory for this upgrade.")
                        false
                    } else {
                        player.inventory[slot] = Item(entry.resultItems.first())
                        true
                    }
                }
            }
        if (!bought) return
        player.attr[currency.attr] = balance - entry.price
        audit(player, entry)
        player.message("You buy ${name(player, entry.purchaseItem)} for ${entry.price.format()} ${currency.plural}.")
        renderPreview(player)
    }

    private fun audit(
        player: Player,
        entry: Entry,
    ) {
        val line = "${LocalDateTime.now()}\t${player.username}\t${entry.shop}\t${entry.purchaseItem}\t${entry.price}\t${entry.currency}" +
            "\tbalance=${player.attr[entry.currency.attr] ?: 0}"
        runCatching {
            val file = File("data/logs/store_purchases.log")
            file.parentFile?.mkdirs()
            file.appendText(line + System.lineSeparator())
        }.onFailure { logger.error(it) { "Could not log store purchase: $line" } }
    }
}
