package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.tools.importer.StoreInterfaceImportTool as Ui
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
    // Component ids come straight from the tool that builds the window, so the two can never drift apart. Ids before the grid
    // are the same in every layout; the grid and everything after it come from [layout].
    const val INTERFACE_ID = Ui.INTERFACE_ID
    const val CLOSE = Ui.CLOSE
    const val TAB_FIRST = Ui.TAB_FIRST
    const val TAB_STRIDE = Ui.TAB_STRIDE
    const val BALANCE = Ui.BALANCE
    const val BALANCE_ICON = Ui.BALANCE_ICON

    @Volatile
    private var detected: Ui.Layout? = null

    /**
     * The grid layout the cache holds (owner 2026-10-03: 7x6 in a larger window, applied by `StoreInterfaceImportTool --apply`
     * while the servers are down). Read once from interface [INTERFACE_ID]'s component count, so this server build drives the
     * old 6x5 window correctly until the cache is updated, and the new one after.
     */
    fun layout(world: World): Ui.Layout =
        detected ?: run {
            val count = runCatching { world.filestore.index(INTERFACE_INDEX).archive(INTERFACE_ID)?.fileIds()?.size ?: 0 }.getOrDefault(0)
            val layout = Ui.layoutFor(count)
            if (layout !== Ui.CURRENT) {
                gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.INTERFACE) {
                    "store interface $INTERFACE_ID has $count components, not the ${Ui.CURRENT.componentCount} of the 7x6 layout: the cache still " +
                        "holds the old 6x5 store window - run StoreInterfaceImportTool --apply with the servers stopped"
                }
            }
            logger.info { "Store window: interface $INTERFACE_ID has $count components -> ${layout.columns}x${layout.rows} grid" }
            detected = layout
            layout
        }

    private const val INTERFACE_INDEX = 3

    fun layout(player: Player): Ui.Layout = layout(player.world)

    /** Every component a click can arrive on, in any layout (the plugin binds them all and [click] dispatches). */
    val CLICKABLE: Set<Int> =
        listOf(Ui.CURRENT, Ui.LEGACY).flatMap { l ->
            listOf(CLOSE, l.pagePrevious, l.pageNext, l.carouselPrevious, l.carouselNext, l.buyLayer) +
                (0 until Shop.values().size).map { TAB_FIRST + it * TAB_STRIDE } +
                (0 until l.slotCount).map { l.slotFirst + it }
        }.toSortedSet()

    /** A click on [component] of the store window, routed by the live layout. */
    fun click(
        player: Player,
        component: Int,
        opcode: Int,
    ) {
        val l = layout(player)
        val tab = (component - TAB_FIRST) / TAB_STRIDE
        when {
            component == CLOSE -> player.closeInterface(INTERFACE_ID)
            component >= TAB_FIRST && (component - TAB_FIRST) % TAB_STRIDE == 0 && tab in Shop.values().indices -> switchShop(player, tab)
            component in l.slotFirst until l.slotFirst + l.slotCount -> {
                val slot = component - l.slotFirst
                if (opcode == 25) {
                    slotEntry(player, slot)?.let { player.world.sendExamine(player, it.purchaseItem, gg.rsmod.game.model.ExamineEntityType.ITEM) }
                } else {
                    select(player, slot)
                }
            }
            component == l.pagePrevious -> turnPage(player, -1)
            component == l.pageNext -> turnPage(player, 1)
            component == l.carouselPrevious -> turnCarousel(player, -1)
            component == l.carouselNext -> turnCarousel(player, 1)
            component == l.buyLayer -> buy(player)
        }
    }

    /** Header tagline per shop (tab order). */
    private val TAGLINES =
        mapOf(
            Shop.DONATOR to "Premium cosmetics - Donator Points from the 78 website",
            Shop.DEADMAN to "Earned in Deadman combat - never for sale",
            Shop.LOYALTY to "Earned by playing - online time, dailies and events",
        )

    /** Tier label colours (text <col> tags): Basic silver, Premium green, Elite blue, Prestige purple. */
    private val TIER_COLOURS =
        mapOf(
            StoreCatalogue.Tier.BASIC to "c0c0c0",
            StoreCatalogue.Tier.PREMIUM to "3cd33c",
            StoreCatalogue.Tier.ELITE to "3fa7ff",
            StoreCatalogue.Tier.PRESTIGE to "c77dff",
        )

    /** The store's painted art (StoreArtTool): tabs, Buy button and currency icons, per shop in tab order. */
    private val Art = gg.rsmod.game.tools.importer.StoreArtTool

    private val SHOP_ATTR = AttributeKey<Int>()
    private val PAGE_ATTR = AttributeKey<Int>()
    private val SELECTED_ATTR = AttributeKey<Int>()
    private val CAROUSEL_ATTR = AttributeKey<Int>()

    /** Catalogue index the player pressed Buy on once; the second press on the same entry buys (spending points is final). */
    private val CONFIRM_ATTR = AttributeKey<Int>()

    private const val OP1 = 0x2
    private const val OP1_OP10 = 0x2 or 0x400

    fun shop(player: Player): Shop = Shop.values()[player.attr[SHOP_ATTR] ?: 0]

    fun entries(player: Player): List<Entry> = StoreCatalogue.entries(shop(player))

    fun isOpen(player: Player): Boolean = player.isInterfaceVisible(INTERFACE_ID)

    fun open(
        player: Player,
        shop: Shop,
    ) {
        // Bug search 2026-10-02: the store is reachable without walking to it (`store`/`deadmanstore`/... commands, the
        // Deadman's skull "Shop" option) and sells real PvP gear, so it was a mid-fight restock anywhere. The shared
        // remote-service rule applies to every route (safe zone, out of combat); the store npcs stand in safe zones.
        if (gg.rsmod.plugins.content.mechanics.pvp.RemoteAccess.refuse(player, "use the store")) return
        player.attr[SHOP_ATTR] = shop.ordinal
        player.attr[PAGE_ATTR] = 0
        player.attr[SELECTED_ATTR] = 0
        player.attr[CAROUSEL_ATTR] = 0
        player.attr.remove(CONFIRM_ATTR)
        if (!isOpen(player)) {
            player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        }
        val l = layout(player)
        player.setInterfaceEvents(INTERFACE_ID, CLOSE, -1..-1, OP1)
        for (i in 0 until Shop.values().size) player.setInterfaceEvents(INTERFACE_ID, TAB_FIRST + i * TAB_STRIDE, -1..-1, OP1)
        for (slot in 0 until l.slotCount) player.setInterfaceEvents(INTERFACE_ID, l.slotFirst + slot, -1..-1, OP1_OP10)
        listOf(l.pagePrevious, l.pageNext, l.carouselPrevious, l.carouselNext, l.buyLayer).forEach { player.setInterfaceEvents(INTERFACE_ID, it, -1..-1, OP1) }
        render(player)
    }

    fun switchShop(
        player: Player,
        tab: Int,
    ) {
        if (tab !in Shop.values().indices) return
        open(player, Shop.values()[tab])
    }

    fun slotCount(player: Player): Int = layout(player).slotCount

    fun pages(player: Player): Int = maxOf(1, (entries(player).size + slotCount(player) - 1) / slotCount(player))

    fun turnPage(
        player: Player,
        delta: Int,
    ) {
        val page = ((player.attr[PAGE_ATTR] ?: 0) + delta).coerceIn(0, pages(player) - 1)
        player.attr[PAGE_ATTR] = page
        player.attr.remove(CONFIRM_ATTR)
        render(player)
    }

    fun select(
        player: Player,
        slot: Int,
    ) {
        if (slot !in 0 until slotCount(player)) return
        val index = (player.attr[PAGE_ATTR] ?: 0) * slotCount(player) + slot
        if (index !in entries(player).indices) return
        player.attr[SELECTED_ATTR] = index
        player.attr[CAROUSEL_ATTR] = 0
        player.attr.remove(CONFIRM_ATTR)
        renderSelection(player)
        renderPreview(player)
    }

    fun slotEntry(
        player: Player,
        slot: Int,
    ): Entry? = if (slot !in 0 until slotCount(player)) null else entries(player).getOrNull((player.attr[PAGE_ATTR] ?: 0) * slotCount(player) + slot)

    fun turnCarousel(
        player: Player,
        delta: Int,
    ) {
        val entry = selected(player) ?: return
        val size = entry.previewItems.size
        if (size <= 1) return
        player.attr[CAROUSEL_ATTR] = Math.floorMod((player.attr[CAROUSEL_ATTR] ?: 0) + delta, size)
        player.attr.remove(CONFIRM_ATTR)
        renderPreview(player)
    }

    fun selected(player: Player): Entry? = entries(player).getOrNull(player.attr[SELECTED_ATTR] ?: 0)

    private fun name(
        player: Player,
        item: Int,
    ): String = player.world.definitions.getNullable(ItemDef::class.java, item)?.name ?: "item $item"

    fun render(player: Player) {
        val l = layout(player)
        val shop = shop(player)
        val list = entries(player)
        val page = player.attr[PAGE_ATTR] ?: 0
        Shop.values().forEachIndexed { i, s ->
            val selected = s == shop
            // Theme: only the active shop's painted window is shown; its tab is the lit one.
            player.setComponentHidden(INTERFACE_ID, Ui.BACKGROUND_FIRST + i, !selected)
            player.setComponentSprite(
                INTERFACE_ID,
                TAB_FIRST + i * TAB_STRIDE + Ui.TAB_GRAPHIC_OFFSET,
                if (selected) Art.TAB_SELECTED_FIRST + i else Art.TAB_NORMAL_FIRST + i,
            )
        }
        player.setComponentText(INTERFACE_ID, Ui.TAGLINE, TAGLINES[shop] ?: "")
        player.setComponentSprite(INTERFACE_ID, BALANCE_ICON, Art.ICON_FIRST + shop.ordinal)
        player.setComponentSprite(INTERFACE_ID, l.priceIcon, Art.ICON_FIRST + shop.ordinal)
        for (slot in 0 until l.slotCount) {
            val entry = list.getOrNull(page * l.slotCount + slot)
            player.setComponentHidden(INTERFACE_ID, l.slotBackgroundFirst + slot, entry == null)
            player.setComponentHidden(INTERFACE_ID, l.slotFirst + slot, entry == null)
            // IF_SETOBJECT with -1 dereferences a null ObjType in the 667 client: empty slots are hidden instead.
            if (entry != null) player.setComponentItem(INTERFACE_ID, l.slotFirst + slot, entry.purchaseItem, 1)
        }
        player.setComponentText(INTERFACE_ID, l.pageText, "Page ${page + 1} / ${pages(player)}")
        player.setComponentText(INTERFACE_ID, l.emptyText, if (list.isEmpty()) "This shop has no stock yet." else "")
        renderSelection(player)
        renderPreview(player)
    }

    /** Gold glow + outline on the selected slot when it is on the visible page. */
    fun renderSelection(player: Player) {
        val l = layout(player)
        val onPage = (player.attr[SELECTED_ATTR] ?: 0) - (player.attr[PAGE_ATTR] ?: 0) * l.slotCount
        val valid = selected(player) != null
        for (slot in 0 until l.slotCount) {
            val show = valid && slot == onPage
            player.setComponentHidden(INTERFACE_ID, l.selectGlowFirst + slot, !show)
            player.setComponentHidden(INTERFACE_ID, l.selectOutlineFirst + slot, !show)
        }
    }

    fun renderPreview(player: Player) {
        val l = layout(player)
        val entry = selected(player)
        val currency = shop(player).currency
        val balance = player.attr[currency.attr] ?: 0
        player.setComponentText(INTERFACE_ID, BALANCE, "<col=ffd700>${balance.format()}</col> ${if (balance == 1) currency.singular else currency.plural}")
        player.setComponentHidden(INTERFACE_ID, l.previewModel, entry == null)
        player.setComponentHidden(INTERFACE_ID, l.priceIcon, entry == null)
        if (entry == null) {
            listOf(l.resultName, l.kitName, l.requirement, l.cosmetic, l.price).forEach { player.setComponentText(INTERFACE_ID, it, "") }
            player.setComponentHidden(INTERFACE_ID, l.buyLayer, true)
            player.setComponentHidden(INTERFACE_ID, l.carouselPrevious, true)
            player.setComponentHidden(INTERFACE_ID, l.carouselNext, true)
            return
        }
        val index = (player.attr[CAROUSEL_ATTR] ?: 0).coerceIn(0, entry.previewItems.size - 1)
        val preview = entry.previewItems[index]
        // The preview is the real ornamented/result item id; it is only drawn, never given.
        player.setComponentItem(INTERFACE_ID, l.previewModel, preview, 1)
        val several = entry.previewItems.size > 1
        player.setComponentHidden(INTERFACE_ID, l.carouselPrevious, !several)
        player.setComponentHidden(INTERFACE_ID, l.carouselNext, !several)
        player.setComponentText(INTERFACE_ID, l.resultName, name(player, preview) + if (several) " (${index + 1}/${entry.previewItems.size})" else "")
        val tier = TIER_COLOURS[entry.tier]?.let { "<col=$it>${entry.tier.label}</col> - " } ?: ""
        player.setComponentText(INTERFACE_ID, l.kitName, tier + name(player, entry.purchaseItem))
        val base = entry.requiredBaseItems.getOrNull(index)
        player.setComponentText(
            INTERFACE_ID,
            l.requirement,
            when {
                base == null -> "No base item required"
                entry.kind == Kind.UPGRADE -> "Exchanges your ${name(player, base)}"
                entry.kind == Kind.UNLOCK -> "Works while you own a ${name(player, base)}"
                else -> "Use on your ${name(player, base)}"
            },
        )
        player.setComponentText(
            INTERFACE_ID,
            l.cosmetic,
            when (entry.kind) {
                Kind.KIT -> "Stats unchanged - cosmetic only"
                Kind.UPGRADE -> "Stats unchanged - base item required"
                Kind.ITEM -> entry.category
                Kind.UNLOCK ->
                    if (entry.purchaseItem in gg.rsmod.plugins.content.items.osrs.MaxCapeLooks.unlocked(player)) {
                        "Unlocked - use Customise on your max cape"
                    } else {
                        "Look only - Customise on your max cape"
                    }
            },
        )
        val affordable = balance >= entry.price
        val priceColour = if (affordable) "ffd700" else "ff5050"
        player.setComponentText(
            INTERFACE_ID,
            l.price,
            "<col=$priceColour>${entry.price.format()}</col> ${if (entry.price == 1) currency.singular else currency.plural}",
        )
        player.setComponentHidden(INTERFACE_ID, l.buyLayer, false)
        val confirming = player.attr[CONFIRM_ATTR] == player.attr[SELECTED_ATTR]
        // Armed purchase: the button turns gold and asks for the second press.
        player.setComponentSprite(INTERFACE_ID, l.buyGraphic, if (confirming) Art.BUY_CONFIRM else Art.BUY)
        player.setComponentText(
            INTERFACE_ID,
            l.buyText,
            when {
                confirming -> "Confirm purchase"
                entry.kind == Kind.KIT -> "Buy kit"
                entry.kind == Kind.UNLOCK -> "Unlock look"
                else -> "Buy"
            },
        )
    }

    /**
     * Buys the selected entry: all-or-nothing. The currency is only taken once the item is in the inventory (full inventory and a
     * missing base item leave the balance untouched), and every purchase is appended to `data/logs/store_purchases.log` so a
     * rollback can be traced.
     */
    fun buy(player: Player) {
        // Re-checked at the purchase: the screen can still be open after walking out of the safe zone or into a fight.
        if (gg.rsmod.plugins.content.mechanics.pvp.RemoteAccess.refuse(player, "use the store")) return
        val entry = selected(player) ?: return
        val currency = entry.currency
        val balance = player.attr[currency.attr] ?: 0
        if (balance < entry.price) {
            player.message("You need ${entry.price.format()} ${currency.plural} to buy that; you have ${balance.format()}.")
            return
        }
        // First press arms the purchase, the second (same entry, nothing changed in between) completes it.
        val index = player.attr[SELECTED_ATTR] ?: 0
        if (player.attr[CONFIRM_ATTR] != index) {
            player.attr[CONFIRM_ATTR] = index
            renderPreview(player)
            return
        }
        player.attr.remove(CONFIRM_ATTR)
        val bought: Boolean =
            when (entry.kind) {
                Kind.UNLOCK -> {
                    val unlocked = gg.rsmod.plugins.content.items.osrs.MaxCapeLooks.unlock(player, entry.purchaseItem)
                    if (!unlocked) player.message("You have already unlocked that look.")
                    unlocked
                }
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
        // Owner 2026-09-23: every shop (Loyalty, Deadman, Donator stores included) plays the shared purchase sound.
        player.playSound(gg.rsmod.plugins.content.mechanics.shops.ShopSounds.TRANSACTION)
        if (entry.kind == Kind.UNLOCK) {
            player.message("You unlock the ${name(player, entry.purchaseItem).lowercase()} look for ${entry.price.format()} ${currency.plural}. Use Customise on your max cape.")
        } else {
            player.message("You buy ${name(player, entry.purchaseItem)} for ${entry.price.format()} ${currency.plural}.")
        }
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
