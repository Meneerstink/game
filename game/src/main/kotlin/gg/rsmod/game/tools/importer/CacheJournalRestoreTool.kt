package gg.rsmod.game.tools.importer

import java.io.File

/**
 * Writes files back from a [CacheTransaction] journal (`<target slug>__idx<i>_grp<g>_file<f>.orig`) through a new transaction
 * (preflight, journal, verify; refuses while the servers run). Only journal entries whose name matches [filter] and that the
 * journaled transaction REPLACED (an `.orig` without an `.absent`) are restored. The current content's SHA-1, read just before,
 * is the expected value, so a write in between still conflicts.
 *
 * Usage: `<journalDir> <regex over "idx<i>_grp<g>_file<f>"> [--apply]`
 */
object CacheJournalRestoreTool {
    private val LOCATION = Regex("""idx(\d+)_grp(\d+)_file(\d+)""")

    @JvmStatic
    fun main(args: Array<String>) {
        val journal = File(args.getOrNull(0) ?: error("Usage: <journalDir> <filter regex> [--apply]"))
        val filter = Regex(args.getOrNull(1) ?: error("Usage: <journalDir> <filter regex> [--apply]"))
        val apply = "--apply" in args
        val targets = OsrsItemImportTool.TARGETS
        val slugs = targets.associateBy { it.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_') }
        val names = journal.listFiles()!!.map { it.name }.toSet()
        val mutationsByTarget = mutableMapOf<String, MutableList<CacheMutation>>()
        names.filter { it.endsWith(".orig") && it.removeSuffix(".orig") + ".absent" !in names }.sorted().forEach { name ->
            val (slug, location) = name.removeSuffix(".orig").split("__", limit = 2)
            val target = slugs[slug] ?: error("unknown target slug $slug")
            if (!filter.containsMatchIn(location)) return@forEach
            val (index, group, file) = LOCATION.matchEntire(location)!!.destructured
            val library = com.displee.cache.CacheLibrary(target)
            val current = try { library.data(index.toInt(), group.toInt(), file.toInt()) } finally { library.close() }
            mutationsByTarget.getOrPut(target) { mutableListOf() } +=
                CacheMutation(
                    index.toInt(), group.toInt(), file.toInt(), File(journal, name).readBytes(), "restore $location from ${journal.name}",
                    expectedCurrentSha1 = current?.let { CacheItemProbeTool.sha1(it) },
                )
        }
        // Both caches must receive the same restore (they are kept byte-identical).
        val perTarget = mutationsByTarget.values.map { list -> list.map { "${it.indexId}/${it.groupId}/${it.fileId}" }.toSet() }.distinct()
        check(perTarget.size == 1 && mutationsByTarget.keys.size == targets.size) { "the targets' journals differ: $mutationsByTarget" }
        val mutations = mutationsByTarget.getValue(targets[0])
        println("RESTORE ${mutations.size} files per cache from ${journal.name}")
        // The expected SHA-1 must hold in every target: the caches are identical, so the first target's value applies to both.
        val transaction = CacheTransaction(targets = targets, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) return println("DRY RUN: pass --apply")
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }
}
