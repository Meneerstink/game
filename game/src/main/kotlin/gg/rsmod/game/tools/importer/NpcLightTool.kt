package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * N606 13-11 (Ba-Ba-keien): sets the lighting bytes of given npcs - opcode 100 (ambient) and 101 (contrast) - for MEASURING how the
 * 667 client draws an OSRS-imported npc against an OSRS in-game screenshot (the OSRS boulders render 2.2x brighter than ours).
 * Prints OLD -> NEW and refuses when the current bytes are not the expected ones (so a run can always be undone with swapped values).
 * Every other definition segment is copied byte for byte and re-checked. Both caches, one [CacheTransaction].
 *
 * Usage: `NpcLightTool plan|apply <npc>=<expectedAmbient>,<expectedContrast>:<newAmbient>,<newContrast> ...`
 */
object NpcLightTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply <npc>=<expA>,<expC>:<newA>,<newC> ..." }
        val jobs = args.drop(1).map { a ->
            val (npc, rest) = a.split('=')
            val (exp, new) = rest.split(':')
            val (ea, ec) = exp.split(',').map { it.toInt() }
            val (na, nc) = new.split(',').map { it.toInt() }
            listOf(npc.toInt(), ea, ec, na, nc)
        }
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            for ((id, ea, ec, na, nc) in jobs) {
                val current = library.data(OsrsLightingRepairTool.INDEX_NPC, id ushr 7, id and 0x7F) ?: error("npc $id missing")
                val segments = OsrsLightingRepairTool.npcSegments(current)
                val a = OsrsLightingRepairTool.byteField(current, segments, 100)
                val c = OsrsLightingRepairTool.byteField(current, segments, 101)
                if (a != ea || c != ec) { println("SKIP npc $id: ambient/contrast $a,$c is not the expected $ea,$ec"); continue }
                if (a == na && c == nc) { println("NOTHING npc $id: $a,$c"); continue }
                val repaired = OsrsLightingRepairTool.rewrite(current, segments, mapOf(100 to na, 101 to nc))
                val strip = { b: ByteArray -> OsrsLightingRepairTool.npcSegments(b).filter { it.first != 100 && it.first != 101 }.map { b.copyOfRange(it.second, it.third).toList() } }
                check(strip(current) == strip(repaired)) { "npc $id: re-encode changed other fields" }
                val rs = OsrsLightingRepairTool.npcSegments(repaired)
                check(OsrsLightingRepairTool.byteField(repaired, rs, 100) == na && OsrsLightingRepairTool.byteField(repaired, rs, 101) == nc)
                println("CHANGE npc $id: ambient $a -> $na, contrast $c -> $nc")
                mutations += CacheMutation(OsrsLightingRepairTool.INDEX_NPC, id ushr 7, id and 0x7F, repaired, "npc $id light $a,$c -> $na,$nc (N606 meting)", CacheItemProbeTool.sha1(current))
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(OsrsItemImportTool.TARGETS, mutations)
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (args[0] == "plan") return
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes")
    }

}
