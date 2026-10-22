package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.content.mechanics.shops.notEnough
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.tools.importer.StoreInterfaceImportTool as Ui
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Entry
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Kind
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Section
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Shop
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.TradeablePolicy
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
                    "store interface $INTERFACE_ID has $count components, not the ${Ui.CURRENT.componentCount} of the bank-size layout: the cache " +
                        "still holds an older store window - run StoreInterfaceImportTool --apply with the servers stopped"
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
        listOf(Ui.CURRENT, Ui.WIDE_7X7, Ui.SECTIONS_7X6, Ui.PREVIOUS, Ui.LEGACY).flatMap { l ->
            listOf(CLOSE, l.pagePrevious, l.pageNext, l.carouselPrevious, l.carouselNext, l.buyLayer) +
                (0 until Shop.values().size).map { TAB_FIRST + it * TAB_STRIDE } +
                (0 until l.slotCount).map { l.slotFirst + it } +
                (if (l.sections) (0 until Ui.SECTION_COUNT).map { l.sectionFirst + it * Ui.SECTION_STRIDE } else emptyList())
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
            component == CLOSE -> close(player)
            component >= TAB_FIRST && (component - TAB_FIRST) % TAB_STRIDE == 0 && tab in Shop.values().indices -> switchShop(player, tab)
            l.sections && component in l.sectionFirst until l.owned && (component - l.sectionFirst) % Ui.SECTION_STRIDE == 0 ->
                switchSection(player, (component - l.sectionFirst) / Ui.SECTION_STRIDE)
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
            component == l.buyLayer -> buyClicked(player, opcode)
        }
    }

    /** Header tagline per shop (tab order). */
    private val TAGLINES =
        mapOf(
            // Owner 2026-10-11 bank-size window: one line of at most ~270 px beside the balance.
            Shop.DONATOR to "Kits, gear and looks - 78legacy.com",
            Shop.DEADMAN to "Earned in Deadman combat only",
            Shop.LOYALTY to "Earned by playing and dailies",
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

    /** A store opened before 2026-10-11 as the fixed-mode full-screen top level; [close] still gives the gameframe back. */
    private val FULLSCREEN_ATTR = AttributeKey<Boolean>()

    private val SHOP_ATTR = AttributeKey<Int>()
    private val SECTION_ATTR = AttributeKey<Int>()
    private val PAGE_ATTR = AttributeKey<Int>()
    private val SELECTED_ATTR = AttributeKey<Int>()
    private val CAROUSEL_ATTR = AttributeKey<Int>()

    /** Catalogue index the player pressed Buy on once; the second press on the same entry buys (spending points is final). */
    private val CONFIRM_ATTR = AttributeKey<Int>()

    /** How many the armed purchase buys (Buy 5 / 10 / X, owner 2026-10-10). */
    private val CONFIRM_AMOUNT_ATTR = AttributeKey<Int>()

    private const val OP1 = 0x2
    private const val OP1_OP10 = 0x2 or 0x400
    private const val OP1_OP4 = 0x2 or 0x4 or 0x8 or 0x10

    fun shop(player: Player): Shop = Shop.values()[player.attr[SHOP_ATTR] ?: 0]

    /** The category buttons of the open shop; a window without category buttons (older cache) always shows everything. */
    fun sections(player: Player): List<Section> = if (layout(player).sections) StoreCatalogue.sections(shop(player)) else listOf(Section.ALL)

    fun section(player: Player): Section = sections(player).getOrNull(player.attr[SECTION_ATTR] ?: 0) ?: Section.ALL

    fun entries(player: Player): List<Entry> = StoreCatalogue.entries(shop(player), section(player))

    fun isOpen(player: Player): Boolean = player.isInterfaceVisible(INTERFACE_ID)

    /** Closes the window: the fixed-mode full-screen store gives the gameframe back, the resizable one closes as a sub-interface. */
    fun close(player: Player) {
        if (player.attr[FULLSCREEN_ATTR] == true) {
            player.attr.remove(FULLSCREEN_ATTR)
            player.interfaces.close(0, 0)
            player.closeFullscreenInterface()
        } else {
            player.closeInterface(INTERFACE_ID)
        }
    }

    fun open(
        player: Player,
        shop: Shop,
    ) {
        // Bug search 2026-10-02: the store is reachable without walking to it (`store`/`deadmanstore`/... commands, the
        // Deadman's skull "Shop" option) and sells real PvP gear, so it was a mid-fight restock anywhere. The shared
        // remote-service rule applies to every route (safe zone, out of combat); the store npcs stand in safe zones.
        if (gg.rsmod.plugins.content.mechanics.pvp.RemoteAccess.refuse(player, "use the store")) return
        player.attr[SHOP_ATTR] = shop.ordinal
        player.attr[SECTION_ATTR] = 0
        player.attr[PAGE_ATTR] = 0
        player.attr[SELECTED_ATTR] = 0
        player.attr[CAROUSEL_ATTR] = 0
        player.attr.remove(CONFIRM_ATTR)
        if (!isOpen(player)) {
            // Owner 2026-10-11 ("stores moeten niet overlappen zeker niet in fixed mode", answer: bank size): the window is 512x334
            // and opens in the game view's modal slot like the bank (fixed 548, resizable 746), so it never covers the chat, the
            // tabs or the minimap. Before: a 660x470 window over the whole resizable view / as the fixed-mode full-screen top level.
            player.attr.remove(FULLSCREEN_ATTR)
            player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        }
        val l = layout(player)
        player.setInterfaceEvents(INTERFACE_ID, CLOSE, -1..-1, OP1)
        for (i in 0 until Shop.values().size) player.setInterfaceEvents(INTERFACE_ID, TAB_FIRST + i * TAB_STRIDE, -1..-1, OP1)
        for (slot in 0 until l.slotCount) player.setInterfaceEvents(INTERFACE_ID, l.slotFirst + slot, -1..-1, OP1_OP10)
        listOf(l.pagePrevious, l.pageNext, l.carouselPrevious, l.carouselNext).forEach { player.setInterfaceEvents(INTERFACE_ID, it, -1..-1, OP1) }
        player.setInterfaceEvents(INTERFACE_ID, l.buyLayer, -1..-1, if (l.sections) OP1_OP4 else OP1)
        if (l.sections) for (i in 0 until Ui.SECTION_COUNT) player.setInterfaceEvents(INTERFACE_ID, l.sectionFirst + i * Ui.SECTION_STRIDE, -1..-1, OP1)
        render(player)
    }

    fun switchShop(
        player: Player,
        tab: Int,
    ) {
        if (tab !in Shop.values().indices) return
        open(player, Shop.values()[tab])
    }

    /** A category button: the shop's list narrows to that category from page 1, its first item selected. */
    fun switchSection(
        player: Player,
        button: Int,
    ) {
        if (button !in sections(player).indices) return
        player.attr[SECTION_ATTR] = button
        player.attr[PAGE_ATTR] = 0
        player.attr[SELECTED_ATTR] = 0
        player.attr[CAROUSEL_ATTR] = 0
        player.attr.remove(CONFIRM_ATTR)
        render(player)
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
        if (l.sections) {
            val buttons = sections(player)
            val current = section(player)
            for (i in 0 until Ui.SECTION_COUNT) {
                val base = l.sectionFirst + i * Ui.SECTION_STRIDE
                val button = buttons.getOrNull(i)
                player.setComponentHidden(INTERFACE_ID, base, button == null)
                if (button == null) continue
                val lit = button == current
                player.setComponentSprite(INTERFACE_ID, base + Ui.SECTION_GRAPHIC_OFFSET, if (lit) Art.SECTION_SELECTED_FIRST + shop.ordinal else Art.SECTION_NORMAL)
                player.setComponentText(INTERFACE_ID, base + Ui.SECTION_TEXT_OFFSET, if (lit) "<col=ffffff>${button.label}</col>" else button.label)
            }
        }
        player.setComponentText(INTERFACE_ID, Ui.TAGLINE, TAGLINES[shop] ?: "")
        player.setComponentSprite(INTERFACE_ID, BALANCE_ICON, Art.ICON_FIRST + shop.ordinal)
        player.setComponentSprite(INTERFACE_ID, l.priceIcon, Art.ICON_FIRST + shop.ordinal)
        for (slot in 0 until l.slotCount) {
            val entry = list.getOrNull(page * l.slotCount + slot)
            player.setComponentHidden(INTERFACE_ID, l.slotBackgroundFirst + slot, entry == null)
            player.setComponentHidden(INTERFACE_ID, l.slotFirst + slot, entry == null)
            // IF_SETOBJECT with -1 dereferences a null ObjType in the 667 client: empty slots are hidden instead.
            if (entry == null) continue
            // Owner 2026-10-10: the grid shows what the player ends up with (Amulet of fury (or)), not a row of identical kit
            // scrolls; the card still names the kit that is bought. The slot frame carries the tier colour.
            player.setComponentItem(INTERFACE_ID, l.slotFirst + slot, entry.previewItems.first(), 1)
            if (l.sections) player.setComponentSprite(INTERFACE_ID, l.slotBackgroundFirst + slot, slotSprite(entry))
        }
        player.setComponentText(INTERFACE_ID, l.pageText, "Page ${page + 1} / ${pages(player)}")
        if (l.pageButtons) {
            // Lit when there is a page that way, dimmed on the first / last page.
            player.setComponentSprite(INTERFACE_ID, l.pagePreviousGraphic, Art.PAGE_BUTTON_FIRST + if (page > 0) 0 else 2)
            player.setComponentSprite(INTERFACE_ID, l.pageNextGraphic, Art.PAGE_BUTTON_FIRST + if (page < pages(player) - 1) 1 else 3)
        }
        player.setComponentText(INTERFACE_ID, l.emptyText, if (list.isEmpty()) "This shop has no stock yet." else "")
        renderSelection(player)
        renderPreview(player)
    }

    private fun slotSprite(entry: Entry): Int =
        when (entry.tier) {
            StoreCatalogue.Tier.PREMIUM -> Art.SLOT_TIER_FIRST
            StoreCatalogue.Tier.ELITE -> Art.SLOT_TIER_FIRST + 1
            StoreCatalogue.Tier.PRESTIGE -> Art.SLOT_TIER_FIRST + 2
            else -> Art.SLOT
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
            if (l.sections) listOf(l.owned, l.tradeable).forEach { player.setComponentText(INTERFACE_ID, it, "") }
            player.setComponentHidden(INTERFACE_ID, l.buyLayer, true)
            player.setComponentHidden(INTERFACE_ID, l.carouselPrevious, true)
            player.setComponentHidden(INTERFACE_ID, l.carouselNext, true)
            return
        }
        val index = (player.attr[CAROUSEL_ATTR] ?: 0).coerceIn(0, entry.previewItems.size - 1)
        val preview = entry.previewItems[index]
        // The preview is the real ornamented/result item id; it is only drawn, never given.
        // The npc preview script ships with the current window (StoreInterfaceImportTool), so an older cache keeps item models.
        val npc = if (l === Ui.CURRENT) StorePreview.npcFor(preview) else null
        if (npc != null) {
            // A pet stands in the showcase as the npc that follows the player, in its idle animation (StorePreview).
            player.runClientScript(Ui.NPC_PREVIEW_SCRIPT, (INTERFACE_ID shl 16) or l.previewModel, npc, StorePreview.PITCH, StorePreview.YAW)
            player.setComponentAnim(INTERFACE_ID, l.previewModel, StorePreview.idleAnimation(player.world, npc))
        } else {
            player.setComponentItem(INTERFACE_ID, l.previewModel, preview, 1)
            // An item after a pet: drop the pet's idle animation, which would otherwise play on the item model.
            if (l.sections) player.setComponentAnim(INTERFACE_ID, l.previewModel, -1)
        }
        val several = entry.previewItems.size > 1
        player.setComponentHidden(INTERFACE_ID, l.carouselPrevious, !several)
        player.setComponentHidden(INTERFACE_ID, l.carouselNext, !several)
        player.setComponentText(INTERFACE_ID, l.resultName, name(player, preview) + if (several) "  <col=c0c0c0>${index + 1} of ${entry.previewItems.size}</col>" else "")
        val card = card(player, entry, index)
        if (l.sections) {
            player.setComponentText(INTERFACE_ID, l.kitName, card.subtitle)
            player.setComponentText(INTERFACE_ID, l.requirement, card.requirement)
            player.setComponentText(INTERFACE_ID, l.owned, card.owned)
            player.setComponentText(INTERFACE_ID, l.tradeable, card.tradeable)
            player.setComponentText(INTERFACE_ID, l.cosmetic, card.effect)
        } else {
            // The 2026-10-03 window has no owned / tradeable lines: the old four-line card.
            player.setComponentText(INTERFACE_ID, l.kitName, card.subtitle)
            player.setComponentText(INTERFACE_ID, l.requirement, card.requirement)
            player.setComponentText(INTERFACE_ID, l.cosmetic, card.effect)
        }
        val affordable = balance >= entry.price
        val priceColour = if (affordable) "ffd700" else "ff5050"
        val confirming = player.attr[CONFIRM_ATTR] == player.attr[SELECTED_ATTR]
        val amount = if (confirming) (player.attr[CONFIRM_AMOUNT_ATTR] ?: 1).coerceAtLeast(1) else 1
        val total = entry.price * amount
        player.setComponentText(
            INTERFACE_ID,
            l.price,
            if (amount > 1) {
                "<col=$priceColour>${total.format()}</col> ${currency.plural}  <col=c0c0c0>($amount x ${entry.price.format()})</col>"
            } else {
                "<col=$priceColour>${entry.price.format()}</col> ${if (entry.price == 1) currency.singular else currency.plural}"
            },
        )
        player.setComponentHidden(INTERFACE_ID, l.buyLayer, false)
        // Armed purchase: the button turns gold and asks for the second press.
        player.setComponentSprite(INTERFACE_ID, l.buyGraphic, if (confirming) Art.BUY_CONFIRM else Art.BUY)
        player.setComponentText(
            INTERFACE_ID,
            l.buyText,
            when {
                // Owner 2026-10-10 "onduidelijk wat je koopt": say how many points are missing instead of a Buy that only refuses.
                !affordable -> "Need ${(entry.price - balance).format()} more"
                confirming && amount > 1 -> "Confirm $amount for ${total.format()}"
                confirming -> "Confirm purchase"
                entry.kind == Kind.KIT -> "Buy kit"
                entry.kind == Kind.UNLOCK -> "Unlock look"
                entry.kind == Kind.UPGRADE -> "Upgrade"
                else -> "Buy"
            },
        )
    }

    /** The card's text lines for [entry] at carousel position [index] (colours as text tags). */
    class Card(val subtitle: String, val requirement: String, val owned: String, val tradeable: String, val effect: String)

    /** The Loyalty kits that make a different item (orbs, shards, upgrade kits) rather than a look; see [card]. */
    private const val UPGRADE_KITS = "High-end rewards"

    private const val GREEN = "5ce05c"
    private const val RED = "ff5050"
    private const val AMBER = "ffb040"

    fun card(
        player: Player,
        entry: Entry,
        index: Int,
    ): Card {
        val tier = TIER_COLOURS[entry.tier]?.let { "<col=$it>${entry.tier.label}</col> - " } ?: ""
        val subtitle =
            when {
                entry.kind == Kind.KIT -> tier + name(player, entry.purchaseItem)
                entry.kind == Kind.UNLOCK -> tier + "Max cape look"
                StoreCatalogue.section(entry) == Section.PETS -> tier + "Pet - follows you around"
                else -> tier + entry.category
            }
        val base = entry.requiredBaseItems.getOrNull(index)
        val requirement =
            when {
                base == null -> "No base item needed"
                entry.kind == Kind.UPGRADE -> "Trade in: ${name(player, base)}"
                else -> "Needs: ${name(player, base)}"
            }
        val owned =
            when {
                base == null -> ""
                entry.kind == Kind.UPGRADE && player.inventory.contains(base) -> "<col=$GREEN>In inventory</col>"
                entry.kind == Kind.UPGRADE && player.hasItem(base) -> "<col=$AMBER>In your bank</col>"
                player.hasItem(base) -> "<col=$GREEN>Owned</col>"
                else -> "<col=$RED>Not owned</col>"
            }
        val tradeable =
            when {
                entry.kind == Kind.UNLOCK -> "Account unlock - not an item"
                entry.tradeablePolicy == TradeablePolicy.UNTRADEABLE -> "<col=$AMBER>Untradeable</col>"
                player.world.definitions.getNullable(ItemDef::class.java, entry.purchaseItem)?.tradeable == true -> "<col=$GREEN>Tradeable</col> - you can sell it to others"
                else -> "<col=$AMBER>Untradeable</col>"
            }
        val effect =
            when (entry.kind) {
                // The Loyalty high-end kits are upgrades, not looks: an orb turns the Nightmare staff into another staff.
                Kind.KIT -> if (entry.category == UPGRADE_KITS) "Upgrades your item into this one" else "Cosmetic only - stats stay the same"
                Kind.UPGRADE -> "Your item is exchanged for this one"
                Kind.ITEM -> "Goes straight to your inventory"
                Kind.UNLOCK ->
                    if (entry.purchaseItem in gg.rsmod.plugins.content.items.osrs.MaxCapeLooks.unlocked(player)) {
                        "Unlocked - use Customise on your max cape"
                    } else {
                        "Look only - Customise on your max cape"
                    }
            }
        return Card(subtitle, requirement, owned, tradeable, effect)
    }

    /** Right-click quantities of the Buy button (op1 = Buy, op2..op4 = Buy 5 / Buy 10 / Buy X, if-button opcodes 61/64/4/52). */
    private val BUY_OPCODES = listOf(61, 64, 4, 52)
    private val BUY_AMOUNTS = listOf(1, 5, 10)
    private const val BUY_X = 3

    /** Entries several of which can be bought at once: items and kits (look unlocks and upgrades are one per purchase). */
    fun multiple(entry: Entry): Boolean = entry.kind == Kind.ITEM || entry.kind == Kind.KIT

    /**
     * A click on Buy. Owner 2026-10-10 ("je kunt telkens maar 1 morrigans javelin kopen ... x en dan aantal invullen"): every item
     * and kit can be bought 1 / 5 / 10 / X at a time, as in the OSRS shops; Buy X asks for the number.
     */
    fun buyClicked(
        player: Player,
        opcode: Int,
    ) {
        val op = BUY_OPCODES.indexOf(opcode).coerceAtLeast(0)
        val entry = selected(player) ?: return
        when {
            op == 0 || !multiple(entry) -> buy(player, null)
            op != BUY_X -> buy(player, BUY_AMOUNTS[op])
            else ->
                player.queue(gg.rsmod.game.model.queue.TaskPriority.WEAK) {
                    val asked = inputInt("How many would you like to buy?")
                    // The window may have changed while the number was typed: only the same open entry is bought.
                    if (asked > 0 && isOpen(player) && selected(player) == entry) buy(player, asked)
                }
        }
    }

    /**
     * Buys the selected entry: [requested] of it ([multiple] entries only; null = a plain left click on Buy). The first press arms
     * the purchase (the button turns gold and shows the amount and total), the second press on the same entry and amount completes
     * it - a left click confirms whatever is armed. The amount is capped by the balance and, for unstackable items, by the free
     * inventory slots; the currency is only taken for what reached the inventory (full inventory and a missing base item leave
     * the balance untouched), and every purchase is appended to `data/logs/store_purchases.log` so a rollback can be traced.
     */
    fun buy(
        player: Player,
        requested: Int? = null,
    ) {
        // Re-checked at the purchase: the screen can still be open after walking out of the safe zone or into a fight.
        if (gg.rsmod.plugins.content.mechanics.pvp.RemoteAccess.refuse(player, "use the store")) return
        val entry = selected(player) ?: return
        val currency = entry.currency
        val balance = player.attr[currency.attr] ?: 0
        if (balance < entry.price) {
            player.notEnough("You need ${entry.price.format()} ${currency.plural} to buy that; you have ${balance.format()}.") // N89: + sound
            if (currency == StoreCatalogue.Currency.DONATOR) player.message("Donator Points are available at 78legacy.com.")
            return
        }
        val index = player.attr[SELECTED_ATTR] ?: 0
        val armed = player.attr[CONFIRM_ATTR] == index
        val armedAmount = (player.attr[CONFIRM_AMOUNT_ATTR] ?: 1).coerceAtLeast(1)
        val wanted = if (!multiple(entry)) 1 else requested ?: if (armed) armedAmount else 1
        val affordableCount = balance / entry.price
        val amount = minOf(wanted, affordableCount)
        // First press arms the purchase, the second (same entry and amount, nothing changed in between) completes it.
        if (!armed || amount != armedAmount) {
            if (amount < wanted) player.message("You can afford ${affordableCount.format()} of those.")
            player.attr[CONFIRM_ATTR] = index
            player.attr[CONFIRM_AMOUNT_ATTR] = amount
            renderPreview(player)
            return
        }
        player.attr.remove(CONFIRM_ATTR)
        player.attr.remove(CONFIRM_AMOUNT_ATTR)
        val bought: Int =
            when (entry.kind) {
                Kind.UNLOCK -> {
                    val unlocked = gg.rsmod.plugins.content.items.osrs.MaxCapeLooks.unlock(player, entry.purchaseItem)
                    if (!unlocked) player.message("You have already unlocked that look.")
                    if (unlocked) 1 else 0
                }
                Kind.KIT, Kind.ITEM -> {
                    val stackable = player.world.definitions.getNullable(ItemDef::class.java, entry.purchaseItem)?.stackable == true
                    val fits = if (stackable) amount else minOf(amount, player.inventory.freeSlotCount)
                    val added = fits > 0 && player.inventory.add(entry.purchaseItem, fits, assureFullInsertion = true).hasSucceeded()
                    if (!added) {
                        player.message("You don't have enough inventory space.")
                        0
                    } else {
                        if (fits < amount) player.message("You only have room for ${fits.format()}.")
                        fits
                    }
                }
                Kind.UPGRADE -> {
                    val base = entry.requiredBaseItems.first()
                    val slot = player.inventory.getItemIndex(base, skipAttrItems = false)
                    if (slot == -1) {
                        player.message("You need a ${name(player, base)} in your inventory for this upgrade.")
                        0
                    } else {
                        // N82: the item handed in becomes the result with its data (an (r)'s recoil charges and recoil setting,
                        // ROOT_CAUSES #133) - a fresh Item wiped up to 100,000 charges.
                        val handedIn = player.inventory[slot]!!
                        player.inventory[slot] = gg.rsmod.plugins.content.items.osrs.RingOfSuffering.exchanged(handedIn, entry.resultItems.first())
                        1
                    }
                }
            }
        if (bought <= 0) {
            renderPreview(player)
            return
        }
        val cost = entry.price * bought
        // N106: through the one balance helper; cost <= balance (affordableCount), so the take always succeeds here.
        if (!gg.rsmod.game.model.attr.PointBalance.take(player.attr, currency.attr, cost)) {
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.ITEM) { "78 Store: ${player.username} got $bought x ${entry.purchaseItem} but ${cost} ${currency.plural} could not be taken from $balance" }
        }
        audit(player, entry, bought, cost)
        // Owner 2026-09-23: every shop (Loyalty, Deadman, Donator stores included) plays the shared purchase sound.
        player.playSound(gg.rsmod.plugins.content.mechanics.shops.ShopSounds.TRANSACTION)
        when {
            entry.kind == Kind.UNLOCK ->
                player.message("You unlock the ${name(player, entry.purchaseItem).lowercase()} look for ${cost.format()} ${currency.plural}. Use Customise on your max cape.")
            bought > 1 -> player.message("You buy ${bought.format()} x ${name(player, entry.purchaseItem)} for ${cost.format()} ${currency.plural}.")
            else -> player.message("You buy ${name(player, entry.purchaseItem)} for ${cost.format()} ${currency.plural}.")
        }
        renderPreview(player)
    }

    /** One line in `data/logs/store_purchases.log` (also Perdu's imbue, which sells the same catalogue entry). */
    fun audit(
        player: Player,
        entry: Entry,
        amount: Int,
        cost: Int,
    ) {
        // Columns as before (the price column is the total paid); the amount is appended so older log readers keep working.
        val line = "${LocalDateTime.now()}\t${player.username}\t${entry.shop}\t${entry.purchaseItem}\t$cost\t${entry.currency}" +
            "\tbalance=${player.attr[entry.currency.attr] ?: 0}\tamount=$amount"
        runCatching {
            val file = gg.rsmod.util.io.SharedDataFiles.of("data/logs/store_purchases.log", createIfMissing = false)
            file.parentFile?.mkdirs()
            file.appendText(line + System.lineSeparator())
        }.onFailure { logger.error(it) { "Could not log store purchase: $line" } }
    }
}
