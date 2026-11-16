package gg.rsmod.game.tools.importer

import java.io.File

/**
 * Rolls one applied [CacheTransaction] back from its journal (`<journalRoot>/<tx>/plan.txt` + the `.orig`/`.absent` files) on both
 * caches - for an import that turned out wrong after `--apply` (16-11: CoX 'cox-reload' wrote multiloc transforms above 65535 that the
 * 667 loc format stores as unsigned shorts). Both servers must be stopped (the caches are written).
 *
 * Usage: CacheRollbackTool <tx-id> [--apply]   (without --apply: only lists what it would restore)
 */
object CacheRollbackTool {
    private val LOCATION = Regex("""location=idx(\d+)_grp(\d+)_file(\d+)""")
    private val TARGET = Regex("""target=([^\t]+)""")

    @JvmStatic
    fun main(args: Array<String>) {
        val tx = args.getOrNull(0) ?: error("Usage: CacheRollbackTool <tx-id> [--apply]")
        val apply = "--apply" in args
        val root = File(RspsPathsBridge.journalRoot())
        val plan = File(File(root, tx), "plan.txt")
        check(plan.isFile) { "no journal plan $plan" }
        val lines = plan.readLines().filter { it.startsWith("plan\t") }
        val targets = lines.mapNotNull { TARGET.find(it)?.groupValues?.get(1) }.distinct()
        val mutations = lines.mapNotNull { LOCATION.find(it) }.map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.distinct().map { (idx, grp, file) ->
            CacheMutation(idx.toInt(), grp.toInt(), file.toInt(), null, "rollback $tx idx$idx grp$grp file$file")
        }
        println("ROLLBACK $tx targets=$targets locations=${mutations.size}")
        mutations.forEach { println("  ${it.describeLocation()}") }
        if (!apply) { println("DRY_RUN (add --apply)"); return }
        val restored = CacheTransaction(targets, mutations, id = tx).rollback()
        println("ROLLED_BACK $tx restored=$restored")
    }
}

private object RspsPathsBridge {
    fun journalRoot(): String = gg.rsmod.game.RspsPaths.root("import-journal")
}
