package gg.rsmod.game.action

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded diagnostic registry of every object click the server could not handle (masterplan M2: "Fallback registreert
 * iedere ongehandelde click met de vereiste velden in een begrensde diagnostische registratie; aggregeer
 * consoleherhalingen"). One row per distinct (object id, transform, option, tile); repeats only raise its count, so a
 * player clicking the same dead door does not grow the file. New rows are appended to [FILE] as they appear, which makes
 * the live world itself a census source for the navigation audit.
 */
object UnhandledInteractions {
    const val MAX_ROWS = 5000
    val FILE = File("./logs/unhandled-object-actions.tsv")

    data class Key(val id: Int, val transform: Int, val option: Int, val x: Int, val z: Int, val height: Int)

    private data class GenericKey(val kind: String, val id: Int, val option: Int, val context: String)

    private val counts = ConcurrentHashMap<Key, Int>()
    private val genericCounts = ConcurrentHashMap<GenericKey, Int>()

    /** Records one unhandled click; returns true when this is a new row. */
    fun record(key: Key, name: String, optionText: String?, type: Int, rot: Int, kind: String = "option"): Boolean {
        val previous = counts[key]
        if (previous != null) {
            counts[key] = previous + 1
            return false
        }
        if (counts.size >= MAX_ROWS) return false
        counts[key] = 1
        try {
            FILE.parentFile?.mkdirs()
            val header = !FILE.exists()
            FILE.appendText(
                (if (header) "kind\tid\ttransform\tname\topt\toption\ttype\trot\tx\tz\theight\n" else "") +
                    "$kind\t${key.id}\t${key.transform}\t$name\t${key.option}\t${optionText ?: ""}\t$type\t$rot\t${key.x}\t${key.z}\t${key.height}\n",
            )
        } catch (_: Exception) {
            // Diagnostics must never break gameplay.
        }
        return true
    }

    /** Records an unresolved non-object interaction at the shared dispatch boundary. */
    fun recordInteraction(
        kind: String,
        id: Int,
        option: Int,
        name: String,
        context: String,
    ): Boolean {
        val key = GenericKey(kind, id, option, context)
        val previous = genericCounts[key]
        if (previous != null) {
            genericCounts[key] = previous + 1
            return false
        }
        if (size() >= MAX_ROWS) return false
        genericCounts[key] = 1
        try {
            FILE.parentFile?.mkdirs()
            val header = !FILE.exists()
            FILE.appendText(
                (if (header) "kind\tid\ttransform\tname\topt\toption\ttype\trot\tx\tz\theight\n" else "") +
                    "$kind\t$id\t$id\t$name\t$option\t$context\t0\t0\t0\t0\t0\n",
            )
        } catch (_: Exception) {
            // Diagnostics must never break gameplay.
        }
        return true
    }

    fun count(key: Key): Int = counts[key] ?: 0

    fun size(): Int = counts.size + genericCounts.size
}
