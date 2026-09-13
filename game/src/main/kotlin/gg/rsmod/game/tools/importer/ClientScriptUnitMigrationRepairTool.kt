package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/** Repairs script 2916 after the original x10 removal left relative branch offsets unchanged. */
object ClientScriptUnitMigrationRepairTool {
    private const val CLIENTSCRIPT_INDEX = 12
    private const val SCRIPT_ID = 2916

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "Usage: <pristineCache> <gameCache>;<fileServerCache> <journalRoot>" }
        val pristineCache = args[0]
        val targets = args[1].split(';').filter { it.isNotBlank() }
        require(targets.size == 2) { "Second argument must contain exactly two cache paths separated by ';'" }

        val pristine = CacheLibrary(pristineCache)
        val original = try {
            requireNotNull(pristine.data(CLIENTSCRIPT_INDEX, SCRIPT_ID, 0)) { "Missing pristine clientscript $SCRIPT_ID" }
        } finally {
            pristine.close()
        }
        val repaired = ClientScriptUnitMigrationTool.migrate(SCRIPT_ID, original)

        val targetLibrary = CacheLibrary(targets.first())
        val current = try {
            requireNotNull(targetLibrary.data(CLIENTSCRIPT_INDEX, SCRIPT_ID, 0)) { "Missing target clientscript $SCRIPT_ID" }
        } finally {
            targetLibrary.close()
        }
        if (current.contentEquals(repaired)) {
            println("CLIENTSCRIPT_UNIT_REPAIR script=$SCRIPT_ID already_repaired=true")
            return
        }

        val mutation = CacheMutation(
            indexId = CLIENTSCRIPT_INDEX,
            groupId = SCRIPT_ID,
            fileId = 0,
            newBytes = repaired,
            label = "clientscript $SCRIPT_ID: repair relative branch after x10 removal",
            expectedCurrentSha1 = CacheItemProbeTool.sha1(current),
        )
        val transaction = CacheTransaction(targets, listOf(mutation), journalRoot = File(args[2]))
        val plan = transaction.preflight()
        println(plan.joinToString("\n"))
        check(transaction.blockingErrors(plan).isEmpty()) { transaction.blockingErrors(plan).joinToString("\n") }
        val result = transaction.apply(plan)
        val problems = transaction.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("CLIENTSCRIPT_UNIT_REPAIR transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
