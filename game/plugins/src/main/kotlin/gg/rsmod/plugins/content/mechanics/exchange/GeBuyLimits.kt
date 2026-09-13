package gg.rsmod.plugins.content.mechanics.exchange

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileReader

/**
 * RCV-011 GE buy limits. Before this every buy offer could take any quantity; there was no per-item limit anywhere.
 *
 * Source: Void 2011 `BuyLimits.kt` (`limit` of every `*.items.toml` row, reset window `grandExchange.buyLimit.hours=4`
 * in `game.properties`). The table is `data/cfg/ge/buy-limits.json`, 3,370 Void rows keyed by id; every id is the same
 * item in the 667 cache (`GrandExchangeBuyLimitTests`). Void row `pollnivneach_teleport` (19475) is left out:
 * SOURCE_CONFLICT, 19475 is "Nardah teleport" in the 667 cache.
 *
 * SOURCE_CONFLICT (window): Void's comment and setting say "every 4 hours", its expiry code (`toHours(elapsed) > 4`)
 * only clears after 5 whole hours; the documented 4-hour window is used. Like Void the ledger lives in memory only.
 */
object GeBuyLimits {
    const val DEFAULT_PATH = "./data/cfg/ge/buy-limits.json"
    const val WINDOW_MS = 4L * 60 * 60 * 1000

    class Row(
        val id: Int = -1,
        val limit: Int = 0,
        @SerializedName("void_key") val voidKey: String = "",
    )

    @Volatile
    private var limits: Map<Int, Int> = emptyMap()

    fun load(file: File = File(DEFAULT_PATH)): Int {
        val rows: Array<Row> = FileReader(file).use { Gson().fromJson(it, Array<Row>::class.java) }
        limits = rows.associate { it.id to it.limit }
        return limits.size
    }

    /** Replaces the table (tests). */
    fun set(table: Map<Int, Int>) {
        limits = table
    }

    fun table(): Map<Int, Int> = limits

    /** The buy limit of unnoted [itemId], or null when the item has none. */
    fun limitOf(itemId: Int): Int? = limits[itemId]
}

/** How much more of an item a buyer may receive right now; asked by every GE fill. */
interface GeBuyAllowance {
    fun remaining(
        username: String,
        itemId: Int,
    ): Int

    fun record(
        username: String,
        itemId: Int,
        quantity: Int,
    )

    companion object {
        val UNLIMITED =
            object : GeBuyAllowance {
                override fun remaining(
                    username: String,
                    itemId: Int,
                ) = Int.MAX_VALUE

                override fun record(
                    username: String,
                    itemId: Int,
                    quantity: Int,
                ) {}
            }
    }
}

/** Void `BuyLimits`: amount bought per buyer and item since the first purchase of the current window. */
class GeBuyLedger(
    private val clock: () -> Long = System::currentTimeMillis,
) : GeBuyAllowance {
    private class Entry(
        var amount: Int,
        val since: Long,
    )

    private val entries = HashMap<String, Entry>()

    private fun key(
        username: String,
        itemId: Int,
    ) = "$username:$itemId"

    override fun remaining(
        username: String,
        itemId: Int,
    ): Int {
        val limit = GeBuyLimits.limitOf(itemId) ?: return Int.MAX_VALUE
        val entry = entries[key(username, itemId)]
        if (entry == null || clock() - entry.since >= GeBuyLimits.WINDOW_MS) return limit
        return (limit - entry.amount).coerceAtLeast(0)
    }

    override fun record(
        username: String,
        itemId: Int,
        quantity: Int,
    ) {
        if (GeBuyLimits.limitOf(itemId) == null || quantity <= 0) return
        val now = clock()
        val key = key(username, itemId)
        val entry = entries[key]
        if (entry == null || now - entry.since >= GeBuyLimits.WINDOW_MS) {
            entries[key] = Entry(quantity, now)
        } else {
            entry.amount += quantity
        }
    }
}
