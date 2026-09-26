package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Builder
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Comp
import java.io.File

/**
 * The quest tab, part 1 (owner mission 2026-09-26, references `C:\RSPS\foto\tab.png` and `tab1.png`): the OSRS journal side
 * panel as revision-667 interfaces, every sprite imported byte for byte from the pinned OSRS cache (openrs2 2686).
 *
 * Sources (all decoded from that cache, 2026-09-26):
 *  - OSRS interface 629 `side_journal`: the tab row (24 high), per tab a left piece, a centred middle piece and the left
 *    piece mirrored on the right (TabsShort 2277/2278 selected, 2279/2280 not selected, 20x21, bottom aligned) with an 18x18
 *    icon one pixel above the bottom; the content box at (3, 24), 184 x 233. tab.png shows four tabs of 190 / 4 each.
 *  - Tab icons (AchievementDiaryIcons): 2276 character summary (tab.png's first tab), 835 quests (blue), 836 diaries (green),
 *    3611 the Deadman skull. Gameframe icon: SideIcons 4815, the large skull of tab1.png (33x36 canvas).
 *  - OSRS interface 846 `deadman_sp` (tab.png): title (3, 0, w-6, 20) font b12 0xFF981F; border layer at y 20 drawn by
 *    clientscript 714/715 (rectangle 0x0E0E0C, inner rectangle 0x474745); content 14 x 7 smaller, centred. Its boxes are
 *    clientscript 5288 with struct 3712: fill rectangle 0x726451 inset 1, texture 297 inset 4, rounded 4 px edges and
 *    corners 3524-3531. Its buttons are clientscript 5845: texture 297 and the v2 stone button pieces 913-920 (9 px), text
 *    font p11 0xDFDFDF. Titles p12 0xFF981F, values b12 white.
 *
 * Interfaces (server fills every text; nothing here decides a value):
 *  - [CONTAINER] replaces interface 190 in the quest tab slot: the tab row and two mounts - [BOX] for the Deadman, Deadman
 *    Groups and Achievement Diaries panels, [QUEST_MOUNT] for the 667 quest list 190 at full width under the tab row;
 *    [QUEST_LIST_FIT] makes 190's four fixed-height parts 24 px shorter so it fits there without a second scrollbar.
 *  - [DEADMAN] five blocks: title and Info button on the first line, the value on the second (owner 2026-09-26).
 *  - [GROUPS] the Deadman groups, laid out by the server inside a scroll layer.
 *  - [DIARIES] "Coming soon".
 *  - [INFO] the one info window (title, text, close) opened by every Info button.
 *  - Gameframe: 548:141 (fixed) and 746:26 (resizable / fullscreen), the quest tab icon, point at the imported skull and get
 *    its 33x36 size, centred on the tab button (548:132 at 96,0 30x37; 746:42 at 360,0 from the bottom right).
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.QuestTabInterfaceImportTool [--apply]` - both caches, one
 * transaction; a re-run replaces only its own sprites and components (pinned to what is there).
 */
object QuestTabInterfaceImportTool {
    const val CONTAINER = 1171
    const val DEADMAN = 1172
    const val GROUPS = 1173
    const val DIARIES = 1174
    const val INFO = 1175

    /** First free sprite id (probe 2026-09-26: highest 8320 in both caches). */
    const val SPRITE_BASE = 8321

    val OSRS_SPRITES =
        intArrayOf(
            4815, // 0 gameframe quest tab icon (SideIcons, the large Deadman skull)
            3611, // 1 tab icon: Deadman skull
            2276, // 2 tab icon: character summary (used for Deadman Groups)
            835, // 3 tab icon: quests (blue)
            836, // 4 tab icon: achievement diaries (green)
            2277, 2278, // 5-6 tab selected: side piece, middle piece
            2279, 2280, // 7-8 tab not selected: side piece, middle piece
            3524, 3525, 3526, 3527, // 9-12 box corners TL, TR, BL, BR (struct 3712 params 1387, 1389, 1393, 1395)
            3528, 3529, 3530, 3531, // 13-16 box edges top, bottom, left, right (params 1388, 1394, 1390, 1392)
        )

    fun sprite(osrsId: Int): Int {
        val index = OSRS_SPRITES.indexOf(osrsId)
        require(index >= 0) { "OSRS sprite $osrsId is not part of the quest tab import" }
        return SPRITE_BASE + index
    }

    val GAMEFRAME_ICON get() = sprite(4815)
    val TAB_SIDE_SELECTED get() = sprite(2277)
    val TAB_MIDDLE_SELECTED get() = sprite(2278)
    val TAB_SIDE_NORMAL get() = sprite(2279)
    val TAB_MIDDLE_NORMAL get() = sprite(2280)

    /** OSRS sprites other imports already brought in (reused, never duplicated). */
    private fun texture() = LootKeyInterfaceImportTool.sprite(297)

    private fun stone(piece: Int) = DeathsOfficeInterfaceImportTool.sprite(piece)

    const val ORANGE = 0xFF981F
    const val WHITE = 0xFFFFFF
    const val BUTTON_TEXT = 0xDFDFDF
    const val BORDER_OUTER = 0x0E0E0C
    const val BORDER_INNER = 0x474745
    const val BOX_FILL = 0x726451

    private const val TYPE_LAYER = 0
    private const val TYPE_RECTANGLE = 3
    private const val TYPE_GRAPHIC = 5

    // ------------------------------------------------------------------ gameframe icon

    class IconPatch(val interfaceId: Int, val component: Int, val x: Int, val y: Int)

    /** Fixed: centred on 548:132 (96..126, 0..37); resizable: on 746:42 (right 360, bottom 0, 30x37), right/bottom aligned. */
    val ICON_PATCHES = listOf(IconPatch(548, 141, 94, 0), IconPatch(746, 26, 359, 1))
    const val ICON_WIDTH = 33
    const val ICON_HEIGHT = 36

    /** Rewrites x, y, width, height and the graphic of one legacy type-5 component, keeping every other byte. */
    fun patchIcon(bytes: ByteArray, patch: IconPatch): ByteArray {
        require(bytes[0].toInt() and 0xFF == 255) { "${patch.interfaceId}:${patch.component} is not a legacy if3 component" }
        require(bytes[1].toInt() == TYPE_GRAPHIC) { "${patch.interfaceId}:${patch.component} is not a plain graphic (type ${bytes[1]})" }
        val out = bytes.copyOf()
        fun p2(at: Int, v: Int) {
            out[at] = (v shr 8).toByte()
            out[at + 1] = v.toByte()
        }
        // 255, type, clientcode(2), x(2) @4, y(2) @6, w(2) @8, h(2) @10, 4 modes, parent(2), flags @18, graphic(4) @19
        p2(4, patch.x)
        p2(6, patch.y)
        p2(8, ICON_WIDTH)
        p2(10, ICON_HEIGHT)
        val g = GAMEFRAME_ICON
        out[19] = (g shr 24).toByte()
        out[20] = (g shr 16).toByte()
        out[21] = (g shr 8).toByte()
        out[22] = g.toByte()
        return out
    }

    // ------------------------------------------------------------------ container (replaces 190 in the quest tab slot)

    enum class Tab(val tooltip: String, val osrsIcon: Int) {
        GROUPS("Deadman Groups", 2276),
        QUEST_LIST("Quest List", 835),
        DIARIES("Achievement Diaries", 836),
        DEADMAN("Deadman", 3611),
    }

    const val PANE_WIDTH = 190
    const val PANE_HEIGHT = 261
    const val TAB_HEIGHT = 24

    const val ROOT = 0
    const val TAB_FIRST = 1
    const val TAB_STRIDE = 5 // layer, left, middle, right, icon
    const val TAB_LEFT = 1
    const val TAB_MIDDLE = 2
    const val TAB_RIGHT = 3
    const val TAB_ICON = 4
    const val BOX = TAB_FIRST + 4 * TAB_STRIDE // 21
    const val QUEST_SCROLL = BOX + 1 // 22
    const val QUEST_MOUNT = QUEST_SCROLL + 1 // 23
    const val QUEST_SCROLLBAR = QUEST_MOUNT + 1 // 24
    const val CONTAINER_COUNT = QUEST_SCROLLBAR + 1

    /** 190 is 261 high; it scrolls under the tab row. */
    /** 190 is mounted in the space under the tab row; [QUEST_LIST_FIT] makes its fixed-height parts fit it. */
    const val QUEST_LIST_HEIGHT = PANE_HEIGHT - TAB_HEIGHT

    /** One 2-byte field of a legacy (if3, first byte 255) component: y at 6, height at 10. [earlier]: a value an earlier run wrote. */
    class FieldPatch(val interfaceId: Int, val component: Int, val offset: Int, val old: Int, val new: Int, val earlier: Int? = null)

    /**
     * Quest list 190 was 261 high; under the 24 px tab row it has 237. Everything in it is sized to its parent or placed
     * from the top, except these four fixed values, which move 24 px (decoded 2026-09-26 from both caches): the quest list
     * layer 13 (bottom aligned, 12 from the bottom) 173 -> 149 high, so its top stays at y 76 under the filters; the
     * background rectangle 7 224 -> 200 high; the bottom edge graphic 6 24 px up; the bottom text 2 (14 high, which already
     * stuck 4 px out of the 261) to the last 14 px of the area.
     */
    val QUEST_LIST_FIT =
        listOf(
            FieldPatch(190, 13, 10, 173, 149),
            FieldPatch(190, 7, 10, 224, 200),
            FieldPatch(190, 6, 6, 236, 212),
            FieldPatch(190, 2, 6, 251, 223, earlier = 227),
        )

    fun patchField(bytes: ByteArray, patch: FieldPatch): ByteArray {
        require(bytes[0].toInt() and 0xFF == 255) { "${patch.interfaceId}:${patch.component} is not a legacy if3 component" }
        val have = ((bytes[patch.offset].toInt() and 0xFF) shl 8) or (bytes[patch.offset + 1].toInt() and 0xFF)
        if (have == patch.new) return bytes
        require(have == patch.old || have == patch.earlier) { "${patch.interfaceId}:${patch.component} @${patch.offset} is $have, expected ${patch.old}" }
        val out = bytes.copyOf()
        out[patch.offset] = (patch.new shr 8).toByte()
        out[patch.offset + 1] = patch.new.toByte()
        return out
    }
    const val QUEST_VIEW_WIDTH = PANE_WIDTH - 16

    fun tabBase(tab: Int) = TAB_FIRST + tab * TAB_STRIDE

    fun tabX(tab: Int) = PANE_WIDTH * tab / 4

    fun tabWidth(tab: Int) = tabX(tab + 1) - tabX(tab)

    fun container(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.add(Comp(ROOT, TYPE_LAYER, 0, 0, 0, 0, -1, resizeX = 1, resizeY = 1))
        Tab.values().forEachIndexed { i, tab ->
            val base = tabBase(i)
            val w = tabWidth(i)
            b.layer(base, tabX(i), 0, w, TAB_HEIGHT, ROOT, ops = listOf(tab.tooltip))
            b.add(Comp(base + TAB_LEFT, TYPE_GRAPHIC, 0, TAB_HEIGHT - 21, 20, 21, base, graphic = TAB_SIDE_NORMAL))
            b.add(Comp(base + TAB_MIDDLE, TYPE_GRAPHIC, (w - 20) / 2, TAB_HEIGHT - 21, 20, 21, base, graphic = TAB_MIDDLE_NORMAL))
            b.add(Comp(base + TAB_RIGHT, TYPE_GRAPHIC, w - 20, TAB_HEIGHT - 21, 20, 21, base, graphic = TAB_SIDE_NORMAL, hflip = true))
            b.add(Comp(base + TAB_ICON, TYPE_GRAPHIC, (w - 18) / 2, TAB_HEIGHT - 1 - 18, 18, 18, base, graphic = sprite(tab.osrsIcon)))
        }
        b.layer(BOX, 3, TAB_HEIGHT, 184, 233, ROOT)
        // Full pane width: 190's filter labels are absolute up to x 183 (a 174 wide mount cut them off).
        b.layer(QUEST_SCROLL, 0, TAB_HEIGHT, PANE_WIDTH, PANE_HEIGHT - TAB_HEIGHT, ROOT, hidden = true)
        b.layer(QUEST_MOUNT, 0, 0, PANE_WIDTH, QUEST_LIST_HEIGHT, QUEST_SCROLL)
        b.scrollbarFor(QUEST_SCROLLBAR, QUEST_VIEW_WIDTH, TAB_HEIGHT, PANE_HEIGHT - TAB_HEIGHT, ROOT, CONTAINER, QUEST_SCROLL)
        val built = b.done(CONTAINER_COUNT)
        // The scrollbar starts hidden with the quest list.
        return built.map { if (it.id == QUEST_SCROLLBAR) it.copy(hidden = true) else it }
    }

    // ------------------------------------------------------------------ shared panel pieces (OSRS 846 geometry)

    const val PANEL_WIDTH = 184
    const val PANEL_HEIGHT = 233
    const val P_ROOT = 0
    const val P_TITLE = 1
    const val P_BORDER = 2
    const val P_OUTER = 3
    const val P_INNER = 4
    const val P_CONTENT = 5
    const val CONTENT_WIDTH = 170
    const val CONTENT_HEIGHT = 204

    /** Title, the 715 border and the content layer (7, 3, 170 x 204 inside the border): ids 0..5. */
    private fun panel(b: Builder, title: String) {
        b.add(Comp(P_ROOT, TYPE_LAYER, 0, 0, 0, 0, -1, resizeX = 1, resizeY = 1))
        b.text(P_TITLE, 3, 0, PANEL_WIDTH - 6, 20, P_ROOT, b.fonts.b12, title, colour = ORANGE)
        b.layer(P_BORDER, 0, 20, PANEL_WIDTH, PANEL_HEIGHT - 22, P_ROOT)
        b.rect(P_OUTER, 0, 0, PANEL_WIDTH, PANEL_HEIGHT - 22, P_BORDER, BORDER_OUTER)
        b.rect(P_INNER, 1, 1, PANEL_WIDTH - 2, PANEL_HEIGHT - 24, P_BORDER, BORDER_INNER)
        b.layer(P_CONTENT, (PANEL_WIDTH - CONTENT_WIDTH) / 2, (PANEL_HEIGHT - 22 - CONTENT_HEIGHT) / 2, CONTENT_WIDTH, CONTENT_HEIGHT, P_BORDER)
    }

    /** Clientscript 5288 with struct 3712: ten components from [first], all children of [parent], filling (0,0,w,h). */
    const val BOX_PIECES = 10

    /** Offsets (x, y) of the ten box pieces inside a w x h box, in component order - the server moves a box with these. */
    fun boxOffsets(w: Int, h: Int): List<Pair<Int, Int>> =
        listOf(1 to 1, 4 to 4, 4 to 0, 4 to h - 4, 0 to 4, w - 4 to 4, 0 to 0, w - 4 to 0, 0 to h - 4, w - 4 to h - 4)

    private fun box(b: Builder, first: Int, parent: Int, w: Int, h: Int, hidden: Boolean = false) {
        val at = boxOffsets(w, h)
        fun x(i: Int) = at[i].first
        fun y(i: Int) = at[i].second
        b.add(Comp(first, TYPE_RECTANGLE, x(0), y(0), w - 2, h - 2, parent, colour = BOX_FILL, filled = true, hidden = hidden))
        b.add(Comp(first + 1, TYPE_GRAPHIC, x(1), y(1), w - 8, h - 8, parent, graphic = texture(), tiling = true, hidden = hidden))
        b.add(Comp(first + 2, TYPE_GRAPHIC, x(2), y(2), w - 8, 4, parent, graphic = sprite(3528), tiling = true, hidden = hidden))
        b.add(Comp(first + 3, TYPE_GRAPHIC, x(3), y(3), w - 8, 4, parent, graphic = sprite(3529), tiling = true, hidden = hidden))
        b.add(Comp(first + 4, TYPE_GRAPHIC, x(4), y(4), 4, h - 8, parent, graphic = sprite(3530), tiling = true, hidden = hidden))
        b.add(Comp(first + 5, TYPE_GRAPHIC, x(5), y(5), 4, h - 8, parent, graphic = sprite(3531), tiling = true, hidden = hidden))
        b.add(Comp(first + 6, TYPE_GRAPHIC, x(6), y(6), 4, 4, parent, graphic = sprite(3524), hidden = hidden))
        b.add(Comp(first + 7, TYPE_GRAPHIC, x(7), y(7), 4, 4, parent, graphic = sprite(3525), hidden = hidden))
        b.add(Comp(first + 8, TYPE_GRAPHIC, x(8), y(8), 4, 4, parent, graphic = sprite(3526), hidden = hidden))
        b.add(Comp(first + 9, TYPE_GRAPHIC, x(9), y(9), 4, 4, parent, graphic = sprite(3527), hidden = hidden))
    }

    /** Clientscript 5845: texture 297 then the eight 9 px v2 stone button pieces 913-920, from [first]. */
    const val BUTTON_PIECES = 9

    private fun button(b: Builder, first: Int, parent: Int, w: Int, h: Int) {
        b.add(Comp(first, TYPE_GRAPHIC, 0, 0, w, h, parent, graphic = texture(), tiling = true))
        b.add(Comp(first + 1, TYPE_GRAPHIC, 0, 0, 9, 9, parent, graphic = stone(913)))
        b.add(Comp(first + 2, TYPE_GRAPHIC, w - 9, 0, 9, 9, parent, graphic = stone(914)))
        b.add(Comp(first + 3, TYPE_GRAPHIC, 0, h - 9, 9, 9, parent, graphic = stone(915)))
        b.add(Comp(first + 4, TYPE_GRAPHIC, w - 9, h - 9, 9, 9, parent, graphic = stone(916)))
        b.add(Comp(first + 5, TYPE_GRAPHIC, 0, 9, 9, h - 18, parent, graphic = stone(917), tiling = true))
        b.add(Comp(first + 6, TYPE_GRAPHIC, 9, 0, w - 18, 9, parent, graphic = stone(918), tiling = true))
        b.add(Comp(first + 7, TYPE_GRAPHIC, w - 9, 9, 9, h - 18, parent, graphic = stone(919), tiling = true))
        b.add(Comp(first + 8, TYPE_GRAPHIC, 9, h - 9, w - 18, 9, parent, graphic = stone(920), tiling = true))
    }

    // ------------------------------------------------------------------ Deadman panel

    const val BLOCKS = 5
    const val BLOCK_FIRST = 6
    const val BLOCK_HEIGHT = 38
    const val BLOCK_PITCH = 41
    const val BLOCK_BOX = 1 // +1..+10
    const val BLOCK_TITLE = BLOCK_BOX + BOX_PIECES // 11
    const val BLOCK_INFO = BLOCK_TITLE + 1 // 12: button layer
    const val BLOCK_INFO_TEXT = BLOCK_INFO + 1 + BUTTON_PIECES // 22
    const val BLOCK_VALUE = BLOCK_INFO_TEXT + 1 // 23
    const val BLOCK_STRIDE = BLOCK_VALUE + 1 // 24
    const val DEADMAN_COUNT = BLOCK_FIRST + BLOCKS * BLOCK_STRIDE
    const val INFO_BUTTON_WIDTH = 40
    const val INFO_BUTTON_HEIGHT = 18

    fun blockBase(block: Int) = BLOCK_FIRST + block * BLOCK_STRIDE

    fun deadman(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        panel(b, "Deadman 78")
        for (i in 0 until BLOCKS) {
            val base = blockBase(i)
            b.layer(base, 0, i * BLOCK_PITCH, CONTENT_WIDTH, BLOCK_HEIGHT, P_CONTENT)
            box(b, base + BLOCK_BOX, base, CONTENT_WIDTH, BLOCK_HEIGHT)
            b.text(base + BLOCK_TITLE, 7, 3, CONTENT_WIDTH - 14 - INFO_BUTTON_WIDTH, 18, base, fonts.p12, "", colour = ORANGE, alignX = 0)
            val info = base + BLOCK_INFO
            b.layer(info, CONTENT_WIDTH - 6 - INFO_BUTTON_WIDTH, 3, INFO_BUTTON_WIDTH, INFO_BUTTON_HEIGHT, base, ops = listOf("Info"))
            button(b, info + 1, info, INFO_BUTTON_WIDTH, INFO_BUTTON_HEIGHT)
            b.text(base + BLOCK_INFO_TEXT, 0, 0, INFO_BUTTON_WIDTH, INFO_BUTTON_HEIGHT, info, fonts.p11, "Info", colour = BUTTON_TEXT)
            b.text(base + BLOCK_VALUE, 4, 20, CONTENT_WIDTH - 8, 15, base, fonts.b12, "", colour = WHITE)
        }
        return b.done(DEADMAN_COUNT)
    }

    // ------------------------------------------------------------------ Deadman Groups panel

    /** Options per group, in [gg.rsmod.plugins.content.quests.foundation.ChoiceGroup] order (a test keeps them equal). */
    val GROUP_SIZES = intArrayOf(3, 2, 3)
    const val MAX_OPTIONS = 3
    const val G_SCROLL = 6
    const val G_SCROLLBAR = 7
    const val G_NOTE = 8
    const val G_GROUP_FIRST = 9
    const val G_HEADER = 0
    const val G_OPTION_FIRST = 1
    const val OPTION_BOX = 0 // +0..+9
    const val OPTION_NAME = BOX_PIECES // 10
    const val OPTION_STATUS = OPTION_NAME + 1 // 11
    const val OPTION_STRIDE = OPTION_STATUS + 1 // 12
    const val G_GROUP_STRIDE = G_OPTION_FIRST + MAX_OPTIONS * OPTION_STRIDE // 37
    const val GROUPS_COUNT = G_GROUP_FIRST + 3 * G_GROUP_STRIDE

    const val G_VIEW_WIDTH = CONTENT_WIDTH - 16
    const val OPTION_WIDTH = G_VIEW_WIDTH - 4

    /** Name line plus two status lines: "Locked - complete Temple at Senntisten" does not fit one 142 px line. */
    const val OPTION_HEIGHT = 42
    const val OPTION_TEXT_X = 6
    const val OPTION_NAME_Y = 3
    const val OPTION_STATUS_Y = 17
    const val HEADER_HEIGHT = 18
    const val NOTE_HEIGHT = 36

    fun groupBase(group: Int) = G_GROUP_FIRST + group * G_GROUP_STRIDE

    fun optionBase(group: Int, option: Int) = groupBase(group) + G_OPTION_FIRST + option * OPTION_STRIDE

    fun groups(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        panel(b, "Deadman Groups")
        b.layer(G_SCROLL, 0, 0, G_VIEW_WIDTH, CONTENT_HEIGHT, P_CONTENT)
        b.scrollbarFor(G_SCROLLBAR, G_VIEW_WIDTH, 0, CONTENT_HEIGHT, P_CONTENT, GROUPS, G_SCROLL)
        b.text(G_NOTE, 2, 0, G_VIEW_WIDTH - 4, NOTE_HEIGHT, G_SCROLL, fonts.p11, "", colour = WHITE, lineHeight = 12, hidden = true)
        for (g in 0 until 3) {
            b.text(groupBase(g) + G_HEADER, 2, 0, G_VIEW_WIDTH - 4, HEADER_HEIGHT, G_SCROLL, fonts.b12, "", colour = ORANGE, alignX = 0)
            for (o in 0 until MAX_OPTIONS) {
                val base = optionBase(g, o)
                box(b, base + OPTION_BOX, G_SCROLL, OPTION_WIDTH, OPTION_HEIGHT, hidden = true)
                b.text(base + OPTION_NAME, 0, 0, OPTION_WIDTH - 2 * OPTION_TEXT_X, 14, G_SCROLL, fonts.p12, "", colour = WHITE, alignX = 0, hidden = true)
                b.text(base + OPTION_STATUS, 0, 0, OPTION_WIDTH - 2 * OPTION_TEXT_X, 22, G_SCROLL, fonts.p11, "", colour = WHITE, alignX = 0, alignY = 0, lineHeight = 11, hidden = true)
            }
        }
        return b.done(GROUPS_COUNT)
    }

    // ------------------------------------------------------------------ Achievement Diaries panel

    const val D_TEXT = 6
    const val DIARIES_COUNT = 7

    fun diaries(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        panel(b, "Achievement Diaries")
        b.text(D_TEXT, 0, 0, CONTENT_WIDTH, CONTENT_HEIGHT, P_CONTENT, fonts.b12, "Coming soon", colour = ORANGE)
        return b.done(DIARIES_COUNT)
    }

    // ------------------------------------------------------------------ Info window

    /** Fits the fixed-mode main screen (512 x 334); room for the longest text (emblem rules) at 14 px per line. */
    const val INFO_WIDTH = 440
    const val INFO_HEIGHT = 300
    const val I_CLOSE = DeathsOfficeInterfaceImportTool.CLOSE // 13
    const val I_TITLE = DeathsOfficeInterfaceImportTool.TITLE // 3
    const val I_BODY = 14
    const val I_BODY_OUTER = 15
    const val I_BODY_INNER = 16
    const val I_TEXT = 17
    const val INFO_COUNT = 18

    fun info(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.frame(INFO_WIDTH, INFO_HEIGHT, "")
        b.layer(I_BODY, 12, 42, INFO_WIDTH - 24, INFO_HEIGHT - 54, DeathsOfficeInterfaceImportTool.WINDOW)
        b.rect(I_BODY_OUTER, 0, 0, INFO_WIDTH - 24, INFO_HEIGHT - 54, I_BODY, BORDER_OUTER)
        b.rect(I_BODY_INNER, 1, 1, INFO_WIDTH - 26, INFO_HEIGHT - 56, I_BODY, BORDER_INNER)
        b.text(I_TEXT, 8, 6, INFO_WIDTH - 40, INFO_HEIGHT - 66, I_BODY, fonts.p12, "", colour = WHITE, alignX = 0, alignY = 0, lineHeight = 14)
        return b.done(INFO_COUNT)
    }

    // ------------------------------------------------------------------ import

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val index = DeathsOfficeInterfaceImportTool.INDEX_INTERFACES
        val sprites = DeathsOfficeInterfaceImportTool.INDEX_SPRITES
        val library = CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        val fonts: LootKeyInterfaceImportTool.Fonts
        val current = HashMap<Int, Map<Int, String>>()
        val currentSprites = HashMap<Int, String>()
        val gameframe = HashMap<IconPatch, ByteArray>()
        val questList = LinkedHashMap<FieldPatch, ByteArray>()
        try {
            fonts = LootKeyInterfaceImportTool.fonts(library)
            listOf(CONTAINER, DEADMAN, GROUPS, DIARIES, INFO).forEach { id ->
                val files = library.index(index).archive(id)?.fileIds()?.toList() ?: emptyList()
                current[id] = files.associateWith { f -> CacheItemProbeTool.sha1(library.data(index, id, f)!!) }
            }
            OSRS_SPRITES.indices.forEach { i ->
                library.data(sprites, SPRITE_BASE + i, 0)?.let { currentSprites[SPRITE_BASE + i] = CacheItemProbeTool.sha1(it) }
            }
            // Other imports must have brought in the texture and stone button sprites this tool reuses.
            listOf(texture(), stone(913), stone(920), stone(535)).forEach { id ->
                require(library.index(sprites).archive(id) != null) { "sprite $id missing: run the loot-key and Death's Office imports first" }
            }
            ICON_PATCHES.forEach { p -> gameframe[p] = library.data(index, p.interfaceId, p.component) ?: error("${p.interfaceId}:${p.component} missing") }
            QUEST_LIST_FIT.forEach { p -> questList[p] = library.data(index, p.interfaceId, p.component) ?: error("${p.interfaceId}:${p.component} missing") }
        } finally {
            library.close()
        }
        val mutations = mutableListOf<CacheMutation>()
        ModernCacheReader(File(LootKeyInterfaceImportTool.SOURCE_CACHE)).use { source ->
            OSRS_SPRITES.forEachIndexed { i, osrsId ->
                val bytes = source.file(sprites, osrsId, 0) ?: error("OSRS sprite $osrsId missing from ${LootKeyInterfaceImportTool.SOURCE_CACHE}")
                mutations += CacheMutation(sprites, SPRITE_BASE + i, 0, bytes, "sprite osrs:$osrsId -> 667:${SPRITE_BASE + i}", expectedCurrentSha1 = currentSprites[SPRITE_BASE + i])
            }
        }
        val screens =
            listOf(
                CONTAINER to container(fonts),
                DEADMAN to deadman(fonts),
                GROUPS to groups(fonts),
                DIARIES to diaries(fonts),
                INFO to info(fonts),
            )
        screens.forEach { (id, comps) ->
            val have = current.getValue(id)
            comps.forEach { c ->
                mutations += CacheMutation(index, id, c.id, DeathsOfficeInterfaceImportTool.encode(c), "interface $id:${c.id} type=${c.type}", expectedCurrentSha1 = have[c.id])
            }
            have.keys.filter { it >= comps.size }.sorted().forEach { f ->
                mutations += CacheMutation(index, id, f, null, "interface $id:$f (old layout)")
            }
        }
        gameframe.forEach { (p, bytes) ->
            mutations += CacheMutation(index, p.interfaceId, p.component, patchIcon(bytes, p), "gameframe quest icon ${p.interfaceId}:${p.component}", expectedCurrentSha1 = CacheItemProbeTool.sha1(bytes))
        }
        questList.forEach { (p, bytes) ->
            mutations += CacheMutation(index, p.interfaceId, p.component, patchField(bytes, p), "quest list fit ${p.interfaceId}:${p.component}", expectedCurrentSha1 = CacheItemProbeTool.sha1(bytes))
        }
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interfaces $CONTAINER-$INFO, sprites $SPRITE_BASE..${SPRITE_BASE + OSRS_SPRITES.size - 1} and the gameframe icon")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }
}
