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

    /** Local npc -> the OSRS npc it was imported from (variants with the same models share one local npc; this is the imported one). */
    private val osrsNpcs: Map<Int, Int> by lazy { table.filterKeys { it.startsWith("npc:") }.entries.associate { (k, v) -> v to k.removePrefix("npc:").toInt() } }
    fun osrsNpcOf(local: Int): Int? = osrsNpcs[local]

    /**
     * The local sequence of OSRS [osrsSeq] FOR npc [osrsNpc]: a skeletal npc's sequences are baked per npc (own frames and skin), so the
     * same OSRS sequence has a different local id for Ba-Ba and for each baboon. Falls back to the npc-less mapping (classic sequences).
     */
    fun npcSeq(osrsNpc: Int, osrsSeq: Int): Int? = table["npcseq:$osrsNpc:$osrsSeq"] ?: table["seq:$osrsSeq"]
    fun seq(osrs: Int): Int? = table["seq:$osrs"]
    fun spot(osrs: Int): Int? = table["spotanim:$osrs"]
    fun item(osrs: Int): Int? = table["item:$osrs"]

    /** Local item -> the OSRS item it was imported from (ToA supplies: dose arithmetic on the OSRS ids). */
    private val osrsItems: Map<Int, Int> by lazy { table.filterKeys { it.startsWith("item:") }.entries.associate { (k, v) -> v to k.removePrefix("item:").toInt() } }
    fun itemSource(local: Int): Int? = osrsItems[local]
    fun loc(osrs: Int): Int? = table["loc:$osrs"]
}
