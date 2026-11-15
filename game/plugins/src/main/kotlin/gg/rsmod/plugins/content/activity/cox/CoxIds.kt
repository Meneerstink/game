package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * CoX: OSRS -> local ids of everything imported for the Chambers of Xeric (npc, seq, spotanim, item, synth, loc), read from
 * data/cfg/cox/ids.tsv - written by work/claude-scripts/cox_ids.py after every import (same pattern as ToaIds), so no id is typed by hand.
 * Code names an OSRS id by its gameval name in a comment (reference/runelite/gameval) and asks this table for the local one.
 */
object CoxIds {
    private val table: Map<String, Int> by lazy {
        val file = File(RspsPaths.data("cfg/cox/ids.tsv"))
        if (!file.isFile) emptyMap()
        else file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 3) null else "${f[0]}:${f[1]}" to (f[2].toIntOrNull() ?: return@mapNotNull null)
        }.toMap()
    }

    fun npc(osrs: Int): Int? = table["npc:$osrs"]
    fun npcSeq(osrsNpc: Int, osrsSeq: Int): Int? = table["npcseq:$osrsNpc:$osrsSeq"] ?: table["seq:$osrsSeq"]
    fun seq(osrs: Int): Int? = table["seq:$osrs"]
    fun spot(osrs: Int): Int? = table["spotanim:$osrs"]
    fun item(osrs: Int): Int? = table["item:$osrs"]
    fun synth(osrs: Int): Int? = table["synth:$osrs"]
    fun loc(osrs: Int): Int? = table["loc:$osrs"]
}
