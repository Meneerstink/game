package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Removes ONE file (index/group/file) from both caches in a paired [CacheTransaction] (preflight, journal, apply, verify), e.g. a
 * component created at the wrong id. Refuses unless both caches hold the same bytes there (sha1 printed and required with --apply).
 * N72C 25-11 (Claude): 192:101 was created by --monster-info-only while 192:100 stayed empty - the gap made the 667 client throw a
 * NullPointerException in executeOnLoad when the spellbook opened at login (client log 07:05, testserver).
 *
 * Usage: `bash work/claude-scripts/kc_tool.sh CacheFileDeleteTool <index> <group> <file> [--apply <sha1>]`.
 */
object CacheFileDeleteTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3 || (args.size == 5 && args[3] == "--apply")) { "Usage: <index> <group> <file> [--apply <sha1>]" }
        val (index, group, file) = args.take(3).map { it.toInt() }
        val current = OsrsItemImportTool.TARGETS.map { target ->
            val library = CacheLibrary(target)
            try { library.data(index, group, file) } finally { library.close() }
        }
        val first = current[0] ?: error("idx$index grp$group file$file is absent in ${OsrsItemImportTool.TARGETS[0]}")
        check(current.all { it != null && it.contentEquals(first) }) { "the caches differ at idx$index grp$group file$file; refusing" }
        val sha1 = CacheItemProbeTool.sha1(first)
        println("CURRENT idx$index grp$group file$file ${first.size} bytes sha1=$sha1")
        val mutation = CacheMutation(index, group, file, null, "delete idx$index grp$group file$file", sha1)
        val transaction = CacheTransaction(OsrsItemImportTool.TARGETS, listOf(mutation))
        val preflight = transaction.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val errors = transaction.blockingErrors(preflight)
        errors.forEach { println("BLOCKED $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (args.size == 3) { println("DRY_RUN_OK no cache writes"); return }
        check(args[4] == sha1) { "sha1 mismatch: expected ${args[4]}, cache holds $sha1" }
        val result = transaction.apply(preflight)
        check(transaction.verify().isEmpty()) { "verify failed: ${transaction.journalDir}" }
        println("APPLIED ${result.applied} skipped=${result.skipped} journal=${result.journalDir}")
    }
}
