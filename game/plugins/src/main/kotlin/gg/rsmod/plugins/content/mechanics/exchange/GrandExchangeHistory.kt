package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
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
 * Layout source: the 667 cache (643: offer type 25..29, quantity 30..34, item name 35..39, price 40..44, row bars
 * 45..49, title 19 - `GrandExchangeBuyLimitTests`). Content source: OSRS cache 2686 script 1645 (one history row):
 * "Bought:" / "Sold:"; the price "<N> coins" ("1 coin"), for a sale that paid the convenience fee
 * "<net> coins<br>(<gross> - <fee>)", and for more than one item "<br>= <each> each"; the row ops "Buy-offer" (op1)
 * and "Examine" (op10) with the item name as op base; title "Grand Exchange Trade History" (OSRS 383:1). OSRS keeps
 * 20 trades (Wiki changes, 9 March 2017) - the service keeps 20, the 667 screen has room for the newest five.
 * An offer is added, newest first, when it finished with something traded and its collection box was emptied (Void
 * `GrandExchangeCollection.kt`).
 */
object GrandExchangeHistory {
    const val INTERFACE = 643
    const val ROWS = 5

    /** OSRS keeps 20 trades (Wiki changes, 9 March 2017). */
    const val KEPT = 20
    const val TITLE = 19
    const val TITLE_TEXT = "Grand Exchange Trade History"
    val TYPE = intArrayOf(25, 26, 27, 28, 29)
    val AMOUNT = intArrayOf(30, 31, 32, 33, 34)
    val NAME = intArrayOf(35, 36, 37, 38, 39)
    val PRICE = intArrayOf(40, 41, 42, 43, 44)

    /** Row bars carrying the row's ops (bar i spans text row i: bar 45 y 96..128, row text 25 y 125). */
    val ROW = intArrayOf(45, 46, 47, 48, 49)

    const val OP_BUY_OFFER = "Buy-offer"
    const val OP_EXAMINE = "Examine"

    /** RspsGrandExchange.SCRIPT_SET_OPS: (component hash, op base, op1 text, op10 text) on a static component. */
    const val SCRIPT_RSPS_SET_OPS = 40101

    /** IF_SETEVENTS of a filled row: op1 (Buy-offer, IF_BUTTON1 61) and op10 (Examine, IF_BUTTON10 25). */
    const val ROW_EVENTS = 2 or (1 shl 10)
    const val OPCODE_BUY_OFFER = 61
    const val OPCODE_EXAMINE = 25

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

    fun rows(
        entries: List<GeHistoryEntry>,
        nameOf: (Int) -> String,
    ): List<Row> =
        (0 until ROWS).map { i ->
            val entry = entries.getOrNull(i) ?: return@map Row("", "", "", "")
            Row(
                type = if (entry.sold) "Sold:" else "Bought:",
                name = nameOf(entry.itemId),
                amount = number(entry.amount.toLong()),
                price = price(entry),
            )
        }

    fun open(player: Player) {
        val service = GrandExchangeInterface.service(player) ?: return
        player.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        player.setComponentText(INTERFACE, TITLE, TITLE_TEXT)
        val entries = service.historyFor(GrandExchangeInterface.username(player))
        val nameOf = { id: Int -> player.world.definitions.get(ItemDef::class.java, id).name }
        rows(entries, nameOf).forEachIndexed { i, row ->
            player.setComponentText(INTERFACE, TYPE[i], row.type)
            player.setComponentText(INTERFACE, NAME[i], row.name)
            player.setComponentText(INTERFACE, AMOUNT[i], row.amount)
            player.setComponentText(INTERFACE, PRICE[i], row.price)
            val entry = entries.getOrNull(i)
            if (entry != null) {
                player.runClientScript(SCRIPT_RSPS_SET_OPS, (INTERFACE shl 16) or ROW[i], "<col=ff9040>${nameOf(entry.itemId)}</col>", OP_BUY_OFFER, OP_EXAMINE)
            }
            player.setInterfaceEvents(interfaceId = INTERFACE, component = ROW[i], range = -1..-1, setting = if (entry != null) ROW_EVENTS else 0)
        }
    }

    /** A history row's op: Buy-offer opens a buy offer for the item ([GrandExchangeInterface.buyOffer]), Examine its text. */
    fun press(
        player: Player,
        row: Int,
        opcode: Int,
    ): Boolean {
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
