package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool
import gg.rsmod.game.tools.importer.GeHistoryInterfaceImportTool
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import java.util.Locale

/** One finished Grand Exchange offer: [coins] paid (bought) or received before the fee ([sold]); [fee] = convenience fee paid. */
data class GeHistoryEntry(
    val itemId: Int = -1,
    val amount: Int = 0,
    val coins: Long = 0,
    val sold: Boolean = false,
    val fee: Long = 0,
)

/**
 * RCV-011 GE History screen (interface 643), previously not built; E6 (owner 2026-10-03 "grand exchange rightbutton
 * history not finished or correct") brings it to OSRS.
 *
 * Content source: OSRS cache 2686 script 1645 (one history row): "Bought:" / "Sold:"; the price "<N> coins" ("1 coin"),
 * for a sale that paid the convenience fee "<net> coins<br>(<gross> - <fee>)", and for more than one item
 * "<br>= <each> each"; the row ops "Buy-offer" (op1) and "Examine" (op10) with the item name as op base; title
 * "Grand Exchange Trade History" (OSRS 383:1). OSRS keeps and shows 20 trades in a scrollable list (Wiki changes,
 * 9 March 2017). An offer is added, newest first, when it finished with something traded and its collection box was
 * emptied (Void `GrandExchangeCollection.kt`).
 *
 * Layout: the 667 screen has five fixed rows ([LEGACY]: type 25..29, quantity 30..34, item name 35..39, price 40..44,
 * the separator lines 45..49 carrying the ops); `GeHistoryInterfaceImportTool` rebuilds them as a 20-row scroll list
 * ([CURRENT]). The server reads which one its cache holds from 643's component count ([layout]), so it drives either.
 */
object GrandExchangeHistory {
    const val INTERFACE = 643

    /** OSRS keeps 20 trades (Wiki changes, 9 March 2017). */
    const val KEPT = 20
    const val TITLE = 19
    const val TITLE_TEXT = "Grand Exchange Trade History"

    const val OP_BUY_OFFER = "Buy-offer"
    const val OP_EXAMINE = "Examine"

    /** RspsGrandExchange.SCRIPT_SET_OPS: (component hash, op base, op1 text, op10 text) on a static component. */
    const val SCRIPT_RSPS_SET_OPS = 40101

    /** IF_SETEVENTS of a filled row: op1 (Buy-offer, IF_BUTTON1 61) and op10 (Examine, IF_BUTTON10 25). */
    const val ROW_EVENTS = 2 or (1 shl 10)
    const val OPCODE_BUY_OFFER = 61
    const val OPCODE_EXAMINE = 25

    /**
     * The row components of one 643 layout. [hit] carries a row's ops; [list] / [scrollbar] are -1 when the layout does
     * not scroll.
     */
    class Layout(
        val rows: Int,
        val type: IntArray,
        val amount: IntArray,
        val name: IntArray,
        val price: IntArray,
        val hit: IntArray,
        val list: Int,
        val scrollbar: Int,
        val componentCount: Int,
        /** The "Exchange" button back to the offer screen (OSRS 383:2), -1 when the layout has none. */
        val exchange: Int = -1,
    )

    /** The 667 cache's five rows (probe 2026-10-04: every row text and the separator lines 45..50 are root-level). */
    val LEGACY =
        Layout(
            rows = 5,
            type = intArrayOf(25, 26, 27, 28, 29),
            amount = intArrayOf(30, 31, 32, 33, 34),
            name = intArrayOf(35, 36, 37, 38, 39),
            price = intArrayOf(40, 41, 42, 43, 44),
            hit = intArrayOf(45, 46, 47, 48, 49),
            list = -1,
            scrollbar = -1,
            componentCount = GeHistoryInterfaceImportTool.LEGACY_COMPONENT_COUNT,
        )

    /** `GeHistoryInterfaceImportTool`: 20 rows in a scroll layer, one hit layer per row. */
    val CURRENT =
        Layout(
            rows = GeHistoryInterfaceImportTool.ROWS,
            type = IntArray(GeHistoryInterfaceImportTool.ROWS) { GeHistoryInterfaceImportTool.type(it) },
            amount = IntArray(GeHistoryInterfaceImportTool.ROWS) { GeHistoryInterfaceImportTool.amount(it) },
            name = IntArray(GeHistoryInterfaceImportTool.ROWS) { GeHistoryInterfaceImportTool.name(it) },
            price = IntArray(GeHistoryInterfaceImportTool.ROWS) { GeHistoryInterfaceImportTool.price(it) },
            hit = IntArray(GeHistoryInterfaceImportTool.ROWS) { GeHistoryInterfaceImportTool.hit(it) },
            list = GeHistoryInterfaceImportTool.LIST,
            scrollbar = GeHistoryInterfaceImportTool.SCROLLBAR,
            componentCount = GeHistoryInterfaceImportTool.COMPONENT_COUNT,
            exchange = GeHistoryInterfaceImportTool.EXCHANGE_BUTTON,
        )

    /** Every component a row op can arrive on, in either layout (the clerk plugin binds them all; [rowOf] maps them back). */
    val ROW_COMPONENTS: List<Int> = (LEGACY.hit.toList() + CURRENT.hit.toList()).distinct()

    /** The history row a click on [component] belongs to, -1 for none. The two layouts' op components do not overlap. */
    fun rowOf(component: Int): Int {
        val current = CURRENT.hit.indexOf(component)
        return if (current >= 0) current else LEGACY.hit.indexOf(component)
    }

    fun layoutFor(componentCount: Int): Layout = if (componentCount == CURRENT.componentCount) CURRENT else LEGACY

    private const val INTERFACE_INDEX = 3

    /** The layout interface 643 has in [world]'s cache, by its component count (as `StoreUi.layout`). */
    fun layout(world: World): Layout {
        val count = runCatching { world.filestore.index(INTERFACE_INDEX).archive(INTERFACE)?.fileIds()?.size ?: 0 }.getOrDefault(0)
        val layout = layoutFor(count)
        if (layout !== CURRENT) {
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.INTERFACE) {
                "GE history interface $INTERFACE has $count components, not the ${CURRENT.componentCount} of the 20-row list: the cache " +
                    "still holds the 667 five-row screen - run GeHistoryInterfaceImportTool --apply with the servers stopped"
            }
        }
        return layout
    }

    data class Row(
        val type: String,
        val name: String,
        val amount: String,
        val price: String,
    )

    private fun number(value: Long): String = String.format(Locale.ENGLISH, "%,d", value)

    private fun coins(value: Long): String = if (value == 1L) "1 coin" else "${number(value)} coins"

    /** OSRS script 1645's price text for [entry]. */
    fun price(entry: GeHistoryEntry): String {
        val each: Long
        val text =
            if (entry.sold && entry.fee > 0) {
                val net = (entry.coins - entry.fee).coerceAtLeast(0)
                each = net
                "<col=ffb83f>${coins(net)}</col><br><col=9f9f9f>(${number(entry.coins)} - ${number(entry.fee)})</col>"
            } else {
                each = entry.coins
                "<col=ffb83f>${coins(entry.coins)}</col>"
            }
        return if (entry.amount > 1) "$text<br>= ${number(each / entry.amount)} each" else text
    }

    /** [count] rows: one per entry, newest first, then empty rows. */
    fun rows(
        entries: List<GeHistoryEntry>,
        count: Int = KEPT,
        nameOf: (Int) -> String,
    ): List<Row> =
        (0 until count).map { i ->
            val entry = entries.getOrNull(i) ?: return@map Row("", "", "", "")
            Row(
                type = if (entry.sold) "Sold:" else "Bought:",
                name = nameOf(entry.itemId),
                amount = number(entry.amount.toLong()),
                price = price(entry),
            )
        }

    fun open(player: Player) = open(player, layout(player.world))

    fun open(
        player: Player,
        layout: Layout,
    ) {
        val service = GrandExchangeInterface.service(player) ?: return
        player.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        player.setComponentText(INTERFACE, TITLE, TITLE_TEXT)
        val entries = service.historyFor(GrandExchangeInterface.username(player)).take(layout.rows)
        val nameOf = { id: Int -> player.world.definitions.get(ItemDef::class.java, id).name }
        rows(entries, layout.rows, nameOf).forEachIndexed { i, row ->
            player.setComponentText(INTERFACE, layout.type[i], row.type)
            player.setComponentText(INTERFACE, layout.name[i], row.name)
            player.setComponentText(INTERFACE, layout.amount[i], row.amount)
            player.setComponentText(INTERFACE, layout.price[i], row.price)
            val entry = entries.getOrNull(i)
            if (entry != null) {
                player.runClientScript(SCRIPT_RSPS_SET_OPS, (INTERFACE shl 16) or layout.hit[i], "<col=ff9040>${nameOf(entry.itemId)}</col>", OP_BUY_OFFER, OP_EXAMINE)
            }
            player.setInterfaceEvents(interfaceId = INTERFACE, component = layout.hit[i], range = -1..-1, setting = if (entry != null) ROW_EVENTS else 0)
        }
        if (layout.exchange >= 0) {
            player.setInterfaceEvents(interfaceId = INTERFACE, component = layout.exchange, range = -1..-1, setting = EXCHANGE_EVENTS)
        }
        if (layout.list >= 0) {
            // Scroll height for the trades there are (at least the five visible rows), clamp the scroll position, redraw the scrollbar.
            player.runClientScript(
                DeathsOfficeInterfaceImportTool.SCROLL_SCRIPT,
                (INTERFACE shl 16) or layout.list,
                (INTERFACE shl 16) or layout.scrollbar,
                GeHistoryInterfaceImportTool.scrollHeight(entries.size),
            )
        }
    }

    /** IF_SETEVENTS of the "Exchange" button: op1. */
    const val EXCHANGE_EVENTS = 2

    /** The "Exchange" button (OSRS 383:2): back to the offer screen, as OSRS. */
    fun backToExchange(player: Player) = GrandExchangeInterface.openMain(player)

    /** A history row's op: Buy-offer opens a buy offer for the item ([GrandExchangeInterface.buyOffer]), Examine its text. */
    fun press(
        player: Player,
        row: Int,
        opcode: Int,
    ): Boolean {
        if (row < 0) return false
        val service = GrandExchangeInterface.service(player) ?: return false
        val entry = service.historyFor(GrandExchangeInterface.username(player)).getOrNull(row) ?: return false
        when (opcode) {
            OPCODE_BUY_OFFER -> GrandExchangeInterface.buyOffer(player, entry.itemId)
            OPCODE_EXAMINE -> player.message(player.world.definitions.get(ItemDef::class.java, entry.itemId).examine ?: return false)
            else -> return false
        }
        return true
    }
}
