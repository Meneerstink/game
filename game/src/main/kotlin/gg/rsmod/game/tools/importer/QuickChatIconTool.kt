package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * N238 (owner 2026-10-23: "verwijder quick chat icoon, haal em dus overal weg"): the 667 chat draws the Quick Chat bubble
 * `<img=3>` after a name - on the chat input line (clientscript 1558: "Anudd<img=3>:", the friends / clan / guest channel
 * prompts; 2650 sizes that line) and on every Quick Chat line in the chat history (84: "Name<img=3>: ..." and "From Name<img=3>:").
 * Those three scripts are the only ones in the cache that push the tag (scan of all clientscripts); each push becomes an empty
 * string, so every JOIN_STRING keeps its operand count and nothing else moves.
 *
 * Usage: `[--apply]` (apply only with 50015/50016 stopped; both caches through one [CacheTransaction]).
 */
object QuickChatIconTool {
    const val CLIENTSCRIPT_INDEX = 12
    const val ICON = "<img=3>"
    val SCRIPTS = intArrayOf(84, 1558, 2650)

    /** [current] without the Quick Chat icon; unchanged when it has none. */
    fun strip(current: ByteArray): ByteArray {
        val script = ClientScriptEditor.decode(current)
        val hits = script.code.indices.filter { script.code[it].opcode == ClientScriptEditor.STRING_CONSTANT && script.code[it].string == ICON }
        if (hits.isEmpty()) return current
        hits.forEach { i ->
            val old = script.code[i]
            val blank = ClientScriptEditor.Ins(ClientScriptEditor.STRING_CONSTANT, string = "")
            // Keep branch targets: an instruction a branch lands on is replaced in place by its twin.
            script.code.forEach { if (it.target === old) it.target = blank }
            script.code[i] = blank
        }
        return script.encode()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            SCRIPTS.forEach { id ->
                val current = requireNotNull(library.data(CLIENTSCRIPT_INDEX, id, 0)) { "clientscript $id missing" }
                val stripped = strip(current)
                if (!stripped.contentEquals(current)) {
                    mutations += CacheMutation(CLIENTSCRIPT_INDEX, id, 0, stripped, "N238 clientscript $id: no Quick Chat icon", expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
                }
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println(plan.joinToString("\n"))
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
    }
}
