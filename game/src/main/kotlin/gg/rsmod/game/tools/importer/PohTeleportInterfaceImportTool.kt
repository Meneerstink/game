package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Builder
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Comp
import java.io.ByteArrayOutputStream

/**
 * The house teleport screens (owner bug 2026-10-06 183223: "scrying portal has no teleport interfaces"; owner 2026-10-02: house
 * options are interfaces, not chat menus), built as revision-667 if3 interfaces through the same transactional, both-caches route
 * and the same OSRS steelborder pieces as [DeathsOfficeInterfaceImportTool]:
 *
 *  - [NEXUS] the OSRS Portal Nexus "Teleport Menu" (OSRS 17, `telenexus_teleport`), opened by the house's nexus (the cache's own
 *    Scrying pool 13639). Window 492x314 as the OSRS Wiki screenshot `Portal_Nexus_teleport_interface.png`; content layer 17:2 at
 *    y 40, 20 narrower and 50 shorter than the window; the destination list 17:6 at 20,8 (208 narrower, 18 shorter) with a black
 *    outline 17:9, a scroll layer 17:11 (1 in, 18 narrower) and a 16 px scrollbar 17:14; the nexus model 17:3 on the right (5 in,
 *    32.45% of the width, 78 shorter). Rows from clientscript 2675: 24 px high, the destination's spell icon 24x24 at x 5, the
 *    name at x 34 in p12_full orange 0xFF981F, left-aligned and shadowed, prefixed "<col=ffffff>key</col> :  " for the first 35
 *    rows (keys 1-9, A-Z), every second row on a black backing at transparency 225, and the name turning white under the mouse
 *    (cc_colour_swapper). OSRS's Teleport Mode / Scry Mode box (17:5) is not built: this server has no scrying.
 *  - [JEWELLERY_BOX] the OSRS house jewellery box (OSRS 590, `poh_jewellery_box`): window 475x333 (OSRS 590:0, 475 wide, the
 *    modal's height less 1), six boxes laid out by clientscript 1685 (two columns 225 wide, rows 5:6:4 of the free height), each
 *    on tradebacking_light with a thinbox outline, its jewellery name (p11_full white, 70x30 at x 5) and the charged item below
 *    it, and its destinations (clientscript 1688: p11_full orange centred text 80 narrower than the box at its right edge, rows
 *    of min((height - 10) / n, 25) px, key prefix "<col=ccccff>key:</col> ", op 1 = the destination name).
 *
 * The OSRS screens draw their rows with clientscripts at runtime; here they are static components the server fills with
 * IF_SETTEXT / IF_SETGRAPHIC / IF_SETOBJECT / IF_SETHIDE. The highlight is [COLOUR_SCRIPT], a new four-instruction clientscript
 * (IF_SETCOLOUR on a component) bound as the row's onMouseOver / onMouseLeave hook.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.PohTeleportInterfaceImportTool [--apply] [--replace-from-journal=<tx>]`
 * with the servers stopped. The tool owns interfaces [NEXUS] / [JEWELLERY_BOX] and clientscript [COLOUR_SCRIPT].
 */
object PohTeleportInterfaceImportTool {
    const val NEXUS = 1178
    const val JEWELLERY_BOX = 1179

    /** The first clientscript id above the cache's last one (5378, probe 2026-10-07). */
    const val COLOUR_SCRIPT = 5379

    const val INDEX_INTERFACES = 3
    const val INDEX_CLIENTSCRIPTS = 12

    const val ORANGE = 0xFF981F
    const val WHITE = 0xFFFFFF

    /**
     * The house nexus loc's model: the OSRS crystalline Portal Nexus (OSRS model 36052 -> local 4905, OsrsLocImportTool batch
     * "poh-house", owner 2026-10-10); it was the Scrying pool 13639's 13192 while that loc stood in for the nexus.
     */
    const val NEXUS_MODEL_ID = 4905

    /** Key labels of the first 35 rows / buttons, OSRS enum 1401: 1-9 then A-Z. */
    val KEYS: List<String> = (1..9).map { it.toString() } + ('A'..'Z').map { it.toString() }

    // ---- 1178 Portal Nexus ----
    object Nexus {
        const val WIDTH = 492
        const val HEIGHT = 314
        const val CONTENT = 14
        const val LIST = 15
        const val OUTLINE = 16
        const val SCROLL = 17
        const val SCROLLBAR = 18
        const val MODEL = 19
        const val ROW_FIRST = 20

        /** Inside a row (a layer carrying the op and the highlight): +1 backing, +2 item icon, +3 spell icon, +4 name. */
        const val ROW_STRIDE = 5
        const val ROW_BACKING = 1
        const val ROW_ITEM = 2
        const val ROW_SPELL = 3
        const val ROW_NAME = 4

        /** Enough for every directory leaf (67 on 2026-10-07; guarded by a test). */
        const val ROWS = 80
        const val COUNT = ROW_FIRST + ROWS * ROW_STRIDE

        const val CONTENT_X = 10
        const val CONTENT_Y = 40
        const val CONTENT_W = WIDTH - 20
        const val CONTENT_H = HEIGHT - 50
        const val LIST_X = 20
        const val LIST_Y = 8
        const val LIST_W = CONTENT_W - 208
        const val LIST_H = CONTENT_H - 18
        const val SCROLL_W = LIST_W - 18
        const val SCROLL_H = LIST_H - 2
        const val ROW_HEIGHT = 24

        /** The item icon keeps the 36x32 item sprite's aspect at the row height. */
        const val ITEM_ICON_W = 27

        fun row(index: Int): Int = ROW_FIRST + index * ROW_STRIDE
    }

    // ---- 1179 Jewellery box ----
    object Box {
        const val WIDTH = 475
        const val HEIGHT = 333
        const val BOX_FIRST = 14

        /** Inside a box: +0 layer, +1 backing, +2/+3 thinbox, +4 title, +5 item, +6.. buttons. */
        const val BOX_STRIDE = 6 + 6
        const val BOX_BACKING = 1
        const val BOX_TITLE = 4
        const val BOX_ITEM = 5
        const val BOX_BUTTON_FIRST = 6
        const val MAX_BUTTONS = 6
        const val COUNT = BOX_FIRST + 6 * BOX_STRIDE

        fun box(index: Int): Int = BOX_FIRST + index * BOX_STRIDE

        fun button(
            box: Int,
            index: Int,
        ): Int = box(box) + BOX_BUTTON_FIRST + index
    }

    /**
     * The six boxes in OSRS order (clientscript 1685: dueling, games | combat, skills | wealth, glory) with this server's own
     * destinations of each item (the jewellery handlers' tiles, `PohTeleports` "Jewellery"). The names are the button texts and
     * their op; the server matches them against its directory by name (test `PohTeleportUiTests`).
     */
    class JewelleryBox(val title: String, val branch: String, val item: Int, val destinations: List<String>)

    val BOXES =
        listOf(
            JewelleryBox("Ring of Dueling", "Ring of dueling", 2552, listOf("Duel Arena", "Castle Wars", "Mobilising Armies", "Fist of Guthix")),
            JewelleryBox("Games Necklace", "Games necklace", 3853, listOf("Burthorpe", "Barbarian Outpost", "Gamers' Grotto", "Corporeal Beast")),
            JewelleryBox("Combat bracelet", "Combat bracelet", 11118, listOf("Warriors' Guild", "Champions' Guild", "Monastery", "Ranging Guild")),
            JewelleryBox("Skills necklace", "Skills necklace", 11105, listOf("Fishing Guild", "Mining Guild", "Crafting Guild", "Cooking Guild")),
            JewelleryBox("Ring of Wealth", "Ring of wealth", 20659, listOf("Miscellania", "Grand Exchange")),
            JewelleryBox("Amulet of Glory", "Amulet of glory", 1712, listOf("Edgeville", "Karamja", "Draynor Village", "Al Kharid")),
        )

    class BoxGeometry(val x: Int, val y: Int, val w: Int, val h: Int)

    /** clientscript 1685 for a 475x333 window: two 225 px columns, heights 5:6:4 of the free 273 px (scale = a * c / b). */
    fun boxGeometry(index: Int): BoxGeometry {
        val w = (Box.WIDTH - 10 - 10 - 5) / 2
        val free = Box.HEIGHT - 40 - 10 - 5 - 5
        val h0 = 5 * free / 15
        val h1 = 6 * free / 15
        val h2 = 4 * free / 15
        val x = if (index % 2 == 0) 10 else 10 + w + 5
        return when (index / 2) {
            0 -> BoxGeometry(x, 40, w, h0)
            1 -> BoxGeometry(x, 40 + h0 + 5, w, h1)
            else -> BoxGeometry(x, 40 + h0 + h1 + 5 + 5, w, h2)
        }
    }

    /** poh_jewellerybox_getbuttonspacing: (first centre offset, spacing) for [count] buttons in a box [height] high. */
    fun buttonSpacing(
        height: Int,
        count: Int,
    ): Pair<Int, Int> {
        val n = maxOf(count, 1)
        val spacing = minOf((height - 10) / n, 25)
        var start = 0 - spacing * (n / 2)
        if (n % 2 == 0) start += spacing / 2
        return start to spacing
    }

    private fun hover(component: Int): Pair<List<Int>, List<Int>> =
        listOf(COLOUR_SCRIPT, component, WHITE) to listOf(COLOUR_SCRIPT, component, ORANGE)

    fun nexus(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val n = Nexus
        val b = Builder(fonts)
        b.frame(n.WIDTH, n.HEIGHT, "Portal Nexus")
        b.layer(n.CONTENT, n.CONTENT_X, n.CONTENT_Y, n.CONTENT_W, n.CONTENT_H, DeathsOfficeInterfaceImportTool.WINDOW)
        b.layer(n.LIST, n.LIST_X, n.LIST_Y, n.LIST_W, n.LIST_H, n.CONTENT)
        b.rect(n.OUTLINE, 0, 0, n.LIST_W, n.LIST_H, n.LIST, 0x000000)
        b.layer(n.SCROLL, 1, 1, n.SCROLL_W, n.SCROLL_H, n.LIST)
        b.scrollbarFor(n.SCROLLBAR, n.LIST_W - 16 - 1, 1, n.SCROLL_H, n.LIST, NEXUS, n.SCROLL)
        val modelW = n.CONTENT_W * 5317 / 16384
        b.add(
            Comp(
                n.MODEL, 6, n.CONTENT_W - 5 - modelW, 0, modelW, n.CONTENT_H - 78, n.CONTENT,
                model = NEXUS_MODEL_ID, xan = 200, yan = 0, zan = 0, zoom = 1100,
            ),
        )
        for (r in 0 until n.ROWS) {
            val row = n.row(r)
            val name = row + n.ROW_NAME
            val (over, leave) = hover((NEXUS shl 16) or name)
            b.add(
                Comp(
                    row, 0, 0, r * n.ROW_HEIGHT, n.SCROLL_W, n.ROW_HEIGHT, n.SCROLL, hidden = true,
                    ops = listOf("Teleport"), onMouseOver = over, onMouseLeave = leave,
                ),
            )
            // Every second row on black at transparency 225 (clientscript 2675; the others are white at 255 = not drawn).
            b.add(Comp(row + n.ROW_BACKING, 3, 0, 0, n.SCROLL_W, n.ROW_HEIGHT, row, colour = 0x000000, filled = true, transparency = 225, hidden = r % 2 == 0))
            b.add(Comp(row + n.ROW_ITEM, 5, 3, 0, n.ITEM_ICON_W, n.ROW_HEIGHT, row, hidden = true, graphic = -1, shadow = 0x333333))
            b.graphic(row + n.ROW_SPELL, 5, 0, n.ROW_HEIGHT, n.ROW_HEIGHT, row, -1)
            b.text(row + n.ROW_NAME, n.ROW_HEIGHT + 10, 0, n.SCROLL_W - n.ROW_HEIGHT - 10, n.ROW_HEIGHT, row, fonts.p12, "", alignX = 0)
        }
        return b.done(n.COUNT)
    }

    fun jewelleryBox(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val x = Box
        val b = Builder(fonts)
        b.frame(x.WIDTH, x.HEIGHT, "Jewellery Box")
        var key = 0
        BOXES.forEachIndexed { i, box ->
            val g = boxGeometry(i)
            val base = x.box(i)
            b.layer(base, g.x, g.y, g.w, g.h, DeathsOfficeInterfaceImportTool.WINDOW)
            b.graphic(base + x.BOX_BACKING, 1, 1, g.w - 2, g.h - 2, base, DeathsOfficeInterfaceImportTool.sprite(1040), tiling = true)
            b.rect(base + 2, 0, 0, g.w, g.h, base, DeathsOfficeInterfaceImportTool.THINBOX_OUTER)
            b.rect(base + 3, 1, 1, g.w - 2, g.h - 2, base, DeathsOfficeInterfaceImportTool.THINBOX_INNER)
            b.text(base + x.BOX_TITLE, 5, (g.h - 30) / 2 + 20, 70, 30, base, fonts.p11, box.title, colour = WHITE)
            b.add(Comp(base + x.BOX_ITEM, 5, 7 + (70 - 36) / 2, (g.h - 32) / 2 - 10, 36, 32, base, graphic = -1, outline = 1, shadow = 0x333333))
            val (start, spacing) = buttonSpacing(g.h, box.destinations.size)
            for (d in 0 until x.MAX_BUTTONS) {
                val id = x.button(i, d)
                val destination = box.destinations.getOrNull(d)
                val y = (g.h - spacing) / 2 + start + d * spacing
                val label = destination?.let { "<col=ccccff>${KEYS[key++]}:</col> $it" } ?: ""
                val (over, leave) = hover(DeathsOfficeInterfaceImportTool.EVENT_COM)
                b.add(
                    Comp(
                        id, 4, 75, y, g.w - 80, spacing, base, hidden = destination == null, font = fonts.p11, text = label,
                        colour = ORANGE, ops = listOfNotNull(destination), onMouseOver = if (destination == null) emptyList() else over,
                        onMouseLeave = if (destination == null) emptyList() else leave,
                    ),
                )
            }
        }
        return b.done(x.COUNT)
    }

    /**
     * [COLOUR_SCRIPT](component, colour): IF_SETCOLOUR(colour, component) - the OSRS `cc_colour_swapper` for one static
     * component. Same encoding as [DeathsOfficeInterfaceImportTool.scrollScript] (IF_ variants are the CC_ ones + 1000 and pop
     * their component last).
     */
    fun colourScript(): ByteArray {
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
        val pushLocal = 33
        val ret = 21
        out.write(0) // no name
        op(pushLocal, 1) // colour
        op(pushLocal, 0) // component
        op(2101, 0, byteOperand = true) // IF_SETCOLOUR
        op(ret, 0, byteOperand = true)
        i32(count)
        u16(2) // int locals
        u16(0)
        u16(0)
        u16(2) // int arguments
        u16(0)
        u16(0)
        out.write(0) // switch table count
        u16(1) // switch section length (the count byte)
        return out.toByteArray()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val previous = args.firstOrNull { it.startsWith("--replace-from-journal=") }?.substringAfter('=')?.let(LootKeyInterfaceImportTool::journalIntendedSha1s) ?: emptyMap()
        val fonts: LootKeyInterfaceImportTool.Fonts
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            fonts = LootKeyInterfaceImportTool.fonts(library)
            if (previous.isEmpty()) {
                require(library.index(INDEX_CLIENTSCRIPTS).archive(COLOUR_SCRIPT) == null) { "clientscript $COLOUR_SCRIPT already exists" }
                listOf(NEXUS, JEWELLERY_BOX).forEach { require(library.index(INDEX_INTERFACES).archive(it) == null) { "interface $it already exists" } }
            }
        } finally {
            library.close()
        }
        val mutations = mutableListOf<CacheMutation>()
        mutations +=
            CacheMutation(
                INDEX_CLIENTSCRIPTS, COLOUR_SCRIPT, 0, colourScript(), "clientscript $COLOUR_SCRIPT (text highlight)",
                expectedCurrentSha1 = previous["idx${INDEX_CLIENTSCRIPTS}_grp${COLOUR_SCRIPT}_file0"],
            )
        listOf(NEXUS to nexus(fonts), JEWELLERY_BOX to jewelleryBox(fonts)).forEach { (interfaceId, comps) ->
            comps.forEach { c ->
                mutations +=
                    CacheMutation(
                        INDEX_INTERFACES, interfaceId, c.id, DeathsOfficeInterfaceImportTool.encode(c), "interface $interfaceId:${c.id} type=${c.type}",
                        expectedCurrentSha1 = previous["idx${INDEX_INTERFACES}_grp${interfaceId}_file${c.id}"],
                    )
            }
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interfaces $NEXUS / $JEWELLERY_BOX and clientscript $COLOUR_SCRIPT")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }
}
