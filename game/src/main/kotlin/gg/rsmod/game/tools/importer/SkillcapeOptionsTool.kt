package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths

/**
 * N410 deel 1 cache-handoff (ChatGPT-B 25-11 01:54, applied by Claude): the third menu option of four skillcape pairs, OSRS-style.
 * Inventory option slot 3 (opcode 37) and the second worn option (string param 529); the worn 'Boost' (param 528) stays. Handlers are
 * in the N410 server code (SkillcapeFeatures). Everything else of the item definition is kept byte for byte.
 *  Agility 9771/9772 'Stamina Boost', Fletching 9783/9784 'Search', Herblore 9774/9775 'Search', Ranging 9756/9757 'Commune'.
 *
 * Usage: `bash work/claude-scripts/kc_tool.sh SkillcapeOptionsTool plan|apply`.
 */
object SkillcapeOptionsTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    private const val INDEX_ITEMS = 19

    val OPTIONS: Map<Int, String> = mapOf(
        9771 to "Stamina Boost", 9772 to "Stamina Boost",
        9783 to "Search", 9784 to "Search",
        9774 to "Search", 9775 to "Search",
        9756 to "Commune", 9757 to "Commune",
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(GAME_CACHE)
        val mutations = ArrayList<CacheMutation>()
        try {
            OPTIONS.forEach { (cape, option) ->
                val bytes = library.data(INDEX_ITEMS, cape ushr 8, cape and 0xFF) ?: error("item $cape missing")
                val edited = ItemDefCodec.cloneWithOverrides(bytes, mapOf(37 to option), stringParams = mapOf(528 to "Boost", 529 to option))
                println("BEFORE $cape ${ItemDefCodec.describeOpcodes(bytes).joinToString(" ")}")
                println("AFTER  $cape ${ItemDefCodec.describeOpcodes(edited).joinToString(" ")}")
                if (!edited.contentEquals(bytes)) {
                    mutations += CacheMutation(INDEX_ITEMS, cape ushr 8, cape and 0xFF, edited, "N410 skillcape $cape: '$option'", CacheItemProbeTool.sha1(bytes))
                }
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO the capes already have the options")
            return
        }
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
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
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
