package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths

/**
 * N577 (owner 25-11 "GE-vlag mee in de volgende cache-ronde"): the 667 client's Grand Exchange search lists only items with the
 * cache GE flag (opcode 65). Items the server admits on the GE (and OSRS lists on its GE) but whose 667 definition lacks the flag
 * could be sold, yet no buyer could find them. This adds the flag to the ids in `data/cfg/ge/ge_flag_add.txt`, in both caches,
 * through one [CacheTransaction]. The counterpart that REMOVES the flag is [RemovedContentCacheTool] (`ge_hidden.txt`).
 *
 * An item definition is an opcode stream ending in 0; opcode 65 has no payload, so the flag is written just before that 0.
 * Usage: GeFlagAddCacheTool plan|apply (apply only with the servers on 50015/50016 stopped).
 */
object GeFlagAddCacheTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    private val TABLE = java.io.File(RspsPaths.data("cfg/ge/ge_flag_add.txt"))
    private const val INDEX_ITEMS = 19
    private const val GE_FLAG_OPCODE = 65

    fun ids(): List<Int> = TABLE.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.trim().toInt() }.distinct().sorted()

    /** [bytes] with opcode 65 added before the closing 0, or the same bytes when the flag is already there. */
    fun withGeFlag(bytes: ByteArray): ByteArray {
        if (ItemDefCodec.describeOpcodes(bytes).any { it == "$GE_FLAG_OPCODE=FLAG" }) return bytes
        check(bytes.isNotEmpty() && bytes.last() == 0.toByte()) { "item definition does not end in opcode 0" }
        val edited = bytes.copyOf(bytes.size + 1)
        edited[bytes.size - 1] = GE_FLAG_OPCODE.toByte()
        edited[bytes.size] = 0
        return edited
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        try {
            for (item in ids()) {
                val bytes = library.data(INDEX_ITEMS, item ushr 8, item and 0xFF) ?: error("item $item missing")
                val edited = withGeFlag(bytes)
                val added = !edited.contentEquals(bytes)
                if (added) {
                    check(ItemDefCodec.describeOpcodes(edited).count { it == "$GE_FLAG_OPCODE=FLAG" } == 1) { "item $item: flag not readable after edit" }
                    check(ItemDefCodec.readName(edited) == ItemDefCodec.readName(bytes)) { "item $item: name changed" }
                    mutations += CacheMutation(INDEX_ITEMS, item ushr 8, item and 0xFF, edited, "item $item: Grand Exchange flag added (N577)", CacheItemProbeTool.sha1(bytes))
                }
                println("ITEM $item ${ItemDefCodec.readName(bytes)} add=$added")
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO every listed item already has the GE flag")
            return
        }
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} mutations=${mutations.size} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
