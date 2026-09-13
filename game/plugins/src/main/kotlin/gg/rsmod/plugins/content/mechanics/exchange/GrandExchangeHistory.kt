package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentText
import java.util.Locale

/** One finished Grand Exchange offer: [coins] paid (bought) or received ([sold]). */
data class GeHistoryEntry(
    val itemId: Int = -1,
    val amount: Int = 0,
    val coins: Long = 0,
    val sold: Boolean = false,
)

/**
 * RCV-011 GE History screen (interface 643), previously not built.
 *
 * Source: Void 2011 `GrandExchangeHistory.kt` + `grand_exchange.ifaces.toml` (643: type_0..4 = 25..29, amount 30..34,
 * name 35..39, price 40..44; texts "You bought"/"You sold", "It cost you<br>N gp"/"You got<br>N gp") and
 * `GrandExchangeCollection.kt` (an offer is added, newest first, when it finished with something traded and its
 * collection box was emptied). The components exist in the 667 cache (`GrandExchangeBuyLimitTests`).
 */
object GrandExchangeHistory {
    const val INTERFACE = 643
    const val ROWS = 5
    val TYPE = intArrayOf(25, 26, 27, 28, 29)
    val AMOUNT = intArrayOf(30, 31, 32, 33, 34)
    val NAME = intArrayOf(35, 36, 37, 38, 39)
    val PRICE = intArrayOf(40, 41, 42, 43, 44)

    data class Row(
        val type: String,
        val name: String,
        val amount: String,
        val price: String,
    )

    fun rows(
        entries: List<GeHistoryEntry>,
        nameOf: (Int) -> String,
    ): List<Row> =
        (0 until ROWS).map { i ->
            val entry = entries.getOrNull(i) ?: return@map Row("", "", "", "")
            Row(
                type = if (entry.sold) "You sold" else "You bought",
                name = nameOf(entry.itemId),
                amount = entry.amount.toString(),
                price = "${if (entry.sold) "You got" else "It cost you"}<br>${String.format(Locale.ENGLISH, "%,d", entry.coins)} gp",
            )
        }

    fun open(player: Player) {
        val service = GrandExchangeInterface.service(player) ?: return
        player.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        val entries = service.historyFor(GrandExchangeInterface.username(player))
        rows(entries) { player.world.definitions.get(ItemDef::class.java, it).name }.forEachIndexed { i, row ->
            player.setComponentText(INTERFACE, TYPE[i], row.type)
            player.setComponentText(INTERFACE, NAME[i], row.name)
            player.setComponentText(INTERFACE, AMOUNT[i], row.amount)
            player.setComponentText(INTERFACE, PRICE[i], row.price)
        }
    }
}
