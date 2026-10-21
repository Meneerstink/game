package gg.rsmod.plugins.content.mechanics.exchange

import com.google.gson.JsonParser
import gg.rsmod.game.fs.def.ItemDef
import java.io.File
import java.io.FileReader

/**
 * The one price rule of "what is an item worth" on the Grand Exchange, the price checker, the trade window, the items kept on death,
 * the risk skull, loot keys and the Quartermaster (A14, owner 2026-10-10: "all grand exchange prices need to be exactly osrs grand
 * exchange prices"; AGENTS.md 9: OSRS wiki, hourly average, daily update; no OSRS price -> cache value).
 *
 * The price of an item is looked up BY OSRS ITEM ID (donor: Void ItemExchangePrices.kt keys the wiki price per item id, Novite
 * ExchangePriceLoader one id-keyed table for GE and trade; Darkan EconomyPrices.getPrice(id) one function for every surface):
 *  1. [OsrsPriceBook.idMap] (`osrs-item-id-map.json`, built by `tools/ge/Build-OsrsPriceData.py`): the OSRS id of this 667 item, by the
 *     import roster, the same id with the same name, or the same id with the same inventory model and cache value (catches the
 *     renames: 667 "Karil's top" = OSRS "Karil's leathertop", id 4736);
 *  2. the OSRS name of the 667 item (`osrs-item-mapping.json`), for 667 items with a different id;
 *  3. no OSRS item at all -> the cache value of the item (a 667-only item, e.g. a summoning pouch).
 * The OSRS price of that id is the average of the last hour from the wiki real-time prices API, fetched daily by [OsrsPriceSync]; the
 * checked-in `osrs-hourly-fallback.json` is the first-boot / offline copy and the Jagex guide price by name
 * (`osrs-ge-guide-prices.json`, RCV-012) the last OSRS source before the cache value.
 */
object OsrsGuidePrices {
    const val DEFAULT_PATH = "./data/cfg/ge/osrs-ge-guide-prices.json"

    class Table(val byName: Map<String, Int>, val snapshot: String) {
        private val byKey: Map<String, Int> =
            byName.entries.groupBy { key(it.key) }.filterValues { it.size == 1 }.mapValues { it.value.single().value }

        /** The OSRS guide price for an item named [name], or null when OSRS has no item of that name. */
        fun price(name: String): Int? = if (name.isBlank()) null else byName[name] ?: byKey[key(name)]
    }

    /** Name spelling differences only: letter case and the 667 space before a dose / charge suffix ("Strength potion (4)" = OSRS "Strength potion(4)"). */
    fun key(name: String): String = name.lowercase().replace(Regex("\\s+\\("), "(").replace("duelling", "dueling").trim()

    fun load(file: File = defaultFile()): Table {
        val json = FileReader(file).use { JsonParser().parse(it).asJsonObject }
        val prices = mutableMapOf<String, Int>()
        json.entrySet().filter { !it.key.startsWith("%") }.forEach { prices[it.key] = it.value.asInt }
        return Table(prices, json.get("%LAST_UPDATE_F%")?.asString ?: "")
    }

    /** Resolve the same checked-in data from both the server root and the plugins test cwd. */
    private fun defaultFile(name: String = "osrs-ge-guide-prices.json"): File =
        listOf(
            File("./data/cfg/ge/$name"),
            File("../../data/cfg/ge/$name"),
        ).firstOrNull { it.isFile } ?: File("./data/cfg/ge/$name")

    val table: Table by lazy { load() }

    /**
     * The same item id under the name OSRS gave it (kept from 2026-10-02): used when an item has no entry in the id map. Source: the OSRS
     * Wiki infobox item id of each page. Without it the Home PK Supplies house tablets fell back to the cache value 0.
     */
    val RENAMED_IN_OSRS: Map<Int, String> =
        mapOf(
            8013 to "Teleport to house (tablet)", // 667 "Teleport to house"
            9244 to "Dragonstone bolts (e)", // 667 "Dragon bolts (e)"
        )

    /** The price data every caller reads; [OsrsPriceSync] replaces it when a fresh daily price list arrives. */
    @Volatile
    var book: OsrsPriceBook = OsrsPriceBook.checkedIn()

    /** The OSRS price of [def] (its OSRS hourly average), or null when OSRS has no such item - then the item is a 667-only item. */
    fun osrsPrice(
        def: ItemDef,
        book: OsrsPriceBook = this.book,
    ): Int? = book.price(def)

    /** The price of [def]: the OSRS price when OSRS has the item, otherwise the cache item value. */
    fun seed(
        def: ItemDef,
        book: OsrsPriceBook = this.book,
    ): Int = book.price(def) ?: def.cost

    internal fun dataFile(name: String): File = defaultFile(name)
}
