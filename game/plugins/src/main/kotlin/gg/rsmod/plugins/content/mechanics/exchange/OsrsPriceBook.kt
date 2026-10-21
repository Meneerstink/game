package gg.rsmod.plugins.content.mechanics.exchange

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import gg.rsmod.game.fs.def.ItemDef
import java.io.File

/**
 * The price data behind [OsrsGuidePrices], immutable so a daily refresh swaps it in one step.
 *
 *  - [hourly]: OSRS item id -> price (the OSRS wiki average of the last hour, [OsrsPriceSync.weighted]);
 *  - [idMap]: 667 item id -> OSRS item id (`osrs-item-id-map.json`, `tools/ge/Build-OsrsPriceData.py`);
 *  - [osrsNames]: OSRS item id -> OSRS name (`osrs-item-mapping.json`, the wiki /mapping);
 *  - [guide]: the Jagex guide price by OSRS name (RCV-012), the last OSRS source before the cache value.
 */
class OsrsPriceBook(
    val hourly: Map<Int, Int>,
    val idMap: Map<Int, Int>,
    val osrsNames: Map<Int, String>,
    val guide: OsrsGuidePrices.Table,
    val label: String = "",
) {
    private val idByKey: Map<String, Int> =
        osrsNames.entries.groupBy { OsrsGuidePrices.key(it.value) }.filterValues { it.size == 1 }.mapValues { it.value.single().key }

    /** The OSRS item id of [def]: the sourced id map, else the item of the same name, else the renamed-in-OSRS list. */
    fun osrsIdOf(def: ItemDef): Int? =
        idMap[def.id]
            ?: def.name.takeIf { it.isNotBlank() }?.let { idByKey[OsrsGuidePrices.key(it)] }
            ?: OsrsGuidePrices.RENAMED_IN_OSRS[def.id]?.let { idByKey[OsrsGuidePrices.key(it)] }

    /** The OSRS price of [def], or null when OSRS has no such item (or no price for it). */
    fun price(def: ItemDef): Int? {
        val osrsId = osrsIdOf(def)
        if (osrsId != null) {
            hourly[osrsId]?.takeIf { it > 0 }?.let { return it }
            osrsNames[osrsId]?.let { guide.price(it) }?.takeIf { it > 0 }?.let { return it }
        }
        return guide.price(def.name)?.takeIf { it > 0 }
            ?: OsrsGuidePrices.RENAMED_IN_OSRS[def.id]?.let { guide.price(it) }?.takeIf { it > 0 }
    }

    /** The same data with the [hourly] prices of a newer list on top (an id the new list lacks keeps its old price). */
    fun withHourly(
        newer: Map<Int, Int>,
        newLabel: String,
    ): OsrsPriceBook = OsrsPriceBook(hourly + newer, idMap, osrsNames, guide, newLabel)

    companion object {
        /** The data checked in under `data/cfg/ge`: the first-boot / offline prices, and the runtime file [OsrsPriceSync] writes on top. */
        fun checkedIn(runtime: File? = null): OsrsPriceBook {
            val ids = readIntMap(OsrsGuidePrices.dataFile("osrs-item-id-map.json"))
            val names = readNameMap(OsrsGuidePrices.dataFile("osrs-item-mapping.json"))
            val fallback = OsrsPriceSync.readHourlyFile(OsrsGuidePrices.dataFile("osrs-hourly-fallback.json"))
            val base = OsrsPriceBook(fallback.prices, ids, names, OsrsGuidePrices.table, "checked-in ${fallback.timestamp}")
            val stored = runtime?.let { OsrsPriceSync.readStoredPrices(it) }
            return if (stored == null || stored.prices.isEmpty()) base else base.withHourly(stored.prices, "daily ${stored.timestamp}")
        }

        fun readIntMap(file: File): Map<Int, Int> {
            if (!file.isFile) return emptyMap()
            val json = JsonParser().parse(file.readText()).asJsonObject
            return json.entrySet().associate { it.key.toInt() to it.value.asInt }
        }

        fun readNameMap(file: File): Map<Int, String> {
            if (!file.isFile) return emptyMap()
            val json: JsonObject = JsonParser().parse(file.readText()).asJsonObject
            return json.entrySet().associate { it.key.toInt() to it.value.asString }
        }
    }
}
