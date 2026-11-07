package gg.rsmod.plugins.content.activity.toa

import com.fasterxml.jackson.databind.ObjectMapper
import gg.rsmod.game.RspsPaths
import gg.rsmod.plugins.content.activity.toa.core.ToaMode
import gg.rsmod.util.io.AtomicFiles
import gg.rsmod.util.io.SharedDataFiles
import java.io.File
import mu.KLogging

/**
 * Owner 07-11 21:5x: server overall records per mode and original party size, with names; no personal bests.
 * OSRS cache2686 775:55+4r = global overall time, 6771-6774 = mode tabs. The timer boundary is the existing
 * ToaCompletion boundary (01-route.tsv: first path entry to the final Warden reaching zero HP).
 * Write-through atomic replacement: a failed save must leave both the previous file and in-memory records intact.
 */
class ToaRecordStore(private val file: File) {
    data class Record(val mode: ToaMode, val ticks: Int, val names: List<String>) {
        val size: Int get() = names.size
    }

    private val mapper = ObjectMapper()
    private var records = load()

    @Synchronized
    fun get(mode: ToaMode, size: Int): Record? = records[mode to size]?.copy(names = records.getValue(mode to size).names.toList())

    /** Equal times retain the first record holders. A reconnect does not change the original roster's size/names. */
    @Synchronized
    fun submit(mode: ToaMode, ticks: Int, names: List<String>): Boolean {
        validate(ticks, names)
        val key = mode to names.size
        if (records[key]?.ticks?.let { it <= ticks } == true) return false
        val next = records.toMutableMap()
        next[key] = Record(mode, ticks, names.toList())
        val rows = next.values.sortedWith(compareBy<Record> { it.mode.ordinal }.thenBy { it.size }).map {
            mapOf("mode" to it.mode.name, "ticks" to it.ticks, "names" to it.names)
        }
        AtomicFiles.writeText(file, mapper.writeValueAsString(mapOf("version" to 1, "records" to rows)))
        records = next
        return true
    }

    private fun load(): Map<Pair<ToaMode, Int>, Record> {
        if (!file.exists() || file.length() == 0L) return emptyMap()
        // Do not silently replace a damaged registry with an empty one on the next completion.
        try {
            val root = mapper.readTree(file)
            require(root["version"]?.asInt() == 1 && root["records"]?.isArray == true)
            val loaded = linkedMapOf<Pair<ToaMode, Int>, Record>()
            root["records"].forEach { row ->
                val mode = ToaMode.valueOf(row["mode"].asText())
                require(row["ticks"]?.isIntegralNumber == true && row["ticks"].canConvertToInt())
                require(row["names"]?.isArray == true && row["names"].all { it.isTextual })
                val ticks = row["ticks"].asInt()
                val names = row["names"].map { it.asText() }
                validate(ticks, names)
                require(loaded.put(mode to names.size, Record(mode, ticks, names)) == null)
            }
            return loaded
        } catch (e: Exception) {
            throw IllegalStateException("PROBLEM unreadable ToA server records: ${file.name}; existing file preserved", e)
        }
    }

    private fun validate(ticks: Int, names: List<String>) {
        require(ticks > 0 && names.size in 1..8)
        require(names.all { it.isNotBlank() && it.none { c -> c == '<' || c == '>' || c.isISOControl() } })
        require(names.map { it.lowercase(java.util.Locale.ROOT) }.distinct().size == names.size)
    }

    companion object : KLogging() {
        val server: ToaRecordStore by lazy {
            ToaRecordStore(SharedDataFiles.of(RspsPaths.data("toa/records.json")))
        }

        /** Invoked once by the completed hook, after rewards; it never writes player saves. */
        fun completed(raid: ToaRaid, completion: ToaCompletion, store: ToaRecordStore = server) {
            // No partial/test-driver room clears in the public records table.
            if (completion.elapsedCycles <= 0 || raid.stats.roomCycles.size != 9) return
            val names = completion.roster.map { raid.memberNames[it] ?: return }
            store.submit(ToaMode.of(completion.settings.raidLevel), completion.elapsedCycles, names)
        }
    }
}
