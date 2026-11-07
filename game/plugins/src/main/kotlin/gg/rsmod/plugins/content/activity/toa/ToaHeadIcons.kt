package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.RspsPaths
import gg.rsmod.game.model.entity.Npc
import java.io.File

/**
 * N462 boss overhead prayers. OSRS gives a boss one npc per protection prayer (cache2686 npc opcode 102 headicons, archive 440 frame:
 * Akkha 11790 [0] melee / 11791 [1] ranged / 11792 [2] magic / 11793-11796 [6] magic+missiles; Elidinis/Tumeken P2 [7] / [8], P3 [9]).
 * Our import keeps one local npc per model set, so NpcHeadIconCloneTool made copies with 667 opcode 102 (data/cfg/toa/headicons.tsv:
 * local npc, 667 frame, copy). OSRS -> 667 frame (both sheets compared 07-11): 0->0, 1->1, 2->2, 6->6, 7->22 (melee+ranged),
 * 8->23 (melee+magic), 9->24 (all three).
 *
 * Usage from a boss card: ToaHeadIcons.show(npc, osrsFrame) / ToaHeadIcons.clear(npc). The npc keeps its own id; only its transmog
 * changes, so its size, seqs (same models/rig) and combat state stay.
 */
object ToaHeadIcons {
    val OSRS_TO_667 = mapOf(0 to 0, 1 to 1, 2 to 2, 6 to 6, 7 to 22, 8 to 23, 9 to 24)

    private val table: Map<Pair<Int, Int>, Int> by lazy {
        val file = File(RspsPaths.data("cfg/toa/headicons.tsv"))
        if (!file.isFile) emptyMap() else file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 3) null else (f[0].toInt() to f[1].toInt()) to f[2].toInt()
        }.toMap()
    }

    /** The copy of [localNpc] that shows OSRS head icon [osrsFrame], or null when it was not made. */
    fun copyOf(localNpc: Int, osrsFrame: Int): Int? = OSRS_TO_667[osrsFrame]?.let { table[localNpc to it] }

    /** Show OSRS overhead [osrsFrame] on [npc] (by its own id); false when no copy exists. */
    fun show(npc: Npc, osrsFrame: Int): Boolean {
        val copy = copyOf(npc.id, osrsFrame) ?: return false
        if (npc.getTransmogId() != copy) npc.setTransmogId(copy)
        return true
    }

    fun clear(npc: Npc) {
        if (table.values.contains(npc.getTransmogId())) npc.setTransmogId(-1)
    }
}
