package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Restores Vampyre bat sequences 4915/4916/4917 (attack/block/death) to their revision-667 bytes
 * (owner decision 2026-09-13).
 *
 * Both production caches carry non-667 versions of these three sequences that reference vorbis sounds
 * 7776/7781, which do not exist in the revision-667 vorbis index. The pristine openrs2 #1473 revision-667
 * cache (`C:\RSPS\openrs2_667 sounds`) is the source: there 4915/4916 carry synth sounds 6647/6649 and
 * 4917 is silent. Only these three sequence files (index 20, group 38, files 51-53) are written, each
 * pinned to the current production SHA-1 and applied through [CacheTransaction] to both caches.
 */
object VampyreBatSeqRestoreTool {
    const val SEQ_INDEX = 20
    val SEQ_IDS = intArrayOf(4915, 4916, 4917)

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "Usage: <openrs2-667 cache> <gameCache>;<fileServerCache> <journalRoot>" }
        val targets = args[1].split(';').filter { it.isNotBlank() }
        require(targets.size == 2) { "Second argument must contain exactly two cache paths separated by ';'" }

        val source = read(args[0])
        val current = read(targets.first())
        val mutations =
            SEQ_IDS.filter { !source.getValue(it).contentEquals(current.getValue(it)) }.map { id ->
                CacheMutation(
                    indexId = SEQ_INDEX,
                    groupId = id ushr 7,
                    fileId = id and 0x7f,
                    newBytes = source.getValue(id),
                    label = "seq $id: restore revision-667 Vampyre bat sequence (openrs2 #1473)",
                    expectedCurrentSha1 = CacheItemProbeTool.sha1(current.getValue(id)),
                )
            }
        if (mutations.isEmpty()) {
            println("VAMPYRE_BAT_SEQ_RESTORE already_restored=true")
            return
        }
        val transaction = CacheTransaction(targets, mutations, journalRoot = File(args[2]))
        val plan = transaction.preflight()
        println(plan.joinToString("\n"))
        check(transaction.blockingErrors(plan).isEmpty()) { transaction.blockingErrors(plan).joinToString("\n") }
        val result = transaction.apply(plan)
        val problems = transaction.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("VAMPYRE_BAT_SEQ_RESTORE transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }

    private fun read(path: String): Map<Int, ByteArray> {
        val library = CacheLibrary(path)
        try {
            return SEQ_IDS.associateWith { id ->
                requireNotNull(library.data(SEQ_INDEX, id ushr 7, id and 0x7f)) { "seq $id missing in $path" }
            }
        } finally {
            library.close()
        }
    }
}
