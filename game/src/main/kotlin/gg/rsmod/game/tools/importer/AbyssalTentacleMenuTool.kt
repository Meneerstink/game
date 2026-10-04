package gg.rsmod.game.tools.importer

/**
 * Gives the Abyssal tentacle (item 22325) its OSRS menus (degradation audit 2026-10-04, OSRS Wiki "Abyssal tentacle" infobox:
 * "options = Wield, Check, Dissolve | wornoptions = Check | destroy = Dissolve"). This cache's item only had "Wield". The 667
 * inventory menu keeps the wield option in slot 2 (opcode 36); "Check" goes to slot 3 (opcode 37) and "Dissolve" replaces Drop in
 * slot 5 (opcode 39 - the server routes a non-Drop op 5 to its item-option plugin, OpHeld5Handler). The worn "Check" is rev-667
 * string param 528 (server `ItemDef.equipmentMenu`). Handlers: `items/osrs/abyssal_tentacle.plugin.kts`.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.AbyssalTentacleMenuTool plan|apply` (servers stopped for apply).
 */
object AbyssalTentacleMenuTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val INDEX_ITEMS = 19
    const val ABYSSAL_TENTACLE = 22325

    val INVENTORY = mapOf(36 to "Wield", 37 to "Check", 39 to "Dissolve")
    val WORN = mapOf(528 to "Check")

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = com.displee.cache.CacheLibrary(GAME_CACHE)
        val bytes: ByteArray
        val edited: ByteArray
        try {
            bytes = library.data(INDEX_ITEMS, ABYSSAL_TENTACLE ushr 8, ABYSSAL_TENTACLE and 0xFF) ?: error("item $ABYSSAL_TENTACLE missing")
            edited = ItemDefCodec.cloneWithOverrides(bytes, INVENTORY, stringParams = WORN)
        } finally {
            library.close()
        }
        println("BEFORE ${ItemDefCodec.describeOpcodes(bytes).joinToString(" ")}")
        println("AFTER  ${ItemDefCodec.describeOpcodes(edited).joinToString(" ")}")
        if (edited.contentEquals(bytes)) {
            println("NOTHING_TO_DO the tentacle already has the OSRS menus")
            return
        }
        val tx =
            CacheTransaction(
                listOf(GAME_CACHE, FILE_SERVER_CACHE),
                listOf(
                    CacheMutation(
                        INDEX_ITEMS, ABYSSAL_TENTACLE ushr 8, ABYSSAL_TENTACLE and 0xFF, edited,
                        "abyssal tentacle: OSRS Wield / Check / Dissolve and worn Check", CacheItemProbeTool.sha1(bytes),
                    ),
                ),
            )
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
