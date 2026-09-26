package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Builds the OSRS death screens as revision-667 if3 interfaces and imports the OSRS sprites and models they draw into both
 * production caches through [CacheTransaction] (the same route as [LootKeyInterfaceImportTool]):
 *
 *  - [OFFICE] "Death's Office Item Retrieval" (OSRS 669),
 *  - [GRAVE] the gravestone (OSRS 672, "gravestone_generic"),
 *  - [COFFER] / [COFFER_SIDE] "Sacrifice items to Death's Coffer" and its side inventory (OSRS 670 / 671),
 *  - [KEPT] "Items Kept on Death" (OSRS 4, "deathkeep").
 *
 * Sources (read-only): component geometry from the pinned OpenRS2 2686 cache (index 3, decoded 2026-09-26), the layout
 * arithmetic, texts, fonts, colours and sprite names from the OSRS clientscripts that build these screens (RuneStar
 * cs2-scripts: steelborder 228, v2_stone_button_* 134/486, options_button_off 835, gravestone_generic_init 3462 /
 * window_set 3464 / parsefee 3475 / parsecoffer 3476 / title 3474, death_office_init 3490 / redraw 3492 / title 3494,
 * death_coffer_init 3479 / script3483 / drawbutton 3489, deathkeep_init 972 / left_redraw 974 / right_initbutton 3453),
 * sprite ids from the RuneLite gameval SpriteID table, and the reference screenshots of the OSRS "Death Changes" news post
 * (25 June 2020: the gravestone and the coffer screens).
 *
 * The OSRS screens are drawn by clientscripts at runtime; here every piece is a static component and the server drives
 * them with IF_SETOBJECT / IF_SETTEXT / IF_SETHIDE / IF_SETPOSITION. Two things need the client: the scrollbars (the 667
 * clientscript 30 builds them from a layer's scroll height, bound on load exactly like 667's own interfaces) and the scroll
 * height itself, which only a clientscript can set - [SCROLL_SCRIPT] is a new eight-line clientscript that sets a layer's
 * scroll height, clamps its scroll position and rebuilds its scrollbar.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool [--apply]`. The tool owns
 * interfaces 1160-1164, sprites [SPRITE_BASE].., clientscript [SCROLL_SCRIPT] and its three models; a re-run replaces
 * exactly what it wrote before (pinned through `--replace-from-journal=<tx>`).
 */
object DeathsOfficeInterfaceImportTool {
    const val OFFICE = 1160
    const val GRAVE = 1161
    const val COFFER = 1162
    const val COFFER_SIDE = 1163
    const val KEPT = 1164

    const val INDEX_INTERFACES = 3
    const val INDEX_SPRITES = 8
    const val INDEX_CLIENTSCRIPTS = 12
    const val INDEX_MODELS = 7

    /** The first sprite id above the cache's last sprite group (8194, probe 2026-09-26). */
    const val SPRITE_BASE = 8200

    /** The first clientscript id above the cache's last one (5376, probe 2026-09-26). */
    const val SCROLL_SCRIPT = 5377

    /** 667 scrollbar builder: script 30(scrollbar, layer) -> 31 with the v2 scrollbar sprites 773/788-792. */
    const val SCROLLBAR_SCRIPT = 30

    /** Clientscript argument that the client replaces with the component the hook runs on (667 `event_com`). */
    const val EVENT_COM = -2147483645

    /** OSRS sprites this tool imports; 667 id = [SPRITE_BASE] + index. */
    val OSRS_SPRITES =
        intArrayOf(
            310, 311, 312, 313, // 0-3 steelborder corners TL, TR, BL, BR (25x30)
            172, 173, // 4-5 miscgraphics 2/3: left and bottom edges
            314, 315, // 6-7 steelborder2 0/1: top and right edges
            2546, // 8 steelborder_divider 0
            535, 536, // 9-10 close_buttons 0/1
            913, 914, 915, 916, 917, 918, 919, 920, // 11-18 v2_stone_button 0-7
            921, 922, 923, 924, 925, 926, 927, 928, // 19-26 v2_stone_button_in 0-7
            929, 930, 931, 932, 933, 934, 935, 936, // 27-34 v2_stone_button_out 0-7
            1141, 1142, 1143, 1144, 1145, 1146, 1147, 1148, 1149, // 35-43 options button (off) 9-slice
            1342, // 44 bankbuttons 7: padlock ("Unlock")
            897, // 45 tradebacking_dark
            1040, // 46 tradebacking_light
            698, 699, // 47-48 options_radio_buttons 1 (off) / 2 (on)
            2507, // 49 mod_icons_interface 21: the gravestone timer icon (gravestone_hud_init 3457), drawn by the client HUD
            2216, // 50 the Death's Office map icon (pixel-identical to the OSRS Wiki "Death's Office icon.png")
        )

    /** 667 sprite of the Death's Office map icon ([DeathsOfficeMapImportTool] points its map element at it). */
    const val OFFICE_MAP_ICON = SPRITE_BASE + 50

    /** 667 sprite of the gravestone timer icon, read by the client's Deadman HUD (DeadmanSkullHud). */
    const val GRAVE_HUD_ICON = SPRITE_BASE + 49

    /** Sprites the loot-key import already brought in (reused, never duplicated). */
    private val LOOT_KEY_SPRITES = setOf(297, 1226)

    fun sprite(osrsId: Int): Int {
        if (osrsId in LOOT_KEY_SPRITES) return LootKeyInterfaceImportTool.sprite(osrsId)
        val index = OSRS_SPRITES.indexOf(osrsId)
        require(index >= 0) { "OSRS sprite $osrsId is not part of the import" }
        return SPRITE_BASE + index
    }

    /** OSRS models drawn by the screens: 9037 incinerator (672:17), 4620 coins (670:4), 38743 swirl (670:7). */
    val OSRS_MODELS = intArrayOf(9037, 4620, 38743)

    /** OSRS sequence 7301 animates the swirl behind the coffer's item (670:7); imported by the npc tool's deaths-office batch. */
    const val OSRS_SWIRL_SEQ = 7301

    // ---- shared frame (steelborder) component ids: the same in every window ----
    const val ROOT = 0
    const val WINDOW = 1
    const val FRAME_BG = 2
    const val TITLE = 3
    const val CLOSE = 13
    const val FRAME_END = 14

    // ---- 1160 Death's Office Item Retrieval (OSRS 669, 480x320) ----
    object Office {
        const val WIDTH = 480
        const val HEIGHT = 320
        const val CONTAINER = 14
        const val ITEMS = 15
        const val SCROLLBAR = 16
        const val BOTTOM = 17
        const val INFO_NONE = 18
        const val INFO_ITEM = 19
        const val BUTTON_1 = 20
        const val BUTTON_5 = 31
        const val BUTTON_X = 42
        const val BUTTON_ALL = 53
        const val BUTTON_TAKE_ALL = 64
        const val SLOT_FIRST = 75
        const val SLOTS = 120
        const val SELECTED = SLOT_FIRST + SLOTS // 195
        const val COUNT = SELECTED + 1

        /** death_office_redraw: 440 px wide list -> 9 columns 50 px apart (36 + 14), rows 42 px (32 + 10), 2 px margin. */
        const val COLUMNS = 9
        const val COLUMN_STEP = 50
        const val ROW_STEP = 42
        const val MARGIN = 2
    }

    // ---- 1161 Gravestone (OSRS 672 at its default 341x296) ----
    object Grave {
        const val WIDTH = 341
        const val HEIGHT = 296
        const val FREE_CONTAINER = 14
        const val FREE_TEXT = 15
        const val FREE_ITEMS = 16
        const val FREE_SCROLLBAR = 17
        const val FREE_BUTTON = 18
        const val PAY_CONTAINER = 29
        const val PAY_TOP = 30
        const val FEE = 31
        const val PAY_ITEMS = 32
        const val PAY_SCROLLBAR = 33
        const val UNLOCK_BUTTON = 34
        const val PAY_TAKE_ALL_BUTTON = 45
        const val INCINERATOR = 56
        const val INFO = 57
        const val FREE_SLOT_FIRST = 58
        const val PAY_SLOT_FIRST = FREE_SLOT_FIRST + 120 // 178
        const val PAY_FEE_FIRST = PAY_SLOT_FIRST + 120 // 298
        const val SLOTS = 120
        const val COUNT = PAY_FEE_FIRST + SLOTS // 418

        /** gravestone_generic_window_set: 259 px wide lists -> 6 columns 44 px apart (36 + 8), 2 px margin. */
        const val COLUMNS = 6
        const val COLUMN_STEP = 44
        const val MARGIN = 2
        const val FREE_ROW_STEP = 36
        const val PAY_ROW_STEP = 48
    }

    // ---- 1162 Death's Coffer (OSRS 670, 460x320) ----
    object Coffer {
        const val WIDTH = 460
        const val HEIGHT = 320
        const val CONTENTS = 14
        const val LEFT = 15
        const val LEFT_TEXT = 16
        const val COINS_MODEL = 17
        const val COFFER_TEXT = 18
        const val RIGHT = 19
        const val SWIRL = 23
        const val DISPLAY = 24
        const val NAME = 25
        const val VALUE = 26
        const val BUTTON_1 = 27
        const val BUTTON_5 = 61
        const val BUTTON_X = 95
        const val BUTTON_ALL = 129
        const val BUTTON_STRIDE = 34

        /** Inside a quantity button: +1 active look, +12 selected look, +23 disabled look (each a layer of 11). */
        const val LOOK_ACTIVE = 1
        const val LOOK_SELECTED = 12
        const val LOOK_DISABLED = 23
        const val CONFIRM = 163
        const val CONFIRM_ACTIVE = 164
        const val CONFIRM_VALUE = 175
        const val CONFIRM_DISABLED = 176
        const val COUNT = 187
    }

    // ---- 1163 Death's Coffer side inventory (OSRS 671, 162x250) ----
    object CofferSide {
        /** OSRS 671:0; the server fills it with the 667 inventory grid script 150 (inventory 93, 4 x 7, op "Select"). */
        const val ROOT = 0
        const val COUNT = 1
    }

    // ---- 1164 Items Kept on Death (OSRS 4, 500x322) ----
    object Kept {
        const val WIDTH = 500
        const val HEIGHT = 322
        const val CONTENTS = 14
        const val LEFT = 15
        const val ITEMS = 16
        const val SCROLLBAR = 17
        const val RIGHT = 18
        const val SHOWING = 19
        const val TOGGLE_FIRST = 20
        const val TOGGLE_STRIDE = 25

        /** Inside a toggle: +1 the "on" look, +13 the "off" look (each a layer of 12). */
        const val LOOK_ON = 1
        const val LOOK_OFF = 13
        const val VALUE = 120
        const val HEADER_FIRST = 123
        const val HEADERS = 5
        const val DIVIDER_FIRST = 128
        const val DIVIDERS = 4
        const val SLOT_FIRST = 136
        const val SLOTS = 60
        const val COUNT = SLOT_FIRST + SLOTS // 196

        /** deathkeep_init: 311 px wide list -> 7 columns 45 px apart (36 + 9), 2 px margin; rows 32 + 9. */
        const val COLUMNS = 7
        const val COLUMN_STEP = 45
        const val MARGIN = 2
        const val ROW_STEP = 41

        /** deathkeep_right_initbutton texts (enum 2910) and their measured heights (5 + max(17, lines * 12 + 2) + 5). */
        val TOGGLE_TEXTS = listOf("Protect Item prayer enabled", "PK Skull active", "Killed by a player", "Wilderness beyond level 20")
        val TOGGLE_HEIGHTS = intArrayOf(36, 27, 27, 36)
    }

    const val ORANGE = 0xFF981F
    const val WHITE = 0xFFFFFF
    const val GREY = 0x9F9F9F
    const val BUTTON_TEXT = 0xDFDFDF
    const val THINBOX_OUTER = 0x0E0E0C
    const val THINBOX_INNER = 0x474745

    private const val TYPE_LAYER = 0
    private const val TYPE_RECTANGLE = 3
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5
    private const val TYPE_MODEL = 6

    /** One static if3 component, with the few fields [LootKeyInterfaceImportTool.Component] does not carry. */
    data class Comp(
        val id: Int,
        val type: Int,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val parent: Int,
        val resizeX: Int = 0,
        val resizeY: Int = 0,
        val reposX: Int = 0,
        val reposY: Int = 0,
        val hidden: Boolean = false,
        val graphic: Int = -1,
        val tiling: Boolean = false,
        val transparency: Int = 0,
        val outline: Int = 0,
        val shadow: Int = 0,
        val font: Int = -1,
        val text: String = "",
        val lineHeight: Int = 0,
        val alignX: Int = 1,
        val alignY: Int = 1,
        val colour: Int = 0,
        val filled: Boolean = false,
        val ops: List<String> = emptyList(),
        val opBase: String = "",
        val model: Int = -1,
        val originX: Int = 0,
        val originY: Int = 0,
        val xan: Int = 0,
        val yan: Int = 0,
        val zan: Int = 0,
        val zoom: Int = 0,
        val animation: Int = -1,
        /** Script id followed by its int arguments ([EVENT_COM] = this component). */
        val onLoad: List<Int> = emptyList(),
    )

    private class Out {
        val bytes = ByteArrayOutputStream()

        fun p1(v: Int) = bytes.write(v and 0xFF)

        fun p2(v: Int) {
            p1(v shr 8)
            p1(v)
        }

        fun p3(v: Int) {
            p1(v shr 16)
            p2(v)
        }

        fun p4(v: Int) {
            p2(v shr 16)
            p2(v)
        }

        fun pjstr(s: String) {
            bytes.write(s.toByteArray(Charsets.ISO_8859_1))
            p1(0)
        }
    }

    /** The legacy (version byte 255) if3 encoding this client's `Component.decode` reads, like every 667 cache component. */
    fun encode(c: Comp): ByteArray {
        val o = Out()
        o.p1(255)
        o.p1(c.type)
        o.p2(0) // clientcode
        o.p2(c.x)
        o.p2(c.y)
        o.p2(c.width)
        o.p2(c.height)
        o.p1(c.resizeX)
        o.p1(c.resizeY)
        o.p1(c.reposX)
        o.p1(c.reposY)
        o.p2(if (c.parent < 0) 65535 else c.parent)
        o.p1(if (c.hidden) 1 else 0)
        when (c.type) {
            TYPE_LAYER -> {
                o.p2(0) // scroll width
                o.p2(0) // scroll height: set at runtime by [SCROLL_SCRIPT]
                o.p1(0) // no-click-through
            }
            TYPE_GRAPHIC -> {
                o.p4(c.graphic)
                o.p2(0) // angle2d
                o.p1(if (c.tiling) 1 else 0)
                o.p1(c.transparency)
                o.p1(c.outline)
                o.p4(c.shadow)
                o.p1(0) // horizontal flip
                o.p1(0) // vertical flip
                o.p4(0) // colour
            }
            TYPE_TEXT -> {
                o.p2(if (c.font < 0) 65535 else c.font)
                o.pjstr(c.text)
                o.p1(c.lineHeight)
                o.p1(c.alignX)
                o.p1(c.alignY)
                o.p1(1) // shadow: every text on these screens has one
                o.p4(c.colour)
                o.p1(c.transparency)
            }
            TYPE_RECTANGLE -> {
                o.p4(c.colour)
                o.p1(if (c.filled) 1 else 0)
                o.p1(c.transparency)
            }
            TYPE_MODEL -> {
                o.p2(if (c.model < 0) 65535 else c.model)
                o.p1(1) // ortho2d
                o.p2(c.originX)
                o.p2(c.originY)
                o.p2(c.xan)
                o.p2(c.yan)
                o.p2(c.zan)
                o.p2(c.zoom)
                o.p2(if (c.animation < 0) 65535 else c.animation)
            }
            else -> error("unsupported type ${c.type}")
        }
        o.p3(0) // events: the server enables ops with IF_SETEVENTS
        o.p1(0) // no op keys
        o.pjstr(c.opBase)
        o.p1(c.ops.size)
        c.ops.forEach { o.pjstr(it) }
        o.pjstr("") // pause text
        o.p1(0) // drag dead zone
        o.p1(0) // drag dead time
        o.p1(0) // drag render behaviour
        o.pjstr("") // target verb
        // 20 hooks (onLoad .. onVarcstrTransmit; no onOpT for a legacy component): only onLoad is ever set here.
        if (c.onLoad.isEmpty()) {
            o.p1(0)
        } else {
            o.p1(c.onLoad.size)
            c.onLoad.forEach {
                o.p1(0) // int argument
                o.p4(it)
            }
        }
        repeat(19) { o.p1(0) }
        repeat(5) { o.p1(0) } // transmit lists
        return o.bytes.toByteArray()
    }

    /**
     * [SCROLL_SCRIPT](layer, scrollbar, height): IF_SETSCROLLSIZE(0, height, layer), then
     * IF_SETSCROLLPOS(0, min(scrolly(layer), max(0, height - height(layer))), layer) - the clamp OSRS applies after every
     * redraw - and GOSUB 30(scrollbar, layer) to rebuild the scrollbar. Opcodes from this client's `ClientScriptOpCode`:
     * IF_ variants are the CC_ ones + 1000 and pop their component last.
     */
    fun scrollScript(): ByteArray {
        val out = ByteArrayOutputStream()
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
        val pushInt = 0
        val pushLocal = 33
        val gosub = 40
        val ret = 21
        out.write(0) // no name
        op(pushInt, 0) // scroll width
        op(pushLocal, 2) // height
        op(pushLocal, 0) // layer
        op(2120, 0, byteOperand = true) // IF_SETSCROLLSIZE
        op(pushInt, 0) // scroll x
        op(pushLocal, 0)
        op(2601, 0, byteOperand = true) // IF_GETSCROLLY(layer)
        op(pushLocal, 2)
        op(pushLocal, 0)
        op(2503, 0, byteOperand = true) // IF_GETHEIGHT(layer)
        op(4001, 0, byteOperand = true) // SUB: height - layer height
        op(pushInt, 0)
        op(4017, 0, byteOperand = true) // MAX(.., 0)
        op(4016, 0, byteOperand = true) // MIN(scrolly, ..)
        op(pushLocal, 0)
        op(2100, 0, byteOperand = true) // IF_SETSCROLLPOS
        op(pushLocal, 1) // scrollbar
        op(pushLocal, 0) // layer
        op(gosub, SCROLLBAR_SCRIPT)
        op(ret, 0, byteOperand = true)
        // metadata: opcode count, int/string/long locals, int/string/long args, then the switch-table section (none).
        i32(count)
        u16(3)
        u16(0)
        u16(0)
        u16(3)
        u16(0)
        u16(0)
        out.write(0) // switch table count
        u16(1) // switch section length (the count byte)
        return out.toByteArray()
    }

    private class Builder(val fonts: LootKeyInterfaceImportTool.Fonts) {
        val list = mutableListOf<Comp>()

        fun add(c: Comp) {
            require(list.none { it.id == c.id }) { "duplicate component ${c.id}" }
            list += c
        }

        fun layer(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, hidden: Boolean = false, ops: List<String> = emptyList(), opBase: String = "", onLoad: List<Int> = emptyList()) =
            add(Comp(id, TYPE_LAYER, x, y, w, h, parent, hidden = hidden, ops = ops, opBase = opBase, onLoad = onLoad))

        fun graphic(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, sprite: Int, tiling: Boolean = false, ops: List<String> = emptyList(), transparency: Int = 0) =
            add(Comp(id, TYPE_GRAPHIC, x, y, w, h, parent, graphic = sprite, tiling = tiling, ops = ops, transparency = transparency))

        fun item(id: Int, x: Int, y: Int, parent: Int, ops: List<String>, outline: Int = 1, hidden: Boolean = true) =
            add(Comp(id, TYPE_GRAPHIC, x, y, 36, 32, parent, hidden = hidden, graphic = -1, outline = outline, shadow = 0x333333, ops = ops))

        fun text(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, font: Int, text: String, colour: Int = ORANGE, alignX: Int = 1, alignY: Int = 1, lineHeight: Int = 0, hidden: Boolean = false) =
            add(Comp(id, TYPE_TEXT, x, y, w, h, parent, hidden = hidden, font = font, text = text, colour = colour, alignX = alignX, alignY = alignY, lineHeight = lineHeight))

        fun rect(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, colour: Int, filled: Boolean = false, hidden: Boolean = false) =
            add(Comp(id, TYPE_RECTANGLE, x, y, w, h, parent, hidden = hidden, colour = colour, filled = filled))

        /** OSRS `steelborder` (clientscript 228): background, title, corners, edges, title divider and close button, ids 2..13. */
        fun frame(w: Int, h: Int, title: String) {
            add(Comp(ROOT, TYPE_LAYER, 0, 0, 0, 0, -1, resizeX = 1, resizeY = 1))
            add(Comp(WINDOW, TYPE_LAYER, 0, 0, w, h, ROOT, reposX = 1, reposY = 1))
            graphic(FRAME_BG, 1, 1, w - 2, h - 2, WINDOW, sprite(297), tiling = true)
            text(TITLE, 6, 6, w - 12, 24, WINDOW, fonts.b12, title)
            graphic(4, 0, 0, 25, 30, WINDOW, sprite(310), tiling = true)
            graphic(5, w - 25, 0, 25, 30, WINDOW, sprite(311), tiling = true)
            graphic(6, 0, h - 30, 25, 30, WINDOW, sprite(312), tiling = true)
            graphic(7, w - 25, h - 30, 25, 30, WINDOW, sprite(313), tiling = true)
            graphic(8, -15, 30, 36, h - 60, WINDOW, sprite(172), tiling = true)
            graphic(9, w - 36 + 15, 30, 36, h - 60, WINDOW, sprite(315), tiling = true)
            graphic(10, 25, -15, w - 50, 36, WINDOW, sprite(314), tiling = true)
            graphic(11, 25, h - 36 + 15, w - 50, 36, WINDOW, sprite(173), tiling = true)
            graphic(12, 5, 14, w - 10, 26, WINDOW, sprite(2546), tiling = true)
            graphic(CLOSE, w - 26 - 3, 6, 26, 23, WINDOW, sprite(535), ops = listOf("Close"))
        }

        /** OSRS v2 stone button: tradebacking plus the eight 9 px border pieces of [firstPiece] (0-3 corners, 4-7 edges). */
        fun stoneButton(firstId: Int, w: Int, h: Int, parent: Int, firstPiece: Int) {
            graphic(firstId, 1, 1, w - 2, h - 2, parent, sprite(297), tiling = true)
            graphic(firstId + 1, 0, 0, 9, 9, parent, sprite(firstPiece), tiling = true)
            graphic(firstId + 2, w - 9, 0, 9, 9, parent, sprite(firstPiece + 1), tiling = true)
            graphic(firstId + 3, 0, h - 9, 9, 9, parent, sprite(firstPiece + 2), tiling = true)
            graphic(firstId + 4, w - 9, h - 9, 9, 9, parent, sprite(firstPiece + 3), tiling = true)
            graphic(firstId + 5, 0, 9, 9, h - 18, parent, sprite(firstPiece + 4), tiling = true)
            graphic(firstId + 6, 9, 0, w - 18, 9, parent, sprite(firstPiece + 5), tiling = true)
            graphic(firstId + 7, w - 9, 9, 9, h - 18, parent, sprite(firstPiece + 6), tiling = true)
            graphic(firstId + 8, 9, h - 9, w - 18, 9, parent, sprite(firstPiece + 7), tiling = true)
        }

        /** OSRS `options_button_off` (clientscript 835): the nine 6 px pieces 1141-1149. */
        fun optionsButton(firstId: Int, w: Int, h: Int, parent: Int) {
            graphic(firstId, 0, 0, 6, 6, parent, sprite(1141))
            graphic(firstId + 1, 6, 0, w - 12, 6, parent, sprite(1142), tiling = true)
            graphic(firstId + 2, w - 6, 0, 6, 6, parent, sprite(1143))
            graphic(firstId + 3, 0, 6, 6, h - 12, parent, sprite(1144), tiling = true)
            graphic(firstId + 4, 6, 6, w - 12, h - 12, parent, sprite(1145), tiling = true)
            graphic(firstId + 5, w - 6, 6, 6, h - 12, parent, sprite(1146), tiling = true)
            graphic(firstId + 6, 0, h - 6, 6, 6, parent, sprite(1147))
            graphic(firstId + 7, 6, h - 6, w - 12, 6, parent, sprite(1148), tiling = true)
            graphic(firstId + 8, w - 6, h - 6, 6, 6, parent, sprite(1149))
        }

        fun scrollbarFor(id: Int, x: Int, y: Int, h: Int, parent: Int, interfaceId: Int, layer: Int) =
            layer(id, x, y, 16, h, parent, onLoad = listOf(SCROLLBAR_SCRIPT, EVENT_COM, (interfaceId shl 16) or layer))

        fun done(count: Int): List<Comp> {
            require(list.size == count) { "expected $count components, built ${list.size}" }
            require(list.map { it.id }.sorted() == (0 until count).toList()) { "component ids must be 0..${count - 1}" }
            return list.sortedBy { it.id }
        }
    }

    private val STONE = 913
    private val STONE_IN = 921
    private val STONE_OUT = 929

    fun office(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val o = Office
        val b = Builder(fonts)
        b.frame(o.WIDTH, o.HEIGHT, "Death's Office Item Retrieval")
        // OSRS 669:2-4: the list at (10, 40) 460x230, the items 440 wide with the scrollbar at its right.
        b.layer(o.CONTAINER, 10, 40, 460, 230, WINDOW)
        b.layer(o.ITEMS, 2, 2, 440, 226, o.CONTAINER)
        b.scrollbarFor(o.SCROLLBAR, 442, 2, 226, o.CONTAINER, OFFICE, o.ITEMS)
        // OSRS 669:5: the bottom row 460x36, 10 px above the window's bottom edge.
        b.layer(o.BOTTOM, 10, 274, 460, 36, WINDOW)
        // death_office_redraw: "Select an item to retrieve." in p12 across the row, or the item's fee in p11 left of the buttons.
        b.text(o.INFO_NONE, 0, 0, 424, 36, o.BOTTOM, fonts.p12, "Select an item to retrieve.")
        b.text(o.INFO_ITEM, 0, 0, 244, 36, o.BOTTOM, fonts.p11, "", hidden = true)
        // 669:6-10: 1 / 5 / X / All at x 244, 289, 334, 379 and Take-All at 424 (36x36, right-aligned offsets 180..0).
        listOf(o.BUTTON_1 to "1", o.BUTTON_5 to "5", o.BUTTON_X to "X", o.BUTTON_ALL to "All").forEachIndexed { i, (id, label) ->
            b.layer(id, 244 + 45 * i, 0, 36, 36, o.BOTTOM, hidden = true, ops = listOf(label))
            b.stoneButton(id + 1, 36, 36, id, STONE)
            b.text(id + 10, 0, 0, 36, 36, id, fonts.p11, label, colour = BUTTON_TEXT)
        }
        b.layer(o.BUTTON_TAKE_ALL, 424, 0, 36, 36, o.BOTTOM, ops = listOf("Take-All"))
        b.stoneButton(o.BUTTON_TAKE_ALL + 1, 36, 36, o.BUTTON_TAKE_ALL, STONE)
        // script3503: the bankbuttons,4 inventory icon, 29x22, centred, half transparent at rest.
        b.graphic(o.BUTTON_TAKE_ALL + 10, 3, 7, 29, 22, o.BUTTON_TAKE_ALL, sprite(1226), transparency = 50)
        for (slot in 0 until o.SLOTS) {
            b.item(o.SLOT_FIRST + slot, o.MARGIN + (slot % o.COLUMNS) * o.COLUMN_STEP, (slot / o.COLUMNS) * o.ROW_STEP, o.ITEMS, listOf("Select", "", "", "", "", "", "", "", "", "Examine"))
        }
        // The selected item is drawn with outline 2 (death_office_redraw); one marker component the server moves onto it.
        b.item(o.SELECTED, 0, 0, o.ITEMS, listOf("Select", "", "", "", "", "", "", "", "", "Examine"), outline = 2)
        return b.done(o.COUNT)
    }

    fun grave(fonts: LootKeyInterfaceImportTool.Fonts, incineratorModel: Int): List<Comp> {
        val g = Grave
        val b = Builder(fonts)
        b.frame(g.WIDTH, g.HEIGHT, "Gravestone <col=ffb83f>(0/120)</col>")
        // gravestone_generic_window_set at 341x296: free section (10, 40) 321x98, pay section (10, 137) 321x149.
        b.layer(g.FREE_CONTAINER, 10, 40, 321, 98, WINDOW)
        b.text(g.FREE_TEXT, 2, 2, 317, 12, g.FREE_CONTAINER, fonts.p11, "Free to reclaim:", alignX = 0)
        b.layer(g.FREE_ITEMS, 18, 14, 259, 82, g.FREE_CONTAINER)
        b.scrollbarFor(g.FREE_SCROLLBAR, 2, 14, 82, g.FREE_CONTAINER, GRAVE, g.FREE_ITEMS)
        b.layer(g.FREE_BUTTON, 278, 28, 41, 41, g.FREE_CONTAINER, ops = listOf("Take-All"))
        b.optionsButton(g.FREE_BUTTON + 1, 41, 41, g.FREE_BUTTON)
        b.graphic(g.FREE_BUTTON + 10, 6, 10, 29, 22, g.FREE_BUTTON, sprite(1226))
        b.layer(g.PAY_CONTAINER, 10, 137, 321, 149, WINDOW)
        b.layer(g.PAY_TOP, 2, 2, 317, 123, g.PAY_CONTAINER)
        b.text(g.FEE, 0, 0, 317, 12, g.PAY_TOP, fonts.p11, "Loading...", alignX = 0, alignY = 0)
        b.layer(g.PAY_ITEMS, 16, 12, 259, 111, g.PAY_TOP)
        b.scrollbarFor(g.PAY_SCROLLBAR, 0, 12, 111, g.PAY_TOP, GRAVE, g.PAY_ITEMS)
        // gravestone_generic_resynchdata: "Unlock" with the padlock while a fee is due, else "Take-All" with the inventory icon.
        b.layer(g.UNLOCK_BUTTON, 276, 0, 41, 41, g.PAY_TOP, ops = listOf("Unlock"))
        b.optionsButton(g.UNLOCK_BUTTON + 1, 41, 41, g.UNLOCK_BUTTON)
        b.graphic(g.UNLOCK_BUTTON + 10, 6, 9, 29, 22, g.UNLOCK_BUTTON, sprite(1342))
        b.layer(g.PAY_TAKE_ALL_BUTTON, 276, 0, 41, 41, g.PAY_TOP, hidden = true, ops = listOf("Take-All"))
        b.optionsButton(g.PAY_TAKE_ALL_BUTTON + 1, 41, 41, g.PAY_TAKE_ALL_BUTTON)
        b.graphic(g.PAY_TAKE_ALL_BUTTON + 10, 6, 9, 29, 22, g.PAY_TAKE_ALL_BUTTON, sprite(1226))
        // OSRS 672:17 the incinerator (model 9037 at zoom 4000, rotation 114/1396), bottom-right: drag items onto it.
        b.add(
            Comp(
                g.INCINERATOR, TYPE_MODEL, 276, 82, 41, 41, g.PAY_TOP, model = incineratorModel, originX = 0, originY = 150,
                xan = 114, yan = 0, zan = 1396, zoom = 4000,
            ),
        )
        b.text(g.INFO, 0, 125, 321, 22, g.PAY_CONTAINER, fonts.p11, "Loading...")
        for (slot in 0 until g.SLOTS) {
            val column = slot % g.COLUMNS
            val row = slot / g.COLUMNS
            b.item(g.FREE_SLOT_FIRST + slot, g.MARGIN + column * g.COLUMN_STEP, row * g.FREE_ROW_STEP, g.FREE_ITEMS, listOf("Take", "", "", "", "", "", "", "", "", "Examine"))
        }
        for (slot in 0 until g.SLOTS) {
            val column = slot % g.COLUMNS
            val row = slot / g.COLUMNS
            b.item(g.PAY_SLOT_FIRST + slot, g.MARGIN + column * g.COLUMN_STEP, row * g.PAY_ROW_STEP, g.PAY_ITEMS, listOf("Take", "", "", "", "", "", "", "", "", "Examine"))
        }
        for (slot in 0 until g.SLOTS) {
            val column = slot % g.COLUMNS
            val row = slot / g.COLUMNS
            b.text(g.PAY_FEE_FIRST + slot, g.MARGIN + column * g.COLUMN_STEP, row * g.PAY_ROW_STEP + 32, 36, 12, g.PAY_ITEMS, fonts.p11, "", hidden = true)
        }
        return b.done(g.COUNT)
    }

    fun coffer(fonts: LootKeyInterfaceImportTool.Fonts, coinsModel: Int, swirlModel: Int, swirlSeq: Int): List<Comp> {
        val c = Coffer
        val b = Builder(fonts)
        b.frame(c.WIDTH, c.HEIGHT, "Sacrifice items to Death's Coffer")
        // OSRS 670:2 contents (10, 40) 440x270; 670:3 left 180 wide; 670:5 right 255 wide at x 185.
        b.layer(c.CONTENTS, 10, 40, 440, 270, WINDOW)
        b.layer(c.LEFT, 0, 0, 180, 270, c.CONTENTS)
        b.text(
            c.LEFT_TEXT, 0, 0, 180, 250, c.LEFT, fonts.p12,
            "You may <col=ffffff>sacrifice</col> unwanted items here. Their value will go into <col=ffffff>Death's Coffer</col>, " +
                "a pot of money from which you can <col=ffffff>pay item reclamation fees</col> in future.<br><br>Items sacrificed " +
                "here <col=ffffff>can </col><col=ff0000>NEVER</col><col=ffffff> be retrieved</col>, and the money " +
                "<col=ff0000>CANNOT</col><col=ffffff> be withdrawn</col> or used for any other purposes.",
            alignX = 0, alignY = 0, lineHeight = 15,
        )
        b.add(Comp(c.COINS_MODEL, TYPE_MODEL, 55, 180, 70, 70, c.LEFT, model = coinsModel, originX = -20, originY = 50, xan = 512, yan = 0, zan = 0, zoom = 400))
        b.text(c.COFFER_TEXT, 0, 250, 180, 20, c.LEFT, fonts.p12, "Coffer: <col=ffffff>Empty</col>")
        b.layer(c.RIGHT, 185, 0, 255, 270, c.CONTENTS)
        b.graphic(20, 1, 1, 253, 268, c.RIGHT, sprite(897), tiling = true)
        b.rect(21, 0, 0, 255, 270, c.RIGHT, THINBOX_OUTER)
        b.rect(22, 1, 1, 253, 268, c.RIGHT, THINBOX_INNER)
        // 670:7 the swirl (model 38743, animation 7301, zoom 700) behind 670:8, the item on offer (64x64), both 30 px left of centre.
        b.add(Comp(c.SWIRL, TYPE_MODEL, -3, 35, 200, 200, c.RIGHT, model = swirlModel, originX = 0, originY = 125, xan = 0, yan = 0, zan = 1024, zoom = 700, animation = swirlSeq))
        b.add(Comp(c.DISPLAY, TYPE_GRAPHIC, 65, 103, 64, 64, c.RIGHT, graphic = -1, shadow = 0x333333))
        b.text(c.NAME, 0, 10, 255, 20, c.RIGHT, fonts.b12, "Select an item...")
        b.text(c.VALUE, 0, 30, 255, 15, c.RIGHT, fonts.p11, "---")
        // 670:9-12 the quantity buttons 32x32 at x 213, y 56 / 98 / 140 / 182; three looks each (death_coffer_drawbutton).
        listOf(c.BUTTON_1 to "1", c.BUTTON_5 to "5", c.BUTTON_X to "X", c.BUTTON_ALL to "All").forEachIndexed { i, (id, label) ->
            b.layer(id, 213, 56 + 42 * i, 32, 32, c.RIGHT, ops = listOf(label))
            val looks = listOf(Triple(c.LOOK_ACTIVE, STONE_OUT, WHITE), Triple(c.LOOK_SELECTED, STONE_IN, WHITE), Triple(c.LOOK_DISABLED, STONE_IN, GREY))
            looks.forEach { (offset, pieces, colour) ->
                val look = id + offset
                b.layer(look, 0, 0, 32, 32, id, hidden = offset != c.LOOK_DISABLED)
                b.stoneButton(look + 1, 32, 32, look, pieces)
                b.text(look + 10, 0, 0, 32, 32, look, fonts.p11, label, colour = colour)
            }
        }
        // 670:13 Confirm, 150x35 centred 10 px above the bottom: "Confirm:" + the coins, or a grey "---".
        b.layer(c.CONFIRM, 52, 225, 150, 35, c.RIGHT, ops = listOf("Confirm"))
        b.layer(c.CONFIRM_ACTIVE, 0, 0, 150, 35, c.CONFIRM, hidden = true)
        b.stoneButton(c.CONFIRM_ACTIVE + 1, 150, 35, c.CONFIRM_ACTIVE, STONE_OUT)
        b.text(c.CONFIRM_ACTIVE + 10, 0, 5, 150, 13, c.CONFIRM_ACTIVE, fonts.p12, "Confirm:", colour = WHITE, alignY = 0)
        b.text(c.CONFIRM_VALUE, 0, 17, 150, 13, c.CONFIRM_ACTIVE, fonts.p11, "", colour = WHITE, alignY = 2)
        b.layer(c.CONFIRM_DISABLED, 0, 0, 150, 35, c.CONFIRM)
        b.stoneButton(c.CONFIRM_DISABLED + 1, 150, 35, c.CONFIRM_DISABLED, STONE_IN)
        b.text(c.CONFIRM_DISABLED + 10, 0, 0, 150, 35, c.CONFIRM_DISABLED, fonts.p12, "---", colour = GREY)
        return b.done(c.COUNT)
    }

    fun cofferSide(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val s = CofferSide
        val b = Builder(fonts)
        // OSRS 671:0, 162x250 at (2, 3) of the side panel; clientscript 150 builds the inventory grid inside it.
        b.add(Comp(s.ROOT, TYPE_LAYER, 2, 3, 162, 250, -1))
        return b.done(s.COUNT)
    }

    fun kept(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val k = Kept
        val b = Builder(fonts)
        b.frame(k.WIDTH, k.HEIGHT, "Items Kept on Death")
        // OSRS 4:2 contents (10, 40) 480x272; 4:4 left 331 wide with the list 311 wide and its scrollbar; 4:12 right 150 wide.
        b.layer(k.CONTENTS, 10, 40, 480, 272, WINDOW)
        b.layer(k.LEFT, 0, 0, 331, 272, k.CONTENTS)
        b.layer(k.ITEMS, 2, 2, 311, 268, k.LEFT)
        b.scrollbarFor(k.SCROLLBAR, 313, 2, 268, k.LEFT, KEPT, k.ITEMS)
        b.layer(k.RIGHT, 330, 0, 150, 272, k.CONTENTS)
        b.text(k.SHOWING, 0, 2, 150, 28, k.RIGHT, fonts.p12, "Showing info for:")
        var y = 30
        for (i in 0 until 4) {
            val base = k.TOGGLE_FIRST + i * k.TOGGLE_STRIDE
            val h = k.TOGGLE_HEIGHTS[i]
            b.layer(base, 10, y, 130, h, k.RIGHT, ops = listOf("Toggle"))
            listOf(Triple(k.LOOK_ON, STONE_IN, 699), Triple(k.LOOK_OFF, STONE_OUT, 698)).forEach { (offset, pieces, radio) ->
                val look = base + offset
                b.layer(look, 0, 0, 130, h, base, hidden = offset == k.LOOK_ON)
                b.stoneButton(look + 1, 130, h, look, pieces)
                b.graphic(look + 10, 6, (h - 17) / 2, 17, 17, look, sprite(radio))
                b.text(look + 11, 25, 0, 100, h, look, fonts.p11, k.TOGGLE_TEXTS[i], lineHeight = 12)
            }
            y += h + 5
        }
        b.text(k.VALUE, 0, 242, 150, 28, k.RIGHT, fonts.p11, "Guide risk value:<br>0", lineHeight = 12)
        b.rect(121, 0, 0, 150, 272, k.RIGHT, THINBOX_OUTER)
        b.rect(122, 1, 1, 148, 270, k.RIGHT, THINBOX_INNER)
        for (i in 0 until k.HEADERS) {
            b.text(k.HEADER_FIRST + i, 1, 0, 309, 12, k.ITEMS, fonts.p11, "", alignX = 0, hidden = true)
        }
        // pest_rewards_divider: a dark and a light 1 px line between two sections.
        for (i in 0 until k.DIVIDERS) {
            b.rect(k.DIVIDER_FIRST + i * 2, 0, 0, 311, 1, k.ITEMS, THINBOX_OUTER, filled = true, hidden = true)
            b.rect(k.DIVIDER_FIRST + i * 2 + 1, 0, 1, 311, 1, k.ITEMS, THINBOX_INNER, filled = true, hidden = true)
        }
        for (slot in 0 until k.SLOTS) {
            b.item(k.SLOT_FIRST + slot, 0, 0, k.ITEMS, listOf("Check"), outline = 0)
        }
        return b.done(k.COUNT)
    }

    /** Local model and sequence ids recorded in the asset map by this tool and the npc tool. */
    private fun assetIds(): Map<String, Int> = OsrsFxImportTool.existingFx(File(OsrsItemImportTool.ASSET_MAP))

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val previous = args.firstOrNull { it.startsWith("--replace-from-journal=") }?.substringAfter('=')?.let(LootKeyInterfaceImportTool::journalIntendedSha1s) ?: emptyMap()
        val assets = assetIds()
        val swirlSeq = assets["seq:$OSRS_SWIRL_SEQ"] ?: error("OSRS sequence $OSRS_SWIRL_SEQ not imported yet: run the npc tool's deaths-office batch first")
        val source = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val mutations = mutableListOf<CacheMutation>()
        val records = mutableListOf<String>()
        val modelIds = HashMap<Int, Int>()
        val fonts: LootKeyInterfaceImportTool.Fonts
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            fonts = LootKeyInterfaceImportTool.fonts(library)
            require(library.index(INDEX_CLIENTSCRIPTS).archive(SCROLL_SCRIPT) == null || previous.isNotEmpty()) { "clientscript $SCROLL_SCRIPT already exists" }
            (OFFICE..KEPT).forEach { require(library.index(INDEX_INTERFACES).archive(it) == null || previous.isNotEmpty()) { "interface $it already exists" } }
            val census = ModelNamespaceCensusTool.census(OsrsItemImportTool.TARGETS[0], OsrsItemImportTool.TARGETS[1], File(OsrsItemImportTool.ASSET_MAP))
            check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
            val free = census.provenFreeHoles.filter { it in 1..0xFFFF }.sorted().iterator()
            val dropped = mutableListOf<String>()
            OSRS_MODELS.forEach { osrs ->
                val local = assets["iface_model:$osrs"] ?: free.next().also { records += "iface_model|$osrs|$it" }
                modelIds[osrs] = local
                mutations += CacheMutation(INDEX_MODELS, local, 0, OsrsModelConversion.convert(source, osrs, dropped), "death screens model osrs=$osrs -> $local")
            }
            dropped.distinct().forEach { println("DROPPED $it") }
            OSRS_SPRITES.forEachIndexed { index, osrsId ->
                val bytes = source.file(INDEX_SPRITES, osrsId, 0) ?: error("OSRS sprite $osrsId missing from ${OsrsItemImportTool.SOURCE_CACHE}")
                mutations += CacheMutation(INDEX_SPRITES, SPRITE_BASE + index, 0, bytes, "sprite osrs:$osrsId -> 667:${SPRITE_BASE + index}")
            }
        } finally {
            library.close()
            source.close()
        }
        mutations += CacheMutation(INDEX_CLIENTSCRIPTS, SCROLL_SCRIPT, 0, scrollScript(), "clientscript $SCROLL_SCRIPT (scroll height + scrollbar)")
        val screens =
            listOf(
                OFFICE to office(fonts),
                GRAVE to grave(fonts, modelIds.getValue(9037)),
                COFFER to coffer(fonts, modelIds.getValue(4620), modelIds.getValue(38743), swirlSeq),
                COFFER_SIDE to cofferSide(fonts),
                KEPT to kept(fonts),
            )
        screens.forEach { (interfaceId, comps) ->
            comps.forEach { c ->
                mutations +=
                    CacheMutation(
                        INDEX_INTERFACES, interfaceId, c.id, encode(c), "interface $interfaceId:${c.id} type=${c.type}",
                        expectedCurrentSha1 = previous["idx${INDEX_INTERFACES}_grp${interfaceId}_file${c.id}"],
                    )
            }
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        println("MODELS $modelIds SWIRL_SEQ $swirlSeq FONTS p11=${fonts.p11} p12=${fonts.p12} b12=${fonts.b12}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interfaces $OFFICE-$KEPT, sprites $SPRITE_BASE..${SPRITE_BASE + OSRS_SPRITES.size - 1}, clientscript $SCROLL_SCRIPT")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
        if (records.isNotEmpty()) {
            val assetMap = File(OsrsItemImportTool.ASSET_MAP)
            val block = StringBuilder()
            records.forEach { r ->
                val (kind, upstream, localId) = r.split('|')
                block.append("  - fx_kind: $kind\n    upstream_fx_id: $upstream\n    local_fx_id: $localId\n    status: IMPORTED_BY_DEATHS_OFFICE_INTERFACE_TOOL\n    transaction: ${transaction.id}\n")
            }
            val text = assetMap.readText()
            assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
            println("ASSET_MAP appended ${records.size} model entries")
        }
    }
}
