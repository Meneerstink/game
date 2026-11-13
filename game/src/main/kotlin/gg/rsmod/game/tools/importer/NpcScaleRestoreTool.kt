package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.ByteArrayOutputStream

/**
 * N606 12-11 23:4x (toa_defcompare.py, eigenaar 'veel grafische issues ... tooling'): the skeletal ToA npc import dropped the OSRS
 * NPCType scale (opcodes 97/98). The Ba-Ba room baboons (OSRS 11712-11714, scale 38/38) and the Scarab (11697, 60/60) therefore
 * render at 128 = the size of the puzzle baboons (client modeltrace: 15213 and 15110 the same height 1524) - about 3.4x / 2.1x too big.
 * Restores exactly those two opcodes from cache2686 (50-npc-defs.tsv: scaleX/scaleY); every other opcode byte stays as it is.
 * One CacheTransaction over the game- and fileserver cache, idempotent. Usage: plan|apply
 */
object NpcScaleRestoreTool {
    private const val NPC_INDEX = 18
    /** local npc -> (OSRS npc, scaleX, scaleY) from cache2686 2/9/<osrs>. */
    val FIXES = mapOf(15213 to Triple(11712, 38, 38), 15214 to Triple(11713, 38, 38), 15215 to Triple(11714, 38, 38),
        15216 to Triple(11697, 60, 60))

    fun withScale(def: ByteArray, x: Int, y: Int): ByteArray = ByteArrayOutputStream().use { out ->
        NpcLookCopyTool.segments(def).filter { it.first != 97 && it.first != 98 }.forEach { out.write(it.second) }
        out.write(97); out.write(x ushr 8); out.write(x and 0xFF)
        out.write(98); out.write(y ushr 8); out.write(y and 0xFF)
        out.write(0)
        out.toByteArray()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val targets = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(targets[0])
        try {
            for ((local, fix) in FIXES) {
                val def = library.data(NPC_INDEX, local ushr 7, local and 0x7F) ?: error("npc $local missing")
                val wanted = withScale(def, fix.second, fix.third)
                val kept = NpcLookCopyTool.segments(def).filter { it.first != 97 && it.first != 98 }.map { it.first }
                check(NpcLookCopyTool.segments(wanted).map { it.first }.filter { it != 97 && it != 98 } == kept) { "npc $local: other opcodes changed" }
                // 13-11 les: read the CURRENT values first - 12-11 23:5x this 'fix' rested on a decoder artefact (old python reader stopped
                // at opcode 241) while the cache already held 38/38 + 60/60 (journal tx-20261112-225806): only re-ordered bytes.
                val cur = NpcLookCopyTool.segments(def).filter { it.first == 97 || it.first == 98 }
                    .associate { it.first to (((it.second[1].toInt() and 0xFF) shl 8) or (it.second[2].toInt() and 0xFF)) }
                if (cur[97] == fix.second && cur[98] == fix.third) { println("NOTHING npc $local already scale ${cur[97]}/${cur[98]}"); continue }
                if (wanted.contentEquals(def)) { println("NOTHING npc $local already scale ${fix.second}/${fix.third}"); continue }
                println("  was ${cur[97] ?: 128}/${cur[98] ?: 128}")
                println("SCALE npc $local (OSRS ${fix.first}) -> ${fix.second}/${fix.third} (${def.size} -> ${wanted.size} bytes)")
                mutations += CacheMutation(NPC_INDEX, local ushr 7, local and 0x7F, wanted, "npc $local OSRS ${fix.first} scale ${fix.second}/${fix.third}",
                    CacheItemProbeTool.sha1(def))
            }
        } finally { library.close() }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(targets = targets, mutations = mutations)
        val plan = tx.preflight()
        val errors = tx.blockingErrors(plan)
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (mode == "plan") { println("PLAN_ONLY transaction=${tx.id} mutations=${mutations.size}"); return }
        val applied = tx.apply(plan)
        check(tx.verify().isEmpty()) { "verify failed ${tx.id}" }
        println("APPLIED ${applied.applied} skipped=${applied.skipped} VERIFY_OK transaction=${tx.id}")
    }
}
