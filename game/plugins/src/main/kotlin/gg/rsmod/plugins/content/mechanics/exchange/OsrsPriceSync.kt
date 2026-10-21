package gg.rsmod.plugins.content.mechanics.exchange

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import gg.rsmod.util.io.AtomicFiles
import mu.KLogging
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A14: the daily OSRS price update. Once a day the server fetches the OSRS Wiki real-time prices (`/1h`: the average high / low trade
 * price of the last hour of every item, one call; `/latest` for the few items without an hourly trade), turns them into one price per
 * OSRS item id ([compute]) and swaps them into [OsrsGuidePrices.book]. The result is stored in `data/ge/osrs-prices.json` so a restart
 * keeps it. A failed fetch keeps the previous prices - never 0, never the cache value for everything.
 *
 * The API needs a descriptive User-Agent with contact (wiki rule: python-requests, curl and the default Java agent are blocked).
 */
object OsrsPriceSync : KLogging() {
    const val API = "https://prices.runescape.wiki/api/v2/osrs"
    const val USER_AGENT = "78legacy RSPS GE price sync - play.78legacy.com (contact: a.benmimun@gmail.com)"
    const val STORED = "data/ge/osrs-prices.json"
    const val INTERVAL_MS = 24L * 60 * 60 * 1000

    /** A list with fewer ids than this is a broken answer (the real /1h holds ~4,000), never applied. */
    const val MIN_ITEMS = 1000

    class Stored(val prices: Map<Int, Int>, val timestamp: Long, val fetchedAtMs: Long)

    class Hourly(val prices: Map<Int, Int>, val timestamp: Long)

    /**
     * The price of one item from its hourly figures: the mean of the average high and the average low trade price weighted by their
     * trade volumes (= the average price of every trade in that hour); one side missing -> the other side; none -> null.
     */
    fun weighted(
        avgHigh: Double?,
        highVolume: Long,
        avgLow: Double?,
        lowVolume: Long,
    ): Int? {
        val price =
            when {
                avgHigh != null && avgLow != null ->
                    if (highVolume + lowVolume > 0) {
                        (avgHigh * highVolume + avgLow * lowVolume) / (highVolume + lowVolume)
                    } else {
                        (avgHigh + avgLow) / 2.0
                    }
                avgHigh != null -> avgHigh
                avgLow != null -> avgLow
                else -> return null
            }
        return Math.round(price).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun JsonObject.double(name: String): Double? = get(name)?.takeIf { !it.isJsonNull }?.asDouble

    private fun JsonObject.long(name: String): Long = get(name)?.takeIf { !it.isJsonNull }?.asLong ?: 0L

    /** The prices of a `/1h` answer ({"data": {id: {avgHighPrice, highPriceVolume, avgLowPrice, lowPriceVolume}}, "timestamp"}). */
    fun parseHourly(hour: JsonObject): Map<Int, Int> {
        val data = hour.getAsJsonObject("data") ?: return emptyMap()
        val out = HashMap<Int, Int>(data.size() * 2)
        for ((id, value) in data.entrySet()) {
            val entry = value.asJsonObject
            val price =
                weighted(entry.double("avgHighPrice"), entry.long("highPriceVolume"), entry.double("avgLowPrice"), entry.long("lowPriceVolume"))
            if (price != null) out[id.toInt()] = price
        }
        return out
    }

    /**
     * The price list of the day: the hourly average of [hour]; an item without an hourly trade takes the mean of its latest high and low
     * trade ([latest], `/latest`), else its price of the previous list ([previous]).
     */
    fun compute(
        hour: JsonObject,
        latest: JsonObject?,
        previous: Map<Int, Int>,
    ): Map<Int, Int> {
        val out = HashMap(parseHourly(hour))
        val data = latest?.getAsJsonObject("data")
        if (data != null) {
            for ((id, value) in data.entrySet()) {
                val key = id.toInt()
                if (key in out) continue
                val entry = value.asJsonObject
                val price = weighted(entry.double("high"), 1, entry.double("low"), 1)
                if (price != null) out[key] = price
            }
        }
        for ((id, price) in previous) out.putIfAbsent(id, price)
        return out
    }

    /** A raw `/1h` answer on disk (the checked-in offline fallback). */
    fun readHourlyFile(file: File): Hourly {
        if (!file.isFile) return Hourly(emptyMap(), 0L)
        val json = JsonParser().parse(file.readText()).asJsonObject
        // "latest": the latest trade of the items without an hourly trade (tools/ge/Build-OsrsPriceData.py), merged like a daily refresh does.
        val latest = json.getAsJsonObject("latest")?.let { JsonObject().also { wrapper -> wrapper.add("data", it) } }
        return Hourly(compute(json, latest, emptyMap()), json.long("timestamp"))
    }

    fun readStoredPrices(file: File): Stored? {
        if (!file.isFile) return null
        return try {
            val json = JsonParser().parse(file.readText()).asJsonObject
            val prices = json.getAsJsonObject("prices").entrySet().associate { it.key.toInt() to it.value.asInt }
            Stored(prices, json.long("timestamp"), json.long("fetchedAtMs"))
        } catch (e: Exception) {
            logger.warn { "PROBLEM ge-prices cannot read ${file.path}: ${e.message}" }
            null
        }
    }

    fun writeStored(
        file: File,
        prices: Map<Int, Int>,
        timestamp: Long,
        now: Long,
    ) {
        val json = JsonObject()
        json.addProperty("source", "$API/1h")
        json.addProperty("timestamp", timestamp)
        json.addProperty("fetchedAtMs", now)
        val body = JsonObject()
        prices.toSortedMap().forEach { (id, price) -> body.addProperty(id.toString(), price) }
        json.add("prices", body)
        AtomicFiles.writeText(file, json.toString())
    }

    /** One HTTPS GET with the descriptive User-Agent. */
    fun httpGet(route: String): String {
        val connection = URL(API + route).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode} for $route" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Fetches and applies a new price list when the stored one is older than [INTERVAL_MS]. Returns true when [OsrsGuidePrices.book] was
     * replaced. [get] is the HTTP client (a fake in tests); every failure is a PROBLEM line and leaves the old prices in place.
     */
    fun refresh(
        file: File,
        now: Long = System.currentTimeMillis(),
        force: Boolean = false,
        get: (String) -> String = ::httpGet,
    ): Boolean {
        val stored = readStoredPrices(file)
        if (!force && stored != null && now - stored.fetchedAtMs in 0 until INTERVAL_MS) return false
        return try {
            val hour = JsonParser().parse(get("/1h")).asJsonObject
            val latest = runCatching { JsonParser().parse(get("/latest")).asJsonObject }.getOrNull()
            val previous = stored?.prices ?: OsrsGuidePrices.book.hourly
            val prices = compute(hour, latest, previous)
            val fresh = parseHourly(hour).size
            check(fresh >= MIN_ITEMS) { "answer holds only $fresh hourly prices" }
            val timestamp = hour.long("timestamp")
            writeStored(file, prices, timestamp, now)
            OsrsGuidePrices.book = OsrsGuidePrices.book.withHourly(prices, "daily $timestamp")
            logger.info { "[ge-prices] ${prices.size} OSRS prices applied (hourly average, timestamp $timestamp)" }
            true
        } catch (e: Exception) {
            logger.warn { "PROBLEM ge-prices fetch failed, keeping the previous prices: ${e.message}" }
            false
        }
    }

    private var started = false

    /** Loads the stored prices and checks hourly whether the daily update is due. Runs on its own daemon thread. */
    @Synchronized
    fun start(file: File) {
        if (started) return
        started = true
        OsrsGuidePrices.book = OsrsPriceBook.checkedIn(file)
        logger.info { "[ge-prices] using ${OsrsGuidePrices.book.label}" }
        val executor = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "ge-price-sync").apply { isDaemon = true } }
        executor.scheduleWithFixedDelay({ refresh(file) }, 30, 3600, TimeUnit.SECONDS)
    }
}
