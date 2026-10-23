package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Builder
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Comp

/**
 * Owner 2026-10-10 ("a gui for Admins and moderator to see all commands ... a list where you can search every command"; then
 * "maak de gui beter"): the staff command list (`::commands`), a revision-667 if3 interface built like
 * [PohTeleportInterfaceImportTool]'s Portal Nexus - the OSRS steelborder frame, a scroll layer with a 16 px scrollbar
 * (clientscript 30), rows that are layers carrying the op and the white-under-the-mouse highlight
 * ([PohTeleportInterfaceImportTool.COLOUR_SCRIPT], already in both caches) - and above the list:
 *
 *  - [TABS] Players / Punish / Teleport / Info (A17; 3 more slots hidden) and the [SEARCH] button ([CLEAR] hidden), each an OSRS v2 stone button
 *    with two looks like the Death's Office coffer buttons ([LOOK_OUT] normal, [LOOK_IN] pressed = the selected tab);
 *  - [STATUS] ("Showing 12 of 160 - search: tele") and [HINT];
 *  - column headers over the list ([HEAD_COMMAND] / [HEAD_DESCRIPTION]).
 *
 * A row ([ROW_HEIGHT] px): the command (p12 orange) with its rank under it (p11, coloured by the server) in the left column, the
 * description (p11, up to two lines) on the right, every second row on a black backing at transparency 225. Owner 2026-10-10 ("its
 * not working easy"): op 1 "Run" runs the command (asking for its arguments first when it takes any), op 2 "Info" prints the usage
 * and the example in the chatbox. The server fills everything with IF_SETTEXT / IF_SETHIDE and sets the scroll height
 * with [DeathsOfficeInterfaceImportTool.SCROLL_SCRIPT].
 *
 * Usage: `./gradlew :game:runStaffCommandsInterfaceImportTool --args="[--apply] [--replace-from-journal=<tx>]"` with the servers
 * stopped. The tool owns interface [INTERFACE_ID].
 */
object StaffCommandsInterfaceImportTool {
    const val INTERFACE_ID = 1180
    const val INDEX_INTERFACES = 3

    /** As large as the fixed-mode main screen allows (512x334) with a little margin. */
    const val WIDTH = 504
    const val HEIGHT = 330

    const val CONTENT = 14

    // ---- buttons: a layer carrying the op, two looks (stone pieces + label) ----
    const val BUTTON_FIRST = 15
    const val BUTTON_STRIDE = 23
    const val LOOK_OUT = 1
    const val LOOK_IN = 12

    /** Inside a look: +1..+9 the stone pieces, +10 the label. */
    const val LOOK_LABEL = 10

    /**
     * A17 (owner 10-11: "fewer buttons, grouped in categories Players / Punish / Teleport / Info"; VRAGEN 17 = A: no Recent or
     * Gear tab). The window keeps its 7 tab slots so every component id stays where tx-20261010-195851 put it (a shorter
     * interface would leave the old higher components behind in the archive): the last three slots and Clear are built hidden.
     */
    val TAB_LABELS = listOf("Players", "Punish", "Teleport", "Info")
    const val TAB_SLOTS = 7
    val TABS = (0 until TAB_SLOTS).map { BUTTON_FIRST + it * BUTTON_STRIDE }
    val SEARCH = BUTTON_FIRST + TAB_SLOTS * BUTTON_STRIDE
    val CLEAR = SEARCH + BUTTON_STRIDE

    val STATUS = CLEAR + BUTTON_STRIDE
    val HINT = STATUS + 1
    val HEAD_COMMAND = HINT + 1
    val HEAD_DESCRIPTION = HEAD_COMMAND + 1
    val LIST = HEAD_DESCRIPTION + 1
    val OUTLINE = LIST + 1
    val SCROLL = OUTLINE + 1
    val SCROLLBAR = SCROLL + 1
    val ROW_FIRST = SCROLLBAR + 1

    /** Inside a row (a layer carrying the op and the highlight): +1 backing, +2 command, +3 rank, +4 description. */
    const val ROW_STRIDE = 5
    const val ROW_BACKING = 1
    const val ROW_NAME = 2
    const val ROW_RANK = 3
    const val ROW_DESCRIPTION = 4

    /** The two ops of a row. */
    val ROW_OPS = listOf("Run", "Info")

    /** Every command with room to grow (235 on 2026-10-10; guarded by a test). */
    const val ROWS = 300
    val COUNT = ROW_FIRST + ROWS * ROW_STRIDE

    // ---- geometry (inside CONTENT) ----
    const val CONTENT_X = 10
    const val CONTENT_Y = 38
    const val CONTENT_W = WIDTH - 20
    const val CONTENT_H = HEIGHT - 48
    const val BUTTON_H = 24
    const val TAB_W = 80
    const val TAB_STEP = 82
    val SEARCH_X = TAB_LABELS.size * TAB_STEP + 4
    const val SEARCH_W = 70
    val CLEAR_X = SEARCH_X + SEARCH_W + 4
    val CLEAR_W = CONTENT_W - CLEAR_X
    const val STATUS_Y = BUTTON_H + 3
    const val STATUS_H = 15
    const val HEAD_Y = STATUS_Y + STATUS_H + 2
    const val HEAD_H = 14
    const val LIST_Y = HEAD_Y + HEAD_H
    const val LIST_W = CONTENT_W
    const val LIST_H = CONTENT_H - LIST_Y
    const val SCROLL_W = LIST_W - 18
    const val SCROLL_H = LIST_H - 2
    const val ROW_HEIGHT = 30
    const val NAME_W = 150

    const val ORANGE = PohTeleportInterfaceImportTool.ORANGE
    const val WHITE = PohTeleportInterfaceImportTool.WHITE
    const val LIGHT = 0xD8D8D8
    const val HEAD = 0xFFCC66
    const val BUTTON_TEXT = DeathsOfficeInterfaceImportTool.BUTTON_TEXT

    /** OSRS v2 stone pieces (DeathsOfficeInterfaceImportTool STONE_OUT / STONE_IN). */
    private const val STONE_OUT = 929
    private const val STONE_IN = 921

    fun row(index: Int): Int = ROW_FIRST + index * ROW_STRIDE

    private fun hover(component: Int): Pair<List<Int>, List<Int>> =
        listOf(PohTeleportInterfaceImportTool.COLOUR_SCRIPT, component, WHITE) to listOf(PohTeleportInterfaceImportTool.COLOUR_SCRIPT, component, ORANGE)

    private fun Builder.button(
        id: Int,
        x: Int,
        w: Int,
        label: String,
        hidden: Boolean = false,
    ) {
        layer(id, x, 0, w, BUTTON_H, CONTENT, hidden = hidden, ops = if (hidden) emptyList() else listOf(label))
        listOf(LOOK_OUT to STONE_OUT, LOOK_IN to STONE_IN).forEach { (offset, pieces) ->
            val look = id + offset
            layer(look, 0, 0, w, BUTTON_H, id, hidden = offset == LOOK_IN)
            stoneButton(look + 1, w, BUTTON_H, look, pieces)
            text(look + LOOK_LABEL, 0, 0, w, BUTTON_H, look, fonts.p11, label, colour = BUTTON_TEXT)
        }
    }

    fun build(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.frame(WIDTH, HEIGHT, "Staff Commands")
        b.layer(CONTENT, CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H, DeathsOfficeInterfaceImportTool.WINDOW)
        TABS.forEachIndexed { i, id ->
            val label = TAB_LABELS.getOrNull(i)
            b.button(id, if (label != null) i * TAB_STEP else 0, TAB_W, label ?: "", hidden = label == null)
        }
        b.button(SEARCH, SEARCH_X, SEARCH_W, "Search")
        b.button(CLEAR, CLEAR_X, CLEAR_W, "Clear", hidden = true)
        b.text(STATUS, 2, STATUS_Y, CONTENT_W / 2, STATUS_H, CONTENT, fonts.p11, "", colour = WHITE, alignX = 0)
        b.text(HINT, CONTENT_W / 2, STATUS_Y, CONTENT_W / 2 - 2, STATUS_H, CONTENT, fonts.p11, "Click: run it. Right-click: Info. Search: every command.", colour = LIGHT, alignX = 2)
        b.text(HEAD_COMMAND, 6, HEAD_Y, NAME_W - 6, HEAD_H, CONTENT, fonts.p11, "Command / rank", colour = HEAD, alignX = 0)
        b.text(HEAD_DESCRIPTION, NAME_W + 1, HEAD_Y, SCROLL_W - NAME_W, HEAD_H, CONTENT, fonts.p11, "What it does", colour = HEAD, alignX = 0)
        b.layer(LIST, 0, LIST_Y, LIST_W, LIST_H, CONTENT)
        b.rect(OUTLINE, 0, 0, LIST_W, LIST_H, LIST, 0x000000)
        b.layer(SCROLL, 1, 1, SCROLL_W, SCROLL_H, LIST)
        b.scrollbarFor(SCROLLBAR, LIST_W - 16 - 1, 1, SCROLL_H, LIST, INTERFACE_ID, SCROLL)
        for (r in 0 until ROWS) {
            val row = row(r)
            val (over, leave) = hover((INTERFACE_ID shl 16) or (row + ROW_NAME))
            b.add(Comp(row, 0, 0, r * ROW_HEIGHT, SCROLL_W, ROW_HEIGHT, SCROLL, hidden = true, ops = ROW_OPS, onMouseOver = over, onMouseLeave = leave))
            b.add(Comp(row + ROW_BACKING, 3, 0, 0, SCROLL_W, ROW_HEIGHT, row, colour = 0x000000, filled = true, transparency = 225, hidden = r % 2 == 0))
            b.text(row + ROW_NAME, 5, 1, NAME_W - 5, 15, row, fonts.p12, "", alignX = 0)
            b.text(row + ROW_RANK, 5, 15, NAME_W - 5, 14, row, fonts.p11, "", colour = WHITE, alignX = 0)
            b.text(row + ROW_DESCRIPTION, NAME_W, 0, SCROLL_W - NAME_W - 4, ROW_HEIGHT, row, fonts.p11, "", colour = LIGHT, alignX = 0)
        }
        return b.done(COUNT)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val previous = args.firstOrNull { it.startsWith("--replace-from-journal=") }?.substringAfter('=')?.let(LootKeyInterfaceImportTool::journalIntendedSha1s) ?: emptyMap()
        val fonts: LootKeyInterfaceImportTool.Fonts
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            fonts = LootKeyInterfaceImportTool.fonts(library)
            require(library.index(12).archive(PohTeleportInterfaceImportTool.COLOUR_SCRIPT) != null) {
                "clientscript ${PohTeleportInterfaceImportTool.COLOUR_SCRIPT} (row highlight) missing - run PohTeleportInterfaceImportTool first"
            }
            if (previous.isEmpty()) require(library.index(INDEX_INTERFACES).archive(INTERFACE_ID) == null) { "interface $INTERFACE_ID already exists" }
        } finally {
            library.close()
        }
        val mutations =
            build(fonts).map { c ->
                CacheMutation(
                    INDEX_INTERFACES, INTERFACE_ID, c.id, DeathsOfficeInterfaceImportTool.encode(c), "interface $INTERFACE_ID:${c.id} type=${c.type}",
                    expectedCurrentSha1 = previous["idx${INDEX_INTERFACES}_grp${INTERFACE_ID}_file${c.id}"],
                )
            }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interface $INTERFACE_ID ($COUNT components)")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }
}
