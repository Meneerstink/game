package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Lets an "Enter amount" box take B for billions, as OSRS does ("1b"; owner buglist E7, the bank must work like the OSRS bank).
 * The 667 amount box (clientscript 112 -> 802) only lets a digit or one of the suffix letters in the string constants "KkMm"
 * (English) / "KkMmTt" (other languages) be typed; 112 then turns K into 000 and M into 000000 and the client (RspsCountInput)
 * turns B into 000000000 and caps the amount. This adds "Bb" to both constants of script 802. A CS2 branch counts instructions,
 * not bytes, and a string operand ends at its 0 byte, so a longer string operand moves no branch target.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.CountInputSuffixTool plan|apply` (servers stopped for apply).
 */
object CountInputSuffixTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val CLIENTSCRIPT_INDEX = 12
    const val SCRIPT = 802

    private val REPLACEMENTS = listOf("KkMmTt" to "KkMmTtBb", "KkMm" to "KkMmBb")

    /** [data] with every 0-terminated string operand equal to a [REPLACEMENTS] key replaced; refuses anything ambiguous. */
    fun patch(data: ByteArray): ByteArray {
        var bytes = data
        for ((from, to) in REPLACEMENTS) {
            val needle = from.toByteArray(Charsets.ISO_8859_1) + 0
            val hits = indicesOf(bytes, needle).filter { it == 0 || bytes[it - 1].toInt() == 0 || !Character.isLetter(bytes[it - 1].toInt().toChar()) }
            if (hits.isEmpty() && indicesOf(bytes, to.toByteArray(Charsets.ISO_8859_1) + 0).isNotEmpty()) continue
            check(hits.size == 1) { "script $SCRIPT: expected one \"$from\" operand, found ${hits.size}" }
            val at = hits.single()
            bytes = bytes.copyOfRange(0, at) + to.toByteArray(Charsets.ISO_8859_1) + bytes.copyOfRange(at + from.length, bytes.size)
        }
        return bytes
    }

    private fun indicesOf(
        data: ByteArray,
        needle: ByteArray,
    ): List<Int> = (0..data.size - needle.size).filter { i -> needle.indices.all { data[i + it] == needle[it] } }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(GAME_CACHE)
        val current: ByteArray
        val patched: ByteArray
        try {
            current = library.data(CLIENTSCRIPT_INDEX, SCRIPT, 0) ?: error("clientscript $SCRIPT missing")
            patched = patch(current)
        } finally {
            library.close()
        }
        if (patched.contentEquals(current)) {
            println("NOTHING_TO_DO clientscript $SCRIPT already accepts B")
            return
        }
        println("PLAN clientscript $SCRIPT ${current.size} -> ${patched.size} bytes")
        val tx =
            CacheTransaction(
                listOf(GAME_CACHE, FILE_SERVER_CACHE),
                listOf(CacheMutation(CLIENTSCRIPT_INDEX, SCRIPT, 0, patched, "clientscript $SCRIPT: amount box accepts B (billions)", CacheItemProbeTool.sha1(current))),
            )
        val preflight = tx.preflight()
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK")
    }
}
