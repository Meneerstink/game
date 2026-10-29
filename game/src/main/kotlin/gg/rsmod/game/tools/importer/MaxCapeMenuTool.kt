package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths

/**
 * Gives the plain max cape (667 item 20767) the OSRS menus (OSRS Wiki "Max cape": inventory "Wear, Teleports, Spellbook, Features,
 * Drop"; worn "Home, Crafting Guild, Guild Teleports, Skilling Areas, POH Portals, Spellbook, Features") and the Magic cape and Magic
 * cape (t) theirs (OSRS Wiki "Magic cape": inventory "Wear, Spellbook, Check, Drop"; worn "Boost, Spellbook, Check"). The 667
 * inventory menu has five slots with the wear option in slot 2 (opcode 36) and Drop in slot 5. Worn options are the rev-667
 * string params 528.. (server `ItemDef.equipmentMenu`). Handlers: `max_cape_options.plugin.kts`, `skillcape_spellbook.plugin.kts`,
 * `skillcape_boost.plugin.kts`.
 *
 * "Spellbook" was left out of the max cape from buglist E2 (owner 2026-10-03) until the owner asked for it on 2026-10-29 ("jawel op
 * maxcape magic en normale magic cape moet je dus 5 keer kunen switchen").
 *
 * Usage: `./gradlew :game:runMaxCapeMenuTool --args="plan|apply"`
 */
object MaxCapeMenuTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    private const val INDEX_ITEMS = 19
    const val MAX_CAPE = 20767
    const val MAGIC_CAPE = 9762
    const val MAGIC_CAPE_T = 9763

    val INVENTORY = mapOf(35 to "Teleports", 36 to "Wear", 37 to "Spellbook", 38 to "Features")

    /**
     * The worn menu holds four options of its own: the equipment slot script (clientscript 1612) shows params 528-531 as ops 2-5
     * (and 1211 as op 6), so the OSRS seven (Home, Crafting Guild, Guild Teleports, Skilling Areas, POH Portals, Spellbook, Features)
     * left POH Portals, Spellbook and Features unreachable. ADAPTED: Home, then "Teleports" - the inventory option's own menu (Guild
     * Teleports with the Crafting Guild, Skilling Areas, POH Portals) - Spellbook and Features: every OSRS destination stays one menu away.
     */
    val WORN = listOf("Home", "Teleports", "Spellbook", "Features").mapIndexed { i, option -> 528 + i to option }.toMap()

    val MAGIC_INVENTORY = mapOf(36 to "Wear", 37 to "Spellbook", 38 to "Check")

    val MAGIC_WORN = listOf("Boost", "Spellbook", "Check").mapIndexed { i, option -> 528 + i to option }.toMap()

    /** Item -> its inventory menu (opcode -> option) and worn menu (param -> option). */
    val MENUS: Map<Int, Pair<Map<Int, String>, Map<Int, String>>> =
        mapOf(MAX_CAPE to (INVENTORY to WORN), MAGIC_CAPE to (MAGIC_INVENTORY to MAGIC_WORN), MAGIC_CAPE_T to (MAGIC_INVENTORY to MAGIC_WORN))

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(GAME_CACHE)
        val mutations = ArrayList<CacheMutation>()
        try {
            MENUS.forEach { (item, menus) ->
                val (inventory, worn) = menus
                val bytes = library.data(INDEX_ITEMS, item ushr 8, item and 0xFF) ?: error("item $item missing")
                val edited =
                    ItemDefCodec.cloneWithOverrides(
                        bytes,
                        inventory,
                        removedOpcodes = (35..38).toSet() - inventory.keys,
                        removedParamIds = (528..535).toSet(),
                        stringParams = worn,
                    )
                println("ITEM $item BEFORE ${ItemDefCodec.describeOpcodes(bytes).joinToString(" ")}")
                println("ITEM $item AFTER  ${ItemDefCodec.describeOpcodes(edited).joinToString(" ")}")
                if (!edited.contentEquals(bytes)) {
                    mutations += CacheMutation(INDEX_ITEMS, item ushr 8, item and 0xFF, edited, "item $item: OSRS inventory and worn menus", CacheItemProbeTool.sha1(bytes))
                }
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO the capes already have the OSRS menus")
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
