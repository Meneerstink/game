package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * CLI front-end for [CacheTransaction] covering **mesh** writes - the asset half of the
 * modern-content import pipeline (`RSPS_DECISIONS.md` 2026-09-02 "STANDING OWNER AUTHORIZATION"),
 * and the counterpart to [ItemTransactionTool], which only ever writes item definitions.
 *
 * Models live in index [ModelConvertTool.MODEL_INDEX], one group per model, so a write is addressed
 * as (index 7, group = model id, file 0). Unlike the item index there is no contiguity invariant to
 * enforce here - the shipped caches already contain thousands of holes in index 7 - so the
 * post-write check is a different one, and a stronger one: every written group is re-read from disk
 * through a fresh [CacheLibrary] and decoded with [Rev667ModelDecoder], the port of the 667 client's
 * own mesh reader. A group that cannot be decoded as a 667 mesh fails verification and the whole
 * transaction is rolled back, so a cache can never be left holding bytes the client would choke on.
 *
 * Every verb is a dry run unless `--apply` is passed.
 *
 * Verbs:
 *
 *  - `put <modelFile> <groupId> --targets=<a>[;<b>...] [--expect-sha1=<hex>] [--apply]`
 *      Write one converted mesh into every target cache. Replacing existing different content
 *      requires `--expect-sha1` naming exactly what is being replaced.
 */
object ModelTransactionTool {
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

        check(verb == "put") { "Unknown verb '$verb'.\n$USAGE" }
        require(positional.size >= 2) { USAGE }

        val targets =
            (flags["targets"] ?: error("Missing --targets=<cachePath>[;<cachePath>...]"))
                .split(';').filter { it.isNotBlank() }
        require(targets.isNotEmpty()) { "--targets resolved to no cache paths." }
        val apply = flags["apply"] == "true"

        val bytes = File(positional[0]).readBytes()
        val groupId = positional[1].toInt()

        // Refuse to stage anything the target client could not read, before a transaction even
        // exists: this is the same decoder the verification step uses.
        val model = Rev667ModelDecoder.decode(bytes)
        println(
            "SOURCE=file:${positional[0]} model=$groupId bytes=${bytes.size} sha1=${CacheItemProbeTool.sha1(bytes)} " +
                "decoded=${model.describe()}",
        )

        val mutation =
            CacheMutation(
                indexId = ModelConvertTool.MODEL_INDEX,
                groupId = groupId,
                fileId = 0,
                newBytes = bytes,
                label = "put model $groupId",
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
        problems += decodeProblems(targets, groupId)
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            val restored = transaction.rollback()
            println("ROLLED_BACK: restored $restored journalled location(s) from ${transaction.journalDir}.")
            error("Transaction ${transaction.id} failed verification and was rolled back.")
        }
        println("VERIFIED: all ${targets.size} target cache(s) agree and model $groupId decodes as a rev-667 mesh.")
    }

    /**
     * Re-reads [groupId] from every target through a freshly opened cache and decodes it with the
     * client's own algorithm. Reading through the cache (rather than trusting the bytes handed to
     * [CacheTransaction]) is what makes this evidence about what the client will actually receive.
     */
    private fun decodeProblems(
        targets: List<String>,
        groupId: Int,
    ): List<String> =
        targets.mapNotNull { target ->
            val library = CacheLibrary(target)
            try {
                val stored = library.data(ModelConvertTool.MODEL_INDEX, groupId)
                when {
                    stored == null -> "MODEL_ABSENT at $target: index ${ModelConvertTool.MODEL_INDEX} group $groupId has no data after the write."
                    else ->
                        runCatching { Rev667ModelDecoder.decode(stored) }
                            .fold(
                                onSuccess = {
                                    println("  MODEL_DECODE_OK $target group=$groupId ${it.describe()}")
                                    null
                                },
                                onFailure = { "MODEL_UNDECODABLE at $target group $groupId: ${it.javaClass.simpleName}: ${it.message}" },
                            )
                }
            } finally {
                library.close()
            }
        }

    private const val USAGE =
        "Usage:\n" +
            "  put <modelFile> <groupId> --targets=<a>[;<b>...] [--expect-sha1=<hex>] [--apply]"
}
