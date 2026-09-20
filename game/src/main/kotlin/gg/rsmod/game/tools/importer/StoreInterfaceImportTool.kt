package gg.rsmod.game.tools.importer

import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool.Component

/**
 * Builds the "78 Store" window (owner night-run 2026-09-19, batches 4 and 5) as the revision-667 if3 interface [INTERFACE_ID],
 * through the same transactional, both-caches route as [LootKeyInterfaceImportTool] and [DizanasQuiverInterfaceImportTool].
 *
 * Owner flow: "kit in shop-grid -> selected kit -> big preview of the ornamented result -> base item requirement -> stats
 * unchanged / cosmetic only -> price and currency balance -> Buy kit", with a carousel when one kit has several results
 * (Infinity hat/top/bottoms, Elder chaos, Dagon'hai, Twisted ancestral, Malediction/Odium ward, max-cape variants).
 *
 * Art: only sprites already in both caches - the OSRS stone frame, dividers, square slot button and grey/red button caps imported
 * by the loot-key window ([LootKeyInterfaceImportTool.sprite]) and the quiver's close button. The preview is a model component
 * whose aspect ratio enlarges the item model five times; the server fills it with IF_SETOBJECT (the client then renders the
 * result item's own model with its inventory angles), the grid slots with IF_SETOBJECT, the texts with IF_SETTEXT, the tab
 * highlight with IF_SETGRAPHIC and enables every op with IF_SETEVENTS (content: `store.plugin.kts`).
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.StoreInterfaceImportTool [--apply] [--replace-from-journal=<tx id>]`
 */
object StoreInterfaceImportTool {
    /** First interface id above the Dizana's quiver window (1150). */
    const val INTERFACE_ID = 1151

    const val WIDTH = 488
    const val HEIGHT = 330

    const val ROOT = 0
    const val WINDOW = 1
    const val TITLE = 12
    const val CLOSE = 13

    /** Three shop tabs: layer (op), left cap, middle, right cap, label. */
    const val TAB_FIRST = 16
    const val TAB_STRIDE = 5
    const val TAB_COUNT = 3

    /*
     * Visual pass (owner 2026-09-19: "make them beautiful, the ui must be top notch"). The 667 server has no IF_SETCOLOUR /
     * IF_SETPOSITION, so every per-shop colour and the selection highlight exist once per theme / slot and the server only
     * shows or hides them (IF_SETHIDE). The client draws a parent's children in component order, so backgrounds get the
     * lower ids and outlines/overlays the higher ones.
     */

    /** Header strip under the tabs: one filled rectangle per shop theme, the shop tagline and the balance. */
    const val BANNER_FIRST = TAB_FIRST + TAB_COUNT * TAB_STRIDE // 31..33
    const val TAGLINE = BANNER_FIRST + TAB_COUNT // 34
    const val BALANCE = TAGLINE + 1 // 35

    /** Dark panel behind the grid plus one outline per shop theme. */
    const val GRID_PANEL = BALANCE + 1 // 36
    const val GRID_OUTLINE_FIRST = GRID_PANEL + 1 // 37..39

    const val GRID_COLUMNS = 6
    const val GRID_ROWS = 5
    const val SLOT_COUNT = GRID_COLUMNS * GRID_ROWS
    const val SLOT_BACKGROUND_FIRST = GRID_OUTLINE_FIRST + TAB_COUNT // 40..69

    /** Selected slot: a soft gold glow under the item and a gold outline over it. */
    const val SELECT_GLOW_FIRST = SLOT_BACKGROUND_FIRST + SLOT_COUNT // 70..99
    const val SLOT_FIRST = SELECT_GLOW_FIRST + SLOT_COUNT // 100..129
    const val SELECT_OUTLINE_FIRST = SLOT_FIRST + SLOT_COUNT // 130..159

    const val PAGE_PREVIOUS = SELECT_OUTLINE_FIRST + SLOT_COUNT // 160
    const val PAGE_TEXT = PAGE_PREVIOUS + 1
    const val PAGE_NEXT = PAGE_PREVIOUS + 2
    const val PREVIEW_LAYER = PAGE_PREVIOUS + 3 // 163
    const val PREVIEW_BACKGROUND = PREVIEW_LAYER + 1
    const val PREVIEW_OUTLINE_FIRST = PREVIEW_LAYER + 2 // 165..167
    const val PREVIEW_MODEL = PREVIEW_OUTLINE_FIRST + TAB_COUNT // 168
    const val CAROUSEL_PREVIOUS = PREVIEW_MODEL + 1
    const val CAROUSEL_NEXT = PREVIEW_MODEL + 2 // 170
    const val RESULT_NAME = 171
    const val KIT_NAME = 172
    const val REQUIREMENT = 173
    const val COSMETIC = 174
    const val PRICE = 175
    const val BUY_LAYER = 176
    const val BUY_LEFT = 177
    const val BUY_MIDDLE = 178
    const val BUY_RIGHT = 179
    const val BUY_TEXT = 180
    const val EMPTY_TEXT = 181
    const val COMPONENT_COUNT = 182

    /** Theme colour per shop tab (Donator gold, Deadman crimson, Loyalty azure), in tab order. */
    val THEME_COLOURS = intArrayOf(0xC9A227, 0xA11818, 0x2B8FBF)
    const val GOLD = 0xFFD700

    /** Loot-key sprite ids (OSRS numbering) of the grey (unselected) and red (selected) button caps. */
    const val GREY_LEFT = 1229
    const val GREY_MIDDLE = 1230
    const val GREY_RIGHT = 1231
    const val RED_LEFT = 1232
    const val RED_MIDDLE = 1233
    const val RED_RIGHT = 1234

    const val ORANGE = LootKeyInterfaceImportTool.ORANGE
    const val WHITE = LootKeyInterfaceImportTool.WHITE
    const val GREEN = 0x3CD33C
    const val LINE = LootKeyInterfaceImportTool.LINE

    /** Tab geometry, exposed for the server (tab i at x = TAB_X + i * TAB_PITCH). */
    const val TAB_X = 12
    const val TAB_Y = 36
    const val TAB_WIDTH = 150
    const val TAB_PITCH = 156

    /**
     * Preview box and its model scale. The client scales the item model by width * 512 / aspectX (InterfaceManager, model
     * component), i.e. PREVIEW_WIDTH / PREVIEW_ASPECT_X times its inventory size. The old 5x cropped tall items (owner screenshot
     * "shop ui.png": Dragon boots (g) cut off).
     *
     * 3.75x was then chosen on the assumption that the inventory drawing is 36 x 32, so 3.75 x 34 fits 128 of height. That
     * holds for a boot; it does not hold for a long weapon, which is drawn corner to corner across its inventory box and so
     * is nearer 45 units tall - at 3.75x that is ~168px in a 128px panel. A model component is not clipped to its box, so the
     * overflow is drawn straight over the window frame (owner screenshot "shop visual.png", 2026-09-20: the Steam battlestaff
     * spilling out of the top of the preview).
     *
     * 3.0x was then chosen, and it is still too big: 128 / 43 is 2.98, and 2.98 x the ~45-unit diagonal of a long weapon is
     * 134px in a 128px panel. That is the owner's 2026-09-20 report, "the preview of the items is still not fitting inside
     * the panel" - and the 3.0x values had in any case never reached the cache, whose last store write (2026-09-19 20:51)
     * predates them, so the client was still drawing the 3.75x version.
     *
     * 2.67x (128 / 48) puts that same diagonal at 120px, inside the panel with a margin, and still fills most of it for a
     * compact item. The horizontal scale is kept within a percent of it (210 / 78 = 2.69) so the model is not stretched.
     *
     * Scale alone cannot be a guarantee, though, because the cache holds one scale and item models are not one size. The
     * client now also clips a model component's render to its own box (`InterfaceManager.clipModelComponents`), so an item
     * nobody anticipated can no longer be drawn over the window frame - it is only ever cropped inside the panel.
     */
    const val PREVIEW_X = 266
    const val PREVIEW_Y = 88
    const val PREVIEW_WIDTH = 210
    const val PREVIEW_HEIGHT = 128
    const val PREVIEW_ASPECT_X = 78
    const val PREVIEW_ASPECT_Y = 48

    const val GRID_X = 12
    const val GRID_Y = 88
    const val GRID_WIDTH = 246
    const val GRID_HEIGHT = 194

    private const val TYPE_LAYER = 0
    private const val TYPE_RECTANGLE = 3
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5
    private const val TYPE_LINE = 9

    fun components(fonts: LootKeyInterfaceImportTool.Fonts): List<Component> {
        val list = mutableListOf<Component>()
        fun add(c: Component) {
            require(list.none { it.id == c.id }) { "duplicate component ${c.id}" }
            list += c
        }
        val s = LootKeyInterfaceImportTool::sprite
        fun layer(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, ops: List<String> = emptyList(), opBase: String = "", hidden: Boolean = false) =
            add(Component(id, TYPE_LAYER, x, y, w, h, parent, ops = ops, opBase = opBase, hidden = hidden))
        fun graphic(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, sprite: Int, tiling: Boolean = false, ops: List<String> = emptyList()) =
            add(Component(id, TYPE_GRAPHIC, x, y, w, h, parent, graphic = sprite, tiling = tiling, ops = ops))
        fun text(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, font: Int, text: String, colour: Int, alignX: Int = 1, ops: List<String> = emptyList(), opBase: String = "") =
            add(Component(id, TYPE_TEXT, x, y, w, h, parent, font = font, text = text, colour = colour, alignX = alignX, alignY = 1, shadow = true, ops = ops, opBase = opBase))

        add(Component(ROOT, TYPE_LAYER, 0, 0, 0, 0, -1, resizeX = 1, resizeY = 1))
        add(Component(WINDOW, TYPE_LAYER, 0, 0, WIDTH, HEIGHT, ROOT, reposX = 1, reposY = 1))
        // Stone frame (loot-key geometry: 32x32 edge canvases with the art 13 px in).
        graphic(2, 1, 1, WIDTH - 2, HEIGHT - 2, WINDOW, s(297), tiling = true)
        graphic(3, 32, -13, WIDTH - 64, 20, WINDOW, s(820), tiling = true)
        graphic(4, 32, HEIGHT - 20, WIDTH - 64, 20, WINDOW, s(822), tiling = true)
        graphic(5, -13, 32, 20, HEIGHT - 64, WINDOW, s(821), tiling = true)
        graphic(6, WIDTH - 20, 32, 20, HEIGHT - 64, WINDOW, s(823), tiling = true)
        graphic(7, 0, 0, 32, 32, WINDOW, s(824))
        graphic(8, WIDTH - 32, 0, 32, 32, WINDOW, s(825))
        graphic(9, 0, HEIGHT - 32, 32, 32, WINDOW, s(826))
        graphic(10, WIDTH - 32, HEIGHT - 32, 32, 32, WINDOW, s(827))
        graphic(11, 11, 15, WIDTH - 22, 20, WINDOW, s(828), tiling = true)
        text(TITLE, 0, 5, WIDTH, 20, WINDOW, fonts.b12, "78 Store", ORANGE)
        graphic(CLOSE, WIDTH - 26, 10, 16, 16, WINDOW, s(831), ops = listOf("Close"))
        graphic(14, 0, 17, 32, 32, WINDOW, s(829))
        graphic(15, WIDTH - 11, 17, 11, 32, WINDOW, s(830))

        val tabNames = listOf("Donator Shop", "Deadman Shop", "Loyalty Shop")
        for (i in 0 until TAB_COUNT) {
            val base = TAB_FIRST + i * TAB_STRIDE
            layer(base, TAB_X + i * TAB_PITCH, TAB_Y, TAB_WIDTH, 22, WINDOW, ops = listOf("View"), opBase = tabNames[i])
            graphic(base + 1, 0, 0, 6, 22, base, s(GREY_LEFT))
            graphic(base + 2, 6, 0, TAB_WIDTH - 12, 22, base, s(GREY_MIDDLE), tiling = true)
            graphic(base + 3, TAB_WIDTH - 6, 0, 6, 22, base, s(GREY_RIGHT))
            text(base + 4, 0, 0, TAB_WIDTH, 22, base, fonts.p12, tabNames[i], ORANGE)
        }

        fun rect(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, colour: Int, filled: Boolean, transparency: Int = 0, hidden: Boolean = false) =
            add(Component(id, TYPE_RECTANGLE, x, y, w, h, parent, colour = colour, filled = filled, transparency = transparency, hidden = hidden))

        // Header strip: the active shop's theme colour (the other two hidden), its tagline left and the balance right.
        for (i in 0 until TAB_COUNT) rect(BANNER_FIRST + i, GRID_X, 62, WIDTH - 2 * GRID_X, 22, WINDOW, THEME_COLOURS[i], filled = true, transparency = 120, hidden = i != 0)
        text(TAGLINE, GRID_X + 6, 62, 290, 22, WINDOW, fonts.p11, "", WHITE, alignX = 0)
        text(BALANCE, WIDTH - GRID_X - 186, 62, 180, 22, WINDOW, fonts.b12, "", WHITE, alignX = 2)

        // Grid panel: dark glass behind the slots with the theme outline.
        rect(GRID_PANEL, GRID_X, GRID_Y, GRID_WIDTH, GRID_HEIGHT, WINDOW, 0, filled = true, transparency = 150)
        for (i in 0 until TAB_COUNT) rect(GRID_OUTLINE_FIRST + i, GRID_X, GRID_Y, GRID_WIDTH, GRID_HEIGHT, WINDOW, THEME_COLOURS[i], filled = false, hidden = i != 0)

        // 6 x 5 grid, 40 x 38 pitch, square slot buttons with the item sprite on top; glow under and outline over the selection.
        val slotOps = listOf("Select", "", "", "", "", "", "", "", "", "Examine")
        fun slotX(slot: Int) = GRID_X + 3 + 40 * (slot % GRID_COLUMNS)
        fun slotY(slot: Int) = GRID_Y + 3 + 38 * (slot / GRID_COLUMNS)
        for (slot in 0 until SLOT_COUNT) graphic(SLOT_BACKGROUND_FIRST + slot, slotX(slot), slotY(slot), 36, 36, WINDOW, s(170))
        for (slot in 0 until SLOT_COUNT) rect(SELECT_GLOW_FIRST + slot, slotX(slot) + 1, slotY(slot) + 1, 34, 34, WINDOW, GOLD, filled = true, transparency = 185, hidden = true)
        for (slot in 0 until SLOT_COUNT) add(Component(SLOT_FIRST + slot, TYPE_GRAPHIC, slotX(slot) + 2, slotY(slot) + 2, 32, 32, WINDOW, graphic = -1, ops = slotOps))
        for (slot in 0 until SLOT_COUNT) rect(SELECT_OUTLINE_FIRST + slot, slotX(slot), slotY(slot), 36, 36, WINDOW, GOLD, filled = false, hidden = true)
        val navY = GRID_Y + GRID_HEIGHT + 6
        text(PAGE_PREVIOUS, GRID_X + 2, navY, 60, 16, WINDOW, fonts.p12, "< Prev", ORANGE, alignX = 0, ops = listOf("Previous page"))
        text(PAGE_TEXT, GRID_X + 62, navY, 122, 16, WINDOW, fonts.p12, "Page 1 / 1", WHITE)
        text(PAGE_NEXT, GRID_X + 184, navY, 60, 16, WINDOW, fonts.p12, "Next >", ORANGE, alignX = 2, ops = listOf("Next page"))

        // Preview: dark glass with the theme outline and the result item's model (whole item visible), carousel arrows inside.
        layer(PREVIEW_LAYER, PREVIEW_X, PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT, WINDOW)
        rect(PREVIEW_BACKGROUND, 0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_LAYER, 0, filled = true, transparency = 120)
        for (i in 0 until TAB_COUNT) rect(PREVIEW_OUTLINE_FIRST + i, 0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_LAYER, THEME_COLOURS[i], filled = false, hidden = i != 0)
        add(
            Component(
                PREVIEW_MODEL, LootKeyInterfaceImportTool.TYPE_MODEL, 0, 0, 0, 0, PREVIEW_LAYER,
                resizeX = 1, resizeY = 1, aspectX = PREVIEW_ASPECT_X, aspectY = PREVIEW_ASPECT_Y,
            ),
        )
        val arrowY = PREVIEW_Y + (PREVIEW_HEIGHT - 20) / 2
        text(CAROUSEL_PREVIOUS, PREVIEW_X + 2, arrowY, 20, 20, WINDOW, fonts.b12, "<", GOLD, ops = listOf("Previous"))
        text(CAROUSEL_NEXT, PREVIEW_X + PREVIEW_WIDTH - 22, arrowY, 20, 20, WINDOW, fonts.b12, ">", GOLD, ops = listOf("Next"))

        // Item card: name, tier + kit, requirement, effect line, price, then the Buy (two-step confirm) button.
        val cardY = PREVIEW_Y + PREVIEW_HEIGHT + 3
        text(RESULT_NAME, PREVIEW_X, cardY, PREVIEW_WIDTH, 15, WINDOW, fonts.b12, "", WHITE)
        text(KIT_NAME, PREVIEW_X, cardY + 15, PREVIEW_WIDTH, 13, WINDOW, fonts.p11, "", ORANGE)
        text(REQUIREMENT, PREVIEW_X, cardY + 28, PREVIEW_WIDTH, 13, WINDOW, fonts.p11, "", WHITE)
        text(COSMETIC, PREVIEW_X, cardY + 41, PREVIEW_WIDTH, 13, WINDOW, fonts.p11, "", GREEN)
        text(PRICE, PREVIEW_X, cardY + 54, PREVIEW_WIDTH, 15, WINDOW, fonts.b12, "", GOLD)
        val buyWidth = 130
        layer(BUY_LAYER, PREVIEW_X + (PREVIEW_WIDTH - buyWidth) / 2, cardY + 71, buyWidth, 22, WINDOW, ops = listOf("Buy"))
        graphic(BUY_LEFT, 0, 0, 6, 22, BUY_LAYER, s(RED_LEFT))
        graphic(BUY_MIDDLE, 6, 0, buyWidth - 12, 22, BUY_LAYER, s(RED_MIDDLE), tiling = true)
        graphic(BUY_RIGHT, buyWidth - 6, 0, 6, 22, BUY_LAYER, s(RED_RIGHT))
        text(BUY_TEXT, 0, 0, buyWidth, 22, BUY_LAYER, fonts.p12, "Buy", WHITE)
        text(EMPTY_TEXT, GRID_X + 4, GRID_Y + 4, GRID_WIDTH - 8, GRID_HEIGHT - 8, WINDOW, fonts.p12, "", ORANGE)

        require(list.size == COMPONENT_COUNT) { "expected $COMPONENT_COUNT components, built ${list.size}" }
        require(list.map { it.id }.sorted() == (0 until COMPONENT_COUNT).toList()) { "component ids must be 0..${COMPONENT_COUNT - 1}" }
        return list.sortedBy { it.id }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        // Re-run over this tool's own earlier write (tx-20260919-021345 = the first 112 components): replaces exactly those files.
        val previous = args.firstOrNull { it.startsWith("--replace-from-journal=") }?.substringAfter('=')
            ?.let(LootKeyInterfaceImportTool::journalIntendedSha1s) ?: emptyMap()
        val fonts =
            com.displee.cache.CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0]).let { library ->
                try {
                    LootKeyInterfaceImportTool.fonts(library)
                } finally {
                    library.close()
                }
            }
        val mutations =
            components(fonts).map { c ->
                CacheMutation(
                    LootKeyInterfaceImportTool.INDEX_INTERFACES,
                    INTERFACE_ID,
                    c.id,
                    LootKeyInterfaceImportTool.encode(c),
                    "interface $INTERFACE_ID:${c.id} type=${c.type}",
                    expectedCurrentSha1 = previous["idx${LootKeyInterfaceImportTool.INDEX_INTERFACES}_grp${INTERFACE_ID}_file${c.id}"],
                )
            }
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
