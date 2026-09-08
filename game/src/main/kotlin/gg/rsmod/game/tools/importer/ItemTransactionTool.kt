package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * CLI front-end for [CacheTransaction], covering the item-definition mutations the modern-content
 * import pipeline performs (`RSPS_DECISIONS.md` 2026-09-02 "STANDING OWNER AUTHORIZATION").
 *
 * Unlike [ItemImportTool], which writes to exactly one cache path and has no recovery story, every
 * verb here plans against **all** target caches at once, journals what it is about to overwrite,
 * verifies both targets afterwards, and rolls back automatically when verification fails. It also
 * enforces the item index's hard contiguity invariant (see [CacheItemProbeTool.maxContiguousItemId])
 * after every write, because a gap crashes the server at boot.
 *
 * Every verb is a dry run unless `--apply` is passed, so a preflight plan can always be inspected
 * before any real cache is touched.
 *
 * Verbs:
 *
 *  - `sync <sourceCachePath> <itemId> --targets=<a>[;<b>...] [--apply]`
 *      Copy an item's exact archive bytes from one cache into every target cache. This is the
 *      repair path for a half-import where one cache received an item and the other did not.
 *
 *  - `put <itemBytesFile> <itemId> --targets=<a>[;<b>...] [--expect-sha1=<hex>] [--apply]`
 *      Write raw, already-prepared item archive bytes to an id in every target cache. Replacing
 *      existing different content requires `--expect-sha1` naming exactly what is being replaced.
 *
 *  - `remove <itemId> --targets=<a>[;<b>...] [--apply]`
 *      Delete an item from every target cache.
 */
object ItemTransactionTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isNotEmpty()) { USAGE }
        val verb = args[0]
        val positional = args.drop(1).filterNot { it.startsWith("--") }
        val flags =
            args.filter { it.startsWith("--") }
                .associate { flag ->
                    val eq = flag.indexOf('=')
                    if (eq == -1) flag.removePrefix("--") to "true" else flag.substring(2, eq) to flag.substring(eq + 1)
                }

        val targets =
            (flags["targets"] ?: error("Missing --targets=<cachePath>[;<cachePath>...]"))
                .split(';').filter { it.isNotBlank() }
        require(targets.isNotEmpty()) { "--targets resolved to no cache paths." }
        val apply = flags["apply"] == "true"

        val (itemId, bytes) =
            when (verb) {
                "sync" -> {
                    require(positional.size >= 2) { USAGE }
                    val sourcePath = positional[0]
                    val id = positional[1].toInt()
                    val library = CacheLibrary(sourcePath)
                    val sourceBytes =
                        try {
                            CacheItemProbeTool.itemData(library, id)
                                ?: error("Source cache $sourcePath has no data for item $id - nothing to sync.")
                        } finally {
                            library.close()
                        }
                    println("SOURCE=$sourcePath item=$id bytes=${sourceBytes.size} sha1=${CacheItemProbeTool.sha1(sourceBytes)} name=${ItemDefCodec.readName(sourceBytes)}")
                    id to sourceBytes
                }
                "put" -> {
                    require(positional.size >= 2) { USAGE }
                    val fileBytes = File(positional[0]).readBytes()
                    val id = positional[1].toInt()
                    println("SOURCE=file:${positional[0]} item=$id bytes=${fileBytes.size} sha1=${CacheItemProbeTool.sha1(fileBytes)} name=${ItemDefCodec.readName(fileBytes)}")
                    id to fileBytes
                }
                "remove" -> {
                    require(positional.isNotEmpty()) { USAGE }
                    positional[0].toInt() to null
                }
                else -> error("Unknown verb '$verb'.\n$USAGE")
            }

        val mutation =
            CacheMutation.item(
                itemId = itemId,
                newBytes = bytes,
                label = "$verb item $itemId",
                expectedCurrentSha1 = flags["expect-sha1"],
            )
        val transaction = CacheTransaction(targets = targets, mutations = listOf(mutation))

        val plan = transaction.preflight()
        println("PREFLIGHT transaction=${transaction.id}")
        plan.forEach { println("  $it") }
        val errors = transaction.blockingErrors(plan)
        if (errors.isNotEmpty()) {
            errors.forEach { println("  BLOCKING: $it") }
            error("Preflight has ${errors.size} blocking error(s); nothing was written.")
        }

        if (!apply) {
            println("DRY_RUN: no cache was modified. Re-run with --apply to perform this transaction.")
            return
        }

        val result = transaction.apply(plan)
        println("APPLIED transaction=${result.transactionId} writes=${result.applied} skipped=${result.skipped} journal=${result.journalDir}")

        val problems = transaction.verify().toMutableList()
        problems += contiguityProblems(targets)
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            val restored = transaction.rollback()
            println("ROLLED_BACK: restored $restored journalled location(s) from ${transaction.journalDir}.")
            error("Transaction ${transaction.id} failed verification and was rolled back.")
        }
        println("VERIFIED: all ${targets.size} target cache(s) agree and the item index is still gap-free.")
    }

    /**
     * Rejects any post-write state where an item id has data but is not part of the contiguous
     * block starting at 0 - the exact shape that crashed the server at boot during the first
     * import attempt (see the id-allocation note in `RSPS_IMPORT_MANIFEST.yml`).
     *
     * Enumerates the complete real item index via [CacheItemProbeTool.allItemIds] rather than
     * scanning a fixed window past the contiguous block, so an orphan anywhere in the id space -
     * not just within the next few hundred ids - is caught.
     */
    private fun contiguityProblems(targets: List<String>): List<String> =
        targets.mapNotNull { target ->
            val library = CacheLibrary(target)
            try {
                val allIds = CacheItemProbeTool.allItemIds(library)
                val max = CacheItemProbeTool.maxContiguousItemId(allIds)
                val orphans = allIds.filter { it > max }
                if (orphans.isNotEmpty()) {
                    "ITEM_ID_GAP at $target: contiguous block ends at $max but ${orphans.size} orphan id(s) also have data, e.g. ${orphans.first()}."
                } else {
                    println("  CONTIGUITY_OK $target max_contiguous_item_id=$max item_index_id_count=${allIds.size}")
                    null
                }
            } finally {
                library.close()
            }
        }

    private const val USAGE =
        "Usage:\n" +
            "  sync <sourceCachePath> <itemId> --targets=<a>[;<b>...] [--apply]\n" +
            "  put <itemBytesFile> <itemId> --targets=<a>[;<b>...] [--expect-sha1=<hex>] [--apply]\n" +
            "  remove <itemId> --targets=<a>[;<b>...] [--apply]"
}
