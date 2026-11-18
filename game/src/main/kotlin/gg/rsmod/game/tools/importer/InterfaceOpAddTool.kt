package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * DMM2 punt 2 (owner 18-11 15:5x): add a right-click op to an existing rev-667 interface component, e.g. 'Animation' on the Home
 * Teleport of the three spellbooks (OSRS 218 Home Teleport carries ops 'Cast' + 'Animation'; wiki Animation overrides: "toggled by
 * right-clicking 'Animation' from the home teleport in the spellbook").
 *
 * The component's op list ([Component.decode]: opBase string, then opFlags byte = opCount in the low nibble, then the op strings)
 * is rewritten in place: `<flags> <op1>\0 ... ` -> `<flags+1> <op1>\0 ... <new>\0`. The rest of the bytes stay identical. The new
 * bytes must decode (InterfaceHookProbeTool.componentOps) to the old ops plus the new one; each mutation is pinned on the CURRENT
 * bytes and journaled (CacheRollbackTool <tx> --apply puts them back). The op only shows/clicks where the server enables its event
 * bit (IF_SETEVENTS), so a server can offer it to some players only.
 *
 * Usage: `InterfaceOpAddTool plan|apply <interface>:<component>=<op text> ...`
 */
object InterfaceOpAddTool {
    private const val INTERFACE_INDEX = 3

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply <interface>:<component>=<op text> ..." }
        val mode = args[0]
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        val mutations = ArrayList<CacheMutation>()
        try {
            for (job in args.drop(1)) {
                val (key, text) = job.split('=', limit = 2)
                val (inter, comp) = key.split(':').map { it.toInt() }
                val current = library.data(INTERFACE_INDEX, inter, comp) ?: error("component $inter:$comp missing")
                val ops = InterfaceHookProbeTool.componentOps(current)
                if (ops.any { it.equals(text, true) }) { println("SKIP $inter:$comp already has '$text' ops=$ops"); continue }
                check(ops.isNotEmpty() && ops.size < 15 && ops.none { it == null }) { "$inter:$comp ops=$ops: only a plain non-empty op list is supported" }
                val oldSection = byteArrayOf(ops.size.toByte()) + ops.joinToString("") { "$it\u0000" }.toByteArray(Charsets.ISO_8859_1)
                val at = indexOfUnique(current, oldSection)
                val newSection = byteArrayOf((ops.size + 1).toByte()) +
                    (ops + text).joinToString("") { "$it\u0000" }.toByteArray(Charsets.ISO_8859_1)
                val next = current.copyOfRange(0, at) + newSection + current.copyOfRange(at + oldSection.size, current.size)
                val nextOps = InterfaceHookProbeTool.componentOps(next)
                check(nextOps == ops + text) { "$inter:$comp decodes to $nextOps after the edit" }
                println("COMPONENT $inter:$comp ops=$ops -> $nextOps (${current.size} -> ${next.size} bytes)")
                mutations += CacheMutation(INTERFACE_INDEX, inter, comp, next, "if $inter:$comp op '$text' (InterfaceOpAddTool)", CacheItemProbeTool.sha1(current))
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

    /** The one place [needle] occurs in [data]; two or none = refuse (the op list must be found unambiguously). */
    private fun indexOfUnique(data: ByteArray, needle: ByteArray): Int {
        val hits = (0..data.size - needle.size).filter { i -> needle.indices.all { data[i + it] == needle[it] } }
        check(hits.size == 1) { "op list found ${hits.size} times" }
        return hits[0]
    }
}
