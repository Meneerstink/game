package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Builder
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Comp
import java.io.File
import javax.imageio.ImageIO

/**
 * The night job of 2026-10-24 (owner approved designs `screens\team\N110\GOEDGEKEURD\01-06`, spec
 * `team\dossiers\NACHTOPDRACHT-DROPVIEWER-QUESTTAB-2026-10-24.txt`): the Drop Viewer (N108 + A37), the Collection Log (N104), the
 * quest tab with four tabs (N103 Quests, N109 Deadman, N104 Deadman Tasks, Drops & Collection Log) and the Deadman Tasks window.
 *
 * Every background piece is the design's own CSS drawn by Chrome at 1x (`work\n108-art\gen.py` -> `data\art\n108\*.png`), imported as
 * 667 sprites from [SPRITE_BASE] in the order of `manifest.json` (sorted by name); the server reads the same order from that
 * manifest ([spriteId]). Text is drawn by the game (p11 / p12 / b12, the fonts the designs' RuneLite TTFs copy). The geometry below
 * is the designs' pixel positions: windows 500 x 322 (fixed-mode main screen), the quest tab pane 190 x 261 with its panel box
 * 184 x 233 at (3, 27) - the HTML pane minus its 1 px border.
 *
 * Interfaces (the server fills and moves everything):
 *  - [CONTAINER] 1171 the tab row and the panel box; [QUESTS] 1173, [DEADMAN] 1172, [TASKS] 1174, [DROPS_LOG] 1182 the four panels;
 *  - [VIEWER] 1176 the Drop Viewer, [LOG] 1183 the Collection Log, [TASK_WINDOW] 1184 the Deadman Tasks window.
 *  - [INFO] 1175 the Deadman Info window (N274, owner design 2026-10-24 GOEDGEKEURD screens\team\INFO\info-v1.png): subjects
 *    with icons in three groups on the left, per subject a page with its big icon, name, tag line, fact rows (or a table), a few
 *    lines of text and what is live now at the bottom (with "Show on map"). It replaces the text-only window
 *    [QuestTabInterfaceImportTool] built.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.N108InterfaceImportTool [--apply]` - both caches, one transaction.
 */
object N108InterfaceImportTool {
    const val CONTAINER = 1171
    const val DEADMAN = 1172
    const val QUESTS = 1173
    const val TASKS = 1174
    const val INFO = 1175
    const val VIEWER = 1176
    const val DROPS_LOG = 1182
    const val LOG = 1183
    const val TASK_WINDOW = 1184

    /** First sprite id of this import (probe 2026-10-24: highest sprite group 8518 in both caches). */
    const val SPRITE_BASE = 8600

    val ART_DIR = RspsPaths.data("art/n108")

    /** Sprite names in id order (manifest.json, sorted). */
    val SPRITES: List<String> by lazy { manifest().keys.sorted() }

    fun manifest(dir: String = ART_DIR): Map<String, IntArray> {
        val text = File("$dir/manifest.json").readText()
        val out = LinkedHashMap<String, IntArray>()
        Regex("\"([a-z0-9_]+)\":\\s*\\[(\\d+),\\s*(\\d+)\\]").findAll(text).forEach { m ->
            out[m.groupValues[1]] = intArrayOf(m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        return out
    }

    fun spriteId(name: String): Int {
        val i = SPRITES.indexOf(name)
        require(i >= 0) { "sprite '$name' is not in the N108 art" }
        return SPRITE_BASE + i
    }

    fun size(name: String): IntArray = manifest()[name] ?: error("sprite '$name' is not in the N108 art")

    private const val TYPE_LAYER = 0
    private const val TYPE_RECTANGLE = 3
    private const val TYPE_GRAPHIC = 5
    private const val TYPE_MODEL = 6

    const val ORANGE = 0xFF981F
    const val GOLD = 0xFFCF4A
    const val TXT = 0xECE2CF
    const val GREY = 0x9A9083
    const val WHITE = 0xFFFFFF

    private fun Builder.g(id: Int, x: Int, y: Int, parent: Int, sprite: String, hidden: Boolean = false, ops: List<String> = emptyList(), w: Int = -1, h: Int = -1) {
        val s = size(sprite)
        add(Comp(id, TYPE_GRAPHIC, x, y, if (w > 0) w else s[0], if (h > 0) h else s[1], parent, hidden = hidden, graphic = spriteId(sprite), ops = ops))
    }

    private fun Builder.t(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, font: Int, colour: Int, text: String = "", alignX: Int = 0, alignY: Int = 1, hidden: Boolean = false, lineHeight: Int = 0) =
        text(id, x, y, w, h, parent, font, text, colour = colour, alignX = alignX, alignY = alignY, hidden = hidden, lineHeight = lineHeight)

    /** Text on parchment (the Wiki table, N279): no drop shadow. */
    private fun Builder.tp(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, font: Int, colour: Int, text: String = "", alignX: Int = 0, alignY: Int = 1, hidden: Boolean = false) =
        add(Comp(id, 4, x, y, w, h, parent, hidden = hidden, font = font, text = text, colour = colour, alignX = alignX, alignY = alignY, textShadow = false))

    private fun Builder.hit(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, op: String, hidden: Boolean = false) = layer(id, x, y, w, h, parent, hidden = hidden, ops = listOf(op))

    /** A scaled item picture (the 667 client draws an object graphic at the component's size). */
    private fun Builder.obj(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, transparency: Int = 0, ops: List<String> = emptyList()) =
        add(Comp(id, TYPE_GRAPHIC, x, y, w, h, parent, hidden = true, graphic = -1, outline = 0, shadow = 0, transparency = transparency, ops = ops))

    private fun Builder.model(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, zoom: Int) =
        add(Comp(id, TYPE_MODEL, x, y, w, h, parent, hidden = true, model = -1, originX = 0, originY = 0, xan = 0, yan = 0, zan = 0, zoom = zoom))

    private fun Builder.root() = add(Comp(0, TYPE_LAYER, 0, 0, 0, 0, -1, resizeX = 1, resizeY = 1))

    private fun Builder.window() = add(Comp(1, TYPE_LAYER, 0, 0, 500, 322, 0, reposX = 1, reposY = 1))

    // ================================================================== Drop Viewer (dropviewer.html)

    object V {
        const val BG = 2
        const val TITLE = 3
        const val CLOSE = 4
        const val SEARCH_ON = 5
        const val SEARCH_TEXT = 6
        const val SEARCH_CLEAR = 7
        const val SEARCH_HIT = 8
        const val LIST = 9
        const val LIST_SCROLLBAR = 10
        const val MODEL = 11
        const val NAME = 12
        const val STAR_ON = 13
        const val STAR_OFF = 14
        const val SUB = 15
        const val TAG_FIRST = 16 // per tag: box graphic, text
        const val TAGS = 4
        const val MAP = TAG_FIRST + TAGS * 2 // 24
        const val MAP_TEXT = MAP + 1
        const val BRK_A = MAP + 2
        const val BRK_B = MAP + 3
        const val BRK_C = MAP + 4
        /** N279 / N284: the bracket line's skull (the "Combat 138" text that stood here is gone). */
        const val BRK_ICON = MAP + 5
        const val ALWAYS_LABEL = MAP + 6
        const val ALWAYS_FIRST = MAP + 7 // per item: picture, name, note
        const val ALWAYS = 3
        const val HEAD_ITEM = ALWAYS_FIRST + ALWAYS * 3 // 40
        const val HEAD_QTY = HEAD_ITEM + 1
        const val HEAD_YOUR = HEAD_ITEM + 2
        const val HEAD_NORMAL = HEAD_ITEM + 3
        const val ROWS = HEAD_ITEM + 4 // 44: scroll layer
        const val ROWS_SCROLLBAR = ROWS + 1
        const val EMPTY = ROWS + 2
        const val ROW_FIRST = ROWS + 3 // 47
        const val ROW_STRIDE = 8 // bg, chance cell, item, name, tag, qty, your, normal
        const val R_BG = 0
        const val R_CELL = 1
        const val R_ITEM = 2
        const val R_NAME = 3
        const val R_TAG = 4
        const val R_QTY = 5
        const val R_YOUR = 6
        const val R_NORMAL = 7
        /** Group headings and every drop table's lines (always open, N279) included; DropViewerAudit checks every entry fits. */
        const val ROW_SLOTS = 200
        const val LIST_FIRST = ROW_FIRST + ROW_SLOTS * ROW_STRIDE
        const val LIST_STRIDE = 9 // bg, arrow, icon, thumb, model, text, sub, view, toggle
        const val L_BG = 0
        const val L_ARROW = 1
        const val L_ICON = 2
        const val L_THUMB = 3
        const val L_MODEL = 4
        const val L_TEXT = 5
        const val L_SUB = 6
        const val L_VIEW = 7
        const val L_TOGGLE = 8
        const val LIST_SLOTS = 140
        const val COUNT = LIST_FIRST + LIST_SLOTS * LIST_STRIDE

        // geometry (window pixels)
        const val LIST_X = 8
        const val LIST_Y = 57
        const val LIST_W = 132
        const val LIST_H = 257
        const val ROWS_X = 163
        const val ROWS_Y = 140
        const val ROWS_W = 314
        const val ROWS_H = 174
        const val ROW_H = 18
        /** A group heading's height (design B5 `.ghd`). */
        const val HEAD_H = 17
        const val NAME_X = 261
        const val TAGS_Y = 66

        fun row(i: Int) = ROW_FIRST + i * ROW_STRIDE

        fun slot(i: Int) = LIST_FIRST + i * LIST_STRIDE
    }

    fun viewer(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.root()
        b.window()
        b.g(V.BG, 0, 0, 1, "dv_bg")
        b.t(V.TITLE, 0, 4, 500, 18, 1, fonts.b12, ORANGE, "Drop Viewer", alignX = 1)
        b.hit(V.CLOSE, 472, 5, 21, 19, 1, "Close")
        b.g(V.SEARCH_ON, 7, 30, 1, "dv_search_on", hidden = true)
        b.t(V.SEARCH_TEXT, 28, 30, 116, 22, 1, fonts.p11, 0xC8BBA6, "Search monster or item")
        b.t(V.SEARCH_CLEAR, 144, 30, 12, 22, 1, fonts.p11, 0x8F8473, "x", alignX = 1, hidden = true)
        b.hit(V.SEARCH_HIT, 7, 30, 135, 22, 1, "Search")
        b.layer(V.LIST, V.LIST_X, V.LIST_Y, V.LIST_W, V.LIST_H, 1)
        b.scrollbarFor(V.LIST_SCROLLBAR, V.LIST_X + V.LIST_W, V.LIST_Y, V.LIST_H, 1, VIEWER, V.LIST)
        b.model(V.MODEL, 163, 31, 90, 70, 1, 1200)
        b.t(V.NAME, V.NAME_X, 29, 150, 16, 1, fonts.b12, ORANGE)
        b.g(V.STAR_ON, 381, 30, 1, "star_gold", hidden = true, ops = listOf("Remove from favourites"))
        b.g(V.STAR_OFF, 381, 30, 1, "star_dim", hidden = true, ops = listOf("Add to favourites"))
        b.t(V.SUB, V.NAME_X, 46, 232, 16, 1, fonts.p11, TXT)
        for (i in 0 until V.TAGS) {
            b.g(V.TAG_FIRST + i * 2, V.NAME_X, V.TAGS_Y, 1, "tag_multi", hidden = true)
            b.t(V.TAG_FIRST + i * 2 + 1, V.NAME_X, V.TAGS_Y, 60, 16, 1, fonts.p11, WHITE, hidden = true)
        }
        b.hit(V.MAP, 408, 30, 84, 18, 1, "Show on map", hidden = true)
        b.t(V.MAP_TEXT, 420, 30, 72, 18, 1, fonts.p11, ORANGE, "Show on map", alignX = 1, hidden = true)
        // N279 design B5: the bracket as gold text in the head under the labels (skull, "Your bracket", the bracket in bold, the
        // boost); no "Combat 138" (N284).
        b.g(V.BRK_ICON, V.NAME_X, 89, 1, "skull12")
        b.t(V.BRK_A, V.NAME_X + 16, 86, 90, 17, 1, fonts.p11, 0xFFD75E, "Your bracket")
        b.t(V.BRK_B, V.NAME_X + 90, 86, 40, 17, 1, fonts.b12, WHITE)
        b.t(V.BRK_C, V.NAME_X + 120, 86, 150, 17, 1, fonts.p11, 0xFFD75E)
        // The Wiki table (parchment, no text shadow): the Always bar, the column heads, the rows.
        b.tp(V.ALWAYS_LABEL, 169, 107, 60, 18, 1, fonts.p11, 0x2A1D0E, "Always:")
        for (i in 0 until V.ALWAYS) {
            val base = V.ALWAYS_FIRST + i * 3
            b.obj(base, 220, 109, 16, 14, 1)
            b.tp(base + 1, 238, 107, 100, 18, 1, fonts.p12, 0x4A2508, hidden = true)
            b.tp(base + 2, 238, 107, 100, 18, 1, fonts.p11, 0x5E4E38, hidden = true)
        }
        b.tp(V.HEAD_ITEM, V.ROWS_X + 31, 125, 100, 15, 1, fonts.p11, 0x000000, "Item")
        b.tp(V.HEAD_QTY, V.ROWS_X + 170, 125, 36, 15, 1, fonts.p11, 0x000000, "Qty", alignX = 1)
        b.tp(V.HEAD_YOUR, V.ROWS_X + 206, 125, 56, 15, 1, fonts.p11, 0x000000, "Your chance", alignX = 1)
        b.tp(V.HEAD_NORMAL, V.ROWS_X + 262, 125, 52, 15, 1, fonts.p11, 0x000000, "Normal", alignX = 1)
        b.layer(V.ROWS, V.ROWS_X, V.ROWS_Y, V.ROWS_W, V.ROWS_H, 1)
        b.scrollbarFor(V.ROWS_SCROLLBAR, V.ROWS_X + V.ROWS_W, V.ROWS_Y, V.ROWS_H, 1, VIEWER, V.ROWS)
        b.tp(V.EMPTY, 0, 0, V.ROWS_W, 60, V.ROWS, fonts.p12, 0x3A2C1C, alignX = 1, hidden = true)
        for (r in 0 until V.ROW_SLOTS) {
            val base = V.row(r)
            val y = r * V.ROW_H
            b.g(base + V.R_BG, 0, y, V.ROWS, "dv_row_even", hidden = true)
            b.g(base + V.R_CELL, 208, y + 1, V.ROWS, "zj_c_common", hidden = true)
            b.obj(base + V.R_ITEM, 2, y, 22, 18, V.ROWS)
            b.tp(base + V.R_NAME, 31, y, 139, V.ROW_H, V.ROWS, fonts.p12, 0x4A2508, hidden = true)
            b.g(base + V.R_TAG, 100, y + 4, V.ROWS, "ok", hidden = true)
            b.tp(base + V.R_QTY, 170, y, 36, V.ROW_H, V.ROWS, fonts.p11, 0x000000, alignX = 1, hidden = true)
            b.tp(base + V.R_YOUR, 208, y, 53, V.ROW_H, V.ROWS, fonts.b12, 0x000000, alignX = 1, hidden = true)
            b.tp(base + V.R_NORMAL, 263, y, 51, V.ROW_H, V.ROWS, fonts.p11, 0x3A2C1C, alignX = 1, hidden = true)
        }
        for (s in 0 until V.LIST_SLOTS) {
            val base = V.slot(s)
            b.g(base + V.L_BG, 0, 0, V.LIST, "dv_grp", hidden = true)
            b.g(base + V.L_ARROW, 5, 6, V.LIST, "ar_open", hidden = true)
            b.g(base + V.L_ICON, 17, 2, V.LIST, "star_gold", hidden = true)
            b.g(base + V.L_THUMB, 4, 1, V.LIST, "dv_thumb", hidden = true)
            b.model(base + V.L_MODEL, 5, 2, 20, 20, V.LIST, 2000)
            b.t(base + V.L_TEXT, 30, 0, 102, 24, V.LIST, fonts.p11, TXT, hidden = true)
            b.t(base + V.L_SUB, 30, 12, 102, 12, V.LIST, fonts.p11, GREY, hidden = true)
            b.hit(base + V.L_VIEW, 0, 0, V.LIST_W, 24, V.LIST, "View", hidden = true)
            b.hit(base + V.L_TOGGLE, 0, 0, V.LIST_W, 17, V.LIST, "Toggle", hidden = true)
        }
        return b.done(V.COUNT)
    }

    // ================================================================== Collection Log (collection-log.html)

    object C {
        const val BG = 2
        const val TITLE = 3
        const val TOTAL = 4
        const val CLOSE = 5
        const val CAT_FIRST = 6 // per category: on bg, off bg, on text, off text, hit
        const val CAT_STRIDE = 5
        const val CATS = 5
        const val LIST = CAT_FIRST + CATS * CAT_STRIDE // 31
        const val LIST_SCROLLBAR = LIST + 1
        const val NAME = LIST + 2
        const val OBTAINED = LIST + 3
        const val KILLS = LIST + 4
        const val ITEMS = LIST + 5
        const val ITEMS_SCROLLBAR = LIST + 6
        const val EMPTY = LIST + 7
        const val ENTRY_FIRST = LIST + 8 // per entry: sel bg, text, hit
        const val ENTRY_STRIDE = 3
        const val ENTRIES = 50
        const val SLOT_FIRST = ENTRY_FIRST + ENTRIES * ENTRY_STRIDE
        const val SLOT_STRIDE = 3 // got, missing, count
        const val SLOTS = 91
        const val COUNT = SLOT_FIRST + SLOTS * SLOT_STRIDE

        val CAT_X = intArrayOf(7, 105, 202, 300, 397)
        val CAT_W = intArrayOf(96, 95, 96, 95, 96)
        const val LIST_X = 8
        const val LIST_Y = 50
        const val LIST_W = 142
        const val LIST_H = 264
        /** N297 design A: the list in p12, 18 px a row. */
        const val ENTRY_H = 18
        const val ITEMS_X = 172
        const val ITEMS_Y = 106
        const val ITEMS_W = 304
        const val ITEMS_H = 208
        const val COLUMNS = 7
        const val PITCH_X = 42
        const val PITCH_Y = 38

        fun cat(i: Int) = CAT_FIRST + i * CAT_STRIDE

        fun entry(i: Int) = ENTRY_FIRST + i * ENTRY_STRIDE

        fun slot(i: Int) = SLOT_FIRST + i * SLOT_STRIDE
    }

    /** 667 transparency (0 opaque .. 255 invisible) of a missing log item: the design's opacity .32. */
    const val MISSING_TRANSPARENCY = 174

    fun log(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.root()
        b.window()
        b.g(C.BG, 0, 0, 1, "cl_bg")
        b.t(C.TITLE, 0, 4, 500, 18, 1, fonts.b12, ORANGE, "Collection Log", alignX = 1)
        b.t(C.TOTAL, 300, 6, 164, 16, 1, fonts.p11, 0xD9CCB3, alignX = 2)
        b.hit(C.CLOSE, 472, 5, 21, 19, 1, "Close")
        val names = listOf("Bosses", "Deadman", "Clues", "Minigames", "Other")
        for (i in 0 until C.CATS) {
            val base = C.cat(i)
            val w = C.CAT_W[i]
            b.g(base, C.CAT_X[i], 29, 1, "cl_cat_on_$w", hidden = i != 0)
            b.g(base + 1, C.CAT_X[i], 29, 1, "cl_cat_off_$w", hidden = i == 0)
            b.t(base + 2, C.CAT_X[i], 29, w, 20, 1, fonts.b12, ORANGE, names[i], alignX = 1, hidden = i != 0)
            b.t(base + 3, C.CAT_X[i], 29, w, 20, 1, fonts.p11, 0xA29680, names[i], alignX = 1, hidden = i == 0)
            b.hit(base + 4, C.CAT_X[i], 29, w, 20, 1, "View")
        }
        b.layer(C.LIST, C.LIST_X, C.LIST_Y, C.LIST_W, C.LIST_H, 1)
        b.scrollbarFor(C.LIST_SCROLLBAR, C.LIST_X + C.LIST_W, C.LIST_Y, C.LIST_H, 1, LOG, C.LIST)
        b.t(C.NAME, 180, 53, 300, 16, 1, fonts.b12, ORANGE)
        b.t(C.OBTAINED, 180, 70, 300, 15, 1, fonts.p11, ORANGE)
        b.t(C.KILLS, 180, 85, 300, 15, 1, fonts.p11, ORANGE)
        b.layer(C.ITEMS, C.ITEMS_X, C.ITEMS_Y, C.ITEMS_W, C.ITEMS_H, 1)
        b.scrollbarFor(C.ITEMS_SCROLLBAR, C.ITEMS_X + C.ITEMS_W, C.ITEMS_Y, C.ITEMS_H, 1, LOG, C.ITEMS)
        b.t(C.EMPTY, 0, 40, C.ITEMS_W, 60, C.ITEMS, fonts.p12, GREY, alignX = 1, hidden = true)
        for (i in 0 until C.ENTRIES) {
            val base = C.entry(i)
            val y = i * C.ENTRY_H
            b.g(base, 0, y, C.LIST, "zj_cl_sel", hidden = true)
            b.t(base + 1, 6, y, C.LIST_W - 6, C.ENTRY_H, C.LIST, fonts.p12, 0xFFB347, hidden = true)
            b.hit(base + 2, 0, y, C.LIST_W, C.ENTRY_H, C.LIST, "View", hidden = true)
        }
        for (i in 0 until C.SLOTS) {
            val base = C.slot(i)
            val x = 8 + (i % C.COLUMNS) * C.PITCH_X
            val y = 8 + (i / C.COLUMNS) * C.PITCH_Y
            b.obj(base, x, y, 36, 32, C.ITEMS, ops = listOf("Inspect"))
            // N297 design A: an item still missing is dimmed to about a third (opacity .32).
            b.obj(base + 1, x, y, 36, 32, C.ITEMS, transparency = MISSING_TRANSPARENCY, ops = listOf("Inspect"))
            b.t(base + 2, x, y - 3, 36, 12, C.ITEMS, fonts.p11, 0xFFFF00, hidden = true)
        }
        return b.done(C.COUNT)
    }

    // ================================================================== Quest tab container (questtab-4-tabs.html)

    object Q {
        const val BG = 1
        const val TAB_FIRST = 2 // per tab: hit layer, off, on, icon
        const val TAB_STRIDE = 4
        const val BOX = TAB_FIRST + 4 * TAB_STRIDE // 18
        const val COUNT = BOX + 1
        val TAB_X = intArrayOf(1, 49, 96, 144)
        val TAB_W = intArrayOf(46, 45, 46, 45)
        val TOOLTIPS = listOf("Quests", "Deadman", "Deadman Tasks", "Drops & Collection Log")
        /** N288 design A3: the Deadman tab carries the DMM skull of the gameframe (OSRS sprite 4815). */
        val ICONS = listOf("qt_ic_quests", "zj_ic_dmm", "qt_ic_tasks", "qt_ic_log")

        fun tab(i: Int) = TAB_FIRST + i * TAB_STRIDE
    }

    fun container(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.add(Comp(0, TYPE_LAYER, 0, 0, 0, 0, -1, resizeX = 1, resizeY = 1))
        b.g(Q.BG, 0, 0, 0, "qt_pane")
        for (i in 0 until 4) {
            val base = Q.tab(i)
            val w = Q.TAB_W[i]
            b.layer(base, Q.TAB_X[i], 0, w, 27, 0, ops = listOf(Q.TOOLTIPS[i]))
            b.g(base + 1, 0, 0, base, "qt_tab_off_$w")
            b.g(base + 2, 0, 0, base, "qt_tab_on_$w", hidden = true)
            b.g(base + 3, (w - 26) / 2, 2, base, Q.ICONS[i])
        }
        b.layer(Q.BOX, 3, 27, 184, 233, 0)
        return b.done(Q.COUNT)
    }

    // ---- shared panel header: title (orange bold) left, info right, the line under it

    const val P_TITLE = 1
    const val P_RIGHT = 2
    const val P_HR = 3
    const val P_FIRST = 4

    private fun Builder.panel(title: String) {
        add(Comp(0, TYPE_LAYER, 0, 0, 0, 0, -1, resizeX = 1, resizeY = 1))
        t(P_TITLE, 6, 2, 170, 18, 0, fonts.b12, ORANGE, title)
        t(P_RIGHT, 6, 2, 173, 18, 0, fonts.p11, WHITE, alignX = 2)
        g(P_HR, 5, 21, 0, "qt_hr")
    }

    // ================================================================== 1 Quests

    object QP {
        const val LIST = P_FIRST // scroll layer
        const val SCROLLBAR = P_FIRST + 1
        const val NOTE = P_FIRST + 2
        const val ROW_FIRST = P_FIRST + 3 // per row: icon, text, right, arrow, hit, star (the chosen quest)
        const val ROW_STRIDE = 6
        const val ROWS = 30
        const val COUNT = ROW_FIRST + ROWS * ROW_STRIDE
        const val LIST_X = 5
        const val LIST_Y = 24
        const val LIST_W = 162
        const val LIST_H = 206

        fun row(i: Int) = ROW_FIRST + i * ROW_STRIDE
    }

    fun quests(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.panel("Quests")
        b.layer(QP.LIST, QP.LIST_X, QP.LIST_Y, QP.LIST_W, QP.LIST_H, 0)
        b.scrollbarFor(QP.SCROLLBAR, QP.LIST_X + QP.LIST_W, QP.LIST_Y, QP.LIST_H, 0, QUESTS, QP.LIST)
        b.t(QP.NOTE, 0, 0, QP.LIST_W, 36, QP.LIST, fonts.p11, GREY, hidden = true, lineHeight = 12)
        for (i in 0 until QP.ROWS) {
            val base = QP.row(i)
            b.g(base, 1, 0, QP.LIST, "q_ck", hidden = true)
            b.t(base + 1, 16, 0, 140, 14, QP.LIST, fonts.p11, TXT, hidden = true)
            b.t(base + 2, 0, 0, 158, 15, QP.LIST, fonts.p11, GREY, alignX = 2, hidden = true)
            b.g(base + 3, 152, 4, QP.LIST, "q_tw", hidden = true)
            b.hit(base + 4, 0, 0, 160, 14, QP.LIST, "Toggle", hidden = true)
            b.g(base + 5, 0, 0, QP.LIST, "q_star", hidden = true)
        }
        return b.done(QP.COUNT)
    }

    // ================================================================== 2 Deadman

    object DP {
        const val INFO = P_FIRST // button bg (op), text
        const val INFO_TEXT = INFO + 1
        const val BRK = INFO + 2
        const val BADGE_TOP = INFO + 3
        const val BADGE = INFO + 4
        const val L1 = INFO + 5
        const val L2 = INFO + 6
        const val CELL_FIRST = INFO + 7 // 3 cells (protection while he has it, skull, kill grace): bg2, bg3, icon, value, label
        const val CELL_STRIDE = 5
        const val CELLS = 3
        const val POINT_FIRST = CELL_FIRST + CELLS * CELL_STRIDE // 3 rows (Loyalty, Donator, Deadman): bg, icon, name, value, hit
        const val POINT_STRIDE = 5
        const val POINTS = 3
        const val EMBLEM_BG = POINT_FIRST + POINTS * POINT_STRIDE
        const val EMBLEM_ITEM = EMBLEM_BG + 1
        const val EMBLEM_TEXT = EMBLEM_BG + 2
        const val EMBLEM_VALUE = EMBLEM_BG + 3
        const val EMBLEM_ARROW = EMBLEM_BG + 4
        const val EMBLEM_HIT = EMBLEM_BG + 5
        const val EVENT_FIRST = EMBLEM_BG + 6 // 2 events: bg, icon, a, b, time, map bg (op), map text
        const val EVENT_STRIDE = 7
        const val COUNT = EVENT_FIRST + 2 * EVENT_STRIDE

        /** Design A3 rows (panel y): bracket box, the cells, the three point rows, the emblem row, the two events. */
        const val BRK_Y = 24
        const val CELL_Y = 61
        const val POINT_Y = 96
        const val EMBLEM_Y = 153
        const val EVENT_Y = 175
        const val EVENT_STEP = 29
        val POINT_ICONS = listOf("zj_dm_ic_loyalty", "zj_dm_ic_donator", "zj_dm_ic_deadman")
        val POINT_NAMES = listOf("Loyalty", "Donator", "Deadman")

        fun cell(i: Int) = CELL_FIRST + i * CELL_STRIDE

        fun point(i: Int) = POINT_FIRST + i * POINT_STRIDE

        fun event(i: Int) = EVENT_FIRST + i * EVENT_STRIDE
    }

    /** Labels a shade lighter than [GREY] (design A3, owner: readable on the dark cells). */
    const val LABEL = 0xB5A891

    /**
     * The Deadman tab, design A3 (owner 2026-10-24 GOEDGEKEURD, screens/team/INFO/dmtab-v3.png, N288): the bracket box with two lines
     * (no Fights line), skull and kill grace, the three point balances as rows with the whole number and the 78 Store icons (no
     * store opens from here, N280), the Archaic Emblem row with an arrow (Info on the emblem), breach and chest. Everything 3 px apart.
     */
    fun deadman(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.panel("Deadman")
        // N274: a real button - the blue "i" and "Info" (design info-v1), right-aligned on the header's 179 px edge.
        b.g(DP.INFO, 133, 2, 0, "zi_btn_info", ops = listOf("Info"))
        b.t(DP.INFO_TEXT, 152, 2, 24, 18, 0, fonts.p11, ORANGE, "Info", alignX = 0)
        b.g(DP.BRK, 5, DP.BRK_Y, 0, "zj_dm_brk")
        b.t(DP.BADGE_TOP, 9, DP.BRK_Y + 5, 46, 11, 0, fonts.p11, 0xC9A24A, "Bracket", alignX = 1)
        b.t(DP.BADGE, 9, DP.BRK_Y + 15, 46, 16, 0, fonts.b12, WHITE, alignX = 1)
        b.t(DP.L1, 62, DP.BRK_Y + 1, 114, 16, 0, fonts.p12, 0xFFE9A8)
        b.t(DP.L2, 62, DP.BRK_Y + 16, 114, 16, 0, fonts.p12, 0xFFE9A8)
        for (i in 0 until DP.CELLS) {
            val base = DP.cell(i)
            b.g(base, 5, DP.CELL_Y, 0, "dm_cell2", hidden = true)
            b.g(base + 1, 5, DP.CELL_Y, 0, "dm_cell3", hidden = true)
            b.g(base + 2, 5, DP.CELL_Y + 1, 0, "dm_skull", hidden = true)
            b.t(base + 3, 5, DP.CELL_Y + 2, 60, 16, 0, fonts.p11, WHITE, hidden = true)
            b.t(base + 4, 5, DP.CELL_Y + 16, 56, 15, 0, fonts.p12, LABEL, alignX = 1, hidden = true)
        }
        for (i in 0 until DP.POINTS) {
            val base = DP.point(i)
            val y = DP.POINT_Y + i * 18
            b.g(base, 5, y, 0, if (i == 1) "zj_dm_row_alt" else "zj_dm_row")
            b.g(base + 1, 9, y + 1, 0, DP.POINT_ICONS[i])
            b.t(base + 2, 28, y, 80, 18, 0, fonts.p12, TXT, DP.POINT_NAMES[i])
            b.t(base + 3, 60, y, 114, 18, 0, fonts.p12, WHITE, alignX = 2)
            // Owner 2026-10-24 (advice taken): a click on a balance opens the Info window on those points - never the store (N280).
            b.hit(base + 4, 5, y, 174, 18, 0, "Info")
        }
        b.g(DP.EMBLEM_BG, 5, DP.EMBLEM_Y, 0, "zj_dm_row")
        b.obj(DP.EMBLEM_ITEM, 8, DP.EMBLEM_Y + 1, 16, 16, 0)
        b.t(DP.EMBLEM_TEXT, 28, DP.EMBLEM_Y, 100, 18, 0, fonts.p12, TXT, "Archaic Emblem")
        b.t(DP.EMBLEM_VALUE, 100, DP.EMBLEM_Y, 62, 18, 0, fonts.p12, WHITE, alignX = 2)
        b.g(DP.EMBLEM_ARROW, 168, DP.EMBLEM_Y + 5, 0, "tk_chev")
        b.hit(DP.EMBLEM_HIT, 5, DP.EMBLEM_Y, 174, 18, 0, "Info")
        for (i in 0 until 2) {
            val base = DP.event(i)
            val y = DP.EVENT_Y + i * DP.EVENT_STEP
            b.g(base, 5, y, 0, "dm_ev4")
            b.g(base + 1, 8, y + 5, 0, if (i == 0) "dm_clock" else "dm_chest")
            b.t(base + 2, 28, y - 1, 120, 14, 0, fonts.p11, TXT)
            b.t(base + 3, 28, y + 11, 110, 14, 0, fonts.p11, LABEL)
            b.t(base + 4, 120, y - 1, 53, 14, 0, fonts.p11, WHITE, alignX = 2)
            b.g(base + 5, 141, y + 1, 0, "dm_mp", hidden = true, ops = listOf("Show on map"))
            b.t(base + 6, 141, y, 34, 13, 0, fonts.p11, ORANGE, "Map", alignX = 1, hidden = true)
        }
        return b.done(DP.COUNT)
    }

    // ================================================================== 3 Deadman Tasks

    object TP {
        const val SKULL = P_FIRST
        const val TIER_FIRST = P_FIRST + 1 // 4 tiers: icon, name, bar bg, fill clip layer, fill rect, count, chevron, hit
        const val TIER_STRIDE = 8
        const val HR2 = TIER_FIRST + 4 * TIER_STRIDE
        const val CLOSEST = HR2 + 1
        const val CARD_FIRST = HR2 + 2 // 4 cards: bg, round, icon, text, dp, bar bg, fill clip, fill, count
        const val CARD_STRIDE = 9
        const val EMPTY = CARD_FIRST + 4 * CARD_STRIDE
        const val COUNT = EMPTY + 1

        fun tier(i: Int) = TIER_FIRST + i * TIER_STRIDE

        fun card(i: Int) = CARD_FIRST + i * CARD_STRIDE
    }

    /** Inner width of a tier's progress bar (tk_bar 54 px minus its border). */
    const val TIER_FILL = 52

    val TIER_ICONS = listOf("tk_easy", "tk_medium", "tk_hard", "tk_elite")
    val TIER_COLOURS = intArrayOf(0xC98A4B, 0x7F9FE0, 0x4FD0D6, 0xFF6262)
    val TIER_NAMES = listOf("Easy", "Medium", "Hard", "Elite")

    fun tasks(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.panel("Deadman Tasks")
        b.g(TP.SKULL, 120, 4, 0, "dm_redskull13")
        for (i in 0 until 4) {
            val base = TP.tier(i)
            val y = 25 + i * 17
            b.g(base, 8, y + 1, 0, TIER_ICONS[i])
            b.t(base + 1, 27, y, 50, 17, 0, fonts.b12, TIER_COLOURS[i], TIER_NAMES[i])
            // N278: the bar starts 7 px after the widest name ("Medium", b12 49 px from x 27) - it touched it before.
            b.g(base + 2, 83, y + 6, 0, "tk_bar")
            b.layer(base + 3, 84, y + 7, TIER_FILL, 4, 0)
            b.rect(base + 4, -TIER_FILL, 0, TIER_FILL, 4, base + 3, TIER_COLOURS[i], filled = true)
            b.t(base + 5, 120, y, 47, 17, 0, fonts.p11, WHITE, alignX = 2)
            b.g(base + 6, 172, y + 5, 0, "tk_chev")
            b.hit(base + 7, 5, y, 174, 17, 0, "View")
        }
        b.g(TP.HR2, 5, 95, 0, "qt_hr")
        b.t(TP.CLOSEST, 6, 97, 170, 18, 0, fonts.b12, GOLD, "Closest to done")
        for (i in 0 until 4) {
            val base = TP.card(i)
            val y = 115 + i * 28
            b.g(base, 5, y, 0, "tk_card_easy", hidden = true)
            b.g(base + 1, 10, y + 3, 0, "tk_rnd18", hidden = true)
            b.obj(base + 2, 11, y + 4, 16, 16, 0)
            b.t(base + 3, 31, y, 110, 15, 0, fonts.p11, TXT, hidden = true)
            b.t(base + 4, 120, y, 54, 15, 0, fonts.p11, 0x56E156, alignX = 2, hidden = true)
            b.g(base + 5, 31, y + 16, 0, "tk_pb110", hidden = true)
            b.layer(base + 6, 32, y + 17, 108, 3, 0, hidden = true)
            b.g(base + 7, -108, 0, base + 6, "grn_fill", w = 108, h = 3)
            b.t(base + 8, 130, y + 11, 44, 13, 0, fonts.p11, GREY, alignX = 2, hidden = true)
        }
        b.t(TP.EMPTY, 5, 120, 174, 60, 0, fonts.p11, GREY, hidden = true, alignX = 1, lineHeight = 13)
        return b.done(TP.COUNT)
    }

    // ================================================================== 4 Drops & Collection Log

    object LP {
        const val CARD1 = P_FIRST
        const val VIEWER_TITLE = CARD1 + 1
        const val VIEWER_SUB = CARD1 + 2
        const val VIEWER_HIT = CARD1 + 3
        const val CARD2 = CARD1 + 4
        const val LOG_TITLE = CARD1 + 5
        const val LOG_SUB = CARD1 + 6
        const val LOG_CLIP = CARD1 + 7
        const val LOG_FILL = CARD1 + 8
        const val LOG_HIT = CARD1 + 9

        // N267 (owner 2026-10-24): "Recently viewed" is gone; "New in your log" moved up into its place.
        const val NEW_TITLE = CARD1 + 10
        const val SLOT_FIRST = NEW_TITLE + 1 // 5 slots: bg, item, kc
        const val SLOT_STRIDE = 3
        const val COUNT = SLOT_FIRST + 5 * SLOT_STRIDE
        val SLOT_X = intArrayOf(5, 40, 75, 111, 146)
        val SLOT_W = intArrayOf(33, 33, 34, 33, 33)
        const val NEW_Y = 111

        fun slot(i: Int) = SLOT_FIRST + i * SLOT_STRIDE
    }

    fun dropsLog(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.panel("Drops & Collection Log")
        b.g(LP.CARD1, 5, 25, 0, "dl_card1")
        b.t(LP.VIEWER_TITLE, 47, 26, 130, 16, 0, fonts.b12, ORANGE, "Drop Viewer")
        b.t(LP.VIEWER_SUB, 47, 41, 130, 15, 0, fonts.p11, 0xD9CCB3, "Drops & your chance")
        b.hit(LP.VIEWER_HIT, 5, 25, 174, 38, 0, "Open")
        b.g(LP.CARD2, 5, 66, 0, "dl_card2")
        b.t(LP.LOG_TITLE, 47, 66, 130, 16, 0, fonts.b12, ORANGE, "Collection Log")
        b.t(LP.LOG_SUB, 47, 81, 130, 15, 0, fonts.p11, 0xD9CCB3)
        b.layer(LP.LOG_CLIP, 47, 101, 120, 4, 0)
        b.g(LP.LOG_FILL, -120, 0, LP.LOG_CLIP, "grn_fill")
        b.hit(LP.LOG_HIT, 5, 66, 174, 44, 0, "Open")
        b.t(LP.NEW_TITLE, 6, LP.NEW_Y, 170, 17, 0, fonts.b12, ORANGE, "New in your log")
        for (i in 0 until 5) {
            val base = LP.slot(i)
            val w = LP.SLOT_W[i]
            val y = LP.NEW_Y + 17
            b.g(base, LP.SLOT_X[i], y, 0, "dl_slot$w")
            b.obj(base + 1, LP.SLOT_X[i] + (w - 30) / 2, y + 1, 30, 21, 0, ops = listOf("Inspect"))
            b.t(base + 2, LP.SLOT_X[i], y + 20, w, 12, 0, fonts.p11, 0x7CE36B, alignX = 1, hidden = true)
        }
        return b.done(LP.COUNT)
    }

    // ================================================================== Deadman Tasks window (deadman-tasks.html)

    object W {
        const val BG = 2
        const val TITLE = 3
        const val CLOSE = 4
        const val TIER_FIRST = 5 // 5 rows (All + 4 tiers): sel bg, plain bg, icon, name, count, bar bg, fill clip, fill, hit
        const val TIER_STRIDE = 9
        const val REWARD_TITLE = TIER_FIRST + 5 * TIER_STRIDE
        const val REWARD_TEXT = REWARD_TITLE + 1
        const val TOTAL_SKULL = REWARD_TITLE + 2
        const val TOTAL = REWARD_TITLE + 3
        const val FILTER_FIRST = REWARD_TITLE + 4 // 3 filters: on, off, hit
        const val FILTER_STRIDE = 3
        const val TYPE_BG = FILTER_FIRST + 3 * FILTER_STRIDE
        const val TYPE_TEXT = TYPE_BG + 1
        const val TYPE_HIT = TYPE_BG + 2
        const val HEAD_TASK = TYPE_BG + 3
        const val HEAD_PROGRESS = TYPE_BG + 4
        const val HEAD_REWARD = TYPE_BG + 5
        const val ROWS = TYPE_BG + 6
        const val ROWS_SCROLLBAR = ROWS + 1
        const val EMPTY = ROWS + 2
        const val ROW_FIRST = ROWS + 3 // per row: bg, round, icon, name, tag, desc, count, bar bg, fill clip, fill, dp, ok
        const val ROW_STRIDE = 12
        const val ROW_SLOTS = 80
        const val COUNT = ROW_FIRST + ROW_SLOTS * ROW_STRIDE
        val FILTER_X = intArrayOf(163, 214, 261)
        val FILTER_NAMES = listOf("todo", "done", "all")
        const val ROWS_X = 164
        const val ROWS_Y = 71
        const val ROWS_W = 313
        const val ROWS_H = 243
        const val ROW_H = 30

        fun tier(i: Int) = TIER_FIRST + i * TIER_STRIDE

        fun filter(i: Int) = FILTER_FIRST + i * FILTER_STRIDE

        fun row(i: Int) = ROW_FIRST + i * ROW_STRIDE
    }

    fun taskWindow(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.root()
        b.window()
        b.g(W.BG, 0, 0, 1, "tw_bg")
        b.t(W.TITLE, 0, 4, 500, 18, 1, fonts.b12, ORANGE, "Deadman Tasks", alignX = 1)
        b.hit(W.CLOSE, 472, 5, 21, 19, 1, "Close")
        val icons = listOf("tk_all19", "tk_easy19", "tk_medium19", "tk_hard19", "tk_elite19")
        val colours = intArrayOf(ORANGE) + TIER_COLOURS
        val names = listOf("All tasks") + TIER_NAMES
        for (i in 0 until 5) {
            val base = W.tier(i)
            val y = 31 + i * 36
            b.g(base, 8, y, 1, "tw_tier_sel", hidden = i != 0)
            b.g(base + 1, 8, y, 1, "tw_tier", hidden = i == 0)
            b.g(base + 2, 13, y + 9, 1, icons[i])
            b.t(base + 3, 38, y + 2, 80, 17, 1, fonts.b12, colours[i], names[i])
            b.t(base + 4, 90, y + 2, 60, 15, 1, fonts.p11, WHITE, alignX = 2)
            b.g(base + 5, 38, y + 22, 1, "tw_bar")
            b.layer(base + 6, 39, y + 23, 110, 5, 1)
            b.rect(base + 7, -110, 0, 110, 5, base + 6, colours[i], filled = true)
            b.hit(base + 8, 8, y, 148, 36, 1, "View")
        }
        b.t(W.REWARD_TITLE, 19, 218, 130, 15, 1, fonts.b12, ORANGE, "Next reward")
        b.t(W.REWARD_TEXT, 19, 233, 130, 30, 1, fonts.p11, GREY, alignY = 0, lineHeight = 14)
        b.g(W.TOTAL_SKULL, 13, 283, 1, "dm_redskull12")
        b.t(W.TOTAL, 30, 281, 120, 15, 1, fonts.p11, GREY)
        for (i in 0 until 3) {
            val base = W.filter(i)
            val n = W.FILTER_NAMES[i]
            b.g(base, W.FILTER_X[i], 30, 1, "tw_fb_${n}_on", hidden = i != 0)
            b.g(base + 1, W.FILTER_X[i], 30, 1, "tw_fb_${n}_off", hidden = i == 0)
            b.hit(base + 2, W.FILTER_X[i], 30, size("tw_fb_${n}_on")[0], 20, 1, "View")
        }
        b.g(W.TYPE_BG, 301, 30, 1, "tw_fb_type")
        b.t(W.TYPE_TEXT, 301, 30, 96, 20, 1, fonts.p11, 0xA29680, "Type: All", alignX = 1)
        b.hit(W.TYPE_HIT, 301, 30, 96, 20, 1, "Next type")
        b.t(W.HEAD_TASK, 191, 55, 100, 15, 1, fonts.p11, 0xC7B48E, "Task")
        b.t(W.HEAD_PROGRESS, 362, 55, 56, 15, 1, fonts.p11, 0xC7B48E, "Progress", alignX = 1)
        b.t(W.HEAD_REWARD, 420, 55, 51, 15, 1, fonts.p11, 0xC7B48E, "Reward", alignX = 2)
        b.layer(W.ROWS, W.ROWS_X, W.ROWS_Y, W.ROWS_W, W.ROWS_H, 1)
        b.scrollbarFor(W.ROWS_SCROLLBAR, W.ROWS_X + W.ROWS_W, W.ROWS_Y, W.ROWS_H, 1, TASK_WINDOW, W.ROWS)
        b.t(W.EMPTY, 0, 30, W.ROWS_W, 40, W.ROWS, fonts.p12, GREY, alignX = 1, hidden = true)
        for (r in 0 until W.ROW_SLOTS) {
            val base = W.row(r)
            val y = r * W.ROW_H
            b.g(base, 0, y, W.ROWS, "tw_row_easy", hidden = true)
            b.g(base + 1, 5, y + 5, W.ROWS, "tk_rnd20", hidden = true)
            b.obj(base + 2, 7, y + 7, 16, 16, W.ROWS)
            b.t(base + 3, 30, y, 165, 15, W.ROWS, fonts.p11, TXT, hidden = true)
            b.g(base + 4, 120, y + 2, W.ROWS, "tw_tag_pvp", hidden = true)
            b.t(base + 5, 30, y + 13, 165, 15, W.ROWS, fonts.p11, GREY, hidden = true)
            b.t(base + 6, 201, y, 56, 15, W.ROWS, fonts.p11, 0xD8CBB2, alignX = 1, hidden = true)
            b.g(base + 7, 201, y + 17, W.ROWS, "tw_pb56", hidden = true)
            b.layer(base + 8, 202, y + 18, 54, 4, W.ROWS, hidden = true)
            b.g(base + 9, -54, 0, base + 8, "grn_fill", w = 54, h = 4)
            b.t(base + 10, 240, y + 7, 68, 15, W.ROWS, fonts.p11, 0x56E156, alignX = 2, hidden = true)
            b.g(base + 11, 296, y + 9, W.ROWS, "ok", hidden = true)
        }
        return b.done(W.COUNT)
    }

    // ================================================================== Deadman Info window (screens\team\INFO\info-v1.html)

    object I {
        const val BG = 2
        const val TITLE = 3
        const val CLOSE = 4
        const val GROUP_FIRST = 5 // 3 group headers
        const val GROUPS = 3
        const val SUBJECT_FIRST = GROUP_FIRST + GROUPS // per subject: selected bg, icon, name, hit
        const val SUBJECT_STRIDE = 4
        const val SUBJECTS = 10
        const val BIG = SUBJECT_FIRST + SUBJECTS * SUBJECT_STRIDE
        const val NAME = BIG + 1
        const val TAG = BIG + 2
        const val ROW_FIRST = BIG + 3 // per row: stripe, four table columns (left-aligned), the fact value (right-aligned)
        const val ROW_STRIDE = 6
        const val R_VALUE = 5
        const val ROWS = 8
        const val TEXT = ROW_FIRST + ROWS * ROW_STRIDE
        const val BAR = TEXT + 1
        const val BAR_TEXT = TEXT + 2
        const val MAP = TEXT + 3
        const val MAP_TEXT = TEXT + 4
        const val COUNT = TEXT + 5

        /** The list: a group header is 14 px, a subject 24 px (3 x 14 + 10 x 24 = 282 of the 284 inside the well). */
        const val LIST_X = 8
        const val LIST_Y = 31
        const val GROUP_H = 14
        const val SUBJECT_H = 24

        /** Subjects per group (QuestTabInfo.Subject order): Points 3, Deadman 3, Rules 4. */
        val GROUP_SIZES = intArrayOf(3, 3, 4)
        val GROUP_NAMES = listOf("Points", "Deadman", "Rules")

        /** The page (window pixels): fact rows from [ROW_Y], 18 px each, 318 wide from [ROW_X]; the live bar at the bottom. */
        const val ROW_X = 167
        const val ROW_Y = 81
        const val ROW_H = 18
        const val ROW_W = 318
        const val TEXT_X = 169
        const val TEXT_W = 314
        const val TEXT_BOTTOM = 279
        const val BAR_X = 167
        const val BAR_Y = 283
        const val MAP_X = 393
        const val MAP_Y = 287

        fun subject(i: Int) = SUBJECT_FIRST + i * SUBJECT_STRIDE

        fun row(i: Int) = ROW_FIRST + i * ROW_STRIDE

        /** Window y of subject [i]'s row. */
        fun subjectY(i: Int): Int {
            var y = LIST_Y
            var left = i
            for (g in GROUP_SIZES.indices) {
                y += GROUP_H
                if (left < GROUP_SIZES[g]) return y + left * SUBJECT_H
                y += GROUP_SIZES[g] * SUBJECT_H
                left -= GROUP_SIZES[g]
            }
            error("subject $i")
        }

        /** Window y of group [g]'s header (just above its first subject). */
        fun groupY(g: Int): Int = subjectY(GROUP_SIZES.take(g).sum()) - GROUP_H
    }

    /** The subjects' icon names (zi_ic_* in the list, zi_big_* on the page), QuestTabInfo.Subject order. */
    val INFO_ICONS = listOf("loyalty", "donator", "deadman", "emblem", "breach", "chest", "protection", "skull", "brackets", "teleports")

    fun info(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.root()
        b.window()
        b.g(I.BG, 0, 0, 1, "zi_bg")
        b.t(I.TITLE, 0, 4, 500, 18, 1, fonts.b12, ORANGE, "Deadman Info", alignX = 1)
        b.hit(I.CLOSE, 472, 5, 21, 19, 1, "Close")
        for (g in 0 until I.GROUPS) b.t(I.GROUP_FIRST + g, 13, I.groupY(g), 140, I.GROUP_H, 1, fonts.p11, LABEL, I.GROUP_NAMES[g], alignY = 2)
        for (s in 0 until I.SUBJECTS) {
            val base = I.subject(s)
            val y = I.subjectY(s)
            b.g(base, I.LIST_X, y, 1, "zi_sel", hidden = true)
            b.g(base + 1, 13, y + 4, 1, "zi_ic_${INFO_ICONS[s]}")
            b.t(base + 2, 36, y, 118, I.SUBJECT_H, 1, fonts.p11, ORANGE)
            b.hit(base + 3, I.LIST_X, y, 148, I.SUBJECT_H, 1, "View")
        }
        b.g(I.BIG, 169, 35, 1, "zi_big_loyalty")
        b.t(I.NAME, 209, 33, 280, 16, 1, fonts.b12, ORANGE)
        // N285 (owner 2026-10-24: readable in fixed + Stretched): lighter labels, facts in p12.
        b.t(I.TAG, 209, 50, 280, 15, 1, fonts.p11, LABEL)
        for (r in 0 until I.ROWS) {
            val base = I.row(r)
            val y = I.ROW_Y + r * I.ROW_H
            b.g(base, I.ROW_X, y, 1, "zi_stripe", hidden = true)
            b.t(base + 1, I.ROW_X + 6, y, 200, I.ROW_H, 1, fonts.p12, LABEL, hidden = true)
            b.t(base + 2, I.ROW_X + 100, y, 80, I.ROW_H, 1, fonts.p11, WHITE, hidden = true)
            b.t(base + 3, I.ROW_X + 180, y, 80, I.ROW_H, 1, fonts.p11, WHITE, hidden = true)
            b.t(base + 4, I.ROW_X + 270, y, 40, I.ROW_H, 1, fonts.p11, WHITE, hidden = true)
            b.t(base + I.R_VALUE, I.ROW_X, y, I.ROW_W - 6, I.ROW_H, 1, fonts.p12, WHITE, alignX = 2, hidden = true)
        }
        b.t(I.TEXT, I.TEXT_X, I.ROW_Y, I.TEXT_W, I.TEXT_BOTTOM - I.ROW_Y, 1, fonts.p11, TXT, alignX = 0, alignY = 0, lineHeight = 15)
        b.g(I.BAR, I.BAR_X, I.BAR_Y, 1, "zi_bar")
        b.t(I.BAR_TEXT, I.BAR_X + 8, I.BAR_Y, 220, 26, 1, fonts.p12, TXT)
        b.g(I.MAP, I.MAP_X, I.MAP_Y, 1, "zi_map", hidden = true, ops = listOf("Show on map"))
        b.t(I.MAP_TEXT, I.MAP_X + 22, I.MAP_Y, 64, 18, 1, fonts.p11, ORANGE, "Show on map", hidden = true)
        return b.done(I.COUNT)
    }

    // ================================================================== import

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val index = DeathsOfficeInterfaceImportTool.INDEX_INTERFACES
        val spritesIndex = DeathsOfficeInterfaceImportTool.INDEX_SPRITES
        val library = CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        val fonts: LootKeyInterfaceImportTool.Fonts
        val current = HashMap<Int, Map<Int, String>>()
        val currentSprites = HashMap<Int, String>()
        val ids = listOf(CONTAINER, DEADMAN, QUESTS, TASKS, INFO, VIEWER, DROPS_LOG, LOG, TASK_WINDOW)
        try {
            fonts = LootKeyInterfaceImportTool.fonts(library)
            ids.forEach { id ->
                val files = library.index(index).archive(id)?.fileIds()?.toList() ?: emptyList()
                current[id] = files.associateWith { f -> CacheItemProbeTool.sha1(library.data(index, id, f)!!) }
            }
            SPRITES.indices.forEach { i ->
                library.data(spritesIndex, SPRITE_BASE + i, 0)?.let { currentSprites[SPRITE_BASE + i] = CacheItemProbeTool.sha1(it) }
            }
            require(library.index(spritesIndex).archive(SPRITE_BASE - 1) == null || SPRITE_BASE - 1 <= 8518) { "sprite range moved" }
        } finally {
            library.close()
        }
        val mutations = mutableListOf<CacheMutation>()
        SPRITES.forEachIndexed { i, name ->
            val image = ImageIO.read(File("$ART_DIR/$name.png")) ?: error("missing $ART_DIR/$name.png")
            mutations += CacheMutation(spritesIndex, SPRITE_BASE + i, 0, StoreArtTool.encode(image), "n108 sprite $name -> ${SPRITE_BASE + i}", expectedCurrentSha1 = currentSprites[SPRITE_BASE + i])
        }
        val screens =
            listOf(
                CONTAINER to container(fonts),
                DEADMAN to deadman(fonts),
                QUESTS to quests(fonts),
                TASKS to tasks(fonts),
                INFO to info(fonts),
                VIEWER to viewer(fonts),
                DROPS_LOG to dropsLog(fonts),
                LOG to log(fonts),
                TASK_WINDOW to taskWindow(fonts),
            )
        screens.forEach { (id, comps) ->
            val have = current.getValue(id)
            comps.forEach { c ->
                mutations += CacheMutation(index, id, c.id, DeathsOfficeInterfaceImportTool.encode(c), "interface $id:${c.id} type=${c.type}", expectedCurrentSha1 = have[c.id])
            }
            have.keys.filter { it >= comps.size }.sorted().forEach { f ->
                mutations += CacheMutation(index, id, f, null, "interface $id:$f (old layout)", expectedCurrentSha1 = have[f])
            }
        }
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.take(20).forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interfaces $ids and sprites $SPRITE_BASE..${SPRITE_BASE + SPRITES.size - 1}")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }
}
