package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.RspsPaths
import java.io.File

/** Deployable output of MageArenaBindingsTool; never requires the development import ledger on the live server. */
object MageArenaAssets {
    private val table by lazy {
        val file = File(RspsPaths.data("cfg/mage-arena/ids.tsv"))
        if (!file.isFile) emptyMap() else file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
            val columns = line.split('\t')
            require(columns.size == 3) { "Invalid N415 binding: expected kind, upstream id and finalized local id" }
            "${columns[0]}:${columns[1].toInt()}" to columns[2].toInt()
        }
    }
    fun item(upstream: Int): Int? = table["item:$upstream"]
}
