package gg.rsmod.game.action

import gg.rsmod.util.io.SharedDataFiles
import java.io.File

/**
 * Bounded diagnostic registry of every object click the server could not handle (masterplan M2: "Fallback registreert
 * iedere ongehandelde click met de vereiste velden in een begrensde diagnostische registratie; aggregeer
 * consoleherhalingen"). One row per distinct (object id, transform, option, tile); repeats only raise its count, so a
 * player clicking the same dead door does not grow the file. The live world itself is a census source for the navigation audit.
 *
 * N9 (LC01 2026-10-14: 104 rows more than once, e.g. 17x the same button): the counts lived only in memory, so after every restart
 * the first click of a known row appended it again. The file is now read back once (duplicate rows of older builds merged), every
 * row carries a `count` column, and the file is rewritten - at once for a new row, at most every [FLUSH_MS] for a repeat.
 */
object UnhandledInteractions {
    const val MAX_ROWS = 5000
    const val FLUSH_MS = 30_000L
    private const val HEADER = "kind\tid\ttransform\tname\topt\toption\ttype\trot\tx\tz\theight\tcount"
    private const val ROW_FIELDS = 11

    val FILE get() = SharedDataFiles.of("./logs/unhandled-object-actions.tsv", createIfMissing = false)

    /** The file this registry reads and writes; tests point it elsewhere. */
    @Volatile
    var file: () -> File = { FILE }

    data class Key(val id: Int, val transform: Int, val option: Int, val x: Int, val z: Int, val height: Int)

    /** Row text (the 11 fields, tab separated) -> count, in first-seen order. */
    private val rows = LinkedHashMap<String, Int>()
    private val keyRows = HashMap<Key, String>()
    private var loaded = false
    private var lastFlush = 0L

    /** Records one unhandled click; returns true when this is a new row. */
    fun record(key: Key, name: String, optionText: String?, type: Int, rot: Int, kind: String = "option"): Boolean {
        val row = "$kind\t${key.id}\t${key.transform}\t$name\t${key.option}\t${optionText ?: ""}\t$type\t$rot\t${key.x}\t${key.z}\t${key.height}"
        synchronized(this) { keyRows[key] = row }
        return add(row)
    }

    /** Records an unresolved non-object interaction at the shared dispatch boundary. */
    fun recordInteraction(
        kind: String,
        id: Int,
        option: Int,
        name: String,
        context: String,
    ): Boolean = add("$kind\t$id\t$id\t$name\t$option\t$context\t0\t0\t0\t0\t0")

    @Synchronized
    private fun add(row: String): Boolean {
        load()
        val previous = rows[row]
        if (previous != null) {
            rows[row] = previous + 1
            flush(force = false)
            return false
        }
        if (rows.size >= MAX_ROWS) return false
        rows[row] = 1
        flush(force = true)
        return true
    }

    /** Reads the file once: old rows without a count column count 1 each, so duplicates of older builds add up into one row. */
    private fun load() {
        if (loaded) return
        loaded = true
        try {
            val f = file()
            if (!f.isFile) return
            f.readLines().drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val fields = line.split('\t')
                if (fields.size < ROW_FIELDS) return@forEach
                val row = fields.take(ROW_FIELDS).joinToString("\t")
                val count = fields.getOrNull(ROW_FIELDS)?.toIntOrNull() ?: 1
                rows[row] = (rows[row] ?: 0) + count
            }
        } catch (_: Exception) {
            // Diagnostics must never break gameplay.
        }
    }

    private fun flush(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastFlush < FLUSH_MS) return
        lastFlush = now
        try {
            val f = file()
            f.parentFile?.mkdirs()
            f.writeText(HEADER + "\n" + rows.entries.joinToString("") { (row, count) -> "$row\t$count\n" })
        } catch (_: Exception) {
            // Diagnostics must never break gameplay.
        }
    }

    @Synchronized
    fun count(key: Key): Int {
        load()
        return keyRows[key]?.let { rows[it] } ?: 0
    }

    @Synchronized
    fun size(): Int {
        load()
        return rows.size
    }

    /** Tests: forget the in-memory state so the next call reads [file] again. */
    @Synchronized
    fun reset() {
        rows.clear()
        keyRows.clear()
        loaded = false
        lastFlush = 0L
    }
}
