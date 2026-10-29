package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths

/**
 * K03 N410 part 5: gives the skillcapes with an OSRS teleport their OSRS menus (OSRS cache 2686 item defs, inventory ops and worn
 * params 451..): Strength cape "Teleport" / worn "Warriors' Guild"; Fishing cape "Fishing Guild", "Otto's Grotto" (both menus);
 * Crafting cape "Teleport" (both); Hunter cape "Teleport" / worn "Carnivorous Chinchompas", "Black Chinchompas" (OSRS also lists
 * the Hunter Guild, which this revision-667 world does not have); Quest point cape "Teleport" (both; the OSRS "Trim" option is
 * left out - this cache has no trimmed quest point cape). The 667 inventory menu keeps Wear in slot 2 (opcode 36); worn options
 * are the string params 528.. and the cape's own "Boost" stays first. Handlers: `skillcape_teleports.plugin.kts`.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.SkillcapeMenuTool plan|apply`
 */
object SkillcapeMenuTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    private const val INDEX_ITEMS = 19

    class Menu(val inventory: Map<Int, String>, val worn: List<String>)

    private val STRENGTH = Menu(mapOf(37 to "Teleport"), listOf("Boost", "Warriors' Guild"))
    private val FISHING = Menu(mapOf(37 to "Fishing Guild", 38 to "Otto's Grotto"), listOf("Boost", "Fishing Guild", "Otto's Grotto"))
    private val CRAFTING = Menu(mapOf(37 to "Teleport"), listOf("Boost", "Teleport"))
    private val HUNTER = Menu(mapOf(37 to "Teleport"), listOf("Boost", "Carnivorous Chinchompas", "Black Chinchompas"))
    private val QUEST_POINT = Menu(mapOf(37 to "Teleport"), listOf("Teleport"))

    /** Strength, Fishing, Crafting and Hunter cape (and trimmed), Quest point cape. */
    val MENUS: Map<Int, Menu> =
        mapOf(
            9750 to STRENGTH, 9751 to STRENGTH,
            9798 to FISHING, 9799 to FISHING,
            9780 to CRAFTING, 9781 to CRAFTING,
            9948 to HUNTER, 9949 to HUNTER,
            9813 to QUEST_POINT,
        )

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(GAME_CACHE)
        val mutations = mutableListOf<CacheMutation>()
        try {
            MENUS.forEach { (item, menu) ->
                val bytes = library.data(INDEX_ITEMS, item ushr 8, item and 0xFF) ?: error("item $item missing")
                val edited =
                    ItemDefCodec.cloneWithOverrides(
                        bytes,
                        menu.inventory,
                        removedParamIds = (528..535).toSet(),
                        stringParams = menu.worn.mapIndexed { i, option -> 528 + i to option }.toMap(),
                    )
                println("$item BEFORE ${ItemDefCodec.describeOpcodes(bytes).joinToString(" ")}")
                println("$item AFTER  ${ItemDefCodec.describeOpcodes(edited).joinToString(" ")}")
                if (!edited.contentEquals(bytes)) {
                    mutations +=
                        CacheMutation(INDEX_ITEMS, item ushr 8, item and 0xFF, edited, "skillcape $item: OSRS teleport menus", CacheItemProbeTool.sha1(bytes))
                }
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO the skillcapes already have the OSRS menus")
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
