package gg.rsmod.game.tools.importer

import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool.Component
import java.io.File

/**
 * Builds the OSRS rune pouch window (OSRS interface 190, owner 2026-10-04 024434: "the rune pouch and the divine rune pouch need
 * an GUI just like osrs when storing runes or withdrawing them") as the revision-667 if3 interface [INTERFACE_ID], through the same
 * transactional, both-caches route as [DizanasQuiverInterfaceImportTool].
 *
 * Source (read-only): OSRS 190 decoded from the pinned OpenRS2 2686 cache (`tools/cache667/osrs_if3.py`, 2026-10-05) - the window
 * 352x326; contents layer 190:2 (10 in from the sides, 10 from the bottom, 50 shorter); 190:3 the top 60 with the pouch box 190:4
 * (150 narrower, centred) on tiled sprite 1040 and its title 190:6 "Pouch" (font 495, 0xFF981F, shadowed, centred); 190:7 the
 * "Inventory" box 332 wide at y 65, 108 shorter than the contents, same backing and title; 190:10 the bottom row 38 high with the
 * four 38x38 quantity buttons 190:11-14 at -105 / -35 / 35 / 105 from the centre (RuneLite gameval InterfaceID.RunePouch: _1 / _5 /
 * X / ALL). OSRS draws the rune slots, the inventory runes and the buttons with clientscripts; here the slots are the 667 item grid
 * (clientscript 150, which spreads its cells over the layer it is given), the buttons are the square button sprite 170 with a text,
 * and the chosen quantity is a highlight the server shows.
 *
 * Usage: `./gradlew :game:runRunePouchInterfaceImportTool --args="[--apply]"`
 */
object RunePouchInterfaceImportTool {
    const val INTERFACE_ID = 1177

    /** 667 sprite for OSRS sprite 1040 (the tiled box backing of 190:5 / 190:8): the first id above the cache's sprites (8337). */
    const val BACKING_SPRITE = 8338
    const val OSRS_BACKING_SPRITE = 1040

    const val WIDTH = 352
    const val HEIGHT = 326

    const val ROOT = 0
    const val WINDOW = 1
    const val TITLE = 11
    const val CLOSE = 12
    const val POUCH_BOX = 13
    const val POUCH_TITLE = 14
    const val POUCH_GRID = 15
    const val INVENTORY_BOX = 16
    const val INVENTORY_TITLE = 17
    const val INVENTORY_GRID = 18

    /** Quantity buttons 1 / 5 / X / All: background, highlight, text = 19 + 3 * index (+0 / +1 / +2). */
    const val BUTTON_FIRST = 19
    const val BUTTON_STRIDE = 3
    val BUTTON_LABELS = listOf("1", "5", "X", "All")
    const val COMPONENT_COUNT = BUTTON_FIRST + 4 * BUTTON_STRIDE

    /** Contents layer 190:2 inside the window. */
    const val CONTENTS_X = 10
    const val CONTENTS_Y = HEIGHT - 10 - (HEIGHT - 50)
    const val CONTENTS_W = WIDTH - 20
    const val CONTENTS_H = HEIGHT - 50

    private const val TYPE_LAYER = 0
    private const val TYPE_RECTANGLE = 3
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5

    private const val TITLE_ORANGE = 0xFF981F
    private const val TITLE_FONT = 495

    fun components(fonts: LootKeyInterfaceImportTool.Fonts): List<Component> {
        val list = mutableListOf<Component>()
        fun add(c: Component) {
            require(list.none { it.id == c.id }) { "duplicate component ${c.id}" }
            list += c
        }
        fun graphic(id: Int, x: Int, y: Int, w: Int, h: Int, sprite: Int, tiling: Boolean = false, ops: List<String> = emptyList()) =
            add(Component(id, TYPE_GRAPHIC, x, y, w, h, WINDOW, graphic = sprite, tiling = tiling, ops = ops))
        fun text(id: Int, x: Int, y: Int, w: Int, h: Int, font: Int, text: String, colour: Int = TITLE_ORANGE, ops: List<String> = emptyList()) =
            add(Component(id, TYPE_TEXT, x, y, w, h, WINDOW, font = font, text = text, colour = colour, alignX = 1, alignY = 1, shadow = true, ops = ops))
        val s = LootKeyInterfaceImportTool::sprite

        add(Component(ROOT, TYPE_LAYER, 0, 0, 0, 0, -1, resizeX = 1, resizeY = 1))
        add(Component(WINDOW, TYPE_LAYER, 0, 0, WIDTH, HEIGHT, ROOT, reposX = 1, reposY = 1, noClickThrough = true))
        // Stone frame (OSRS steelborder): the loot-key frame sprites, same geometry as the quiver window.
        graphic(2, 1, 1, WIDTH - 2, HEIGHT - 2, s(297), tiling = true)
        graphic(3, 32, -13, WIDTH - 64, 20, s(820), tiling = true)
        graphic(4, 32, HEIGHT - 20, WIDTH - 64, 20, s(822), tiling = true)
        graphic(5, -13, 32, 20, HEIGHT - 64, s(821), tiling = true)
        graphic(6, WIDTH - 20, 32, 20, HEIGHT - 64, s(823), tiling = true)
        graphic(7, 0, 0, 32, 32, s(824))
        graphic(8, WIDTH - 32, 0, 32, 32, s(825))
        graphic(9, 0, HEIGHT - 32, 32, 32, s(826))
        graphic(10, WIDTH - 32, HEIGHT - 32, 32, 32, s(827))
        text(TITLE, 0, 8, WIDTH, 20, fonts.b12, "Rune Pouch")
        graphic(CLOSE, WIDTH - 26 - 6, 6, 26, 23, DizanasQuiverInterfaceImportTool.CLOSE_SPRITE, ops = listOf("Close"))

        // 190:3/4/5/6: the pouch box, 150 narrower than the contents, centred, 60 high, the title in its top 20.
        val pouchW = CONTENTS_W - 150
        val pouchX = CONTENTS_X + (CONTENTS_W - pouchW) / 2
        val pouchY = CONTENTS_Y
        graphic(POUCH_BOX, pouchX + 1, pouchY + 1, pouchW - 2, 60 - 2, BACKING_SPRITE, tiling = true)
        text(POUCH_TITLE, pouchX, pouchY, pouchW, 20, TITLE_FONT, "Pouch")
        add(Component(POUCH_GRID, TYPE_LAYER, pouchX + 6, pouchY + 22, pouchW - 12, 32, WINDOW))

        // 190:7/8/9: the inventory box, 332 wide at y 65, 108 shorter than the contents.
        val invY = CONTENTS_Y + 65
        val invH = CONTENTS_H - 108
        graphic(INVENTORY_BOX, CONTENTS_X + 1, invY + 1, CONTENTS_W - 2, invH - 2, BACKING_SPRITE, tiling = true)
        text(INVENTORY_TITLE, CONTENTS_X, invY, CONTENTS_W, 20, TITLE_FONT, "Inventory")
        add(Component(INVENTORY_GRID, TYPE_LAYER, CONTENTS_X + 8, invY + 22, CONTENTS_W - 16, invH - 28, WINDOW))

        // 190:10-14: four 38x38 quantity buttons, bottom row of the contents, centred at -105 / -35 / 35 / 105.
        val rowY = CONTENTS_Y + CONTENTS_H - 38
        listOf(-105, -35, 35, 105).forEachIndexed { i, offset ->
            val bx = CONTENTS_X + CONTENTS_W / 2 + offset - 19
            val base = BUTTON_FIRST + i * BUTTON_STRIDE
            graphic(base, bx, rowY, 38, 38, s(170), ops = listOf("Select"))
            add(Component(base + 1, TYPE_RECTANGLE, bx + 2, rowY + 2, 34, 34, WINDOW, colour = 0xFFFFFF, filled = true, transparency = 200, hidden = true))
            text(base + 2, bx, rowY, 38, 38, fonts.b12, BUTTON_LABELS[i])
        }

        require(list.size == COMPONENT_COUNT) { "expected $COMPONENT_COUNT components, built ${list.size}" }
        require(list.map { it.id }.sorted() == (0 until COMPONENT_COUNT).toList()) { "component ids must be 0..${COMPONENT_COUNT - 1}" }
        return list.sortedBy { it.id }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val mutations = mutableListOf<CacheMutation>()
        ModernCacheReader(File(LootKeyInterfaceImportTool.SOURCE_CACHE)).use { source ->
            val bytes = source.file(LootKeyInterfaceImportTool.INDEX_SPRITES, OSRS_BACKING_SPRITE, 0) ?: error("OSRS sprite $OSRS_BACKING_SPRITE missing")
            mutations += CacheMutation(LootKeyInterfaceImportTool.INDEX_SPRITES, BACKING_SPRITE, 0, bytes, "sprite osrs:$OSRS_BACKING_SPRITE -> 667:$BACKING_SPRITE")
        }
        val fonts =
            com.displee.cache.CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0]).let { library ->
                try {
                    LootKeyInterfaceImportTool.fonts(library)
                } finally {
                    library.close()
                }
            }
        components(fonts).forEach { c ->
            mutations += CacheMutation(LootKeyInterfaceImportTool.INDEX_INTERFACES, INTERFACE_ID, c.id, LootKeyInterfaceImportTool.encode(c), "interface $INTERFACE_ID:${c.id} type=${c.type}")
        }
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interface $INTERFACE_ID and sprite $BACKING_SPRITE")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) println("VERIFY_OK transaction=${transaction.id}") else {
            problems.forEach { println("  VERIFY_PROBLEM: $it") }
            error("verify failed")
        }
    }
}
