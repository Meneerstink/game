package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * E6 (owner 2026-10-03/04): the Grand Exchange "Trade History" screen (interface 643) shows all 20 trades the server keeps
 * (OSRS Wiki, Grand Exchange changes 9 March 2017: "The Grand Exchange 'History' tab now displays up to 20 of your most
 * recent trades"), in a scrollable list like OSRS. The 667 screen has five fixed rows of root-level components (probe of
 * the 667 cache 2026-10-04: type 25..29, quantity 30..34, item name 35..39, price 40..44, separator lines 45..50 - sprite
 * 1074 is a 2 px line at y 16 of its 32 px tile - all children of the root, no layer), so this tool rebuilds the row area
 * as a scroll layer through the same transactional, both-caches route as [StoreInterfaceImportTool]:
 *
 *  - [LIST] a layer at the old row area (below the header line 643:15, above the bottom border) with its scrollbar
 *    [SCROLLBAR] (667 clientscript 30 on load, exactly like [DeathsOfficeInterfaceImportTool]); the server sets the scroll
 *    height with [DeathsOfficeInterfaceImportTool.SCROLL_SCRIPT] (5377, already in both caches);
 *  - 20 rows of [ROW_HEIGHT] px: type / item name / quantity / price texts and a separator line, every one a byte copy of
 *    the 667 row-0 component (font 494, colour, alignment) with only position, size and parent changed - the existing
 *    rows 0..4 keep their ids, rows 5..19 are new ids;
 *  - one transparent hit layer per row ([hit]) that carries the row's "Buy-offer" / "Examine" (the 667 separators only
 *    covered the top half of a row);
 *  - the "Quantity" / "Price" headers (643:23 / 643:24) moved over their columns, which make room for the scrollbar.
 *
 * Components 0..22 (frame, title, close button, header bar and the other headers) are not touched.
 *
 * The server picks the layout by the component count of 643 in its cache (`GrandExchangeHistory.layout`), so a build that
 * runs against a cache without this tool keeps driving the five 667 rows.
 *
 * Usage: `./gradlew :game:runGeHistoryInterfaceImportTool --args="[--apply]"` (or `java -cp <game lib>
 * gg.rsmod.game.tools.importer.GeHistoryInterfaceImportTool [--apply]`) with the servers stopped. Without `--apply` it only
 * prints the preflight. A re-run is a NO_OP (every component is rebuilt from the row-0 templates, which it only moves).
 */
object GeHistoryInterfaceImportTool {
    const val INTERFACE_ID = 643
    const val INDEX_INTERFACES = 3

    /** The 667 screen: components 0..50. */
    const val LEGACY_COMPONENT_COUNT = 51

    /** OSRS keeps (and shows) 20 trades. */
    const val ROWS = 20
    const val LEGACY_ROWS = 5
    const val VISIBLE_ROWS = 5
    const val ROW_HEIGHT = 40

    // ---- the 667 components this tool reuses ----
    const val LEGACY_TYPE_FIRST = 25
    const val LEGACY_AMOUNT_FIRST = 30
    const val LEGACY_NAME_FIRST = 35
    const val LEGACY_PRICE_FIRST = 40

    /** 45..50: six separator lines (667 rows 0..4 used 45..49 as their op carriers). */
    const val LEGACY_SEPARATOR_FIRST = 45
    const val LEGACY_SEPARATORS = 6
    const val HEADER_QUANTITY = 23
    const val HEADER_PRICE = 24

    // ---- new components ----
    const val LIST = 51
    const val SCROLLBAR = 52
    const val TYPE_NEW_FIRST = 53 // rows 5..19 -> 53..67
    const val AMOUNT_NEW_FIRST = TYPE_NEW_FIRST + (ROWS - LEGACY_ROWS) // 68..82
    const val NAME_NEW_FIRST = AMOUNT_NEW_FIRST + (ROWS - LEGACY_ROWS) // 83..97
    const val PRICE_NEW_FIRST = NAME_NEW_FIRST + (ROWS - LEGACY_ROWS) // 98..112
    const val SEPARATOR_NEW_FIRST = PRICE_NEW_FIRST + (ROWS - LEGACY_ROWS) // rows 6..19 -> 113..126
    const val HIT_FIRST = SEPARATOR_NEW_FIRST + (ROWS - LEGACY_SEPARATORS) // 127..146
    const val COMPONENT_COUNT = HIT_FIRST + ROWS // 147

    // ---- geometry (643 is a 512x334 main-screen window; frame inner area x 17..505) ----
    /** Below the header bar's line (643:15, sprite 851: 3 px at y 84 + 13). */
    const val LIST_X = 19
    const val LIST_Y = 101
    const val SCROLLBAR_WIDTH = 16

    /** 19..477, the scrollbar 477..493 inside the right border (643:4 sprite 823 at x 480). */
    const val LIST_WIDTH = 458
    const val LIST_HEIGHT = VISIBLE_ROWS * ROW_HEIGHT

    /** 667 scrollbar builder: script 30(scrollbar, layer). */
    const val SCROLLBAR_SCRIPT = DeathsOfficeInterfaceImportTool.SCROLLBAR_SCRIPT
    const val EVENT_COM = DeathsOfficeInterfaceImportTool.EVENT_COM

    private const val TYPE_LAYER = 0
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5

    /** One text column: the row-0 component it copies, its x inside [LIST] and its width. */
    class Column(val template: Int, val x: Int, val width: Int)

    /** 667 columns kept (type x 25 w 95, item name x 140 w 140); quantity and price move left 10 / 17 px for the scrollbar. */
    val TYPE_COLUMN = Column(LEGACY_TYPE_FIRST, 25 - LIST_X, 95)
    val NAME_COLUMN = Column(LEGACY_NAME_FIRST, 140 - LIST_X, 140)
    val AMOUNT_COLUMN = Column(LEGACY_AMOUNT_FIRST, 278 - LIST_X, 90)
    val PRICE_COLUMN = Column(LEGACY_PRICE_FIRST, 370 - LIST_X, LIST_WIDTH - (370 - LIST_X) - 1)

    fun type(row: Int): Int = if (row < LEGACY_ROWS) LEGACY_TYPE_FIRST + row else TYPE_NEW_FIRST + row - LEGACY_ROWS

    fun amount(row: Int): Int = if (row < LEGACY_ROWS) LEGACY_AMOUNT_FIRST + row else AMOUNT_NEW_FIRST + row - LEGACY_ROWS

    fun name(row: Int): Int = if (row < LEGACY_ROWS) LEGACY_NAME_FIRST + row else NAME_NEW_FIRST + row - LEGACY_ROWS

    fun price(row: Int): Int = if (row < LEGACY_ROWS) LEGACY_PRICE_FIRST + row else PRICE_NEW_FIRST + row - LEGACY_ROWS

    /** The line under row [row]. */
    fun separator(row: Int): Int = if (row < LEGACY_SEPARATORS) LEGACY_SEPARATOR_FIRST + row else SEPARATOR_NEW_FIRST + row - LEGACY_SEPARATORS

    /** The transparent layer over row [row] that carries its ops. */
    fun hit(row: Int): Int = HIT_FIRST + row

    fun rowTop(row: Int): Int = row * ROW_HEIGHT

    /** The list's scroll height for [filled] trades: at least the visible rows, so a short history does not scroll. */
    fun scrollHeight(filled: Int): Int = maxOf(filled.coerceIn(0, ROWS), VISIBLE_ROWS) * ROW_HEIGHT

    // ---- legacy (version byte 255, unnamed) component header: fixed offsets, see Component.decode ----
    private const val OFFSET_X = 4
    private const val OFFSET_Y = 6
    private const val OFFSET_WIDTH = 8
    private const val OFFSET_HEIGHT = 10
    private const val OFFSET_MODES = 12
    private const val OFFSET_PARENT = 16
    const val HEADER_BYTES = 19

    fun isLegacyUnnamed(bytes: ByteArray): Boolean = bytes.size > HEADER_BYTES && (bytes[0].toInt() and 0xFF) == 255 && (bytes[1].toInt() and 0x80) == 0

    fun typeOf(bytes: ByteArray): Int = bytes[1].toInt() and 0x7F

    private fun u2(
        bytes: ByteArray,
        at: Int,
    ): Int = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

    private fun s2(
        bytes: ByteArray,
        at: Int,
    ): Int = u2(bytes, at).let { if (it > Short.MAX_VALUE) it - 65536 else it }

    fun x(bytes: ByteArray): Int = s2(bytes, OFFSET_X)

    fun y(bytes: ByteArray): Int = s2(bytes, OFFSET_Y)

    fun width(bytes: ByteArray): Int = u2(bytes, OFFSET_WIDTH)

    fun height(bytes: ByteArray): Int = u2(bytes, OFFSET_HEIGHT)

    /** The parent component id, -1 for a root-level component. */
    fun parent(bytes: ByteArray): Int = u2(bytes, OFFSET_PARENT).let { if (it == 0xFFFF) -1 else it }

    /**
     * [template] with only its box and parent changed (absolute size and position); every other byte - font, colour,
     * alignment, shadow, sprite, tiling, ops, hooks - stays the 667 component's own. Null keeps the template's value.
     */
    fun relocate(
        template: ByteArray,
        x: Int? = null,
        y: Int? = null,
        width: Int? = null,
        height: Int? = null,
        parent: Int? = null,
    ): ByteArray {
        require(isLegacyUnnamed(template)) { "not a legacy unnamed component" }
        val out = template.copyOf()
        fun put2(
            at: Int,
            value: Int,
        ) {
            out[at] = (value shr 8).toByte()
            out[at + 1] = value.toByte()
        }
        x?.let { put2(OFFSET_X, it) }
        y?.let { put2(OFFSET_Y, it) }
        width?.let { put2(OFFSET_WIDTH, it) }
        height?.let { put2(OFFSET_HEIGHT, it) }
        parent?.let {
            for (i in 0 until 4) out[OFFSET_MODES + i] = 0
            put2(OFFSET_PARENT, if (it < 0) 0xFFFF else it)
        }
        return out
    }

    private fun layer(
        id: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        parent: Int,
        onLoad: List<Int> = emptyList(),
    ): ByteArray = DeathsOfficeInterfaceImportTool.encode(DeathsOfficeInterfaceImportTool.Comp(id, TYPE_LAYER, x, y, width, height, parent, onLoad = onLoad))

    /**
     * Every component this tool writes, by id, built from [current] (interface 643's files as the cache holds them: the 667
     * layout or this tool's own). Components 0..22 are not part of the result.
     */
    fun build(current: Map<Int, ByteArray>): Map<Int, ByteArray> {
        require(current.size == LEGACY_COMPONENT_COUNT || current.size == COMPONENT_COUNT) {
            "interface $INTERFACE_ID has ${current.size} components: neither the 667 layout ($LEGACY_COMPONENT_COUNT) nor this tool's ($COMPONENT_COUNT)"
        }
        fun template(
            id: Int,
            type: Int,
        ): ByteArray {
            val bytes = current[id] ?: error("643:$id missing")
            require(isLegacyUnnamed(bytes) && typeOf(bytes) == type) { "643:$id is not a legacy type-$type component" }
            return bytes
        }
        val out = sortedMapOf<Int, ByteArray>()
        out[HEADER_QUANTITY] = relocate(template(HEADER_QUANTITY, TYPE_TEXT), x = LIST_X + AMOUNT_COLUMN.x, width = AMOUNT_COLUMN.width)
        out[HEADER_PRICE] = relocate(template(HEADER_PRICE, TYPE_TEXT), x = LIST_X + PRICE_COLUMN.x, width = PRICE_COLUMN.width)
        out[LIST] = layer(LIST, LIST_X, LIST_Y, LIST_WIDTH, LIST_HEIGHT, -1)
        out[SCROLLBAR] =
            layer(SCROLLBAR, LIST_X + LIST_WIDTH, LIST_Y, SCROLLBAR_WIDTH, LIST_HEIGHT, -1, onLoad = listOf(SCROLLBAR_SCRIPT, EVENT_COM, (INTERFACE_ID shl 16) or LIST))
        val columns: List<Pair<Column, (Int) -> Int>> =
            listOf(
                TYPE_COLUMN to { r: Int -> type(r) },
                NAME_COLUMN to { r: Int -> name(r) },
                AMOUNT_COLUMN to { r: Int -> amount(r) },
                PRICE_COLUMN to { r: Int -> price(r) },
            )
        val separatorTemplate = template(LEGACY_SEPARATOR_FIRST, TYPE_GRAPHIC)
        for (row in 0 until ROWS) {
            val top = rowTop(row)
            columns.forEach { (column, idOf) ->
                out[idOf(row)] = relocate(template(column.template, TYPE_TEXT), x = column.x, y = top + 2, width = column.width, height = ROW_HEIGHT - 4, parent = LIST)
            }
            // Sprite 1074 draws its line at y 16..17 of the 32 px tile: the line sits on the row's last two pixels.
            out[separator(row)] = relocate(separatorTemplate, x = 0, y = top + ROW_HEIGHT - 18, width = LIST_WIDTH, height = 32, parent = LIST)
            out[hit(row)] = layer(hit(row), 0, top, LIST_WIDTH, ROW_HEIGHT, LIST)
        }
        val ids = (0 until COMPONENT_COUNT).filter { it !in out && it >= LEGACY_COMPONENT_COUNT }
        check(ids.isEmpty()) { "new components without bytes: $ids" }
        return out
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        val current: Map<Int, ByteArray>
        try {
            val files = library.index(INDEX_INTERFACES).archive(INTERFACE_ID)?.fileIds()?.toList() ?: error("interface $INTERFACE_ID missing")
            current = files.associateWith { f -> library.data(INDEX_INTERFACES, INTERFACE_ID, f) ?: error("643:$f unreadable") }
        } finally {
            library.close()
        }
        val built = build(current)
        val mutations =
            built.map { (id, bytes) ->
                CacheMutation(
                    INDEX_INTERFACES,
                    INTERFACE_ID,
                    id,
                    bytes,
                    "interface $INTERFACE_ID:$id",
                    expectedCurrentSha1 = current[id]?.let(CacheItemProbeTool::sha1),
                )
            }
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interface $INTERFACE_ID (${current.size} -> $COMPONENT_COUNT components)")
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
