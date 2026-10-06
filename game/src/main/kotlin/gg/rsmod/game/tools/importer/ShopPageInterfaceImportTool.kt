package gg.rsmod.game.tools.importer

import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool.Component

/**
 * Previous / Next page buttons and a "Page n / m" label on the 667 shop window (interface [INTERFACE_ID] = 620), for shops that
 * are split over several shop pages (owner 2026-10-06, bug 204956 / OWNER_QUESTIONS Q2 = A: "Trade opens the shop directly on
 * page 1; the shop gets Previous/Next page buttons like the Loyalty/Donator store screen"). The server shows the row only while the
 * open shop belongs to a page group (`ShopPageGroups`); the components are baked hidden, so every other shop on the server keeps
 * the exact 667 window.
 *
 * Look: the 78 Store's own page row ([StoreInterfaceImportTool] PAGE_PREVIOUS / PAGE_TEXT / PAGE_NEXT): plain text buttons
 * "Prev page" / "Next page" in [StoreInterfaceImportTool.GOLD] and the label in [StoreInterfaceImportTool.CREAM], font p12, shadowed,
 * with the same "Previous page" / "Next page" ops. The store uses no sprites for them, so none are added here.
 *
 * Placement (decoded from both caches 2026-10-06 with `InterfaceHookProbeTool layout 620` and the component headers, see
 * [EXISTING_WINDOW_SHA1]): the window is layer 620:0 (506x321, centred in the 512x334 main screen, so window = viewport - (3, 6)).
 * Rows, in window coordinates:
 * - y 4..21: title 620:20 (b12, centred in x 40..466; the longest page title "Pikkupstix's Pouches (levels 70-99)" is 239 px), close
 *   620:18 at x 472..488;
 * - y 22: top border of the inner panel (rectangle 620:9 and backgrounds 620:6/7/8 from y 22);
 * - y 24..41: the headings row - "Free sample items" 620:21 (b12, left-aligned at x 20: 119 px -> x 20..139) and "Key" 620:23
 *   (centred in x 435..483: 24 px -> x 447..471), both shown by CS2 2717 / 2726 only for a shop with free samples, hidden otherwise;
 * - y 40..295: the stock (620:25 / samples 620:26 / scrollbar 620:32 are root-level at viewport y 46, moved by CS2 2718 / 2719);
 * - y 294..307: the help line 620:24 (p11, centred, 400 px of text in its 400 px box - the bottom row is full).
 * The only free strip is the middle of the headings row: [NAV_X] 153..353 (centred on the window like the title), [NAV_Y] 23..39 -
 * 14 px right of "Free sample items", 94 px left of "Key", 1 px above the stock area, below the title row. Measured text widths
 * (cache font metrics 13/495): "Prev page" 57, "Next page" 58, "Page 10 / 10" 71 px - each fits its box.
 *
 * Client scripts: 620 has no onLoad. Its only hooks are onVarTransmit 2716 (stock, varp 118) and 2717 (samples, varp 1496), which
 * call 2723 / 2726 / 2718 / 2719: they rebuild CC children inside 620:25 / 620:26 / 620:32, move/resize 620:25 and 620:32, and
 * show 620:21 / 22 / 23 / 26..31. Script 2001 / 2723 use 620:34 (the price tooltip). No script in the cache references 620:35+
 * (`InterfaceHookProbeTool refs`), and CC_DELETEALL only removes dynamic children, so the appended static components are never
 * hidden, moved or deleted by the client.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.ShopPageInterfaceImportTool [--apply]` (or
 * `./gradlew :game:runShopPageInterfaceImportTool --args="[--apply]"`). Refuses unless both caches hold exactly the decoded 667
 * window ([EXISTING_WINDOW_SHA1]); a re-run replaces only its own components 35..38 (pinned to what is there). Like every cache
 * write it is refused while the servers run.
 */
object ShopPageInterfaceImportTool {
    const val INTERFACE_ID = 620

    /** Components of the 667 shop window (0..34). */
    const val EXISTING_COMPONENTS = 35

    /** SHA-1 over 620:0..34 in file order, identical in both caches on 2026-10-06 - the window the placement was measured on. */
    const val EXISTING_WINDOW_SHA1 = "fe4f00095010a5cece5a3b6e5e4247871dc4e06e"

    /** The window layer every row above hangs off. */
    const val WINDOW = 0
    const val WINDOW_WIDTH = 506
    const val WINDOW_HEIGHT = 321

    /** Hidden row layer (shown by the server for a page-group shop) and its three texts. */
    const val NAV = 35
    const val PAGE_PREVIOUS = 36
    const val PAGE_TEXT = 37
    const val PAGE_NEXT = 38
    const val COMPONENT_COUNT = 39

    const val NAV_WIDTH = 200
    const val NAV_HEIGHT = 16
    const val NAV_X = (WINDOW_WIDTH - NAV_WIDTH) / 2 // 153
    const val NAV_Y = 23
    const val BUTTON_WIDTH = 60
    const val LABEL_WIDTH = NAV_WIDTH - 2 * BUTTON_WIDTH // 80

    private const val TYPE_LAYER = 0
    private const val TYPE_TEXT = 4

    fun components(fonts: LootKeyInterfaceImportTool.Fonts): List<Component> {
        fun text(id: Int, x: Int, w: Int, text: String, colour: Int, alignX: Int, ops: List<String> = emptyList()) =
            Component(id, TYPE_TEXT, x, 0, w, NAV_HEIGHT, NAV, font = fonts.p12, text = text, colour = colour, alignX = alignX, alignY = 1, shadow = true, ops = ops)

        val list =
            listOf(
                Component(NAV, TYPE_LAYER, NAV_X, NAV_Y, NAV_WIDTH, NAV_HEIGHT, WINDOW, hidden = true),
                // Plain words, no "<": the client parses "<" as a text tag (78 Store, owner 2026-09-29).
                text(PAGE_PREVIOUS, 0, BUTTON_WIDTH, "Prev page", StoreInterfaceImportTool.GOLD, alignX = 0, ops = listOf("Previous page")),
                text(PAGE_TEXT, BUTTON_WIDTH, LABEL_WIDTH, "Page 1 / 1", StoreInterfaceImportTool.CREAM, alignX = 1),
                text(PAGE_NEXT, BUTTON_WIDTH + LABEL_WIDTH, BUTTON_WIDTH, "Next page", StoreInterfaceImportTool.GOLD, alignX = 2, ops = listOf("Next page")),
            )
        require(list.map { it.id } == (EXISTING_COMPONENTS until COMPONENT_COUNT).toList()) { "component ids must be $EXISTING_COMPONENTS..${COMPONENT_COUNT - 1}" }
        return list
    }

    /** The common header of a 667 component file (version 255), as `Component.decode` reads it. */
    class Header(
        val type: Int,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val resizeX: Int,
        val resizeY: Int,
        val reposX: Int,
        val reposY: Int,
        val parent: Int,
        val hidden: Boolean,
        /** Offset of the first type-specific byte (a text component's font id). */
        val end: Int,
    )

    fun decodeHeader(bytes: ByteArray): Header {
        val b = java.nio.ByteBuffer.wrap(bytes)
        require(b.get().toInt() and 0xFF == 255) { "not a version-255 component" }
        var type = b.get().toInt() and 0xFF
        if (type and 0x80 != 0) {
            type = type and 0x7F
            while (b.get().toInt() != 0) Unit // name
        }
        b.short // clientcode
        val x = b.short.toInt()
        val y = b.short.toInt()
        val w = b.short.toInt() and 0xFFFF
        val h = b.short.toInt() and 0xFFFF
        val resizeX = b.get().toInt()
        val resizeY = b.get().toInt()
        val reposX = b.get().toInt()
        val reposY = b.get().toInt()
        val parent = (b.short.toInt() and 0xFFFF).let { if (it == 65535) -1 else it }
        val hidden = b.get().toInt() and 1 != 0
        return Header(type, x, y, w, h, resizeX, resizeY, reposX, reposY, parent, hidden, b.position())
    }

    /** A box in window coordinates: x, y, width, height. */
    data class Box(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun intersects(o: Box) = x < o.x + o.width && o.x < x + width && y < o.y + o.height && o.y < y + height
    }

    /**
     * [h]'s box in window coordinates. Window children (parent 0) are laid out in the 506x321 window; root-level components
     * (parent -1) in the 512x334 main screen, whose window origin is (3, 6). Only the size/position modes 620 uses (0 absolute,
     * 1 minus/centre, 2 right/bottom) are resolved.
     */
    fun windowBox(h: Header): Box {
        val root = h.parent < 0
        val parentW = if (root) 512 else WINDOW_WIDTH
        val parentH = if (root) 334 else WINDOW_HEIGHT
        fun size(mode: Int, v: Int, p: Int) = when (mode) { 0 -> v; 1 -> p - v; else -> error("size mode $mode") }
        fun pos(mode: Int, v: Int, s: Int, p: Int) = when (mode) { 0 -> v; 1 -> (p - s) / 2 + v; 2 -> p - s - v; else -> error("position mode $mode") }
        val w = size(h.resizeX, h.width, parentW)
        val ht = size(h.resizeY, h.height, parentH)
        val x = pos(h.reposX, h.x, w, parentW)
        val y = pos(h.reposY, h.y, ht, parentH)
        return if (root) Box(x - (512 - WINDOW_WIDTH) / 2, y - (334 - WINDOW_HEIGHT) / 2, w, ht) else Box(x, y, w, ht)
    }

    /** SHA-1 over [files] in order. */
    fun sha1(files: List<ByteArray>): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        files.forEach { digest.update(it) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val targets = LootKeyInterfaceImportTool.TARGETS
        val index = LootKeyInterfaceImportTool.INDEX_INTERFACES
        var fonts: LootKeyInterfaceImportTool.Fonts? = null
        val pinned = mutableMapOf<Int, String>()
        targets.forEachIndexed { t, path ->
            val library = com.displee.cache.CacheLibrary(path)
            try {
                val ids = library.index(index).archive(INTERFACE_ID)?.fileIds()?.toList() ?: error("$path: interface $INTERFACE_ID missing")
                require(ids == (0 until EXISTING_COMPONENTS).toList() || ids == (0 until COMPONENT_COUNT).toList()) {
                    "$path: interface $INTERFACE_ID has files $ids - not the 667 shop window (0..${EXISTING_COMPONENTS - 1}) or this tool's layout, refusing"
                }
                val window = (0 until EXISTING_COMPONENTS).map { library.data(index, INTERFACE_ID, it) ?: error("$path: $INTERFACE_ID:$it unreadable") }
                val sha = sha1(window)
                require(sha == EXISTING_WINDOW_SHA1) { "$path: interface $INTERFACE_ID:0..${EXISTING_COMPONENTS - 1} changed since it was measured (sha1 $sha), refusing" }
                // Our own earlier components may be replaced; both caches must hold the same bytes (one pin per mutation).
                for (id in EXISTING_COMPONENTS until COMPONENT_COUNT) {
                    val current = library.data(index, INTERFACE_ID, id)?.let(CacheItemProbeTool::sha1) ?: continue
                    if (t == 0) pinned[id] = current else require(pinned[id] == current) { "$path: $INTERFACE_ID:$id differs from ${targets[0]}, refusing" }
                }
                if (t == 0) fonts = LootKeyInterfaceImportTool.fonts(library)
            } finally {
                library.close()
            }
        }
        val built = components(fonts!!)
        built.forEach { c -> println("  $INTERFACE_ID:${c.id} type=${c.type} box=${c.x},${c.y} ${c.width}x${c.height} parent=${c.parent} text='${c.text}' ops=${c.ops}") }
        val mutations =
            built.map { c ->
                CacheMutation(index, INTERFACE_ID, c.id, LootKeyInterfaceImportTool.encode(c), "interface $INTERFACE_ID:${c.id} shop page row", expectedCurrentSha1 = pinned[c.id])
            }
        val transaction = CacheTransaction(targets = targets, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write $INTERFACE_ID:$EXISTING_COMPONENTS..${COMPONENT_COUNT - 1}")
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
