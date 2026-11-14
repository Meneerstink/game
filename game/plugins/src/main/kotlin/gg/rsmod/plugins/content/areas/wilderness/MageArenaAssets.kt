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
            val identity = if (columns[0] == "variant") columns[1] else columns[1].toInt().toString()
            "${columns[0]}:$identity" to columns[2].toInt()
        }
    }
    fun item(upstream: Int): Int? = table["item:$upstream"]
    fun npc(upstream: Int): Int? = table["npc:$upstream"]
    fun sequence(upstream: Int): Int? = table["seq:$upstream"]
    fun spotanim(upstream: Int): Int? = table["spotanim:$upstream"]
    /** N416-style clones have no OSRS item id; their allocated local ids are recorded by name. */
    fun variant(identity: String): Int? = table["variant:$identity"]
}
