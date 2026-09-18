package gg.rsmod.game.tools.importer

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Builds the OSRS "Loot keys" chest interface (OSRS interface 742, owner reference
 * `C:\RSPS\foto\Lootkeys interface`) as a revision-667 if3 interface and imports every OSRS sprite it
 * draws, byte for byte, into both production caches through [CacheTransaction].
 *
 * Source (all read-only, pinned OpenRS2 2686 cache):
 *  - component geometry, fonts, colours, texts, ops and sprite ids: OSRS interface 742 decoded from
 *    index 3 (script-built parts - the stone frame from clientscript 703 and the five key tabs from
 *    8006 - were measured on the 1:1 owner screenshot, every sprite of which was located by exact
 *    pixel match against the OSRS sprite index);
 *  - sprites (index 8, one frame each): 297 background texture, 820-831 stone frame edges, corners,
 *    divider ends and close button, 1077-1080 key tabs, 170 square button, 1226 inventory icon,
 *    1227 bank icon, 1235 destroy icon, 1229-1234 Item/Note button caps (grey / red), 653 dialog
 *    button. The OSRS sprite container format is the same trailer format this client's
 *    `IndexedImage.load` decodes, so the group bytes are copied unchanged into new 667 sprite ids
 *    [SPRITE_BASE]..
 *
 * The interface is entirely static: the server fills the 28 loot slots and the five key tabs with
 * IF_SETOBJECT, switches the tab / Item / Note sprites with IF_SETGRAPHIC, toggles the empty text
 * and the destroy dialog with IF_SETHIDE, and enables the ops with IF_SETEVENTS - so nothing here
 * depends on a clientscript the 667 cache does not have.
 *
 * Usage: `./gradlew :game:runLootKeyInterfaceImportTool --args="[--apply]"`
 */
object LootKeyInterfaceImportTool {
    const val SOURCE_CACHE = OsrsItemImportTool.SOURCE_CACHE
    val TARGETS = OsrsItemImportTool.TARGETS

    const val INDEX_INTERFACES = 3
    const val INDEX_SPRITES = 8

    /** New 667 interface id: the first id above the cache's last interface (1148). */
    const val INTERFACE_ID = 1149

    /** First new 667 sprite id: the first id above the cache's last sprite group (7911). */
    const val SPRITE_BASE = 7912

    /** OSRS sprite ids in import order; 667 id = [SPRITE_BASE] + index. */
    val OSRS_SPRITES =
        intArrayOf(
            297, // 0 background texture 88x60
            820, 821, 822, 823, // 1-4 top, left, bottom, right edge (32x32 canvas)
            824, 825, 826, 827, // 5-8 corners TL, TR, BL, BR
            828, 829, 830, // 9-11 divider middle (tiling), left end, right end
            831, // 12 close button 16x16
            1077, 1078, 1079, 1080, // 13-16 key tabs: pressed, hover, selected, empty/unselected
            170, // 17 square button background 36x36
            1226, 1227, 1235, // 18-20 inventory icon, bank icon, destroy icon
            1229, 1230, 1231, // 21-23 grey button left cap, middle (tiling), right cap
            1232, 1233, 1234, // 24-26 red button left cap, middle, right cap
            653, // 27 dialog button 68x44
        )

    fun sprite(osrsId: Int): Int {
        val index = OSRS_SPRITES.indexOf(osrsId)
        require(index >= 0) { "OSRS sprite $osrsId is not part of the import" }
        return SPRITE_BASE + index
    }

    // ---- component ids (fixed: the server addresses them) ----

    const val ROOT = 0
    const val WINDOW = 1
    const val TITLE = 12
    const val CLOSE = 13
    const val TAB_LINE = 20
    const val TAB_FIRST = 21 // 5 tabs x 3 components (background, key item, value text) = 21..35
    const val TAB_STRIDE = 3
    const val EMPTY_LAYER = 36
    const val EMPTY_TEXT = 37
    const val SLOT_FIRST = 38 // 28 loot slots = 38..65
    const val SLOT_COUNT = 28
    const val DESTROY_LAYER = 66
    const val DESTROY_BUTTON = 67
    const val DESTROY_ICON = 68
    const val WITHDRAW_AS_TEXT = 70
    const val ITEM_LAYER = 71
    const val ITEM_LEFT = 72
    const val ITEM_MIDDLE = 73
    const val ITEM_RIGHT = 74
    const val ITEM_TEXT = 75
    const val NOTE_LAYER = 76
    const val NOTE_LEFT = 77
    const val NOTE_MIDDLE = 78
    const val NOTE_RIGHT = 79
    const val NOTE_TEXT = 80
    const val INVENTORY_LAYER = 81
    const val INVENTORY_BUTTON = 82
    const val BANK_USED_TEXT = 84
    const val BANK_SIZE_TEXT = 86
    const val BANK_LAYER = 87
    const val BANK_BUTTON = 88
    const val CONFIRM_LAYER = 90
    const val CONFIRM_BUTTON = 95
    const val CANCEL_BUTTON = 96
    const val COMPONENT_COUNT = 100

    /**
     * Owner 2026-09-18 (#1): loot slot items are drawn 5% larger than the 32px item sprite ("just
     * 5%, no more"): 32 * 1.05 = 33.6 -> 34px boxes, centred on the old 32px box (the client scales
     * a graphic component's item sprite to the component box, InterfaceManager TYPE_GRAPHIC).
     */
    const val SLOT_ITEM_SIZE = 34

    const val TAB_COUNT = 5
    const val GRID_COLUMNS = 7
    const val GRID_ROWS = 4

    /** RuneScape interface orange. */
    const val ORANGE = 0xFF981F
    const val WHITE = 0xFFFFFF
    const val RED = 0xFF0000

    /** The divider/line colour of OSRS 742 (3025699). */
    const val LINE = 0x2E2B23

    private const val TYPE_LAYER = 0
    private const val TYPE_RECTANGLE = 3
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5
    private const val TYPE_LINE = 9
    const val TYPE_MODEL = 6

    class Component(
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
        val noClickThrough: Boolean = false,
        val graphic: Int = -1,
        val tiling: Boolean = false,
        val transparency: Int = 0,
        val font: Int = -1,
        val text: String = "",
        val lineHeight: Int = 0,
        val alignX: Int = 0,
        val alignY: Int = 0,
        val shadow: Boolean = false,
        val colour: Int = 0,
        val filled: Boolean = false,
        val lineWidth: Int = 1,
        val ops: List<String> = emptyList(),
        val opBase: String = "",
        /** TYPE_MODEL (6) only: a raw model shown ortho-2d like an item (Component.decode `ortho2d` branch). */
        val model: Int = -1,
        val modelOriginX: Int = 0,
        val modelOriginY: Int = 0,
        val xan: Int = 0,
        val yan: Int = 0,
        val zan: Int = 0,
        val zoom: Int = 0,
        val aspectX: Int = 0,
        val aspectY: Int = 0,
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

    /** Encodes one component the way this revision's `Component.decode` reads it (version byte 255 = legacy if3, like every 667 cache component). */
    fun encode(c: Component): ByteArray {
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
                o.p2(0) // scrollWidth
                o.p2(0) // scrollHeight
                o.p1(if (c.noClickThrough) 1 else 0)
            }
            TYPE_GRAPHIC -> {
                o.p4(c.graphic)
                o.p2(0) // angle2d
                o.p1(if (c.tiling) 1 else 0) // bit 1 alpha off
                o.p1(c.transparency)
                o.p1(0) // outline
                o.p4(0) // graphicShadow
                o.p1(0) // horizontalFlip
                o.p1(0) // verticalFlip
                o.p4(0) // colour
            }
            TYPE_TEXT -> {
                o.p2(if (c.font < 0) 65535 else c.font)
                o.pjstr(c.text)
                o.p1(c.lineHeight)
                o.p1(c.alignX)
                o.p1(c.alignY)
                o.p1(if (c.shadow) 1 else 0)
                o.p4(c.colour)
                o.p1(c.transparency)
            }
            TYPE_RECTANGLE -> {
                o.p4(c.colour)
                o.p1(if (c.filled) 1 else 0)
                o.p1(c.transparency)
            }
            TYPE_LINE -> {
                o.p1(c.lineWidth)
                o.p4(c.colour)
                o.p1(0) // lineDirection
            }
            TYPE_MODEL -> {
                o.p2(if (c.model < 0) 65535 else c.model)
                o.p1(1) // model flags: ortho2d
                o.p2(c.modelOriginX)
                o.p2(c.modelOriginY)
                o.p2(c.xan)
                o.p2(c.yan)
                o.p2(c.zan)
                o.p2(c.zoom)
                o.p2(65535) // no animation
                if (c.resizeX != 0) o.p2(c.aspectX)
                if (c.resizeY != 0) o.p2(c.aspectY)
            }
            else -> error("unsupported type ${c.type}")
        }
        o.p3(0) // events: the server enables ops with IF_SETEVENTS
        o.p1(0) // no op keys
        o.pjstr(c.opBase)
        o.p1(c.ops.size) // op count, no cursors
        c.ops.forEach { o.pjstr(it) }
        o.pjstr("") // pauseText
        o.p1(0) // dragDeadZone
        o.p1(0) // dragDeadTime
        o.p1(0) // dragRenderBehaviour
        o.pjstr("") // targetVerb
        repeat(20) { o.p1(0) } // hooks (onLoad .. onVarcstrTransmit; no onOpT for version < 0)
        repeat(5) { o.p1(0) } // transmit lists
        return o.bytes.toByteArray()
    }

    /**
     * The component tree. Coordinates are absolute inside the 308x320 window (OSRS 742 child 1),
     * measured from the reference screenshot at 1:1 (frame) or copied from the OSRS definitions.
     * Sprite canvases are the OSRS ones (edges are 32x32 canvases with the art 13px in), so the
     * component boxes below include the canvas offsets and clip the tiling to one row/column.
     */
    fun components(fonts: Fonts): List<Component> {
        val list = mutableListOf<Component>()
        fun add(c: Component) {
            require(list.none { it.id == c.id }) { "duplicate component ${c.id}" }
            list += c
        }
        fun layer(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, hidden: Boolean = false, reposX: Int = 0, reposY: Int = 0, resizeX: Int = 0, resizeY: Int = 0, noClickThrough: Boolean = false, ops: List<String> = emptyList(), opBase: String = "") =
            add(Component(id, TYPE_LAYER, x, y, w, h, parent, hidden = hidden, reposX = reposX, reposY = reposY, resizeX = resizeX, resizeY = resizeY, noClickThrough = noClickThrough, ops = ops, opBase = opBase))
        fun graphic(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, sprite: Int, tiling: Boolean = false, ops: List<String> = emptyList(), opBase: String = "", hidden: Boolean = false) =
            add(Component(id, TYPE_GRAPHIC, x, y, w, h, parent, graphic = sprite, tiling = tiling, ops = ops, opBase = opBase, hidden = hidden))
        fun item(id: Int, x: Int, y: Int, parent: Int, ops: List<String> = emptyList(), size: Int = 32) =
            add(Component(id, TYPE_GRAPHIC, x, y, size, size, parent, graphic = -1, ops = ops))
        fun text(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, font: Int, text: String, colour: Int, alignX: Int = 1, alignY: Int = 1, lineHeight: Int = 0) =
            add(Component(id, TYPE_TEXT, x, y, w, h, parent, font = font, text = text, colour = colour, alignX = alignX, alignY = alignY, shadow = true, lineHeight = lineHeight))
        fun line(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, colour: Int) =
            add(Component(id, TYPE_LINE, x, y, w, h, parent, colour = colour))

        // Root fills the main-screen area; the window is centred in it (OSRS 742: 512x334 root, 308x320 window).
        layer(ROOT, 0, 0, 0, 0, parent = -1, resizeX = 1, resizeY = 1)
        layer(WINDOW, 0, 0, 308, 320, parent = ROOT, reposX = 1, reposY = 1)

        // Stone frame (clientscript 703): background texture, four edges, four corners.
        graphic(2, 1, 1, 306, 318, WINDOW, sprite(297), tiling = true)
        graphic(3, 32, -13, 244, 20, WINDOW, sprite(820), tiling = true) // top edge, art rows 13..19
        graphic(4, 32, 300, 244, 20, WINDOW, sprite(822), tiling = true) // bottom edge at 313..319
        graphic(5, -13, 32, 20, 256, WINDOW, sprite(821), tiling = true) // left edge, art cols 13..19
        graphic(6, 288, 32, 20, 256, WINDOW, sprite(823), tiling = true) // right edge at 301..307
        graphic(7, 0, 0, 32, 32, WINDOW, sprite(824))
        graphic(8, 276, 0, 32, 32, WINDOW, sprite(825))
        graphic(9, 0, 288, 32, 32, WINDOW, sprite(826))
        graphic(10, 276, 288, 32, 32, WINDOW, sprite(827))
        // Title divider: middle art at rows 14..19 of its canvas -> y 29..34; ends at 26..37.
        graphic(11, 11, 15, 286, 20, WINDOW, sprite(828), tiling = true)
        text(TITLE, 0, 5, 308, 20, WINDOW, fonts.b12, "Loot keys", ORANGE)
        graphic(CLOSE, 282, 10, 16, 16, WINDOW, sprite(831), ops = listOf("Close"))
        graphic(14, 0, 17, 32, 32, WINDOW, sprite(829))
        graphic(15, 297, 17, 11, 32, WINDOW, sprite(830))
        // Lower divider (OSRS 742 children 8-10).
        graphic(16, 11, 249, 286, 20, WINDOW, sprite(828), tiling = true)
        graphic(17, 0, 251, 32, 32, WINDOW, sprite(829))
        graphic(18, 297, 251, 11, 32, WINDOW, sprite(830))
        // Line under the tabs (child 3) and the two vertical lines beside the bottom buttons (14, 29).
        line(19, 8, 84, 292, 0, WINDOW, LINE)
        line(TAB_LINE, 52, 269, 0, 43, WINDOW, LINE)

        // Five key tabs (clientscript 8006): 41x40 at x = 12 + 40 * i, y = 45; key item and value under it.
        for (i in 0 until TAB_COUNT) {
            val x = 12 + 40 * i
            val base = TAB_FIRST + i * TAB_STRIDE
            graphic(base, x, 45, 41, 40, WINDOW, sprite(1080), ops = listOf("View"), opBase = "Loot key ${i + 1}")
            item(base + 1, x + 4, 46, WINDOW)
            text(base + 2, x, 68, 41, 14, WINDOW, fonts.p11, "", WHITE)
        }

        // "This chest is now empty." (children 6-7), hidden until the viewed key holds nothing.
        layer(EMPTY_LAYER, 19, 93, 270, 152, WINDOW, hidden = true)
        text(EMPTY_TEXT, 0, 0, 270, 152, EMPTY_LAYER, fonts.p12, "This chest is now empty.", ORANGE)

        // 28 loot slots, 7 x 4, 39 x 38 pitch inside the 270x152 grid area at (19, 93).
        val slotOps = listOf("Withdraw-1", "Withdraw-5", "Withdraw-10", "Withdraw-All", "Withdraw-X", "", "", "", "", "Examine")
        val slotInset = (SLOT_ITEM_SIZE - 32) / 2 // keep the 5%-larger box centred on the 32px cell
        for (slot in 0 until SLOT_COUNT) {
            val column = slot % GRID_COLUMNS
            val row = slot / GRID_COLUMNS
            item(SLOT_FIRST + slot, 20 + 39 * column - slotInset, 91 + 38 * row - slotInset, WINDOW, ops = slotOps, size = SLOT_ITEM_SIZE)
        }

        // Bottom bar: destroy button (children 11-13).
        layer(DESTROY_LAYER, 11, 273, 36, 36, WINDOW)
        graphic(DESTROY_BUTTON, 0, 0, 36, 36, DESTROY_LAYER, sprite(170), ops = listOf("Destroy"))
        graphic(DESTROY_ICON, 3, 7, 29, 22, DESTROY_LAYER, sprite(1235))
        line(69, 183, 269, 0, 43, WINDOW, LINE)
        text(WITHDRAW_AS_TEXT, 56, 272, 124, 16, WINDOW, fonts.p12, "Withdraw as:", ORANGE)
        // Item / Note buttons (children 16-25): caps + tiling middle, red set = selected.
        layer(ITEM_LAYER, 56, 288, 62, 22, WINDOW, ops = listOf("Item"))
        graphic(ITEM_LEFT, 0, 0, 6, 22, ITEM_LAYER, sprite(1232))
        graphic(ITEM_MIDDLE, 6, 0, 50, 22, ITEM_LAYER, sprite(1233), tiling = true)
        graphic(ITEM_RIGHT, 56, 0, 6, 22, ITEM_LAYER, sprite(1234))
        text(ITEM_TEXT, 0, 0, 62, 22, ITEM_LAYER, fonts.p12, "Item", ORANGE)
        layer(NOTE_LAYER, 118, 288, 62, 22, WINDOW, ops = listOf("Note"))
        graphic(NOTE_LEFT, 0, 0, 6, 22, NOTE_LAYER, sprite(1229))
        graphic(NOTE_MIDDLE, 6, 0, 50, 22, NOTE_LAYER, sprite(1230), tiling = true)
        graphic(NOTE_RIGHT, 56, 0, 6, 22, NOTE_LAYER, sprite(1231))
        text(NOTE_TEXT, 0, 0, 62, 22, NOTE_LAYER, fonts.p12, "Note", ORANGE)
        // Withdraw all to inventory (children 26-28).
        layer(INVENTORY_LAYER, 261, 273, 36, 36, WINDOW)
        graphic(INVENTORY_BUTTON, 0, 0, 36, 36, INVENTORY_LAYER, sprite(170), ops = listOf("Withdraw all to"), opBase = "inventory")
        graphic(83, 2, 7, 29, 22, INVENTORY_LAYER, sprite(1226))
        // Bank space used / size (children 30-32).
        text(BANK_USED_TEXT, 187, 279, 32, 24, WINDOW, fonts.p11, "", ORANGE, alignY = 0)
        line(85, 196, 290, 12, 0, WINDOW, ORANGE)
        text(BANK_SIZE_TEXT, 187, 279, 32, 24, WINDOW, fonts.p11, "", ORANGE, alignY = 2)
        // Withdraw all to bank (children 33-35).
        layer(BANK_LAYER, 220, 273, 36, 36, WINDOW)
        graphic(BANK_BUTTON, 0, 0, 36, 36, BANK_LAYER, sprite(170), ops = listOf("Withdraw all to"), opBase = "bank")
        graphic(89, 3, 7, 29, 22, BANK_LAYER, sprite(1227))

        // Destroy confirmation. Owner 2026-09-18 (#3, "gui is not nice"): instead of OSRS 742's bare
        // dimmed window with a huge red quill-font question, a RuneScape-style stone panel floats
        // over the dimmed window - stone texture (297) with a dark outline, a bold orange title,
        // a white explanation and the two 653 buttons at their native 68x44 with p12 labels.
        layer(CONFIRM_LAYER, 0, 0, 308, 320, WINDOW, hidden = true, noClickThrough = true)
        add(Component(91, TYPE_RECTANGLE, 0, 0, 308, 320, CONFIRM_LAYER, colour = 0, filled = true, transparency = 150))
        val panelX = 44
        val panelY = 98
        val panelW = 220
        val panelH = 124
        graphic(92, panelX, panelY, panelW, panelH, CONFIRM_LAYER, sprite(297), tiling = true)
        add(Component(93, TYPE_RECTANGLE, panelX, panelY, panelW, panelH, CONFIRM_LAYER, colour = LINE, filled = false))
        text(94, panelX, panelY + 8, panelW, 18, CONFIRM_LAYER, fonts.b12, "Destroy the loot in this key?", ORANGE)
        graphic(CONFIRM_BUTTON, panelX + 26, panelY + 70, 68, 44, CONFIRM_LAYER, sprite(653), ops = listOf("Confirm Destroy"))
        graphic(CANCEL_BUTTON, panelX + panelW - 26 - 68, panelY + 70, 68, 44, CONFIRM_LAYER, sprite(653), ops = listOf("Cancel"))
        text(97, panelX + 26, panelY + 70, 68, 44, CONFIRM_LAYER, fonts.p12, "Destroy", ORANGE)
        text(98, panelX + panelW - 26 - 68, panelY + 70, 68, 44, CONFIRM_LAYER, fonts.p12, "Cancel", ORANGE)
        text(99, panelX + 10, panelY + 30, panelW - 20, 36, CONFIRM_LAYER, fonts.p12, "Everything in this key will be lost.<br>This cannot be undone.", WHITE, lineHeight = 14)

        require(list.size == COMPONENT_COUNT) { "expected $COMPONENT_COUNT components, built ${list.size}" }
        require(list.map { it.id }.sorted() == (0 until COMPONENT_COUNT).toList()) { "component ids must be 0..${COMPONENT_COUNT - 1}" }
        return list.sortedBy { it.id }
    }

    /** The 667 sprite ids of the four fonts, resolved by name from the target cache. */
    class Fonts(val p11: Int, val p12: Int, val b12: Int, val q8: Int)

    fun fonts(library: com.displee.cache.CacheLibrary): Fonts {
        val index = library.index(INDEX_SPRITES)
        fun id(name: String) = index.archive(name)?.id ?: error("font sprite '$name' not found in target cache")
        return Fonts(p11 = id("p11_full"), p12 = id("p12_full"), b12 = id("b12_full"), q8 = id("q8_full"))
    }

    /**
     * `--replace-from-journal=<tx id>`: the sha1 this tool's earlier transaction recorded as
     * `intended_sha1` per interface file (its `plan.txt` under `C:\RSPS\import-journal`). Pinning
     * those as [CacheMutation.expectedCurrentSha1] lets a re-run replace exactly the components this
     * tool wrote before (a layout change such as the owner's 5%-larger slots or the new destroy
     * dialog) while any other content in those files still blocks as a CONFLICT.
     */
    fun journalIntendedSha1s(transactionId: String): Map<String, String> {
        val plan = File("C:/RSPS/import-journal/$transactionId/plan.txt")
        require(plan.isFile) { "journal plan not found: $plan" }
        val result = HashMap<String, String>()
        plan.readLines().filter { it.startsWith("plan\t") }.forEach { line ->
            val fields = line.split('\t').drop(1).associate { it.substringBefore('=') to it.substringAfter('=') }
            val location = fields["location"] ?: return@forEach
            val intended = fields["intended_sha1"] ?: return@forEach
            result.putIfAbsent(location, intended)
        }
        return result
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val previous = args.firstOrNull { it.startsWith("--replace-from-journal=") }?.substringAfter('=')?.let(::journalIntendedSha1s) ?: emptyMap()
        val source = ModernCacheReader(File(SOURCE_CACHE))
        val mutations = mutableListOf<CacheMutation>()
        source.use {
            OSRS_SPRITES.forEachIndexed { index, osrsId ->
                val bytes = source.file(INDEX_SPRITES, osrsId, 0) ?: error("OSRS sprite $osrsId missing from $SOURCE_CACHE")
                mutations += CacheMutation(INDEX_SPRITES, SPRITE_BASE + index, 0, bytes, "sprite osrs:$osrsId -> 667:${SPRITE_BASE + index}")
            }
        }
        val fonts =
            com.displee.cache.CacheLibrary(TARGETS[0]).let { library ->
                try {
                    fonts(library)
                } finally {
                    library.close()
                }
            }
        println("FONTS p11=${fonts.p11} p12=${fonts.p12} b12=${fonts.b12} q8=${fonts.q8}")
        components(fonts).forEach { c ->
            mutations +=
                CacheMutation(
                    INDEX_INTERFACES,
                    INTERFACE_ID,
                    c.id,
                    encode(c),
                    "interface $INTERFACE_ID:${c.id} type=${c.type}",
                    expectedCurrentSha1 = previous["idx${INDEX_INTERFACES}_grp${INTERFACE_ID}_file${c.id}"],
                )
        }

        val transaction = CacheTransaction(targets = TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interface $INTERFACE_ID and sprites $SPRITE_BASE..${SPRITE_BASE + OSRS_SPRITES.size - 1}")
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
