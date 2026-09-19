package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Restores native revision-667 NPC definitions (index 18) to their pristine openrs2 #1473 bytes
 * (`C:\RSPS\reference\openrs2_667\cache`), through [CacheTransaction] on both production caches.
 *
 * Owner 2026-09-19 (Deadman guards 100 % OSRS): Lucien 14256 had been reused as a Deadman guard post
 * (renamed "1337 guard", level 1337, Attack option stripped); that post no longer exists, so the npc
 * gets its own 667 definition back. Each write is pinned to the current production SHA-1.
 *
 * Usage: `./gradlew :game:runNpcPristineRestoreTool --args="<npcId> [npcId ...] [--apply]"`
 */
object NpcPristineRestoreTool {
    const val NPC_INDEX = 18
    const val PRISTINE_CACHE = "C:\\RSPS\\reference\\openrs2_667\\cache"

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val ids = args.filter { !it.startsWith("--") }.map { it.toInt() }
        require(ids.isNotEmpty()) { "Usage: <npcId> [npcId ...] [--apply]" }
        val targets = OsrsItemImportTool.TARGETS

        val source = read(PRISTINE_CACHE, ids)
        val current = read(targets.first(), ids)
        val mutations =
            ids.filter { !source.getValue(it).contentEquals(current.getValue(it)) }.map { id ->
                CacheMutation(
                    indexId = NPC_INDEX,
                    groupId = id ushr 7,
                    fileId = id and 0x7f,
                    newBytes = source.getValue(id),
                    label = "npc $id: restore revision-667 definition (openrs2 #1473)",
                    expectedCurrentSha1 = CacheItemProbeTool.sha1(current.getValue(id)),
                )
            }
        if (mutations.isEmpty()) {
            println("NPC_PRISTINE_RESTORE already_restored=true")
            return
        }
        val transaction = CacheTransaction(targets, mutations)
        val plan = transaction.preflight()
        println(plan.joinToString("\n"))
        val errors = transaction.blockingErrors(plan)
        check(errors.isEmpty()) { errors.joinToString("\n") }
        if (!apply) {
            println("DRY_RUN mutations=${mutations.size}")
            return
        }
        val result = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            println("ROLLED_BACK ${transaction.rollback()}")
            error(problems.joinToString("\n"))
        }
        println("NPC_PRISTINE_RESTORE transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }

    private fun read(
        path: String,
        ids: List<Int>,
    ): Map<Int, ByteArray> {
        require(File(path).isDirectory) { "cache $path missing" }
        val library = CacheLibrary(path)
        try {
            return ids.associateWith { id ->
                requireNotNull(library.data(NPC_INDEX, id ushr 7, id and 0x7f)) { "npc $id missing in $path" }
            }
        } finally {
            library.close()
        }
    }
}
