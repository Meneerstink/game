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
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.StoreInterfaceImportTool [--apply]`
 */
object StoreInterfaceImportTool {
    /** First interface id above the Dizana's quiver window (1150). */
    const val INTERFACE_ID = 1151

    const val WIDTH = 488
    const val HEIGHT = 320

    const val ROOT = 0
    const val WINDOW = 1
    const val TITLE = 12
    const val CLOSE = 13

    /** Three shop tabs: layer (op), left cap, middle, right cap, label. */
    const val TAB_FIRST = 16
    const val TAB_STRIDE = 5
    const val TAB_COUNT = 3

    const val GRID_COLUMNS = 6
    const val GRID_ROWS = 5
    const val SLOT_COUNT = GRID_COLUMNS * GRID_ROWS
    const val SLOT_BACKGROUND_FIRST = TAB_FIRST + TAB_COUNT * TAB_STRIDE // 31
    const val SLOT_FIRST = SLOT_BACKGROUND_FIRST + SLOT_COUNT // 61

    const val PAGE_PREVIOUS = SLOT_FIRST + SLOT_COUNT // 91
    const val PAGE_TEXT = PAGE_PREVIOUS + 1
    const val PAGE_NEXT = PAGE_PREVIOUS + 2
    const val DIVIDER = PAGE_PREVIOUS + 3
    const val PREVIEW_LAYER = PAGE_PREVIOUS + 4 // 95
    const val PREVIEW_BACKGROUND = PREVIEW_LAYER + 1
    const val PREVIEW_MODEL = PREVIEW_LAYER + 2
    const val CAROUSEL_PREVIOUS = PREVIEW_LAYER + 3
    const val RESULT_NAME = PREVIEW_LAYER + 4
    const val CAROUSEL_NEXT = PREVIEW_LAYER + 5 // 100
    const val KIT_NAME = 101
    const val REQUIREMENT = 102
    const val COSMETIC = 103
    const val PRICE = 104
    const val BALANCE = 105
    const val BUY_LAYER = 106
    const val BUY_LEFT = 107
    const val BUY_MIDDLE = 108
    const val BUY_RIGHT = 109
    const val BUY_TEXT = 110
    const val EMPTY_TEXT = 111
    const val COMPONENT_COUNT = 112

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

    /** Preview box and its model scale: the model renders at (PREVIEW_WIDTH / PREVIEW_ASPECT_X) = 5x its inventory size. */
    const val PREVIEW_X = 266
    const val PREVIEW_Y = 62
    const val PREVIEW_WIDTH = 210
    const val PREVIEW_HEIGHT = 148
    const val PREVIEW_ASPECT_X = 42
    const val PREVIEW_ASPECT_Y = 30

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

        // 6 x 5 kit grid, 40 x 38 pitch, square slot buttons with the kit's item sprite on top.
        val slotOps = listOf("Select", "", "", "", "", "", "", "", "", "Examine")
        for (slot in 0 until SLOT_COUNT) {
            val x = 14 + 40 * (slot % GRID_COLUMNS)
            val y = 64 + 38 * (slot / GRID_COLUMNS)
            graphic(SLOT_BACKGROUND_FIRST + slot, x, y, 36, 36, WINDOW, s(170))
            add(Component(SLOT_FIRST + slot, TYPE_GRAPHIC, x + 2, y + 2, 32, 32, WINDOW, graphic = -1, ops = slotOps))
        }
        text(PAGE_PREVIOUS, 14, 262, 60, 16, WINDOW, fonts.p12, "< Prev", ORANGE, alignX = 0, ops = listOf("Previous page"))
        text(PAGE_TEXT, 74, 262, 120, 16, WINDOW, fonts.p12, "Page 1 / 1", WHITE)
        text(PAGE_NEXT, 194, 262, 60, 16, WINDOW, fonts.p12, "Next >", ORANGE, alignX = 2, ops = listOf("Next page"))
        add(Component(DIVIDER, TYPE_LINE, 258, 62, 0, 236, WINDOW, colour = LINE))

        // Preview: dark box with the result item's model at 5x (aspect ratio), carousel arrows around the result name.
        layer(PREVIEW_LAYER, PREVIEW_X, PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT, WINDOW)
        add(Component(PREVIEW_BACKGROUND, TYPE_RECTANGLE, 0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_LAYER, colour = 0, filled = true, transparency = 160))
        add(
            Component(
                PREVIEW_MODEL, LootKeyInterfaceImportTool.TYPE_MODEL, 0, 0, 0, 0, PREVIEW_LAYER,
                resizeX = 1, resizeY = 1, aspectX = PREVIEW_ASPECT_X, aspectY = PREVIEW_ASPECT_Y,
            ),
        )
        text(CAROUSEL_PREVIOUS, PREVIEW_X, 212, 20, 16, WINDOW, fonts.b12, "<", ORANGE, ops = listOf("Previous"))
        text(RESULT_NAME, PREVIEW_X + 20, 212, PREVIEW_WIDTH - 40, 16, WINDOW, fonts.p12, "", WHITE)
        text(CAROUSEL_NEXT, PREVIEW_X + PREVIEW_WIDTH - 20, 212, 20, 16, WINDOW, fonts.b12, ">", ORANGE, ops = listOf("Next"))
        text(KIT_NAME, PREVIEW_X, 228, PREVIEW_WIDTH, 14, WINDOW, fonts.p11, "", ORANGE)
        text(REQUIREMENT, PREVIEW_X, 242, PREVIEW_WIDTH, 14, WINDOW, fonts.p11, "", WHITE)
        text(COSMETIC, PREVIEW_X, 256, PREVIEW_WIDTH, 14, WINDOW, fonts.p11, "", GREEN)
        text(PRICE, PREVIEW_X, 270, PREVIEW_WIDTH / 2, 14, WINDOW, fonts.p11, "", ORANGE)
        text(BALANCE, PREVIEW_X + PREVIEW_WIDTH / 2, 270, PREVIEW_WIDTH / 2, 14, WINDOW, fonts.p11, "", ORANGE)
        layer(BUY_LAYER, PREVIEW_X + (PREVIEW_WIDTH - 110) / 2, 288, 110, 22, WINDOW, ops = listOf("Buy"))
        graphic(BUY_LEFT, 0, 0, 6, 22, BUY_LAYER, s(RED_LEFT))
        graphic(BUY_MIDDLE, 6, 0, 98, 22, BUY_LAYER, s(RED_MIDDLE), tiling = true)
        graphic(BUY_RIGHT, 104, 0, 6, 22, BUY_LAYER, s(RED_RIGHT))
        text(BUY_TEXT, 0, 0, 110, 22, BUY_LAYER, fonts.p12, "Buy", ORANGE)
        text(EMPTY_TEXT, 14, 64, 236, 186, WINDOW, fonts.p12, "", ORANGE)

        require(list.size == COMPONENT_COUNT) { "expected $COMPONENT_COUNT components, built ${list.size}" }
        require(list.map { it.id }.sorted() == (0 until COMPONENT_COUNT).toList()) { "component ids must be 0..${COMPONENT_COUNT - 1}" }
        return list.sortedBy { it.id }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
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
