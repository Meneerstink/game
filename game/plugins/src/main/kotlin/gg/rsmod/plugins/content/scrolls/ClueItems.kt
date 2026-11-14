package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.RspsPaths
import java.io.File

/** Verified OSRS-to-local identities. Missing imports have no fallback to an unrelated 667 ID. */
object ClueItems {
    private val existing = mapOf(2677 to 2677, 2801 to 2801, 2722 to 2722, 12073 to 19043,
        20546 to 2714, 20545 to 2802, 20544 to 2724, 20543 to 19039, 23184 to 23729)

    fun parse(lines: List<String>): Map<Int, Int> = lines.filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
        val fields = line.split('\t')
        require(fields.size >= 2) { "Invalid clue item mapping: $line" }
        fields[0].toInt() to fields[1].toInt()
    }

    private val mappings by lazy {
        val file = File(RspsPaths.data("cfg/clue/items.tsv"))
        existing + if (file.isFile) parse(file.readLines()) else emptyMap()
    }

    fun local(osrs: Int): Int? = mappings[osrs]
}
