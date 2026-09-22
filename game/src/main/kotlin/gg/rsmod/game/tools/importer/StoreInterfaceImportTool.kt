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
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.StoreInterfaceImportTool [--apply]`. The tool owns interface
 * [INTERFACE_ID]: a re-run replaces its own components (pinned to what is there) and removes any left over from an older
 * layout.
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

    const val GRID_COLUMNS = 6
    const val GRID_ROWS = 5
    const val SLOT_COUNT = GRID_COLUMNS * GRID_ROWS
    const val SLOT_BACKGROUND_FIRST = 15 // 15..44
    const val SELECT_GLOW_FIRST = SLOT_BACKGROUND_FIRST + SLOT_COUNT // 45..74
    const val SLOT_FIRST = SELECT_GLOW_FIRST + SLOT_COUNT // 75..104
    const val SELECT_OUTLINE_FIRST = SLOT_FIRST + SLOT_COUNT // 105..134

    const val PAGE_PREVIOUS = SELECT_OUTLINE_FIRST + SLOT_COUNT // 135
    const val PAGE_TEXT = PAGE_PREVIOUS + 1
    const val PAGE_NEXT = PAGE_PREVIOUS + 2
    const val PREVIEW_LAYER = PAGE_PREVIOUS + 3 // 138
    const val PREVIEW_MODEL = PREVIEW_LAYER + 1 // 139
    const val CAROUSEL_PREVIOUS = PREVIEW_MODEL + 1
    const val CAROUSEL_NEXT = PREVIEW_MODEL + 2 // 141
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
    const val COMPONENT_COUNT = EMPTY_TEXT + 1

    const val GOLD = 0xFFD700
    const val WHITE = LootKeyInterfaceImportTool.WHITE
    const val ORANGE = LootKeyInterfaceImportTool.ORANGE
    const val CREAM = 0xEDE3C6
    const val GREEN = 0x5CE05C

    /** The preview model's box: above the painted pedestal, clear of the carousel arrows. */
    const val PREVIEW_X = Art.SHOW_X + 24
    const val PREVIEW_Y = Art.SHOW_Y + 6
    const val PREVIEW_WIDTH = Art.SHOW_WIDTH - 48
    const val PREVIEW_HEIGHT = Art.SHOW_HEIGHT - 20

    /**
     * Fallback scale for a client without the per-item fit: width * 512 / aspect, kept within the box for a long weapon's
     * ~48-unit diagonal and equal on both axes.
     */
    const val PREVIEW_ASPECT_X = 80
    const val PREVIEW_ASPECT_Y = 50

    private const val TYPE_LAYER = 0
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5

    fun slotX(slot: Int) = Art.GRID_X + 2 + 40 * (slot % GRID_COLUMNS)

    fun slotY(slot: Int) = Art.GRID_Y + 2 + 38 * (slot / GRID_COLUMNS)

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
        add(Component(WINDOW, TYPE_LAYER, 0, 0, WIDTH, HEIGHT, ROOT, reposX = 1, reposY = 1))
        for (i in 0 until TAB_COUNT) graphic(BACKGROUND_FIRST + i, 0, 0, WIDTH, HEIGHT, WINDOW, Art.BACKGROUND_FIRST + i, hidden = i != 0)
        graphic(CLOSE, WIDTH - 34, 12, Art.CLOSE_SIZE, Art.CLOSE_SIZE, WINDOW, Art.CLOSE, ops = listOf("Close"))

        val tabNames = listOf("Donator Shop", "Deadman Shop", "Loyalty Shop")
        for (i in 0 until TAB_COUNT) {
            val base = TAB_FIRST + i * TAB_STRIDE
            layer(base, Art.TAB_X + i * Art.TAB_PITCH, Art.TAB_Y, Art.TAB_WIDTH, Art.TAB_HEIGHT, WINDOW, ops = listOf("View"), opBase = tabNames[i])
            graphic(base + TAB_GRAPHIC_OFFSET, 0, 0, Art.TAB_WIDTH, Art.TAB_HEIGHT, base, if (i == 0) Art.TAB_SELECTED_FIRST else Art.TAB_NORMAL_FIRST + i)
        }

        // Info strip: tagline left, currency icon and balance right.
        text(TAGLINE, Art.STRIP_X + 10, Art.STRIP_Y, 290, Art.STRIP_HEIGHT, WINDOW, fonts.p11, "", CREAM, alignX = 0)
        graphic(BALANCE_ICON, Art.STRIP_X + 306, Art.STRIP_Y + 2, Art.ICON_SIZE, Art.ICON_SIZE, WINDOW, Art.ICON_FIRST)
        text(BALANCE, Art.STRIP_X + 326, Art.STRIP_Y, 128, Art.STRIP_HEIGHT, WINDOW, fonts.b12, "", WHITE, alignX = 0)

        // Item grid: glass slot, gold glow under the selected item, the item, gold ring over it.
        val slotOps = listOf("Select", "", "", "", "", "", "", "", "", "Examine")
        for (slot in 0 until SLOT_COUNT) graphic(SLOT_BACKGROUND_FIRST + slot, slotX(slot), slotY(slot), Art.SLOT_SIZE, Art.SLOT_SIZE, WINDOW, Art.SLOT)
        for (slot in 0 until SLOT_COUNT) graphic(SELECT_GLOW_FIRST + slot, slotX(slot), slotY(slot), Art.SLOT_SIZE, Art.SLOT_SIZE, WINDOW, Art.SLOT_GLOW, hidden = true)
        for (slot in 0 until SLOT_COUNT) add(Component(SLOT_FIRST + slot, TYPE_GRAPHIC, slotX(slot) + 2, slotY(slot) + 2, 32, 32, WINDOW, graphic = -1, ops = slotOps))
        for (slot in 0 until SLOT_COUNT) graphic(SELECT_OUTLINE_FIRST + slot, slotX(slot), slotY(slot), Art.SLOT_SIZE, Art.SLOT_SIZE, WINDOW, Art.SLOT_RING, hidden = true)

        val navY = Art.GRID_Y + Art.GRID_HEIGHT + 2
        text(PAGE_PREVIOUS, Art.GRID_X + 4, navY, 60, 16, WINDOW, fonts.p12, "< Prev", GOLD, alignX = 0, ops = listOf("Previous page"))
        text(PAGE_TEXT, Art.GRID_X + 64, navY, 116, 16, WINDOW, fonts.p12, "Page 1 / 1", CREAM)
        text(PAGE_NEXT, Art.GRID_X + Art.GRID_WIDTH - 64, navY, 60, 16, WINDOW, fonts.p12, "Next >", GOLD, alignX = 2, ops = listOf("Next page"))

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

        // Item card: name, tier + kit, requirement, effect line and the price with its currency icon.
        val cx = Art.CARD_X + 10
        val cw = Art.CARD_WIDTH - 20
        text(RESULT_NAME, cx, Art.CARD_Y + 2, cw, 14, WINDOW, fonts.b12, "", WHITE, alignX = 0)
        text(KIT_NAME, cx, Art.CARD_Y + 15, cw, 12, WINDOW, fonts.p11, "", ORANGE, alignX = 0)
        text(REQUIREMENT, cx, Art.CARD_Y + 27, cw, 12, WINDOW, fonts.p11, "", CREAM, alignX = 0)
        text(COSMETIC, cx, Art.CARD_Y + 39, cw, 12, WINDOW, fonts.p11, "", GREEN, alignX = 0)
        graphic(PRICE_ICON, cx - 2, Art.CARD_Y + 50, Art.ICON_SIZE, Art.ICON_SIZE, WINDOW, Art.ICON_FIRST)
        text(PRICE, cx + 18, Art.CARD_Y + 51, cw - 18, 14, WINDOW, fonts.b12, "", GOLD, alignX = 0)

        val buyX = Art.CARD_X + (Art.CARD_WIDTH - Art.BUY_WIDTH) / 2
        layer(BUY_LAYER, buyX, Art.CARD_Y + Art.CARD_HEIGHT + 4, Art.BUY_WIDTH, Art.BUY_HEIGHT, WINDOW, ops = listOf("Buy"))
        graphic(BUY_GRAPHIC, 0, 0, Art.BUY_WIDTH, Art.BUY_HEIGHT, BUY_LAYER, Art.BUY)
        text(BUY_TEXT, 0, 0, Art.BUY_WIDTH, Art.BUY_HEIGHT, BUY_LAYER, fonts.b12, "Buy", WHITE)
        text(EMPTY_TEXT, Art.GRID_X + 4, Art.GRID_Y + 4, Art.GRID_WIDTH - 8, Art.GRID_HEIGHT - 8, WINDOW, fonts.p12, "", CREAM)

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
        try {
            fonts = LootKeyInterfaceImportTool.fonts(library)
            // Interface 1151 belongs to this tool: pin what is there so a re-run replaces its own earlier layout.
            val files = library.index(LootKeyInterfaceImportTool.INDEX_INTERFACES).archive(INTERFACE_ID)?.fileIds()?.toList() ?: emptyList()
            current = files.associateWith { f -> CacheItemProbeTool.sha1(library.data(LootKeyInterfaceImportTool.INDEX_INTERFACES, INTERFACE_ID, f)!!) }
        } finally {
            library.close()
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
        val mutations = writes + removals
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
