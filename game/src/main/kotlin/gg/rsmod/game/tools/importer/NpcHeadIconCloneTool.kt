package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * N462 (07-11): OSRS gives one npc per prayer a boss protects with (Akkha 11790 melee / 11791 ranged / 11792 magic / 11793 enrage,
 * the Wardens 11753/11754/11763 ...: cache2686 npc opcode 102 headicons, archive 440 frame). Our import keeps ONE local npc per
 * model set, so the overhead prayer was lost. This tool copies a local 667 NPCType and gives the copy 667 opcode 102 headIcon
 * (client NPCType.java:361 -> OverlayManager draws Sprites.headiconsPrayer[headIcon]); the server transmogs the boss to the copy.
 *
 * Usage: runNpcHeadIconCloneTool --args="<localNpc>:<headIcon>[,<localNpc>:<headIcon>...] [--apply]"
 * Writes data/cfg/toa/headicons.tsv (local npc, head icon, clone id) - appended, an existing pair is reused.
 */
object NpcHeadIconCloneTool {
    private const val INDEX_NPC = 18
    private val TABLE = File(RspsPaths.data("cfg/toa/headicons.tsv"))

    /** The 667 NPCType bytes with opcode 102 set to [headIcon] (an existing 102 is replaced; added before the terminating 0). */
    fun withHeadIcon(def: ByteArray, headIcon: Int): ByteArray {
        require(headIcon in 0..0xFFFF)
        require(def.isNotEmpty() && def.last() == 0.toByte()) { "NPCType must end with opcode 0" }
        val body = def.copyOf(def.size - 1)
        return body + byteArrayOf(102, (headIcon ushr 8).toByte(), headIcon.toByte(), 0)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val pairs = args.first().split(',').map { it.split(':').let { (n, h) -> n.trim().toInt() to h.trim().toInt() } }
        val apply = "--apply" in args
        val existing = if (TABLE.isFile) TABLE.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }.associate { (it[0].toInt() to it[1].toInt()) to it[2].toInt() } else emptyMap()
        val mutations = ArrayList<CacheMutation>()
        val created = LinkedHashMap<Pair<Int, Int>, Int>()
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            val index = library.index(INDEX_NPC)
            val lastGroup = index.archiveIds().maxOrNull() ?: 0
            var next = (lastGroup shl 7) + (index.archive(lastGroup)?.fileIds()?.maxOrNull() ?: -1) + 1
            for (pair in pairs) {
                if (pair in existing || pair in created) continue
                val (npc, icon) = pair
                val def = library.data(INDEX_NPC, npc ushr 7, npc and 0x7F) ?: error("local npc $npc missing")
                // A def that already ends in a 102 of ours is fine to re-copy: the last 102 wins in the decoder.
                val clone = next++
                mutations += CacheMutation(INDEX_NPC, clone ushr 7, clone and 0x7F, withHeadIcon(def, icon), "npc $npc headicon $icon -> $clone")
                created[pair] = clone
                println("PLAN npc $npc headIcon=$icon -> clone $clone")
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO (all pairs already in $TABLE)"); return }
        val tx = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = tx.preflight()
        val errors = tx.blockingErrors(plan)
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) { println("DRY_RUN ${mutations.size} clones"); return }
        val result = tx.apply(plan)
        val problems = tx.verify()
        problems.forEach { println("  VERIFY_FAILURE: $it") }
        check(problems.isEmpty()) { "verify failed - journal ${result.journalDir}" }
        TABLE.parentFile.mkdirs()
        if (!TABLE.isFile) TABLE.writeText("# N462 NpcHeadIconCloneTool: local npc, 667 headicons_prayer frame, clone npc - generated, do not edit\n")
        TABLE.appendText(created.entries.joinToString("") { (k, v) -> "${k.first}\t${k.second}\t$v\n" })
        println("APPLIED transaction=${tx.id} writes=${result.applied} VERIFY_OK clones=${created.values}")
    }
}
