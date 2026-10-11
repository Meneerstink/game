package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Gives the Construct. cape and its trimmed form (667 items 9789 / 9790) the OSRS menus (owner 2026-10-10 night run: "Construction
 * cape perks like OSRS"). OSRS Wiki "Construct. cape" (read 2026-10-10): inventory "Wear, Teleport (Home, Rimmington, Taverley, ...),
 * Tele to POH, Drop"; worn "Boost, Teleport, Tele to POH". Rev 667 has no sub-menus, so "Teleport" asks for the town in a dialogue;
 * "Boost" is left out because no skillcape boost exists on this server. Same layout as [MaxCapeMenuTool]: inventory slot 1 (opcode
 * 35) Teleport, slot 2 (36) Wear, slot 3 (37) Tele to POH, Drop stays slot 5; worn options are the string params 528...
 * Handlers: `areas/poh/poh_build.plugin.kts`.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.ConstructionCapeMenuTool plan|apply`
 */
object ConstructionCapeMenuTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val INDEX_ITEMS = 19
    val CAPES = intArrayOf(9789, 9790)

    val INVENTORY = mapOf(35 to "Teleport", 36 to "Wear", 37 to "Tele to POH")
    val WORN = listOf("Teleport", "Tele to POH").mapIndexed { i, option -> 528 + i to option }.toMap()

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(GAME_CACHE)
        val mutations = ArrayList<CacheMutation>()
        try {
            CAPES.forEach { cape ->
                val bytes = library.data(INDEX_ITEMS, cape ushr 8, cape and 0xFF) ?: error("item $cape missing")
                val edited = ItemDefCodec.cloneWithOverrides(bytes, INVENTORY, removedParamIds = (528..535).toSet(), stringParams = WORN)
                println("BEFORE $cape ${ItemDefCodec.describeOpcodes(bytes).joinToString(" ")}")
                println("AFTER  $cape ${ItemDefCodec.describeOpcodes(edited).joinToString(" ")}")
                if (!edited.contentEquals(bytes)) {
                    mutations += CacheMutation(INDEX_ITEMS, cape ushr 8, cape and 0xFF, edited, "construct. cape $cape: OSRS menus", CacheItemProbeTool.sha1(bytes))
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
