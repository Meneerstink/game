package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * GRAFISCH 17-11 (night run, CoX floors vs the OSRS video): replace whole rev-667 loc definitions with given bytes - for proofs and
 * small fixes where an existing tool has no verb (e.g. a definition without a model to prove which ground decoration makes the
 * speckles). Every new definition must decode with [Rev667LocType.decode]; each mutation is pinned on the CURRENT bytes, so a changed
 * cache blocks the preflight, and the journal keeps the old bytes (CacheRollbackTool <tx> --apply puts them back).
 *
 * Usage: `LocDefBytesTool plan|apply <localLoc>=<hex> ...` | `LocDefBytesTool plan|apply nomodel <localLoc> ...` (nomodel = the
 * current definition with its model opcode (1/5/241) removed - nothing to draw, everything else kept).
 */
object LocDefBytesTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply <localLoc>=<hex> ... | plan|apply nomodel <localLoc> ..." }
        val mode = args[0]
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        val mutations = ArrayList<CacheMutation>()
        try {
            val jobs: List<Pair<Int, (ByteArray) -> ByteArray>> =
                if (args[1] == "nomodel") {
                    args.drop(2).map { it.toInt() to ::withoutModel }
                } else {
                    args.drop(1).map { job ->
                        val (id, hex) = job.split('=')
                        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        id.toInt() to { _: ByteArray -> bytes }
                    }
                }
            for ((id, make) in jobs) {
                val current = library.data(FeroxImportTool.LOC_INDEX, id ushr 8, id and 0xFF) ?: error("loc $id missing")
                val next = make(current)
                Rev667LocType.decode(id, next) // full opcode walk: throws on a malformed definition
                println("LOC $id ${hexOf(current)} -> ${hexOf(next)}")
                if (next.contentEquals(current)) continue
                mutations += CacheMutation(FeroxImportTool.LOC_INDEX, id ushr 8, id and 0xFF, next, "loc $id def bytes (LocDefBytesTool)", CacheItemProbeTool.sha1(current))
            }
        } finally {
            library.close()
        }
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") return
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK transaction=${tx.id}")
    }

    /** The definition without its model opcode(s): walks the opcodes with [Rev667LocType.decode]'s offsets and drops 1/5/241. */
    private fun withoutModel(data: ByteArray): ByteArray {
        val type = Rev667LocType.decode(-1, data)
        val out = java.io.ByteArrayOutputStream()
        var removed = 0
        for ((index, op) in type.opcodes.withIndex()) {
            val start = type.opcodeOffsets[index]
            val end = if (index + 1 < type.opcodeOffsets.size) type.opcodeOffsets[index + 1] else data.size - 1
            if (op == 1 || op == 5 || op == 241) { removed++; continue }
            out.write(data, start, end - start)
        }
        check(removed > 0) { "no model opcode found" }
        out.write(0)
        return out.toByteArray()
    }

    private fun hexOf(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}
