package gg.rsmod.plugins.content.mechanics.exchange

import com.google.gson.JsonParser
import gg.rsmod.game.fs.def.ItemDef
import java.io.File
import java.io.FileReader

/**
 * RCV-012 owner decision "GE guide price starting values: use OSRS values" (closes the RCV-010 C3 guide-price SOURCE_CONFLICT).
 *
 * `data/cfg/ge/osrs-ge-guide-prices.json` is the OSRS Wiki `Module:GEPrices/data.json` (the official Jagex Grand Exchange guide price
 * per item name) stored verbatim; its `%LAST_UPDATE_F%` key records the snapshot time. An exchangeable item's starting guide price is
 * the snapshot price of the item with the same name (exact spelling first, then ignoring case and the space 667 puts before a dose or
 * charge suffix, see [key]). Items renamed in OSRS (e.g. 667 "Cannonball" = OSRS "Steel cannonball") are not aliased without a sourced
 * rename list, and items that do not exist in OSRS keep the cache item value (SOURCE_GAP); executed trades replace either seed in
 * [GrandExchangeService.guidePrice].
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
    fun key(name: String): String = name.lowercase().replace(Regex("\\s+\\("), "(").trim()

    fun load(file: File = defaultFile()): Table {
        val json = FileReader(file).use { JsonParser().parse(it).asJsonObject }
        val prices = mutableMapOf<String, Int>()
        json.entrySet().filter { !it.key.startsWith("%") }.forEach { prices[it.key] = it.value.asInt }
        return Table(prices, json.get("%LAST_UPDATE_F%")?.asString ?: "")
    }

    /** Resolve the same checked-in data from both the server root and the plugins test cwd. */
    private fun defaultFile(): File =
        listOf(
            File(DEFAULT_PATH),
            File("../../data/cfg/ge/osrs-ge-guide-prices.json"),
        ).firstOrNull { it.isFile } ?: File(DEFAULT_PATH)

    val table: Table by lazy { load() }

    /** The starting guide price of [def]: the OSRS guide price by name, otherwise the cache item value. */
    fun seed(
        def: ItemDef,
        table: Table = this.table,
    ): Int = table.price(def.name) ?: def.cost
}
