package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.mechanics.shops.ItemCurrency
import gg.rsmod.plugins.content.mechanics.store.StoreUi
import gg.rsmod.game.tools.importer.StoreInterfaceImportTool as Ui
import java.util.Locale

/**
 * A16 (owner 2026-10-11: "Pikkupstix-shop: 1 scherm, tabs Pouches / Scrolls / Supplies"; owner 2026-10-06 Q2 = A: Previous/Next page
 * buttons like the Loyalty/Donator store): Pikkupstix's Trade opens ONE window - the 78 Store window (interface 1151) with its own
 * "SUMMONING SHOP" background - with three category buttons, Supplies / Pouches / Scrolls, and the store's Prev / Next page within a
 * category. Everything that is sold, its price and its stock stay the coin shops of summoning_supply_shops.plugin.kts: a row here is
 * (shop, slot) and buying goes through that shop's own coin currency ([ItemCurrency.sellToPlayer]: price never below high alchemy,
 * stock, inventory room, refunds), exactly what the 620 window did. The Donator / Deadman / Loyalty store ([StoreUi]) is not touched:
 * its three top tabs are hidden here and every click is routed here while this shop is the one open ([active]).
 */
object SummoningStoreUi {
    private val Art = gg.rsmod.game.tools.importer.StoreArtTool

    enum class Tab(val label: String) {
        SUPPLIES("Supplies"),
        POUCHES("Pouches"),
        SCROLLS("Scrolls"),
    }

    /** One row of the grid: the coin shop and slot that sells it. */
    data class Row(val shop: String, val slot: Int, val item: Int)

    private val ACTIVE = AttributeKey<Boolean>()
    private val TAB = AttributeKey<Int>()
    private val PAGE = AttributeKey<Int>()
    private val SELECTED = AttributeKey<Int>()

    private const val OP1 = 0x2
    private const val OP1_OP10 = 0x2 or 0x400
    private const val OP1_OP4 = 0x2 or 0x4 or 0x8 or 0x10

    /** Buy (op1) and the right-click Buy 5 / Buy 10 / Buy X of the store's button (if-button opcodes 61/64/4/52). */
    private val BUY_OPCODES = listOf(61, 64, 4, 52)
    private val BUY_AMOUNTS = listOf(1, 5, 10)
    private const val BUY_X = 3

    const val TAGLINE = "Familiar pouches, scrolls and supplies"

    /** Pikkupstix's Summoning Shop window is the one showing. */
    fun active(player: Player): Boolean = player.attr[ACTIVE] == true && player.isInterfaceVisible(Ui.INTERFACE_ID)

    /** The coin shops each tab lists, in order (SummoningShopStock: pages of at most 40 rows, lowest level first). */
    fun shops(tab: Tab): List<String> =
        when (tab) {
            Tab.SUPPLIES -> listOf(SummoningShopStock.SUPPLIES_SHOP)
            Tab.POUCHES -> SummoningShopStock.pages.filter { it.stock.firstOrNull()?.first in pouchIds }.map { it.title }
            Tab.SCROLLS -> SummoningShopStock.pages.filter { it.stock.firstOrNull()?.first in scrollIds }.map { it.title }
        }

    private val pouchIds by lazy { SummoningShopStock.pouches.map { it.first }.toSet() }
    private val scrollIds by lazy { SummoningShopStock.scrolls.map { it.first }.toSet() }

    fun rows(
        lookup: (String) -> Shop?,
        tab: Tab,
    ): List<Row> =
        shops(tab).flatMap { name ->
            val shop = lookup(name) ?: return@flatMap emptyList<Row>()
            shop.items.indices.mapNotNull { slot -> shop.items[slot]?.let { Row(name, slot, it.item) } }
        }

    private fun rows(player: Player): List<Row> = rows({ player.world.getShop(it) }, tab(player))

    fun tab(player: Player): Tab = Tab.values()[(player.attr[TAB] ?: 0).coerceIn(0, Tab.values().size - 1)]

    private fun layout(player: Player): Ui.Layout = StoreUi.layout(player)

    private fun pages(player: Player): Int = maxOf(1, (rows(player).size + layout(player).slotCount - 1) / layout(player).slotCount)

    fun open(player: Player) {
        if (gg.rsmod.plugins.content.mechanics.pvp.RemoteAccess.refuse(player, "trade with Pikkupstix")) return
        // The window needs the category layout (owner-approved bank-size store window); an older cache keeps the 620 pages.
        val l = layout(player)
        if (!l.sections) {
            gg.rsmod.plugins.content.mechanics.shops.ShopPageGroups.open(player, SummoningShopStock.PAGE_GROUP)
            return
        }
        player.attr[ACTIVE] = true
        player.attr[TAB] = 0
        player.attr[PAGE] = 0
        player.attr[SELECTED] = 0
        if (!player.isInterfaceVisible(Ui.INTERFACE_ID)) player.openInterface(Ui.INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(Ui.INTERFACE_ID, Ui.CLOSE, -1..-1, OP1)
        for (slot in 0 until l.slotCount) player.setInterfaceEvents(Ui.INTERFACE_ID, l.slotFirst + slot, -1..-1, OP1_OP10)
        listOf(l.pagePrevious, l.pageNext).forEach { player.setInterfaceEvents(Ui.INTERFACE_ID, it, -1..-1, OP1) }
        player.setInterfaceEvents(Ui.INTERFACE_ID, l.buyLayer, -1..-1, OP1_OP4)
        for (i in 0 until Ui.SECTION_COUNT) player.setInterfaceEvents(Ui.INTERFACE_ID, l.sectionFirst + i * Ui.SECTION_STRIDE, -1..-1, OP1)
        render(player)
    }

    /** StoreUi.open: the Donator / Deadman / Loyalty store takes the window back. */
    fun leave(player: Player) {
        player.attr.remove(ACTIVE)
    }

    /** A click on the window while this shop is open (store.plugin.kts routes it here). */
    fun click(
        player: Player,
        component: Int,
        opcode: Int,
    ) {
        val l = layout(player)
        when {
            component == Ui.CLOSE -> {
                leave(player)
                player.closeInterface(Ui.INTERFACE_ID)
            }
            component in l.sectionFirst until l.owned && (component - l.sectionFirst) % Ui.SECTION_STRIDE == 0 ->
                switchTab(player, (component - l.sectionFirst) / Ui.SECTION_STRIDE)
            component in l.slotFirst until l.slotFirst + l.slotCount -> {
                val slot = component - l.slotFirst
                val row = rows(player).getOrNull((player.attr[PAGE] ?: 0) * l.slotCount + slot) ?: return
                if (opcode == 25) {
                    player.world.sendExamine(player, row.item, gg.rsmod.game.model.ExamineEntityType.ITEM)
                } else {
                    player.attr[SELECTED] = (player.attr[PAGE] ?: 0) * l.slotCount + slot
                    renderSelection(player)
                    renderPreview(player)
                }
            }
            component == l.pagePrevious -> turnPage(player, -1)
            component == l.pageNext -> turnPage(player, 1)
            component == l.buyLayer -> buyClicked(player, opcode)
        }
    }

    fun switchTab(
        player: Player,
        index: Int,
    ) {
        if (index !in Tab.values().indices) return
        player.attr[TAB] = index
        player.attr[PAGE] = 0
        player.attr[SELECTED] = 0
        render(player)
    }

    fun turnPage(
        player: Player,
        delta: Int,
    ) {
        player.attr[PAGE] = ((player.attr[PAGE] ?: 0) + delta).coerceIn(0, pages(player) - 1)
        render(player)
    }

    private fun selected(player: Player): Row? = rows(player).getOrNull(player.attr[SELECTED] ?: 0)

    private fun name(
        player: Player,
        item: Int,
    ): String = player.world.definitions.getNullable(ItemDef::class.java, item)?.name ?: "item $item"

    private fun Int.format(): String = String.format(Locale.US, "%,d", this)

    fun render(player: Player) {
        val l = layout(player)
        // Pikkupstix's painted window in the first background slot, the three shop backgrounds and tabs hidden.
        for (i in 0 until 3) player.setComponentHidden(Ui.INTERFACE_ID, Ui.BACKGROUND_FIRST + i, i != 0)
        player.setComponentSprite(Ui.INTERFACE_ID, Ui.BACKGROUND_FIRST, Art.SUMMONING_BACKGROUND)
        for (i in 0 until 3) player.setComponentHidden(Ui.INTERFACE_ID, Ui.TAB_FIRST + i * Ui.TAB_STRIDE, true)
        val current = tab(player)
        for (i in 0 until Ui.SECTION_COUNT) {
            val base = l.sectionFirst + i * Ui.SECTION_STRIDE
            val tab = Tab.values().getOrNull(i)
            player.setComponentHidden(Ui.INTERFACE_ID, base, tab == null)
            if (tab == null) continue
            val lit = tab == current
            player.setComponentSprite(Ui.INTERFACE_ID, base + Ui.SECTION_GRAPHIC_OFFSET, if (lit) Art.SUMMONING_SECTION_SELECTED else Art.SECTION_NORMAL)
            player.setComponentText(Ui.INTERFACE_ID, base + Ui.SECTION_TEXT_OFFSET, if (lit) "<col=ffffff>${tab.label}</col>" else tab.label)
        }
        player.setComponentText(Ui.INTERFACE_ID, Ui.TAGLINE, TAGLINE)
        // Coins: no painted currency icon (the item's own coin pile is the price).
        player.setComponentHidden(Ui.INTERFACE_ID, Ui.BALANCE_ICON, true)
        val list = rows(player)
        val page = (player.attr[PAGE] ?: 0).coerceIn(0, pages(player) - 1)
        for (slot in 0 until l.slotCount) {
            val row = list.getOrNull(page * l.slotCount + slot)
            player.setComponentHidden(Ui.INTERFACE_ID, l.slotBackgroundFirst + slot, row == null)
            player.setComponentHidden(Ui.INTERFACE_ID, l.slotFirst + slot, row == null)
            if (row == null) continue
            player.setComponentItem(Ui.INTERFACE_ID, l.slotFirst + slot, row.item, 1)
            player.setComponentSprite(Ui.INTERFACE_ID, l.slotBackgroundFirst + slot, Art.SLOT)
        }
        player.setComponentText(Ui.INTERFACE_ID, l.pageText, "Page ${page + 1} / ${pages(player)}")
        if (l.pageButtons) {
            player.setComponentSprite(Ui.INTERFACE_ID, l.pagePreviousGraphic, Art.PAGE_BUTTON_FIRST + if (page > 0) 0 else 2)
            player.setComponentSprite(Ui.INTERFACE_ID, l.pageNextGraphic, Art.PAGE_BUTTON_FIRST + if (page < pages(player) - 1) 1 else 3)
        }
        player.setComponentText(Ui.INTERFACE_ID, l.emptyText, if (list.isEmpty()) "Pikkupstix has nothing in stock." else "")
        renderSelection(player)
        renderPreview(player)
    }

    private fun renderSelection(player: Player) {
        val l = layout(player)
        val onPage = (player.attr[SELECTED] ?: 0) - (player.attr[PAGE] ?: 0) * l.slotCount
        val valid = selected(player) != null
        for (slot in 0 until l.slotCount) {
            val show = valid && slot == onPage
            player.setComponentHidden(Ui.INTERFACE_ID, l.selectGlowFirst + slot, !show)
            player.setComponentHidden(Ui.INTERFACE_ID, l.selectOutlineFirst + slot, !show)
        }
    }

    /** What [player] pays for one of [row] (the shop's own coin price) and how many the shop has left. */
    fun price(
        player: Player,
        row: Row,
    ): Pair<Int, Int> {
        val shop = player.world.getShop(row.shop) ?: return 0 to 0
        val item = shop.items[row.slot] ?: return 0 to 0
        val currency = shop.currency as? ItemCurrency ?: return 0 to item.currentAmount
        return currency.sellPriceOf(player.world, item) to item.currentAmount
    }

    /** The level a familiar pouch or scroll asks (SummoningShopStock, lowest familiar that uses it). */
    private fun level(row: Row): Int? =
        SummoningShopStock.pouches.firstOrNull { it.first == row.item }?.second
            ?: SummoningShopStock.scrolls.firstOrNull { it.first == row.item }?.second

    fun renderPreview(player: Player) {
        val l = layout(player)
        val coins = player.inventory.getItemCount(Items.COINS_995)
        player.setComponentText(Ui.INTERFACE_ID, Ui.BALANCE, "<col=ffd700>${coins.format()}</col> ${if (coins == 1) "coin" else "coins"}")
        val row = selected(player)
        player.setComponentHidden(Ui.INTERFACE_ID, l.carouselPrevious, true)
        player.setComponentHidden(Ui.INTERFACE_ID, l.carouselNext, true)
        player.setComponentHidden(Ui.INTERFACE_ID, l.priceIcon, true)
        player.setComponentHidden(Ui.INTERFACE_ID, l.previewModel, row == null)
        if (row == null) {
            listOf(l.resultName, l.kitName, l.requirement, l.cosmetic, l.price, l.owned, l.tradeable).forEach { player.setComponentText(Ui.INTERFACE_ID, it, "") }
            player.setComponentHidden(Ui.INTERFACE_ID, l.buyLayer, true)
            return
        }
        player.setComponentItem(Ui.INTERFACE_ID, l.previewModel, row.item, 1)
        player.setComponentText(Ui.INTERFACE_ID, l.resultName, name(player, row.item))
        val level = level(row)
        val kind =
            when (tab(player)) {
                Tab.POUCHES -> "Familiar pouch"
                Tab.SCROLLS -> "Summoning scroll"
                Tab.SUPPLIES -> "Summoning supplies"
            }
        player.setComponentText(Ui.INTERFACE_ID, l.kitName, kind)
        val summoning = player.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.SUMMONING)
        player.setComponentText(
            Ui.INTERFACE_ID,
            l.requirement,
            when {
                level == null -> "No level needed to buy"
                summoning >= level -> "Summoning level $level"
                else -> "<col=ff5050>Summoning level $level</col>"
            },
        )
        val (price, stock) = price(player, row)
        player.setComponentText(Ui.INTERFACE_ID, l.owned, if (stock > 0) "<col=5ce05c>In stock: ${stock.format()}</col>" else "<col=ff5050>Out of stock</col>")
        player.setComponentText(Ui.INTERFACE_ID, l.tradeable, "<col=5ce05c>Tradeable</col>")
        player.setComponentText(Ui.INTERFACE_ID, l.cosmetic, "Goes straight to your inventory")
        val affordable = price in 1..coins
        player.setComponentText(Ui.INTERFACE_ID, l.price, "<col=${if (affordable) "ffd700" else "ff5050"}>${price.format()}</col> ${if (price == 1) "coin" else "coins"}")
        player.setComponentHidden(Ui.INTERFACE_ID, l.buyLayer, false)
        player.setComponentSprite(Ui.INTERFACE_ID, l.buyGraphic, Art.BUY)
        player.setComponentText(
            Ui.INTERFACE_ID,
            l.buyText,
            when {
                stock <= 0 -> "Out of stock"
                !affordable -> "Need ${(price - coins).format()} more"
                else -> "Buy"
            },
        )
    }

    fun buyClicked(
        player: Player,
        opcode: Int,
    ) {
        val op = BUY_OPCODES.indexOf(opcode).coerceAtLeast(0)
        val row = selected(player) ?: return
        when (op) {
            BUY_X ->
                player.queue(gg.rsmod.game.model.queue.TaskPriority.WEAK) {
                    val asked = inputInt("How many would you like to buy?")
                    if (asked > 0 && active(player) && selected(player) == row) buy(player, asked)
                }
            else -> buy(player, BUY_AMOUNTS[op])
        }
    }

    /** Buys [amount] of the selected row through its coin shop (price, stock, inventory room and refunds are the shop's own). */
    fun buy(
        player: Player,
        amount: Int,
    ) {
        if (gg.rsmod.plugins.content.mechanics.pvp.RemoteAccess.refuse(player, "trade with Pikkupstix")) return
        val row = selected(player) ?: return
        val shop = player.world.getShop(row.shop) ?: return
        val before = player.inventory.getItemCount(row.item)
        shop.currency.sellToPlayer(player, shop, row.slot, amount)
        val bought = player.inventory.getItemCount(row.item) - before
        if (bought > 0) {
            player.playSound(gg.rsmod.plugins.content.mechanics.shops.ShopSounds.TRANSACTION)
            player.message("You buy ${bought.format()} x ${name(player, row.item)}.")
        }
        renderPreview(player)
    }
}
