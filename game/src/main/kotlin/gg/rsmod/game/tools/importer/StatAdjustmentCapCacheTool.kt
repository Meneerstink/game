package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Owner 2026-10-11 ("Show stat adjustments klopt niet"): the curse tab's stat adjustment (clientscript 1711) clamps the
 * shown bonus to the 2011 maximum of +15 %. Leech Ranged (+20 % accuracy) and Leech Magic (+25 %) carry the owner's Rigour /
 * Augury modifier plus up to 5 leeched levels (AncientCurses.syncStatVarbits), so the tab could never show them. This raises
 * that one constant - the third instruction of script 1711, `PUSH_CONSTANT_INT 15` - to [NEW_CAP]; the -25 % floor and the rest
 * of the script stay byte for byte. One [CacheTransaction] over both production caches; running it again is a no-op.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.StatAdjustmentCapCacheTool plan|apply`
 */
object StatAdjustmentCapCacheTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val SCRIPT_INDEX = 12
    private const val SCRIPT = 1711
    private const val OLD_CAP = 15
    const val NEW_CAP = 50

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = args.firstOrNull() == "apply"
        val mutations = plan(GAME_CACHE)
        if (mutations.isEmpty()) {
            println("STAT_ADJUSTMENT_CAP already applied")
            return
        }
        val transaction = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = transaction.preflight()
        println(preflight.joinToString("\n"))
        val errors = transaction.blockingErrors(preflight)
        check(errors.isEmpty()) { errors.joinToString("\n") }
        if (!apply) return
        val result = transaction.apply(preflight)
        val problems = transaction.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("STAT_ADJUSTMENT_CAP transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }

    /** The mutation still needed against [cache]; empty once the cap is [NEW_CAP]. */
    fun plan(cache: String): List<CacheMutation> {
        val library = CacheLibrary(cache)
        try {
            val current = requireNotNull(library.data(SCRIPT_INDEX, SCRIPT, 0)) { "script $SCRIPT missing" }
            val patched = patch(current) ?: return emptyList()
            return listOf(
                CacheMutation(
                    SCRIPT_INDEX, SCRIPT, 0, patched, "script $SCRIPT: stat adjustment cap $OLD_CAP -> $NEW_CAP",
                    expectedCurrentSha1 = CacheItemProbeTool.sha1(current),
                ),
            )
        } finally {
            library.close()
        }
    }

    /**
     * [data] with the cap constant set to [NEW_CAP]; null when it already is. The code starts after the script's name string;
     * instructions 0 and 1 (`PUSH_CONSTANT_INT -25`, `POP_INT_LOCAL 1`) are 6 bytes each (2-byte opcode, 4-byte operand).
     */
    fun patch(data: ByteArray): ByteArray? {
        val code = data.indexOf(0.toByte()) + 1
        fun op(at: Int) = ((data[at].toInt() and 0xff) shl 8) or (data[at + 1].toInt() and 0xff)
        fun int(at: Int) = java.nio.ByteBuffer.wrap(data, at, 4).int
        check(op(code) == 0 && int(code + 2) == -25) { "script $SCRIPT: instruction 0 is not PUSH_CONSTANT_INT -25" }
        val capAt = code + 12
        check(op(capAt) == 0) { "script $SCRIPT: instruction 2 is not PUSH_CONSTANT_INT" }
        val cap = int(capAt + 2)
        if (cap == NEW_CAP) return null
        check(cap == OLD_CAP) { "script $SCRIPT: cap is $cap, expected $OLD_CAP" }
        return data.copyOf().also { java.nio.ByteBuffer.wrap(it, capAt + 2, 4).putInt(NEW_CAP) }
    }
}
