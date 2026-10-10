package gg.rsmod.game.tools.importer

import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool.Component
import gg.rsmod.game.tools.importer.StoreArtTool as Art

/**
 * Builds the "78 Store" window (owner night-run 2026-09-19, batches 4 and 5) as the revision-667 if3 interface [INTERFACE_ID],
 * through the same transactional, both-caches route as [LootKeyInterfaceImportTool] and [DizanasQuiverInterfaceImportTool].
 *
 * Owner flow: "kit in shop-grid -> selected kit -> big preview of the ornamented result -> base item requirement -> stats
 * unchanged / cosmetic only -> price and currency balance -> Buy kit", with a carousel when one kit has several results.
 *
 * Redesign (owner 2026-09-22: "redesign the shop they need to look special ... use the highest quality visuals"): every
 * visual is the store's own painted art from [StoreArtTool] - one full window per shop theme (gold frame, engraved title,
 * inset panels, a spotlit showcase with a pedestal), glossy tabs, glass item slots with a gold selection glow and ring,
 * a jewelled Buy button, currency icons, carousel arrows and a close button. The components only add what changes: the
 * item icons, the preview model (fitted per item by the client, `InterfaceManager.fitModelToBox`) and the texts.
 *
 * The server swaps sprites with IF_SETGRAPHIC and shows/hides theme art with IF_SETHIDE (content: `StoreUi`).
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.StoreInterfaceImportTool [--apply] [--png=<dir>]`. The tool owns
 * interface [INTERFACE_ID] and the [StoreArtTool] sprites: a re-run replaces its own components and art (pinned to what is
 * there) in one transaction and removes any component left over from an older layout. Like every cache write it is refused
 * while the servers run (both caches are read at boot).
 */
object StoreInterfaceImportTool {
    /** First interface id above the Dizana's quiver window (1150). */
    const val INTERFACE_ID = 1151

    const val WIDTH = Art.WIDTH
    const val HEIGHT = Art.HEIGHT

    const val ROOT = 0
    const val WINDOW = 1

    /** The painted window, one per shop theme; only the active shop's is shown. */
    const val BACKGROUND_FIRST = 2 // 2..4
    const val CLOSE = 5

    /** Three shop tabs: a clickable layer and its painted tab graphic. */
    const val TAB_FIRST = 6 // 6..11
    const val TAB_STRIDE = 2
    const val TAB_COUNT = 3
    const val TAB_GRAPHIC_OFFSET = 1

    const val TAGLINE = 12
    const val BALANCE_ICON = 13
    const val BALANCE = 14

    /** Owner 2026-10-03 (bug 210545): 7x6 = 42 kits a page (was 6x5 = 30) in the 8% larger window ([StoreArtTool.WIDTH]). */
    const val GRID_COLUMNS = 7
    const val GRID_ROWS = 7
    const val SLOT_COUNT = GRID_COLUMNS * GRID_ROWS
    const val SLOT_BACKGROUND_FIRST = 15 // 15..56
    const val SELECT_GLOW_FIRST = SLOT_BACKGROUND_FIRST + SLOT_COUNT // 57..98
    const val SLOT_FIRST = SELECT_GLOW_FIRST + SLOT_COUNT // 99..140
    const val SELECT_OUTLINE_FIRST = SLOT_FIRST + SLOT_COUNT // 141..182

    const val PAGE_PREVIOUS = SELECT_OUTLINE_FIRST + SLOT_COUNT // 183
    const val PAGE_TEXT = PAGE_PREVIOUS + 1
    const val PAGE_NEXT = PAGE_PREVIOUS + 2
    const val PREVIEW_LAYER = PAGE_PREVIOUS + 3
    const val PREVIEW_MODEL = PREVIEW_LAYER + 1
    const val CAROUSEL_PREVIOUS = PREVIEW_MODEL + 1
    const val CAROUSEL_NEXT = PREVIEW_MODEL + 2
    const val RESULT_NAME = CAROUSEL_NEXT + 1
    const val KIT_NAME = RESULT_NAME + 1
    const val REQUIREMENT = KIT_NAME + 1
    const val COSMETIC = REQUIREMENT + 1
    const val PRICE_ICON = COSMETIC + 1
    const val PRICE = PRICE_ICON + 1
    const val BUY_LAYER = PRICE + 1
    const val BUY_GRAPHIC = BUY_LAYER + 1
    const val BUY_TEXT = BUY_LAYER + 2
    const val EMPTY_TEXT = BUY_TEXT + 1

    /**
     * Owner 2026-10-10 redesign: appended after every 2026-10-03 id, so ids 0..[EMPTY_TEXT] keep their meaning and a cache that
     * still holds the 2026-10-03 window simply lacks these. Category buttons: a clickable layer, its pill graphic and its label.
     */
    const val SECTION_FIRST = EMPTY_TEXT + 1
    const val SECTION_STRIDE = 3
    const val SECTION_COUNT = Art.SECTION_COUNT
    const val SECTION_GRAPHIC_OFFSET = 1
    const val SECTION_TEXT_OFFSET = 2

    /** Card lines: whether the player owns the base item, and the item's tradeability. */
    const val OWNED = SECTION_FIRST + SECTION_COUNT * SECTION_STRIDE
    const val TRADEABLE = OWNED + 1

    /** The painted Prev / Next buttons (owner 2026-10-11), drawn over the clickable [PAGE_PREVIOUS] / [PAGE_NEXT] areas. */
    const val PAGE_PREVIOUS_GRAPHIC = TRADEABLE + 1
    const val PAGE_NEXT_GRAPHIC = TRADEABLE + 2
    const val COMPONENT_COUNT = TRADEABLE + 3

    /**
     * Component ids of one grid size. Everything before the grid (0..14) is the same in every layout; everything after it moves
     * with the slot count. The server ([CURRENT] or [LEGACY]) picks the layout the cache actually holds by its component count,
     * so a server build that reaches players before this tool was applied (or the reverse) never addresses a missing component.
     */
    class Layout(val columns: Int, val rows: Int, val sections: Boolean = false) {
        val slotCount = columns * rows
        val slotBackgroundFirst = SLOT_BACKGROUND_FIRST
        val selectGlowFirst = slotBackgroundFirst + slotCount
        val slotFirst = selectGlowFirst + slotCount
        val selectOutlineFirst = slotFirst + slotCount
        val pagePrevious = selectOutlineFirst + slotCount
        val pageText = pagePrevious + 1
        val pageNext = pagePrevious + 2
        val previewLayer = pagePrevious + 3
        val previewModel = previewLayer + 1
        val carouselPrevious = previewModel + 1
        val carouselNext = previewModel + 2
        val resultName = carouselNext + 1
        val kitName = resultName + 1
        val requirement = kitName + 1
        val cosmetic = requirement + 1
        val priceIcon = cosmetic + 1
        val price = priceIcon + 1
        val buyLayer = price + 1
        val buyGraphic = buyLayer + 1
        val buyText = buyLayer + 2
        val emptyText = buyText + 1
        val sectionFirst = emptyText + 1
        val owned = sectionFirst + SECTION_COUNT * SECTION_STRIDE
        val tradeable = owned + 1
        val pagePreviousGraphic = tradeable + 1
        val pageNextGraphic = tradeable + 2

        /** The first 2026-10-10 window had the categories but not yet the painted page buttons. */
        val pageButtons = sections && columns * rows == GRID_COLUMNS * GRID_ROWS
        val componentCount = if (pageButtons) pageNextGraphic + 1 else if (sections) tradeable + 1 else emptyText + 1
    }

    /** The layout this tool builds (2026-10-10 v3: 7x7 grid plus category buttons and the owned / tradeable card lines). */
    val CURRENT = Layout(GRID_COLUMNS, GRID_ROWS, sections = true)

    /** The first 2026-10-10 window (7x6 grid with category buttons, 220 components). */
    val SECTIONS_7X6 = Layout(7, 6, sections = true)

    /** The 2026-10-03 window (7x6 grid, 200 components, no categories). */
    val PREVIOUS = Layout(7, 6)

    /** The 2026-09-22 layout (6x5 grid, 152 components) - what both caches hold until this tool's 2026-10-03 layout is applied. */
    val LEGACY = Layout(6, 5)

    /** The layout of a cache whose interface [INTERFACE_ID] has [componentCount] components (the legacy one for anything unknown). */
    fun layoutFor(componentCount: Int): Layout = listOf(CURRENT, SECTIONS_7X6, PREVIOUS).firstOrNull { it.componentCount == componentCount } ?: LEGACY

    const val GOLD = 0xFFD700
    const val WHITE = LootKeyInterfaceImportTool.WHITE
    const val ORANGE = LootKeyInterfaceImportTool.ORANGE
    const val CREAM = 0xEDE3C6
    const val GREEN = 0x5CE05C

    /** The preview model's box: above the painted pedestal, clear of the carousel arrows. */
    const val PREVIEW_X = Art.SHOW_X + 28
    const val PREVIEW_Y = Art.SHOW_Y + 6
    const val PREVIEW_WIDTH = Art.SHOW_WIDTH - 56
    const val PREVIEW_HEIGHT = Art.SHOW_HEIGHT - 26

    /**
     * Fallback scale for a client without the per-item fit: width * 512 / aspect, kept within the box for a long weapon's
     * ~48-unit diagonal and equal on both axes. 2026-10-03 box 161x118: 161/67 = 2.40x and 118/49 = 2.41x (a 48-unit
     * diagonal is 115.6 px of the 118 px box). 2026-10-10 box 284x134: 284/102 = 2.78x and 134/48 = 2.79x (a 48-unit diagonal fills
     * the 134 px height exactly).
     */
    const val PREVIEW_ASPECT_X = 102
    const val PREVIEW_ASPECT_Y = 48

    /**
     * Clientscript [NPC_PREVIEW_SCRIPT](component, npc, xan, yan): clears the item, sets the view angle and IF_SETNPCMODEL(npc). Owner 2026-10-10 ("de preview van de pets
     * kloppen niet ... liefst hoe die in game staat"): a pet is previewed as its full npc, as it follows you in game, not as its
     * inventory model (a head or bust for most OSRS pets). Revision 667 has no server packet for a full npc model in an interface
     * (IF_SETNPCHEAD is the chathead); only the clientscript instruction exists. The server animates it with IF_SETANIM.
     * The id leaves a gap after the cache's last script (5384, MansionMapIconTool's categories use 5381..).
     */
    const val NPC_PREVIEW_SCRIPT = 5395

    fun npcPreviewScript(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun u16(v: Int) {
            out.write(v ushr 8 and 0xFF)
            out.write(v and 0xFF)
        }
        fun i32(v: Int) {
            u16(v ushr 16)
            u16(v and 0xFFFF)
        }
        var count = 0
        fun op(opcode: Int, operand: Int, byteOperand: Boolean = false) {
            u16(opcode)
            if (byteOperand) out.write(operand and 0xFF) else i32(operand)
            count++
        }
        // Opcodes from this client's ClientScriptOpCode: IF_ variants are the CC_ ones + 1000 and pop their component last.
        val pushInt = 0
        val pushLocal = 33
        val ret = 21
        out.write(0) // no name
        // IF_SETOBJECT(-1, 0, component): the client draws the item model while invObject != -1; this clears it (ScriptRunner).
        op(pushInt, -1)
        op(pushInt, 0)
        op(pushLocal, 0)
        op(2200, 0, byteOperand = true) // IF_SETOBJECT (CC_IF_SETOBJECT 1200 + 1000)
        // IF_SETMODELANGLE(xof, yof, xan, yan, zan, zoom, component): the item's tilt would otherwise stay on the npc. The zoom only
        // places the camera; the client fits the model to the box (InterfaceManager.fitModelToBox).
        op(pushInt, 0)
        op(pushInt, 0)
        op(pushLocal, 2) // xan
        op(pushLocal, 3) // yan
        op(pushInt, 0)
        op(pushInt, 2000)
        op(pushLocal, 0)
        op(2109, 0, byteOperand = true) // IF_SETMODELANGLE (CC_IF_SETMODELANGLE 1109 + 1000)
        op(pushLocal, 1) // npc
        op(pushLocal, 0) // component
        op(2203, 0, byteOperand = true) // IF_SETNPCMODEL (CC_IF_SETNPCMODEL 1203 + 1000)
        op(ret, 0, byteOperand = true)
        // metadata: opcode count, int/string/long locals, int/string/long args, then the switch-table section (none).
        i32(count)
        u16(4)
        u16(0)
        u16(0)
        u16(4)
        u16(0)
        u16(0)
        out.write(0) // switch table count
        u16(1) // switch section length (the count byte)
        return out.toByteArray()
    }

    /**
     * The window is centred in the game view this many pixels left of the middle (owner client 2026-10-10, 978x709 resizable: centred,
     * the minimap and its orbs covered the window's top-right corner and the close button). Negative = left.
     */
    const val WINDOW_OFFSET_X = -120

    const val PAGE_PREVIOUS_X = Art.GRID_X + 4
    const val PAGE_NEXT_X = Art.GRID_X + Art.GRID_WIDTH - 4 - Art.PAGE_BUTTON_WIDTH

    /** Width of the owned status at the right of the requirement row. */
    const val OWNED_WIDTH = 110

    private const val TYPE_LAYER = 0
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5

    fun slotX(slot: Int) = Art.GRID_X + 2 + 40 * (slot % GRID_COLUMNS)

    fun slotY(slot: Int) = Art.GRID_Y + 2 + 40 * (slot / GRID_COLUMNS)

    fun components(fonts: LootKeyInterfaceImportTool.Fonts): List<Component> {
        val list = mutableListOf<Component>()
        fun add(c: Component) {
            require(list.none { it.id == c.id }) { "duplicate component ${c.id}" }
            list += c
        }
        fun layer(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, ops: List<String> = emptyList(), opBase: String = "") =
            add(Component(id, TYPE_LAYER, x, y, w, h, parent, ops = ops, opBase = opBase))
        fun graphic(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, sprite: Int, ops: List<String> = emptyList(), hidden: Boolean = false) =
            add(Component(id, TYPE_GRAPHIC, x, y, w, h, parent, graphic = sprite, ops = ops, hidden = hidden))
        fun text(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, font: Int, text: String, colour: Int, alignX: Int = 1, ops: List<String> = emptyList()) =
            add(Component(id, TYPE_TEXT, x, y, w, h, parent, font = font, text = text, colour = colour, alignX = alignX, alignY = 1, shadow = true, ops = ops))

        add(Component(ROOT, TYPE_LAYER, 0, 0, 0, 0, -1, resizeX = 1, resizeY = 1))
        add(Component(WINDOW, TYPE_LAYER, WINDOW_OFFSET_X, 0, WIDTH, HEIGHT, ROOT, reposX = 1, reposY = 1))
        for (i in 0 until TAB_COUNT) graphic(BACKGROUND_FIRST + i, 0, 0, WIDTH, HEIGHT, WINDOW, Art.BACKGROUND_FIRST + i, hidden = i != 0)
        graphic(CLOSE, WIDTH - 34, 14, Art.CLOSE_SIZE, Art.CLOSE_SIZE, WINDOW, Art.CLOSE, ops = listOf("Close"))

        val tabNames = listOf("Donator Shop", "Deadman Shop", "Loyalty Shop")
        for (i in 0 until TAB_COUNT) {
            val base = TAB_FIRST + i * TAB_STRIDE
            layer(base, Art.TAB_X + i * Art.TAB_PITCH, Art.TAB_Y, Art.TAB_WIDTH, Art.TAB_HEIGHT, WINDOW, ops = listOf("View"), opBase = tabNames[i])
            graphic(base + TAB_GRAPHIC_OFFSET, 0, 0, Art.TAB_WIDTH, Art.TAB_HEIGHT, base, if (i == 0) Art.TAB_SELECTED_FIRST else Art.TAB_NORMAL_FIRST + i)
        }

        // Info strip: tagline left, currency icon and balance right.
        text(TAGLINE, Art.STRIP_X + 10, Art.STRIP_Y, 400, Art.STRIP_HEIGHT, WINDOW, fonts.p12, "", CREAM, alignX = 0)
        graphic(BALANCE_ICON, Art.STRIP_X + Art.STRIP_WIDTH - 200, Art.STRIP_Y + 4, Art.ICON_SIZE, Art.ICON_SIZE, WINDOW, Art.ICON_FIRST)
        text(BALANCE, Art.STRIP_X + Art.STRIP_WIDTH - 180, Art.STRIP_Y, 172, Art.STRIP_HEIGHT, WINDOW, fonts.b12, "", WHITE, alignX = 0)

        // Item grid: glass slot, gold glow under the selected item, the item, gold ring over it.
        val slotOps = listOf("Select", "", "", "", "", "", "", "", "", "Examine")
        for (slot in 0 until SLOT_COUNT) graphic(SLOT_BACKGROUND_FIRST + slot, slotX(slot), slotY(slot), Art.SLOT_SIZE, Art.SLOT_SIZE, WINDOW, Art.SLOT)
        for (slot in 0 until SLOT_COUNT) graphic(SELECT_GLOW_FIRST + slot, slotX(slot), slotY(slot), Art.SLOT_SIZE, Art.SLOT_SIZE, WINDOW, Art.SLOT_GLOW, hidden = true)
        for (slot in 0 until SLOT_COUNT) add(Component(SLOT_FIRST + slot, TYPE_GRAPHIC, slotX(slot) + 2, slotY(slot) + 2, 32, 32, WINDOW, graphic = -1, ops = slotOps))
        for (slot in 0 until SLOT_COUNT) graphic(SELECT_OUTLINE_FIRST + slot, slotX(slot), slotY(slot), Art.SLOT_SIZE, Art.SLOT_SIZE, WINDOW, Art.SLOT_RING, hidden = true)

        val navY = Art.NAV_Y
        val navH = Art.NAV_HEIGHT
        // Owner 2026-09-29 "staat er niet de prev": the client parses "<" as the start of a text tag (<col=..>), so "< Prev" was never
        // drawn. Plain words, no angle brackets.
        // Owner 2026-10-11: the words are painted into the Prev / Next buttons ([PAGE_PREVIOUS_GRAPHIC]); these stay the click areas.
        val buttonY = navY + (navH - Art.PAGE_BUTTON_HEIGHT) / 2
        text(PAGE_PREVIOUS, PAGE_PREVIOUS_X, buttonY, Art.PAGE_BUTTON_WIDTH, Art.PAGE_BUTTON_HEIGHT, WINDOW, fonts.p12, "", GOLD, alignX = 0, ops = listOf("Previous page"))
        text(PAGE_TEXT, Art.GRID_X + 96, navY, Art.GRID_WIDTH - 192, navH, WINDOW, fonts.p12, "Page 1 / 1", CREAM)
        text(PAGE_NEXT, PAGE_NEXT_X, buttonY, Art.PAGE_BUTTON_WIDTH, Art.PAGE_BUTTON_HEIGHT, WINDOW, fonts.p12, "", GOLD, alignX = 2, ops = listOf("Next page"))

        // Showcase: the result item's model standing on the painted pedestal, carousel arrows at the sides.
        layer(PREVIEW_LAYER, PREVIEW_X, PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT, WINDOW)
        add(
            Component(
                PREVIEW_MODEL, LootKeyInterfaceImportTool.TYPE_MODEL, 0, 0, 0, 0, PREVIEW_LAYER,
                resizeX = 1, resizeY = 1, aspectX = PREVIEW_ASPECT_X, aspectY = PREVIEW_ASPECT_Y,
            ),
        )
        val arrowY = Art.SHOW_Y + (Art.SHOW_HEIGHT - Art.ARROW_HEIGHT) / 2 - 6
        graphic(CAROUSEL_PREVIOUS, Art.SHOW_X + 4, arrowY, Art.ARROW_WIDTH, Art.ARROW_HEIGHT, WINDOW, Art.ARROW_LEFT, ops = listOf("Previous"))
        graphic(CAROUSEL_NEXT, Art.SHOW_X + Art.SHOW_WIDTH - 4 - Art.ARROW_WIDTH, arrowY, Art.ARROW_WIDTH, Art.ARROW_HEIGHT, WINDOW, Art.ARROW_RIGHT, ops = listOf("Next"))

        // Item card (owner 2026-10-10 "betere leesbaarheid"): every fact on its own p12 line - name, tier + item, requirement, owned,
        // tradeable, effect - and the price in the lit band at the bottom.
        val cx = Art.CARD_X + 10
        val cw = Art.CARD_WIDTH - 20
        val cy = Art.CARD_Y
        text(RESULT_NAME, cx, cy + 5, cw, 16, WINDOW, fonts.b12, "", WHITE, alignX = 0)
        text(KIT_NAME, cx, cy + 28, cw, 14, WINDOW, fonts.p12, "", ORANGE, alignX = 0)
        text(REQUIREMENT, cx, cy + 44, cw - OWNED_WIDTH, 14, WINDOW, fonts.p12, "", CREAM, alignX = 0)
        text(COSMETIC, cx, cy + 76, cw, 14, WINDOW, fonts.p12, "", GREEN, alignX = 0)
        val bandY = cy + Art.CARD_HEIGHT - Art.PRICE_BAND_HEIGHT - 3
        graphic(PRICE_ICON, cx, bandY + 4, Art.ICON_SIZE, Art.ICON_SIZE, WINDOW, Art.ICON_FIRST)
        text(PRICE, cx + 22, bandY, cw - 22, Art.PRICE_BAND_HEIGHT, WINDOW, fonts.b12, "", GOLD, alignX = 0)

        val buyX = Art.CARD_X + (Art.CARD_WIDTH - Art.BUY_WIDTH) / 2
        // Owner 2026-10-10: items and kits are bought 1 / 5 / 10 / X at a time (StoreUi.buyClicked); right-click lists the amounts.
        layer(BUY_LAYER, buyX, Art.CARD_Y + Art.CARD_HEIGHT + 4, Art.BUY_WIDTH, Art.BUY_HEIGHT, WINDOW, ops = listOf("Buy", "Buy 5", "Buy 10", "Buy X"))
        graphic(BUY_GRAPHIC, 0, 0, Art.BUY_WIDTH, Art.BUY_HEIGHT, BUY_LAYER, Art.BUY)
        text(BUY_TEXT, 0, 0, Art.BUY_WIDTH, Art.BUY_HEIGHT, BUY_LAYER, fonts.b12, "Buy", WHITE)
        text(EMPTY_TEXT, Art.GRID_X + 4, Art.GRID_Y + 4, Art.GRID_WIDTH - 8, Art.GRID_HEIGHT - 8, WINDOW, fonts.p12, "", CREAM)

        // Category buttons; the server labels them (only the categories a shop has) and lights the chosen one.
        for (i in 0 until SECTION_COUNT) {
            val base = SECTION_FIRST + i * SECTION_STRIDE
            layer(base, Art.SECTION_X + i * Art.SECTION_PITCH, Art.SECTION_Y, Art.SECTION_WIDTH, Art.SECTION_HEIGHT, WINDOW, ops = listOf("View"))
            graphic(base + SECTION_GRAPHIC_OFFSET, 0, 0, Art.SECTION_WIDTH, Art.SECTION_HEIGHT, base, Art.SECTION_NORMAL)
            text(base + SECTION_TEXT_OFFSET, 0, 0, Art.SECTION_WIDTH, Art.SECTION_HEIGHT, base, fonts.p12, "", CREAM)
        }
        // Owned sits right-aligned on the requirement's row ("Needs: Amulet of fury ........ Not owned").
        text(OWNED, cx + cw - OWNED_WIDTH, cy + 44, OWNED_WIDTH, 14, WINDOW, fonts.p12, "", CREAM, alignX = 2)
        text(TRADEABLE, cx, cy + 60, cw, 14, WINDOW, fonts.p12, "", CREAM, alignX = 0)
        graphic(PAGE_PREVIOUS_GRAPHIC, PAGE_PREVIOUS_X, buttonY, Art.PAGE_BUTTON_WIDTH, Art.PAGE_BUTTON_HEIGHT, WINDOW, Art.PAGE_BUTTON_FIRST + 2)
        graphic(PAGE_NEXT_GRAPHIC, PAGE_NEXT_X, buttonY, Art.PAGE_BUTTON_WIDTH, Art.PAGE_BUTTON_HEIGHT, WINDOW, Art.PAGE_BUTTON_FIRST + 3)

        require(list.size == COMPONENT_COUNT) { "expected $COMPONENT_COUNT components, built ${list.size}" }
        require(list.map { it.id }.sorted() == (0 until COMPONENT_COUNT).toList()) { "component ids must be 0..${COMPONENT_COUNT - 1}" }
        return list.sortedBy { it.id }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = com.displee.cache.CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        val fonts: LootKeyInterfaceImportTool.Fonts
        val current: Map<Int, String>
        val art = Art.all()
        val currentArt: Map<Int, String?>
        val currentScript: String?
        try {
            fonts = LootKeyInterfaceImportTool.fonts(library)
            // Interface 1151 belongs to this tool: pin what is there so a re-run replaces its own earlier layout.
            val files = library.index(LootKeyInterfaceImportTool.INDEX_INTERFACES).archive(INTERFACE_ID)?.fileIds()?.toList() ?: emptyList()
            current = files.associateWith { f -> CacheItemProbeTool.sha1(library.data(LootKeyInterfaceImportTool.INDEX_INTERFACES, INTERFACE_ID, f)!!) }
            currentArt = art.keys.associateWith { id -> library.data(LootKeyInterfaceImportTool.INDEX_SPRITES, id, 0)?.let(CacheItemProbeTool::sha1) }
            currentScript = library.data(DeathsOfficeInterfaceImportTool.INDEX_CLIENTSCRIPTS, NPC_PREVIEW_SCRIPT, 0)?.let(CacheItemProbeTool::sha1)
        } finally {
            library.close()
        }
        args.firstOrNull { it.startsWith("--png=") }?.substringAfter('=')?.let { dir ->
            java.io.File(dir).mkdirs()
            art.forEach { (id, image) -> javax.imageio.ImageIO.write(image, "png", java.io.File(dir, "store_$id.png")) }
            println("PNG written to $dir")
        }
        val built = components(fonts)
        val writes =
            built.map { c ->
                CacheMutation(
                    LootKeyInterfaceImportTool.INDEX_INTERFACES,
                    INTERFACE_ID,
                    c.id,
                    LootKeyInterfaceImportTool.encode(c),
                    "interface $INTERFACE_ID:${c.id} type=${c.type}",
                    expectedCurrentSha1 = current[c.id],
                )
            }
        val removals =
            current.keys.filter { it >= COMPONENT_COUNT }.sorted().map { f ->
                CacheMutation(LootKeyInterfaceImportTool.INDEX_INTERFACES, INTERFACE_ID, f, null, "interface $INTERFACE_ID:$f (old layout)")
            }
        // The painted art is sized for this layout (2026-10-03: 527x360 window), so it goes in the same transaction: a cache never
        // holds the new window with the old art or the reverse.
        val artWrites =
            art.map { (id, image) ->
                CacheMutation(LootKeyInterfaceImportTool.INDEX_SPRITES, id, 0, Art.encode(image), "store art sprite $id", expectedCurrentSha1 = currentArt[id])
            }
        // The npc preview script belongs to this tool too (pinned to what is there, so a re-run replaces only its own script).
        val scriptWrite =
            CacheMutation(
                DeathsOfficeInterfaceImportTool.INDEX_CLIENTSCRIPTS, NPC_PREVIEW_SCRIPT, 0, npcPreviewScript(),
                "clientscript $NPC_PREVIEW_SCRIPT (store npc preview)", expectedCurrentSha1 = currentScript,
            )
        val mutations = writes + removals + artWrites + scriptWrite
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interface $INTERFACE_ID")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) {
            println("VERIFY_OK transaction=${transaction.id}")
        } else {
            problems.forEach { println("  VERIFY_PROBLEM: $it") }
            error("verify failed")
        }
    }
}
