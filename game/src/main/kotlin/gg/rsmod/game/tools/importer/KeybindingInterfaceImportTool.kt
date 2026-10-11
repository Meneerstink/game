package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Builder
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Comp
import java.io.File

/**
 * The OSRS Keybinding window (owner 2026-10-11: "zet keybinding net als osrs in de client" - in game, like OSRS, not in the 78
 * Plugins sidebar), ported from OSRS interface 121 "KEYBINDING" (OpenRS2 cache 2686, decoded with tools/cache667/osrs_if3.py):
 * the steelborder frame (502x300), fourteen tab rows in three columns (x 20 / 180 / 340, y 5 + 43n) - each the stone (sprite
 * 1180) with the tab icon, and a dropdown: dark box 0x2e281d, outline 0x544834, the key in p12 orange and the arrow (sprite 316
 * flipped) - the "Esc closes current interface" checkbox (sprites 8378 / 8379) and the "Restore Defaults" stone button. OSRS
 * builds the dropdown list with clientscripts; here it is one list of fourteen keys the server moves under the clicked row.
 *
 * Revision 667 has no Account Management tab: that row (OSRS sprite 1709) is the 667 Friends Chat tab with its own 667 icon
 * (sprite 1828); the OSRS chat-channel row is the 667 Clan Chat tab.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.KeybindingInterfaceImportTool [--apply]` with the servers stopped. The
 * tool owns interface [INTERFACE_ID], sprites [SPRITE_BASE].. and the [SETTINGS_BUTTON] components it appends to the 667 Settings
 * tab (interface 261: a "Keybinding" button on the empty band between its button rows).
 */
object KeybindingInterfaceImportTool {
    const val INTERFACE_ID = 1181
    const val INDEX_INTERFACES = 3
    const val INDEX_SPRITES = 8

    const val WIDTH = 502
    const val HEIGHT = 300

    /** OSRS sprites imported as-is; 667 id = [SPRITE_BASE] + index (first free group after the cache's 8450). */
    const val SPRITE_BASE = 8500
    val OSRS_SPRITES =
        intArrayOf(
            1180, // 0 stone
            168, 898, 899, 900, 901, // 1-5 combat, skills, quests, inventory, equipment
            902, 903, 905, 907, // 6-9 prayer, magic, friends, logout
            908, 909, 904, 910, // 10-13 settings, emotes, chat-channel (clan), music
            316, // 14 dropdown arrow
            8378, 8379, // 15-16 checkbox off / on
        )

    fun sprite(osrsId: Int): Int {
        val index = OSRS_SPRITES.indexOf(osrsId)
        require(index >= 0) { "OSRS sprite $osrsId is not part of the import" }
        return SPRITE_BASE + index
    }

    /** 667 Friends Chat tab icon (gameframe 548:110), in the cache already. */
    const val FRIENDS_CHAT_ICON = 1828

    /**
     * The fourteen rows in OSRS order (columns of five, five and four): the setting key (server ClientHudConfig.KEYBIND_TABS),
     * the label and the icon sprite.
     */
    val ROWS: List<Triple<String, String, Int>> =
        listOf(
            Triple("combat", "Combat Options", sprite(168)),
            Triple("skills", "Skills", sprite(898)),
            Triple("quests", "Quest List", sprite(899)),
            Triple("inventory", "Inventory", sprite(900)),
            Triple("equipment", "Worn Equipment", sprite(901)),
            Triple("prayer", "Prayer", sprite(902)),
            Triple("magic", "Magic", sprite(903)),
            Triple("friends", "Friends List", sprite(905)),
            Triple("friendschat", "Friends Chat", FRIENDS_CHAT_ICON),
            Triple("logout", "Logout", sprite(907)),
            Triple("settings", "Settings", sprite(908)),
            Triple("emotes", "Emotes", sprite(909)),
            Triple("clanchat", "Clan Chat", sprite(904)),
            Triple("music", "Music Player", sprite(910)),
        )

    /** The dropdown choices, in list order (server ClientHudConfig.KEYBIND_KEYS). */
    val KEY_LABELS = listOf("None", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "Esc")

    // ---- components (after the steelborder frame 0..13) ----
    const val CONTENT = 14
    const val ROW_FIRST = 15

    /** Inside a row: +0 layer, +1 stone, +2 icon, +3 box, +4 outline, +5 key text, +6 arrow, +7 click area. */
    const val ROW_STRIDE = 8
    const val ROW_STONE = 1
    const val ROW_ICON = 2
    const val ROW_KEY = 5
    const val ROW_CLICK = 7

    val ESC = ROW_FIRST + ROWS.size * ROW_STRIDE

    /** Inside the Esc option: +0 layer (click), +1 checkbox, +2 label. */
    val ESC_CHECKBOX = ESC + 1
    val ESC_LABEL = ESC + 2
    val RESTORE = ESC + 3

    /** Restore Defaults: +0 layer (click), +1..+9 stone pieces, +10 label. */
    val RESTORE_LABEL = RESTORE + 10

    /** The dropdown: a catch-all layer over the content (a click outside the list closes it) and the list box it holds. */
    val DROP_OVERLAY = RESTORE + 11
    val DROP_BOX = DROP_OVERLAY + 1
    val DROP_FILL = DROP_BOX + 1
    val DROP_OUTLINE = DROP_BOX + 2

    /** Inside an option: +0 layer (click, hover), +1 text. */
    val OPTION_FIRST = DROP_BOX + 3
    const val OPTION_STRIDE = 2
    val COUNT = OPTION_FIRST + KEY_LABELS.size * OPTION_STRIDE

    // ---- geometry (OSRS 121) ----
    const val CONTENT_X = 5
    const val CONTENT_Y = 40
    const val CONTENT_W = WIDTH - 10
    const val CONTENT_H = HEIGHT - 45
    const val ROW_W = 136
    const val ROW_H = 38
    const val ROW_STEP_Y = 43
    val COLUMN_X = listOf(20, 180, 340)
    const val COLUMN_ROWS = 5

    /** The dropdown box inside a row (OSRS 121: outline 40,5 84x18). */
    const val BOX_X = 40
    const val BOX_Y = 5
    const val BOX_W = 84
    const val BOX_H = 18
    const val OPTION_H = 15
    val LIST_H = KEY_LABELS.size * OPTION_H + 4

    const val ORANGE = PohTeleportInterfaceImportTool.ORANGE
    const val WHITE = PohTeleportInterfaceImportTool.WHITE
    const val BOX_FILL = 0x2E281D
    const val BOX_LINE = 0x544834

    /** if3 component types (DeathsOfficeInterfaceImportTool's are private). */
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5

    fun row(index: Int): Int = ROW_FIRST + index * ROW_STRIDE

    fun option(index: Int): Int = OPTION_FIRST + index * OPTION_STRIDE

    fun rowX(index: Int): Int = COLUMN_X[index / COLUMN_ROWS]

    fun rowY(index: Int): Int = 5 + (index % COLUMN_ROWS) * ROW_STEP_Y

    private fun hover(component: Int): Pair<List<Int>, List<Int>> =
        listOf(PohTeleportInterfaceImportTool.COLOUR_SCRIPT, component, WHITE) to listOf(PohTeleportInterfaceImportTool.COLOUR_SCRIPT, component, ORANGE)

    fun build(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.frame(WIDTH, HEIGHT, "Keybinding")
        b.layer(CONTENT, CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H, DeathsOfficeInterfaceImportTool.WINDOW)
        ROWS.forEachIndexed { i, (_, label, icon) ->
            val r = row(i)
            b.layer(r, rowX(i), rowY(i), ROW_W, ROW_H, CONTENT)
            b.graphic(r + ROW_STONE, 1, 1, 33, 36, r, sprite(1180), tiling = true)
            b.graphic(r + ROW_ICON, 1, 1, 33, 36, r, icon)
            b.rect(r + 3, BOX_X + 1, BOX_Y + 1, 66, 16, r, BOX_FILL, filled = true)
            b.rect(r + 4, BOX_X, BOX_Y, BOX_W, BOX_H, r, BOX_LINE)
            b.text(r + ROW_KEY, BOX_X + 6, BOX_Y + 2, 62, 16, r, fonts.p12, "", alignX = 0)
            b.add(Comp(r + 6, TYPE_GRAPHIC, BOX_X + 67, BOX_Y + 1, 16, 16, r, graphic = sprite(316), vflip = true))
            val (over, leave) = hover((INTERFACE_ID shl 16) or (r + ROW_KEY))
            b.add(Comp(r + ROW_CLICK, 0, BOX_X, BOX_Y, BOX_W, BOX_H, r, ops = listOf("Select"), opBase = label, onMouseOver = over, onMouseLeave = leave))
        }
        b.layer(ESC, 19, 225, 200, 21, CONTENT, ops = listOf("Toggle"), opBase = "Esc closes current interface")
        b.graphic(ESC_CHECKBOX, 5, 2, 17, 17, ESC, sprite(8378))
        b.text(ESC_LABEL, 30, 1, 170, 20, ESC, fonts.p12, "Esc closes current interface", alignX = 0)
        b.layer(RESTORE, 333, 218, 140, 30, CONTENT, ops = listOf("Restore Defaults"))
        b.stoneButton(RESTORE + 1, 140, 30, RESTORE, 913) // OSRS v2_stone_button pieces (the builder maps them)
        b.text(RESTORE_LABEL, 0, 0, 140, 30, RESTORE, fonts.b12, "Restore Defaults")
        b.layer(DROP_OVERLAY, 0, 0, CONTENT_W, CONTENT_H, CONTENT, hidden = true, ops = listOf("Close"))
        b.layer(DROP_BOX, 0, 0, BOX_W, LIST_H, DROP_OVERLAY)
        b.rect(DROP_FILL, 0, 0, BOX_W, LIST_H, DROP_BOX, BOX_FILL, filled = true)
        b.rect(DROP_OUTLINE, 0, 0, BOX_W, LIST_H, DROP_BOX, BOX_LINE)
        KEY_LABELS.forEachIndexed { k, label ->
            val o = option(k)
            val (over, leave) = hover((INTERFACE_ID shl 16) or (o + 1))
            b.add(Comp(o, 0, 2, 2 + k * OPTION_H, BOX_W - 4, OPTION_H, DROP_BOX, ops = listOf("Select"), opBase = label, onMouseOver = over, onMouseLeave = leave))
            b.text(o + 1, 4, 0, BOX_W - 8, OPTION_H, o, fonts.p12, label, alignX = 0)
        }
        return b.done(COUNT)
    }

    // ---- the "Keybinding" button appended to the 667 Settings tab (261) ----

    /** 261 had components 0..22; the button is appended after them (a layer with the op and its label). */
    const val SETTINGS_INTERFACE = 261
    const val SETTINGS_BUTTON = 23
    const val SETTINGS_LABEL = 24

    /** On 261's middle band (sprite 851 at y 90, 190x27) between its two rows of buttons. */
    fun settingsButton(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> =
        listOf(
            Comp(SETTINGS_BUTTON, 0, 20, 92, 150, 23, 0, ops = listOf("Keybinding")),
            Comp(SETTINGS_LABEL, TYPE_TEXT, 0, 0, 150, 23, SETTINGS_BUTTON, font = fonts.p12, text = "Keybinding", colour = ORANGE, alignX = 1, alignY = 1),
        )

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val fonts: LootKeyInterfaceImportTool.Fonts
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val current: Map<String, String?>
        val settingsFiles: List<Int>
        try {
            fonts = LootKeyInterfaceImportTool.fonts(library)
            require(library.index(12).archive(PohTeleportInterfaceImportTool.COLOUR_SCRIPT) != null) {
                "clientscript ${PohTeleportInterfaceImportTool.COLOUR_SCRIPT} (hover colour) missing - run PohTeleportInterfaceImportTool first"
            }
            settingsFiles = library.index(INDEX_INTERFACES).archive(SETTINGS_INTERFACE)?.fileIds()?.toList() ?: emptyList()
            require(settingsFiles.isNotEmpty()) { "interface $SETTINGS_INTERFACE missing" }
            require(settingsFiles.max() < SETTINGS_BUTTON || settingsFiles.max() == SETTINGS_LABEL) { "interface 261 grew: re-check the button ids" }
            // This tool owns these files: pin what is there so a re-run replaces its own earlier writes.
            val mine = mutableMapOf<String, String?>()
            library.index(INDEX_INTERFACES).archive(INTERFACE_ID)?.fileIds()?.forEach { f ->
                mine["i$INTERFACE_ID:$f"] = library.data(INDEX_INTERFACES, INTERFACE_ID, f)?.let(CacheItemProbeTool::sha1)
            }
            listOf(SETTINGS_BUTTON, SETTINGS_LABEL).forEach { f ->
                mine["i$SETTINGS_INTERFACE:$f"] = library.data(INDEX_INTERFACES, SETTINGS_INTERFACE, f)?.let(CacheItemProbeTool::sha1)
            }
            OSRS_SPRITES.indices.forEach { i -> mine["s${SPRITE_BASE + i}"] = library.data(INDEX_SPRITES, SPRITE_BASE + i, 0)?.let(CacheItemProbeTool::sha1) }
            current = mine
        } finally {
            library.close()
        }
        val source = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val mutations = mutableListOf<CacheMutation>()
        try {
            OSRS_SPRITES.forEachIndexed { i, osrsId ->
                val bytes = source.file(INDEX_SPRITES, osrsId, 0) ?: error("OSRS sprite $osrsId missing from ${OsrsItemImportTool.SOURCE_CACHE}")
                mutations += CacheMutation(INDEX_SPRITES, SPRITE_BASE + i, 0, bytes, "keybinding sprite osrs:$osrsId -> 667:${SPRITE_BASE + i}", expectedCurrentSha1 = current["s${SPRITE_BASE + i}"])
            }
        } finally {
            source.close()
        }
        build(fonts).forEach { c ->
            mutations += CacheMutation(
                INDEX_INTERFACES, INTERFACE_ID, c.id, DeathsOfficeInterfaceImportTool.encode(c), "interface $INTERFACE_ID:${c.id} type=${c.type}",
                expectedCurrentSha1 = current["i$INTERFACE_ID:${c.id}"],
            )
        }
        settingsButton(fonts).forEach { c ->
            mutations += CacheMutation(
                INDEX_INTERFACES, SETTINGS_INTERFACE, c.id, DeathsOfficeInterfaceImportTool.encode(c), "interface $SETTINGS_INTERFACE:${c.id} keybinding button",
                expectedCurrentSha1 = current["i$SETTINGS_INTERFACE:${c.id}"],
            )
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interface $INTERFACE_ID ($COUNT components), 261:$SETTINGS_BUTTON-$SETTINGS_LABEL and sprites $SPRITE_BASE..")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }
}
