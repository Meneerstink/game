package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * N462: OSRS -> local ids of everything imported for ToA (npc, seq, spotanim, item, synth, loc), read from data/cfg/toa/ids.tsv - written
 * by Claude's import scripts (work/claude-scripts/toa_ids.py + toa_locmap.py) after every import, so no id is typed by hand.
 */
object ToaIds {
    private val table: Map<String, Int> by lazy {
        val file = File(RspsPaths.data("cfg/toa/ids.tsv"))
        if (!file.isFile) emptyMap()
        else file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 3) null else "${f[0]}:${f[1]}" to (f[2].toIntOrNull() ?: return@mapNotNull null)
        }.toMap()
    }

    fun npc(osrs: Int): Int? = table["npc:$osrs"]
    fun seq(osrs: Int): Int? = table["seq:$osrs"]
    fun spot(osrs: Int): Int? = table["spotanim:$osrs"]
    fun item(osrs: Int): Int? = table["item:$osrs"]
    fun loc(osrs: Int): Int? = table["loc:$osrs"]
}
